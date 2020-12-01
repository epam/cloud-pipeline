import platform


def is_windows():
    """
    Checks if the execution environment is Windows.
    """
    return platform.system() == 'Windows'


def is_wsl():
    """
    Checks if the execution environment is Windows Subsystem for Linux.
    """
    if is_windows():
        return False
    platform_uname = platform.uname()
    if platform_uname and len(platform_uname) > 3:
        platform_version = platform_uname[3]
        if platform_version:
            return 'microsoft' in platform_version.lower()
    return False
