import argparse

from python.sugarlyzer.SugarCRunner import process_alarms
from python.sugarlyzer.models.Alarm import Alarm
from pathlib import Path

# Example call: python PresenceConditionDemo.py
# /home/tim/Desktop/MA_Workspace/sample-spls/SimpleCode/SimpleCodeVariabilityBugComplexDesugared.c 1298

argumentParser = argparse.ArgumentParser(description="A simple demo script determining the presence condition for "
                                                     "an arbitrary line of code in a source code file desugared by "
                                                     "SugarC.")
argumentParser.add_argument("path", help="The full path to the desugared file.", type=str)
argumentParser.add_argument("loc", help="The line of code for which the presence condition should be determined.",
                            type=int)
arguments = argumentParser.parse_args()

desugaredFilePath = Path(arguments.path)
lineNumber = arguments.loc
alarm = Alarm(input_file=desugaredFilePath,
              line_in_input_file=lineNumber)

print("Processing input... ", end='')
processedAlarms = process_alarms([alarm], desugaredFilePath)
print("finished!")
print("Presence Conditions:")
for alarm in processedAlarms:
    print(f"File with path: \"{alarm.input_file}\" and loc {alarm.line_in_input_file}")
    print(f"\t Presence condition: {alarm.presence_condition}")
    print(f"\t Presence condition satisfiable: {alarm.feasible}")
