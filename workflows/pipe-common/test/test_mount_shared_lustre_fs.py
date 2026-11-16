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

# Covers how workflows/pipe-common/shell/mount_shared_lustre_fs gets the scratch Lustre file system of a run from the
# API: the master (POST) deletes a FAILED file system and creates it again, CP_CAP_LUSTRE_CREATE_RETRIES attempts in all
# (the first one included), CP_CAP_LUSTRE_CREATE_RETRY_SLEEP seconds apart, and waits for a deletion up to
# CP_CAP_LUSTRE_DELETE_WAIT_ATTEMPTS checks. A worker (GET) fails at once if the file system fails or is deleted.
# Runs the script with bash. curl, jq, mount, sleep and the pipe_log_* commands are replaced by stubs first on PATH.
# The curl stub answers each method with the next line of its list of answers (the last one once the list ends), and
# records the calls. The jq stub knows the ".a.b" paths only, which is all the script asks for.

import json
import os
import stat
import subprocess
import sys

import pytest

LUSTRE_SCRIPT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                             'shell', 'mount_shared_lustre_fs')

API = 'https://api.test/restapi'
RUN_ID = '42'

CURL_STUB = '''#!/bin/bash
_method=GET
_url=
while [ $# -gt 0 ]; do
    case "$1" in
        -X) _method="$2"; shift ;;
        --header) shift ;;
        -*) ;;
        *) _url="$1" ;;
    esac
    shift
done
printf 'curl %s %s\\n' "$_method" "$_url" >> "$CALLS"
_counter="$STUB_DIR/$_method.count"
_index=$(( $(cat "$_counter" 2>/dev/null || echo 0) + 1 ))
echo "$_index" > "$_counter"
_answers="$STUB_DIR/$_method.answers"
_lines=$(wc -l < "$_answers")
[ "$_index" -gt "$_lines" ] && _index="$_lines"
sed -n "${_index}p" "$_answers"
'''

JQ_STUB = '''#!%s
import json
import sys

path = [key for key in sys.argv[-1].split('.') if key]
text = sys.stdin.read().strip()
if not text:
    sys.exit(0)
try:
    value = json.loads(text)
except ValueError:
    sys.exit(4)
for key in path:
    value = value.get(key) if isinstance(value, dict) else None
if value is None:
    print('null')
elif isinstance(value, bool):
    print(str(value).lower())
else:
    print(value)
''' % sys.executable

RECORD_STUB = '''#!/bin/bash
printf '%s %s\\n' "{name}" "$*" >> "$CALLS"
'''

LOG_STUB = '''#!/bin/bash
printf '%s %s\\n' "{level}" "$1" >> "$CALLS"
'''


def _fs(status, path='10.0.0.1@tcp:/fsx', options='noatime'):
    return json.dumps({'status': 'OK', 'payload': {'status': status, 'mountPath': path, 'mountOptions': options}})


GONE = json.dumps({'status': 'OK', 'payload': {}})
DELETED = json.dumps({'status': 'OK'})
NOT_DELETED = json.dumps({'status': 'ERROR', 'message': 'Lustre is busy'})


