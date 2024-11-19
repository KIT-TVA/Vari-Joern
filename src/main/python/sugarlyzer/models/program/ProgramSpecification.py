import functools
import importlib.resources
import json
import logging
import os
import re
import shutil
import subprocess
from abc import ABC, abstractmethod
from dataclasses import dataclass
from importlib.abc import Traversable
from io import StringIO
from pathlib import Path
from typing import List, Iterable, Dict, Tuple, Any, Callable

from jsonschema.validators import RefResolver, Draft7Validator

from python.kgenerateBeta.kgenerate import run_kgenerate
from python.sugarlyzer.util.Kconfig import collect_kconfig_files
from python.sugarlyzer.util.MacroDiscoveryPreprocessor import MacroDiscoveryPreprocessor

logger = logging.getLogger(__name__)


class ProgramSpecification(ABC):
    """
    Abstract base class for the specification of a program supported by the analysis.
    """

    def __init__(self,
                 name: str,
                 project_root: Path,
                 tmp_dir: Path,
                 # Everything below set by program specification file.
                 remove_errors: bool = False,
                 config_prefix: str = None,
                 source_dirs: list[str] = None,
                 make_target: str = None,
                 makefile_dir_path: str = None,
                 kconfig_root_file_path: str = None,
                 kconfig_root_path: str = None,
                 kconfig_file_names: list[str] = None,
                 config_header_path: str = None,
                 included_files_and_directories: Iterable[Dict] | None = None,
                 source_file_encoding: str = None):
        """
        Constructs a new ProgramSpecification instance.

        :param name: The name of the program.
        :param project_root: The absolute path to the root directory of the program.
        :param remove_errors: Whether desugaring should be re-run to remove bad configurations.
        :param config_prefix: The prefix to which SugarC should be limited in its macro expansion
        :param source_dirs: The relative paths to directories containing source code starting from the project's root.
        :param make_target: The make target that should be used to determine necessary includes of the source files.
        :param makefile_dir_path: The relative path to the directory containing the Makefile starting from the project's root.
        :param kconfig_root_file_path: The relative path to the root Kconfig file starting from the project's root.
        :param kconfig_root_path: The relative path to the directory from which Kconfig source calls are resolved starting from the project's root.
        :param config_header_path: The relative path to the config header file starting from the project's root.
        :param included_files_and_directories: Macros as well as headers and directories required for parsing the source
        files of the project.
        :return: A new ProgramSpecification.
        """

        self.name: str = name
        self.project_root: Path = project_root
        self.source_file_encoding = source_file_encoding
        self.__tmp_dir: Path = tmp_dir

        self.remove_errors: bool = remove_errors
        self.config_prefix: str | None = config_prefix

        self.source_dirs: list[Path] = []
        if source_dirs is None or len(source_dirs) == 0:
            self.source_dirs.append(self.project_root)
        else:
            for source_dir in source_dirs:
                self.source_dirs.append(self.try_resolve_path(path=source_dir,
                                                              root=self.project_root))

        self.make_target: str | None = make_target
        self.makefile_dir_path: Path = self.try_resolve_path(path=makefile_dir_path, root=self.project_root) \
            if makefile_dir_path is not None else self.project_root
        self.kconfig_root_file_path: Path = self.try_resolve_path(path=kconfig_root_file_path, root=self.project_root) \
            if kconfig_root_file_path is not None else self.project_root / Path("Config.in")
        self.kconfig_root_path: Path = self.try_resolve_path(path=kconfig_root_path, root=self.project_root) \
            if kconfig_root_path is not None else self.project_root
        self.kconfig_file_names: list[str] = ["Config.in"] if kconfig_file_names is None else kconfig_file_names

        if config_header_path is None:
            self.config_header_path: Path = self.project_root / Path("config.h")
        else:
            # Cannot resolve the path as it might not exist yet (sometimes only generated during a make call).
            self.config_header_path: Path = self.project_root / Path(config_header_path)

        self.inc_dirs_and_files = [] if included_files_and_directories is None else included_files_and_directories

        self.no_std_libs = True  # TODO Consider removing (Sugarlyzer debt).
        self.__oldconfig_location = "config/.config"  # TODO Consider removing (Sugarlyzer debt).
        self.__search_context = None

        # Collect includes and macros from system and make.
        self.__make_includes: list[dict] = self.collect_make_includes()
        self.__system_includes: list[Path] = self.__collect_system_includes()
        self.__system_macros: Path = self.__get_system_macro_header()
        self.__program_macros: Path = self.__get_program_macro_header()

        # Replaces the default config.h (or similar) with the one generated by kgenerate and creates the mapping file.
        # Note: It is important that this happens after the make includes have been collected as sometimes certain Kconfig
        # files are only generated during the build.
        self.create_config_header_and_mapping()

    # TODO Consider removing (Sugarlyzer debt).
    @property
    def oldconfig_location(self):
        return self.try_resolve_path(self.__oldconfig_location, self.makefile_dir_path)

    # TODO Consider removing (Sugarlyzer debt).
    @property
    def sample_directory(self):
        return self.try_resolve_path(self.__sample_directory, importlib.resources.path('resources.programs', ''))

    # TODO Consider removing (Sugarlyzer debt).
    @property
    def search_context(self):
        p = Path(self.__search_context)
        if not p.exists():
            raise RuntimeError(f"Search context {p} does not exist.")
        else:
            return p

    def get_source_files(self) -> Iterable[Path]:
        """
        :return: All .c or .i files that are in the program's source directory but have not been produced by earlier
        desugaring.
        """
        for source_dir in self.source_dirs:
            for root, dirs, files in os.walk(source_dir):
                for f in files:
                    if (f.endswith(".c") or f.endswith(".i")) and not ("sugarlyzer.desugared" in f):
                        yield Path(root) / f

    def clean_intermediary_results(self):
        """
        Walks through the program and cleans up all intermediary results/files created by Sugarlyzer.
        """
        for root, dirs, files in os.walk(self.project_root):
            for file in files:
                try:
                    # Reinstate initial versions of altered files and delete the altered version.
                    if file.endswith(".sugarlyzer.orig"):
                        original_file_name: str = file.split(".sugarlyzer.orig")[0]
                        transformed_file: Path = Path(root) / original_file_name
                        transformed_file.unlink()

                        os.rename(src=str(Path(root) / file), dst=str(Path(root) / original_file_name))
                        continue

                    # Delete intermediary files.
                    if ".sugarlyzer." in file:
                        file_to_delete = Path(root) / file
                        file_to_delete.unlink()
                        continue
                except FileNotFoundError:
                    logger.warning(
                        f"Tried to clean up {file} in {root} or associated intermediary results but could not a file.")
                except PermissionError:
                    logger.warning(
                        f"Tried to clean up {file} in {root} or associated intermediary results but did not have sufficient permissions.")
                except Exception as e:
                    logger.exception(
                        f"Tried to clean up {file} in {root} or associated intermediary results but encountered unexpected exception: {e}")

    def inc_files_and_dirs_for_file(self, file: Path) -> Tuple[Iterable[Path], Iterable[Path], Iterable[str]]:
        """
        Iterates through the program.json's get_recommended_space field,
        returning the first match. See program_specification_schema.json for more info.
        :param file: The source file to search for.
        :return: included_files, included_directories for the first object in
        get_recommended_space with a regular expression that matches the **absolute** file name.
        """

        # Collect includes and macros from make call.
        inc_files_make, inc_dirs_make, cmd_decs_make = self.process_inc_dirs_and_files(
            inc_dirs_and_files=self.__make_includes,
            file=file,
            include_only_file_specific_macros=False)
        # Collect manually defined includes and macros from program.json.
        inc_files_manual, inc_dirs_manual, cmd_decs_manual = self.process_inc_dirs_and_files(
            inc_dirs_and_files=self.inc_dirs_and_files,
            file=file,
            include_only_file_specific_macros=True)

        inc_dirs: List[Path] = []
        inc_dirs.extend(self.__system_includes)  # Add system header paths.
        inc_dirs.extend(inc_dirs_make)
        inc_dirs.extend(inc_dirs_manual)

        # System and program macros are stored in their own headers that should be included via -include.
        inc_files: List[Path] = [self.__system_macros, self.__program_macros]
        inc_files.extend(inc_files_make)
        inc_files.extend(inc_files_manual)

        cmd_decs: List[str] = []
        cmd_decs.extend(cmd_decs_make)
        cmd_decs.extend(cmd_decs_manual)

        return inc_files, inc_dirs, cmd_decs

    def process_inc_dirs_and_files(self, inc_dirs_and_files: Iterable[dict],
                                   file: Path,
                                   include_only_file_specific_macros: bool) -> tuple[list[Path], list[Path], list[str]]:
        inc_files: list[Path] = []
        inc_dirs: list[Path] = []
        cmd_decs: list[str] = []

        for spec in inc_dirs_and_files:
            # Note the difference between s[a] and s.get(a) is the former will
            #  raise an exception if a is not in s, while s.get will return None.
            if spec.get('file_pattern') is None or re.search(pattern=spec.get('file_pattern'),
                                                             string=str(file.absolute())):
                if (rt := spec.get('relative_to')) is not None:
                    relative_to = Path(rt)
                else:
                    relative_to = self.project_root
                if 'included_files' in spec.keys():
                    inc_files.extend(self.try_resolve_path(Path(p), relative_to) for p in spec['included_files'])
                if 'included_directories' in spec.keys():
                    inc_dirs.extend(self.try_resolve_path(Path(p), relative_to) for p in spec['included_directories'])
                # Deal with macros.
                if not include_only_file_specific_macros or spec.get('file_pattern') is not None:
                    # Conventional macro (un)defs.
                    if 'macro_definitions' in spec.keys():
                        cmd_decs.extend(spec['macro_definitions'])
                    # Macros relating to configuration variables (i.e., features of the SPL).
                    if 'predefined_config_macros' in spec.keys():
                        macros: list[str] = spec['predefined_config_macros']
                        # Add the config prefix to the macro name.
                        macros_with_prefix: map = map(
                            lambda macro: f"{macro[:2]} {self.config_prefix}{macro[2:].strip()}", macros)
                        cmd_decs.extend(list(macros_with_prefix))

        return inc_files, inc_dirs, cmd_decs

    @functools.cache
    def try_resolve_path(self, path: str | Path, root: Path = Path("/")) -> Path:
        """
        Copied directly from ECSTATIC.
        :param path: The path to resolve.
        :param root: The root from which to try to resolve the path.
        :return: The fully resolved path.
        """
        if not isinstance(path, Path):
            path = Path(path)
        if path is None:
            raise ValueError("Supplied path is None")

        if path.name == root.name:
            return root
        if path.is_absolute():
            ##logger.warning(f"Tried to resolve an absolute path {str(path)} from root {str(root)}. May lead to incorrect resolutions.")
            return path
        if os.path.exists(joined_path := Path(root) / path):
            return joined_path.absolute()
        results = set()
        for rootdir, _, _ in os.walk(root):
            if (cur := Path(rootdir) / path).exists():
                logger.debug(f"Checking if {cur} exists.")
                results.add(cur)
        match len(results):
            case 0:
                raise FileNotFoundError(f"Could not resolve path {path} from root {root}")
            case 1:
                logger.debug(f"Result of trying to resolve {path} with {root} was {list(results)[0]}")
                return results.pop()
            case _:
                raise RuntimeError(f"Path {path} in root {root} is ambiguous. Found the following potential results: "
                                   f"{results}. Try adding more context information to the index.json file, "
                                   f"so that the path is unique.")

    @dataclass
    class BaselineConfig:
        source_file: Path
        configuration: List[Tuple[str, str]] | Path

    def get_baseline_configurations(self) -> Iterable[Path]:
        if self.sample_directory is None:
            # If we don't have a sample directory, we use the get_all_macros function to get every possible configuration.
            raise RuntimeError("Need to reimplement this.")
        else:
            yield from self.try_resolve_path(self.sample_directory).iterdir()

    def get_all_macros(self, fpa):
        parser = MacroDiscoveryPreprocessor()
        with open(fpa, 'r') as f:
            parser.parse(f.read())
        parser.write(StringIO())
        logger.debug(f"Discovered the following macros in file {fpa}: {parser.collected}")
        return parser.collected

    @search_context.setter
    def search_context(self, value):
        self.__search_context = value

    def create_config_header_and_mapping(self):
        """
        Creates the config header (usually config.h) and mapping fie (kgenerate_macro_mapping.json) based on the
        information extracted from the program's Kconfig files.
        """
        logger.info("Creating config header and mapping.")

        # Bring the program's Kconfig files into the format required by kextract. This is necessary as newer versions of
        # the Kconfig parser used in kextract (notably next-20200430 and next-20210426) expect the same format as found
        # in the Linux kernel.
        logger.info("Transforming the kconfig files of the program into a suitable form.")
        transformed_to_original_kconfig_files: dict[str, str] = self.transform_kconfig_into_kextract_format()

        # Preserve old config header, if necessary.
        if self.config_header_path.exists():
            shutil.copyfile(src=self.config_header_path, dst=str(self.config_header_path) + ".sugarlyzer.orig")

        logger.info("Running kgenerate.")
        with (importlib.resources.path(f'resources.sugarlyzer.programs.{self.name}', 'kgenerate_format.txt')
              as format_file_path):
            run_kgenerate(kconfig_file_path=self.kconfig_root_file_path,
                          format_file_path=format_file_path,
                          header_output_path=self.config_header_path,
                          mapping_file_output_dir_path=self.__tmp_dir,
                          tmp_directory_path=self.__tmp_dir,
                          source_tree_path=self.kconfig_root_path,
                          config_prefix=self.config_prefix,
                          module_version="next-20210426")

        # Revert the changes to the Kconfig files as they can interfere with subsequent make calls.
        for transformed, original in transformed_to_original_kconfig_files.items():
            os.unlink(transformed)
            os.rename(src=original, dst=transformed)

    def transform_kconfig_into_kextract_format(self) -> dict[str, str]:
        """
        Transform the kconfig files of the program into the format used within the Linux kernel and expected by newer
        version of kextract.

        :return: A dict that maps the transformed kconfig files to their original unaltered variant.
        """
        transformed_to_old_files: dict[str, str] = {}
        kconfig_files: list[Path] = collect_kconfig_files(kconfig_file_names=self.kconfig_file_names,
                                                          root_directory=self.project_root)

        problematic_patterns_and_rewrites: list[
            (str, Callable[[str], str])] = self.problematic_kconfig_lines_and_corrections()

        # Go through the Kconfig files and adjust problematic syntax.
        for kconfig_file in kconfig_files:
            transformed_file_path: str = str(kconfig_file) + ".tmp"
            with open(kconfig_file, "r") as input_file, open(transformed_file_path, "w") as output_file:
                for line in input_file:
                    rewritten: bool = False
                    for pattern, rewriter in problematic_patterns_and_rewrites:
                        if re.match(pattern=pattern, string=line.strip()):
                            output_file.write(rewriter(line))
                            logger.info(
                                f"In file {kconfig_file} rewrote \"{line.strip()}\" to \"{rewriter(line).strip()}\"")
                            rewritten = True
                            break

                    if rewritten:
                        continue
                    output_file.write(f"{line}")

            # Replace old Kconfig file with transformed one but retain the old one to restore it after the analysis.
            original_file_path: str = str(kconfig_file)
            tmp_save_file_path: str = str(kconfig_file) + ".sugarlyzer.orig"
            os.rename(src=original_file_path, dst=tmp_save_file_path)
            os.rename(src=transformed_file_path, dst=original_file_path)
            transformed_to_old_files[original_file_path] = tmp_save_file_path

        return transformed_to_old_files

    @abstractmethod
    def problematic_kconfig_lines_and_corrections(self) -> list[(str, Callable[[str], str])]:
        """
        Gets a list of patterns of problematic source code lines and corresponding transformations that turn these
        problematic lines into the format found within the Linux kernel.

        :return: A list of tuples, where each tuple associates a regex string describing the problematic line with the corresponding transformation.
        """
        pass

    def collect_make_includes(self) -> List[Dict]:
        """
        Collects the included files and directories on a per-file basis by running make and scanning for compile calls in the output.
        :return: A List of Dicts with the following fields: file_pattern, included_files, included_directories and build_location.
        """
        logger.info("Collecting make includes for desugaring.")

        make_output_file: Path = self.project_root / Path("make_output.sugarlyzer.txt")
        includes_per_file_pattern: List[Dict] = []

        logger.info("Running make.")
        if return_code := self.run_make(make_output_file) == 0:
            # Parse the make output.
            logger.info("Parsing the output produced by make.")
            includes_per_file_pattern = self.parse_make_output(make_output_file)
        else:
            logger.warning(f"Call to make with returned with exit status {return_code}. Make includes will not be "
                           f"utilized during desugaring.")

        return includes_per_file_pattern

    @abstractmethod
    def run_make(self, output_path: Path) -> int:
        """
        Runs make on the subject system writing the generated output including the compile calls to the file described
        by the specified path.
        :param output_path: A Path to the file to which the make output should be written.
        :return: The return code of running make.
        """
        pass

    @abstractmethod
    def parse_make_output(self, make_output_file: Path) -> List[Dict]:
        """
        Scans each line contained in the specified file containing make output and extracts -I and -include options
        passed to the compile calls for c source files.
        :param make_output_file: A Path describing the file containing the output of calling make.
        :return: A List of Dicts with the following fields: file_pattern, included_files, included_directories and build_location.
        """
        pass

    def __collect_system_includes(self) -> List[Path]:
        # Collect locations of system headers.
        standard_include_paths: List[Path] = []

        cmd = ["cc", "-v", "-E", "-xc", "-", "<", "/dev/null", ">", "standard_include_locs.sugarlyzer.txt", "2>&1"]
        ps = subprocess.run(" ".join(str(s) for s in cmd), shell=True,
                            executable='/bin/bash', cwd=self.project_root)
        if ps.returncode == 0:
            with open(self.project_root / Path("standard_include_locs.sugarlyzer.txt"),
                      "r") as standard_includes_output:
                inside_search_list = False
                for line in standard_includes_output:
                    if "#include \"...\" search starts here" in line or "#include <...> search starts here:" in line:
                        inside_search_list = True
                    elif "End of search list." in line:
                        inside_search_list = False
                    elif inside_search_list and os.path.isdir(line.strip()):
                        standard_include_paths.append(Path(line.strip()))

        return standard_include_paths

    def __get_system_macro_header(self) -> Path:
        # Collect default macro definitions and store them in a new header.
        macro_header_path = self.project_root / Path("standard_macro_defs.sugarlyzer.h")

        cmd = ["cc", "-dM", "-E", "-xc", "-", "<", "/dev/null", ">", str(macro_header_path), "2>&1"]
        ps = subprocess.run(" ".join(str(s) for s in cmd), shell=True, executable='/bin/bash', )

        if ps.returncode != 0:
            logger.warning(f"Retrieving system macro definitions probably failed. Exit code was {ps.returncode}")

        return macro_header_path

    def __get_program_macro_header(self) -> Path:
        project_macro_header_path = self.project_root / Path("program_macro_defs.sugarlyzer.h")

        def rewrite_macro(macro_to_rewrite: str, add_config_prefix: bool = False) -> str | None:
            # Check macro format.
            if re.search(pattern=r'(-D)|(-U)\s?\S+(=\S+)?', string=macro_to_rewrite) is None:
                logger.warning(f"The macro {macro_to_rewrite} found within the program specification "
                               f"of {self.name} is malformed.")
                return None

            # Example line: "-D chtype=char"
            operator: str = "#define" if macro_to_rewrite[:2] == "-D" else "#undef"
            macro_split: list[str] = re.split(r'=', macro_to_rewrite[2:].strip())
            macro_name: str = macro_split[0] if not add_config_prefix else f"{self.config_prefix}{macro_split[0]}"
            macro_value: str | None = None if len(macro_split) < 2 else macro_split[1]

            return f"{operator} {macro_name} {macro_value if macro_value is not None else ''}"

        with open(project_macro_header_path, "w") as project_macro_header:
            for spec in self.inc_dirs_and_files:
                # Only macro definitions that apply to all files should be placed in the header.
                if spec.get('file_pattern') is None:
                    # Conventional macro (un)definitions.
                    macros: Iterable[str] = spec.get('macro_definitions')
                    for macro in macros or []:
                        project_macro_header.write(f"{rewrite_macro(macro_to_rewrite=macro)}\n")

                    # Macro (un)definitions relating to configuration variables (i.e., features of the SPL).
                    config_macros: Iterable[str] = spec.get('predefined_config_macros')
                    for config_macro in config_macros or []:
                        project_macro_header.write(
                            f"{rewrite_macro(macro_to_rewrite=config_macro, add_config_prefix=True)}\n")

        return project_macro_header_path

    @staticmethod
    def validate_and_read_program_specification(program_specification_file: Path) -> Dict[str, Any]:
        """
        Given a JSON file that corresponds to a program specification,
        we read it in and validate that it conforms to the schema (resources.programs.program_specification_schema.json)

        :param program_specification_file: The program file to read.
        :return: The JSON representation of the program file. Throws an exception if the file is malformed.
        """
        schema_path: Traversable = importlib.resources.files(
            "resources.sugarlyzer.programs") / "program_specification_schema.json"
        with schema_path.open('r') as schema_file:
            resolver = RefResolver.from_schema(schema := json.load(schema_file))
            validator = Draft7Validator(schema, resolver)

        with open(program_specification_file, 'r') as program_file:
            result = json.load(program_file)
        validator.validate(result)

        return result
