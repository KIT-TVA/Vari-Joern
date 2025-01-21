import os
import subprocess
from pathlib import Path
from typing import List, Dict, Callable

from main.python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification, logger


class FiascoSpecification(ProgramSpecification):
    def problematic_kconfig_lines_and_corrections(self) -> list[(str, Callable[[str], str])]:
        # TODO
        pass

    def parse_make_output(self, make_output_file: Path) -> List[Dict]:
        includes_per_file_pattern: List[Dict] = []
        # Fiasco does use absolute paths and not relative ones.
        building_directory: Path = self.makefile_dir_path

        with open(make_output_file, "r") as make_output:
            compile_call: bool = False
            file_pattern: str | None = None

            for line in make_output:
                line: str = line.strip()

                if line.startswith("x86_64-linux-gnu"):
                    compile_call = True

                    # file_name_match = re.search(r' (\S+\.c)(\s+|$)', line)
                    # if file_name_match is not None:
                    #     relative_file_path = file_name_match.group(1)
                    #
                    #     # Resolve full paths of the included files.
                    #     included_files = []
                    #     for included_file in re.findall(r'-include ?\S+', line):
                    #         included_file = included_file.lstrip("-include").strip()
                    #         full_file_path = (Path(building_directory) / Path(included_file)).resolve()
                    #         included_files.append(full_file_path)
                    #
                    #     # Resolve full paths of the included dirs.
                    #     included_dirs = []
                    #     for included_dir in re.findall(r'-I ?\S+', line):
                    #         included_dir = included_dir.lstrip("-I").strip()
                    #         full_dir_path = (Path(building_directory) / Path(included_dir)).resolve()
                    #         included_dirs.append(full_dir_path)
                    #
                    #     make_entry = {'file_pattern': relative_file_path.replace('.', r'\.') + '$',
                    #                   'included_files': included_files,
                    #                   'included_directories': included_dirs,
                    #                   'build_location': building_directory}
                    #     includes_per_file_pattern.append(make_entry)

                if not line or not line.endswith("\\"):
                    compile_call = False

        return includes_per_file_pattern

    def run_make(self, output_path: Path) -> int:
        # Clean output of potential previous make call.
        cmd: List[str] = ["make", "clean", "&&", "make", "purge", "&&", "rm", "-rf", "vari-joern-build"]
        logger.info(f"Cleaning the fiasco repository. Executing: {' '.join(str(s) for s in cmd)}")
        subprocess.run(" ".join(str(s) for s in cmd),
                       shell=True,
                       executable='/bin/bash',
                       cwd=self.makefile_dir_path,
                       stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE)

        # Configure Fiasco with the default config.
        cmd: List[str] = ["make", "BUILDDIR=\"vari-joern-build\""]
        logger.info(f"Establish default configuration of fiasco...")
        process: subprocess.CompletedProcess = subprocess.run(" ".join(str(s) for s in cmd),
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
            logger.info("Building Fiascos default configuration...")
            cmd: List[str] = ["make", "-i", f"-j{os.cpu_count() or 1}", "V=1", "2>&1", "|", "tee", str(output_path)]
            process: subprocess.CompletedProcess = subprocess.run(" ".join(str(s) for s in cmd),
                                                                  shell=True,
                                                                  executable='/bin/bash',
                                                                  cwd=f"{self.makefile_dir_path}{os.path.sep}vari-joern-build")
            if process.returncode != 0:
                logger.warning(f"Running make with command \"{' '.join(str(s) for s in cmd)}\" returned with "
                               f"exit code {process.returncode}")

        return process.returncode
