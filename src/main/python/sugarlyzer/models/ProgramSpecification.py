import functools
import importlib.resources
import logging
import os
import re
import subprocess
from abc import ABC, abstractmethod
from dataclasses import dataclass
from io import StringIO
from pathlib import Path
from typing import List, Iterable, Optional, Dict, Tuple

from python.sugarlyzer.util.MacroDiscoveryPreprocessor import MacroDiscoveryPreprocessor

logger = logging.getLogger(__name__)


class ProgramSpecification(ABC):

    def __init__(self,
                 name: str,
                 build_script: str,
                 project_root: str,
                 config_prefix: str = None,
                 whitelist: str = None,
                 kgen_map: str = None,
                 remove_errors: bool = False,
                 source_dir: Optional[str] = None,
                 make_root: Optional[str] = None,
                 included_files_and_directories: Optional[Iterable[Dict]] = None,
                 sample_dir: Optional[str] = None,
                 makefile_location: str = None,
                 oldconfig_location: Optional[str] = None
                 ):
        self.name = name
        self.remove_errors = remove_errors
        self.config_prefix = config_prefix
        self.whitelist = whitelist
        self.kgen_map = kgen_map
        self.no_std_libs = True
        self.__project_root = project_root
        self.__source_dir = source_dir
        self.__make_root = make_root
        self.__build_script = build_script
        self.__source_location = source_dir
        self.inc_dirs_and_files = [] if included_files_and_directories is None else included_files_and_directories
        self.__sample_directory = sample_dir
        self.__makefile_location = makefile_location
        self.__search_context = "/"
        self.__oldconfig_location = "config/.config" if oldconfig_location is None else oldconfig_location

        self.__make_includes = self.collect_make_includes()
        self.__system_includes = self.__collect_system_includes()
        self.__system_macros = self.__get_system_macro_header()
        self.__program_macros = self.__get_program_macro_header()

    @property
    def oldconfig_location(self):
        return self.try_resolve_path(self.__oldconfig_location, self.make_root)

    @property
    def search_context(self):
        p = Path(self.__search_context)
        if not p.exists():
            raise RuntimeError(f"Search context {p} does not exist.")
        else:
            return p

    @property
    def project_root(self):
        return self.try_resolve_path(self.__project_root, self.source_directory)

    @property
    def source_directory(self):
        return self.try_resolve_path(self.__source_dir,
                                     self.search_context) if self.__source_dir is not None else self.project_root

    @property
    def sample_directory(self):
        return self.try_resolve_path(self.__sample_directory, importlib.resources.path('resources.programs', ''))

    @property
    def make_root(self):
        return self.try_resolve_path(self.__make_root,
                                     self.search_context) if self.__make_root is not None else self.project_root

    @property
    def build_script(self):
        return self.try_resolve_path(self.__build_script, importlib.resources.path('resources.programs', ''))

    def get_source_files(self) -> Iterable[Path]:
        """
        :return: All .c or .i files that are in the program's source locations but have not been produced by earlier
        desugaring.
        """
        for root, dirs, files in os.walk(self.source_directory):
            for f in files:
                if (f.endswith(".c") or f.endswith(".i")) and not f.endswith(".desugared.c"):
                    yield Path(root) / f

    def clean_intermediary_results(self):
        for root, dirs, files in os.walk(self.source_directory):
            for f in files:
                if ".sugarlyzer." in f:
                    file_to_delete = Path(root) / f
                    try:
                        file_to_delete.unlink()
                    except FileNotFoundError:
                        logger.warning(f"Tried to clean up {file_to_delete} but could not find file.")
                    except PermissionError:
                        logger.warning(f"Tried to clean up {file_to_delete} but did not have sufficient permissions.")
                    except Exception as e:
                        logger.exception(
                            f"Tried to clean up {file_to_delete} but encountered unexpected exception: {e}")

    def inc_files_and_dirs_for_file(self, file: Path) -> Tuple[Iterable[Path], Iterable[Path], Iterable[str]]:
        """
        Iterates through the program.json's get_recommended_space field,
        returning the first match. See program_schema.json for more info.
        :param file: The source file to search for.
        :return: included_files, included_directories for the first object in
        get_recommended_space with a regular expression that matches the **absolute** file name.
        """

        # Collect includes and macros from make call.
        inc_dirs_make, inc_files_make, cmd_decs_make = self.process_inc_dirs_and_files(
            inc_dirs_and_files=self.__make_includes,
            file=file,
            include_only_file_specific_macros=False)
        # Collect manually defined includes and macros from program.json.
        inc_dirs_manual, inc_files_manual, cmd_decs_manual = self.process_inc_dirs_and_files(
            inc_dirs_and_files=self.inc_dirs_and_files,
            file=file,
            include_only_file_specific_macros=True)

        inc_dirs = []
        inc_dirs.extend(self.__system_includes) # Add system header paths.
        inc_dirs.extend(inc_dirs_make)
        inc_dirs.extend(inc_dirs_manual)

        inc_files = []
        inc_files.extend(inc_files_make)
        inc_files.extend(inc_files_manual)
        # System and program macros are stored in their own headers that should be included via -include.
        inc_files.append(self.__system_macros)
        inc_files.append(self.__program_macros)

        cmd_decs = []
        cmd_decs.extend(cmd_decs_make)
        cmd_decs.extend(cmd_decs_manual)

        return inc_files, inc_dirs, cmd_decs

    def process_inc_dirs_and_files(self, inc_dirs_and_files: Iterable[dict], file: Path,
                                   include_only_file_specific_macros: bool) -> Tuple[
        List[Path], List[Path], List[str]]:
        inc_dirs, inc_files, cmd_decs = [], [], []
        for spec in inc_dirs_and_files:
            # Note the difference between s[a] and s.get(a) is the former will
            #  raise an exception if a is not in s, while s.get will return None.
            if spec.get('file_pattern') is None or re.search(spec.get('file_pattern'), str(file.absolute())):
                if (rt := spec.get('relative_to')) is not None:
                    relative_to = Path(rt)
                else:
                    relative_to = self.project_root
                if 'included_files' in spec.keys():
                    inc_files.extend(self.try_resolve_path(Path(p), relative_to) for p in spec['included_files'])
                if 'included_directories' in spec.keys():
                    inc_dirs.extend(self.try_resolve_path(Path(p), relative_to) for p in spec['included_directories'])
                if 'macro_definitions' in spec.keys():
                    # Deal only with macros specified for the specific file. Program-specific macros are handled
                    # separately.
                    if include_only_file_specific_macros and spec.get('file_pattern') is not None:
                        cmd_decs.extend(spec['macro_definitions'])

        return inc_files, inc_dirs, cmd_decs

    def download(self) -> int:
        """
        Runs the script to obtain the program's source code.
        :return: The return code
        """
        ps = subprocess.run(self.build_script)
        return ps.returncode

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

    @abstractmethod
    def collect_make_includes(self) -> List[Dict]:
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

        with open(project_macro_header_path, "w") as project_macro_header:
            for spec in self.inc_dirs_and_files:
                if spec.get('file_pattern') is None:
                    macros: Iterable[str] = spec['macro_definitions']
                    for macro in macros:
                        macro_split = re.split(r'[ =]', macro)
                        operator = "#undef" if macro_split[0] == "-U" else "#define"
                        name = macro_split[1]
                        value = None if len(macro_split) < 3 else macro_split[2]
                        project_macro_header.write(f"{operator} {name} {value if value is not None else ''}")

        return project_macro_header_path
