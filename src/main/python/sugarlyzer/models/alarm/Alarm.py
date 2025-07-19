import logging
import re
from abc import abstractmethod
from pathlib import Path
from typing import List, Dict, Iterable, TypeVar, Tuple

import itertools
from python.sugarlyzer.models.alarm.IntegerRange import IntegerRange
from z3.z3 import ModelRef

logger = logging.getLogger(__name__)

class Lines:
    approximated: bool = False

    def __init__(self, files: dict[Path, list[IntegerRange]]) -> None:
        """
        Create a new LineMapping instance.

        :param files: A dictionary mapping original source files to a list of IntegerRanges, where each IntegerRange
        describes a line range in the original source file that corresponds to the line in the desugared source file.
        """
        self.files: dict[Path, list[IntegerRange]] = files

        # Ensure monotonicity of line ranges. We expect that between two line ranges, there is at least one line that is
        # not covered by any line range.
        for file, ranges in self.files.items():
            for i in range(len(ranges) - 1):
                if ranges[i].end_line >= ranges[i + 1].start_line:
                    raise ValueError(f"Line ranges for file {file} are not monotonic: {ranges[i]} and {ranges[i + 1]}.")

    def is_valid(self) -> bool:
        """
        Determine whether the LineMapping instance is valid (i.e., all files are known and all line ranges are valid).
        :return: True if the LineMapping instance is valid and False otherwise.
        """
        if "???" in self.files:
            return False

        for ranges in self.files.values():
            if any(not r.is_valid_line_range() for r in ranges):
                return False

        return True

    def is_in(self, other: 'Lines') -> bool:
        """
        Determine whether this LineMapping instance is contained within another LineMapping instance.
        :param other: Another LineMapping instance.
        :return: True if this LineMapping instance is contained within the other and False otherwise.
        """
        for file, ranges in self.files.items():
            if file not in other.files:
                return False
            for r in ranges:
                if not any(other_r.includes(r) for other_r in other.files[file]):
                    return False
        return True

    def same_lines(self, other: 'Lines') -> bool:
        """
        Determine whether this LineMapping instance refers to the same lines as another LineMapping instance.
        :param other: Another LineMapping instance.
        :return: True if this LineMapping instance refers to the same lines as the other and False otherwise.
        """
        if len(self.files) != len(other.files):
            return False

        for file, ranges in self.files.items():
            if file not in other.files:
                return False
            other_ranges = other.files[file]
            if len(ranges) != len(other_ranges):
                return False

            # This is simplified by the assumption that the ranges are sorted and non-overlapping.
            for range_self, range_other in zip(ranges, other_ranges):
                if not IntegerRange.same_range(range_self, range_other):
                    return False
        return True

    def __str__(self) -> str:
        """
        Create a string representation of the LineMapping instance.
        :return: A string representation of the LineMapping instance.
        """
        return ";".join(f"{file}:{','.join(str(r) for r in ranges)}" for file, ranges in self.files.items()) + \
                (" (approximated)" if self.approximated else "")

def try_parse_comment(line: str) -> Lines | None:
    """
    Try to parse a comment in the form of "// L /path/to/file1:5-10,14;/path/to/file2:20-25" and return a
    LineMapping instance if successful.
    :param line: The line containing the comment to parse.
    :return: A LineMapping instance if the comment was successfully parsed, None otherwise.
    """
    line_comment_pattern: str = r"//\s?L\s+((?:[^:]+:(?:\d+(?:-\d+)?,?)+;?)+)"
    if match := re.search(line_comment_pattern, line):
        files: dict[Path, list[IntegerRange]] = {}
        for file_range in match.group(1).split(';'):
            file_path, ranges = file_range.split(':')
            file_path = Path(file_path.strip())
            if file_path not in files:
                files[file_path] = []
            for r in ranges.split(','):
                if '-' in r:
                    start, end = map(int, r.split('-'))
                    files[file_path].append(IntegerRange(start, end))
                else:
                    line_number = int(r)
                    files[file_path].append(IntegerRange(line_number, line_number))
        return Lines(files)
    return None

