from python.sugarlyzer.SugarCRunner import process_alarms
from python.sugarlyzer.models.InferAlarm import InferAlarm
from pathlib import Path

desugaredFilePath = Path("/home/tim/Desktop/MA_Workspace/sample-spls/SimpleCode/SimpleCodeVariabilityBugComplexDesugared.c")
lineNumber = 1298
alarm = InferAlarm(input_file = desugaredFilePath,
                   line_in_input_file = lineNumber,
                   bug_type = "({cpg.method(\"(?i)(__strcpy.*|strncpy)\").callIn}).l",
                   message = "__strcpy_976  ( __dest_1122 , __src_1121 )",
                   alarm_type = "Pattern Occurance",
                   warning_path = [lineNumber])

print("Starting to process alarms...")
processedAlarms = process_alarms([alarm], desugaredFilePath)
print("Alarms processed!")
print("Presence Conditions:")
for alarm in processedAlarms:
    print(f"Alarm in line #{alarm.line_in_input_file}")
    print(f"\t Presence condition: {alarm.presence_condition}")
    print(f"\t Presence condition satisfiable: {alarm.feasible}")