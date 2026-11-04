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

# Covers the Python 3.12 installation of scripts/pipeline-launch/launch.sh: where the distribution is downloaded from,
# what it replaces in the image, and what a failed download leaves behind (no temporary folder, and the Python the
# image already has). Runs the install_python3_packages function of launch.sh with bash and a stub "wget" first on
# PATH. Needs bash and tar.

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
DEFAULT_GLOBAL_DISTRIBUTION_URL = 'https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/'


def _read_launch():
    with open(LAUNCH_SH) as f:
        return f.read()


def _function_source(text, name):
    start = text.index('function %s {' % name)
    end = text.index('\n}\n', start) + 3
    return text[start:end]


class LaunchPythonInstallTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.install_path = os.path.join(self.tmp, 'usr-local')
        os.makedirs(os.path.join(self.install_path, 'bin'))
        self.stub_bin = os.path.join(self.tmp, 'stub-bin')
        os.makedirs(self.stub_bin)
        self.wget_log = os.path.join(self.tmp, 'wget.log')
        self.tarball = os.path.join(self.tmp, 'python3.12.tar.gz')
        python_bin = os.path.join(self.tmp, 'dist', 'python3.12', 'bin')
        os.makedirs(python_bin)
        for name in ('python3.12', 'pip3.12'):
            with open(os.path.join(python_bin, name), 'w') as f:
                f.write('#!/bin/sh\n')
            os.chmod(os.path.join(python_bin, name), 0o755)
        with tarfile.open(self.tarball, 'w:gz') as tar:
            tar.add(os.path.join(self.tmp, 'dist', 'python3.12'), arcname='python3.12')

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def _add_openssl_to_the_distro(self):
        # The previous distro (python3.12.0.tar.gz) links its own /usr/local/openssl
        os.makedirs(os.path.join(self.tmp, 'dist', 'openssl'))
        open(os.path.join(self.tmp, 'dist', 'openssl', 'from-distro'), 'w').close()
        with tarfile.open(self.tarball, 'w:gz') as tar:
            for name in ('python3.12', 'openssl'):
                tar.add(os.path.join(self.tmp, 'dist', name), arcname=name)

    def _stub_wget(self, succeed):
        # wget -q <url> -O <file>
        body = 'echo "$2" >> "%s"\n' % self.wget_log
        body += 'cp "%s" "$4"\n' % self.tarball if succeed else 'exit 4\n'
        path = os.path.join(self.stub_bin, 'wget')
        with open(path, 'w') as f:
            f.write('#!/bin/sh\n' + body)
        os.chmod(path, os.stat(path).st_mode | stat.S_IEXEC)

    def _install(self, env=None):
        script = _function_source(_read_launch(), 'install_python3_packages') \
            + '\ninstall_python3_packages "%s"\necho "RC=$?"\n' % self.install_path
        run_env = {'PATH': self.stub_bin + os.pathsep + os.environ.get('PATH', '')}
        run_env.update(env or {})
        proc = subprocess.run([BASH, '-c', script], env=run_env, capture_output=True, text=True)
        return proc.stdout

    def _downloaded_urls(self):
        with open(self.wget_log) as f:
            return f.read().split()

    def test_installs_from_the_global_distribution_url(self):
        self._stub_wget(succeed=True)
        out = self._install({'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/'})
        self.assertIn('RC=0', out)
        self.assertEqual(['https://dist.example.com/tools/python/3/python3.12.tar.gz'], self._downloaded_urls())
        self.assertEqual(os.path.join(self.install_path, 'python3.12', 'bin', 'python3.12'),
                         os.readlink(os.path.join(self.install_path, 'bin', 'python3')))

    def _existing_install(self):
        python_bin = os.path.join(self.install_path, 'python3.12', 'bin')
        os.makedirs(python_bin)
        with open(os.path.join(python_bin, 'python3.12'), 'w') as f:
            f.write('#!/bin/sh\necho existing\n')
        os.makedirs(os.path.join(self.install_path, 'openssl'))
        open(os.path.join(self.install_path, 'openssl', 'from-image'), 'w').close()
        os.symlink(os.path.join(python_bin, 'python3.12'), os.path.join(self.install_path, 'bin', 'python3'))

    def test_a_failed_download_keeps_the_python_the_image_has(self):
        # E.g. the image of a committed run: its /usr/local/python3.12 keeps working without the distribution
        self._existing_install()
        self._stub_wget(succeed=False)
        out = self._install({'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/'})
        self.assertIn('RC=1', out)
        with open(os.path.join(self.install_path, 'python3.12', 'bin', 'python3.12')) as f:
            self.assertIn('existing', f.read())
        self.assertTrue(os.path.isdir(os.path.join(self.install_path, 'openssl')))
        self.assertTrue(os.path.exists(os.path.join(self.install_path, 'bin', 'python3')))
        self.assertEqual(['bin', 'openssl', 'python3.12'], sorted(os.listdir(self.install_path)))

    def test_a_download_replaces_the_python_the_image_has(self):
        self._existing_install()
        self._stub_wget(succeed=True)
        out = self._install({'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/'})
        self.assertIn('RC=0', out)
        with open(os.path.join(self.install_path, 'python3.12', 'bin', 'python3.12')) as f:
            self.assertNotIn('existing', f.read())
        self.assertEqual(['bin', 'openssl', 'python3.12'], sorted(os.listdir(self.install_path)))

    def test_a_download_keeps_the_openssl_of_the_image(self):
        # The distro has its OpenSSL built in, and /usr/local/openssl may be what the software of the image needs
        self._existing_install()
        self._stub_wget(succeed=True)
        out = self._install({'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/'})
        self.assertIn('RC=0', out)
        self.assertEqual(['from-image'], os.listdir(os.path.join(self.install_path, 'openssl')))

    def test_a_distro_with_openssl_replaces_the_openssl_of_the_image(self):
        self._existing_install()
        self._add_openssl_to_the_distro()
        self._stub_wget(succeed=True)
        out = self._install({'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/'})
        self.assertIn('RC=0', out)
        self.assertEqual(['from-distro'], os.listdir(os.path.join(self.install_path, 'openssl')))
        self.assertEqual(['bin', 'openssl', 'python3.12'], sorted(os.listdir(self.install_path)))

    def test_a_failed_download_reports_an_error_and_leaves_no_links(self):
        self._stub_wget(succeed=False)
        out = self._install({'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/'})
        self.assertIn('RC=1', out)
        self.assertIn('[WARN] Unable to install the python3 distro', out)
        self.assertFalse(os.path.lexists(os.path.join(self.install_path, 'bin', 'python3')))
        self.assertFalse(os.path.lexists(os.path.join(self.install_path, 'bin', 'pip3')))


class LaunchGlobalDistributionUrlTest(unittest.TestCase):
    # The API of this version does not pass GLOBAL_DISTRIBUTION_URL to a run: launch.sh defaults it

    def test_defaults_to_the_public_bucket_before_the_first_use(self):
        text = _read_launch()
        default = 'export GLOBAL_DISTRIBUTION_URL="${GLOBAL_DISTRIBUTION_URL:-"%s"}"' % DEFAULT_GLOBAL_DISTRIBUTION_URL
        self.assertIn(default, text)
        main_flow = text[text.index(default):]
        self.assertIn('\nconfigure_package_manager\n', main_flow)
        self.assertIn('\ninstall_python3_packages "/usr/local"\n', main_flow)


if __name__ == '__main__':
    unittest.main()
