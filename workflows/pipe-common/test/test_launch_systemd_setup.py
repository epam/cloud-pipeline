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

# Covers the "Setup Systemd" step of scripts/pipeline-launch/launch.sh. Runs its setup_systemd, install_procps and
# check_cp_cap functions with bash. The functions which change the container or read /proc - is_systemd_pid1,
# prepare_systemd_container, start_systemd_in_background - and the package installation are replaced by stubs,
# which record their calls. Needs bash 4.

import os
import subprocess

import pytest

LAUNCH_SH = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
                         'scripts', 'pipeline-launch', 'launch.sh')

STUBS = '''
record() { printf '%s\\n' "$*" >> "$CALLS"; }
check_installed() { [ "$1" != "systemctl" ] || [ "$STUB_SYSTEMCTL" == "true" ]; }
get_install_command_by_current_distr() { eval "$1='record install $2'"; }
is_systemd_pid1() { [ "$STUB_PID1" == "systemd" ]; }
prepare_systemd_container() { record prepare; }
start_systemd_in_background() { record start; [ "$STUB_START" == "ok" ]; }
'''


def _function_source(name):
    with open(LAUNCH_SH) as f:
        text = f.read()
    start = text.index('function %s {' % name)
    end = text.index('\n}\n', start) + 3
    return text[start:end]


@pytest.fixture
def setup_systemd(tmpdir):
    calls = tmpdir.join('calls')
    script = STUBS + _function_source('check_cp_cap') + _function_source('install_procps') \
        + _function_source('setup_systemd') + 'setup_systemd\n'

    def run(os_name='rocky', systemd='false', kube='false', systemctl='true', pid1='bash', start='fail'):
        env = {
            'PATH': os.environ['PATH'],
            'CALLS': str(calls),
            'CP_OS': os_name,
            'CP_VER': '8',
            'CP_CAP_SYSTEMD_CONTAINER': systemd,
            'CP_CAP_KUBE': kube,
            'STUB_SYSTEMCTL': systemctl,
            'STUB_PID1': pid1,
            'STUB_START': start
        }
        process = subprocess.Popen(['bash', '-c', script], env=env,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)
        stdout, stderr = process.communicate()
        assert process.returncode == 0
        assert stderr == ''
        return stdout.splitlines(), calls.read().splitlines() if calls.exists() else []

    return run


def test_systemd_is_not_set_up_if_not_requested(setup_systemd):
    output, calls = setup_systemd(os_name='rocky')

    assert output == ['Systemd is not requested, skipping installation']
    assert calls == ['install procps']


def test_procps_is_not_installed_if_systemd_is_not_requested_for_not_rpm_image(setup_systemd):
    output, calls = setup_systemd(os_name='ubuntu')

    assert output == ['Systemd is not requested, skipping installation']
    assert calls == []


def test_systemd_running_as_pid1_is_not_started_again(setup_systemd):
    output, calls = setup_systemd(systemd='true', pid1='systemd')

    assert output == ['Systemd is running as PID 1']
    assert calls == ['install procps', 'prepare']


def test_kubernetes_requests_systemd(setup_systemd):
    output, calls = setup_systemd(kube='true', pid1='systemd')

    assert output == ['Systemd is running as PID 1']
    assert calls == ['install procps', 'prepare']


def test_procps_is_not_installed_if_systemd_is_not_requested_for_centos_image(setup_systemd):
    output, calls = setup_systemd(os_name='centos')

    assert output == ['Systemd is not requested, skipping installation']
    assert calls == []


def test_systemd_is_started_as_background_process_if_pid1_is_not_systemd(setup_systemd):
    output, calls = setup_systemd(os_name='centos', systemd='true', start='ok')

    assert output == ['Systemd is started as a background process']
    assert calls == ['install procps', 'prepare', 'start']


def test_warns_if_systemd_cannot_be_started(setup_systemd):
    output, calls = setup_systemd(systemd='true', start='fail')

    assert calls == ['install procps', 'prepare', 'start']
    assert len(output) == 2
    assert output[0].startswith('[WARN] Systemd is requested (CP_CAP_SYSTEMD_CONTAINER or CP_CAP_KUBE), '
                                'but it is not running.')
    assert 'NICE DCV' in output[0]
    assert output[1].startswith('[WARN] Systemd runs as PID 1 when the run is started with init.sh.')
    assert "launch.pod.cmd.template.linux preference has an entry with init.sh for 'rocky'" in output[1]


def test_warns_if_systemd_is_requested_for_not_supported_image(setup_systemd):
    output, calls = setup_systemd(os_name='ubuntu', systemd='true')

    assert output == ['[WARN] Systemd is requested (CP_CAP_SYSTEMD_CONTAINER or CP_CAP_KUBE), but it is supported '
                      'only for CentOS, Rocky Linux and RHEL images with systemctl installed. '
                      'It will not be set up for ubuntu 8.']
    assert calls == []


def test_warns_if_systemd_is_requested_for_image_without_systemctl(setup_systemd):
    output, calls = setup_systemd(os_name='rhel', systemd='true', systemctl='false')

    assert len(output) == 1
    assert output[0].startswith('[WARN] Systemd is requested')
    assert calls == ['install procps']
