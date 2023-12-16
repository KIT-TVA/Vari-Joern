#!/usr/bin/env python3

# Runs kmaxall and converts its output to JSON. All arguments are passed to kmaxall.

import sys
import subprocess
import pickle
import json

kmax = subprocess.run(["kmaxall"] + sys.argv[1:], stdout=subprocess.PIPE)
conditions = pickle.loads(kmax.stdout)
print(json.dumps(conditions, indent=4))