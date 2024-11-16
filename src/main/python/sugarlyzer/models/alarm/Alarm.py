import logging
import re
from abc import abstractmethod
from pathlib import Path
from typing import List, Dict, Iterable, TypeVar, Tuple

import itertools
from python.sugarlyzer.models.alarm.IntegerRange import IntegerRange
from z3.z3 import ModelRef

logger = logging.getLogger(__name__)


def map_source_line(line_number: int,
                    desugared_file: Path,
                    function_line_range: tuple[str, IntegerRange]) -> IntegerRange:
    """
    Map a line within a desugared source file back to its original range within the unpreprocessed source code.

    :param line_number: The line number of the line that should be mapped (starts at 1 for the first line).
    :param desugared_file: The desugared file in which the line is present.
    :param function_line_range: The line range of the enclosing function or a complete range over the file for an alarm
    raised in global scope.

    :return: The line range in the original source file.
    """
    logger.info(f"Trying to open file {desugared_file}")
    with open(desugared_file, 'r') as infile:
        line_range_pattern: str = r"// L(.*):L(.*)$"  # Example: "int  (__cmds_8183)[] ;// L41:L42"
        single_line_pattern: str = r"// L(.*)$"  # Example: "int  __i_8187 ;// L49"
        array_access_fixed_index_pattern: str = r"\s*__\S+_\d+\[\d+\] = .+$"  # Example: "__cmds_8183[0] = 128"

        def check_for_line_number_comment(line: str) -> IntegerRange | None:
            if match := re.search(line_range_pattern, line):
                return IntegerRange(int(match.group(1)), int(match.group(2)))
            if match := re.search(single_line_pattern, line):
                return IntegerRange(int(match.group(1)), int(match.group(1)))
            return None

        lines: List[str] = list(map(lambda x: x.strip('\n'), infile.readlines()))
        try:
            the_line: str = lines[line_number - 1]
        except IndexError as ie:
            logger.exception(f"Trying to find {line_number} in file {desugared_file} failed: {ie}.")
            raise

        if (original_line_range := check_for_line_number_comment(the_line)) is not None:
            return original_line_range

        if re.search(array_access_fixed_index_pattern, the_line) or not (function_line_range[0] == "GLOBAL"):
            # Try to approximate line numbers. This is necessary given that some constructs are desugared to multiple
            # lines, where only the last line (or those of the surrounding scope) is assigned to proper line mapping.
            # For instance, arrays containing entries specified by macros will be desugared to multiple lines contained
            # in a block that specifies the line number. Example:
            # int abc[] = {VALUE_A, VALUE_B, VALUE_C}; will be desugared to:
            # if (1) {
            # {
            # __abc_1159[0] = 5677;
            # __abc_1159[1] = 535655;
            # __abc_1159[2] = 12345;
            # } } ;// L21
            curren_line_number: int = line_number
            open_parentheses: int = 0

            while curren_line_number < len(lines):
                current_line: str = lines[curren_line_number]
                if "{" in current_line:
                    open_parentheses += current_line.count("{")
                if "}" in current_line and open_parentheses > 0:
                    open_parentheses -= min(current_line.count("}"), open_parentheses)

                if open_parentheses <= 0:
                    original_line_range: IntegerRange = check_for_line_number_comment(current_line)
                    if original_line_range is not None and (
                            original_line_range.start_line <= function_line_range[1].end_line):
                        original_line_range.approximated = True
                        return original_line_range

                curren_line_number += 1

    raise ValueError(f"Could not find source line for line {desugared_file}:{line_number} ({the_line})")


