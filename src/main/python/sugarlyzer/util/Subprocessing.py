import logging
import re
from collections import namedtuple
from subprocess import CompletedProcess
from typing import Tuple, Optional

logger = logging.getLogger(__name__)
ResourceStats = namedtuple("ResourceStats", ["usr_time", "sys_time", "max_memory"])


def parse_bash_time(stderr: str) -> Tuple[float, float, float]:
    usr_time_match = re.search(r"User time \(seconds\): ([\d.]*)", stderr)
    usr_time = float(usr_time_match.group(1)) if usr_time_match is not None else None

    sys_time_match = re.search(r"System time \(seconds\): ([\d.]*)", stderr)
    sys_time = float(sys_time_match.group(1)) if sys_time_match is not None else None

    max_memory_match = re.search(r"Maximum resident set size \(kbytes\): (\d*)", stderr)
    max_memory = int(max_memory_match.group(1)) if max_memory_match is not None else None

    return usr_time, sys_time, max_memory


def get_resource_usage_of_process(ps: CompletedProcess) -> Optional[ResourceStats]:
    try:
        stderr_output = ps.stderr.decode() if isinstance(ps.stderr, bytes) else ps.stderr
        time_cmd_stats = "\n".join(stderr_output.split("\n")[-30:])
        usr_time, sys_time, max_memory = parse_bash_time(time_cmd_stats)

        if usr_time is not None and sys_time is not None and max_memory is not None:
            return ResourceStats(usr_time=usr_time, sys_time=sys_time, max_memory=max_memory)
    except UnicodeDecodeError as e:
        logger.exception(f"Could not decode stderr of CompletedProcess: {e}")
    return None
