import os
from pathlib import Path


def collect_kconfig_files(kconfig_file_names: list[str], root_directory: Path) -> list[Path]:
    kconfig_files: list[Path] = []
    for dir_path, dir_names, file_names in os.walk(root_directory):
        for file_name in file_names:
            if file_name in kconfig_file_names:
                kconfig_files.append(Path(dir_path) / Path(file_name))

    return kconfig_files