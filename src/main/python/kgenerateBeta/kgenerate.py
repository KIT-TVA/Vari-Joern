import argparse
import json
import os
import pickle
import re
import tempfile
from pathlib import Path


class kvar:
   def __init__(self,name,type):
      self.name = name
      self.type = type
      self.promptable = False
      self.default = None
      self.cond = None
   def processClause(self, cond, genvars):
      self.cond = parseSMT(cond,genvars)
   def define(self, format):
      default = str(self.default)
      alt = ''
      if self.default == None:
         default = '"X"' if self.type == 'string' else '1'
      alt = '""' if self.type == 'string' else '0'
      values = [self.name,default,alt]
      start = f'#ifdef KGENMACRO_{self.name}\n'
      end = f'#endif\n'
      key = 'default'
      if self.type in format.keys():
         key = self.type
      return start + format[key].replace('$0',self.name).replace('$1',default).replace('$2',alt) + end
   def addMap(self, maps, defining):
      if self.type == 'bool' and not defining:
         maps[f'DEF_KGENMACRO_{self.name}'] = f'DEF_{self.name}'
         maps[f'!DEF_KGENMACRO_{self.name}'] = f'!DEF_{self.name}'
      else:
         default = str(self.default)
         alt = ''
         if self.default == None:
            default = '"X"' if self.type == 'string' else '1'
         alt = '""' if self.type == 'string' else '0'
         maps[f'DEF_KGENMACRO_{self.name}'] = f'USE_{self.name} == {default}'
         maps[f'!DEF_KGENMACRO_{self.name}'] = f'USE_{self.name} == {alt}'
   def __str__(self):
      return f'{self.name}({self.type})={self.default}{" or ?" if self.promptable else ""}'
   def __repr__(self):
      return self.__str__()

def read_arguments() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="A tool to parse kmax output and generate config header files for configuration exploration")
    p.add_argument('-d', '--directory', help='root directory for kbuild', default="./")
    p.add_argument('-m', '--module-version', help='module version for kextract', default="3.19")
    p.add_argument('-i', '--input', help='kbuild file', default="Config.in")
    p.add_argument('-o', '--output', help='Directory to output header and mapping to',default=f"{Path.cwd()}")
    p.add_argument('-f', '--format', help='Format of the output',required=True)
    p.add_argument('--define-false', action='store_true',
                   help='Instead of bools being either defined or undefined, define them as 1 or 0')
    return p.parse_args()

def findClause(string, start):
   i = start
   count = 0
   while i < len(string):
      if string[i] == '(':
         count += 1
      elif string[i] == ')':
         count -= 1
      if count == 0:
         return i+1
      i += 1
   print('illegal parens',string, start)
   exit()

def processLet(cond,predefs):
   stripped = cond[len('(let '):-1]
   firstClause = findClause(stripped,0)
   firstClause = stripped[0:firstClause]
   #find xvars
   var = re.search('\(\((\$x\d+) ',stripped)
   var = var.group(1)
   #if x var already exists, ignore
   if var not in predefs.keys():
      subcond = firstClause[3+len(var):-2].lstrip().rstrip()
      predefs[var] = processSMT(subcond,predefs)
   #else process SMT on  the x var and create a mapping
   return processSMT(stripped[len(firstClause):].lstrip(),predefs)

def processLogic(cond,predefs,op):
   parts = cond[:-1].split(' ')
   parts = parts[1:]
   i = 0
   while i < len(parts):
      while parts[i].count('(') != parts[i].count(')'):
         parts[i] = parts[i] + ' ' + parts[i+1]
         parts.pop(i+1)
      i += 1
   processed = []
   for p in parts:
      processed.append(processSMT(p.lstrip().rstrip(),predefs))
   return '(' + op.join(processed) + ')'

def processNot(cond,predefs):
   return '(!' + processSMT(cond[len('(not '):-1].lstrip().rstrip(),predefs) + ')'

def processSolo(cond,predefs):
   return 'defined KGENMACRO_' + cond[len('CONFIG_'):]

def processPredef(cond,predefs):
   if cond not in predefs.keys():
      print('var not found',cond)
      exit()
   return '(' + 'KGEN_' + cond[1:] + ')'

def processSMT(cond, predefs):
   if cond.startswith('(let'):
      return processLet(cond,predefs)
   elif cond.startswith('(or'):
      return processLogic(cond,predefs, '||')
   elif cond.startswith('(and'):
      return processLogic(cond,predefs,'&&')
   elif cond.startswith('(not'):
      return processNot(cond,predefs)
   elif cond.startswith('CONFIG_'):
      return processSolo(cond,predefs)
   elif cond.startswith('$x'):
      return processPredef(cond,predefs)
   else:
      print('cant handle',cond)
   return None

def parseSMT(stmts, predefs):
   parts = []
   for s in stmts:
      if '(assert' in s and ')\n(check-sat)\n' in s:
         assertion = s.split('assert\n')[1]
         assertion = assertion[:-len(')\n(check-sat)\n')]
         assertion = assertion.replace('\n',' ')
         assertion = assertion.lstrip().rstrip()
         parts.append(processSMT(assertion,predefs))
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
               allConfigs.append(os.path.join(root,file))
      return allConfigs
   return [inputs]

