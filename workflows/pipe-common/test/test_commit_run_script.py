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

# Runs scripts/commit-run-scripts/commit_run.sh with docker, the pipe-common shell commands and the Python of the node
# host ($CP_PYTHON_PATH, which runs commit_run.py) replaced by stubs, which record their calls. A "python" first on PATH
# fails, as the python of a node host has no pipe-common. Needs bash.

import os
import re
import stat
import subprocess

import pytest

_REPO_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
COMMIT_RUN_SCRIPT = os.path.join(_REPO_DIR, 'scripts', 'commit-run-scripts', 'commit_run.sh')

REGISTRY = 'registry:5000'
NEW_IMAGE_NAME = 'library/centos:v3'
FULL_NEW_IMAGE_NAME = REGISTRY + '/' + NEW_IMAGE_NAME

COMMON_COMMIT_INITIALIZATION_STUB = '''
record() { printf '%s\\n' "$*" >> "$CALLS"; }
commit_hook() { record "commit_hook $*"; }
commit_file() { record "commit_file $*"; }
check_last_exit_code() { :; }
pipe_log_info() { :; }
pipe_log_warn() { :; }
pipe_log_success() { :; }
pipe_log_fail() { :; }
pipe_exec() { eval "$1"; }
docker() { record "docker $*"; }
'''

PYTHON_STUB = '''#!/bin/bash
printf '%%s\\n' "%s $(basename "$1") ${*:2}" >> "$CALLS"
case "$2" in
    ite) echo True ;;
    ive) echo True ;;
esac
'''


def _stub(path, content):
    path.write(content)
    os.chmod(str(path), stat.S_IRWXU)


@pytest.fixture
def commit(tmpdir):
    scripts_dir = tmpdir.mkdir('commit-run-scripts')
    scripts_dir.join('common_commit_initialization.sh').write(COMMON_COMMIT_INITIALIZATION_STUB)
    stub_bin = tmpdir.mkdir('stub-bin')
    _stub(stub_bin.join('python'), PYTHON_STUB % 'python')
    host_python = tmpdir.mkdir('python3.12').join('python3.12')
    _stub(host_python, PYTHON_STUB % 'python3.12')
    calls = tmpdir.join('calls')

    def run(stop_pipeline='false'):
        env = {
            'PATH': str(stub_bin) + os.pathsep + os.environ['PATH'],
            'CALLS': str(calls),
            'SCRIPTS_DIR': str(scripts_dir),
            'CP_PYTHON_PATH': str(host_python),
            'COMMON_REPO_DIR': str(tmpdir.join('pipe-common')),
            'RUN_ID': '1',
            'CONTAINER_ID': 'container',
            'CLEAN_UP': 'true',
            'STOP_PIPELINE': stop_pipeline,
            'REGISTRY_TO_PUSH': REGISTRY,
            'REGISTRY_TO_PUSH_ID': '1',
            'TOOL_GROUP_ID': '1',
            'NEW_IMAGE_NAME': NEW_IMAGE_NAME,
            'FULL_NEW_IMAGE_NAME': FULL_NEW_IMAGE_NAME,
            'TASK_NAME': 'CommitPipelineRun'
        }
        with open(os.devnull, 'w') as devnull:
            subprocess.check_call(['bash', COMMIT_RUN_SCRIPT], env=env, stdout=devnull)
        return calls.read().splitlines()

    return run


def _commit_run_py_calls(calls):
    return [call for call in calls if ' commit_run.py ' in call]


def test_commit_run_py_runs_with_the_python_of_pipe_common(commit):
    calls = _commit_run_py_calls(commit())

    assert calls == ['python3.12 commit_run.py ite 1 1 ' + NEW_IMAGE_NAME,
                     'python3.12 commit_run.py ive 1 ' + FULL_NEW_IMAGE_NAME + ' latest',
                     'python3.12 commit_run.py evs 1 ' + REGISTRY + ' ' + NEW_IMAGE_NAME,
                     'python3.12 commit_run.py ups 1 SUCCESS']


def test_the_run_is_stopped_with_the_python_of_pipe_common(commit):
    calls = _commit_run_py_calls(commit(stop_pipeline='true'))

    assert calls[-1] == 'python3.12 commit_run.py sp 1'
    assert not [call for call in calls if call.startswith('python ')]


# "python", "python2", "pip" or "pip2" as a command: at the start of a line, of a quoted command or a substitution
_PYTHON_COMMAND = re.compile(r'(?:^\s*|[;&|`]\s*|\$\(\s*|"\s*)(?:python2?|pip2?)(?=\s|$)')


def test_no_commit_script_calls_the_python_first_on_path():
    scripts_dir = os.path.dirname(COMMIT_RUN_SCRIPT)
    found = []
    for name in sorted(os.listdir(scripts_dir)):
        with open(os.path.join(scripts_dir, name)) as f:
            for number, line in enumerate(f, 1):
                if not line.lstrip().startswith('#') and (_PYTHON_COMMAND.search(line) or 'CP_PYTHON2_PATH' in line):
                    found.append('%s:%d: %s' % (name, number, line.strip()))
    assert found == []
