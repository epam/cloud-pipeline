import os


def determinate_prefix_from_glob(_glob_str):
    _prefix = None
    if "*" in _glob_str:
        _prefix = os.path.split(_glob_str.split("*", 1)[0])[0]
    else:
        _prefix = _glob_str

    return _prefix.replace("/", "", 1) if _prefix.startswith("/") else _prefix
