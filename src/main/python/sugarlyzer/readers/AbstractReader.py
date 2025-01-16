from abc import ABC, abstractmethod
from pathlib import Path
from typing import Iterable

from python.sugarlyzer.models.alarm.Alarm import Alarm


class AbstractReader(ABC):

    @abstractmethod
    def read_output(self,
                    report_file: Path,
                    desugared_source_file: Path,
                    unpreprocessed_source_file: Path) -> Iterable[Alarm]:
        """
        Given the output of an analysis tool, read in the file and return the alarms.
        :param desugared_source_file: The desugared source file on which the report_file was created.
        :param report_file: The analysis tool result.
        :param unpreprocessed_source_file: The unpreprocessed source file to which the report file relates.
        :return: The alarms in the file.
        """
        pass