class Alarm:
    """
    Class representing an alarm (i.e., a warning) issued by an analysis tool.
    """

    __id_generator = itertools.count()

    def __init__(self,
                 input_file: Path,
                 line_in_input_file: int,
                 unpreprocessed_source_file: Path,
                 message: str,
                 ):
        self.input_file: Path = input_file
        self.unpreprocessed_source_file: Path = unpreprocessed_source_file
        self.line_in_input_file: int = int(line_in_input_file)
        self.other_lines_in_input_file: list[int] = []
        self.message: str = message
        self.id: int = next(Alarm.__id_generator)

        self.__original_line_range: IntegerRange | None = None
        self.__function_line_range: tuple[str, IntegerRange] | None = None
        self.__sanitized_message: str | None = None

        self.presence_condition: str | None = None
        self.feasible: bool | None = None
        self.model: ModelRef | str | None = None  # TODO: More elegant way to handle the two possible types of model.

        self.analysis_time: float | None = None
        self.desugaring_time: float | None = None

        self.get_recommended_space: bool | None = None
        self.remove_errors: bool | None = None
        self.verified: str | None = None

    Printable = TypeVar('Printable')

    def as_dict(self) -> Dict[str, Printable]:
        executor = {
            "id": lambda: str(self.id),
            "input_file": lambda: str(self.input_file),
            "input_line": lambda: self.line_in_input_file,
            "other_input_lines": lambda: self.other_lines_in_input_file,
            "original_file": lambda: str(self.unpreprocessed_source_file),
            "original_line": lambda: str(self.original_line_range),
            "function_line_range": lambda: f"{self.function_line_range[0]}:{str(self.function_line_range[1])}",
            "message": lambda: self.message,
            "sanitized_message": lambda: self.sanitized_message,
            "presence_condition": lambda: self.presence_condition,
            "feasible": lambda: self.feasible,
            "configuration": lambda: str(self.model) if isinstance(self.model, ModelRef) else self.model,
            "analysis_time": lambda: self.analysis_time,
            "desugaring_time": lambda: self.desugaring_time,
            "get_recommended_space": lambda: self.get_recommended_space,
            "remove_errors": lambda: self.remove_errors,
            "verified": lambda: self.verified
        }

        result = {}
        for k, v in executor.items():
            try:
                result[k] = v()
            except (ValueError, IndexError):
                result[k] = "ERROR"

        return result

    @property
    def sanitized_message(self) -> str:
        if self.message is None:
            raise ValueError("Cannot compute sanitized message while self.message is None.")

        if self.__sanitized_message is None:
            self.__sanitized_message = self.sanitize(self.message)
        return self.__sanitized_message

    @property
    def original_line_range(self) -> IntegerRange:
        if self.input_file is None:
            raise ValueError("Trying to set original line range when self.original_file is none.")

        # A more robust solution to distinguish conventional from desugared source files would involve incorporating a
        # specially formatted comment into the code during desugaring.
        if 'desugared' not in self.input_file.name:
            return IntegerRange(self.line_in_input_file, self.line_in_input_file)

        if self.__original_line_range is None:
            self.__original_line_range = map_source_line(line_number=self.line_in_input_file,
                                                         desugared_file=self.input_file,
                                                         function_line_range=self.function_line_range)
            if (self.__original_line_range is not None
                    and not self.function_line_range[0] == "GLOBAL"
                    and not self.__original_line_range.is_in(self.function_line_range[1])):
                logger.critical(
                    f"Sanity check failed. Warning ({self.input_file}:{self.line_in_input_file} {self.message}) "
                    f"original line range {self.original_line_range} and "
                    f"function line range {self.__function_line_range}, and the former is not included in the latter, "
                    f"which is not a global scope. Please double check that our line mapping is correct.")
                # self.__original_line_range = IntegerRange(-1, 0)  # TODO Is there a better way to represent null values without using None?
        return self.__original_line_range

    @property
    def function_line_range(self) -> Tuple[str, IntegerRange]:
        if self.input_file is None:
            raise ValueError

        if self.__function_line_range is None:
            with open(self.input_file) as f:
                lines = f.readlines()

            lines_to_reverse_iterate_over = lines[:self.line_in_input_file]
            lines_to_reverse_iterate_over.reverse()

            found = False
            for l in lines_to_reverse_iterate_over:
                l = l.strip()
                # Only consider the last 1000 characters since static renamings can be multiple million characters long.
                if re.search(r"//\s?M:L(\d*):L(\d*)$", l[-1000:]):
                    found = True
                    # Function defs are not overly long. Can therefore now match on the whole string.
                    mat = re.search(r"(.*)//\s?M:L(\d*):L(\d*)$", l)
                    self.__function_line_range = (mat.group(1), IntegerRange(int(mat.group(2)), int(mat.group(3))))
                    break
            if not found:
                self.__function_line_range = ("GLOBAL", IntegerRange(1, len(lines)))

        return self.__function_line_range

    @property
    def all_relevant_lines(self) -> Iterable[int]:
        """
        Returns all desugared lines. Useful for use with :func:`src.sugarlyzer.SugarCRunner.calculate_asserts`

        :return: An iterator of desugared lines.
        """
        return [self.line_in_input_file] + self.other_lines_in_input_file

    # noinspection PyMethodMayBeStatic
    def sanitize(self, message: str):
        logger.warning("Sanitize is not implemented.")
        return message

    @abstractmethod
    def is_alarm_valid(self, file_encoding: str = None) -> bool:
        """
        Performs a sanity check for the alarm.

        :param file_encoding: The encoding of the original source file to which the alarm relates.
        :return: True if the alarm passed the sanity check and False otherwise.
        """
        pass
