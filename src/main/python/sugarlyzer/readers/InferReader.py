import logging
import json
from pathlib import Path
from typing import Iterable

from python.sugarlyzer.models.alarm.Alarm import Alarm
from python.sugarlyzer.models.alarm.InferAlarm import InferAlarm
from python.sugarlyzer.readers.AbstractReader import AbstractReader

logger = logging.getLogger(__name__)


class InferReader(AbstractReader):

    def read_output(self, report_file: Path, desugared_source_file: Path, unpreprocessed_source_file: Path) -> Iterable[Alarm]:
        if not Path(report_file).exists():
            return []
        with open(report_file, 'r') as rf:
            reportData = json.load(rf)
            logger.debug(f"alarms are in {report_file}")
            alarmList = []
            logger.debug(f"reportData is {reportData}")
            for alarmData in reportData:
                logger.debug(f"Alarm is {alarmData}")
                warningLines = []
                for alarmTrace in alarmData['bug_trace']:
                    if alarmTrace['line_number'] not in warningLines:
                        warningLines.append(alarmTrace['line_number'])
                ret = InferAlarm(unpreprocessed_source_file=unpreprocessed_source_file,
                                 line_in_input_file=alarmData['line'],
                                 bug_type=alarmData['bug_type'],
                                 message=alarmData['qualifier'],
                                 alarm_type=alarmData['severity'],
                                 warning_path=warningLines,
                                 input_file=desugared_source_file)
                alarmList.append(ret)

            return alarmList
