# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import platform
import errno
import os


def _mkdir(path):
    try:
        os.makedirs(path)
    except OSError as e:
        if e.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            raise


def _add_to_powershell_profile(path, profile_path):
    profile_dir_path = os.path.dirname(profile_path)
    _mkdir(profile_dir_path)
    with open(profile_path, 'a') as f:
        f.write('$env:PATH = "$env:PATH;{path}"\n'.format(path=path))


def _add_to_batch_profile(path, profile_path):
    from .reg import set_local_machine_str_value
    set_local_machine_str_value('Software\\Microsoft\\Command Processor', 'AutoRun', profile_path)
    profile_dir_path = os.path.dirname(profile_path)
    _mkdir(profile_dir_path)
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
