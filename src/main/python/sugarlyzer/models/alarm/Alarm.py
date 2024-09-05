import collections
import logging
from pathlib import Path
from typing import List, Dict, Optional, Iterable, Callable, TypeVar, Tuple
from dataclasses import dataclass
import itertools
import re

import z3
from z3.z3 import ModelRef

logger = logging.getLogger(__name__)


@dataclass
class IntegerRange:
    start_line: int
    end_line: int

    def __str__(self):
        return f"{self.start_line}:{self.end_line}"

    def is_in(self, i) -> bool:
        if isinstance(i, IntegerRange):
            return self.start_line >= i.start_line and self.end_line <= i.end_line
        else:
            return self.start_line == i == self.end_line

    def includes(self, i) -> bool:
        if isinstance(i, IntegerRange):
            return i.start_line >= self.start_line and i.end_line <= self.end_line
        else:
            return self.start_line <= i <= self.end_line


def same_range(range1: IntegerRange, range2: IntegerRange) -> bool:
    return range1.start_line == range2.start_line and range1.end_line == range2.end_line


def map_source_line(desugared_file: Path, line_number: int) -> IntegerRange:
    """
    Given an alarm, map it back to original source.

    :param desugared_file: The desugared file in which the line is present.
    :param line_number: The linen umber to map (starts at 1 for the first line).
    :return: The line range in the original source file.
    """
    with open(desugared_file, 'r') as infile:
        line_range_pattern: str = r"// L(.*):L(.*)$" # Example: "int  (__cmds_8183)[] ;// L41:L42"
        single_line_pattern: str = r"// L(.*)$" # Example: "int  __i_8187 ;// L49"
        array_access_fixed_index_pattern: str = r"\s*__\S+_\d+\[\d+\] = .+$" # Example: "__cmds_8183[0] = 128"

        def check_for_line_number_comment(line: str) -> IntegerRange | None:
            if match := re.search(line_range_pattern, line):
                return IntegerRange(int(match.group(1)), int(match.group(2)))
            if match := re.search(single_line_pattern, line):
                return IntegerRange(int(match.group(1)), int(match.group(1)))

        lines: List[str] = list(map(lambda x: x.strip('\n'), infile.readlines()))
        try:
            the_line: str = lines[line_number - 1]
        except IndexError as ie:
            logger.exception(f"Trying to find {line_number} in file {desugared_file}.")
            raise

        if original_line_range := check_for_line_number_comment(the_line) is not None:
            return original_line_range

        if re.search(array_access_fixed_index_pattern, the_line) is not None:
            # Search for a line number specified by the enclosing block. This is necessary given that arrays containing
            # entries specified by macros will be desugared to multiple lines contained in a block that specifies the
            # line number. Example:
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
                current_line:str = lines[curren_line_number]
                if "{" in current_line:
                    open_parentheses += current_line.count("{")
                if "}" in current_line:
                    open_parentheses += current_line.count("{")

                if open_parentheses <= 0 and (original_line_range := check_for_line_number_comment(current_line) is not None):
                    return original_line_range

                curren_line_number += 1

    raise ValueError(f"Could not find source line for line {desugared_file}:{line_number} ({the_line})")


class Alarm:
    __id_generator = itertools.count()

    def __init__(self,
                 input_file: Path = None,
                 line_in_input_file: int = None,
                 message: str = None,
                 ):
        self.input_file: Path = input_file
        self.line_in_input_file: int = int(line_in_input_file)
        self.message: str = message
        self.id: int = next(Alarm.__id_generator)

        self.__original_line_range: IntegerRange = None
        self.__function_line_range = None
        self.__method_mapping = None
        self.__sanitized_message = None

        self.presence_condition: Optional[str] = None
        self.feasible: Optional[bool] = None
        self.model: Optional[ModelRef | str] = None  # TODO: More elegant way to handle the two possible types of model.

        self.analysis_time: float = None
        self.desugaring_time: float = None

        self.get_recommended_space: bool = None
        self.remove_errors: bool = None

        self.verified: str = None

    Printable = TypeVar('Printable')

    def as_dict(self) -> Dict[str, Printable]:
        executor = {
            "id": lambda: str(self.id),
            "input_file": lambda: str(self.input_file.absolute()),
            "input_line": lambda: self.line_in_input_file,
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

        if 'desugared' not in self.input_file.name:  ## Bad, bad, bad. TODO: Fix this with a more robust solution
            return IntegerRange(self.line_in_input_file, self.line_in_input_file)

        if self.__original_line_range is None:
            self.__original_line_range = map_source_line(self.input_file, self.line_in_input_file)
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
                if mat := re.search(r"(.*)//\s?M:L(\d*):L(\d*)$", l.strip()):
                    found = True
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
        return [self.line_in_input_file]

    # noinspection PyMethodMayBeStatic
    def sanitize(self, message: str):
        logger.warning("Sanitize is not implemented.")
        return message
