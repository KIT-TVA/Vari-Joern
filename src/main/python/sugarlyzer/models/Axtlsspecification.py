import re
import subprocess
from pathlib import Path
from typing import List, Dict

from python.sugarlyzer.models.ProgramSpecification import ProgramSpecification


class Axtlsspecification(ProgramSpecification):
    def collect_make_includes(self) -> List[Dict]:
        # TODO Extract make calls into their own function in the base class.
        # Clean output of potential previous make call.
        cmd = ["make", "clean"]
        subprocess.run(" ".join(str(s) for s in cmd),
                       shell=True,
                       executable='/bin/bash',
                       cwd=self.makefile_dir_path,
                       stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)

        # Collect information from make call.
        includes_per_file_pattern: List[Dict] = []
        cmd = ["make", "-i", self.make_target if self.make_target is not None else "", ">",
               str(self.project_root / Path("make_output.sugarlyzer.txt")), "2>&1"]
        ps = subprocess.run(" ".join(str(s) for s in cmd),
                            shell=True,
                            executable='/bin/bash',
                            cwd=self.makefile_dir_path)
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
                            includes_per_file_pattern.append(make_entry)

        return includes_per_file_pattern
