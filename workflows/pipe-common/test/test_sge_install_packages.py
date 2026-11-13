# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# Covers the Grid Engine packages that the SGE setup scripts of workflows/pipe-common/shell install on the RPM based
# images: the master and the worker ask only for the packages that the Cloud Pipeline repository for Rocky Linux 9.8
# provides, as yum fails the whole install if one package is missing. That leaves out gridengine-debuginfo and
# gridengine-guiinst. Reads the "yum install -y -q gridengine" command of each script.

import os

import pytest

SHELL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell')

# The gridengine packages of tools/repos/rocky/9.8 in the global distribution
GRIDENGINE_PACKAGES = {
    'gridengine',
    'gridengine-devel',
    'gridengine-drmaa4ruby',
    'gridengine-execd',
    'gridengine-qmaster',
    'gridengine-qmon',
}

INSTALL_COMMAND = 'yum install -y -q gridengine'


def _gridengine_install_packages(script):
    with open(os.path.join(SHELL_DIR, script)) as f:
        lines = f.read().splitlines()
    commands = []
    for index, line in enumerate(lines):
        if INSTALL_COMMAND not in line:
            continue
        words = []
        text = line[line.index(INSTALL_COMMAND) + len('yum install -y -q'):]
        while True:
            continues = text.rstrip().endswith('\\')
            words.extend(text.rstrip().rstrip('\\').split())
            if not continues:
                break
            index += 1
            text = lines[index]
        commands.append(words)
    return commands


@pytest.mark.parametrize('script', ['sge_setup_master', 'sge_setup_worker'])
def test_sge_setup_installs_only_the_gridengine_packages_of_the_repositories(script):
    commands = _gridengine_install_packages(script)

    assert len(commands) == 1
    assert set(commands[0]) == GRIDENGINE_PACKAGES
