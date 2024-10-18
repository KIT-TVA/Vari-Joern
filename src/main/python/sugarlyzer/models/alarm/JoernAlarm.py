from pathlib import Path
from typing import Dict

from python.sugarlyzer.models.alarm.Alarm import Alarm


class JoernAlarm(Alarm):
    def __init__(self,
                 input_file: Path,
                 line_in_input_file: int,
                 unpreprocessed_source_file: Path,
                 name: str,
                 message: str,
                 description: str,
                 score: float
                 ):
        super().__init__(input_file=input_file,
                         line_in_input_file=line_in_input_file,
                         unpreprocessed_source_file=unpreprocessed_source_file,
                         message=message)

        self.name: str = name
        self.description: str = description
        self.score: float = score

    def as_dict(self) -> Dict[str, str]:
        result = super().as_dict()
        result['name'] = self.name
        result['description'] = self.description
        result['score'] = self.score
        return result

    # Joern's message holds the title of the warning which is unaffected by desugaring. Thus, no sanitization is required.
    def sanitize(self, message: str):
        return message
