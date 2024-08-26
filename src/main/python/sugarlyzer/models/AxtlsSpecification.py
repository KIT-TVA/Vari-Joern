import os
import re
import subprocess
from pathlib import Path
from typing import List, Dict

from python.sugarlyzer.models.ProgramSpecification import ProgramSpecification


class AxtlsSpecification(ProgramSpecification):
    def transform_kconfig_into_kextract_format(self):
        kconfig_files: list[Path] = []
        for dir_path, dir_names, file_names in os.walk(self.project_root):
            for file_name in file_names:
                if file_name == "Config.in":
                    kconfig_files.append(Path(dir_path) / Path(file_name))

        # Problem: Missing quotation marks surrounding the file path.
        problematic_pattern_source_directive: str = r'source [^"\s]*Config\.in'
        # Problem: Colon after help.
        problematic_pattern_help_directive: str = r'help:'

        # Go through the Kconfig files and adjust problematic syntax.
        for kconfig_file in kconfig_files:
            with open(kconfig_file, "r") as input_file, open(Path(str(kconfig_file) + ".tmp"), "w") as output_file:
                for line in input_file:
                    if re.fullmatch(problematic_pattern_source_directive, line.strip()):
                        source_left_right: list[str] = line.split("source")
                        indentation: str = source_left_right[0]
                        included_file: str = source_left_right[1].strip()

                        output_file.write(f"{indentation}source \"{included_file}\"\n")
                    elif re.fullmatch(problematic_pattern_help_directive, line.strip()):
                        help_left_right: list[str] = line.split("help")
                        indentation: str = help_left_right[0]
                        output_file.write(f"{indentation}help\n")
                    else:
                        output_file.write(f"{line}")

        # TODO Replace old Kconfig file with transformed one but retain the old one.

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
