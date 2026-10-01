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

# Covers the CP_PYTHON_VERSION/CP_PYTHON_PATH resolution added to cp-edge's "init" (issue #4531),
# mirroring cp-api-srv's init-api, and checks that sync-routes.py/maintenance.py are still importable
# source under both Python 2 and Python 3. There was no test runner in this package before - run with
# `python -m unittest test_python_resolution` (or under pytest).

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
INIT_SCRIPT = os.path.join(THIS_DIR, 'init')
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


def _run_resolution(cp_python_version, available_interpreters):
    bin_dir = tempfile.mkdtemp()
    try:
        for name in available_interpreters:
            _write_stub_bin(bin_dir, name)
        script = _extract_resolution_block() + '\necho "RESOLVED=$CP_PYTHON_PATH"\n'
        env = {'PATH': bin_dir, 'CP_PYTHON_VERSION': cp_python_version}
        proc = subprocess.Popen([BASH, '-c', script], env=env,
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        return proc.returncode, out.decode(), err.decode()
    finally:
        shutil.rmtree(bin_dir)


class CpPythonPathResolutionTest(unittest.TestCase):

    def test_defaults_to_python2_when_unset(self):
        bin_dir = tempfile.mkdtemp()
        try:
            python2_path = _write_stub_bin(bin_dir, 'python2')
            script = _extract_resolution_block() + '\necho "RESOLVED=$CP_PYTHON_PATH"\n'
            env = {'PATH': bin_dir}
            proc = subprocess.Popen([BASH, '-c', script], env=env,
                                     stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            out, _ = proc.communicate()
            self.assertEqual(0, proc.returncode)
            self.assertIn('RESOLVED=%s' % python2_path, out.decode())
        finally:
            shutil.rmtree(bin_dir)

    def test_version_2_resolves_to_python2(self):
        rc, out, _ = _run_resolution('2', ['python2', 'python3.12'])
        self.assertEqual(0, rc)
        self.assertIn('python2', out)
        self.assertNotIn('python3.12', out)

    def test_version_3_resolves_to_python3_12_over_python3(self):
        rc, out, _ = _run_resolution('3', ['python2', 'python3.12', 'python3'])
        self.assertEqual(0, rc)
        self.assertIn('python3.12', out)

    def test_version_3_falls_back_to_python3_when_3_12_missing(self):
        rc, out, _ = _run_resolution('3', ['python2', 'python3'])
        self.assertEqual(0, rc)
        self.assertIn('RESOLVED=', out)
        self.assertTrue(out.strip().endswith('/python3'))

    def test_version_2_falls_back_to_bare_python_when_python2_missing(self):
        rc, out, _ = _run_resolution('2', ['python'])
        self.assertEqual(0, rc)
        self.assertTrue(out.strip().endswith('/python'))

    def test_missing_interpreter_exits_nonzero(self):
        rc, out, _ = _run_resolution('3', ['python2'])
        self.assertNotEqual(0, rc)
        self.assertNotIn('RESOLVED=', out)


class CpEdgeScriptsCompileUnderBothInterpretersTest(unittest.TestCase):

    SCRIPTS = [
        os.path.join(THIS_DIR, 'sync-routes.py'),
        os.path.join(THIS_DIR, 'maintenance', 'scripts', 'maintenance.py'),
    ]

    def test_compiles_under_current_interpreter(self):
        for script in self.SCRIPTS:
            with tempfile.NamedTemporaryFile(suffix='.pyc') as cfile:
                py_compile.compile(script, cfile=cfile.name, doraise=True)

    def test_compiles_under_other_available_interpreters(self):
        candidates = ['python2', 'python3', 'python3.12']
        found_other = False
        for candidate in candidates:
            interpreter = _which(candidate)
            if not interpreter:
                continue
            if os.path.realpath(interpreter) == os.path.realpath(sys.executable):
                continue
            found_other = True
            for script in self.SCRIPTS:
                proc = subprocess.Popen([interpreter, '-m', 'py_compile', script],
                                         stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                out, err = proc.communicate()
                self.assertEqual(0, proc.returncode,
                                  '%s failed to compile %s: %s' % (interpreter, script, err))
        if not found_other:
            raise unittest.SkipTest('no other Python interpreter available on PATH to cross-check')


if __name__ == '__main__':
    unittest.main()
