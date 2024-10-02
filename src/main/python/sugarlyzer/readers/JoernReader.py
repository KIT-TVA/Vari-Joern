import json
import logging
import os.path
from pathlib import Path
from typing import Iterable

from python.sugarlyzer.models.alarm.Alarm import Alarm
from python.sugarlyzer.models.alarm.JoernAlarm import JoernAlarm
from python.sugarlyzer.readers.AbstractReader import AbstractReader

logger = logging.getLogger(__name__)


class JoernReader(AbstractReader):
    def read_output(self, report_file: Path, unpreprocessed_source_file: Path) -> Iterable[Alarm]:
        res = []

        if os.path.getsize(report_file) != 0:
            with open(report_file, 'r') as rf:
                try:
                    warnings = json.load(rf)
                    for warning in warnings:
                        query_name = warning["name"]
                        query_title = warning["title"]
                        description = warning["description"]
                        score = warning["score"]
                        evidence_list = warning["evidence"]

                        for evidence in evidence_list:
                            res.append(JoernAlarm(input_file=evidence["filename"],
                                                  line_in_input_file=evidence["lineNumber"],
                                                  unpreprocessed_source_file=unpreprocessed_source_file,
                                                  name=query_name,
                                                  message=query_title,
                                                  description=description,
                                                  score=score))
                except json.JSONDecodeError as e:
                    logger.exception(f"Error during parse of Joern report file \"{report_file.name}\": {e}")
        return res
