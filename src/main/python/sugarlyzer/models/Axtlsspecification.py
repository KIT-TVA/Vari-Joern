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

        # Collect information from make call.
        make_info = []

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
                            make_entry = {'file_pattern': file_name.replace('.', r'\.') + '$',
                                          'included_files': re.findall(r'-include ?\S+', line),
                                          'included_directories': re.findall(r'-I ?\S+', line),
                                          'build_location': current_building_directory}
                            make_info.append(make_entry)

        print("#1:")
        print(make_info)

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

        print("#2:")
        print(standard_include_paths)

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
                        standard_macro_defs.append({
                            'macro_name': define_split[1],
                            'value': define_split[2].strip()
                        })

        print("#3:")
        print(standard_macro_defs)

        return file_includes, dir_includes, macros
