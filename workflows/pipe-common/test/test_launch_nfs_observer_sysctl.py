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

# Covers the "Setup NFS events observer" step of scripts/pipeline-launch/launch.sh: before it raises the inotify limits
# with sysctl, it installs procps if the image has no sysctl, and only when the observer runs.
# Runs the step of launch.sh with bash. check_installed, get_install_command_by_current_distr, sysctl and nohup are
# replaced by stubs, which record their calls.

import os
import subprocess

import pytest

LAUNCH_SH = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
                         'scripts', 'pipeline-launch', 'launch.sh')

STEP_START = 'if [ "$CP_CAP_NFS_MNT_OBSERVER_DISABLED" == "true" ]; then'

STUBS = r'''
record() { printf '%s\n' "$*" >> "$CALLS"; }
check_installed() { [ "$1" == "sysctl" ] && [ "$STUB_SYSCTL" == "true" ]; }
get_install_command_by_current_distr() {
    record "install command $2"
    eval $1=\"record installed $2\"
}
sysctl() { record "sysctl $*"; }
nohup() { record "nohup"; }
'''


def _step_source():
    with open(LAUNCH_SH) as f:
        text = f.read()
    start = text.index(STEP_START)
    end = text.index('\nfi\n', start) + 4
    return text[start:end]


def _run(tmpdir, sysctl_installed, **env_vars):
    calls = tmpdir.join('calls')
    calls.write('')
    env = dict(os.environ)
    for name in ('CP_CAP_NFS_MNT_OBSERVER_DISABLED', 'CP_SENSITIVE_RUN', 'CP_CAP_NFS_MNT_OBSERVER_RUN_WATCHERS'):
        env.pop(name, None)
    env.update({
        'CALLS': str(calls),
        'STUB_SYSCTL': 'true' if sysctl_installed else 'false',
        'CP_PYTHON_PATH': 'python',
        'COMMON_REPO_DIR': str(tmpdir),
        'LOG_DIR': str(tmpdir),
    })
    env.update(env_vars)
    subprocess.check_call(['bash', '-c', STUBS + _step_source() + 'wait\n'], env=env)
    return calls.read().splitlines()


def test_observer_installs_procps_before_sysctl_if_sysctl_is_missing(tmpdir):
    calls = _run(tmpdir, sysctl_installed=False)

    assert calls[:3] == ['install command procps',
                         'installed procps',
                         'sysctl -w fs.inotify.max_user_watches=65535']
    assert 'sysctl -w fs.inotify.max_queued_events=131070' in calls
    assert 'nohup' in calls


def test_observer_does_not_install_procps_if_sysctl_is_there(tmpdir):
    calls = _run(tmpdir, sysctl_installed=True)

    assert not any(call.startswith('install') for call in calls)
    assert calls[0] == 'sysctl -w fs.inotify.max_user_watches=65535'


@pytest.mark.parametrize('env_vars', [
    {'CP_CAP_NFS_MNT_OBSERVER_DISABLED': 'true'},
    {'CP_SENSITIVE_RUN': 'true'},
])
def test_procps_is_not_installed_if_the_observer_does_not_run(tmpdir, env_vars):
    calls = _run(tmpdir, sysctl_installed=False, **env_vars)

    assert calls == []
