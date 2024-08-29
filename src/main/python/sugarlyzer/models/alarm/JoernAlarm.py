from pathlib import Path
from typing import Dict

from python.sugarlyzer.models.alarm.Alarm import Alarm


class JoernAlarm(Alarm):
    def __init__(self,
                 input_file: Path = None,
                 line_in_input_file: int = None,
                 message: str = None,
                 description: str = None,
                 score: int = None
                 ):
        super().__init__(input_file, line_in_input_file, message)
        self.description: str = description
        self.score: int = score

    def as_dict(self) -> Dict[str, str]:
        result = super().as_dict()
        result['description'] = self.description
        result['score'] = self.score
        return result

    # Joern's message holds the title of the warning which is unaffected by desugaring. Thus, no sanitization is required.
    def sanitize(self, message: str):
        return message