def _write_stub(stub_dir, name, text):
    path = stub_dir.join(name)
    path.write(text)
    os.chmod(str(path), os.stat(str(path)).st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def _run(tmpdir, request_type, answers, **env_vars):
    stub_dir = tmpdir.mkdir('stub-bin')
    calls = tmpdir.join('calls')
    calls.write('')
    _write_stub(stub_dir, 'curl', CURL_STUB)
    _write_stub(stub_dir, 'jq', JQ_STUB)
    for name in ('mount', 'sleep'):
        _write_stub(stub_dir, name, RECORD_STUB.replace('{name}', name))
    for level in ('info', 'warn', 'fail'):
        _write_stub(stub_dir, 'pipe_log_' + level, LOG_STUB.replace('{level}', level))
    for method, lines in answers.items():
        stub_dir.join(method + '.answers').write('\n'.join(lines) + '\n')
    env = dict(os.environ)
    for name in list(env):
        if name.startswith('CP_CAP_'):
            env.pop(name)
    env.update({
        'PATH': '%s:%s' % (stub_dir, env['PATH']),
        'CALLS': str(calls),
        'STUB_DIR': str(stub_dir),
        'API': API,
        'API_TOKEN': 'token',
    })
    env.update(env_vars)
    mount_dir = tmpdir.join('mnt')
    result = subprocess.call(['bash', LUSTRE_SCRIPT, str(mount_dir), RUN_ID, request_type, 'Task'], env=env)
    return result, calls.read().splitlines()


def _requests(calls):
    return [call for call in calls if call.startswith('curl ')]


def _mounted(calls):
    return [call for call in calls if call.startswith('mount ')]


URL = '%s/lustre/%s' % (API, RUN_ID)


def test_master_deletes_a_failed_lustre_and_creates_it_again(tmpdir):
    result, calls = _run(tmpdir, 'POST', {'POST': [_fs('FAILED'), _fs('AVAILABLE')],
                                         'DELETE': [DELETED],
                                         'GET': [GONE]})

    assert result == 0
    assert _requests(calls) == ['curl POST ' + URL, 'curl DELETE ' + URL, 'curl GET ' + URL, 'curl POST ' + URL]
    assert 'warn Lustre filesystem has FAILED status (attempt 1/3)' in calls
    assert 'sleep 60' in calls
    assert _mounted(calls) == ['mount -t lustre 10.0.0.1@tcp:/fsx %s -o noatime' % tmpdir.join('mnt')]


def test_master_retries_a_lustre_that_fails_while_it_is_created(tmpdir):
    result, calls = _run(tmpdir, 'POST', {'POST': [_fs('CREATING'), _fs('AVAILABLE')],
                                         'DELETE': [DELETED],
                                         'GET': [_fs('CREATING'), _fs('FAILED'), GONE]})

    assert result == 0
    assert _requests(calls) == ['curl POST ' + URL, 'curl GET ' + URL, 'curl GET ' + URL,
                                'curl DELETE ' + URL, 'curl GET ' + URL, 'curl POST ' + URL]
    assert len(_mounted(calls)) == 1


def test_master_waits_for_a_lustre_that_is_still_deleting(tmpdir):
    result, calls = _run(tmpdir, 'POST', {'POST': [_fs('DELETING'), _fs('AVAILABLE')],
                                         'GET': [_fs('DELETING'), GONE]})

    assert result == 0
    assert 'curl DELETE ' + URL not in calls
    assert _requests(calls) == ['curl POST ' + URL, 'curl GET ' + URL, 'curl GET ' + URL, 'curl POST ' + URL]
    assert len(_mounted(calls)) == 1


def test_master_retries_an_empty_answer(tmpdir):
    result, calls = _run(tmpdir, 'POST', {'POST': ['', _fs('AVAILABLE')]})

    assert result == 0
    assert 'warn Failed to get Lustre filesystem info (attempt 1/3), retrying in 60s...' in calls
    assert len(_mounted(calls)) == 1


def test_master_fails_once_the_attempts_are_exhausted(tmpdir):
    result, calls = _run(tmpdir, 'POST', {'POST': [_fs('FAILED')], 'DELETE': [DELETED], 'GET': [GONE]},
                         CP_CAP_LUSTRE_CREATE_RETRIES='2', CP_CAP_LUSTRE_CREATE_RETRY_SLEEP='7')

    assert result == 1
    assert [call for call in _requests(calls) if call.startswith('curl POST')] == ['curl POST ' + URL] * 2
    assert 'sleep 7' in calls
    assert 'fail All attempts to create Lustre filesystem have been exhausted' in calls
    assert _mounted(calls) == []


def test_master_fails_if_the_failed_lustre_cannot_be_deleted(tmpdir):
    result, calls = _run(tmpdir, 'POST', {'POST': [_fs('FAILED')], 'DELETE': [NOT_DELETED]})

    assert result == 1
    assert 'fail Failed to delete Lustre filesystem (Lustre is busy), cannot retry creation' in calls
    assert _mounted(calls) == []


def test_master_fails_if_the_deletion_does_not_end(tmpdir):
    result, calls = _run(tmpdir, 'POST', {'POST': [_fs('FAILED')], 'DELETE': [DELETED], 'GET': [_fs('DELETING')]},
                         CP_CAP_LUSTRE_DELETE_WAIT_ATTEMPTS='2')

    assert result == 1
    assert [call for call in _requests(calls) if call.startswith('curl GET')] == ['curl GET ' + URL] * 2
    assert 'fail Lustre filesystem was not fully deleted, cannot retry creation' in calls
    assert _mounted(calls) == []


@pytest.mark.parametrize('status, message', [
    ('FAILED', 'fail Failed to get Lustre filesystem info: filesystem is in FAILED state'),
    ('DELETING', 'fail Lustre filesystem is in DELETING state'),
])
def test_worker_fails_at_once(tmpdir, status, message):
    result, calls = _run(tmpdir, 'GET', {'GET': [_fs(status)]}, CP_CAP_LUSTRE_WAIT_ATTEMPTS='3')

    assert result == 1
    assert _requests(calls) == ['curl GET ' + URL]
    assert message in calls
    assert _mounted(calls) == []


def test_worker_mounts_an_available_lustre(tmpdir):
    result, calls = _run(tmpdir, 'GET', {'GET': [_fs('AVAILABLE', options='null')]})

    assert result == 0
    assert _mounted(calls) == ['mount -t lustre 10.0.0.1@tcp:/fsx %s' % tmpdir.join('mnt')]


def test_master_fails_if_the_lustre_does_not_become_available(tmpdir):
    result, calls = _run(tmpdir, 'POST', {'POST': [_fs('CREATING')], 'GET': [_fs('CREATING')]},
                         CP_CAP_LUSTRE_WAIT_ATTEMPTS='2')

    assert result == 1
    assert _requests(calls) == ['curl POST ' + URL, 'curl GET ' + URL, 'curl GET ' + URL]
    assert 'fail Lustre filesystem did not become AVAILABLE within the expected time (status: CREATING)' in calls
    assert _mounted(calls) == []


def test_master_fails_once_the_attempts_are_exhausted_on_empty_answers(tmpdir):
    result, calls = _run(tmpdir, 'POST', {'POST': ['']}, CP_CAP_LUSTRE_CREATE_RETRIES='2')

    assert result == 1
    assert _requests(calls) == ['curl POST ' + URL] * 2
    assert 'fail Failed to get Lustre filesystem info: empty or invalid API response' in calls
    assert _mounted(calls) == []


def test_master_waits_for_a_lustre_that_starts_deleting_while_it_is_created(tmpdir):
    result, calls = _run(tmpdir, 'POST', {'POST': [_fs('CREATING'), _fs('AVAILABLE')],
                                         'GET': [_fs('DELETING'), GONE]},
                         CP_CAP_LUSTRE_WAIT_ATTEMPTS='3')

    assert result == 0
    assert 'curl DELETE ' + URL not in calls
    assert _requests(calls) == ['curl POST ' + URL, 'curl GET ' + URL, 'curl GET ' + URL, 'curl POST ' + URL]
    assert 'warn Lustre filesystem transitioned to DELETING state during polling (attempt 1/3)' in calls
    assert len(_mounted(calls)) == 1


def test_master_fails_on_an_unexpected_status_after_the_deletion(tmpdir):
    result, calls = _run(tmpdir, 'POST', {'POST': [_fs('FAILED')], 'DELETE': [DELETED], 'GET': [_fs('CREATING')]})

    assert result == 1
    assert 'warn Unexpected Lustre filesystem status after deletion: CREATING' in calls
    assert 'fail Lustre filesystem was not fully deleted, cannot retry creation' in calls
    assert [call for call in _requests(calls) if call.startswith('curl POST')] == ['curl POST ' + URL]
    assert _mounted(calls) == []


@pytest.mark.parametrize('status, message', [
    ('FAILED', 'fail An error occurred while creating Lustre filesystem, most likely Cloud provider is out of capacity '
               'at the moment'),
    ('DELETING', 'fail Lustre filesystem transitioned to DELETING state unexpectedly'),
])
def test_worker_fails_at_once_if_the_lustre_fails_while_it_is_created(tmpdir, status, message):
    result, calls = _run(tmpdir, 'GET', {'GET': [_fs('CREATING'), _fs(status)]}, CP_CAP_LUSTRE_WAIT_ATTEMPTS='3')

    assert result == 1
    assert _requests(calls) == ['curl GET ' + URL] * 2
    assert message in calls
    assert _mounted(calls) == []
