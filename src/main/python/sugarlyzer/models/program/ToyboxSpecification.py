import re
import subprocess
from pathlib import Path
from subprocess import CompletedProcess
from typing import List, Dict, Callable

from python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification, logger
from python.sugarlyzer.util.Kconfig import kconfig_add_quotes_to_source_directive


class ToyboxSpecification(ProgramSpecification):
    """
    Program specification for the subject system Toybox (https://www.landley.net/toybox/)
    """

    @classmethod
    def __kconfig_add_quotes_to_bool_directive(cls, bool_directive: str) -> str:
        """
        Takes in a bool directive of a Kconfig file that does not surround the contained name with quotation marks and
        returns a version where the name is correctly surrounded.

        Example: "bool stat" -> "bool "stat""

        :param bool_directive: The malformed bool directive.

        :return: The bool directive with the name enclosed in quotation marks.
        """

        bool_left_right: list[str] = bool_directive.split("bool")
        indentation: str = bool_left_right[0]
        name: str = bool_left_right[1].strip()

        return f"{indentation}bool \"{name}\"\n"

    def problematic_kconfig_lines_and_corrections(self) -> list[(str, Callable[[str], str])]:
        # Problematic cases:
        # Problem: Missing quotation marks surrounding the file path.
        # Examples: "source generated/Config.probed" and "source generated/Config.in".
        # Problem: Missing quotation marks surrounding the name of the boolean variable.
        # Example: "bool stat"
        return [(r'source [^"\s]*Config\.[^"\s]+', kconfig_add_quotes_to_source_directive),
                (r'bool [^"\s]+', ToyboxSpecification.__kconfig_add_quotes_to_bool_directive)]

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
            logger.warning(f"Running make with command \"{' '.join(str(s) for s in cmd)}\" returned with "
                           f"exit code {process.returncode}")
        else:
            # Collect information from make call into the specified output file.
            cmd: List[str] = ["make", "-i", "V=1", ">", str(output_path), "2>&1"]
            process: CompletedProcess = subprocess.run(" ".join(str(s) for s in cmd),
                                                       shell=True,
                                                       executable='/bin/bash',
                                                       cwd=self.makefile_dir_path)
            if process.returncode != 0:
                logger.warning(f"Running make with command \"{' '.join(str(s) for s in cmd)}\" returned with "
                               f"exit code {process.returncode}")

        return process.returncode

    def parse_make_output(self, make_output_file: Path) -> List[Dict]:
        includes_per_file_pattern: List[Dict] = []
        # Toybox does not change directories when building the source files.
        building_directory: Path = self.makefile_dir_path

        with open(make_output_file, "r") as make_output:
            for line in make_output:
                if line.startswith("cc "):
                    file_name_match = re.search(r' (\S+\.c)(\s+|$)', line)
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
