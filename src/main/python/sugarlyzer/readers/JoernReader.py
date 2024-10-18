import json
import logging
import os.path
import re
from pathlib import Path
from python.sugarlyzer.models.alarm.Alarm import Alarm, IntegerRange
from python.sugarlyzer.models.alarm.JoernAlarm import JoernAlarm
from python.sugarlyzer.readers.AbstractReader import AbstractReader
from typing import Iterable, TextIO

logger = logging.getLogger(__name__)


class JoernReader(AbstractReader):
    def __init__(self,
                 sanity_checks_per_query: dict[str, str],
                 source_file_encoding: str = None):
        super().__init__()
        self.sanity_checks_per_query = sanity_checks_per_query
        self.source_file_encoding = source_file_encoding

    def __alarm_valid(self, alarm: JoernAlarm) -> bool:
        def open_source_file(file: Path) -> TextIO:
            if self.source_file_encoding is None:
                return open(file, 'r')
            else:
                return open(file, 'r', encoding=self.source_file_encoding)

        # Check whether original line range can be established.
        original_line_mapping: IntegerRange
        try:
            original_line_mapping = alarm.original_line_range
        except ValueError as ve:
            logger.debug(f"Could not establish a line mapping for an alarm of type {alarm.message} in "
                         f"{alarm.input_file}:{alarm.line_in_input_file}")
            logger.debug(ve)
            return False

        original_file: Path = alarm.unpreprocessed_source_file
        query_name: str = alarm.name

        try:
            with open_source_file(original_file) as source_file:
                for current_line_number, line in enumerate(source_file, start=1):
                    if original_line_mapping.start_line <= current_line_number <= original_line_mapping.end_line:
                        if re.search(pattern=self.sanity_checks_per_query[query_name], string=line):
                            return True

            logger.debug(f"Could not validate warning with query {query_name} in "
                         f"{original_file}::{original_line_mapping} "
                         f"{'(Approximated)' if original_line_mapping.approximated else ''}")
        except UnicodeDecodeError as ude:
            logger.critical(f"Caught a UnicodeDecodeError when trying to open {original_file}. "
                            f"Was the correct encoding selected?")
            logger.exception(ude)

        return False

    def read_output(self,
                    report_file: Path,
                    desugared_source_file: Path,
                    unpreprocessed_source_file: Path,
                    source_file_encoding: str = None) -> Iterable[Alarm]:
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
                            alarm: JoernAlarm = JoernAlarm(input_file=desugared_source_file,
                                                           line_in_input_file=int(evidence["lineNumber"]),
                                                           unpreprocessed_source_file=unpreprocessed_source_file,
                                                           name=query_name,
                                                           message=query_title,
                                                           description=description,
                                                           score=score)

                            if self.__alarm_valid(alarm=alarm):
                                res.append(alarm)
                            else:
                                logger.debug(f"Read a match for {alarm.name} in {report_file} relating to "
                                             f"{alarm.input_file}::{alarm.line_in_input_file} but could not validate "
                                             f"the alarm.")
                except json.JSONDecodeError as e:
                    logger.exception(f"Error during parse of Joern report file \"{report_file.name}\": {e}")
        return res
