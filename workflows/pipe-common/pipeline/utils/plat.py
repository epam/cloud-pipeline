# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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


def is_mac():
    """
    Checks if the execution environment is Mac.
    """
    return platform.system() == 'Darwin'
