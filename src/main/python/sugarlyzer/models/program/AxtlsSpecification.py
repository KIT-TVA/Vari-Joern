import os
import re
import subprocess
from pathlib import Path
from typing import List, Dict

from python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification
from python.sugarlyzer.util.Kconfig import collect_kconfig_files


class AxtlsSpecification(ProgramSpecification):
    def transform_kconfig_into_kextract_format(self):
        kconfig_files: list[Path] = collect_kconfig_files(kconfig_file_names=["Config.in"],
                                                          root_directory=self.project_root)

        # Problem: Missing quotation marks surrounding the file path.
        problematic_pattern_source_directive: str = r'source [^"\s]*Config\.in'
        # Problem: Colon after help.
        problematic_pattern_help_directive: str = r'help:'

        # Go through the Kconfig files and adjust problematic syntax.
        for kconfig_file in kconfig_files:
            transformed_file_path: str = str(kconfig_file) + ".tmp"
            with open(kconfig_file, "r") as input_file, open(transformed_file_path, "w") as output_file:
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

            # Replace old Kconfig file with transformed one but retain the old one to restore it after the analysis.
            os.rename(src=kconfig_file, dst=str(kconfig_file) + ".sugarlyzer.orig")
            os.rename(src=transformed_file_path, dst=kconfig_file)

    def run_make(self, output_path: Path):
        # Clean output of potential previous make call.
        cmd = ["make", "clean"]
        subprocess.run(" ".join(str(s) for s in cmd),
                       shell=True,
                       executable='/bin/bash',
                       cwd=self.makefile_dir_path,
                       stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)

        # Collect information from make call into dedicated file.
        cmd = ["make", "-i", self.make_target, ">", str(output_path), "2>&1"]
        return subprocess.run(" ".join(str(s) for s in cmd),
                              shell=True,
                              executable='/bin/bash',
                              cwd=self.makefile_dir_path).returncode

    def parse_make_output(self, make_output_file: Path) -> List[Dict]:
        includes_per_file_pattern: List[Dict] = []

        with open(make_output_file, "r") as make_output:
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
