import importlib
import logging
import os
import re
import subprocess
import importlib.resources
from pathlib import Path
from typing import Iterable
from python.sugarlyzer.analyses.AbstractTool import AbstractTool
from python.sugarlyzer.readers.JoernReader import JoernReader
from python.sugarlyzer.util.Subprocessing import get_resource_usage_of_process

logger = logging.getLogger(__name__)


class Joern(AbstractTool):
    """
    Tool class adding support for Joern.
    """

    # Function names on which the queries rely and that therefore be excluded from the desugaring process.
    whitelist_function_names: list[str] = ["gets", "getwd", "strtok", "access", "chdir", "chmod", "chown", "creat",
                                           "faccessat", "fchmodat", "fopen", "fstatat", "lchown", "linkat", "link",
                                           "lstat", "mkdirat", "mkdir", "mkfifoat", "mkfifo", "mknodat", "mknod",
                                           "openat", "open", "readlinkat", "readlink", "renameat", "rename", "rmdir",
                                           "stat", "unlinkat", "unlink", "printf", "sprintf", "vsprintf", "free",
                                           "memset", "bzero", "malloc", "memcpy", "setresgid", "setregid", "setegid",
                                           "setgid", "setgroups", "setresuid", "setreuid", "seteuid", "setuid", "send",
                                           "strlen", "strncpy", "read", "recv"]

    # Command used for constructing a CPG.
    joern_parse_command: list[str] = ["/usr/bin/time", "-v",
                                      "joern-parse", "{maximum_heap_size_option}", "{input}", "-o", "{output}",
                                      "--language", "C", "--frontend-args", "{file_includes}", "{dir_includes}",
                                      "{macro_defs}"]
    # Command used for analyzing a previously constructed CPG.
    joern_analyze_command: list[str] = ["/usr/bin/time", "-v",
                                        "joern", "{maximum_heap_size_option}", "--script", "{script}", "--param",
                                        "cpgPath={cpg_path}", "--param", "outFile={report_path}"]
    # The scala script that should be executed by Joern to initiate the analysis and collect the results.
    joern_script = importlib.resources.files('resources.joern') / "scan.sc"

    def __init__(self,
                 intermediary_results_path: Path,
                 cache_dir: Path = None,
                 maximum_heap_size: int = None):
        """
        Constructs a new instance.
        :param intermediary_results_path: The path at which intermediary results of Joern should be stored.
        :param cache_dir: The path to the directory that should be used to cache reports created by Joern.
        :param maximum_heap_size: The maximum heap size that should be used by Joern in gigabytes.
        """

        super().__init__(reader=JoernReader(),
                         name='joern',
                         make_main=True,
                         keep_mem=True,
                         remove_errors=True,
                         intermediary_results_path=intermediary_results_path,
                         results_cache=cache_dir,
                         desugaring_function_whitelist=Joern.whitelist_function_names)
        self.maximum_heap_size = maximum_heap_size

    def analyze(self, desugared_source_file: Path,
                included_dirs: Iterable[Path] = None,
                included_files: Iterable[Path] = None,
                command_line_defs: Iterable[str] = None) -> Iterable[Path]:
        if included_files is None:
            included_files = []
        if included_dirs is None:
            included_dirs = []
        if command_line_defs is None:
            command_line_defs = []

        cpg_file = self.results_dir / Path(f"cpg_{desugared_source_file.name}.bin")
        dest_file = self.results_dir / Path(f"joern_report_{desugared_source_file.name}.json")
        self.results_dir.mkdir(exist_ok=True, parents=True)

        file_includes = " ".join(f"--include {file}" for file in included_files)
        dir_includes = " ".join(f"--include {included_dir}" for included_dir in included_dirs)
        macro_defs = " ".join([macro.replace("-D", "--define")
                               for macro in command_line_defs if macro.startsWith("-D")])

        maximum_heap_size_option = f"-J-Xmx{self.maximum_heap_size}G" if self.maximum_heap_size is not None else ""

        ############################################
        ### Generate CPG.
        ############################################
        cmd = " ".join(str(s) for s in Joern.joern_parse_command)
        cmd = cmd.format(input=desugared_source_file.absolute(),
                         output=cpg_file,
                         file_includes=file_includes,
                         dir_includes=dir_includes,
                         macro_defs=macro_defs,
                         maximum_heap_size_option=maximum_heap_size_option)
        logger.debug(
            f"Building CPG for file \"{desugared_source_file.absolute()}\" and writing result to \"{cpg_file}\"")
        logger.debug(f"Command for building CPG: \"{cmd}\"")
        ps = subprocess.run(cmd, text=True, shell=True, capture_output=True,
                            executable='/bin/bash')
        if (resource_stats := get_resource_usage_of_process(ps)) is not None:
            logger.info(f"Resource usage for building cpg of {desugared_source_file}: "
                        f"CPU time {resource_stats.usr_time + resource_stats.sys_time}s "
                        f"max memory {resource_stats.max_memory}kb")

        if ps.returncode == 0:
            logger.debug(f"Successfully built CPG for file \"{desugared_source_file.absolute()}\"")
            ############################################
            ### Analyze CPG.
            ############################################
            cmd = " ".join(str(s) for s in Joern.joern_analyze_command)
            with importlib.resources.as_file(Joern.joern_script) as joern_script_path:
                cmd = cmd.format(script=str(joern_script_path),
                                 cpg_path=cpg_file,
                                 report_path=dest_file,
                                 maximum_heap_size_option=maximum_heap_size_option)

            logger.debug(f"Analyzing CPG \"{cpg_file.absolute()}\" and writing analysis report to \"{dest_file}\"")
            logger.debug(f"Command for analysis: \"{cmd}\"")
            ps = subprocess.run(cmd, text=True, shell=True,
                                capture_output=True,
                                executable='/bin/bash', cwd=self.results_dir)

            if ps.returncode != 0:
                logger.warning(
                    f"Running joern on file \"{str(desugared_source_file)}\" with command \"{cmd}\" potentially failed "
                    f"(exit code {ps.returncode}).")
                logger.warning(ps.stdout)
            if (resource_stats := get_resource_usage_of_process(ps)) is not None:
                logger.info(f"Resource usage for analyzing cpg of {desugared_source_file}: "
                            f"CPU time {resource_stats.usr_time + resource_stats.sys_time}s "
                            f"max memory {resource_stats.max_memory}kb")
        else:
            logger.warning(
                f"Running joern-parse on file {str(desugared_source_file)} with command {cmd} potentially failed "
                f"(exit code {ps.returncode}).")
            if os.environ.get('JAVA_HOME') is not None:
                match = re.search(r"java-(\d+)", os.environ.get('JAVA_HOME'))
                if match is not None:
                    java_version = int(match.group(1))
                    if java_version < 11:
                        logger.error(f"JAVA_HOME points to Java version {java_version} "
                                     f"but Joern requires at least Java 11!")
            logger.debug(ps.stdout)
            logger.debug(ps.stderr)

        # Ensure that dest_file exists for JoernReader.
        with open(dest_file, "a"):
            pass

        yield dest_file
