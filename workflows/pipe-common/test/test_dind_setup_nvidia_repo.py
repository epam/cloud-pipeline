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

# add_nvidia_docker_apt_repo of workflows/pipe-common/shell/dind_setup: which NVIDIA list it adds, and how it trusts
# the NVIDIA key, by the apt version. Runs the function with bash, stub curl, dpkg-query and apt-key, and the absolute
# paths replaced with temporary ones. Needs bash and dpkg (dpkg --compare-versions). Runs on Python 2.7 and 3.

import os
import re
import shutil
import subprocess
import tempfile
import unittest

try:
    from shutil import which
except ImportError:  # Python 2
    from distutils.spawn import find_executable as which

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
DIND_SETUP = os.path.join(REPO_ROOT, 'workflows', 'pipe-common', 'shell', 'dind_setup')
BASH = which('bash') or '/bin/bash'

NVIDIA_URL = 'https://nvidia.github.io/nvidia-docker/'
KEY = '-----BEGIN PGP PUBLIC KEY BLOCK-----\nKEY\n-----END PGP PUBLIC KEY BLOCK-----\n'
# The list that NVIDIA serves for ubuntu20.04 and ubuntu22.04
NVIDIA_LIST = 'deb https://nvidia.github.io/libnvidia-container/stable/ubuntu18.04/$(ARCH) /\n' \
              '#deb https://nvidia.github.io/libnvidia-container/experimental/ubuntu18.04/$(ARCH) /\n'
KEYRING = '/etc/apt/keyrings/nvidia-docker.asc'
LIST = '/etc/apt/sources.list.d/nvidia-docker.list'

# Logs its URL, and prints the key or the list
CURL_STUB = """#!/bin/bash
url="${@: -1}"
echo "curl $url" >> "$STUB_LOG"
case "$url" in
    */gpgkey) printf '%s' "$NVIDIA_KEY" ;;
    */nvidia-docker.list) printf '%s' "$NVIDIA_LIST" ;;
esac
"""
# Prints the version of the apt package
DPKG_QUERY_STUB = '#!/bin/bash\nprintf "%s" "$APT_VERSION"\n'
# Logs the key it gets. Fails as on apt 2.9+ if APT_KEY_MISSING is set
APT_KEY_STUB = """#!/bin/bash
[ "$APT_KEY_MISSING" ] && { echo "apt-key: command not found" >&2; exit 127; }
echo "apt-key $* $(cat)" >> "$STUB_LOG"
"""

# An absolute /etc path in the shell code
SYSTEM_PATH = re.compile(r'(?<![\w./-])/etc/')


def _read(path):
    with open(path) as f:
        return f.read()


def _function_source(text, name):
    start = text.index('function %s {' % name)
    end = text.index('\n}\n', start) + 3
    return text[start:end]


def _stub(directory, name, body):
    path = os.path.join(directory, name)
    with open(path, 'w') as f:
        f.write(body)
    os.chmod(path, 0o755)


@unittest.skipUnless(which('dpkg'), 'needs dpkg')
class AddNvidiaDockerAptRepoTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = os.path.join(self.tmp, 'root')
        self.stub_bin = os.path.join(self.tmp, 'stub-bin')
        self.log = os.path.join(self.tmp, 'calls.log')
        os.makedirs(os.path.join(self.root, 'etc', 'apt', 'sources.list.d'))
        os.makedirs(self.stub_bin)
        _stub(self.stub_bin, 'curl', CURL_STUB)
        _stub(self.stub_bin, 'dpkg-query', DPKG_QUERY_STUB)
        _stub(self.stub_bin, 'apt-key', APT_KEY_STUB)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def path(self, path):
        return self.root + path

    def add(self, distribution, apt_version, **env):
        text = _read(DIND_SETUP)
        variables = ''.join(line + '\n' for line in text.splitlines() if line.startswith('NVIDIA_DOCKER_APT_'))
        code = SYSTEM_PATH.sub(lambda path: self.root + path.group(0),
                               variables + _function_source(text, 'add_nvidia_docker_apt_repo'))
        self.assertIsNone(SYSTEM_PATH.search(code))
        run_env = {'PATH': self.stub_bin + os.pathsep + os.environ.get('PATH', ''), 'STUB_LOG': self.log,
                   'NVIDIA_KEY': KEY, 'NVIDIA_LIST': NVIDIA_LIST, 'APT_VERSION': apt_version}
        run_env.update(env)
        proc = subprocess.Popen([BASH, '-c', code + 'add_nvidia_docker_apt_repo "%s"\n' % distribution], env=run_env,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)
        stdout, stderr = proc.communicate()
        self.assertEqual(0, proc.returncode, stderr)
        return stdout

    def calls(self):
        return _read(self.log).splitlines() if os.path.exists(self.log) else []

    def clear_calls(self):
        if os.path.exists(self.log):
            os.remove(self.log)

    def assert_signed_by_keyring(self):
        keyring = self.path(KEYRING)
        self.assertEqual(KEY, _read(keyring))
        self.assertEqual(NVIDIA_LIST.replace('deb https://', 'deb [signed-by=%s] https://' % keyring, 1),
                         _read(self.path(LIST)))
        self.assertFalse([call for call in self.calls() if call.startswith('apt-key')])

    def test_the_list_names_the_keyring_on_apt_without_apt_key(self):
        self.add('ubuntu22.04', '3.2.1ubuntu0.1', APT_KEY_MISSING='true')
        self.assert_signed_by_keyring()
        self.assertIn('curl %subuntu22.04/nvidia-docker.list' % NVIDIA_URL, self.calls())

    def test_the_list_names_the_keyring_from_apt_1_4_on(self):
        for apt_version in ('1.4', '1.6.17', '2.4.15ubuntu0.1'):
            self.add('ubuntu20.04', apt_version)
            self.assert_signed_by_keyring()

    def test_the_releases_after_22_04_use_the_22_04_list(self):
        for distribution in ('ubuntu22.10', 'ubuntu24.04', 'ubuntu25.10', 'ubuntu26.04'):
            self.clear_calls()
            self.add(distribution, '3.2.1ubuntu0.1')
            self.assertIn('curl %subuntu22.04/nvidia-docker.list' % NVIDIA_URL, self.calls(), distribution)

    def test_the_other_distributions_keep_their_list(self):
        for distribution in ('ubuntu18.04', 'ubuntu20.04', 'ubuntu22.04', 'debian11'):
            self.clear_calls()
            self.add(distribution, '2.4.15')
            self.assertIn('curl %s%s/nvidia-docker.list' % (NVIDIA_URL, distribution), self.calls())

    def test_apt_before_1_4_imports_the_key_with_apt_key(self):
        self.add('ubuntu16.04', '1.2.35')
        self.assertIn('apt-key add - ' + KEY.rstrip('\n'), _read(self.log))
        self.assertEqual(NVIDIA_LIST, _read(self.path(LIST)))
        self.assertFalse(os.path.exists(self.path(KEYRING)))


if __name__ == '__main__':
    unittest.main()
