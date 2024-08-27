import importlib
import logging
import subprocess
import os
import re
from pathlib import Path
from typing import Iterable, Dict

from python.sugarlyzer.analyses.AbstractTool import AbstractTool
from python.sugarlyzer.util.Subprocessing import get_resource_usage_of_process
from python.sugarlyzer.readers.JoernReader import JoernReader

logger = logging.getLogger(__name__)


class Joern(AbstractTool):
    whitelist_function_names: list[str] = ["gets", "getwd", "strtok", "access" "chdir", "chmod", "chown", "creat", "faccessat",
                                "fchmodat", "fopen", "fstatat", "lchown", "linkat", "link", "lstat", "mkdirat", "mkdir",
                                "mkfifoat", "mkfifo", "mknodat", "mknod", "openat", "open", "readlinkat", "readlink",
                                "renameat", "rename", "rmdir", "stat", "unlinkat", "unlink", "printf", "sprintf",
                                "vsprintf", "free", "memset", "bzero", "malloc", "memcpy", "setresgid", "setregid",
                                "setegid", "setgroups", "setresuid", "setreuid", "seteuid", "setgid", "send", "strlen",
                                "strncpy", "read", "recv"]

    joern_parse_command: list[str] = ["/usr/bin/time", "-v",
                           "joern-parse", "{maximum_heap_size_option}", "{input}", "-o", "{output}", "--language", "C",
                           "--frontend-args", "{file_includes}", "{dir_includes}", "{macro_defs}"]
    joern_analyze_command: list[str] = ["/usr/bin/time", "-v",
                             "joern", "{maximum_heap_size_option}", "--script", "{script}",
                             "--param", "cpgPath={cpg_path}", "--param", "outFile={report_path}"]
    joern_script_path: Path = importlib.resources.files('resources.joern') / "scan.sc"

    def __init__(self, intermediary_results_path: Path, maximum_heap_size: int = None):
        super().__init__(JoernReader(), name='joern',
                         make_main=True,
                         keep_mem=True,
                         remove_errors=True,
                         intermediary_results_path=intermediary_results_path,
                         desugaring_function_whitelist=Joern.whitelist_function_names)
        self.maximum_heap_size = maximum_heap_size

    def analyze(self, file: Path,
                included_dirs: Iterable[Path] = None,
                included_files: Iterable[Path] = None,
                command_line_defs: Iterable[str] = None) -> Iterable[Path]:
        if included_files is None:
            included_files = []
        if included_dirs is None:
            included_dirs = []
        if command_line_defs is None:
            command_line_defs = []

        cpg_file = self.results_dir / Path(f"cpg_{file.name}.bin")
        dest_file = self.results_dir / Path(f"joern_report_{file.name}.json")
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
        cmd = cmd.format(input=file.absolute(),
                         output=cpg_file,
                         file_includes=file_includes,
                         dir_includes=dir_includes,
                         macro_defs=macro_defs,
                         maximum_heap_size_option=maximum_heap_size_option)
        logger.debug(f"Building CPG for file \"{file.absolute()}\" and writing result to \"{cpg_file}\"")
        logger.debug(f"Command for building CPG: \"{cmd}\"")
        ps = subprocess.run(cmd, text=True, shell=True, capture_output=True,
                            executable='/bin/bash')

        if ps.returncode == 0:
            logger.debug(f"Successfully built CPG for file \"{file.absolute()}\"")
            ############################################
            ### Analyze CPG.
            ############################################
            cmd = " ".join(str(s) for s in Joern.joern_analyze_command)
            cmd = cmd.format(script=str(Joern.joern_script_path),
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
                    f"Running joern on file \"{str(file)}\" with command \"{cmd}\" potentially failed "
                    f"(exit code {ps.returncode}).")
                logger.warning(ps.stdout)
            if (resource_stats := get_resource_usage_of_process(ps)) is not None:
                logger.info(f"Resource usage for analyzing cpg of {file}: "
                            f"CPU time {resource_stats.usr_time + resource_stats.sys_time}s "
                            f"max memory {resource_stats.max_memory}kb")
        else:
            logger.warning(
                f"Running joern-parse on file {str(file)} with command {cmd} potentially failed "
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
