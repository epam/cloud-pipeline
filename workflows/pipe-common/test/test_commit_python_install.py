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

# Covers the Python 3.12 installation that committing and pausing a run do on the node host: the install_python
# function of scripts/commit-run-scripts/common_commit_initialization.sh. Runs it with bash, a stub "wget" first on
# PATH, and /usr/local in the function replaced by a temporary folder. Needs bash and tar.

import os
import shutil
import stat
import subprocess
import tarfile
import tempfile
import unittest

COMMON_COMMIT_INITIALIZATION_SH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
    'scripts', 'commit-run-scripts', 'common_commit_initialization.sh')
BASH = shutil.which('bash') or '/bin/bash'


def _function_source(name):
    with open(COMMON_COMMIT_INITIALIZATION_SH) as f:
        text = f.read()
    start = text.index('\n%s() {' % name) + 1
    end = text.index('\n}\n', start) + 3
    return text[start:end]


def _write(path, content=''):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(content)
    os.chmod(path, stat.S_IRWXU)


class CommitPythonInstallTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.usr_local = os.path.join(self.tmp, 'usr-local')
        os.makedirs(self.usr_local)
        self.stub_bin = os.path.join(self.tmp, 'stub-bin')
        self.tarball = os.path.join(self.tmp, 'python3.12.tar.gz')
        # wget -q --no-check-certificate <url> -O <file>
        _write(os.path.join(self.stub_bin, 'wget'),
               '#!/bin/sh\nwhile [ $# -gt 0 ]; do [ "$1" = "-O" ] && cp "%s" "$2"; shift; done\n' % self.tarball)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def _distro(self, with_openssl):
        dist = os.path.join(self.tmp, 'dist')
        _write(os.path.join(dist, 'python3.12', 'bin', 'python3.12'), '#!/bin/sh\n')
        if with_openssl:
            # The previous distro (python3.12.0.tar.gz) links its own /usr/local/openssl
            _write(os.path.join(dist, 'openssl', 'from-distro'))
        with tarfile.open(self.tarball, 'w:gz') as tar:
            for name in sorted(os.listdir(dist)):
                tar.add(os.path.join(dist, name), arcname=name)

    def _host_has_openssl(self):
        _write(os.path.join(self.usr_local, 'openssl', 'from-host'))

    def _install(self):
        script = _function_source('install_python').replace('/usr/local', self.usr_local) \
            + '\ninstall_python\necho "RC=$?"\n'
        env = {'PATH': self.stub_bin + os.pathsep + os.environ.get('PATH', '')}
        proc = subprocess.run([BASH, '-c', script], env=env, capture_output=True, text=True)
        self.assertIn('RC=0', proc.stdout, proc.stdout + proc.stderr)

    def test_installs_the_python_where_the_host_has_none(self):
        self._distro(with_openssl=False)
        self._install()
        self.assertTrue(os.access(os.path.join(self.usr_local, 'python3.12', 'bin', 'python3.12'), os.X_OK))
        self.assertEqual(['python3.12'], os.listdir(self.usr_local))

    def test_a_distro_without_openssl_keeps_the_openssl_of_the_host(self):
        self._host_has_openssl()
        self._distro(with_openssl=False)
        self._install()
        self.assertEqual(['from-host'], os.listdir(os.path.join(self.usr_local, 'openssl')))

    def test_a_distro_with_openssl_replaces_the_openssl_of_the_host(self):
        self._host_has_openssl()
        self._distro(with_openssl=True)
        self._install()
        self.assertEqual(['from-distro'], os.listdir(os.path.join(self.usr_local, 'openssl')))
        self.assertEqual(['openssl', 'python3.12'], sorted(os.listdir(self.usr_local)))


if __name__ == '__main__':
    unittest.main()
