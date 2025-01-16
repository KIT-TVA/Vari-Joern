from __future__ import annotations
from dataclasses import dataclass


@dataclass
class IntegerRange:
    """
    Data class representing an integer range (i.e., an interval) that may be approximate.
    """

    start_line: int
    end_line: int
    approximated: bool = False

    @classmethod
    def same_range(cls, range1: 'IntegerRange', range2: 'IntegerRange') -> bool:
        """
        Check whether two integer range instances describe the same range.

        :param range1: The first integer range.
        :param range2: The second integer range.
        :return: True if the two instances describe the same range and False otherwise.
        """

        if range1.is_valid_line_range() and range2.is_valid_line_range():
            return range1.start_line == range2.start_line and range1.end_line == range2.end_line

        # No comparisons between invalid ranges.
        return False

    def is_valid_line_range(self) -> bool:
        """
        Determine whether the integer range represents a valid line range (i.e., 0 < start <= end).

        :return: True if the range represents a valid line range and false otherwise.
        """

        return 0 < self.start_line <= self.end_line

    def is_in(self, other: 'IntegerRange' | int) -> bool:
        """
        Determine whether this integer range is contained within another integer range or limited to a single integer.

        :param other: Another integer range or an integer that should be checked for whether it encloses this integer range.
        :return: True if the integer range is contained within other False otherwise.
        """

        if isinstance(other, IntegerRange):
            return self.start_line >= other.start_line and self.end_line <= other.end_line
        else:
            return self.start_line == other == self.end_line

    def includes(self, other: IntegerRange | int) -> bool:
        """
        Determine whether another integer range or an integer is contained within this integer range.

        :param other: Another integer range or an integer that should be checked for whether they are enclosed by
        this integer range.
        :return: True if this integer range encloses other and False otherwise.
        """

        if isinstance(other, IntegerRange):
            return other.start_line >= self.start_line and other.end_line <= self.end_line
        else:
            return self.start_line <= other <= self.end_line

    def __str__(self):
        return f"{self.start_line}:{self.end_line}{' (Approximated)' if self.approximated else ''}"
