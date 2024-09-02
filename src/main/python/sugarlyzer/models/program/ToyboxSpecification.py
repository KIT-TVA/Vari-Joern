import os
import subprocess
import re
from pathlib import Path
from subprocess import CompletedProcess
from typing import List, Dict

from python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification, logger
from python.sugarlyzer.util.Kconfig import collect_kconfig_files


class ToyboxSpecification(ProgramSpecification):
    def transform_kconfig_into_kextract_format(self) -> dict[str, str]:
        transformed_to_old_files: dict[str, str] = {}
        kconfig_files: list[Path] = collect_kconfig_files(kconfig_file_names=["Config.in", "Config.probed"],
                                                          root_directory=self.project_root)

        # Problem: Missing quotation marks surrounding the file path.
        # Examples: "source generated/Config.probed" and "source generated/Config.in".
        problematic_pattern_source_directive: str = r'source [^"\s]*Config\.[^"\s]+'
        # Problem: Missing quotation marks surrounding the name of the boolean variable.
        # Example: "bool stat"
        problematic_pattern_bool_directive: str = r'bool [^"\s]+'

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
                    elif re.fullmatch(problematic_pattern_bool_directive, line.strip()):
                        bool_left_right: list[str] = line.split("bool")
                        indentation: str = bool_left_right[0]
                        name: str = bool_left_right[1].strip()

                        output_file.write(f"{indentation}bool \"{name}\"\n")
                    else:
                        output_file.write(f"{line}")

            # Replace old Kconfig file with transformed one but retain the old one to restore it after the analysis.
            original_file_path: str = str(kconfig_file)
            tmp_save_file_path:str = str(kconfig_file) + ".sugarlyzer.orig"
            os.rename(src=original_file_path, dst=tmp_save_file_path)
            os.rename(src=transformed_file_path, dst=original_file_path)
            transformed_to_old_files[original_file_path] = tmp_save_file_path

        return transformed_to_old_files

    def run_make(self, output_path: Path) -> int:
        # Clean output of potential previous make call.
        cmd: List[str] = ["make", "distclean"]
        subprocess.run(" ".join(str(s) for s in cmd),
                       shell=True,
                       executable='/bin/bash',
                       cwd=self.makefile_dir_path,
                       stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)

        # Configure toybox with defconfig.
        # Description of this target: "New config with default answer to all options. This is the maximum sane configuration."
        cmd: List[str] = ["make", "-i", self.make_target]
        process: CompletedProcess = subprocess.run(" ".join(str(s) for s in cmd),
                                                   shell=True,
                                                   executable='/bin/bash',
                                                   cwd=self.makefile_dir_path,
                                                   stdout=subprocess.DEVNULL,
                                                   stderr=subprocess.DEVNULL)

        if process.returncode != 0:
            logger.warning(f"Running make with command \"{" ".join(str(s) for s in cmd)}\" returned with "
                           f"exit code {process.returncode}")
        else:
            # Collect information from make call into the specified output file.
            cmd: List[str] = ["make", "-i", "V=1", ">", str(output_path), "2>&1"]
            process: CompletedProcess = subprocess.run(" ".join(str(s) for s in cmd),
                                                       shell=True,
                                                       executable='/bin/bash',
                                                       cwd=self.makefile_dir_path)
            if process.returncode != 0:
                logger.warning(f"Running make with command \"{" ".join(str(s) for s in cmd)}\" returned with "
                               f"exit code {process.returncode}")

        return process.returncode

    def parse_make_output(self, make_output_file: Path) -> List[Dict]:
        includes_per_file_pattern: List[Dict] = []
        # Toybox does not change directories when building the source files.
        building_directory: Path = self.makefile_dir_path

        with open(make_output_file, "r") as make_output:
            for line in make_output:
                if line.startswith("cc "):
                    file_name_match = re.search(r' (\S+\.c)', line)
                    if file_name_match is not None:
                        relative_file_path = file_name_match.group(1)

                        # Resolve full paths of the included files.
                        included_files = []
                        for included_file in re.findall(r'-include ?\S+', line):
                            included_file = included_file.lstrip("-include").strip()
                            full_file_path = (Path(building_directory) / Path(included_file)).resolve()
                            included_files.append(full_file_path)

                        # Resolve full paths of the included dirs.
                        included_dirs = []
                        for included_dir in re.findall(r'-I ?\S+', line):
                            included_dir = included_dir.lstrip("-I").strip()
                            full_dir_path = (Path(building_directory) / Path(included_dir)).resolve()
                            included_dirs.append(full_dir_path)

                        make_entry = {'file_pattern': relative_file_path.replace('.', r'\.') + '$',
                                      'included_files': included_files,
                                      'included_directories': included_dirs,
                                      'build_location': building_directory}
                        includes_per_file_pattern.append(make_entry)

        return includes_per_file_pattern
