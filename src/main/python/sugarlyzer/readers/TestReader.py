from pathlib import Path
from typing import Iterable

from python.sugarlyzer.models.alarm.Alarm import Alarm
from python.sugarlyzer.readers.AbstractReader import AbstractReader


class TestReader(AbstractReader):

    def read_output(self, report_file: Path) -> Iterable[Alarm]:
        return []
