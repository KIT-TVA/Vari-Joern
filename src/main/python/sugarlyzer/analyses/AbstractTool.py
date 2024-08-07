import functools
import logging
import operator
import tempfile
import time
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Iterable, List

from python.sugarlyzer.models.Alarm import Alarm
from python.sugarlyzer.readers.AbstractReader import AbstractReader

logger = logging.getLogger(__name__)


class AbstractTool(ABC):

    def __init__(self, reader: AbstractReader, name: str, keep_mem: bool, make_main: bool, remove_errors: bool,
                 intermediary_results_path: Path, desugaring_function_whitelist: List[str] = None):
        self.reader = reader
        self.keep_mem = keep_mem,
        self.make_main = make_main
        self.remove_errors = remove_errors
        self.name = name
        self.results_dir = intermediary_results_path

        self.desugaring_function_whitelist = [] if desugaring_function_whitelist is None \
            else desugaring_function_whitelist

    def analyze_and_read(self, source_file: Path, command_line_defs: Iterable[str] = None,
                         included_dirs: Iterable[Path] = None, included_files: Iterable[Path] = None,
                         recommended_space=None) -> Iterable[Alarm]:
        """
        Analyzes a desugared .c file, and returns the alarms generated.
        :param no_std_libs:
        :param includes: Includes to specify to the tool (right now, just used to pass in macro definitions.
        :param source_file: The file to analyze.
        :return: A collection of alarms.
        """
        if recommended_space is not None:
            with tempfile.NamedTemporaryFile('w', delete=False) as uds:
                uds.write(recommended_space)
                if included_files is None:
                    included_files = []
                included_files.append(uds.name)

        start_time = time.monotonic()
        alarms = \
            functools.reduce(operator.iconcat, [self.reader.read_output(f) for f in
                                                self.analyze(file=source_file,
                                                             command_line_defs=command_line_defs,
                                                             included_dirs=included_dirs,
                                                             included_files=included_files)], [])
        total_time = time.monotonic() - start_time
        logger.info(f"Analyzing file {source_file} took {total_time}s")
        for a in alarms:
            a.input_file = source_file
            a.analysis_time = total_time

        try:
            uds.close()
        except UnboundLocalError:
            pass

        return alarms

    @abstractmethod
    def analyze(self, file: Path,
                included_dirs: Iterable[Path] = None,
                included_files: Iterable[Path] = None,
                command_line_defs: Iterable[str] = None) -> Iterable[Path]:
        """
        Analyzes a file and returns the location of its output.
        :param file: The file to run analysis on.
        :return: The output file produced by running the tool.
        """
        pass
