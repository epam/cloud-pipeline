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

# Covers the installation of the environment modules (Tcl) and Lmod by workflows/pipe-common/shell/modules_setup on
# the RPM based images. Runs the script with bash, with the absolute paths it uses replaced by temporary ones. rpm,
# yum, wget, tar, make, the sources' configure script and the pipe_log_* commands are replaced by stubs, which record
# their calls. The tar stub extracts a sources folder with a configure script next to the downloaded modules.tar.gz.
# Needs bash 4.4+ (bash refuses cd with two arguments from 4.4 on, as on Rocky Linux 8).

import os
import stat
import subprocess

import pytest

MODULES_SETUP = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell', 'modules_setup')

RECORDING_STUB = '''#!/bin/bash
printf '%s\\n' "$(basename "$0") $*" >> "$CALLS"
'''

# yum repolist all: the repositories of the image, as a Rocky Linux 8.7 image lists them
YUM_STUB = RECORDING_STUB + '''if [ "$1" == "repolist" ]; then
    echo "repo id                  repo name                                      status"
    echo "baseos                   Rocky Linux 8 - BaseOS                         enabled"
    [ "$STUB_POWERTOOLS" == "true" ] && echo "powertools               Rocky Linux 8 - PowerTools                     disabled"
    echo "powertools-source        Rocky Linux 8 - PowerTools - Source            disabled"
fi
exit 0
'''

# wget -q <url> -O <file>
WGET_STUB = RECORDING_STUB + 'touch "$4"\n'

# tar -zxf modules.tar.gz: the sources folder, with a configure script which records the folder it is run in
TAR_STUB = RECORDING_STUB + '''mkdir -p "$STUB_SOURCES"
printf '#!/bin/bash\\nprintf "%%s\\\\n" "configure $(basename "$PWD")" >> "$CALLS"\\n' > "$STUB_SOURCES/configure"
chmod +x "$STUB_SOURCES/configure"
'''


def _stub(directory, name, body):
    stub = directory.join(name)
    stub.write(body)
    os.chmod(str(stub), stat.S_IRWXU)


@pytest.fixture
def modules_setup(tmpdir):
    stub_bin = tmpdir.mkdir('stub-bin')
    tmpdir.mkdir('profile.d')
    for name in ('rpm', 'make', 'pipe_log_info', 'pipe_log_success', 'pipe_log_fail'):
        _stub(stub_bin, name, RECORDING_STUB)
    _stub(stub_bin, 'yum', YUM_STUB)
    _stub(stub_bin, 'wget', WGET_STUB)
    _stub(stub_bin, 'tar', TAR_STUB)
    calls = tmpdir.join('calls')
    with open(MODULES_SETUP) as f:
        script = f.read() \
            .replace('/usr/bin/rpm', str(stub_bin.join('rpm'))) \
            .replace('/tmp/modules-tmp', str(tmpdir.join('modules-tmp'))) \
            .replace('/var/log/', str(tmpdir) + '/') \
            .replace('/etc/profile.d/', str(tmpdir.join('profile.d')) + '/') \
            .replace('/etc/cp_env.sh', str(tmpdir.join('cp_env.sh')))

    def run(modules_type, cp_os='rocky', powertools=True):
        env = {
            'PATH': str(stub_bin) + os.pathsep + os.environ['PATH'],
            'CALLS': str(calls),
            'CP_OS': cp_os,
            'CP_CAP_MODULES_TYPE': modules_type,
            'CP_CAP_MODULES_INSTALL_DIR': str(tmpdir.join('install')),
            'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/',
            'STUB_POWERTOOLS': 'true' if powertools else 'false',
            'STUB_SOURCES': 'modules-4.2.4' if modules_type == 'tcl' else 'Lmod-8.3'
        }
        exit_code = subprocess.call(['bash', '-c', script], env=env)
        return exit_code, calls.read().splitlines() if calls.exists() else []

    return run


def _yum_install(calls):
    return next(call for call in calls if call.startswith('yum ') and ' install ' in call).split()


def test_tcl_modules_are_built_in_the_sources_folder(modules_setup):
    # "cd modules*" also matched the downloaded modules.tar.gz, and bash 4.4+ refuses cd with two arguments
    exit_code, calls = modules_setup('tcl')

    assert exit_code == 0
    assert 'configure modules-4.2.4' in calls
    assert not [call for call in calls if call.startswith('pipe_log_fail ')]


def test_tcl_modules_get_the_build_tools(modules_setup):
    # Neither the Rocky Linux nor the CentOS container images have make and gcc
    exit_code, calls = modules_setup('tcl')

    packages = _yum_install(calls)
    assert 'make' in packages
    assert 'gcc' in packages


def test_lmod_gets_make(modules_setup):
    exit_code, calls = modules_setup('lmod')

    assert exit_code == 0
    assert 'configure Lmod-8.3' in calls
    assert 'make' in _yum_install(calls)


def test_lmod_on_rocky_enables_powertools(modules_setup):
    # Rocky Linux 8 has lua-devel, lua-filesystem and lua-posix in PowerTools, which its images have disabled
    exit_code, calls = modules_setup('lmod')

    assert '--enablerepo=powertools' in _yum_install(calls)


def test_lmod_on_rocky_without_powertools_does_not_enable_it(modules_setup):
    exit_code, calls = modules_setup('lmod', powertools=False)

    assert exit_code == 0
    assert '--enablerepo=powertools' not in _yum_install(calls)


def test_lmod_on_rocky_does_not_install_the_centos_only_packages(modules_setup):
    # Rocky Linux 8 has no lua-bitop, lua-term and tcl4 packages, and yum fails on a package it cannot find
    exit_code, calls = modules_setup('lmod')

    packages = _yum_install(calls)
    assert exit_code == 0
    for package in ('lua-bitop', 'lua-term', 'tcl4'):
        assert package not in packages


def test_lmod_on_centos_keeps_its_repositories_and_packages(modules_setup):
    exit_code, calls = modules_setup('lmod', cp_os='centos')

    packages = _yum_install(calls)
    assert exit_code == 0
    assert '--enablerepo=powertools' not in packages
    for package in ('lua-bitop', 'lua-term', 'tcl4'):
        assert package in packages
