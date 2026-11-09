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

# Covers the start of the DCV server by workflows/pipe-common/shell/nice_dcv_setup. Runs its dcv_log_text and run_dcv
# functions with bash. systemctl, service, nohup, curl, sleep, the pipe_log_* commands, the session creation and the
# name of PID 1 are replaced by stubs, which record their calls. One test runs the real pipe_log_* scripts on the text,
# with a stub pipeline package.

import os
import subprocess
import sys

import pytest

SHELL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell')
NICE_DCV_SETUP = os.path.join(SHELL_DIR, 'nice_dcv_setup')

BUS_ERROR = 'Failed to connect to bus: No such file or directory'
JOB_FAILED = 'Job for dcvserver.service failed because the control process exited with error code. ' \
             'See "systemctl status dcvserver.service" and "journalctl -xe" for details. C:\\dcv'

PIPELINE_STUB = '''import os


class bcolors:
    WARNING = ''
    ENDC = ''


class Logger:
    @staticmethod
    def log_task_event(task, text, status=None, api_url=None, log_dir=None, run_id=None):
        with open(os.environ['LOGGED'], 'a') as f:
            f.write('%s %s %s\\n' % (task, status, text))
'''

STUBS = r'''
record() { printf '%s\n' "$*" >> "$CALLS"; }
pipe_log_info() { record "info $1"; }
pipe_log_warn() { record "warn $1"; }
pipe_log_fail() { record "fail $1"; }
sleep() { :; }
service() { record "service $*"; }
nohup() { record "nohup $*"; }
nice_dcv_create_session() { record session; }
dcv_pid1() { echo bash; }
curl() {
    _CURL_CALLS=$((_CURL_CALLS + 1))
    record curl
    [ -n "$STUB_READY_AT" ] && [ "$_CURL_CALLS" -ge "$STUB_READY_AT" ]
}
systemctl() {
    case "$1" in
        start)
            if [ "$2" == "dcvserver" ] && [ "$STUB_START" != "ok" ]; then
                if [ "$STUB_SYSTEMD" == "running" ]; then
                    printf '%s\n' "$JOB_FAILED" >&2
                else
                    printf '%s\n' "$BUS_ERROR" >&2
                fi
                return 1
            fi ;;
        show-environment)
            [ "$STUB_SYSTEMD" == "running" ] ;;
        is-failed)
            record "systemctl is-failed"
            [ "$STUB_FAILED" == "true" ] ;;
        status)
            echo "dcvserver.service - NICE DCV server daemon"
            echo "Active: failed (Result: exit-code)" ;;
    esac
}
'''


def _function_source(name):
    with open(NICE_DCV_SETUP) as f:
        text = f.read()
    start = text.index('function %s {' % name)
    end = text.index('\n}\n', start) + 3
    return text[start:end]


@pytest.fixture
def run_dcv(tmpdir):
    calls = tmpdir.join('calls')
    script = STUBS + _function_source('dcv_log_text') + _function_source('run_dcv') + 'run_dcv\n'

    def run(rpm_based=True, systemd='running', start='ok', failed='false', ready_at=''):
        env = {
            'PATH': os.environ['PATH'],
            'CALLS': str(calls),
            'IS_RPM_BASED': '0' if rpm_based else '1',
            'DCV_INSTALL_TASK': 'NiceDCVInitialization',
            'CP_DCV_WEB_PORT': '8100',
            'CP_DCV_DESKTOP_PORT': '',
            '_RETRIES_COUNT': '3',
            '_RETRIES_TIMEOUT': '0',
            'BUS_ERROR': BUS_ERROR,
            'JOB_FAILED': JOB_FAILED,
            'STUB_SYSTEMD': systemd,
            'STUB_START': start,
            'STUB_FAILED': failed,
            'STUB_READY_AT': ready_at
        }
        exit_code = subprocess.call(['bash', '-c', script], env=env)
        return exit_code, calls.read().splitlines() if calls.exists() else []

    return run


def _messages(calls, level):
    return [call[len(level) + 1:] for call in calls if call.startswith(level + ' ')]


def test_dcv_setup_fails_at_once_if_systemd_is_not_running(run_dcv):
    exit_code, calls = run_dcv(systemd='stopped', start='fail')

    assert exit_code == 1
    assert _messages(calls, 'fail') == ["[ERROR] DCV server cannot be started, systemd is not running in the "
                                        "container (PID 1 is 'bash'): " + BUS_ERROR]
    assert 'curl' not in calls


def test_dcv_setup_waits_if_start_fails_while_systemd_runs(run_dcv):
    exit_code, calls = run_dcv(start='fail', ready_at='2')

    assert exit_code == 0
    assert _messages(calls, 'warn')[0].startswith('[WARNING] DCV server start has failed, '
                                                  'waiting for systemd to start it again: Job for dcvserver.service')
    assert calls.count('curl') == 2
    assert 'info [INFO] DCV server is running. Proceeding.' in calls
    assert not _messages(calls, 'fail')


