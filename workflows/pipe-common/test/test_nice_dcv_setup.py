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

# Covers the start of the DCV server by workflows/pipe-common/shell/nice_dcv_setup, for an RPM based image.
# Runs its run_dcv function with bash. systemctl, curl, sleep, the pipe_log_* commands and the session creation
# are replaced by stubs, which record their calls.

import os
import subprocess
import sys

import pytest

NICE_DCV_SETUP = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell', 'nice_dcv_setup')

BUS_ERROR = 'Failed to connect to bus: No such file or directory'

STUBS = '''
record() { printf '%s\\n' "$*" >> "$CALLS"; }
pipe_log_info() { record "info $1"; }
pipe_log_warn() { record "warn $1"; }
pipe_log_fail() { record "fail $1"; }
sleep() { :; }
nice_dcv_create_session() { record session; }
dcv_systemd_hint() { echo " HINT"; }
curl() {
    _CURL_CALLS=$((_CURL_CALLS + 1))
    record curl
    [ -n "$STUB_READY_AT" ] && [ "$_CURL_CALLS" -ge "$STUB_READY_AT" ]
}
systemctl() {
    case "$1" in
        start)
            if [ "$2" == "dcvserver" ] && [ "$STUB_START" != "ok" ]; then
                if [ "$STUB_START" == "job-failed" ]; then
                    echo "Job for dcvserver.service failed because the control process exited with error code." >&2
                    echo 'See "systemctl status dcvserver.service" and "journalctl -xe" for details.' >&2
                else
                    echo "BUS_ERROR" >&2
                fi
                return 1
            fi ;;
        is-failed)
            [ "$STUB_FAILED" == "true" ] ;;
        status)
            echo "dcvserver.service - NICE DCV server"
            echo "Active: failed (Result: exit-code)" ;;
    esac
}
'''.replace('BUS_ERROR', BUS_ERROR)


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

    def run(start='ok', failed='false', ready_at=''):
        env = {
            'PATH': os.environ['PATH'],
            'CALLS': str(calls),
            'IS_RPM_BASED': '0',
            'DCV_INSTALL_TASK': 'NiceDCVInitialization',
            'CP_DCV_WEB_PORT': '8100',
            'CP_DCV_DESKTOP_PORT': '',
            '_RETRIES_COUNT': '3',
            '_RETRIES_TIMEOUT': '0',
            'STUB_START': start,
            'STUB_FAILED': failed,
            'STUB_READY_AT': ready_at
        }
        exit_code = subprocess.call(['bash', '-c', script], env=env)
        return exit_code, calls.read().splitlines() if calls.exists() else []

    return run


def test_dcv_setup_fails_at_once_if_dcv_server_cannot_be_started(run_dcv):
    exit_code, calls = run_dcv(start='fail')

    assert exit_code == 1
    assert 'fail [ERROR] DCV server cannot be started: ' + BUS_ERROR + '. HINT' in calls
    assert 'curl' not in calls


def test_dcv_setup_escapes_reason_for_python_string_literal_of_pipe_log(run_dcv):
    exit_code, calls = run_dcv(start='job-failed')

    assert exit_code == 1
    message = [call for call in calls if call.startswith('fail ')][0][len('fail '):]
    assert message == '[ERROR] DCV server cannot be started: Job for dcvserver.service failed because the control ' \
                      'process exited with error code. See \\"systemctl status dcvserver.service\\" and ' \
                      '\\"journalctl -xe\\" for details.. HINT'
    # pipe_log puts the text into a python string literal
    printed = subprocess.check_output([sys.executable, '-c', 'print("%s")' % message], universal_newlines=True)
    assert 'See "systemctl status dcvserver.service"' in printed


def test_dcv_setup_fails_at_once_if_dcv_server_fails_after_start(run_dcv):
    exit_code, calls = run_dcv(failed='true')

    assert exit_code == 1
    assert calls.count('curl') == 1
    assert 'fail [ERROR] DCV server has failed: dcvserver.service - NICE DCV server ' \
           'Active: failed (Result: exit-code)' in calls
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
    assert 'fail [ERROR] DCV server cannot be run.' in calls


def test_systemd_hint_names_pid1(tmpdir):
    script = _function_source('dcv_systemd_hint') + 'dcv_systemd_hint\n'

    hint = subprocess.check_output(['bash', '-c', script], universal_newlines=True)

    try:
        with open('/proc/1/comm') as f:
            pid1 = f.read().strip()
    except IOError:
        pid1 = ''
    if pid1 == 'systemd':
        assert hint == ''
    else:
        assert hint == " Systemd is not running as PID 1 (PID 1 is '%s'), " \
                       "see the 'Setup Systemd' messages of the run log.\n" % pid1
