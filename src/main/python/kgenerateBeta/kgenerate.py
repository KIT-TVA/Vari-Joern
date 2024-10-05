import argparse
import json
import logging
import os
import pickle
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from subprocess import CompletedProcess
from typing import TextIO

logger = logging.getLogger(__name__)
config_macro_prefix:str = "KGENMACRO_"

class ConfigurationVariable:
    def __init__(self, name: str, data_type: str):
        self.name = name
        self.type = data_type
        self.promptable: bool = False
        self.default = None
        self.cond: str | None = None

    def define(self, format):
        global config_macro_prefix

        default = str(self.default)
        alt = ''
        if self.default == None:
            default = '"X"' if self.type == 'string' else '1'
        alt = '""' if self.type == 'string' else '0'
        values = [self.name, default, alt]
        start = f'#ifdef {config_macro_prefix}{self.name}\n'
        end = f'#endif\n'
        key = 'default'
        if self.type in format.keys():
            key = self.type
        return start + format[key].replace('$0', self.name).replace('$1', default).replace('$2', alt) + end

    def addMap(self, maps, defining):
        global config_macro_prefix

        if self.type == 'bool' and not defining:
            maps[f'DEF_{config_macro_prefix}{self.name}'] = f'DEF_{self.name}'
            maps[f'!DEF_{config_macro_prefix}{self.name}'] = f'!DEF_{self.name}'
        else:
            default = str(self.default)
            alt = ''
            if self.default == None:
                default = '"X"' if self.type == 'string' else '1'
            alt = '""' if self.type == 'string' else '0'
            maps[f'DEF_{config_macro_prefix}{self.name}'] = f'USE_{self.name} == {default}'
            maps[f'!DEF_{config_macro_prefix}{self.name}'] = f'USE_{self.name} == {alt}'

    def __str__(self):
        return f'{self.name}({self.type})={self.default}{" or ?" if self.promptable else ""}'

    def __repr__(self):
        return self.__str__()


def findClause(string, start):
    i = start
    count = 0
    while i < len(string):
        if string[i] == '(':
            count += 1
        elif string[i] == ')':
            count -= 1
        if count == 0:
            return i + 1
        i += 1
    print('illegal parens', string, start)
    exit()


def process_let(cond, predefs):
    stripped = cond[len('(let '):-1]
    firstClause = findClause(stripped, 0)
    firstClause = stripped[0:firstClause]
    # find xvars
    var = re.search(r'\(\((\$x\d+) ', stripped)
    var = var.group(1)
    # if x var already exists, ignore
    if var not in predefs.keys():
        subcond = firstClause[3 + len(var):-2].lstrip().rstrip()
        predefs[var] = process_smt(subcond, predefs)
    # else process SMT on  the x var and create a mapping
    return process_smt(stripped[len(firstClause):].lstrip(), predefs)


def process_logic(cond, predefs, logical_operator: str):
    # Remove (OPERATOR and ) from the beginning and end, respectively.
    parts: list[str] = cond[:-1].split(' ')
    parts = parts[1:]

    # Identify operator sub-statements (need ti be correctly encapsulated within parentheses).
    i = 0
    while i < len(parts):
        while parts[i].count('(') != parts[i].count(')'):
            parts[i] = parts[i] + ' ' + parts[i + 1]
            parts.pop(i + 1)
        i += 1

    # Recursively descend for the sub-statements.
    processed = []
    for p in parts:
        processed.append(process_smt(p.strip(), predefs))
    return '(' + logical_operator.join(processed) + ')'


def process_not(cond, predefs):
    substatement: str = cond[len('(not '):-1].strip()
    return '(!' + process_smt(substatement, predefs) + ')'


def process_solo(cond, predefs):
    global config_macro_prefix
    return f'defined {config_macro_prefix}' + cond[len('CONFIG_'):]


def processPredef(cond, predefs) -> str:
    if cond not in predefs.keys():
        logger.error('var not found', cond)
        exit()
    return '(' + 'KGEN_' + cond[1:] + ')'


