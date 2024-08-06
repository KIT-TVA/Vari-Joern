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
    def get_tool(cls, tool, intermediary_results_path: Path) -> AbstractTool:
        """
        Given the name of the tool, return the appropriate tool class.
        :param tool:
        :return:
        """

        match tool.lower():
            case "clang": return Clang(intermediary_results_path)
            case "testtool": return TestTool(intermediary_results_path)
            case "infer": return Infer(intermediary_results_path)
            case "phasar": return Phasar(intermediary_results_path)
            case "joern": return Joern(intermediary_results_path)
            case _: raise ValueError(f"No tool for {tool}")
