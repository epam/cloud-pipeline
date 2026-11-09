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

# Covers the Python 3.12 installation of scripts/pipeline-launch/launch.sh: what it replaces in the image, and what a
# failed download or unpack leaves behind (what the image had, and no temporary folder). Runs the
# install_python3_packages function of launch.sh with bash, a stub "wget" first on PATH, and a temporary folder in
# place of /usr/local. Needs bash and tar.

import os
import shutil
import stat
import subprocess
import tarfile
import tempfile
import unittest

LAUNCH_SH = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
                         'scripts', 'pipeline-launch', 'launch.sh')
BASH = shutil.which('bash') or '/bin/bash'


def _function_source(name):
    with open(LAUNCH_SH) as f:
        text = f.read()
    start = text.index('function %s {' % name)
    end = text.index('\n}\n', start) + 3
    return text[start:end]


def _write(path, content, executable=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(content)
    if executable:
        os.chmod(path, 0o755)


class LaunchPythonInstallTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.install_path = os.path.join(self.tmp, 'usr-local')
        os.makedirs(os.path.join(self.install_path, 'bin'))
        self.stub_bin = os.path.join(self.tmp, 'stub-bin')
        os.makedirs(self.stub_bin)
        self.tarball = os.path.join(self.tmp, 'python3.12.tar.gz')

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def _tarball_of(self, files):
        dist = os.path.join(self.tmp, 'dist')
        for name in files:
            _write(os.path.join(dist, name), '#!/bin/sh\necho distro\n', executable=True)
        with tarfile.open(self.tarball, 'w:gz') as tar:
            for name in sorted(os.listdir(dist)):
                tar.add(os.path.join(dist, name), arcname=name)

    def _distro(self, with_openssl):
        files = ['python3.12/bin/python3.12', 'python3.12/bin/pip3.12']
        if with_openssl:
            files.append('openssl/lib/from-distro')
        self._tarball_of(files)
        self._stub_wget('cp "%s" "$4"\n' % self.tarball)

    def _stub_wget(self, body):
        # wget -q <url> -O <file>
        _write(os.path.join(self.stub_bin, 'wget'), '#!/bin/sh\n' + body, executable=True)

    def _image_has(self):
        # E.g. a python:3.x image committed from an earlier run: its own python3 and pip3 in /usr/local/bin, and the
        # Python 3.12 distribution the earlier run installed
        _write(self._path('python3.12', 'bin', 'python3.12'), '#!/bin/sh\necho image\n', executable=True)
        _write(self._path('python3.12', 'bin', 'pip3.12'), '#!/bin/sh\necho image\n', executable=True)
        _write(self._path('openssl', 'lib', 'from-image'), '')
        _write(self._path('bin', 'python3.11'), '#!/bin/sh\necho image\n', executable=True)
        os.symlink('python3.11', self._path('bin', 'python3'))
        _write(self._path('bin', 'pip3'), '#!/bin/sh\necho image\n', executable=True)

    def _install(self):
        script = _function_source('install_python3_packages') \
            + '\ninstall_python3_packages "%s"\necho "RC=$?"\n' % self.install_path
        env = {'PATH': self.stub_bin + os.pathsep + os.environ.get('PATH', ''),
               'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/'}
        proc = subprocess.run([BASH, '-c', script], env=env, capture_output=True, text=True)
        return proc.stdout

    def _installs(self):
        out = self._install()
        self.assertIn('RC=0', out)

    def _path(self, *parts):
        return os.path.join(self.install_path, *parts)

    def _tree(self):
        # Every entry of the install path: a link with its target, a file with its content, a folder with nothing
        tree = {}
        for root, dirs, files in os.walk(self.install_path):
            for name in dirs + files:
                path = os.path.join(root, name)
                if os.path.islink(path):
                    tree[os.path.relpath(path, self.install_path)] = 'link:' + os.readlink(path)
                elif os.path.isfile(path):
                    with open(path) as f:
                        tree[os.path.relpath(path, self.install_path)] = f.read()
                else:
                    tree[os.path.relpath(path, self.install_path)] = None
        return tree

    def _fails_and_keeps_the_image(self):
        before = self._tree()
        out = self._install()
        self.assertEqual(before, self._tree())
        self.assertIn('RC=1', out)
        self.assertIn('[WARN] Unable to install the python3 distro', out)
        self.assertNotIn('Python3 distro is installed', out)

    def test_replaces_the_python_of_the_image_and_links_it(self):
        self._image_has()
        self._distro(with_openssl=False)
        self._installs()
        with open(self._path('python3.12', 'bin', 'python3.12')) as f:
            self.assertIn('distro', f.read())
        self.assertEqual(self._path('python3.12', 'bin', 'python3.12'), os.readlink(self._path('bin', 'python3')))
        self.assertEqual(self._path('python3.12', 'bin', 'pip3.12'), os.readlink(self._path('bin', 'pip3')))
        self.assertEqual(['bin', 'openssl', 'python3.12'], sorted(os.listdir(self.install_path)))

    def test_installs_where_the_image_has_no_python(self):
        self._distro(with_openssl=False)
        self._installs()
        self.assertTrue(os.access(os.readlink(self._path('bin', 'python3')), os.X_OK))
        self.assertTrue(os.access(os.readlink(self._path('bin', 'pip3')), os.X_OK))
        self.assertEqual(['bin', 'python3.12'], sorted(os.listdir(self.install_path)))

    def test_a_distro_without_openssl_keeps_the_openssl_of_the_image(self):
        # The distro has its OpenSSL built in, and /usr/local/openssl may be what the software of the image needs
        self._image_has()
        self._distro(with_openssl=False)
        self._installs()
        self.assertEqual(['from-image'], os.listdir(self._path('openssl', 'lib')))

    def test_a_distro_with_openssl_replaces_the_openssl_of_the_image(self):
        # The previous distro (python3.12.0.tar.gz) links its own /usr/local/openssl
        self._image_has()
        self._distro(with_openssl=True)
        self._installs()
        self.assertEqual(['from-distro'], os.listdir(self._path('openssl', 'lib')))
        self.assertEqual(['bin', 'openssl', 'python3.12'], sorted(os.listdir(self.install_path)))

    def test_a_distro_with_openssl_installs_it_where_the_image_has_none(self):
        self._distro(with_openssl=True)
        self._installs()
        self.assertEqual(['from-distro'], os.listdir(self._path('openssl', 'lib')))

    def test_a_failed_download_keeps_the_python_of_the_image(self):
        # E.g. a distribution URL that is not reachable from the node, or a 404
        self._image_has()
        self._stub_wget('exit 8\n')
        self._fails_and_keeps_the_image()

    def test_a_corrupt_tarball_keeps_the_python_of_the_image(self):
        # E.g. an error page of a proxy, saved with a success status
        self._image_has()
        self._stub_wget('echo "<html>Access denied</html>" > "$4"\n')
        self._fails_and_keeps_the_image()

    def test_a_tarball_without_the_python_keeps_the_python_and_the_openssl_of_the_image(self):
        self._image_has()
        self._tarball_of(['python3.12/lib/python3.12/os.py', 'openssl/lib/from-distro'])
        self._stub_wget('cp "%s" "$4"\n' % self.tarball)
        self._fails_and_keeps_the_image()

    def test_a_failed_download_leaves_no_links_where_the_image_has_no_python(self):
        self._stub_wget('exit 8\n')
        self._fails_and_keeps_the_image()
        self.assertEqual(['bin'], os.listdir(self.install_path))
        self.assertEqual([], os.listdir(self._path('bin')))


if __name__ == '__main__':
    unittest.main()
