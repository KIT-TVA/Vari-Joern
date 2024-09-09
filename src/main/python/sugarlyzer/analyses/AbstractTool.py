import functools
import logging
import operator
import shutil
import tempfile
import time
from abc import ABC, abstractmethod
from hashlib import sha256
from pathlib import Path
from typing import Iterable, List

from python.sugarlyzer.models.alarm.Alarm import Alarm
from python.sugarlyzer.readers.AbstractReader import AbstractReader

logger = logging.getLogger(__name__)


class AbstractTool(ABC):

    def __init__(self, reader: AbstractReader,
                 name: str,
                 keep_mem: bool,
                 make_main: bool,
                 remove_errors: bool,
                 intermediary_results_path: Path,
                 results_cache: Path = None,
                 desugaring_function_whitelist: List[str] = None):
        self.reader = reader
        self.keep_mem = keep_mem,
        self.make_main = make_main
        self.remove_errors = remove_errors
        self.name = name
        self.results_dir = intermediary_results_path
        self.cache_dir = results_cache

        self.desugaring_function_whitelist = [] if desugaring_function_whitelist is None \
            else desugaring_function_whitelist

    def analyze_file_and_read_alarms(self,
                                     source_file: Path,
                                     command_line_defs: Iterable[str] = None,
                                     included_dirs: Iterable[Path] = None,
                                     included_files: Iterable[Path] = None,
                                     recommended_space=None) -> Iterable[Alarm]:
        """
        Analyzes a desugared .c file, and returns the alarms generated.
        :param source_file: The (desugared) source file to analyze.
        :param command_line_defs: Macro (un)definitions that should be set for the analysis.
        :param included_dirs: Directories required for the analysis (e.g., to search for headers).
        :param included_files: Includes to specify to the tool (right now, just used to pass in macro definitions
        in separate headers).
        :param recommended_space: TODO
        :return: A collection of alarms reported by the analysis tool.
        """
        if recommended_space is not None:
            with tempfile.NamedTemporaryFile('w', delete=False) as uds:
                uds.write(recommended_space)
                if included_files is None:
                    included_files = []
                included_files.append(uds.name)

        start_time = time.monotonic()

        # Check cache for existing reports for the specific file.
        cache_dir_hits: list[Path] | None = None
        hex_hash: str | None = None
        if self.cache_dir is not None:
            # Build hash.
            hasher = sha256()

            with open(file=source_file, mode="r") as file:
                for line in file:
                    hasher.update(bytes(line, 'utf-8'))
            for command_line_def in sorted(command_line_defs or []):
                hasher.update(bytes(command_line_def, 'utf-8'))
            for included_dir in sorted(included_dirs or []):
                hasher.update(bytes(str(included_dir), 'utf-8'))
            for included_file in sorted(included_files or []):
                hasher.update(bytes(str(included_file), 'utf-8'))
            if recommended_space is not None:
                hasher.update(bytes(recommended_space, 'utf-8'))

            hex_hash: str = hasher.hexdigest()
            cache_dir_hits = list(self.cache_dir.glob(pattern=f"{self.name}_report_{source_file.name}_{hex_hash}*"))

        cache_hit: bool = (cache_dir_hits is not None) and (len(list(cache_dir_hits)) > 0)

        if cache_hit:
            logger.debug(f"Cache hit for analysis results of file {str(source_file)}")
        else:
            logger.debug(f"Cache miss for file {str(source_file)}. Running {self.name}...")

        tool_report_files: list[Path] = cache_dir_hits if cache_hit else list(self.analyze(file=source_file,
                                                                                          command_line_defs=command_line_defs,
                                                                                          included_dirs=included_dirs,
                                                                                          included_files=included_files))

        # Add new entries to cache.
        if self.cache_dir is not None and not cache_hit:
            # Ensure that the cache folder exists.
            self.cache_dir.mkdir(parents=True, exist_ok=True)

            index: int = 1  # There could be multiple report files for a single source file.
            for tool_report_file in tool_report_files:
                shutil.copy(src=tool_report_file, dst=self.cache_dir / Path(
                    f"{self.name}_report_{source_file.name}_{hex_hash}_{index}{tool_report_file.suffix}"))
                index += 1

        alarms = \
            functools.reduce(operator.iconcat, [self.reader.read_output(f) for f in tool_report_files], [])
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