def map_source_line(line_number: int,
                    desugared_file: Path,
                    function_line_range: tuple[str, Lines]) -> Lines:
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
        array_access_fixed_index_pattern: str = r"\s*__\S+_\d+\[\d+\] = .+$"  # Example: "__cmds_8183[0] = 128"

        lines: List[str] = list(map(lambda x: x.strip('\n'), infile.readlines()))
        try:
            the_line: str = lines[line_number - 1]
        except IndexError as ie:
            logger.exception(f"Trying to find {line_number} in file {desugared_file} failed: {ie}.")
            raise

        if (original_line_range := try_parse_comment(the_line)) is not None:
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
            # } } ;// L /path/to/file.c:21
            if not function_line_range[1].is_valid() or len(function_line_range[1].files) != 1:
                raise ValueError(f"Function line range {function_line_range} is not valid or does not contain exactly "
                                 "one file. Cannot approximate line number "
                                 f"for {desugared_file}:{line_number} ({the_line}).")
            function_line_range_end = next(iter(function_line_range[1].files.values()))[-1].end_line

            curren_line_number: int = line_number
            open_parentheses: list[str] = []

            parentheses = "{}()[]"

            while curren_line_number < len(lines):
                current_line: str = lines[curren_line_number]
                current_line_without_comments: str = re.sub(r"//.*", "", current_line).strip()
                if "\"" in current_line_without_comments or "'" in current_line_without_comments:
                    raise ValueError("Cannot approximate line number for "
                                     f"{desugared_file}:{line_number} ({the_line}) because it contains a string literal.")

                if "/*" in current_line_without_comments or "*/" in current_line_without_comments:
                    raise ValueError("Cannot approximate line number for "
                                     f"{desugared_file}:{line_number} ({the_line}) because it contains a block comment.")

                for c in current_line_without_comments:
                    parenthesis_idx = parentheses.find(c)
                    if parenthesis_idx == -1:
                        continue
                    if parenthesis_idx % 2 == 0:  # Opening parenthesis
                        open_parentheses.append(c)
                    elif len(open_parentheses) > 0:  # Closing parenthesis
                        if open_parentheses[-1] == parentheses[parenthesis_idx - 1]:
                            open_parentheses.pop()
                        else:
                            raise ValueError("Cannot approximate line number for "
                                             f"{desugared_file}:{line_number} ({the_line}) because it contains"
                                             f" mismatched parentheses.")

                if len(open_parentheses) == 0:
                    original_line_range: Lines = try_parse_comment(current_line)
                    if original_line_range is not None:
                        original_line_range.approximated = True
                        return original_line_range

                curren_line_number += 1

    raise ValueError(f"Could not find source line for line {desugared_file}:{line_number} ({the_line})")


