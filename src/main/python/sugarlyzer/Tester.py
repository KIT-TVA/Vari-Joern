import argparse
import copy
import functools
import importlib.resources
import json
import logging
import os
import shutil
import tempfile
import time
import sys
from concurrent.futures import ProcessPoolExecutor
from logging import FileHandler
from pathlib import Path
from typing import Iterable, List, Dict, Any, Tuple, TextIO

# noinspection PyUnresolvedReferences
from dill import pickle
from pathos.pools import ProcessPool
from python.sugarlyzer.models.alarm.IntegerRange import IntegerRange
# noinspection PyUnresolvedReferences
from tqdm import tqdm
# noinspection PyUnresolvedReferences
from z3.z3 import Solver, sat, Bool, Int, Not, And, Or

from python.sugarlyzer import SugarCRunner
from python.sugarlyzer.SugarCRunner import process_alarms
from python.sugarlyzer.analyses.AbstractTool import AbstractTool
from python.sugarlyzer.analyses.AnalysisToolFactory import AnalysisToolFactory
from python.sugarlyzer.models.alarm.Alarm import Alarm, Lines
from python.sugarlyzer.models.program.ProgramSpecification import ProgramSpecification
from python.sugarlyzer.models.program.ProgramSpecificationFactory import ProgramSpecificationFactory

logger = logging.getLogger(__name__)


