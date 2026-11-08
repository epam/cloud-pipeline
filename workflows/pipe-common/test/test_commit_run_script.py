# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# Runs scripts/commit-run-scripts/commit_run.sh with docker, commit_run.py and the pipe-common shell
# commands replaced by stubs, which record their calls.

import os
import stat
import subprocess

import pytest

_REPO_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
COMMIT_RUN_SCRIPT = os.path.join(_REPO_DIR, 'scripts', 'commit-run-scripts', 'commit_run.sh')

REGISTRY = 'registry:5000'
NEW_IMAGE_NAME = 'library/centos:v3'
FULL_NEW_IMAGE_NAME = REGISTRY + '/' + NEW_IMAGE_NAME
LATEST_IMAGE_NAME = REGISTRY + '/library/centos:latest'

# The real pipe_exec puts its text into a python string literal, where \' becomes ', and runs it with a shell
PIPE_EXEC_STUB = r'''
pipe_exec() { eval "$(printf '%s' "$1" | sed "s/\\\\'/'/g")"; }
'''

COMMON_COMMIT_INITIALIZATION_STUB = '''
record() { printf '%s\\n' "$*" >> "$CALLS"; }
commit_hook() { :; }
commit_file() { :; }
check_last_exit_code() { :; }
pipe_log_info() { record "log $1"; }
pipe_log_warn() { record "log $1"; }
pipe_log_success() { record "log $1"; }
pipe_log_fail() { record "log $1"; }
docker() {
    record "docker $*"
    [ "$1" != "commit" ] || record "docker commit arguments: $(printf '<%s>' "$@")"
}
''' + PIPE_EXEC_STUB

COMMIT_RUN_PY_STUB = '''#!/bin/bash
shift
printf '%s\\n' "commit_run.py $*" >> "$CALLS"
case "$1" in
    ite) echo True ;;
    ive) echo "$STUB_IVE" ;;
    idvs) echo "$STUB_IDVS" ;;
esac
'''


@pytest.fixture
def commit(tmpdir):
    scripts_dir = tmpdir.mkdir('commit-run-scripts')
    scripts_dir.join('common_commit_initialization.sh').write(COMMON_COMMIT_INITIALIZATION_STUB)
    python_stub = tmpdir.join('python')
    python_stub.write(COMMIT_RUN_PY_STUB)
    os.chmod(str(python_stub), stat.S_IRWXU)
    calls = tmpdir.join('calls')

    def run(latest_exists, default_version_set, os_label=''):
        env = {
            'PATH': os.environ['PATH'],
            'CALLS': str(calls),
            'SCRIPTS_DIR': str(scripts_dir),
            'CP_PYTHON_PATH': str(python_stub),
            'COMMON_REPO_DIR': str(tmpdir.join('pipe-common')),
            'RUN_ID': '1',
            'CONTAINER_ID': 'container',
            'CLEAN_UP': 'true',
            'STOP_PIPELINE': 'false',
            'REGISTRY_TO_PUSH': REGISTRY,
            'REGISTRY_TO_PUSH_ID': '1',
            'TOOL_GROUP_ID': '1',
            'NEW_IMAGE_NAME': NEW_IMAGE_NAME,
            'FULL_NEW_IMAGE_NAME': FULL_NEW_IMAGE_NAME,
            'TASK_NAME': 'CommitPipelineRun',
            'STUB_IVE': latest_exists,
            'STUB_IDVS': default_version_set,
            'CP_COMMIT_OS_LABEL': os_label
        }
        with open(os.devnull, 'w') as devnull:
            subprocess.check_call(['bash', COMMIT_RUN_SCRIPT], env=env, stdout=devnull)
        return calls.read().splitlines()

    return run


def test_commit_does_not_create_latest_version_of_tool_with_default_version(commit):
    calls = commit(latest_exists='False', default_version_set='True')

    assert 'commit_run.py idvs 1 ' + FULL_NEW_IMAGE_NAME in calls
    assert not [call for call in calls if call.startswith('docker tag')]
    assert 'log [INFO] ' + FULL_NEW_IMAGE_NAME + ' tool has a default version set, ' \
                                                 'latest version will not be created' in calls
    assert 'commit_run.py ups 1 SUCCESS' in calls


def test_commit_reads_default_version_check_after_retries(commit):
    calls = commit(latest_exists='False',
                   default_version_set='There is exception with message:timeout \n Retry: (1/10)\nTrue')

    assert not [call for call in calls if call.startswith('docker tag')]


def test_commit_creates_latest_version_of_tool_without_default_version(commit):
    calls = commit(latest_exists='False', default_version_set='False')

    assert 'docker tag ' + FULL_NEW_IMAGE_NAME + ' ' + LATEST_IMAGE_NAME in calls
    assert 'docker push ' + LATEST_IMAGE_NAME in calls
    assert 'commit_run.py ups 1 SUCCESS' in calls


def test_commit_creates_latest_version_if_default_version_is_unknown(commit):
    calls = commit(latest_exists='False', default_version_set='')

    assert 'docker tag ' + FULL_NEW_IMAGE_NAME + ' ' + LATEST_IMAGE_NAME in calls
    assert 'docker push ' + LATEST_IMAGE_NAME in calls


def _final_commit(calls):
    return [call for call in calls if call.startswith('docker commit --pause=false --change=')][0]


def test_commit_labels_new_image_with_os_of_run_image(commit):
    calls = commit(latest_exists='True', default_version_set='', os_label='rocky:8.7')

    final_commit = 'docker commit arguments: <commit><--pause=false><--change=ENV'
    arguments = [call for call in calls if call.startswith(final_commit)]
    assert len(arguments) == 1
    assert '<--change=LABEL com.epam.cloud-pipeline.os=rocky:8.7>' in arguments[0]


def test_commit_does_not_label_new_image_without_os(commit):
    calls = commit(latest_exists='True', default_version_set='')

    assert 'LABEL' not in _final_commit(calls)


def test_commit_does_not_label_new_image_with_malformed_os(commit):
    calls = commit(latest_exists='True', default_version_set='', os_label="rocky:8' x")

    assert 'LABEL' not in _final_commit(calls)


def test_commit_does_not_check_default_version_of_tool_with_latest_version(commit):
    calls = commit(latest_exists='True', default_version_set='True')

    assert not [call for call in calls if call.startswith('commit_run.py idvs')]
    assert not [call for call in calls if call.startswith('docker tag')]
    assert 'commit_run.py ups 1 SUCCESS' in calls
