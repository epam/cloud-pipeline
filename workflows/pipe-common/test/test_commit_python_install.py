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
# Also covers the pip mirror of the commit and pause scripts, and that they run pipe-common with that Python.

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
        self.downloads = os.path.join(self.tmp, 'downloads')
        # wget -q --no-check-certificate <url> -O <file>
        _write(os.path.join(self.stub_bin, 'wget'),
               '#!/bin/sh\necho "wget $3" >> "%s"\n'
               'while [ $# -gt 0 ]; do [ "$1" = "-O" ] && cp "%s" "$2"; shift; done\n' % (self.downloads, self.tarball))

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

    def _run_install(self, env=None):
        script = _function_source('install_python').replace('/usr/local', self.usr_local) \
            + '\ninstall_python\necho "RC=$?"\necho "CP_PYTHON_PATH=$CP_PYTHON_PATH"\n'
        run_env = {'PATH': self.stub_bin + os.pathsep + os.environ.get('PATH', '')}
        run_env.update(env or {})
        return subprocess.run([BASH, '-c', script], env=run_env, capture_output=True, text=True)

    def _install(self, env=None):
        proc = self._run_install(env)
        self.assertIn('RC=0', proc.stdout, proc.stdout + proc.stderr)
        return proc

    def _downloads(self):
        if not os.path.exists(self.downloads):
            return []
        with open(self.downloads) as f:
            return f.read().splitlines()

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

    def test_the_python_of_the_host_is_used_without_a_download(self):
        # E.g. an AWS node image, or a host where an earlier commit installed it
        _write(os.path.join(self.usr_local, 'python3.12', 'bin', 'python3.12'), '#!/bin/sh\n')
        proc = self._install()
        self.assertEqual([], self._downloads())
        self.assertIn('CP_PYTHON_PATH=%s' % os.path.join(self.usr_local, 'python3.12', 'bin', 'python3.12'),
                      proc.stdout)

    def test_the_distro_is_downloaded_from_the_global_distribution_url(self):
        # The API passes it to the commit and pause scripts
        self._distro(with_openssl=False)
        self._install({'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/'})
        self.assertEqual(['wget https://dist.example.com/tools/python/3/python3.12.tar.gz'], self._downloads())

    def test_the_distro_is_downloaded_from_the_public_bucket_by_default(self):
        self._distro(with_openssl=False)
        self._install({'GLOBAL_DISTRIBUTION_URL': ''})
        self.assertEqual(['wget https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/'
                          'tools/python/3/python3.12.tar.gz'], self._downloads())

    def test_curl_downloads_the_distro_if_wget_fails(self):
        self._distro(with_openssl=False)
        _write(os.path.join(self.stub_bin, 'wget'), '#!/bin/sh\nexit 1\n')
        # curl -s -k -f <url> -o <file>
        _write(os.path.join(self.stub_bin, 'curl'), '#!/bin/sh\ncp "%s" "$6"\n' % self.tarball)
        self._install()
        self.assertTrue(os.access(os.path.join(self.usr_local, 'python3.12', 'bin', 'python3.12'), os.X_OK))

    def test_a_failed_download_stops_the_commit_and_leaves_nothing(self):
        _write(os.path.join(self.stub_bin, 'wget'), '#!/bin/sh\nexit 8\n')
        _write(os.path.join(self.stub_bin, 'curl'), '#!/bin/sh\nexit 22\n')
        proc = self._run_install()
        self.assertEqual(1, proc.returncode)
        self.assertIn('[ERROR] Python 3.12 is not found and cannot be installed. Exiting', proc.stdout)
        self.assertEqual([], os.listdir(self.usr_local))

    def test_a_tarball_without_the_python_stops_the_commit_and_keeps_the_openssl_of_the_host(self):
        self._host_has_openssl()
        dist = os.path.join(self.tmp, 'dist')
        _write(os.path.join(dist, 'openssl', 'from-distro'))
        with tarfile.open(self.tarball, 'w:gz') as tar:
            tar.add(os.path.join(dist, 'openssl'), arcname='openssl')
        proc = self._run_install()
        self.assertEqual(1, proc.returncode)
        self.assertEqual(['openssl'], os.listdir(self.usr_local))
        self.assertEqual(['from-host'], os.listdir(os.path.join(self.usr_local, 'openssl')))


def _script(name):
    with open(os.path.join(os.path.dirname(COMMON_COMMIT_INITIALIZATION_SH), name)) as f:
        return f.read()


class CommitPythonUsageTest(unittest.TestCase):

    def test_commit_and_pause_install_the_python_before_pip_and_pipe_common(self):
        for name in ('commit_run_starter.sh', 'pause_run.sh'):
            text = _script(name)
            self.assertIn('\ninstall_python\ninstall_pip\n', text, name)
            self.assertLess(text.index('\ninstall_python\n'), text.index('\ninstall_pipeline_code\n'), name)
            self.assertNotIn('CP_PYTHON2_PATH', text, name)

    def test_pip_and_pipe_common_run_with_the_python_of_the_host(self):
        text = _script('common_commit_initialization.sh')
        install_pip = _function_source('install_pip')
        self.assertIn('$CP_PYTHON_PATH -m pip --version', install_pip)
        self.assertIn('$CP_PYTHON_PATH -m ensurepip --upgrade', install_pip)
        self.assertNotIn('get-pip', text)
        install = _function_source('install_pipeline_code')
        self.assertIn('$CP_PYTHON_PATH -m pip install $CP_PIP_EXTRA_ARGS -I -q setuptools==81.0.0', install)
        self.assertIn('$CP_PYTHON_PATH -m pip install $CP_PIP_EXTRA_ARGS . -q -I', install)


class CommitPipMirrorTest(unittest.TestCase):
    # The commit and pause scripts always install pipe-common from the Python 3 mirror of the platform (pypi3)

    def _configure(self, env):
        script = _function_source('configure_pip') + 'configure_pip\necho "ARGS=$CP_PIP_EXTRA_ARGS"\n'
        out = subprocess.run([BASH, '-c', script], env=dict({'PATH': os.environ.get('PATH', '')}, **env),
                             capture_output=True, text=True).stdout
        return dict(line.split('=', 1) for line in out.splitlines() if line.startswith('ARGS='))['ARGS'].split()

    def test_the_mirror_of_a_regional_bucket_is_its_website_endpoint(self):
        args = self._configure({'GLOBAL_DISTRIBUTION_URL': 'https://cp-dist.s3.eu-central-1.amazonaws.com/'})
        self.assertEqual(['--index-url', 'http://cp-dist.s3-website.eu-central-1.amazonaws.com/tools/python/pypi3/simple',
                          '--trusted-host', 'cp-dist.s3-website.eu-central-1.amazonaws.com'], args)

    def test_the_public_mirror_is_the_default(self):
        args = self._configure({'GLOBAL_DISTRIBUTION_URL': ''})
        self.assertEqual(['--index-url', 'http://cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com/'
                                         'tools/python/pypi3/simple',
                          '--trusted-host', 'cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com'], args)

    def test_the_python2_mirror_parameters_have_no_effect(self):
        args = self._configure({'GLOBAL_DISTRIBUTION_URL': '',
                                'CP_REPO_PYPI_BASE_URL_DEFAULT': 'http://py2.example.com/tools/python/pypi/simple'})
        self.assertNotIn('py2.example.com', ' '.join(args))


if __name__ == '__main__':
    unittest.main()
