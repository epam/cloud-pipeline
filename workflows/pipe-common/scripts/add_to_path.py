import os
import platform


def _add_to_powershell_profile(path, profile_path):
    import pathlib
    profile_dir_path = os.path.dirname(profile_path)
    pathlib.Path(profile_dir_path).mkdir(parents=True, exist_ok=True)
    with open(profile_path, 'a') as f:
        f.write('$env:PATH = "$env:PATH;{path}"\n'.format(path=path))


def _add_to_batch_profile(path, profile_path):
    import winreg
    import pathlib
    with winreg.CreateKey(winreg.HKEY_LOCAL_MACHINE, 'Software\\Microsoft\\Command Processor') as key:
        winreg.SetValueEx(key, 'AutoRun', 0, winreg.REG_SZ, profile_path)
    profile_dir_path = os.path.dirname(profile_path)
    pathlib.Path(profile_dir_path).mkdir(parents=True, exist_ok=True)
    if os.path.exists(profile_path):
        with open(profile_path, 'a') as f:
            f.write('set PATH=%PATH%;{path}\n'.format(path=path))
    else:
        with open(profile_path, 'w') as f:
            f.write('@echo off\n'
                    'set PATH=%PATH%;{path}\n'.format(path=path))


def add_to_path(path,
                powershell_profile_path='c:\\windows\\system32\\windowspowershell\\v1.0\\profile.ps1',
                batch_profile_path='c:\\init\\profile.cmd'):
    current_platform = platform.system()
    if current_platform == 'Windows':
        if powershell_profile_path:
            _add_to_powershell_profile(path, profile_path=powershell_profile_path)
        if batch_profile_path:
            _add_to_batch_profile(path, profile_path=batch_profile_path)
    else:
        raise RuntimeError('Adding to path is not supported on {platform} platform.'
                           .format(platform=current_platform))
