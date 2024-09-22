import os
import re
import subprocess
from subprocess import CompletedProcess
from pathlib import Path
from typing import List, Dict, Callable

from python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification, logger
from python.sugarlyzer.util.Kconfig import kconfig_add_quotes_to_source_directive


class BusyboxSpecification(ProgramSpecification):
    def problematic_kconfig_lines_and_corrections(self) -> list[(str, Callable[[str], str])]:
        return [(r'source [^"\s]*Config\.[^"\s]+', kconfig_add_quotes_to_source_directive)]

    def parse_make_output(self, make_output_file: Path) -> List[Dict]:
        includes_per_file_pattern: List[Dict] = []
        # BusyBox does not change directories when building the source files.
        building_directory: Path = self.makefile_dir_path

        with open(make_output_file, "r") as make_output:
            for line in make_output:
                line: str = line.strip()

                if line.startswith("gcc "):
                    file_name_match = re.search(r' (\S+\.c)(\s+|$)', line) # TODO Fix bug in this line and in the other program specifications.
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

    def run_make(self, output_path: Path) -> int:
        # Clean output of potential previous make call.
        cmd: List[str] = ["make", "distclean"]
        logger.info("Cleaning the repository...")
        subprocess.run(" ".join(str(s) for s in cmd),
                       shell=True,
                       executable='/bin/bash',
                       cwd=self.makefile_dir_path,
                       stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE)

        # Configure busybox with defconfig.
        # Description of this target: "New config with default answer to all options. This is the maximum sane configuration."
        cmd: List[str] = ["make", "-i", self.make_target]
        logger.info(f"Establish {self.make_target} configuration...")
        process: CompletedProcess = subprocess.run(" ".join(str(s) for s in cmd),
                                                              shell=True,
                                                              executable='/bin/bash',
                                                              cwd=self.makefile_dir_path,
                                                              stdout=subprocess.PIPE,
                                                              stderr=subprocess.PIPE)

        if process.returncode != 0:
            logger.warning(f"Running make with command \"{' '.join(str(s) for s in cmd)}\" returned with "
                           f"exit code {process.returncode}")
        else:
            # Collect information from make call into the specified output file.
            cmd: List[str] = ["make", "-i", f"-j{os.cpu_count() or 1}", "V=1", "2>&1", "|", "tee", str(output_path)]
            logger.info("Building the configuration...")
            process: CompletedProcess = subprocess.run(" ".join(str(s) for s in cmd),
                                                                  shell=True,
                                                                  executable='/bin/bash',
                                                                  cwd=self.makefile_dir_path)
            if process.returncode != 0:
                logger.warning(f"Running make with command \"{' '.join(str(s) for s in cmd)}\" returned with "
                               f"exit code {process.returncode}")

        return process.returncode