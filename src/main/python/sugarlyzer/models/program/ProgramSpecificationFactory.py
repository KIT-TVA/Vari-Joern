from pathlib import Path
from typing import Any

from python.sugarlyzer.models.program.AxtlsSpecification import AxtlsSpecification
from python.sugarlyzer.models.program.BusyboxSpecification import BusyboxSpecification
from python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification
from python.sugarlyzer.models.program.ToyboxSpecification import ToyboxSpecification


class ProgramSpecificationFactory:

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
            case "busybox":
                return BusyboxSpecification(name=name,
                                            project_root=project_root,
                                            tmp_dir=tmp_dir,
                                            **program_specification_json)
            case "toybox":
                return ToyboxSpecification(name=name,
                                           project_root=project_root,
                                           tmp_dir=tmp_dir,
                                           **program_specification_json)
            case _:
                raise ValueError(f"No tool for {name}")