class Tester:
    def __init__(self, args: argparse.Namespace):
        self.logfiles_disabled: bool = args.logfiles_disabled

        self.no_recommended_space = True
        self.jobs: int = max(args.jobs if args.jobs is not None else os.cpu_count() or 1, 1)
        self.maximum_heap_per_job = args.max_heap_per_job
        self.verbosity = args.verbosity
        self.keep_intermediary_files: bool = args.keep_intermediary_files if args.keep_intermediary_files is not None else False
        self.relative_paths: bool = args.relative_paths if args.relative_paths is not None else False

        # Set paths.
        self.__tmp_path = args.tmp_path if args.tmp_path is not None else tempfile.TemporaryDirectory(
            prefix="vari-joern-").name
        self.intermediary_results_path = Path(self.__tmp_path) / Path("family-based-analysis")
        self.intermediary_results_path.mkdir(exist_ok=True, parents=True)

        self.cache_dir_path = Path.home() / Path(".vari-joern/sugarlyzer-cache")
        self.cache_dir_path.mkdir(exist_ok=True, parents=True)

        # Read program specification file (program.json).
        program_specification = importlib.resources.files(
            f"resources.sugarlyzer.programs.{args.program}") / "program.json"
        with importlib.resources.as_file(program_specification) as program_specification_path:
            program_specification_json: Dict[str, Any] = ProgramSpecification.validate_and_read_program_specification(
                program_specification_path)

        # Subject system to analyze.
        self.program: ProgramSpecification = ProgramSpecificationFactory.get_program_specification(
            program_name=args.program,
            program_root=Path(args.program_path),
            tmp_dir=self.intermediary_results_path,
            program_specification_json=program_specification_json)
        # Analysis tool.
        self.tool: AbstractTool = AnalysisToolFactory().get_tool(tool_name=args.tool,
                                                                 intermediary_results_path=self.intermediary_results_path,
                                                                 cache_dir=self.cache_dir_path / Path(args.tool) / Path(
                                                                     self.program.name),
                                                                 maximum_heap_size=self.maximum_heap_per_job)

        self.output_file_path: Path = Path(args.output_path) if args.output_path is not None else Path.home() / Path(
            f"sugarlyzer_results_{self.tool.name}_{self.program.name}.json")
        self.output_file_path.parent.mkdir(exist_ok=True, parents=True)

        self.remove_errors = self.tool.remove_errors if self.program.remove_errors is None else self.program.remove_errors
        self.config_prefix = self.program.config_prefix
        self.whitelist = None  # TODO Consider removing (Sugarlyzer debt).
        self.kgen_map = self.intermediary_results_path / Path("kgenerate_macro_mapping.json")

    @functools.cache
    def get_inc_files_and_dirs_for_file(self, file: Path):
        included_files, included_directories, cmd_decs = self.program.inc_files_and_dirs_for_file(file)
        logger.debug(f"Included files, included directories for {file}: {included_files} {included_directories}")
        if self.no_recommended_space:
            recommended_space = None
        else:
            recommended_space = SugarCRunner.get_recommended_space(file, included_files, included_directories)
        logger.debug(f"User defined space for file {file} is {recommended_space}")
        return included_directories, included_files, cmd_decs, recommended_space

    def analyze_one_file(self, desugared_source_file: Path,
                         unpreprocessed_source_file: Path,
                         program_specification: ProgramSpecification) -> Iterable[Alarm]:
        inc_files, inc_dirs, cmd_decs = program_specification.inc_files_and_dirs_for_file(desugared_source_file)
        alarms = self.tool.analyze_file_and_read_alarms(desugared_source_file=desugared_source_file,
                                                        unpreprocessed_source_file=unpreprocessed_source_file,
                                                        command_line_defs=cmd_decs,
                                                        included_files=inc_files,
                                                        included_dirs=inc_dirs)
        return alarms

    def execute(self):
        logger.info(f"PYTHONPATH is {os.environ.get('PYTHONPATH')}")
        logger.info(f"Current environment is {os.environ}")
        ###################################
        # Run SugarC.
        ###################################
        logger.info(f"Desugaring the source code in {self.program.source_dirs} with {self.jobs} job(s)...")
        start_time_desugaring: float = time.monotonic()
        source_files = list(self.program.get_source_files())
        desugared_files: List[Tuple] = []

        with ProcessPoolExecutor(max_workers=self.jobs) as executor:
            # Submit tasks.
            desugar_tasks = [executor.submit(self.desugar, file) for file in source_files]

            # Collect results.
            for desugar_task in tqdm(desugar_tasks, total=len(source_files), miniters=1):
                try:
                    desugared_file = desugar_task.result()
                    desugared_files.append(desugared_file)
                except Exception as e:
                    logger.exception(f"Error during desugaring: {e}")

        logger.info(f"Finished desugaring the source code.")
        logger.info(f"Time elapsed during desugaring: {time.monotonic() - start_time_desugaring}s")
        logger.info(f"From a total of {len(source_files)} source files, collected {len(desugared_files)} "
                    f"desugared .c files.")

        # Prune desugared files that are empty.
        desugared_files = [desugaring_result for desugaring_result in desugared_files
                           if os.path.getsize(desugaring_result[0]) > 0]

        logger.info(f"After pruning empty files, {len(desugared_files)} desugared files remain for analysis.")

        ###################################
        # Run analysis tool.
        ###################################
        alarms = []
        logger.info(f"Running analysis with tool {self.tool.name}...")
        start_time_analysis: float = time.monotonic()

        with ProcessPoolExecutor(max_workers=self.jobs) as executor:
            # Submit tasks.
            analysis_tasks = [executor.submit(self.analyze_read_and_process, *(d, o, dt))
                              for d, _, o, dt in desugared_files]

            # Collect results.
            for analysis_task in tqdm(analysis_tasks, total=len(desugared_files), miniters=1):
                try:
                    results = analysis_task.result()
                    alarms.extend(results)
                except Exception as e:
                    logger.exception(f"Error during analysis: {e}")

        logger.info(f"Analysis with tool {self.tool.name} finished.")
        logger.info(f"Elapsed time during analysis: {time.monotonic() - start_time_analysis}s")
        logger.info(f"Got {len(alarms)} unique alarms from the analysis tool.")

        ###################################
        # Post processing of alarms.
        ###################################
        start_time_post_processing: float = time.monotonic()

        # Prune infeasible alarms.
        logger.info("Pruning alarms whose presence condition is not SAT...")
        alarms = [alarm for alarm in alarms if alarm.feasible]
        logger.info(f"{len(alarms)} alarms remain after pruning alarms whose presence condition is not SAT.")

        # Prune warnings whose original line range could not be established as these most likely relate to code
        # introduced during desugaring that is not found in the unpreprocessed source file.
        def line_mapping_exists(alarm: Alarm) -> bool:
            try:
                _ = alarm.original_lines
                return True
            except ValueError as ve:
                logger.debug(f"Could not establish a line mapping for an alarm of type {alarm.message} in "
                             f"{alarm.input_file}:{alarm.line_in_input_file}")
                logger.debug(ve)
            return False

        logger.info("Pruning alarms whose line mapping to the unpreprocessed source code could not be established...")
        alarms = [alarm for alarm in alarms if line_mapping_exists(alarm)]
        logger.info(f"{len(alarms)} alarms remain after pruning without a line mapping.")

        logger.info("Pruning alarms that do not originate from their original source files...")
        alarms = [alarm for alarm in alarms if alarm.unpreprocessed_source_file.absolute() in alarm.original_lines.files]
        logger.info(f"{len(alarms)} alarms remain after pruning alarms that do not originate from their original source files.")

        # Internal function that checks whether two alarms refer to the same issue in the unpreprocessed
        # source code.
        def alarm_match(a: Alarm, b: Alarm) -> bool:
            return (a.input_file == b.input_file
                    and a.feasible == b.feasible
                    and a.sanitized_message == b.sanitized_message
                    and a.original_lines.same_lines(b.original_lines))

        # Collect alarms into "buckets" based on equivalence.
        # Then, for each bucket, we will return one alarm, combining all the
        # models into a list.
        logger.info(f"Sorting the {len(alarms)} alarms into buckets to eliminate duplicates...")
        buckets: list[list[Alarm]] = [[]]
        bucket_matches = 0
        for ba in alarms:
            for bucket in buckets:
                # Bucket hit.
                if len(bucket) > 0 and alarm_match(bucket[0], ba):
                    bucket.append(ba)
                    bucket_matches += 1
                    break
            else:
                # If we get here, then there wasn't a bucket that this could fit into,
                # So it gets its own bucket and we add a new one to the end of the list.
                buckets[-1].append(ba)
                buckets.append([])

        logger.info(f"Sorted the {len(alarms)} alarms into {len(buckets) - 1} buckets "
                    f"({bucket_matches} matches on existing buckets).")

        # Aggregate alarms and join their presence conditions via disjunctions.
        logger.info("Now aggregating alarms within the buckets...")
        alarms: list[Alarm] | Iterable[Alarm] = []
        for bucket in (b for b in buckets if len(b) > 0):
            alarms.append(bucket[0])
            alarms[-1].presence_condition = f"Or({','.join(str(m.presence_condition) for m in bucket)})"
            alarms[-1].other_lines_in_input_file = [alarm.line_in_input_file for alarm in bucket[1:]]
        logger.debug("Done.")
        logger.info(f"{len(alarms)} alarms remain after aggregation.")

        logger.info(f"Time elapsed during post-processing of alarms: "
                    f"{time.monotonic() - start_time_post_processing}s")

        logger.info(f"Writing alarms to file \"{self.output_file_path}\"")

        # Sort alarms w.r.t full file name and original line range to ease comparison of reports between executions.
        alarms.sort(key=lambda
            alarm_param: f"{alarm_param.input_file}::{'A' if not alarm_param.original_lines.approximated else 'B'}"
                         f"::{str(alarm_param.original_lines)}::{alarm_param.message}")

        alarm_id: int = 0
        for alarm in alarms:
            # Assign unique ids to the alarms (ProcessPoolExecutor leads to overlapping ids and postprocessing can
            # create discontinuations).
            alarm.id = alarm_id
            alarm_id += 1

            # Turn absolute paths to relative ones if desired.
            if self.relative_paths:
                alarm.input_file = alarm.input_file.relative_to(self.program.project_root)
                alarm.unpreprocessed_source_file = alarm.unpreprocessed_source_file.relative_to(
                    self.program.project_root)

        # Write report file.
        with open(self.output_file_path, 'w') as f:
            json.dump([a.as_dict() for a in alarms], f, indent=4)

        # Clean desugared source files and associated log files.
        if not self.keep_intermediary_files:
            logger.debug("Cleaning intermediary results from tmp directory.")
            if os.path.exists(self.__tmp_path):
                shutil.rmtree(self.__tmp_path)

            logger.debug("Cleaning intermediary results from subject system.")
            self.program.clean_intermediary_results()

    def desugar(self, unpreprocessed_file: Path) -> Tuple[Path, Path, Path, float]:  # God, what an ugly tuple
        included_directories, included_files, cmd_decs, recommended_space = self.get_inc_files_and_dirs_for_file(
            unpreprocessed_file)
        start = time.monotonic()
        # noinspection PyTypeChecker
        desugared_file_location, log_file = (
            SugarCRunner.desugar_file(file_to_desugar=unpreprocessed_file,
                                      recommended_space=None,
                                      remove_errors=self.remove_errors,
                                      config_prefix=self.config_prefix,
                                      whitelist=self.whitelist,
                                      no_stdlibs=True,
                                      included_files=included_files,
                                      included_directories=included_directories,
                                      commandline_declarations=cmd_decs,
                                      keep_mem=self.tool.keep_mem,
                                      make_main=self.tool.make_main,
                                      cache_dir_path=self.cache_dir_path / Path("SugarC") / Path(self.program.name),
                                      desugaring_function_whitelist=self.tool.desugaring_function_whitelist,
                                      maximum_heap_size=self.maximum_heap_per_job,
                                      no_logfile=self.logfiles_disabled))

        return desugared_file_location, log_file, unpreprocessed_file, time.monotonic() - start

    def analyze_read_and_process(self, desugared_file: Path, original_file: Path, desugaring_time: float = None) -> \
            Iterable[Alarm]:
        included_directories, included_files, cmd_decs, user_defined_space = (
            self.get_inc_files_and_dirs_for_file(original_file))

        alarms = self.tool.analyze_file_and_read_alarms(desugared_source_file=desugared_file,
                                                        unpreprocessed_source_file=original_file,
                                                        included_files=included_files,
                                                        included_dirs=included_directories)

        processed_alarms = process_alarms(alarms, desugared_file)

        for a in processed_alarms:
            a.desugaring_time = desugaring_time
        return processed_alarms

    def analyze_file_and_associate_configuration(self,
                                                 desugared_source_file: Path,
                                                 unpreprocessed_source_file: Path,
                                                 config: Path,
                                                 program_specification: ProgramSpecification) -> Iterable[
        Alarm]:
        def get_config_object(config: Path) -> List[Tuple[str, str]]:
            with open(config, 'r') as f:
                lines = [l.strip() for l in f.readlines()]

            result = []
            for x in lines:
                if x.startswith("#"):
                    result.append((x[1:].strip().split(" ")[0], False))
                else:
                    result.append(((toks := x.strip().split("="))[0], toks[1]))

            return result

        alarms_from_one_file = self.analyze_one_file(desugared_source_file=desugared_source_file,
                                                     unpreprocessed_source_file=unpreprocessed_source_file,
                                                     program_specification=program_specification)
        for a in alarms_from_one_file:
            a.model = get_config_object(config)
        return alarms_from_one_file

