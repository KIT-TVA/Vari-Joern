from pathlib import Path
from typing import List, Dict, Callable

from python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification
from python.sugarlyzer.util.Kconfig import kconfig_add_quotes_to_source_directive


class BusyboxSpecification(ProgramSpecification):
    def problematic_kconfig_lines_and_corrections(self) -> list[(str, Callable[[str], str])]:
        return [(r'source [^"\s]*Config\.[^"\s]+', kconfig_add_quotes_to_source_directive)]

    def parse_make_output(self, make_output_file: Path) -> List[Dict]:
        # TODO
        pass

    def run_make(self, output_path: Path) -> int:
        # TODO
        pass