def test_dcv_setup_fails_if_dcv_server_fails_after_failed_start(run_dcv):
    exit_code, calls = run_dcv(start='fail', failed='true')

    assert exit_code == 1
    assert _messages(calls, 'warn') == ["[WARNING] DCV server start has failed, waiting for systemd to start it "
                                        "again: Job for dcvserver.service failed because the control process exited "
                                        "with error code. See 'systemctl status dcvserver.service' and "
                                        "'journalctl -xe' for details. C:/dcv"]
    assert _messages(calls, 'fail') == ['[ERROR] DCV server has failed: dcvserver.service - NICE DCV server daemon '
                                        'Active: failed (Result: exit-code)']


def test_reason_reaches_log_of_pipe_log_warn_and_pipe_log_fail(tmpdir):
    # The real pipe_log scripts, with a stub pipeline package, which writes down what it would log
    package = tmpdir.mkdir('pipeline')
    package.join('__init__.py').write(PIPELINE_STUB)
    package.join('api.py').write('class TaskStatus:\n    SUCCESS, FAILURE, RUNNING, STOPPED, PAUSED = range(5)\n')
    bin_dir = tmpdir.mkdir('bin')
    for command in ('pipe_log', 'pipe_log_warn', 'pipe_log_fail'):
        wrapper = bin_dir.join(command)
        wrapper.write('#!/bin/bash\nexec bash "%s" "$@"\n' % os.path.join(SHELL_DIR, command))
        wrapper.chmod(0o755)
    logged = tmpdir.join('logged')
    script = _function_source('dcv_log_text') + \
        'pipe_log_warn "[WARNING] start failed: $(dcv_log_text "$JOB_FAILED")" NiceDCVInitialization\n' \
        'pipe_log_fail "[ERROR] cannot be started: $(dcv_log_text "$JOB_FAILED")" NiceDCVInitialization\n'
    env = {
        'PATH': str(bin_dir) + os.pathsep + os.environ['PATH'],
        'PYTHONPATH': str(tmpdir),
        'CP_PYTHON_PATH': sys.executable,
        'API': 'https://api/',
        'RUN_ID': '1',
        'LOG_DIR': str(tmpdir),
        'LOGGED': str(logged),
        'JOB_FAILED': JOB_FAILED
    }

    # From the temporary folder: python -c finds the pipeline package of the current folder first
    with open(os.devnull, 'w') as devnull:
        subprocess.check_call(['bash', '-c', script], env=env, stdout=devnull, cwd=str(tmpdir))

    reason = "Job for dcvserver.service failed because the control process exited with error code. " \
             "See 'systemctl status dcvserver.service' and 'journalctl -xe' for details. C:/dcv"
    assert logged.read().splitlines() == ['NiceDCVInitialization 2 [WARNING] start failed: ' + reason,
                                          'NiceDCVInitialization 1 [ERROR] cannot be started: ' + reason]


def test_dcv_setup_fails_at_once_if_dcv_server_fails_after_start(run_dcv):
    exit_code, calls = run_dcv(failed='true')

    assert exit_code == 1
    assert calls.count('curl') == 1
    assert _messages(calls, 'fail') == ['[ERROR] DCV server has failed: dcvserver.service - NICE DCV server daemon '
                                        'Active: failed (Result: exit-code)']
    assert not [call for call in calls if call.startswith('warn [WARNING] DCV server is still not running')]


def test_dcv_setup_waits_for_dcv_server(run_dcv):
    exit_code, calls = run_dcv(ready_at='2')

    assert exit_code == 0
    assert calls.count('curl') == 2
    assert 'warn [WARNING] DCV server is still not running. Try #1.' in calls
    assert 'info [INFO] DCV server is running. Proceeding.' in calls
    assert 'session' in calls


def test_dcv_setup_fails_if_dcv_server_does_not_answer(run_dcv):
    exit_code, calls = run_dcv()

    assert exit_code == 1
    assert calls.count('curl') == 3
    assert _messages(calls, 'fail') == ['[ERROR] DCV server cannot be run.']


def test_dcv_setup_does_not_ask_systemd_in_not_rpm_based_image(run_dcv):
    exit_code, calls = run_dcv(rpm_based=False, failed='true', ready_at='2')

    assert exit_code == 0
    assert 'service dbus start' in calls
    assert 'systemctl is-failed' not in calls
    assert 'info [INFO] DCV server is running. Proceeding.' in calls


def test_pid1_name_is_read_from_proc(tmpdir):
    script = _function_source('dcv_pid1') + 'dcv_pid1\n'

    name = subprocess.check_output(['bash', '-c', script], universal_newlines=True)

    try:
        with open('/proc/1/comm') as f:
            assert name == f.read()
    except IOError:
        assert name == ''
