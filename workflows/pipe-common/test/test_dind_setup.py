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

# Covers the docker version and the dependencies install of workflows/pipe-common/shell/dind_setup on the RPM based
# images: RHEL (also the UBI images), Rocky Linux and CentOS. Runs the script with bash up to the docker install, with
# rpm, yum and the pipe_log_* commands replaced by stubs, which record their calls. util_version_compare is the one
# of pipe-common.

import os
import stat
import subprocess

import pytest

SHELL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell')
DIND_SETUP = os.path.join(SHELL_DIR, 'dind_setup')

RECORDING_STUB = '''#!/bin/bash
printf '%s\\n' "$(basename "$0") $*" >> "$CALLS"
'''

DEPENDENCIES = ['e2fsprogs', 'iptables', 'iproute', 'xfsprogs', 'xz', 'pigz', 'kmod', 'findutils']


def _stub(directory, name, body):
    stub = directory.join(name)
    stub.write(body)
    os.chmod(str(stub), stat.S_IRWXU)


def _dependencies_part():
    # The script up to the docker install, which downloads it
    with open(DIND_SETUP) as f:
        text = f.read()
    end = text.index('# Install docker\n')
    end = text.rindex('\n#####', 0, end) + 1
    return text[:end] + 'echo "DOCKER VERSION: $CP_CAP_DIND_VERSION"\n'


@pytest.fixture
def dind_setup(tmpdir):
    stub_bin = tmpdir.mkdir('stub-bin')
    for name in ('rpm', 'pipe_log_info', 'pipe_log_fail'):
        _stub(stub_bin, name, RECORDING_STUB)
    _stub(stub_bin, 'yum', RECORDING_STUB + 'exit ${STUB_YUM_RC:-0}\n')
    _stub(stub_bin, 'util_version_compare', '#!/bin/bash\nexec bash "%s" "$@"\n'
          % os.path.join(SHELL_DIR, 'util_version_compare'))
    calls = tmpdir.join('calls')
    script = _dependencies_part().replace('/usr/bin/rpm', str(stub_bin.join('rpm')))

    def run(cp_os, yum_rc=0, **env):
        run_env = {
            'PATH': str(stub_bin) + os.pathsep + os.environ['PATH'],
            'CALLS': str(calls),
            'CP_OS': cp_os,
            'GLOBAL_DISTRIBUTION_URL': 'https://dist.example/',
            'STUB_YUM_RC': str(yum_rc)
        }
        run_env.update(env)
        proc = subprocess.Popen(['bash', '-c', script], env=run_env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        stdout, stderr = proc.communicate()
        recorded = calls.read().splitlines() if calls.check() else []
        return proc.returncode, stdout.decode('utf-8'), recorded

    return run


def _yum_calls(calls):
    return [call.split() for call in calls if call.startswith('yum ')]


@pytest.mark.parametrize('cp_os', ['rhel', 'rocky'])
def test_rhel_and_rocky_get_docker_20_and_the_same_dependencies(dind_setup, cp_os):
    # No CentOS 8 Stream repository on RHEL: mirror.centos.org has none any more
    exit_code, stdout, calls = dind_setup(cp_os)

    assert exit_code == 0
    assert 'DOCKER VERSION: 20.10.24' in stdout
    assert _yum_calls(calls) == [['yum', 'install', '-y', '-q'] + DEPENDENCIES]
    assert not [call for call in calls if call.startswith('pipe_log_fail ')]


def test_centos_gets_docker_18_and_btrfs_progs(dind_setup):
    exit_code, stdout, calls = dind_setup('centos')

    assert exit_code == 0
    assert 'DOCKER VERSION: 18.09.6' in stdout
    assert _yum_calls(calls) == [['yum', 'install', '-y', '-q'] + DEPENDENCIES + ['btrfs-progs']]


def test_the_requested_docker_version_is_kept_on_rhel(dind_setup):
    exit_code, stdout, calls = dind_setup('rhel', CP_CAP_DIND_VERSION='24.0.9')

    assert 'DOCKER VERSION: 24.0.9' in stdout


def test_rhel_names_the_missing_dependencies_if_they_cannot_be_installed(dind_setup):
    # The UBI repositories have no e2fsprogs and xfsprogs
    exit_code, stdout, calls = dind_setup('rhel', yum_rc=1)

    assert exit_code == 1
    assert 'pipe_log_fail Unable to install DIND dependencies, aborting. A RHEL image needs e2fsprogs and xfsprogs ' \
           'installed, or the RHEL repositories: the UBI repositories do not have them SetupDinD' in calls
    assert 'DOCKER VERSION' not in stdout


def test_rocky_reports_the_failed_dependencies_install(dind_setup):
    exit_code, stdout, calls = dind_setup('rocky', yum_rc=1)

    assert exit_code == 1
    assert 'pipe_log_fail Unable to install DIND dependencies, aborting SetupDinD' in calls
