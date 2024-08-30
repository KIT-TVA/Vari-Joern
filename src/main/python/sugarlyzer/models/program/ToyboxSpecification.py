import os
import subprocess
import re
from pathlib import Path
from subprocess import CompletedProcess
from typing import List, Dict

from python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification, logger
from python.sugarlyzer.util.Kconfig import collect_kconfig_files


class ToyboxSpecification(ProgramSpecification):
    def transform_kconfig_into_kextract_format(self):
        kconfig_files: list[Path] = collect_kconfig_files(kconfig_file_name="Config.in",
                                                          root_directory=self.project_root)

        # Problem: Missing quotation marks surrounding the file path.
        # Examples: "source generated/Config.probed" and "source generated/Config.in".
        problematic_pattern_source_directive: str = r'source [^"\s]*Config\.\s+\n'
        problematic_pattern_bool_directive: str = r'bool [^"\s]*\s+\n'

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
            os.rename(src=kconfig_file, dst=str(kconfig_file) + ".sugarlyzer.orig")
            os.rename(src=transformed_file_path, dst=kconfig_file)

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
            cmd: List[str] = ["make", "-i", "V=1"]
            process: CompletedProcess = subprocess.run(" ".join(str(s) for s in cmd),
                                                       shell=True,
                                                       executable='/bin/bash',
                                                       cwd=self.makefile_dir_path)
            if process.returncode != 0:
                logger.warning(f"Running make with command \"{" ".join(str(s) for s in cmd)}\" returned with "
                               f"exit code {process.returncode}")

        return process.returncode

    def parse_make_output(self, make_output_file: Path) -> List[Dict]:
        # TODO Implement
        pass
