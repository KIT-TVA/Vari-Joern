import itertools
import logging
import subprocess
import tempfile
from pathlib import Path
from typing import Iterable

from python.sugarlyzer.analyses.AbstractTool import AbstractTool
import os
from python.sugarlyzer.readers.PhasarReader import PhasarReader
from python.sugarlyzer.util.Subprocessing import parse_bash_time

logger = logging.getLogger(__name__)


class Phasar(AbstractTool):

    def __init__(self, intermediary_results_path: Path):
        super().__init__(PhasarReader(), name='phasar', keep_mem=True, make_main=True, remove_errors=False,
                         intermediary_results_path=intermediary_results_path)

    def analyze(self, desugared_source_file: Path,
                included_dirs: Iterable[Path] = None,
                included_files: Iterable[Path] = None,
                command_line_defs: Iterable[str] = None) -> Iterable[Path]:
        if included_files is None:
            included_files = []
        if included_dirs is None:
            included_dirs = []
        if command_line_defs is None:
            command_line_defs = []
        output_location = self.results_dir
        # create ll file
        llFile = os.path.join(output_location, str(desugared_source_file)[:-2] + '.ll')
        cmd = ['/usr/bin/time', '-v', 'clang-12', '-emit-llvm', '-S', '-fno-discard-value-names', '-c', '-g',
               *list(itertools.chain(*zip(itertools.cycle(["-I"]), included_dirs))),
               *list(itertools.chain(*zip(itertools.cycle(["--include"]), included_files))),
               *command_line_defs,
               "-nostdinc", "-c", desugared_source_file.absolute(), '-o', llFile]
        logger.info(f"Running cmd {cmd}")
        ps = subprocess.run(" ".join(str(s) for s in cmd), shell=True, executable="/bin/bash", capture_output=True,
                            text=True)
        if (ps.returncode != 0) or ("error" in ps.stdout.lower()):
            logger.warning(f"Running clang on file {str(desugared_source_file)} potentially failed.")
            logger.warning(ps.stderr)

        times = " ".join(ps.stderr.split('\n')[-30:])
        try:
            usr_time, sys_time, max_memory = parse_bash_time(times)
            logger.info(f"CPU time to compile {desugared_source_file} was {usr_time + sys_time}s")
            logger.info(f"Max memory to compile {desugared_source_file} was {max_memory}kb")
        except Exception as ve:
            logger.exception("Could not parse time in string " + times)

        # run phasar on ll
        cmd = ['/usr/bin/time', '-v', '/phasar/build/tools/phasar-llvm/phasar-llvm', '-D', 'IFDSUninitializedVariables',
               '-m', llFile, '-O', output_location]
        logger.info(f"Running cmd {cmd}")
        ps = subprocess.run(" ".join(str(s) for s in cmd), capture_output=True, text=True, shell=True,
                            executable="/bin/bash")
        if (ps.returncode != 0) or ("error" in ps.stdout.lower()):
            logger.warning(f"Running phasar on file {str(desugared_source_file)} potentially failed.")
            logger.warning(ps.stderr)

        if ps.returncode == 0:
            try:
                times = "\n".join(ps.stderr.split("\n")[-30:])
                usr_time, sys_time, max_memory = parse_bash_time(times)
                logger.info(f"CPU time to analyze {desugared_source_file} was {usr_time + sys_time}s")
                logger.info(f"Max memory to analyze {desugared_source_file} was {max_memory}kb")
            except Exception as ve:
                logger.exception("Could not parse time in string " + times)

        for root, dirs, files in os.walk(output_location):
            for f in files:
                if f.startswith("psr-report") and f.endswith(".txt"):
                    yield Path(root) / f
