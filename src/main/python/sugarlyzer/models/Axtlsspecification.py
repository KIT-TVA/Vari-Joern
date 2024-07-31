import os.path
import re
import subprocess
from pathlib import Path
from typing import List

from python.sugarlyzer.models.ProgramSpecification import ProgramSpecification


class Axtlsspecification(ProgramSpecification):
    def determine_includes_and_macros(self) -> (List[Path], List[Path], List[str]):
        file_includes = []
        dir_includes = []
        macros = []

        # TODO Refactor and try to replace program.json with the dynamically resolved data.

        included_files_and_directories = []

        # Collect information from make call.
        cmd = ["make", "linuxconf", ">", "make_output.sugarlyzer.txt", "2>&1"]
        ps = subprocess.run(" ".join(str(s) for s in cmd), shell=True,
                            executable='/bin/bash', cwd=self.project_root)
        if ps.returncode == 0:
            with open(self.project_root / Path("make_output.sugarlyzer.txt"), "r") as make_output:
                current_building_directory = ""
                for line in make_output:
                    if "Entering directory" in line:
                        current_building_directory = line.split("'")[1]
                    elif line.startswith("cc "):
                        file_name_match = re.search(r' (\S+\.c)', line)
                        if file_name_match is not None:
                            file_name = file_name_match.group(1)

                            # Resolve full paths of the included files.
                            included_files = []
                            for included_file in re.findall(r'-include ?\S+', line):
                                included_file = included_file.lstrip("-include").strip()
                                full_file_path = (Path(self.project_root) / Path(current_building_directory)
                                                  / Path(included_file)).resolve()
                                included_files.append(full_file_path)

                            # Resolve full paths of the included dirs.
                            included_dirs = []
                            for included_dir in re.findall(r'-I ?\S+', line):
                                included_dir = included_dir.lstrip("-I").strip()
                                full_dir_path = (Path(self.project_root) / Path(current_building_directory)
                                                 / Path(included_dir)).resolve()
                                included_dirs.append(full_dir_path)

                            make_entry = {'file_pattern': file_name.replace('.', r'\.') + '$',
                                          'included_files': included_files,
                                          'included_directories': included_dirs,
                                          'build_location': current_building_directory}
                            included_files_and_directories.append(make_entry)

        # Collect locations of system headers.
        standard_include_paths = []

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

        # Collect default macro definitions.
        standard_macro_defs = []

        cmd = ["cc", "-dM", "-E", "-xc", "-", "<", "/dev/null", ">", "standard_macro_defs.sugarlyzer.txt", "2>&1"]
        ps = subprocess.run(" ".join(str(s) for s in cmd), shell=True,
                            executable='/bin/bash', cwd=self.project_root)
        if ps.returncode == 0:
            with open(self.project_root / Path("standard_macro_defs.sugarlyzer.txt"), "r") as standard_includes_output:
                for line in standard_includes_output:
                    if line.startswith("#define"):
                        define_split = line.split(" ")
                        standard_macro_defs.append(f"-D {define_split[1]}={define_split[2].strip()}")

        # Add file-independent includes and macros to included_files_and_directories.
        included_files_and_directories.append({'included_directories': standard_include_paths,
                                               'macro_definitions': standard_macro_defs})

        print(included_files_and_directories)

        return file_includes, dir_includes, macros
