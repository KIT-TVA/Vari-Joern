import logging
from pathlib import Path
from typing import Iterable

from python.sugarlyzer.models.alarm.ClangAlarm import ClangAlarm
from python.sugarlyzer.readers.AbstractReader import AbstractReader

logger = logging.getLogger(__name__)


class ClangReader(AbstractReader):

    def read_output(self, report_file: Path, desugared_source_file: Path, unpreprocessed_source_file: Path) -> Iterable[ClangAlarm]:
        res = []
        with open(report_file, 'r') as rf:
            currentAlarm = None
            for l in rf:
                l = l.lstrip().rstrip()
                if ': warning:' in l:

                    if currentAlarm != None:
                        res.append(currentAlarm)
                    file = l.split(':')[0]
                    line = int(l.split(':')[1])
                    message = ':'.join(l.split(':')[4:])
                    message = '['.join(message.split('[')[:-1])
                    logger.debug(f"l={l}; line={line}; message={message}")
                    currentAlarm = ClangAlarm(unpreprocessed_source_file=unpreprocessed_source_file,
                                              line_in_input_file=line,
                                              message=message,
                                              input_file=desugared_source_file,
                                              alarm_type='warning',
                                              warning_path=[])
                elif ': note:' in l:
                    line = int(l.split(':')[1])
                    if currentAlarm != None and line not in currentAlarm.warning_path:
                        currentAlarm.warning_path.append(line)
        if currentAlarm != None:
            res.append(currentAlarm)
        return res
