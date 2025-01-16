import logging
from pathlib import Path
from typing import Iterable

from python.sugarlyzer.models.alarm.Alarm import Alarm
from python.sugarlyzer.models.alarm.PhasarAlarm import PhasarAlarm
from python.sugarlyzer.readers.AbstractReader import AbstractReader

logger = logging.getLogger(__name__)


class PhasarReader(AbstractReader):

    def read_output(self,
                    report_file: Path,
                    desugared_source_file: Path,
                    unpreprocessed_source_file: Path, ) -> Iterable[Alarm]:
        if not Path(report_file).exists():
            return []
        with open(report_file, 'r') as rf:
            logging.info(f"alarms are in {report_file}")
            alarmList = []
            warning = {}
            for l in rf:
                if 'Use  --------' in l:
                    if 'function' in warning.keys() and 'variables' in warning.keys() and 'line' in warning.keys():
                        for var in warning['variables']:
                            alarmList.append(PhasarAlarm(unpreprocessed_source_file=unpreprocessed_source_file,
                                                         function=warning['function'],
                                                         line_in_input_file=warning['line'],
                                                         variable_name=var))
                    warning = {}
                elif 'Function   :' in l:
                    warning['function'] = l.split('Function   : ')[1].lstrip().rstrip()
                elif 'Variable(s):' in l:
                    warning['variables'] = l.split('Variable(s):')[1].lstrip().rstrip().split(',')
                elif 'Line       :' in l:
                    warning['line'] = int(l.split('Line       : ')[1].lstrip().rstrip())
            if 'function' in warning.keys() and 'variables' in warning.keys() and 'line' in warning.keys():
                for var in warning['variables']:
                    alarmList.append(PhasarAlarm(unpreprocessed_source_file=unpreprocessed_source_file,
                                                 function=warning['function'],
                                                 line_in_input_file=warning['line'],
                                                 variable_name=var,
                                                 input_file=desugared_source_file))

            return alarmList
