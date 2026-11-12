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
# place of /usr/local. Needs bash, mktemp, and tar with gzip.
# Also covers what the run takes as its Python after that (CP_PYTHON_PATH, CP_PYTHON2_PATH, PATH, pip), where the
# distribution is downloaded from, and the pip mirror of the run.

import os
import shutil
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


# wget -q --no-check-certificate <url> -O <file>, or curl -s -k -f <url> -o <file>: records its arguments, and runs the
# body with $URL and $OUT
DOWNLOADER_STUB = '''#!/bin/sh
for arg in "$@"; do
    case "$prev" in -O|-o) OUT="$arg" ;; esac
    case "$arg" in http://*|https://*) URL="$arg" ;; esac
    prev="$arg"
done
echo "%s $*" >> "%s"
%s
'''
# What the install and the Python block call, besides the bash builtins and the stubs
TOOLS = ['rm', 'mktemp', 'tar', 'gzip', 'mv', 'ln', 'cp']


def _stub_downloader(stub_bin, name, body, log):
    _write(os.path.join(stub_bin, name), DOWNLOADER_STUB % (name, log, body), executable=True)


def _hermetic_path(tmp, *first):
    # Only the tools that the script needs: a python3.12 of the host is not in this PATH
    tools = os.path.join(tmp, 'tools')
    if not os.path.isdir(tools):
        os.makedirs(tools)
        for tool in TOOLS:
            os.symlink(shutil.which(tool), os.path.join(tools, tool))
    return os.pathsep.join(list(first) + [tools])


class LaunchPythonInstallTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.install_path = os.path.join(self.tmp, 'usr-local')
        os.makedirs(os.path.join(self.install_path, 'bin'))
        self.stub_bin = os.path.join(self.tmp, 'stub-bin')
        os.makedirs(self.stub_bin)
        self.tarball = os.path.join(self.tmp, 'python3.12.tar.gz')
        self.downloads = os.path.join(self.tmp, 'downloads')
        self._stub_curl('exit 22\n')

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
        self._stub_wget('cp "%s" "$OUT"\n' % self.tarball)

    def _stub_wget(self, body):
        _stub_downloader(self.stub_bin, 'wget', body, self.downloads)

    def _stub_curl(self, body):
        _stub_downloader(self.stub_bin, 'curl', body, self.downloads)

    def _downloads(self):
        with open(self.downloads) as f:
            return f.read().splitlines()

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
        env = {'PATH': _hermetic_path(self.tmp, self.stub_bin),
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
        self._stub_wget('echo "<html>Access denied</html>" > "$OUT"\n')
        self._fails_and_keeps_the_image()

    def test_a_tarball_without_the_python_keeps_the_python_and_the_openssl_of_the_image(self):
        self._image_has()
        self._tarball_of(['python3.12/lib/python3.12/os.py', 'openssl/lib/from-distro'])
        self._stub_wget('cp "%s" "$OUT"\n' % self.tarball)
        self._fails_and_keeps_the_image()

    def test_a_folder_left_by_a_killed_download_is_removed(self):
        _write(self._path('.cp-python3.Ab12Cd', 'python3.tgz'), 'partial download')
        self._image_has()
        self._distro(with_openssl=False)
        self._installs()
        self.assertEqual([], [name for name in os.listdir(self.install_path) if name.startswith('.cp-python3.')])

    def test_a_failed_move_leaves_the_links_of_the_image(self):
        self._image_has()
        self._distro(with_openssl=False)
        # mv fails for the new python3.12 of the distro, e.g. on a full disk
        _write(os.path.join(self.stub_bin, 'mv'),
               '#!/bin/sh\n'
               'case "$1" in */.cp-python3.*/python3.12) exit 1 ;; esac\n'
               'exec %s "$@"\n' % (shutil.which('mv') or '/bin/mv'), executable=True)
        out = self._install()
        self.assertIn('RC=1', out)
        self.assertIn('[WARN] Unable to move the python3 distro', out)
        self.assertNotIn('Python3 distro is installed', out)
        self.assertEqual('python3.11', os.readlink(self._path('bin', 'python3')))
        self.assertFalse(os.path.islink(self._path('bin', 'pip3')))
        self.assertEqual(['bin', 'openssl'], sorted(os.listdir(self.install_path)))

    def test_a_dangling_python_link_of_the_image_is_replaced(self):
        os.symlink(self._path('gone', 'python3.12'), self._path('python3.12'))
        self._distro(with_openssl=False)
        self._installs()
        self.assertFalse(os.path.islink(self._path('python3.12')))
        self.assertTrue(os.path.isfile(self._path('python3.12', 'bin', 'python3.12')))

    def test_no_temporary_folder_fails_without_changes(self):
        self.install_path = os.path.join(self.tmp, 'missing')
        out = self._install()
        self.assertIn('RC=1', out)
        self.assertIn('[WARN] Unable to create a temporary folder', out)
        self.assertFalse(os.path.exists(self.install_path))

    def test_the_distro_is_downloaded_from_the_global_distribution_url_without_a_certificate_check(self):
        # As the other downloads of launch.sh do: e.g. for a distribution URL with an internal CA
        self._distro(with_openssl=False)
        self._installs()
        downloads = self._downloads()
        self.assertEqual(1, len(downloads))
        self.assertTrue(downloads[0].startswith('wget -q --no-check-certificate '
                                                'https://dist.example.com/tools/python/3/python3.12.tar.gz -O '),
                        downloads)

    def test_curl_downloads_the_distro_if_wget_fails(self):
        # E.g. an image without wget
        self._distro(with_openssl=False)
        self._stub_wget('exit 127\n')
        self._stub_curl('cp "%s" "$OUT"\n' % self.tarball)
        self._installs()
        self.assertTrue(self._downloads()[1].startswith(
            'curl -s -k -f https://dist.example.com/tools/python/3/python3.12.tar.gz -o '), self._downloads())
        with open(self._path('python3.12', 'bin', 'python3.12')) as f:
            self.assertIn('distro', f.read())

    def test_a_failed_download_with_wget_and_curl_keeps_the_python_of_the_image(self):
        self._image_has()
        self._stub_wget('exit 8\n')
        self._fails_and_keeps_the_image()
        self.assertEqual(['wget', 'curl'], [call.split()[0] for call in self._downloads()])

    def test_a_failed_download_leaves_no_links_where_the_image_has_no_python(self):
        self._stub_wget('exit 8\n')
        self._fails_and_keeps_the_image()
        self.assertEqual(['bin'], os.listdir(self.install_path))
        self.assertEqual([], os.listdir(self._path('bin')))



def _launch_text():
    with open(LAUNCH_SH) as f:
        return f.read()


def _python_block_source():
    # From the install of the distribution to the pip check: the main flow of launch.sh that selects the Python
    text = _launch_text()
    start = text.index('\ninstall_python3_packages "/usr/local"\n') + 1
    end = text.index('\n', text.index('check_python_module_installed "pip --version"', start)) + 1
    return text[start:end]


# A python3.12 that records how it is called. "-m pip --version" fails if $STUB_NO_PIP is set
PYTHON_STUB = '''#!/bin/sh
echo "%s $*" >> "$STUB_CALLS"
if [ "$1 $2" = "-m pip" ] && [ -n "$STUB_NO_PIP" ]; then exit 1; fi
exit 0
'''


class LaunchPythonSelectionTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.usr_local = os.path.join(self.tmp, 'usr-local')
        os.makedirs(os.path.join(self.usr_local, 'bin'))
        self.stub_bin = os.path.join(self.tmp, 'stub-bin')
        os.makedirs(self.stub_bin)
        self.image_bin = os.path.join(self.tmp, 'image-bin')
        os.makedirs(self.image_bin)
        self.calls = os.path.join(self.tmp, 'calls')
        self.tarball = os.path.join(self.tmp, 'python3.12.tar.gz')
        dist = os.path.join(self.tmp, 'dist')
        _write(os.path.join(dist, 'python3.12', 'bin', 'python3.12'), PYTHON_STUB % 'distro', executable=True)
        _write(os.path.join(dist, 'python3.12', 'bin', 'pip3.12'), '#!/bin/sh\n', executable=True)
        with tarfile.open(self.tarball, 'w:gz') as tar:
            tar.add(os.path.join(dist, 'python3.12'), arcname='python3.12')

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def _download(self, succeeds):
        downloads = os.path.join(self.tmp, 'downloads')
        _stub_downloader(self.stub_bin, 'wget', 'cp "%s" "$OUT"\n' % self.tarball if succeeds else 'exit 8\n',
                         downloads)
        _stub_downloader(self.stub_bin, 'curl', 'exit 22\n', downloads)

    def _select(self, extra_env=None):
        script = _function_source('install_python3_packages') \
            + _function_source('check_python_module_installed') \
            + 'function exit_init { echo "EXIT_INIT=$1"; exit "$1"; }\n' \
            + _python_block_source().replace('/usr/local', self.usr_local) \
            + 'echo "CP_PYTHON_PATH=$CP_PYTHON_PATH"\n' \
            + 'echo "CP_PYTHON2_PATH=$CP_PYTHON2_PATH"\n' \
            + 'echo "PATH=$PATH"\n'
        env = {'PATH': _hermetic_path(self.tmp, self.stub_bin, self.image_bin),
               'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/',
               'STUB_CALLS': self.calls}
        env.update(extra_env or {})
        proc = subprocess.run([BASH, '-c', script], env=env, capture_output=True, text=True)
        values = dict(line.split('=', 1) for line in proc.stdout.splitlines() if '=' in line)
        return proc, values

    def _calls(self):
        if not os.path.exists(self.calls):
            return []
        with open(self.calls) as f:
            return f.read().splitlines()

    def test_the_installed_distribution_is_the_python_of_the_run(self):
        self._download(succeeds=True)
        proc, values = self._select()
        python = os.path.join(self.usr_local, 'python3.12', 'bin', 'python3.12')
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        self.assertEqual(python, values['CP_PYTHON_PATH'])
        self.assertIn('Python interpreter (CP_PYTHON_PATH): %s' % python, proc.stdout)

    def test_the_python2_name_is_kept_for_the_same_python(self):
        # User scripts that call $CP_PYTHON2_PATH run with the Python 3.12 of the run: there is no Python 2 any more
        self._download(succeeds=True)
        _, values = self._select()
        self.assertEqual(values['CP_PYTHON_PATH'], values['CP_PYTHON2_PATH'])

    def test_the_bin_folder_of_the_distribution_goes_first_in_path(self):
        # So python3 and pip3 are the platform's ones, even if the image has its own python3 earlier in PATH
        self._download(succeeds=True)
        _write(os.path.join(self.image_bin, 'python3'), '#!/bin/sh\n', executable=True)
        _, values = self._select()
        path = values['PATH'].split(os.pathsep)
        self.assertEqual(os.path.join(self.usr_local, 'python3.12', 'bin'), path[0])
        self.assertIn(self.image_bin, path[1:])

    def test_a_failed_download_takes_the_python_the_image_has_in_its_place(self):
        # E.g. a committed run: the distribution that the earlier run installed is still there
        self._download(succeeds=False)
        python = os.path.join(self.usr_local, 'python3.12', 'bin', 'python3.12')
        _write(python, PYTHON_STUB % 'image', executable=True)
        proc, values = self._select()
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn('[WARN] Unable to install the python3 distro', proc.stdout)
        self.assertEqual(python, values['CP_PYTHON_PATH'])

    def test_a_failed_download_takes_the_python3_12_of_the_image_from_path(self):
        self._download(succeeds=False)
        python = os.path.join(self.image_bin, 'python3.12')
        _write(python, PYTHON_STUB % 'image', executable=True)
        proc, values = self._select()
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        self.assertEqual(python, values['CP_PYTHON_PATH'])
        self.assertEqual(python, values['CP_PYTHON2_PATH'])

    def test_a_failed_download_without_a_python3_12_in_the_image_fails_the_run(self):
        self._download(succeeds=False)
        # A python3 of another version does not do: pipe-common requires Python 3.12
        _write(os.path.join(self.image_bin, 'python3'), '#!/bin/sh\n', executable=True)
        proc, values = self._select()
        self.assertEqual(1, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn('[ERROR] Python 3.12 environment not found, exiting.', proc.stdout)
        self.assertEqual('1', values['EXIT_INIT'])

    def test_pip_is_installed_with_ensurepip_if_the_python_has_none(self):
        self._download(succeeds=True)
        self._select({'STUB_NO_PIP': 'true'})
        self.assertEqual(['distro -m pip --version', 'distro -m ensurepip --upgrade'], self._calls())

    def test_pip_is_not_installed_if_the_python_has_it(self):
        self._download(succeeds=True)
        self._select()
        self.assertEqual(['distro -m pip --version'], self._calls())

    def test_no_python2_is_looked_for_or_installed(self):
        block = _python_block_source()
        for removed in ('python2', 'conda', 'get-pip', 'install_private_packages'):
            self.assertNotIn(removed, block)
        self.assertNotIn('install_private_packages', _launch_text())


class LaunchGlobalDistributionUrlTest(unittest.TestCase):
    # The Python 3.12 distribution and the pip mirror are taken from GLOBAL_DISTRIBUTION_URL, which the API passes to a
    # run. launch.sh defaults it to the public bucket before the first use

    def test_defaults_to_the_public_bucket_before_the_first_use(self):
        text = _launch_text()
        default = 'export GLOBAL_DISTRIBUTION_URL="${GLOBAL_DISTRIBUTION_URL:-"%s"}"' \
                  % 'https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/'
        self.assertIn(default, text)
        main_flow = text[text.index(default):]
        self.assertIn('\nconfigure_package_manager\n', main_flow)
        self.assertIn('\ninstall_python3_packages "/usr/local"\n', main_flow)

    def _downloaded_urls(self, env):
        tmp = tempfile.mkdtemp()
        try:
            stub_bin = os.path.join(tmp, 'stub-bin')
            downloads = os.path.join(tmp, 'downloads')
            for name in ('wget', 'curl'):
                _stub_downloader(stub_bin, name, 'echo "$URL" >> "%s.urls"\nexit 8\n' % downloads, downloads)
            os.makedirs(os.path.join(tmp, 'usr-local'))
            subprocess.run([BASH, '-c', _function_source('install_python3_packages')
                            + 'install_python3_packages "%s"\n' % os.path.join(tmp, 'usr-local')],
                           env=dict({'PATH': _hermetic_path(tmp, stub_bin)}, **env), capture_output=True, text=True)
            with open(downloads + '.urls') as f:
                return f.read().splitlines()
        finally:
            shutil.rmtree(tmp)

    def test_the_distribution_is_downloaded_from_the_global_distribution_url(self):
        # By wget, and by curl once more, as wget fails here
        self.assertEqual(['https://dist.example.com/tools/python/3/python3.12.tar.gz'] * 2,
                         self._downloaded_urls({'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/'}))

    def test_cp_python3_distro_url_sets_another_location(self):
        mirror = 'https://mirror.example.com/python3.12.0.tar.gz'
        self.assertEqual([mirror] * 2, self._downloaded_urls({'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/',
                                                              'CP_PYTHON3_DISTRO_URL': mirror}))


class LaunchPipMirrorTest(unittest.TestCase):
    # With CP_REPO_ENABLED, pip of the run installs from the Python 3 mirror of the platform (pypi3)

    def _run(self, env):
        script = _function_source('configure_package_manager_pip') + 'configure_package_manager_pip\n' \
            + 'echo "ARGS=$CP_PIP_EXTRA_ARGS"\n'
        return subprocess.run([BASH, '-c', script], env=dict({'PATH': os.environ.get('PATH', '')}, **env),
                              capture_output=True, text=True).stdout

    def _configure(self, env):
        out = self._run(env)
        return dict(line.split('=', 1) for line in out.splitlines() if line.startswith('ARGS='))['ARGS'].split()

    def test_the_python2_mirror_parameter_is_reported_as_ignored(self):
        python2_mirror = 'http://py2.example.com/tools/python/pypi/simple'
        out = self._run({'GLOBAL_DISTRIBUTION_URL': '', 'CP_REPO_PYPI_BASE_URL_DEFAULT': python2_mirror})
        self.assertIn('[WARN] CP_REPO_PYPI_BASE_URL_DEFAULT (%s) is ignored' % python2_mirror, out)
        out = self._run({'GLOBAL_DISTRIBUTION_URL': '', 'CP_REPO_PYPI_BASE_URL_DEFAULT': python2_mirror,
                         'CP_REPO_PYPI3_BASE_URL_DEFAULT': 'https://pypi.example.com/simple'})
        self.assertNotIn('[WARN]', out)
        self.assertNotIn('[WARN]', self._run({'GLOBAL_DISTRIBUTION_URL': ''}))

    def test_the_mirror_of_a_regional_bucket_is_its_website_endpoint(self):
        args = self._configure({'GLOBAL_DISTRIBUTION_URL': 'https://cp-dist.s3.eu-central-1.amazonaws.com/'})
        self.assertEqual(['--index-url',
                          'http://cp-dist.s3-website.eu-central-1.amazonaws.com/tools/python/pypi3/simple',
                          '--trusted-host', 'cp-dist.s3-website.eu-central-1.amazonaws.com'], args)

    def test_the_public_mirror_is_the_default(self):
        args = self._configure({'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/'})
        self.assertEqual(['--index-url', 'http://cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com/'
                                         'tools/python/pypi3/simple',
                          '--trusted-host', 'cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com'], args)

    def test_the_pypi3_parameters_set_another_mirror(self):
        args = self._configure({'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/',
                                'CP_REPO_PYPI3_BASE_URL_DEFAULT': 'https://pypi.example.com/simple',
                                'CP_PIP_EXTRA_ARGS': '--timeout 60'})
        self.assertEqual(['--timeout', '60', '--index-url', 'https://pypi.example.com/simple',
                          '--trusted-host', 'pypi.example.com'], args)

    def test_the_python2_mirror_parameters_have_no_effect(self):
        # They point to the mirror of the Python 2 packages
        args = self._configure({'GLOBAL_DISTRIBUTION_URL': 'https://dist.example.com/',
                                'CP_REPO_PYPI_BASE_URL_DEFAULT': 'http://py2.example.com/tools/python/pypi/simple',
                                'CP_REPO_PYPI_TRUSTED_HOST_DEFAULT': 'py2.example.com'})
        self.assertNotIn('py2.example.com', ' '.join(args))
        self.assertTrue(args[1].endswith('/tools/python/pypi3/simple'))

    def test_pipe_common_is_installed_with_the_python_of_the_run(self):
        text = _launch_text()
        install = text[text.index('if [ "$CP_PIPE_COMMON_ENABLED" == "true" ]; then'):]
        install = install[:install.index('\nfi\n')]
        self.assertIn('$CP_PYTHON_PATH -m pip install $CP_PIP_EXTRA_ARGS -I -q setuptools==81.0.0', install)
        self.assertIn('$CP_PYTHON_PATH -m pip install $CP_PIP_EXTRA_ARGS . -q -I', install)



class LaunchPipeCliBinaryTest(unittest.TestCase):
    # The pipe-el6 build (CentOS 6) is no longer published: a run that asks for it gets the pipe build

    def _downloaded(self, env):
        text = _launch_text()
        start = text.index('            CP_PIPELINE_CLI_BINARY_NAME="${CP_PIPELINE_CLI_BINARY_NAME:-pipe}"\n')
        end = text.index('\n', text.index('download_file "${DISTRIBUTION_URL}${CP_PIPELINE_CLI_BINARY_NAME}"', start))
        script = 'function download_file { echo "DOWNLOAD=$1"; }\n' + text[start:end + 1]
        return subprocess.run([BASH, '-c', script], env=dict({'PATH': os.environ.get('PATH', ''),
                                                             'DISTRIBUTION_URL': 'https://cp.example.com/'}, **env),
                              capture_output=True, text=True).stdout

    def test_pipe_is_installed_by_default(self):
        self.assertIn('DOWNLOAD=https://cp.example.com/pipe\n', self._downloaded({}))

    def test_pipe_el6_falls_back_to_pipe(self):
        out = self._downloaded({'CP_PIPELINE_CLI_BINARY_NAME': 'pipe-el6'})
        self.assertIn("[WARN] The pipe-el6 build of the 'pipe' CLI (CentOS 6) is no longer published", out)
        self.assertIn('DOWNLOAD=https://cp.example.com/pipe\n', out)

    def test_another_binary_name_is_kept(self):
        out = self._downloaded({'CP_PIPELINE_CLI_BINARY_NAME': 'pipe-custom'})
        self.assertIn('DOWNLOAD=https://cp.example.com/pipe-custom\n', out)
        self.assertNotIn('[WARN]', out)


if __name__ == '__main__':
    unittest.main()
