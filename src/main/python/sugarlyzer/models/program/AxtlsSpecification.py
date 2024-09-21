import re
import subprocess
from pathlib import Path
from typing import List, Dict, Callable

from python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification
from python.sugarlyzer.util.Kconfig import kconfig_add_quotes_to_source_directive


class AxtlsSpecification(ProgramSpecification):
    @classmethod
    def __kconfig_remove_colon_after_help(cls, help_directive: str) -> str:
        """
        Takes in a source help directive where the help keyword is followed by a colon and returns a version where the
        colon is removed.

        Example: "help:"

        :param help_directive: The malformed help directive.
        :return: The help directive with the colon removed.
        """
        help_left_right: list[str] = help_directive.split("help")
        indentation: str = help_left_right[0]

        return f"{indentation}help\n"

    def problematic_kconfig_lines_and_corrections(self) -> list[(str, Callable[[str], str])]:
        return [(r'source [^"\s]*Config\.[^"\s]+', kconfig_add_quotes_to_source_directive),
                (r'help:', AxtlsSpecification.__kconfig_remove_colon_after_help)]

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
