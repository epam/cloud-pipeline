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

# The shell commands of pipe-common (shell/) run their Python code with the Python of the run ($CP_PYTHON_PATH), which
# is Python 3.12: no command calls $CP_PYTHON2_PATH or the python first on PATH, and the Python code that the commands
# pass to it runs on Python 3. The commands run with bash, $CP_PYTHON_PATH is the Python of the tests, and the pipeline
# package is a stub where a command calls the API. Needs bash.

import os
import re
import subprocess
import sys

import pytest

SHELL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell')
# Python scripts without the .py suffix, which the shell commands run with $CP_PYTHON_PATH
PYTHON_SCRIPTS = ['spark_setup_ui']
# "python", "python2", "pip" or "pip2" as a command: at the start of a line, of a quoted command or a substitution
PYTHON_COMMAND = re.compile(r'(?:^\s*|[;&|`]\s*|\$\(\s*|"\s*)(?:python2?|pip2?)(?=\s|$)')

PIPELINE_STUB = '''import os


class bcolors:
    WARNING = '<warning>'
    ENDC = '</warning>'


class Logger:
    @staticmethod
    def log_task_event(task, text, status=None, api_url=None, log_dir=None, run_id=None):
        with open(os.environ['LOGGED'], 'a') as f:
            f.write('%s|%s|%s|%s|%s\\n' % (task, text, status, api_url, run_id))
'''

PIPELINE_API_STUB = '''import json


class TaskStatus:
    SUCCESS, FAILURE, RUNNING, STOPPED, PAUSED = range(5)


class PipelineAPI:
    def __init__(self, api_url, log_dir):
        self.api_url = api_url

    def load_datastorage_rules(self, pipeline_id):
        return json.dumps([{'pipelineId': pipeline_id, 'fileMask': '*.bam', 'moveToSts': False}])
'''


def _shell_commands():
    for name in sorted(os.listdir(SHELL_DIR)):
        if name not in PYTHON_SCRIPTS:
            yield name


def _shell(name):
    with open(os.path.join(SHELL_DIR, name)) as f:
        return f.read()


@pytest.fixture
def run_command(tmpdir):
    stub = tmpdir.mkdir('stub')
    stub.mkdir('pipeline').join('__init__.py').write(PIPELINE_STUB)
    stub.join('pipeline', 'api.py').write(PIPELINE_API_STUB)
    logged = tmpdir.join('logged')

    def run(name, *args, **env):
        run_env = {
            'PATH': os.environ['PATH'],
            'CP_PYTHON_PATH': sys.executable,
            'PYTHONPATH': str(stub),
            'PYTHONDONTWRITEBYTECODE': '1',
            'LOGGED': str(logged),
            'LOG_DIR': str(tmpdir),
            'API': 'https://cp.example.com/pipeline/restapi/',
            'RUN_ID': '42'
        }
        run_env.update(env)
        # Not in pipe-common: "python -c" puts the current folder first on sys.path, before the stub
        proc = subprocess.run(['bash', os.path.join(SHELL_DIR, name)] + list(args), env=run_env, cwd=str(tmpdir),
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)
        return proc, logged.read().splitlines() if logged.check() else []

    return run


def test_no_shell_command_calls_python2_or_the_python_first_on_path():
    found = []
    for name in _shell_commands():
        for number, line in enumerate(_shell(name).splitlines(), 1):
            if line.lstrip().startswith('#'):
                continue
            if 'CP_PYTHON2_PATH' in line or PYTHON_COMMAND.search(line):
                found.append('%s:%d: %s' % (name, number, line.strip()))
    assert found == []


@pytest.mark.parametrize('url,part,expected', [
    ('https://git.example.com:8443/group/repo.git', 'hostname', 'git.example.com'),
    ('https://git.example.com:8443/group/repo.git', 'port', '8443'),
    ('git.example.com/group/repo.git', 'scheme', 'http'),
    ('git.example.com:8080', 'port', '8080'),
    ('https://git.example.com/group/repo.git', None, 'https://git.example.com/group/repo.git'),
])
def test_parse_url_on_python3(run_command, url, part, expected):
    proc, _ = run_command('parse_url', *([url, part] if part else [url]))

    assert proc.returncode == 0, proc.stderr
    assert proc.stdout.strip() == expected


@pytest.mark.parametrize('variable', ['GIT_HOST_EXTERNAL_PREF', 'GIT_HOST_PREF'])
def test_ssh_personal_keys_get_the_git_host_names_on_python3(variable):
    line = [line for line in _shell('ssh_setup_personal_keys').splitlines()
            if line.startswith('%s=$($CP_PYTHON_PATH ' % variable)]
    assert len(line) == 1
    script = '%s="https://git.example.com:8443/"\n%s\necho "$%s"\n' % (variable, line[0], variable)
    proc = subprocess.run(['bash', '-c', script], env={'PATH': os.environ['PATH'], 'CP_PYTHON_PATH': sys.executable},
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)

    assert proc.returncode == 0, proc.stderr
    assert proc.stdout.strip() == 'git.example.com'


def test_pipe_storage_rules_prints_the_rules_of_the_pipeline(run_command):
    proc, _ = run_command('pipe_storage_rules', PIPELINE_ID='7')

    assert proc.returncode == 0, proc.stderr
    assert proc.stdout.strip() == '[{"pipelineId": 7, "fileMask": "*.bam", "moveToSts": false}]'


def test_pipe_log_sends_the_event_with_the_python_of_the_run(run_command):
    proc, logged = run_command('pipe_log', 'SUCCESS', 'Done - 100%', 'Task')

    assert proc.returncode == 0, proc.stderr
    assert logged == ['Task|Done - 100%|0|https://cp.example.com/pipeline/restapi/|42']


def test_pipe_log_sends_a_warning_as_a_colored_running_event(run_command):
    proc, logged = run_command('pipe_log', 'WARNING', 'Slow', 'Task')

    assert proc.returncode == 0, proc.stderr
    assert logged == ['Task|<warning>Slow</warning>|2|https://cp.example.com/pipeline/restapi/|42']


def test_lustre_autoscale_computes_the_required_disk_size_on_python3():
    text = _shell('lustre_autoscale')
    start = text.index('function get_required_disk_size() {')
    function = text[start:text.index('\n}\n', start) + 3]
    proc = subprocess.run(['bash', '-c', function + 'get_required_disk_size 1200 0.25\n'],
                          env={'PATH': os.environ['PATH'], 'CP_PYTHON_PATH': sys.executable},
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)

    assert proc.stdout.strip() == '1500'


@pytest.mark.parametrize('name,pins', [
    # The desktop endpoints run serve_desktop.py with Flask on the Python 3.12 of the run
    ('nice_dcv_setup', 'flask==3.1.3 Flask-HTTPAuth==4.8.1'),
    ('nomachine_setup', 'flask==3.1.3'),
])
def test_desktops_install_flask_versions_for_python3(name, pins):
    installs = [line.strip() for line in _shell(name).splitlines() if '-m pip install' in line]

    assert len(installs) == 1
    assert installs[0].startswith('$CP_PYTHON_PATH -m pip install ')
    assert installs[0].split('$CP_PIP_EXTRA_ARGS ', 1)[1].rstrip(' &\\') == pins
