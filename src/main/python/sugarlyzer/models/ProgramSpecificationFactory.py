from typing import Optional, Iterable, Dict, Any

from python.sugarlyzer.models.ProgramSpecification import ProgramSpecification
from python.sugarlyzer.models.axtlsSpecification import axtlsSpecification


class ProgramSpecificationFactory:

    # noinspection PyTypeChecker
    @classmethod
    def get_program_specification(cls,
                                  name: str,
                                  program_json: dict[str, Any],
                                  source_dir) -> ProgramSpecification:
        match name.lower():
            case "axtls":
                return axtlsSpecification(name=name, source_dir=source_dir, **program_json)
            case _:
                raise ValueError(f"No tool for {name}")