def process_smt(cond, predefs):
    if cond.startswith('(let'):
        return process_let(cond, predefs)
    elif cond.startswith('(or'):
        return process_logic(cond, predefs, '||')
    elif cond.startswith('(and'):
        return process_logic(cond, predefs, '&&')
    elif cond.startswith('(not'):
        return process_not(cond, predefs)
    elif cond.startswith('CONFIG_'):
        return process_solo(cond, predefs)
    elif cond.startswith('$x'):
        return processPredef(cond, predefs)
    else:
        logger.warning(f"Cannot handle condition: {cond}")
    return None


def parse_smt(smt_lib_stmts: list[str], predefs) -> str:
    parts = []
    for smt_lib_stmt in smt_lib_stmts:
        if '(assert' in smt_lib_stmt and ')\n(check-sat)\n' in smt_lib_stmt:
            assertion: str = smt_lib_stmt.split('assert\n')[1]  # Everything that comes after 'assert\n'
            assertion = assertion[:-len(')\n(check-sat)\n')]  # Remove trailing ')\n(check-sat)\n'
            assertion = assertion.replace('\n', ' ')
            assertion = assertion.strip()
            parts.append(process_smt(assertion, predefs))
    return '(' + ') && ('.join(parts) + ')'


def getConfigFiles(inputs):
    if not os.path.exists(inputs):
        print(f'Input file/directory {inputs} does not exist')
        exit()
    if os.path.isdir(inputs):
        allConfigs = []
        for root, dirs, files in os.walk(inputs):
            for file in files:
                if file == "Config.in":
                    allConfigs.append(os.path.join(root, file))
        return allConfigs
    return [inputs]


def parse_kclause_output(kclause_file: str, configuration_variables: list[ConfigurationVariable]):
    genvars: dict = {}
    choice: str | None = None

    with open(kclause_file, 'rb') as input_file:
        clause_parse = pickle.load(input_file)

    # The kclause output contains logical formulas for each configuration options’ constraints in the SMT-LIB 2 format.
    # See also Oh et al. (https://doi.org/10.1145/3468264.3468578)
    for option, constraints in clause_parse.items():
        if option == '<CHOICE>':
            choice = parse_smt(constraints, genvars)
            continue

        for configuration_variable in configuration_variables:
            if 'CONFIG_' + configuration_variable.name == option:
                configuration_variable.cond = parse_smt(constraints, genvars)

    return genvars, choice


def parse_kextract_output(kextract_file: str):
    configuration_variables = []

    with open(kextract_file) as file:
        for line in file:
            line = line.lstrip().rstrip()

            # Case: Found definition of a configuration variable.
            # Exemplary structure: config CONFIG_MY_CONFIG_NAME type
            if line.startswith('config'):
                split_components: list[str] = line.split(' ')
                configuration_variable_name: str = split_components[1][len('CONFIG_'):]
                configuration_variable_type = split_components[2]
                configuration_variables.append(
                    ConfigurationVariable(configuration_variable_name, configuration_variable_type))
                continue

            # Cases: Found a definition of a prompt associated with a configuration variable or the definition of
            # its default value.
            # Exemplary structure: prompt CONFIG_MY_CONFIG_NAME (OTHER_CONFIG_EXPRESSION)
            # Exemplary structure: def_bool CONFIG_MY_CONFIG_NAME DEFAULT_VALUE|(CONFIG_MY_OTHER_CONFIG_NAME and
            # not CONFIG_MY_OTHER_OTHER_CONFIG_NAME)
            if line.startswith('prompt') or line.startswith('def_'):
                split_components: list[str] = line.split(' ')
                configuration_variable_name: str = split_components[1][len('CONFIG_'):]
                found_corresponding_variable: bool = False

                for configuration_variable in configuration_variables:
                    if configuration_variable.name == configuration_variable_name:
                        found_corresponding_variable = True
                        break

                if found_corresponding_variable is False:
                    continue

                if line.startswith('prompt'):
                    configuration_variable.promptable = True
                else:
                    configuration_variable.default = ' '.join(split_components[2:]).split('|')[0]
                    if configuration_variable.type == 'number':
                        configuration_variable.default = configuration_variable.default.split('"')[1]
                continue

            if line.startswith('bool_choice'):
                # ignoreForNow
                continue
                vars = line.split('|')[0].split(' ')[1:]

                global config_macro_prefix
                choiceName = f'{config_macro_prefix}CHOICE' + str(choices_encountered)
                choices_encountered += 1
                options = 1
                for v in vars:
                    v = v[len('CONFIG_'):]
                    for configuration_variable in configuration_variables:
                        if v == configuration_variable.name:
                            configuration_variable.choices.append((choiceName, options))
                            options = options + 1
    return configuration_variables


