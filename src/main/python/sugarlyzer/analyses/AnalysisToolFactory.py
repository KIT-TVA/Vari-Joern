from pathlib import Path

from python.sugarlyzer.analyses.AbstractTool import AbstractTool
from python.sugarlyzer.analyses.Joern import Joern


class AnalysisToolFactory:

    # noinspection PyTypeChecker
    @classmethod
    def get_tool(cls,
                 tool_name: str,
                 intermediary_results_path: Path,
                 cache_dir: Path = None,
                 maximum_heap_size: int = None) -> AbstractTool:
        """
        Given the name of the tool, return the appropriate analysis tool class.

        :param tool_name: The name of the tool.
        :param intermediary_results_path: The path at which to store intermediary results of the tool.
        :param cache_dir: The directory that should be used to cache reports created by the analysis tool.
        :param maximum_heap_size: The maximum heap size in gigabytes that should be used for the analysis.
        :return: An instance of the corresponding tool class.
        """

        match tool_name.lower():
            case "testtool" | "clang" | "infer" | "phasar":
                # Classes relating to the SAST tool used by the original Sugarlyzer were not yet adjusted to reflect the
                # changes made to the framework.
                raise ValueError(f"{tool_name} is currently not supported by this version of Sugarlyzer.")
            case "joern":
                return Joern(intermediary_results_path=intermediary_results_path,
                             cache_dir=cache_dir,
                             maximum_heap_size=maximum_heap_size)
            case _:
                raise ValueError(f"No tool for {tool_name}")
