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
    def read_output(self,
                    report_file: Path,
                    desugared_source_file: Path,
                    unpreprocessed_source_file: Path) -> Iterable[Alarm]:
        res: list[JoernAlarm] = []

        if os.path.getsize(report_file) != 0:
            with open(report_file, 'r') as rf:
                try:
                    warnings = json.load(rf)
                    for warning in warnings:
                        query_name: str = warning["name"]
                        query_title: str = warning["title"]
                        description: str = warning["description"]
                        score: float = float(warning["score"])
                        evidence_list: list[dict] = warning["evidence"]

                        for evidence in evidence_list:
                            res.append(JoernAlarm(input_file=desugared_source_file,
                                                  line_in_input_file=int(evidence["lineNumber"]),
                                                  unpreprocessed_source_file=unpreprocessed_source_file,
                                                  name=query_name,
                                                  title=query_title,
                                                  description=description,
                                                  score=score))
                except json.JSONDecodeError as e:
                    logger.exception(f"Error during parse of Joern report file \"{report_file.name}\": {e}")
        return res
