from pathlib import Path
from typing import List

from python.sugarlyzer.models.ProgramSpecification import ProgramSpecification


class axtlsSpecification(ProgramSpecification):
    def determine_includes_and_macros_for_file(self, file: Path) -> (List[Path], List[Path], List[str]):
        # TODO Start implementing.
        pass