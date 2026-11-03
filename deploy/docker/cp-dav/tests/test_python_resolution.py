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

# Covers the CP_PYTHON_PATH resolution of cp-dav's "init": the Python 3.12 of CP_PYTHON3_HOME, or an explicit
# CP_PYTHON_PATH, and nothing else; and that the pipe-common install for the NFS mount watcher runs with it, from the
# pypi3 mirror. Run with `python3 -m unittest test_python_resolution` from inside deploy/docker/cp-dav/tests.

import os
import shutil
import stat
import subprocess
import tempfile
import unittest

THIS_DIR = os.path.dirname(os.path.abspath(__file__))
CP_DAV_DIR = os.path.dirname(THIS_DIR)
INIT_SCRIPT = os.path.join(CP_DAV_DIR, 'init')
START_MARKER = '# --- Python interpreter selection ---'
END_MARKER = '# pipe-common is installed from the pypi3 mirror, as in a job container'
BASH = shutil.which('bash') or '/bin/bash'


def _read_init():
    with open(INIT_SCRIPT) as f:
        return f.read()


def _extract_resolution_block():
    lines = _read_init().splitlines(True)
    start = next(i for i, line in enumerate(lines) if line.strip() == START_MARKER)
    end = next(i for i, line in enumerate(lines) if line.strip() == END_MARKER)
    return ''.join(lines[start:end])


def _write_stub_bin(bin_dir, name):
    path = os.path.join(bin_dir, name)
    with open(path, 'w') as f:
        f.write('#!/bin/sh\necho stub:%s\n' % name)
    os.chmod(path, os.stat(path).st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)
    return path


def _run_resolution(available_interpreters, python3_home, cp_python_path=None):
    bin_dir = tempfile.mkdtemp()
    try:
        for name in available_interpreters:
            _write_stub_bin(bin_dir, name)
        env = {'PATH': bin_dir, 'CP_PYTHON3_HOME': python3_home}
        if cp_python_path is not None:
            env['CP_PYTHON_PATH'] = cp_python_path
        proc = subprocess.Popen([BASH, '-c', _extract_resolution_block() + '\necho "RESOLVED=$CP_PYTHON_PATH"\n'],
                                env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        return proc.returncode, out.decode(), err.decode()
    finally:
        shutil.rmtree(bin_dir)


def _make_python3_home(root):
    home_bin_dir = os.path.join(root, 'python3-home', 'bin')
    os.makedirs(home_bin_dir)
    _write_stub_bin(home_bin_dir, 'python3.12')
    return os.path.dirname(home_bin_dir)


class CpPythonPathResolutionTest(unittest.TestCase):

    def test_resolves_to_cp_python3_home_over_python_on_path(self):
        tmp = tempfile.mkdtemp()
        try:
            home = _make_python3_home(tmp)
            rc, out, _ = _run_resolution(['python3.12', 'python3', 'python'], home)
            self.assertEqual(0, rc)
            self.assertIn('RESOLVED=%s' % os.path.join(home, 'bin', 'python3.12'), out)
        finally:
            shutil.rmtree(tmp)

    def test_keeps_an_explicit_cp_python_path(self):
        tmp = tempfile.mkdtemp()
        try:
            explicit = _write_stub_bin(tmp, 'custom-python3')
            rc, out, _ = _run_resolution([], os.path.join(tmp, 'no-such-home'), cp_python_path=explicit)
            self.assertEqual(0, rc)
            self.assertIn('RESOLVED=%s' % explicit, out)
        finally:
            shutil.rmtree(tmp)

    def test_missing_python3_home_exits_nonzero_without_falling_back(self):
        tmp = tempfile.mkdtemp()
        try:
            rc, out, _ = _run_resolution(['python2', 'python', 'python3'], os.path.join(tmp, 'no-such-home'))
            self.assertNotEqual(0, rc)
            self.assertNotIn('RESOLVED=', out)
        finally:
            shutil.rmtree(tmp)


class PipeCommonInstallTest(unittest.TestCase):

    def test_installs_pipe_common_with_the_resolved_python_from_pypi3(self):
        init = _read_init()
        self.assertIn('tools/python/pypi3/simple', init)
        self.assertIn('"$CP_PYTHON_PATH" -m pip install $CP_PIP_PACKAGE_INDEX_ARGS -I -q pipe-common.tar.gz', init)
        self.assertNotIn('python2', init)


class ImageLibrariesTest(unittest.TestCase):

    def test_installs_libffi_so_6_for_the_ctypes_of_python_3_12(self):
        # The NFS mount watcher's "watchdog" needs ctypes, which links libffi.so.6: Rocky Linux 9 has only
        # libffi.so.8 of its own
        with open(os.path.join(CP_DAV_DIR, 'Dockerfile')) as f:
            dockerfile = f.read()
        self.assertIn('dnf install -y libffi3.1', dockerfile)
        self.assertLess(dockerfile.index('dnf install -y libffi3.1'), dockerfile.index('dnf remove -y epel-release'))


if __name__ == '__main__':
    unittest.main()
