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

# Covers the CP_PYTHON_PATH resolution of cp-edge's "init": the Python 3.12 of CP_PYTHON3_HOME, or an explicit
# CP_PYTHON_PATH, and nothing else. Checks that sync-routes.py compiles under the running interpreter. Run with
# `python3 -m unittest test_python_resolution` (or under pytest) from inside deploy/docker/cp-edge/tests.

import glob
import os
import py_compile
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest

def _which(name):
    for directory in os.environ.get('PATH', '').split(os.pathsep):
        candidate = os.path.join(directory, name)
        if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
    return None


THIS_DIR = os.path.dirname(os.path.abspath(__file__))
CP_EDGE_DIR = os.path.dirname(THIS_DIR)
INIT_SCRIPT = os.path.join(CP_EDGE_DIR, 'init')
BASH = _which('bash') or '/bin/bash'

START_MARKER = '# --- Python interpreter selection ---'
END_MARKER = '# Persist environment for a cron job'


def _extract_resolution_block():
    with open(INIT_SCRIPT) as f:
        lines = f.readlines()
    try:
        start = next(i for i, line in enumerate(lines) if line.strip() == START_MARKER)
    except StopIteration:
        raise AssertionError('start marker %r not found in %s - did it move or get reworded?'
                              % (START_MARKER, INIT_SCRIPT))
    try:
        end = next(i for i, line in enumerate(lines) if line.strip() == END_MARKER)
    except StopIteration:
        raise AssertionError('end marker %r not found in %s - did it move or get reworded?'
                              % (END_MARKER, INIT_SCRIPT))
    return ''.join(lines[start:end])


def _write_stub_bin(bin_dir, name):
    path = os.path.join(bin_dir, name)
    with open(path, 'w') as f:
        f.write('#!/bin/sh\necho stub:%s\n' % name)
    st = os.stat(path)
    os.chmod(path, st.st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)
    return path


def _run_resolution(available_interpreters, python3_home=None, cp_python_path=None):
    bin_dir = tempfile.mkdtemp()
    try:
        for name in available_interpreters:
            _write_stub_bin(bin_dir, name)
        script = _extract_resolution_block() + '\necho "RESOLVED=$CP_PYTHON_PATH"\n'
        env = {'PATH': bin_dir}
        if python3_home is not None:
            env['CP_PYTHON3_HOME'] = python3_home
        if cp_python_path is not None:
            env['CP_PYTHON_PATH'] = cp_python_path
        proc = subprocess.Popen([BASH, '-c', script], env=env,
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        return proc.returncode, out.decode(), err.decode()
    finally:
        shutil.rmtree(bin_dir)


def _make_python3_home(bin_dir):
    home_dir = os.path.join(bin_dir, 'python3-home')
    home_bin_dir = os.path.join(home_dir, 'bin')
    os.makedirs(home_bin_dir)
    _write_stub_bin(home_bin_dir, 'python3.12')
    return home_dir


class CpPythonPathResolutionTest(unittest.TestCase):

    def test_resolves_to_cp_python3_home_over_bare_python3(self):
        tmp = tempfile.mkdtemp()
        try:
            python3_home = _make_python3_home(tmp)
            rc, out, _ = _run_resolution(['python3'], python3_home=python3_home)
            self.assertEqual(0, rc)
            self.assertIn('RESOLVED=%s' % os.path.join(python3_home, 'bin', 'python3.12'), out)
        finally:
            shutil.rmtree(tmp)

    def test_ignores_distro_python3_12_shadowing_on_path(self):
        # The distro can put its own bare python3.12 (no pip, none of the image's packages) on PATH.
        # Resolution must still use CP_PYTHON3_HOME, never a PATH-found "python3.12".
        tmp = tempfile.mkdtemp()
        try:
            python3_home = _make_python3_home(tmp)
            rc, out, _ = _run_resolution(['python3.12', 'python3'], python3_home=python3_home)
            self.assertEqual(0, rc)
            self.assertIn('RESOLVED=%s' % os.path.join(python3_home, 'bin', 'python3.12'), out)
        finally:
            shutil.rmtree(tmp)

    def test_keeps_an_explicit_cp_python_path(self):
        tmp = tempfile.mkdtemp()
        try:
            explicit = _write_stub_bin(tmp, 'custom-python3')
            rc, out, _ = _run_resolution([], python3_home=os.path.join(tmp, 'no-such-home'), cp_python_path=explicit)
            self.assertEqual(0, rc)
            self.assertIn('RESOLVED=%s' % explicit, out)
        finally:
            shutil.rmtree(tmp)

    def test_missing_python3_home_exits_nonzero_without_falling_back(self):
        tmp = tempfile.mkdtemp()
        try:
            missing_home = os.path.join(tmp, 'no-such-python3-home')
            rc, out, _ = _run_resolution(['python2', 'python', 'python3'], python3_home=missing_home)
            self.assertNotEqual(0, rc)
            self.assertNotIn('RESOLVED=', out)
        finally:
            shutil.rmtree(tmp)


class CpEdgeScriptsCompileTest(unittest.TestCase):

    SCRIPTS = [
        os.path.join(CP_EDGE_DIR, 'sync-routes.py'),
    ]

    def test_compiles_under_current_interpreter(self):
        for script in self.SCRIPTS:
            with tempfile.NamedTemporaryFile(suffix='.pyc') as cfile:
                py_compile.compile(script, cfile=cfile.name, doraise=True)


if __name__ == '__main__':
    unittest.main()
