import logging
from pathlib import Path
from typing import Iterable

from python.sugarlyzer.models.Alarm import Alarm
from python.sugarlyzer.readers.AbstractReader import AbstractReader

logger = logging.getLogger(__name__)


class JoernReader(AbstractReader):

    def read_output(self, report_file: Path) -> Iterable[Alarm]:
        res = []
        with open(report_file, 'r') as rf:
            currentalarm = None
            for line in rf:
                line = line.lstrip().rstrip()
                if line.startswith('Result:'):
                    if currentalarm != None:
                        res.append(currentalarm)
                    alarmInfo = line.split(':')

                    file = alarmInfo[3]
                    linenum = int(alarmInfo[4])
                    message = ':'.join([alarmInfo[5], alarmInfo[2]])
                    logger.debug(f"line={line}; lineNumber={linenum}; message={message}")
                    currentalarm = Alarm(input_file=file,
                                         line_in_input_file=linenum,
                                         message=message)
        if currentalarm is not None:
            res.append(currentalarm)
        return res
