from pathlib import Path
from typing import Any

from python.sugarlyzer.models.program.AxtlsSpecification import AxtlsSpecification
from python.sugarlyzer.models.program.BusyboxSpecification import BusyboxSpecification
from python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification
from python.sugarlyzer.models.program.ToyboxSpecification import ToyboxSpecification


class ProgramSpecificationFactory:
    """
    Factory class for creating ProgramSpecification instances corresponding to the supported subject systems.
    """

    @classmethod
    def get_program_specification(cls,
                                  program_name: str,
                                  program_root: Path,
                                  tmp_dir: Path,
                                  program_specification_json: dict[str, Any]) -> ProgramSpecification:
        """
        Create a ProgramSpecification instance based on the provided data.

        :param program_name: The name of the program (e.g., axtls).
        :param program_root: The path to the program's root directory.
        :param tmp_dir: The directory that should be used for temporary files created during the analysis.
        :param program_specification_json: A dictionary containing the information read from the program specification
        JSON file associated with the program.
        :return: A corresponding ProgramSpecification instance.
        """

        match program_name.lower():
            case "axtls":
                return AxtlsSpecification(name=program_name,
                                          project_root=program_root,
                                          tmp_dir=tmp_dir,
                                          **program_specification_json)
            case "busybox":
                return BusyboxSpecification(name=program_name,
                                            project_root=program_root,
                                            tmp_dir=tmp_dir,
                                            **program_specification_json)
            case "toybox":
                return ToyboxSpecification(name=program_name,
                                           project_root=program_root,
                                           tmp_dir=tmp_dir,
                                           **program_specification_json)
            case _:
                raise ValueError(f"{program_name} is currently not supported for analysis by Sugarlyzer.")
