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

# How configure_package_manager of scripts/pipeline-launch/launch.sh adds the Cloud Pipeline apt repo on Ubuntu: as a
# trusted repo in its own list file, with no key, so the apt-key stub fails as on Ubuntu 25.10+. Runs the function with
# bash and a stub apt-get, with the absolute /etc and /usr/lib/apt paths replaced with temporary ones. Runs on Python 2.7
# and 3.

import os
import re
import stat
import subprocess

import pytest

LAUNCH_SH = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
                         'scripts', 'pipeline-launch', 'launch.sh')

DIST_URL = 'https://dist.example/'
UBUNTU_SOURCES = 'deb http://archive.ubuntu.com/ubuntu/ noble main restricted\n'
# The line that the older runs added to /etc/apt/sources.list, and the list file that is used now
OLD_CP_SOURCE = 'deb https://dist.example/tools/repos/ubuntu/24.04 stable main\n'
CP_LIST = '/etc/apt/sources.list.d/cloud-pipeline.list'
CP_TRUSTED_SOURCE = 'deb [trusted=yes] https://dist.example/tools/repos/ubuntu/24.04 stable main\n'
APT_UPDATE = 'update -qq -y --allow-insecure-repositories'
APT_INSTALL_DEPS = 'install curl apt-transport-https -y -qq'

# Logs its arguments. With STUB_REPO_UNREACHABLE set, fails a refresh while the Cloud Pipeline list exists
APT_GET_STUB = '''#!/bin/bash
echo "$*" >> "$STUB_LOG"
if [ "$STUB_REPO_UNREACHABLE" ] && [ "$1" == "update" ] && [ -f "$STUB_CP_LIST" ]; then
    exit 100
fi
'''
# apt 2.9+ (Ubuntu 25.10+) has no apt-key
APT_KEY_STUB = '#!/bin/bash\necho "apt-key: command not found" >&2\nexit 127\n'
# check_installed that does not find curl, whether the host has it or not
CURL_NOT_INSTALLED = 'function check_installed { [ "$1" != "curl" ] && command -v "$1" >/dev/null 2>&1; }\n'

# An absolute /etc or /usr/lib/apt path in the shell code
SYSTEM_PATH = re.compile(r'(?<![\w./-])/(etc|usr/lib/apt)/')
# The https method of apt, which apt before 1.5 has only with apt-transport-https
APT_HTTPS_METHOD = '/usr/lib/apt/methods/https'


def _read(path):
    with open(path) as f:
        return f.read()


def _write(path, text):
    with open(path, 'w') as f:
        f.write(text)


def _function_source(text, name):
    start = text.index('function %s {' % name)
    end = text.index('\n}\n', start) + 3
    return text[start:end]


def _stub(directory, name, body):
    path = os.path.join(directory, name)
    _write(path, body)
    os.chmod(path, stat.S_IRWXU)


class Run(object):

    def __init__(self, tmpdir):
        self.root = str(tmpdir.join('root'))
        for directory in ('etc/apt/sources.list.d', 'etc/apt/apt.conf.d', 'usr/lib/apt/methods'):
            os.makedirs(os.path.join(self.root, directory))
        _write(self.path('/etc/apt/sources.list'), UBUNTU_SOURCES)
        _write(self.path(APT_HTTPS_METHOD), '')
        self.stub_bin = str(tmpdir.mkdir('stub-bin'))
        self.log = str(tmpdir.join('calls.log'))
        _stub(self.stub_bin, 'apt-get', APT_GET_STUB)
        _stub(self.stub_bin, 'apt-key', APT_KEY_STUB)
        _stub(self.stub_bin, 'curl', '#!/bin/bash\n')

    def path(self, path):
        return self.root + path

    def __call__(self, code='', **env):
        launch = _read(LAUNCH_SH)
        functions = SYSTEM_PATH.sub(lambda path: self.root + path.group(0),
                                    _function_source(launch, 'configure_package_manager'))
        assert SYSTEM_PATH.search(functions) is None
        script = _function_source(launch, 'check_cp_cap') + _function_source(launch, 'check_installed') + code \
            + 'function configure_package_manager_pip { :; }\n' + functions + 'configure_package_manager\n'
        run_env = {'PATH': self.stub_bin + os.pathsep + os.environ.get('PATH', ''), 'STUB_LOG': self.log,
                   'STUB_CP_LIST': self.path(CP_LIST), 'CP_OS': 'ubuntu', 'CP_VER': '24.04',
                   'CP_REPO_ENABLED': 'true', 'GLOBAL_DISTRIBUTION_URL': DIST_URL}
        run_env.update(env)
        proc = subprocess.Popen(['bash', '-c', script], env=run_env, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                universal_newlines=True)
        stdout, stderr = proc.communicate()
        assert proc.returncode == 0, stderr
        return stdout

    def calls(self):
        return _read(self.log).splitlines() if os.path.exists(self.log) else []


