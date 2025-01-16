from python.sugarlyzer.analyses.AbstractTool import AbstractTool
from pathlib import Path
from typing import Iterable
from python.sugarlyzer.readers.TestReader import TestReader


class TestTool(AbstractTool):

    def __init__(self, intermediary_results_path: Path):
        super().__init__(TestReader(), name='testTool', keep_mem=True, make_main=True, remove_errors=False,
                         intermediary_results_path=intermediary_results_path)

    def analyze(self, desugared_source_file: Path,
                included_dirs: Iterable[Path] = None,
                included_files: Iterable[Path] = None,
                command_line_defs: Iterable[str] = None):
        print(f"Analyzing {desugared_source_file}")
        yield desugared_source_file