def format_cond(condition: str | None, configuration_variables: list[ConfigurationVariable], defining):
    if condition is None or condition == '1':
        return None

    new_cond: str = condition.replace(' and ', ' && ')
    new_cond = new_cond.replace(' or ', ' || ')
    new_cond = new_cond.replace('not ', '!')

    for configuration_variable in configuration_variables:
        if configuration_variable.name in condition:
            if configuration_variable.type == 'bool' and not defining:
                replacement: str = 'defined ' + configuration_variable.name
            else:
                replacement: str = configuration_variable.name

            new_cond = new_cond.replace('CONFIG_' + configuration_variable.name, replacement)
            new_cond = new_cond.replace('"' + configuration_variable.name + '"', replacement)
    return new_cond


def parse_format(format_file_path: Path) -> dict[str, str]:
    res = {}
    with open(format_file_path, 'r') as format_file:
        type = ''
        text = ''

        for line in format_file.readlines():
            if line.startswith(':'):
                temp = line.split(':')[1]
                if temp == 'end':
                    res[type] = text
                    text = ""
                else:
                    type = temp
            else:
                text += line
    return res


def generateHeader(configuration_variables: list[ConfigurationVariable],
                   prevars, choice: str | None,
                   format_file_path: Path):
    header_content: str = ''
    output_format = parse_format(format_file_path)
    logger.info(f"Header generation uses the following output format: {output_format}")

    for p, configuration_variable in prevars.items():
        header_content += f'#define KGEN_{p[1:]} ({configuration_variable})\n'
    #   if choice != None:
    #      header_content += f"#if ({choice})\n"

    for configuration_variable in configuration_variables:
        if configuration_variable.cond is not None:
            header_content += '#if ' + configuration_variable.cond + '\n'
        header_content += configuration_variable.define(output_format)
        if configuration_variable.cond is not None:
            header_content += '#endif\n'
    #   if choice != None:
    #      header_content += f"#else\n#error\n#endif"
    return header_content


def printMapping(mapping_file: TextIO, configuration_variables: list[ConfigurationVariable], define_false: bool):
    maps = {}
    for configuration_variable in configuration_variables:
        configuration_variable.addMap(maps, define_false)
    mapping_file.write(json.dumps(maps, indent=4))


