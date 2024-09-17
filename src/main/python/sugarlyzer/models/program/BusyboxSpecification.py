from pathlib import Path
from typing import List, Dict

from python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification


class BusyboxSpecification(ProgramSpecification):
    def transform_kconfig_into_kextract_format(self) -> dict[str, str]:
        pass

    def parse_make_output(self, make_output_file: Path) -> List[Dict]:
        pass

    def run_make(self, output_path: Path) -> int:
        pass