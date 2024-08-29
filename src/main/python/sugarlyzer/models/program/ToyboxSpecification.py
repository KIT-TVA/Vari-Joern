from pathlib import Path
from typing import List, Dict

from python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification


class ToyboxSpecification(ProgramSpecification):
    def transform_kconfig_into_kextract_format(self):
        # TODO Implement
        pass

    def parse_make_output(self, make_output_file: Path) -> List[Dict]:
        # TODO Implement
        pass