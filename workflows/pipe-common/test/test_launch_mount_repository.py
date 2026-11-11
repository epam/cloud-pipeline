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

# Covers the pipeline repository mount of scripts/pipeline-launch/launch.sh: the permission check before the gitfs
# mount runs with the Python of pipe-common ($CP_PYTHON_PATH), not with the "python" first on PATH. Runs the mount
# block of launch.sh with bash. The interpreter of $CP_PYTHON_PATH, "python" and "gitfs" are stubs, which record their
# calls; the pipe_log_* functions are stubs, which print the message. Needs bash.

import os
import stat
import subprocess

import pytest

LAUNCH_SH = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
                         'scripts', 'pipeline-launch', 'launch.sh')

GIT_REPO = 'https://git.example.com/library/pipeline.git'

# Records its name and arguments. "python" fails, as a python without the pipeline package of pipe-common does
STUB = '''#!/bin/bash
echo "%s $*" >> "$STUB_CALLS"
exit %s
'''

LOG_STUBS = '''
function pipe_log_info { echo "INFO: $1"; }
function pipe_log_warn { echo "WARN: $1"; }
function pipe_log_success { echo "SUCCESS: $1"; }
'''


def _mount_block_source():
    with open(LAUNCH_SH) as f:
        text = f.read()
    start = text.index('if [ $GITFS_INSTALL -ne 0 ] && [ ! -z "$GIT_REPO" ];')
    end = text.index('\nfi\n', start) + 4
    return text[start:end]


def _write_stub(path, name, exit_code):
    path.write(STUB % (name, exit_code))
    os.chmod(str(path), stat.S_IRWXU)


@pytest.fixture
def mount_repository(tmpdir):
    stub_bin = tmpdir.mkdir('stub-bin')
    _write_stub(stub_bin.join('python'), 'python', '1')
    _write_stub(stub_bin.join('gitfs'), 'gitfs', '0')
    pipe_common_python = tmpdir.mkdir('python3.12').join('python3.12')
    _write_stub(pipe_common_python, 'cp-python3.12', '$STUB_PERMISSION_RESULT')
    calls = tmpdir.join('calls')
    mount = tmpdir.join('repository', 'pipeline')

    def run(permission_result):
        env = {
            'PATH': str(stub_bin) + os.pathsep + os.environ['PATH'],
            'CP_PYTHON_PATH': str(pipe_common_python),
            'COMMON_REPO_DIR': '/opt/common',
            'GITFS_INSTALL': '1',
            'GIT_REPO': GIT_REPO,
            'PIPELINE_ID': '42',
            'PIPELINE_MOUNT': str(mount),
            'LOG_DIR': str(tmpdir),
            'MOUNT_GIT_TASK_NAME': 'MountRepository',
            'STUB_CALLS': str(calls),
            'STUB_PERMISSION_RESULT': permission_result
        }
        output = subprocess.check_output(['bash', '-c', LOG_STUBS + _mount_block_source()], env=env)
        recorded = calls.read().splitlines() if calls.check() else []
        return output.decode('utf-8'), recorded

    run.mount = str(mount)
    run.log_dir = str(tmpdir)
    return run


def test_the_permission_check_runs_with_the_python_of_pipe_common(mount_repository):
    output, calls = mount_repository('0')

    assert calls == ['cp-python3.12 /opt/common/scripts/check_pipeline_permission.py '
                     '--pipeline_id 42 --permission WRITE',
                     'gitfs %s %s -o log=%s/gitfs.log,allow_other=true'
                     % (GIT_REPO, mount_repository.mount, mount_repository.log_dir)]
    assert 'INFO: GitFs is installed. Will try to mount git repository.' in output
    assert 'SUCCESS: Mounted git repository to %s' % mount_repository.mount in output


def test_the_repository_is_not_mounted_without_the_write_permission(mount_repository):
    output, calls = mount_repository('1')

    assert calls == ['cp-python3.12 /opt/common/scripts/check_pipeline_permission.py '
                     '--pipeline_id 42 --permission WRITE']
    assert 'INFO: User is not allowed to modify GIT repository.' in output
