from pathlib import Path
from typing import Any

from python.sugarlyzer.models.AxtlsSpecification import AxtlsSpecification
from python.sugarlyzer.models.ProgramSpecification import ProgramSpecification


class ProgramSpecificationFactory:

    # noinspection PyTypeChecker
    @classmethod
    def get_program_specification(cls,
                                  name: str,
                                  project_root: Path,
                                  tmp_dir: Path,
                                  program_specification_json: dict[str, Any]) -> ProgramSpecification:
        match name.lower():
            case "axtls":
                return AxtlsSpecification(name=name,
                                          project_root=project_root,
                                          tmp_dir=tmp_dir,
                                          **program_specification_json)
            case _:
                raise ValueError(f"No tool for {name}")