def run_kgenerate(kconfig_file_path: Path,
                  format_file_path: Path,
                  header_output_path: Path = Path.cwd() / Path("Config.h"),
                  mapping_file_output_dir_path: Path = Path.cwd(),
                  tmp_directory_path: Path = None,
                  source_tree_path: Path = None,
                  config_prefix: str = None,
                  module_version: str = "3.19",
                  define_false: bool = True):
    # Set config macro prefix to something other than "KGENMACRO_" if desired.
    if config_prefix is not None:
        global config_macro_prefix
        config_macro_prefix = config_prefix

    # Create full header_output_path if only a target directory is provided.
    if header_output_path.is_dir():
        header_output_path = header_output_path / Path("config.h")

    # Create temp-files.
    tmp_directory = None
    if tmp_directory_path is None:
        tmp_directory = tempfile.TemporaryDirectory(prefix="vari-joern-kgenerate-")
        tmp_directory_path = Path(tmp_directory.name)

    kextract_file, kextract_tmp = tempfile.mkstemp(prefix="kextract-out-", dir=tmp_directory_path)
    kclause_file, kclause_tmp = tempfile.mkstemp(prefix="kclause-out-", dir=tmp_directory_path)

    # kextract and kclause documentation of the kmax project:
    # https://github.com/paulgazz/kmax/blob/master/docs/advanced.md#kclause

    ######################################################
    ### Run kextract on the Kconfig files of the project.
    ######################################################
    kextract_cmd: str = f'kextract --module-version {module_version} -e srctree={source_tree_path}  --extract {kconfig_file_path}'

    with open(kextract_file, "w") as stdout_target:
        logger.debug(f"Running kextract with command: {kextract_cmd}")
        process: CompletedProcess = subprocess.run(kextract_cmd, shell=True, stderr=sys.stderr, stdout=stdout_target)
        if process.returncode != 0:
            logger.warning(f"Call to kextract returned with exitcode {process.returncode}")

    ######################################################
    ### Run kclause on the output generated by kextract.
    ######################################################
    kclause_cmd = f'kclause < {kextract_tmp}'

    # Note that the output produced by kclause can change between executions. The number of items within stays the same
    # but the $xNUM identifiers often change.
    with open(kclause_file, "w") as stdout_target:
        logger.debug(f"Running kclause with command: {kclause_cmd}")
        process: CompletedProcess = subprocess.run(kclause_cmd, shell=True, stderr=sys.stderr, stdout=stdout_target)
        if process.returncode != 0:
            logger.warning(f"Call to kclause returned with exitcode {process.returncode}")

    configuration_variables: list[ConfigurationVariable] = parse_kextract_output(kextract_tmp)
    genvars, choice = parse_kclause_output(kclause_tmp, configuration_variables)

    for k in configuration_variables:
        k.cond = format_cond(k.cond, configuration_variables, define_false)

    # Write output.
    with open(header_output_path, 'w') as header_file:
        header_file.write(generateHeader(configuration_variables, genvars, choice, format_file_path))
    with open(mapping_file_output_dir_path / Path('kgenerate_macro_mapping.json'), 'w') as mapping_file:
        printMapping(mapping_file, configuration_variables, define_false)

    # If tmp_directory had to be created and was not provided do cleanup. Otherwise, caller is responsible for cleanup.
    if tmp_directory is not None:
        tmp_directory.cleanup()


def read_arguments() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="A tool to parse kmax output and generate config header files for configuration exploration")
    p.add_argument('-d', '--directory', help='root directory for kbuild', default="./")
    p.add_argument('-m', '--module-version', help='module version for kextract', default="3.19")
    p.add_argument('-i', '--input', help='kbuild file', default="Config.in")
    p.add_argument('-o', '--header-output',
                   help='Path to output the config header to. If pointing to a directory, the default name config.h will be used.',
                   default=f"{Path.cwd()}")
    p.add_argument('-p', '--mapping-output', help="The location where to put the mapping file.",
                   default=f"{Path.cwd()}")
    p.add_argument('-f', '--format', help='Path to a file specifying the format of the output',
                   required=True)
    p.add_argument('-t', '--tmp-dir', help='Path to the tmp directory that should be used')
    p.add_argument('-c', '--config-prefix',
                   help="The prefix that should be used for configuration related macros. Default is KGENMACRO_",)
    p.add_argument('--define-false', action='store_true',
                   help='Instead of bools being either defined or undefined, define them as 1 or 0')
    p.add_argument("-v", dest="verbosity", action="store_true", help="Print debug messages.")
    return p.parse_args()


def main() -> None:
    args = read_arguments()

    logging_level: int = logging.DEBUG if args.verbosity else logging.INFO

    logging_dir: Path = Path.home() / Path(".vari-joern")
    logging_dir.mkdir(exist_ok=True, parents=True)

    logging_kwargs = {"level": logging_level,
                      "format": '%(asctime)s %(name)s [%(levelname)s - %(process)d] %(message)s',
                      "handlers": [
                          logging.StreamHandler(),
                          logging.FileHandler(os.path.join(logging_dir, "kgenerate.log"), 'w')
                      ]
                      }
    logging.basicConfig(**logging_kwargs)

    run_kgenerate(kconfig_file_path=Path(args.input),
                  format_file_path=Path(args.format),
                  header_output_path=Path(args.header_output),
                  mapping_file_output_dir_path=Path(args.mapping_output),
                  source_tree_path=Path(args.directory),
                  config_prefix=args.config_prefix,
                  module_version=args.module_version,
                  define_false=args.define_false,
                  tmp_directory_path=None if args.tmp_dir is None else Path(args.tmp_dir))


if __name__ == '__main__':
    main()
