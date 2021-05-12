import os
import platform


def add_to_path(appending_path):
    current_platform = platform.system()
    if current_platform == 'Windows':
        import pathlib
        import subprocess
        profile_path = subprocess.check_output(['powershell', '$Profile']).decode('utf-8').strip()
        profile_dir_path = os.path.dirname(profile_path)
        pathlib.Path(profile_dir_path).mkdir(parents=True, exist_ok=True)
        with open(profile_path, 'a') as f:
            f.write('$env:PATH = "$env:PATH;{path}"\n'.format(path=appending_path))
    else:
        raise RuntimeError('Adding to path is not supported on {platform} platform.'
                           .format(platform=current_platform))