@pytest.fixture
def run(tmpdir):
    return Run(tmpdir)


def _assert_repo_added(run, stdout):
    assert 'Failed to configure' not in stdout
    assert _read(run.path(CP_LIST)) == CP_TRUSTED_SOURCE


def test_the_repo_is_added_as_trusted_with_no_key(run):
    stdout = run()
    _assert_repo_added(run, stdout)
    assert run.calls() == [APT_UPDATE]
    assert _read(run.path('/etc/apt/sources.list')) == UBUNTU_SOURCES


def test_curl_and_apt_transport_https_are_installed_without_gnupg_when_curl_is_missing(run):
    stdout = run(CURL_NOT_INSTALLED)
    _assert_repo_added(run, stdout)
    assert run.calls() == [APT_UPDATE, APT_INSTALL_DEPS, APT_UPDATE]


def test_curl_and_apt_transport_https_are_installed_when_apt_has_no_https_method(run):
    os.remove(run.path(APT_HTTPS_METHOD))
    stdout = run()
    _assert_repo_added(run, stdout)
    assert run.calls() == [APT_UPDATE, APT_INSTALL_DEPS, APT_UPDATE]


def test_the_line_that_an_older_run_added_to_sources_list_is_removed(run):
    _write(run.path('/etc/apt/sources.list'), OLD_CP_SOURCE + UBUNTU_SOURCES)
    stdout = run()
    _assert_repo_added(run, stdout)
    assert _read(run.path('/etc/apt/sources.list')) == UBUNTU_SOURCES


def test_the_repo_is_added_when_the_image_has_no_sources_list(run):
    os.remove(run.path('/etc/apt/sources.list'))
    stdout = run()
    _assert_repo_added(run, stdout)
    assert not os.path.exists(run.path('/etc/apt/sources.list'))


def test_the_offline_mode_removes_the_default_lists_and_adds_the_repo(run):
    _write(run.path('/etc/apt/sources.list.d/ubuntu.sources'), 'Types: deb\n')
    stdout = run(CP_CAP_OFFLINE='true')
    _assert_repo_added(run, stdout)
    assert os.listdir(run.path('/etc/apt/sources.list.d')) == ['cloud-pipeline.list']
    assert _read(run.path('/etc/apt/sources.list')) == '\n'


def test_the_repo_is_removed_when_its_refresh_fails(run):
    _write(run.path('/etc/apt/sources.list'), OLD_CP_SOURCE + UBUNTU_SOURCES)
    stdout = run(CP_REPO_RETRY_COUNT='2', STUB_REPO_UNREACHABLE='true')
    assert stdout.count('Failed to configure') == 2
    assert run.calls() == [APT_UPDATE, APT_UPDATE]
    assert not os.path.exists(run.path(CP_LIST))
    assert _read(run.path('/etc/apt/sources.list')) == UBUNTU_SOURCES
