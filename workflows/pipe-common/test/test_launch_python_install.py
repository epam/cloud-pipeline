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

# Covers the Python 3.12 installation of scripts/pipeline-launch/launch.sh: what it replaces in the image. Runs the
# install_python3_packages function of launch.sh with bash, a stub "wget" first on PATH, and its download folder (/tmp)
# replaced by a temporary folder. Needs bash, tar and grep.

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
    source = text[start:end]
    assert 'local _tmp_install_dir="/tmp/"' in source
    return source


def _write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(content)


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

    def _distro(self, with_openssl):
        dist = os.path.join(self.tmp, 'dist')
        for name in ('python3.12', 'pip3.12'):
            _write(os.path.join(dist, 'python3.12', 'bin', name), '#!/bin/sh\necho distro\n')
        if with_openssl:
            _write(os.path.join(dist, 'openssl', 'lib', 'from-distro'), '')
        with tarfile.open(self.tarball, 'w:gz') as tar:
            for name in sorted(os.listdir(dist)):
                tar.add(os.path.join(dist, name), arcname=name)
        # wget -q <url> -O <file>
        _write(os.path.join(self.stub_bin, 'wget'), '#!/bin/sh\ncp "%s" "$4"\n' % self.tarball)
        os.chmod(os.path.join(self.stub_bin, 'wget'), stat.S_IRWXU)

    def _image_has(self):
        _write(os.path.join(self.install_path, 'python3.12', 'bin', 'python3.12'), '#!/bin/sh\necho image\n')
        _write(os.path.join(self.install_path, 'openssl', 'lib', 'from-image'), '')

    def _install(self):
        script = _function_source('install_python3_packages').replace('"/tmp/"', '"%s/"' % self.tmp) \
            + '\ninstall_python3_packages "%s"\necho "RC=$?"\n' % self.install_path
        env = {'PATH': self.stub_bin + os.pathsep + os.environ.get('PATH', ''),
               'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/'}
        proc = subprocess.run([BASH, '-c', script], env=env, capture_output=True, text=True)
        self.assertIn('RC=0', proc.stdout, proc.stderr)

    def _path(self, *parts):
        return os.path.join(self.install_path, *parts)

    def test_replaces_the_python_of_the_image_and_links_it(self):
        self._image_has()
        self._distro(with_openssl=False)
        self._install()
        with open(self._path('python3.12', 'bin', 'python3.12')) as f:
            self.assertIn('distro', f.read())
        self.assertEqual(self._path('python3.12', 'bin', 'python3.12'), os.readlink(self._path('bin', 'python3')))
        self.assertEqual(self._path('python3.12', 'bin', 'pip3.12'), os.readlink(self._path('bin', 'pip3')))

    def test_a_distro_without_openssl_keeps_the_openssl_of_the_image(self):
        # The distro has its OpenSSL built in, and /usr/local/openssl may be what the software of the image needs
        self._image_has()
        self._distro(with_openssl=False)
        self._install()
        self.assertEqual(['from-image'], os.listdir(self._path('openssl', 'lib')))

    def test_a_distro_with_openssl_replaces_the_openssl_of_the_image(self):
        # The previous distro (python3.12.0.tar.gz) links its own /usr/local/openssl
        self._image_has()
        self._distro(with_openssl=True)
        self._install()
        self.assertEqual(['from-distro'], os.listdir(self._path('openssl', 'lib')))

    def test_a_distro_with_openssl_installs_it_where_the_image_has_none(self):
        self._distro(with_openssl=True)
        self._install()
        self.assertEqual(['from-distro'], os.listdir(self._path('openssl', 'lib')))


if __name__ == '__main__':
    unittest.main()
