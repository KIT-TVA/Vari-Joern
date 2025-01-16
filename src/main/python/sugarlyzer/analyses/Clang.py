import itertools
import logging
import os
import subprocess
from pathlib import Path
from typing import Iterable

from python.sugarlyzer.analyses.AbstractTool import AbstractTool
from python.sugarlyzer.readers.ClangReader import ClangReader
from python.sugarlyzer.util.Subprocessing import parse_bash_time

logger = logging.getLogger(__name__)


class Clang(AbstractTool):
    """
    Tool class adding support for Clang Static Analyzer.
    """

    def __init__(self, intermediary_results_path: Path):
        super().__init__(reader=ClangReader(),
                         name='clang',
                         keep_mem=True,
                         make_main=True,
                         remove_errors=False,
                         intermediary_results_path=intermediary_results_path)

    def analyze(self, desugared_source_file: Path,
                included_dirs: Iterable[Path] = None,
                included_files: Iterable[Path] = None,
                command_line_defs: Iterable[str] = None,
                **kwargs):
        if command_line_defs is None:
            command_line_defs = []
        if included_dirs is None:
            included_dirs = []
        if included_files is None:
            included_files = []

        output_location = self.results_dir
        cmd = ["/usr/bin/time", "-v", "clang-11", '--analyze', "-Xanalyzer", "-analyzer-output=text",
               *list(itertools.chain(*zip(itertools.cycle(["-I"]), included_dirs))),
               *list(itertools.chain(*zip(itertools.cycle(["--include"]), included_files))),
               *command_line_defs,
               '-nostdinc',
               "-c", desugared_source_file.absolute()]
        logger.info(f"Running cmd {' '.join(str(s) for s in cmd)}")

        ps = subprocess.run(" ".join(str(s) for s in cmd), capture_output=True, shell=True, text=True,
                            executable="/bin/bash")
        if ps.returncode == 0:
            try:
                times = "\n".join(ps.stderr.split("\n")[-30:])
                usr_time, sys_time, max_memory = parse_bash_time(times)
                logger.info(f"CPU time to analyze {desugared_source_file} was {usr_time + sys_time}s")
                logger.info(f"Max memory to analyze {desugared_source_file} was {max_memory}kb")
            except Exception as ve:
                logger.exception("Could not parse time in string " + times)

        if (ps.returncode != 0) or ("error" in ps.stdout.lower()):
            logger.warning(f"Running clang on file {str(desugared_source_file)} potentially failed.")
            logger.warning(ps.stdout)

        with open(output_location + '/report.report', 'w') as o:
            o.write(ps.stderr)

        lines = ps.stdout.split("\n")
        logger.critical(f"Analysis time: {lines[-1]}")

        for root, dirs, files in os.walk(output_location):
            for fil in files:
                if fil.startswith("report") and fil.endswith(".report"):
                    r = Path(root) / fil
                    logger.debug(f"Yielding report file {r}")
                    yield Path(root) / fil