def get_arguments() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    # Mandatory parameters.
    p.add_argument("tool", help="The tool to run.")
    p.add_argument("program", help="The target program.")
    p.add_argument("program_path", help="The absolute path to the target program.")

    # Options.
    p.add_argument("-v", help="Print debug messages instead of only info messages to the console.",
                   dest="verbosity", action="store_true")
    p.add_argument("--no-logfiles", help="Disable the creation of logfiles and log only to the command line",
                   dest="logfiles_disabled", action="store_true")
    p.add_argument("--output-path", help="The path to the file to which the output is going to be written. "
                                         "If None, will write to ~/sugarlyzer-results.json")
    p.add_argument("--tmp-path", help="The path to the tmp directory that will be used for storing intermediary "
                                      "results.")
    p.add_argument("--jobs", help="The number of jobs to use. If None, will use all CPUs", type=int)
    p.add_argument("--max-heap-per-job", help="The maximum JVM heap size allocated to each job (--jobs) in gigabytes.",
                   type=int)

    p.add_argument("--keep-intermediary-files", action="store_true",
                   help="Keep all intermediary files (desugared source files, desugaring logs, system and project headers, ...).")
    p.add_argument("--relative-paths", action="store_true",
                   help="Use relative paths to the subject system's root directory in the report file.")

    p.add_argument("--no-recommended-space", help="""Do not generate a recommended space.""", action='store_true')
    p.add_argument("--validate",
                   help="""Try running desugared alarms with Z3's configuration to see if they are retained.""",
                   action='store_true')
    return p.parse_args()


