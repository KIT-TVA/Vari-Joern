import importlib
import logging
import subprocess
from pathlib import Path
from typing import Iterable

from python.sugarlyzer.analyses.AbstractTool import AbstractTool
from python.sugarlyzer.readers.JoernReader import JoernReader
from python.sugarlyzer.util.ParseBashTime import parse_bash_time

logger = logging.getLogger(__name__)


class Joern(AbstractTool):
    joern_parse_command = "joern-parse {input} -o {output} --language c --frontend-args {includes}"
    joern_analyze_command = "joern --script {script} --param cpgPath={cpg_path} --param outFile={report_path}"
    joern_script_path = importlib.resources.path(f'resources.joern', "scan.sc")

    def __init__(self, intermediary_results_path: Path):
        super().__init__(JoernReader(), name='joern', make_main=True, keep_mem=True, remove_errors=True,
                         intermediary_results_path=intermediary_results_path)

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
        dest_file = self.results_dir / Path(f"joern_report_{file.name}.txt")
        includes = " ".join(f"--include {file}" for file in included_files)

        # TODO Adjust Joern call (see product-based JoernAnalyzer).
        # cmd = ["joern-scan", file.absolute(), "--overwrite", ">", dest_file]
        # ps = subprocess.run(" ".join([str(s) for s in cmd]), text=True, shell=True, capture_output=True,
        #                    executable='/bin/bash')
        # cmd = ["/usr/bin/time", "-v", "infer", "--pulse-only", '-o', output_location, '--', "clang",
        #        *list(itertools.chain(*zip(itertools.cycle(["-I"]), included_dirs))),
        #        *list(itertools.chain(*zip(itertools.cycle(["--include"]), included_files))),
        #        *command_line_defs,
        #        "-nostdinc", "-c", file.absolute()]

        # Generate CPG.
        parse_cmd = Joern.joern_parse_command.format(input=file.absolute(),
                                                     output=cpg_file,
                                                     includes=includes)
        logger.debug(f"Building CPG for file \"{file.absolute()}\" and writing result to \"{cpg_file}\"")
        ps = subprocess.run(parse_cmd, text=True, shell=True, capture_output=True,
                            executable='/bin/bash')

        if ps.returncode == 0:
            # Analyze CPG.
            analyze_cmd = Joern.joern_analyze_command.format(script=Joern.joern_script_path,
                                                             cpg_path=cpg_file,
                                                             report_path=dest_file)
            logger.debug(f"Analyzing CPG \"{cpg_file.absolute()}\" and writing analysis report to \"{dest_file}\"")
            ps = subprocess.run(analyze_cmd, text=True, shell=True, capture_output=True,
                                executable='/bin/bash')

            if ps.returncode == 0:
                try:
                    times = "\n".join(ps.stderr.split("\n")[-30:])
                    usr_time, sys_time, max_memory = parse_bash_time(times)
                    logger.info(f"CPU time to analyze {file} was {usr_time + sys_time}s")
                    logger.info(f"Max memory to analyze {file} was {max_memory}kb")
                except Exception as ve:
                    logger.exception("Could not parse time in string " + times)
            else:
                logger.warning(
                    f"Running joern on file {str(file)} with command {analyze_cmd} potentially failed (exit code {ps.returncode}).")
                logger.warning(ps.stdout)
        else:
            logger.warning(
                f"Running joern on file {str(file)} with command {parse_cmd} potentially failed (exit code {ps.returncode}).")
            logger.warning(ps.stdout)

        report = dest_file
        yield report
