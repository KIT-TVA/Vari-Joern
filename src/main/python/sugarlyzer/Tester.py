import argparse
import copy
import functools
import importlib
import json
import logging
import os
import shutil
import tempfile
import time
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path
from typing import Iterable, List, Dict, Any, Tuple

# noinspection PyUnresolvedReferences
from dill import pickle
from jsonschema.validators import RefResolver, Draft7Validator
from pathos.pools import ProcessPool
# noinspection PyUnresolvedReferences
from tqdm import tqdm
# noinspection PyUnresolvedReferences
from z3.z3 import Solver, sat, Bool, Int, Not, And, Or

from python.sugarlyzer import SugarCRunner
from python.sugarlyzer.SugarCRunner import process_alarms
from python.sugarlyzer.analyses.AbstractTool import AbstractTool
from python.sugarlyzer.analyses.AnalysisToolFactory import AnalysisToolFactory
from python.sugarlyzer.models.Alarm import Alarm, same_range
from python.sugarlyzer.models.ProgramSpecification import ProgramSpecification
from python.sugarlyzer.models.ProgramSpecificationFactory import ProgramSpecificationFactory

logger = logging.getLogger(__name__)


class Tester:
    def __init__(self, args: argparse.Namespace):
        # args.tool, args.program, args.program_path, args.baselines, True, args.jobs, args.validate
        #
        # tool: str, program: str, program_path: str, baselines: bool, no_recommended_space: bool,
        # superc_path: str = None, jobs: int = None, validate: bool = False):

        self.baselines = args.baselines
        self.no_recommended_space = True
        self.jobs: int = max(args.jobs if args.jobs is not None else os.cpu_count() or 1, 1)
        self.maximum_heap_per_job = args.max_heap_per_job
        self.validate = args.validate if args.validate is not None else False
        self.verbosity = args.verbosity
        self.keep_desugaring_files: bool = True if args.keep_desugared_files is not None else False

        # Set paths.
        tmp_path = args.tmp_path if args.tmp_path is not None else tempfile.TemporaryDirectory(
            prefix="vari-joern-").name
        self.intermediary_results_path = Path(tmp_path) / Path("family-based-analysis")
        self.intermediary_results_path.mkdir(exist_ok=True, parents=True)

        self.cache_dir_path = Path.home() / Path(".vari-joern/sugarlyzer-cache")
        self.cache_dir_path.mkdir(exist_ok=True, parents=True)

        self.output_file_path = args.output_path if args.output_path is not None else Path.home() / Path(
            "sugarlyzer-results.json")
        self.output_file_path.parent.mkdir(exist_ok=True, parents=True)

        def read_json_and_validate(file: str) -> Dict[str, Any]:
            """
            Given a JSON file that corresponds to a program specification,
            we read it in and validate that it conforms to the schema (resources.programs.program_schema.json)

            :param file: The program file to read.
            :return: The JSON representation of the program file. Throws an exception if the file is malformed.
            """
            with open(importlib.resources.path(f'resources.sugarlyzer.programs', 'program_schema.json'),
                      'r') as schema_file:
                resolver = RefResolver.from_schema(schema := json.load(schema_file))
                validator = Draft7Validator(schema, resolver)
            with open(file, 'r') as program_file:
                result = json.load(program_file)
            validator.validate(result)
            return result

        program_as_json = read_json_and_validate(
            importlib.resources.path(f'resources.sugarlyzer.programs.{args.program}', 'program.json'))
        self.program: ProgramSpecification = ProgramSpecificationFactory.get_program_specification(
            name=args.program,
            program_json=program_as_json,
            source_dir=args.program_path)
        self.tool: AbstractTool = AnalysisToolFactory().get_tool(tool_name=args.tool,
                                                                 intermediary_results_path=self.intermediary_results_path,
                                                                 maximum_heap_size=self.maximum_heap_per_job)
        self.remove_errors = self.tool.remove_errors if self.program.remove_errors is None else self.program.remove_errors
        self.config_prefix = self.program.config_prefix
        self.whitelist = self.program.whitelist
        self.kgen_map = self.program.kgen_map

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

    def analyze_one_file(self, fi: Path, ps: ProgramSpecification) -> Iterable[Alarm]:
        inc_files, inc_dirs, cmd_decs = ps.inc_files_and_dirs_for_file(fi)
        alarms = self.tool.analyze_file_and_read_alarms(fi, command_line_defs=cmd_decs,
                                                        included_files=inc_files,
                                                        included_dirs=inc_dirs)
        return alarms

    @staticmethod
    def configure_code(program: ProgramSpecification, config: Path):
        logger.info(f"Running configuration {config.name}")
        # Copy config to .config
        cwd = os.curdir
        os.chdir(program.make_root)
        logger.debug(f"Copying {config.name} to {program.oldconfig_location}")
        shutil.copyfile(config, program.oldconfig_location)
        os.system('yes "" | make oldconfig')
        logger.debug("make finished.")
        os.chdir(cwd)
        # if cp.returncode != 0:
        #    logger.warning(f"Running command {' '.join(make_cmd)} resulted in a non-zero error code.\n"
        #                   f"Output was:\n" + cp.stdout)

    @staticmethod
    def clone_program_and_configure(ps: ProgramSpecification, config: Path) -> ProgramSpecification:
        """Clones a program spec to a new directory, and returns a program spec with updated search context."""
        code_dest = Path("/targets") / Path(config.name) / Path(ps.project_root.name)
        code_dest.parent.mkdir(parents=True)
        logger.debug(f"Cloning {ps.project_root} to {code_dest}")

        shutil.copytree(ps.project_root, code_dest)
        ps_copy = copy.deepcopy(ps)
        ps_copy.search_context = code_dest.parent
        Tester.configure_code(ps_copy, config)
        return ps_copy

    def execute(self):
        logger.info(f"PYTHONPATH is {os.environ.get('PYTHONPATH')}")
        logger.info(f"Current environment is {os.environ}")
        if not self.baselines:
            ###################################
            # Run SugarC.
            ###################################
            logger.info(f"Desugaring the source code in {self.program.source_directory} with {self.jobs} jobs...")
            source_files = list(self.program.get_source_files())
            desugared_files: List[Tuple] = []

            with ProcessPoolExecutor(max_workers=self.jobs) as executor:
                # Submit tasks.
                desugar_tasks = [executor.submit(self.desugar, file) for file in source_files]

                # Collect results.
                for desugar_task in tqdm(desugar_tasks, total=len(source_files)):
                    try:
                        desugared_file = desugar_task.result()
                        desugared_files.append(desugared_file)
                    except Exception as e:
                        logger.exception(f"Error during desugaring: {e}")

            logger.info(f"Finished desugaring the source code.")
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

            with ProcessPoolExecutor(max_workers=self.jobs) as executor:
                # Submit tasks.
                analysis_tasks = [executor.submit(self.analyze_read_and_process, *(d, o, dt))
                                  for d, _, o, dt in desugared_files]

                # Collect results.
                for analysis_task in tqdm(analysis_tasks, total=len(desugared_files)):
                    try:
                        results = analysis_task.result()
                        alarms.extend(results)
                    except Exception as e:
                        logger.exception(f"Error during analysis: {e}")

            logger.info(f"Got {len(alarms)} unique alarms from the analysis tool.")

            ###################################
            # Post processing of alarms.
            ###################################
            buckets: List[List[Alarm]] = [[]]

            def alarm_match(a: Alarm, b: Alarm):
                return (a.input_file == b.input_file
                        and a.feasible == b.feasible
                        and a.sanitized_message == b.sanitized_message
                        and same_range(a.original_line_range, b.original_line_range))

            # Collect alarms into "buckets" based on equivalence.
            # Then, for each bucket, we will return one alarm, combining all the
            # models into a list.
            logger.debug("Now deduplicating results.")
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

            logger.debug(f"Sorted the {len(alarms)} alarms into {len(buckets) - 1} buckets "
                         f"({bucket_matches} bucket matches).")

            # Aggregate alarms and join their presence conditions via disjunctions.
            logger.debug("Now aggregating alarms.")
            alarms = []
            for bucket in (b for b in buckets if len(b) > 0):
                alarms.append(bucket[0])
                alarms[-1].presence_condition = f"Or({','.join(str(m.presence_condition) for m in bucket)})"
            logger.debug("Done.")
            logger.info(f"{len(alarms)} alarms remain after aggregation.")

            if self.validate:
                logger.debug("Now validating....")
                with ProcessPool(self.jobs) as p:
                    alarms = list(tqdm(p.imap(self.verify_alarm, alarms)))
        else:
            alarms = self.run_baseline_experiments()

        logger.debug(f"Writing alarms to file \"{self.output_file_path}\"")

        # Assign unique ids to the alarms (ProcessPoolExecutor leads to overlapping ids and postprocessing can
        # create discontinuations).
        alarm_id: int = 0
        for alarm in alarms:
            alarm.id = alarm_id
            alarm_id += 1

        with open(self.output_file_path, 'w') as f:
            json.dump([a.as_dict() for a in alarms], f, indent=4)

        # Clean desugared source files and associated log files.
        if not self.keep_desugaring_files:
            logger.debug("Cleaning intermediary results from subject system.")
            self.program.clean_intermediary_results()

    def desugar(self, file: Path) -> Tuple[Path, Path, Path, float]:  # God, what an ugly tuple
        included_directories, included_files, cmd_decs, recommended_space = self.get_inc_files_and_dirs_for_file(
            file)
        start = time.monotonic()
        # noinspection PyTypeChecker
        desugared_file_location, log_file = (
            SugarCRunner.desugar_file(file,
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
                                      cache_dir_path=self.cache_dir_path,
                                      desugaring_function_whitelist=self.tool.desugaring_function_whitelist,
                                      maximum_heap_size=self.maximum_heap_per_job))

        return desugared_file_location, log_file, file, time.monotonic() - start

    def analyze_read_and_process(self, desugared_file: Path, original_file: Path, desugaring_time: float = None) -> \
            Iterable[Alarm]:
        included_directories, included_files, cmd_decs, user_defined_space = (
            self.get_inc_files_and_dirs_for_file(original_file))

        alarms = self.tool.analyze_file_and_read_alarms(desugared_file,
                                                        included_files=included_files,
                                                        included_dirs=included_directories)

        processed_alarms = process_alarms(alarms, desugared_file)

        for a in processed_alarms:
            a.desugaring_time = desugaring_time
        return processed_alarms

    def verify_alarm(self, alarm):
        alarm = copy.deepcopy(alarm)
        alarm.verified = "UNVERIFIED"
        if not alarm.feasible:
            logger.debug(f'infeasible alarm left unverified')
            return alarm
        logger.debug(f"Constructing model {alarm.model}")
        if alarm.model is not None:
            config_string = ""
            for k, v in alarm.model.items():
                mappedKey = k
                mappedValue = v
                # TODO Rework.
                if self.kgen_map != None:
                    with open(self.program.project_root / Path("config/mapping.json"), 'w') as mapping:
                        map = json.load(mapping)
                    kdef = k
                    if v.lower() == 'false':
                        kdef = '!' + kdef
                    if kdef in map.keys():
                        toParse = map[kdef]
                        if toParse.startswith('DEF'):
                            mappedKey = toParse
                            mappedValue = 'True'
                        elif toParse.startswith('!DEF'):
                            mappedKey = toParse[1:]
                            mappedValue = 'False'
                        else:
                            mappedKey = toParse.split(' == ')[0]
                            mappedValue = toParse.split(' == ')[1]
                if mappedKey.startswith('DEF_'):
                    match mappedValue.lower():
                        case 'true':
                            config_string += f"{mappedKey[4:]}=y\n"
                        case 'false':
                            config_string += f"{mappedKey[4:]}=n\n"
                elif k.startswith('USE_'):
                    config_string += f"{mappedKey[4:]}={mappedValue}\n"
                else:
                    logger.critical(f"Ignored constraint {str(mappedKey)}={str(mappedValue)}")
            if config_string == "":
                return alarm
            loggable_config_string = config_string.replace("\n", ", ")
            logger.debug(f"Configuration is {loggable_config_string}")
            ntf = tempfile.NamedTemporaryFile(delete=False, mode="w")
            ntf.write(config_string)
            ps: ProgramSpecification = self.clone_program_and_configure(self.program, Path(ntf.name))
            updated_file = str(alarm.input_file.absolute()).replace('/targets',
                                                                    f'/targets/{Path(ntf.name).name}').replace(
                '.desugared', '')
            updated_file = Path(updated_file)
            logger.debug(f"Mapped file {alarm.input_file} to {updated_file}")
            verify = self.analyze_file_and_associate_configuration(updated_file, Path(ntf.name), ps)
            logger.debug(
                f"Got the following alarms {[json.dumps(b.as_dict()) for b in verify]} when trying to verify alarm {json.dumps(alarm.as_dict())}")
            ntf.close()
            for v in verify:
                logger.debug(f"Comparing alarms {alarm.as_dict()} and {v.as_dict()}")
                if alarm.sanitized_message == v.sanitized_message and \
                        alarm.verified not in ["FUNCTION_LEVEL", "FULL"]:
                    alarm.verified = "MESSAGE_ONLY"
                try:
                    if alarm.sanitized_message == v.sanitized_message and \
                            alarm.function_line_range[1].includes(v.line_in_input_file) and \
                            alarm.verified != "FULL":
                        alarm.verified = "FUNCTION_LEVEL"
                except ValueError as ve:
                    pass
                try:
                    if alarm.sanitized_message == v.sanitized_message and \
                            alarm.original_line_range.includes(v.line_in_input_file):
                        alarm.verified = "FULL"
                        break  # no need to continue
                except ValueError as ve:
                    pass
            logger.debug(f"Alarm with validation updated: {alarm.as_dict()}")
            return alarm
        else:
            return alarm

    def analyze_file_and_associate_configuration(self, file: Path, config: Path, ps: ProgramSpecification) -> Iterable[
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

        alarms_from_one_file = self.analyze_one_file(file, ps)
        for a in alarms_from_one_file:
            a.model = get_config_object(config)
        return alarms_from_one_file

    def run_baseline_experiments(self) -> Iterable[Alarm]:
        alarms: List[Alarm] = []
        count = 0
        count += 1

        logger.info("Performing code cloning for baseline experiments:")

        spec_config_pairs: List[Tuple[ProgramSpecification, Path]] = []
        all_configs = list(self.program.get_baseline_configurations())
        logger.debug(f"All configs are {all_configs}")
        i = 0

        with ProcessPool(self.jobs) as p:
            for result in tqdm(p.imap(lambda x: (self.clone_program_and_configure(self.program, x), x),
                                      all_configs),
                               total=len(all_configs)):
                # logger.info(f"Copying configuration {i}/1000")
                i += 1
                spec_config_pairs.append(result)

        logger.debug(f"Config pairs is {list((ps.search_context, x) for ps, x in spec_config_pairs)}")
        source_files_config_spec_triples: List[Tuple[Path, Path, ProgramSpecification]] = []
        for spec, config in spec_config_pairs:
            source_files_config_spec_triples.extend((fi, config, spec) for fi in spec.get_source_files())

        logger.info("Running analysis:")
        logger.debug(f"Running analysis on pairs {source_files_config_spec_triples}")
        with ProcessPool(self.jobs) as p:
            alarms = list()
            for i in tqdm(
                    p.imap(lambda x: self.analyze_file_and_associate_configuration(*x),
                           source_files_config_spec_triples),
                    total=len(source_files_config_spec_triples)):
                alarms.extend(i)

        for alarm in alarms:
            alarm.get_recommended_space = (not self.no_recommended_space)
            alarm.remove_errors = self.remove_errors

        return alarms


def get_arguments() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument("tool", help="The tool to run.")
    p.add_argument("program", help="The target program.")
    p.add_argument("program_path", help="The absolute path to the target program.")

    p.add_argument("-v", dest="verbosity", action="store_true", help="Print debug messages.")
    p.add_argument("--output-path", help="The path to the file in which the output is going to be written.")
    p.add_argument("--tmp-path", help="The path to the tmp directory that will be used for storing intermediary "
                                      "results.")
    p.add_argument("--jobs", help="The number of jobs to use. If None, will use all CPUs", type=int)
    p.add_argument("--max-heap-per-job", help="The maximum JVM heap size allocated to each job in gigabytes (--jobs).",
                   type=int)
    p.add_argument("--keep-desugared-files", action="store_true",
                   help="Keep the desugared source files (.desugared.c) and associated log "
                        "files (.sugarc.log) alongside the original source files.")

    p.add_argument("--baselines", action="store_true",
                   help="""Run the baseline experiments. In these, we configure each 
                   file with every possible configuration, and then run the experiments.""")
    p.add_argument("--no-recommended-space", help="""Do not generate a recommended space.""", action='store_true')
    p.add_argument("--validate",
                   help="""Try running desugared alarms with Z3's configuration to see if they are retained.""",
                   action='store_true')
    return p.parse_args()


def set_up_logging(args: argparse.Namespace) -> None:
    if args.verbosity:
        logging_level = logging.DEBUG
    else:
        logging_level = logging.INFO

    logging_dir = Path.home() / Path(".vari-joern")
    logging_dir.mkdir(exist_ok=True, parents=True)

    logging_kwargs = {"level": logging_level,
                      "format": '%(asctime)s %(name)s [%(levelname)s - %(process)d] %(message)s',
                      "handlers": [
                          logging.StreamHandler(),
                          logging.FileHandler(os.path.join(logging_dir, "sugarlyzer.log"), 'w')
                      ]
                      }

    logging.basicConfig(**logging_kwargs)


def main():
    start = time.monotonic()
    args = get_arguments()

    set_up_logging(args)
    t = Tester(args)
    t.execute()
    logger.info(f"Total execution time of Sugarlyzer: {time.monotonic() - start}s")


if __name__ == '__main__':
    main()