class Alarm:
    """
    Class representing an alarm (i.e., a warning) issued by an analysis tool.
    """

    __id_generator = itertools.count()
    Printable = TypeVar('Printable')

    def __init__(self,
                 input_file: Path,
                 line_in_input_file: int,
                 unpreprocessed_source_file: Path,
                 message: str,
                 ):
        """
        Create a new Alarm instance.

        :param input_file: The desugared source file on which the alarm was raised.
        :param line_in_input_file: The line number of the line on which the alarm was raised.
        :param unpreprocessed_source_file: The unpreprocessed source file from which the desugared file was created.
        :param message: A message associated with the alarm (e.g., its type).
        """

        self.input_file: Path = input_file
        self.unpreprocessed_source_file: Path = unpreprocessed_source_file
        self.line_in_input_file: int = int(line_in_input_file)
        self.other_lines_in_input_file: list[int] = []
        self.message: str = message
        self.id: int = next(Alarm.__id_generator)

        self.__original_lines: Lines | None = None
        self.__function_lines: tuple[str, Lines] | None = None
        self.__sanitized_message: str | None = None

        self.presence_condition: str | None = None
        self.feasible: bool | None = None
        self.model: ModelRef | str | None = None

        self.analysis_time: float | None = None
        self.desugaring_time: float | None = None

        self.get_recommended_space: bool | None = None
        self.remove_errors: bool | None = None
        self.verified: str | None = None

    @property
    def sanitized_message(self) -> str:
        """
        Get a sanitized version of the alarm's message (i.e., one where the renamings applied by SugarC are
        reverted).

        :return: The sanitized message.
        """

        if self.message is None:
            raise ValueError("Cannot compute sanitized message while self.message is None.")

        if self.__sanitized_message is None:
            self.__sanitized_message = self.sanitize(self.message)
        return self.__sanitized_message

    @property
    def original_lines(self) -> Lines:
        """
        Determine the line range in the unpreprocessed code to which the Alarm instance refers to.

        :return: An IntegerRange describing the line range in the unpreprocessed source code.
        """

        if self.input_file is None:
            raise ValueError("Trying to set original line range when self.original_file is none.")

        # A more robust solution to distinguish conventional from desugared source files would involve incorporating a
        # specially formatted comment into the code during desugaring.
        if 'desugared' not in self.input_file.name:
            return Lines({Path(self.input_file.name): [IntegerRange(self.line_in_input_file, self.line_in_input_file)]})

        if self.__original_lines is None:
            self.__original_lines = map_source_line(line_number=self.line_in_input_file,
                                                    desugared_file=self.input_file,
                                                    function_line_range=self.function_lines)
            if (self.__original_lines is not None
                    and not self.function_lines[0] == "GLOBAL"
                    and not self.__original_lines.is_in(self.function_lines[1])):
                logger.critical(
                    f"Sanity check failed. Warning ({self.input_file}:{self.line_in_input_file} {self.message}) "
                    f"original line range {self.original_lines} and "
                    f"function line range {self.__function_lines[1]}, and the former is not included in the latter, "
                    f"which is not a global scope. Please double check that our line mapping is correct.")
        return self.__original_lines

    @property
    def function_lines(self) -> Tuple[str, Lines]:
        """
        Determine the line range of the surrounding function in the unpreprocessed source code.

        :return: A Tuple containing the signature of the enclosing function (or "GLOBAL" if the Alarm instance refers
        to a location with global scope) together with the associated line range of the function (or a full line range
        over the file if the Alarm instance refers
        to a location with global scope)
        """

        if self.input_file is None:
            raise ValueError

        if self.__function_lines is None:
            with open(self.input_file) as f:
                lines = f.readlines()

            lines_to_reverse_iterate_over = lines[:self.line_in_input_file]
            lines_to_reverse_iterate_over.reverse()

            found = False
            for l in lines_to_reverse_iterate_over:
                l = l.strip()
                # Only consider the last 1000 characters since static renamings can be multiple million characters long.
                if re.search(r"//\s?M:L\s+[^:]+:(\d*)-(\d*)$", l[-1000:]):
                    found = True
                    # Function defs are not overly long. Can therefore now match on the whole string.
                    mat = re.search(r"(.*)//\s?M:L\s+([^:]+):(\d*)-(\d*)$", l)
                    self.__function_lines = (mat.group(1), Lines({
                            Path(mat.group(2)): [IntegerRange(int(mat.group(3)), int(mat.group(4)))]
                    }))
                    break
            if not found:
                self.__function_lines = ("GLOBAL", Lines({Path("???"): [IntegerRange(1, len(lines))]}))

        return self.__function_lines

    @property
    def all_relevant_lines(self) -> Iterable[int]:
        """
        Returns all desugared lines. Useful for use with :func:`src.sugarlyzer.SugarCRunner.calculate_asserts`

        :return: An iterator of desugared lines.
        """
        return [self.line_in_input_file] + self.other_lines_in_input_file

    @abstractmethod
    def sanitize(self, message: str) -> str:
        """
        Sanitize the specified message (i.e., revert the renaming of identifiers applied by SugarC).

        :param message: The message that should be sanitized.
        :return: The sanitized message.
        """

        pass

    @abstractmethod
    def is_alarm_valid(self, file_encoding: str = None) -> bool:
        """
        Perform a sanity check for the alarm (i.e., check whether a structure typical of the alarm can be found within
        the referenced line range of the unpreprocessed code).

        :param file_encoding: The encoding of the original source file to which the alarm relates.
        :return: True if the alarm passed the sanity check and False otherwise.
        """
        pass

    def as_dict(self) -> Dict[str, Printable]:
        """
        Create a dict with explanatory information on the Alarm instance.

        :return: The dict with explanatory information on the alarm.
        """

        executor = {
            "id": lambda: str(self.id),
            "input_file": lambda: str(self.input_file),
            "input_line": lambda: self.line_in_input_file,
            "other_input_lines": lambda: self.other_lines_in_input_file,
            "original_file": lambda: str(self.unpreprocessed_source_file),
            "original_line": lambda: str(self.original_lines),
            "function_line_range": lambda: f"{self.function_lines[0]}:{str(self.function_lines[1])}",
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