def set_up_logging(args: argparse.Namespace) -> None:
    # Ensure that directory for logfile exists.
    logging_dir: Path = Path.home() / Path(".vari-joern")
    logging_dir.mkdir(exist_ok=True, parents=True)

    # Set logging levels.
    logging_level_console: int = logging.DEBUG if args.verbosity else logging.INFO
    logging_level_logfile: int = logging.DEBUG  # Always use DEBUG level for logfile.
    root_logger = logging.getLogger()
    root_logger.setLevel(logging.DEBUG)  # Default level.

    # Create and customize handlers.
    console_handler: logging.StreamHandler[TextIO] = logging.StreamHandler()
    console_handler.setLevel(logging_level_console)
    logfile_handler: FileHandler = logging.FileHandler(logging_dir / Path(f"sugarlyzer_{args.tool}_{args.program}.log"),
                                                       'w')
    logfile_handler.setLevel(logging_level_logfile)
    formatter = logging.Formatter("%(asctime)s %(name)s [%(levelname)s - %(process)d] %(message)s")
    console_handler.setFormatter(formatter)
    logfile_handler.setFormatter(formatter)

    # Add handlers to logger.
    root_logger.addHandler(console_handler)
    if not args.logfiles_disabled:
        root_logger.addHandler(logfile_handler)

    root_logger.propagate = False


def main():
    start = time.monotonic()
    args = get_arguments()

    # Allows to add additional paths to the PATH environment variable via the SUGARLYZER_PATH variable, if manual
    # changes are not possible (e.g., because a virtual env sets the PATH).
    if os.getenv("SUGARLYZER_PATH") is not None:
        os.environ["PATH"] += os.pathsep + os.environ["SUGARLYZER_PATH"]

    set_up_logging(args)

    logger.info(f"Executing interpreter: {sys.executable} (version {sys.version})")

    t = Tester(args)
    t.execute()
    logger.info(f"Total execution time of Sugarlyzer: {time.monotonic() - start}s")


if __name__ == '__main__':
    main()
