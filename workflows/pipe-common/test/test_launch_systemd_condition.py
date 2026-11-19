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

# Covers the condition of the "Setup Systemd" step of scripts/pipeline-launch/launch.sh: the step runs only when
# CP_CAP_SYSTEMD_CONTAINER or CP_CAP_KUBE is requested, systemctl is installed and the OS is CentOS, Rocky Linux or
# RHEL. Runs the condition and the check_cp_cap function of launch.sh with bash. check_installed is replaced by a stub,
# which reports systemctl as installed or not. Needs bash 4 or later.

import os
import subprocess

import pytest

LAUNCH_SH = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
                         'scripts', 'pipeline-launch', 'launch.sh')

CONDITION_START = 'if ( check_cp_cap "CP_CAP_SYSTEMD_CONTAINER" || check_cp_cap "CP_CAP_KUBE" )'

STUBS = '''
check_installed() { [ "$1" == "systemctl" ] && [ "$STUB_SYSTEMCTL" == "true" ]; }
'''


def _launch_sh():
    with open(LAUNCH_SH) as f:
        return f.read()


def _function_source(text, name):
    start = text.index('function %s {' % name)
    end = text.index('\n}\n', start) + 3
    return text[start:end]


def _condition_source(text):
    start = text.index(CONDITION_START)
    end = text.index('; then', start) + len('; then')
    return text[start:end]


def _systemd_step_runs(os_name, systemd_cap=None, kube_cap=None, systemctl=True):
    text = _launch_sh()
    script = STUBS + _function_source(text, 'check_cp_cap') + '\n' + _condition_source(text) + \
        '\n    echo setup\nelse\n    echo skip\nfi\n'
    env = dict(os.environ)
    for name in ('CP_CAP_SYSTEMD_CONTAINER', 'CP_CAP_KUBE'):
        env.pop(name, None)
    env['CP_OS'] = os_name
    env['STUB_SYSTEMCTL'] = 'true' if systemctl else 'false'
    if systemd_cap is not None:
        env['CP_CAP_SYSTEMD_CONTAINER'] = systemd_cap
    if kube_cap is not None:
        env['CP_CAP_KUBE'] = kube_cap
    output = subprocess.check_output(['bash', '-c', script], env=env, universal_newlines=True)
    return output.strip() == 'setup'


@pytest.mark.parametrize('os_name', ['centos', 'rocky', 'rhel'])
def test_systemd_step_runs_if_systemd_is_requested(os_name):
    assert _systemd_step_runs(os_name, systemd_cap='true')


@pytest.mark.parametrize('os_name', ['centos', 'rocky', 'rhel'])
def test_systemd_step_runs_if_kube_is_requested(os_name):
    assert _systemd_step_runs(os_name, kube_cap='true')


@pytest.mark.parametrize('os_name', ['centos', 'rocky', 'rhel'])
def test_systemd_step_is_skipped_if_nothing_requests_it(os_name):
    assert not _systemd_step_runs(os_name)


@pytest.mark.parametrize('os_name', ['centos', 'rocky', 'rhel'])
def test_systemd_step_is_skipped_if_capabilities_are_false(os_name):
    assert not _systemd_step_runs(os_name, systemd_cap='false', kube_cap='false')


@pytest.mark.parametrize('os_name', ['centos', 'rocky', 'rhel'])
def test_systemd_step_is_skipped_without_systemctl(os_name):
    assert not _systemd_step_runs(os_name, systemd_cap='true', systemctl=False)


@pytest.mark.parametrize('os_name', ['ubuntu', 'debian', 'amzn'])
def test_systemd_step_is_skipped_on_other_os(os_name):
    assert not _systemd_step_runs(os_name, systemd_cap='true')
