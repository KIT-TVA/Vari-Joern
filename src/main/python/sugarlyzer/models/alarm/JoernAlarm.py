import logging
import re
from pathlib import Path
from typing import Dict, TextIO

from python.sugarlyzer.models.alarm.Alarm import Alarm, IntegerRange

logger = logging.getLogger(__name__)


class JoernAlarm(Alarm):
    # Regexes describing structures typical of the standard Joern queries that can be used as sanity checks.
    sanity_check_pattern_per_query: dict[str, str] = {
        "call-to-gets": r"(?i)gets",
        "call-to-getwd": r"(?i)getwd",
        "call-to-strtok": r"(?i)strtok",
        "constant-array-access-no-check": r"[.*\d.*]",
        "copy-loop": r"\[.+].*=",
        "file-operation-race": r"access|chdir|chmod|chown|creat|faccessat|fchmodat|fopen|fstatat|lchown||linkat|link|lstat|"
                               r"mkdirat|mkdir|mkfifoat|mkfifo|mknodat|mknod|openat|open|readlinkat|readlink|renameat|rename|"
                               r"rmdir|stat|unlinkat|unlink|",
        "format-controlled-printf": r"((?i)printf)|((?i)sprintf)|((?i)vsprintf)",
        "free-field-no-reassign": r"free",
        "malloc-memcpy-int-overflow": r"malloc|((?i)memcpy)",
        "setgid-without-setgroups": r"(?i)set(res|re|e|)gid",
        "setuid-without-setgid": r"(?i)set(res|re|e|)uid",
        "signed-left-shift": r"<<",
        "socket-send": r"send",
        "strlen-truncation": r"(?i)strlen",
        "strncpy-no-null-term": r"(?i)strncpy",
        "unchecked-read-recv-malloc": r"((?i)read)|((?i)recv)|((?i)malloc)"
    }

    def __init__(self,
                 input_file: Path,
                 line_in_input_file: int,
                 unpreprocessed_source_file: Path,
                 name: str,
                 title: str,
                 description: str,
                 score: float
                 ):
        """
        Creates a new JoernAlarm instance.

        :param input_file: The desugared source file on which the alarm was raised.
        :param line_in_input_file: The line number of the line on which the alarm was raised.
        :param unpreprocessed_source_file: The unpreprocessed source file from which the desugared source file was
        created.
        :param name: The name of the matching Joern query.
        :param title: The title of the matching Joern query.
        :param description: The description of the matching Joern query.
        :param score: The numerical score associated with the matching Joern query.
        """

        super().__init__(input_file=input_file,
                         line_in_input_file=line_in_input_file,
                         unpreprocessed_source_file=unpreprocessed_source_file,
                         message=title)

        self.name: str = name
        self.description: str = description
        self.score: float = score

    def as_dict(self) -> Dict[str, str]:
        result = super().as_dict()
        result['name'] = self.name
        result['description'] = self.description
        result['score'] = self.score
        return result

    def sanitize(self, message: str):
        # Joern's message holds the title of the warning which is unaffected by SuperC's renamings. Thus, no
        # sanitization is required.
        return message

    def is_alarm_valid(self, file_encoding: str = None) -> bool:
        def open_source_file(file: Path) -> TextIO:
            if file_encoding is None:
                return open(file, 'r')
            else:
                return open(file, 'r', encoding=file_encoding)

        # Check whether original line range can be established.
        original_line_mapping: IntegerRange
        try:
            original_line_mapping = self.original_line_range
        except ValueError as ve:
            logger.debug(f"Could not establish a line mapping for an alarm of type {self.message} in "
                         f"{self.input_file}:{self.line_in_input_file}")
            logger.debug(ve)
            return False

        original_file: Path = self.unpreprocessed_source_file
        query_name: str = self.name

        try:
            with open_source_file(original_file) as source_file:
                for current_line_number, line in enumerate(source_file, start=1):
                    if original_line_mapping.start_line <= current_line_number <= original_line_mapping.end_line:
                        if re.search(pattern=self.sanity_check_pattern_per_query[query_name], string=line):
                            return True

            logger.debug(f"Could not validate warning with query {query_name} in "
                         f"{original_file}::{original_line_mapping}"
                         f"{' (Approximated)' if original_line_mapping.approximated else ''}. Location in desugared code "
                         f"was line {self.line_in_input_file}.")
        except UnicodeDecodeError as ude:
            logger.critical(f"Caught a UnicodeDecodeError when trying to open {original_file}. "
                            f"Was the correct encoding selected?")
            logger.exception(ude)

        return False
