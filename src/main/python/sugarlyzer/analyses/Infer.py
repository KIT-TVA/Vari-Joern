import itertools
import logging
import subprocess
import tempfile
from pathlib import Path
from typing import Iterable

from python.sugarlyzer.analyses.AbstractTool import AbstractTool
import os

from python.sugarlyzer.readers.InferReader import InferReader
from python.sugarlyzer.util.Subprocessing import parse_bash_time

logger = logging.getLogger(__name__)


class Infer(AbstractTool):
    """
    Tool class adding support for Infer.
    """

    def __init__(self, intermediary_results_path: Path):
        super().__init__(reader=InferReader(),
                         name='infer',
                         make_main=True,
                         keep_mem=True,
                         remove_errors=True,
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
        cmd = ["/usr/bin/time", "-v", "infer", "--pulse-only", '-o', output_location, '--', "clang",
               *list(itertools.chain(*zip(itertools.cycle(["-I"]), included_dirs))),
               *list(itertools.chain(*zip(itertools.cycle(["--include"]), included_files))),
               *command_line_defs,
               "-nostdinc", "-c", desugared_source_file.absolute()]
        logger.debug(f"Running cmd {cmd}")
        ps = subprocess.run(" ".join([str(s) for s in cmd]), text=True, shell=True, capture_output=True,
                            executable='/bin/bash')
        if (ps.returncode != 0):
            logger.warning(
                f"Running infer on file {str(desugared_source_file)} with command {' '.join(str(s) for s in cmd)} potentially failed (exit code {ps.returncode}).")
            logger.warning(ps.stdout)
        if ps.returncode == 0:
            try:
                times = "\n".join(ps.stderr.split("\n")[-30:])
                usr_time, sys_time, max_memory = parse_bash_time(times)
                logger.info(f"CPU time to analyze {desugared_source_file} was {usr_time + sys_time}s")
                logger.info(f"Max memory to analyze {desugared_source_file} was {max_memory}kb")
            except Exception as ve:
                logger.exception("Could not parse time in string " + times)

        report = os.path.join(output_location, 'report.json')
        yield report
