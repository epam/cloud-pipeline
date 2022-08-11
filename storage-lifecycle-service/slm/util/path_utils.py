import os


def determinate_prefix_from_glob(_glob_str):
    _prefix = None
    if "*" in _glob_str:
        _prefix = os.path.split(_glob_str.split("*", 1)[0])[0]
    else:
        _prefix = _glob_str

    return _prefix


def convert_glob_to_regexp(glob_str):
    resulted_regexp = "^\\/"
    for glob_part in glob_str.split("/"):
        if not glob_part:
            continue
        if "*" not in glob_part:
            resulted_regexp += glob_part + "\\/"
        elif glob_part == "**":
            resulted_regexp += "(?:[^\\/]+\\/)+"
        else:
            resulted_regexp += glob_part.replace("*", "[^\\/]*") + "\\/"
    if resulted_regexp.endswith("\\/"):
        resulted_regexp = resulted_regexp[:len(resulted_regexp) - 2]
    resulted_regexp += "$"
    return resulted_regexp


def generate_all_possible_dir_paths(paths):
    def generate_hierarchy(_path):
        _result = set()
        interim_result = ""
        for path_part in _path.split("/"):
            interim_result = interim_result + "/" + path_part if path_part else interim_result
            _result.add(interim_result)
        return _result

    result = set()
    for path in paths:
        result = result.union(generate_hierarchy(path))
    return result
