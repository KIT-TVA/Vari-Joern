from python.sugarlyzer.analyses.AbstractTool import AbstractTool
from python.sugarlyzer.analyses.Clang import Clang
from python.sugarlyzer.analyses.Infer import Infer
from python.sugarlyzer.analyses.Phasar import Phasar
from python.sugarlyzer.analyses.Joern import Joern
from python.sugarlyzer.analyses.TestTool import TestTool


class AnalysisToolFactory:

    # noinspection PyTypeChecker
    @classmethod
    def get_tool(cls, tool) -> AbstractTool:
        """
        Given the name of the tool, return the appropriate tool class.
        :param tool:
        :return:
        """

        match tool.lower():
            case "clang": return Clang()
            case "testtool": return TestTool()
            case "infer": return Infer()
            case "phasar": return Phasar()
            case "joern": return Joern()
            case _: raise ValueError(f"No tool for {tool}")