def parseClause(input,kvars):
   genvars = {}
   choice = None
   with open(input, 'rb') as inputFile:
      clauseParse = pickle.load(inputFile)
   for k,v in clauseParse.items():
      if k == '<CHOICE>':
         choice = parseSMT(v,genvars)
      for kv in kvars:
         if 'CONFIG_'+kv.name == k:
            kv.processClause(v,genvars)
   return genvars, choice


def parseExtract(input):
   kvars = []
   choicesEncountered=0
   with open(input) as f:
      for l in f:
         l = l.lstrip().rstrip()
         if l.startswith('config'):
            ls = l.split(' ')
            var = ls[1][len('CONFIG_'):]
            type = ls[2]
            kvars.append(kvar(var,type))
         elif l.startswith('prompt'):
            ls = l.split(' ')
            var = ls[1][len('CONFIG_'):]
            thisVar = None
            for k in kvars:
               if k.name == var:
                  thisVar = k
                  break
            if thisVar == None:
               continue
            k.promptable = True
         elif l.startswith('def_'):
            ls = l.split(' ')
            var = ls[1][len('CONFIG_'):]
            thisVar = None
            for k in kvars:
               if k.name == var:
                  thisVar = k
                  break
            if thisVar == None:
               continue
            k.default = ' '.join(ls[2:]).split('|')[0]
            if k.type == 'number':
               k.default = k.default.split('"')[1]
         elif l.startswith('bool_choice'):
            #ignoreForNow
            continue
            vars = l.split('|')[0].split(' ')[1:]
            choiceName = 'KGENMACRO_CHOICE'+str(choicesEncountered)
            choicesEncountered += 1
            options = 1
            for v in vars:
               v = v[len('CONFIG_'):]
               for k in kvars:
                  if v == k.name:
                     k.choices.append((choiceName,options))
                     options = options + 1
   return kvars

def formatCond(condition, variables, defining):
   if condition == '1' or condition == None:
      return None
   newCond = condition.replace(' and ', ' && ')
   newCond = newCond.replace(' or ', ' || ')
   for v in variables:
      if v.name in condition:
         if v.type == 'bool' and not defining:
            rep = 'defined ' + v.name
         else:
            rep = v.name
         newCond = newCond.replace('CONFIG_'+v.name,rep)
         newCond = newCond.replace('"'+v.name+'"',rep)
   newCond = newCond.replace('not ', '!')
   return newCond

def parseFormat(format):
   res = {}
   with open(format, 'r') as formatFile:
      type = ''
      text = ''
      for l in formatFile.readlines():
         if l.startswith(':'):
            temp = l.split(':')[1]
            if temp == 'end':
               res[type] = text
               text = ""
            else:
               type = temp
         else:
            text += l
   return res


def generateHeader(vars, prevars, choice, format):
   toReturn = ''
   outputFormat = parseFormat(format)
   print(outputFormat)
   for p,v in prevars.items():
      toReturn += f'#define KGEN_{p[1:]} ({v})\n'
#   if choice != None:
#      toReturn += f"#if ({choice})\n"
   for v in vars:
      if v.cond != None:
         toReturn += '#if ' + v.cond + '\n'
      toReturn += v.define(outputFormat)
      if v.cond != None:
         toReturn += '#endif\n'
#   if choice != None:
#      toReturn += f"#else\n#error\n#endif"
   return toReturn

def printMapping(file, vars, defining):
   maps = {}
   for v in vars:
      v.addMap(maps,defining)
   file.write(json.dumps(maps, indent=4))

def run_kgenerate(kconfig_file_path: Path,
                  output_directory_path: Path,
                  format_file_path: Path,
                  source_tree_path: Path = None,
                  module_version: str = "3.19",
                  define_false: bool = True):
    kextract_file, kextract_tmp = tempfile.mkstemp()
    kclause_file, kclause_tmp = tempfile.mkstemp()
    cur_dir = Path.cwd()

    os.chdir(source_tree_path) # Achieves the same as --extract -e srctree=/path/to/srctree
    os.system(f'kextract --module-version {module_version} --extract {kconfig_file_path} >> {kextract_tmp}')
    os.system(f'kclause < {kextract_tmp} > {kclause_tmp}')

    kvars = parseExtract(kextract_tmp)
    genvars, choice = parseClause(kclause_tmp,kvars)

    os.chdir(cur_dir)
    os.system(f'cp {kextract_tmp} tmp')
    os.system(f'cp {kclause_tmp} tmp2')
    os.remove(kextract_tmp)
    os.remove(kclause_tmp)

    for k in kvars:
        k.cond = formatCond(k.cond, kvars, define_false)

    # Write output.
    with open(output_directory_path / Path('Config.h'),'w') as header_file:
        header_file.write(generateHeader(kvars, genvars, choice, format_file_path))
    with open(output_directory_path / Path('mapping.json'), 'w') as mapping_file:
        printMapping(mapping_file, kvars, define_false)

# TODO Debug why old implementation produced 822 lines in Config.h, whereas this version produces only 818 lines.

def main() -> None:
    args = read_arguments()
    run_kgenerate(kconfig_file_path=args.input,
                  output_directory_path=args.output,
                  format_file_path=args.format,
                  source_tree_path=args.directory,
                  module_version=args.module_version,
                  define_false=args.define_false)

if __name__ == '__main__':
   main()

