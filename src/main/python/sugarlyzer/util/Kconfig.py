import os
from pathlib import Path


def collect_kconfig_files(kconfig_file_names: list[str], root_directory: Path) -> list[Path]:
    """
    Recursively traverses the files starting from the specified root directory and returns all those matching one of the
    specified kconfig file names.

    :param kconfig_file_names: The file names of kconfig files.
    :param root_directory: The root directory, from which the traversal should begin.

    :return: The list of kconfig files represented as a set of Path objects.
    """
    kconfig_files: list[Path] = []
    for dir_path, dir_names, file_names in os.walk(root_directory):
        for file_name in file_names:
            if file_name in kconfig_file_names:
                kconfig_files.append(Path(dir_path) / Path(file_name))

    return kconfig_files

def kconfig_add_quotes_to_source_directive(source_directive: str) -> str:
    """
    Takes in a source directive from a Kconfig file that does not surround the contained path with quotation marks and
    returns a version where the path is correctly surrounded.

    Examples: "source generated/Config.probed" --> "source "generated/Config.probed""
    and "source generated/Config.in" --> "source "generated/Config.in"".

    :param source_directive: The malformed source directive.

    :return: The source directive with the path wrapped in quotation marks.
    """
    source_left_right: list[str] = source_directive.split("source")
    indentation: str = source_left_right[0]
    included_file: str = source_left_right[1].strip()

    return f"{indentation}source \"{included_file}\"\n"