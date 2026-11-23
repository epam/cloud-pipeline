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

# Covers empty_uid_indexed_logs of scripts/commit-run-scripts/common_commit_initialization.sh. Runs it with bash and
# a stub "docker", which runs the script given to "docker exec" locally, with /var/log/ replaced by a temporary
# folder, or fails if asked to. The pipe-common log commands are replaced by stubs, which record their calls.
# The helper only checks that a file is not empty, so the sparse files here are small.
# commit_run_starter.sh and pause_run.sh download and install the platform code, and pause_run.sh resets the node,
# so they are not run: their tests check the order of the calls in their source.

import os
import subprocess

import pytest

COMMIT_RUN_SCRIPTS = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
    'scripts', 'commit-run-scripts')
COMMON_COMMIT_INITIALIZATION = os.path.join(COMMIT_RUN_SCRIPTS, 'common_commit_initialization.sh')
EMPTY_CALL = 'empty_uid_indexed_logs ${CONTAINER_ID}'

STUBS = '''
record() { printf '%s\\n' "$*" >> "$CALLS"; }
pipe_log_info() { record "info $1"; }
pipe_log_warn() { record "warn $1"; }
docker() {
    record "docker $1 $2 $3 $4"
    [ "$STUB_DOCKER" != "fail" ] || return 1
    sh -c "$(printf '%s' "$5" | sed "s|/var/log/|$LOG_DIR/|g")"
}
'''

LOG_SIZE = 64 * 1024


@pytest.fixture
def empty_logs(tmpdir):
    log_dir = tmpdir.mkdir('log')
    calls = tmpdir.join('calls')

    def run(docker='ok'):
        script = STUBS + '. "%s"\nempty_uid_indexed_logs container\n' % COMMON_COMMIT_INITIALIZATION
        env = {
            'PATH': os.environ['PATH'],
            'CALLS': str(calls),
            'LOG_DIR': str(log_dir),
            'TASK_NAME': 'CommitPipelineRun',
            'STUB_DOCKER': docker
        }
        subprocess.check_call(['bash', '-c', script], env=env)
        return calls.read().splitlines()

    return log_dir, run


def _sparse(path, size=LOG_SIZE):
    with open(str(path), 'wb') as f:
        f.truncate(size)


def test_empties_sparse_logs_in_container(empty_logs):
    log_dir, run = empty_logs
    _sparse(log_dir.join('lastlog'))
    _sparse(log_dir.join('tallylog'), 1024)

    calls = run()

    assert calls[0] == 'docker exec container sh -c'
    assert os.path.getsize(str(log_dir.join('lastlog'))) == 0
    assert os.path.getsize(str(log_dir.join('tallylog'))) == 0
    assert 'info [INFO] Emptied in the container before the commit: %s/lastlog %s/tallylog' \
           % (log_dir, log_dir) in calls


def test_keeps_target_of_symlinked_log(empty_logs, tmpdir):
    log_dir, run = empty_logs
    target = tmpdir.join('target')
    target.write('kept')
    os.symlink(str(target), str(log_dir.join('faillog')))

    calls = run()

    assert target.read() == 'kept'
    assert [call for call in calls if call.startswith('info') or call.startswith('warn')] == []


def test_logs_nothing_if_logs_are_missing_or_empty(empty_logs):
    log_dir, run = empty_logs
    log_dir.join('lastlog').write('')

    calls = run()

    assert calls == ['docker exec container sh -c']


def test_warns_if_logs_cannot_be_emptied(empty_logs):
    log_dir, run = empty_logs
    _sparse(log_dir.join('lastlog'))

    calls = run(docker='fail')

    assert calls[-1].startswith('warn [WARN] Cannot empty lastlog, faillog and tallylog in the container.')


def _lines(script):
    with open(os.path.join(COMMIT_RUN_SCRIPTS, script)) as f:
        return [line.strip() for line in f]


def _calls_between(lines, first, last):
    start = lines.index(first)
    return lines[start:lines.index(last, start)]


def test_commit_starter_empties_logs_before_container_size_check():
    lines = _lines('commit_run_starter.sh')
    size_check = [line for line in lines if line.startswith('CONTAINER_SIZE_B=$(docker inspect')][0]

    assert EMPTY_CALL in _calls_between(lines, 'install_pipeline_code', size_check)


def test_pause_empties_logs_before_disk_space_check():
    lines = _lines('pause_run.sh')
    main_flow = lines[len(lines) - lines[::-1].index('install_pipeline_code') - 1:]

    assert EMPTY_CALL in _calls_between(main_flow, 'install_pipeline_code', 'check_free_disc_space')


def test_pause_empties_logs_after_pre_commit_hook_before_first_commit():
    lines = _lines('pause_run.sh')

    assert EMPTY_CALL in _calls_between(lines, 'commit_hook ${CONTAINER_ID} "pre" false true ${PRE_COMMIT_COMMAND}',
                                        'commit_file ${NEW_IMAGE_NAME}')

