from pathlib import Path

from python.sugarlyzer.analyses.AbstractTool import AbstractTool
from python.sugarlyzer.analyses.Clang import Clang
from python.sugarlyzer.analyses.Infer import Infer
from python.sugarlyzer.analyses.Phasar import Phasar
from python.sugarlyzer.analyses.Joern import Joern
from python.sugarlyzer.analyses.TestTool import TestTool


class AnalysisToolFactory:

    # noinspection PyTypeChecker
    @classmethod
    def get_tool(cls, tool_name: str,
                 intermediary_results_path: Path,
                 cache_dir: Path = None,
                 maximum_heap_size: int = None,
                 source_file_encoding: str = None) -> AbstractTool:
        """
        Given the name of the tool, return the appropriate tool class.
        :param source_file_encoding: The encoding used by the files that should be analyzed.
        :param tool_name: The name of the tool.
        :param intermediary_results_path: The path at which to store intermediary results of the tool.
        :param cache_dir: The directory where to cache reports created by the analysis tool.
        :param maximum_heap_size: The maximum heap size in gigabytes that should be used for the analysis.
        :return:
        """

        match tool_name.lower():
            case "clang":
                return Clang(intermediary_results_path)
            case "testtool":
                return TestTool(intermediary_results_path)
            case "infer":
                return Infer(intermediary_results_path)
            case "phasar":
                return Phasar(intermediary_results_path)
            case "joern":
                return Joern(intermediary_results_path=intermediary_results_path,
                             cache_dir=cache_dir,
                             maximum_heap_size=maximum_heap_size,
                             source_file_encoding=source_file_encoding)
            case _:
                raise ValueError(f"No tool for {tool_name}")
