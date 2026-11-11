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

# Covers the Python 3.12 of cp-edge:
#   - the CP_PYTHON_PATH resolution of "init": the Python 3.12 of CP_PYTHON3_HOME, or an explicit CP_PYTHON_PATH,
#     and nothing else
#   - sync-routes.sh and maintenance.sh run their scripts with the CP_PYTHON_PATH that "init" persists in env.sh
#   - sync-routes.py and maintenance.py compile under the running interpreter with no warnings, and keep no Python 2
#     idioms that fail on Python 3
# Run with `python3 -m unittest discover -s tests` (or under pytest) from inside deploy/docker/cp-edge.

import os
import py_compile
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import types
import unittest
import warnings


def _which(name):
    for directory in os.environ.get('PATH', '').split(os.pathsep):
        candidate = os.path.join(directory, name)
        if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
    return None


THIS_DIR = os.path.dirname(os.path.abspath(__file__))
CP_EDGE_DIR = os.path.dirname(THIS_DIR)
INIT_SCRIPT = os.path.join(CP_EDGE_DIR, 'init')
SYNC_ROUTES_PY = os.path.join(CP_EDGE_DIR, 'sync-routes.py')
SYNC_ROUTES_SH = os.path.join(CP_EDGE_DIR, 'sync-routes.sh')
MAINTENANCE_PY = os.path.join(CP_EDGE_DIR, 'maintenance', 'scripts', 'maintenance.py')
MAINTENANCE_SH = os.path.join(CP_EDGE_DIR, 'maintenance', 'scripts', 'maintenance.sh')
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


def _write_stub_bin(bin_dir, name, body=None):
    path = os.path.join(bin_dir, name)
    with open(path, 'w') as f:
        f.write('#!/bin/sh\n' + (body or 'echo stub:%s\n' % name))
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


class CpEdgeCronScriptsTest(unittest.TestCase):
    # sync-routes.sh and maintenance.sh run from a loop, with the environment that "init" writes to env.sh

    def setUp(self):
        self.tmp = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def _run(self, script_path, env_file_path, script_dir, script_name, env_lines):
        record = os.path.join(self.tmp, 'invocations.txt')
        python = _write_stub_bin(self.tmp, 'python3.12', 'echo "$@" >> "%s"\n' % record)
        # A "python" on PATH that is not the one of CP_PYTHON_PATH: the scripts must not run it
        bin_dir = os.path.join(self.tmp, 'bin')
        os.makedirs(bin_dir)
        _write_stub_bin(bin_dir, 'python', 'echo "python $@" >> "%s"\n' % record)
        env_file = os.path.join(self.tmp, 'env.sh')
        with open(env_file, 'w') as f:
            f.write(''.join(line % {'python': python} + '\n' for line in env_lines))
        log_dir = os.path.join(self.tmp, 'log')
        os.makedirs(log_dir)
        with open(script_path) as f:
            script = f.read() \
                .replace(env_file_path, env_file) \
                .replace('/var/log/', log_dir + '/') \
                .replace(script_dir, os.path.join(self.tmp, 'scripts'))
        proc = subprocess.Popen([BASH, '-c', script], env={'PATH': bin_dir + os.pathsep + '/usr/bin:/bin'},
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        self.assertEqual(0, proc.returncode, 'out=%r err=%r' % (out, err))
        with open(record) as f:
            return f.read().splitlines()

    def test_sync_routes_runs_with_cp_python_path(self):
        invocations = self._run(SYNC_ROUTES_SH, '/etc/sync-routes/env.sh', '/etc/sync-routes',
                                'sync-routes.py', ['CP_PYTHON_PATH=%(python)s'])
        self.assertEqual([os.path.join(self.tmp, 'scripts', 'sync-routes.py')], invocations)

    def test_maintenance_runs_with_cp_python_path(self):
        invocations = self._run(MAINTENANCE_SH, '/etc/maintenance/env.sh', '/etc/maintenance',
                                'maintenance.py', ['CP_PYTHON_PATH=%(python)s'])
        self.assertEqual([os.path.join(self.tmp, 'scripts', 'maintenance.py')], invocations)


class CpEdgeScriptsSourceTest(unittest.TestCase):
    # sync-routes.py reads the Kubernetes service account when it is imported, so these checks are on the source

    SCRIPTS = [SYNC_ROUTES_PY, MAINTENANCE_PY]

    def _lines(self, script, pattern):
        with open(script) as f:
            return [line.strip() for line in f if re.search(pattern, line)]

    def test_compiles_under_current_interpreter_with_no_warnings(self):
        # E.g. an invalid escape sequence such as '\d' in a plain string is a SyntaxWarning on Python 3.12
        for script in self.SCRIPTS:
            with warnings.catch_warnings():
                warnings.simplefilter('error')
                with tempfile.NamedTemporaryFile(suffix='.pyc') as cfile:
                    py_compile.compile(script, cfile=cfile.name, doraise=True)

    def test_keeps_no_filter_or_map_result(self):
        # Python 3's filter() and map() return one-pass iterators: len() and indexing fail on them
        for script in self.SCRIPTS:
            self.assertEqual([], self._lines(script, r'=\s*(filter|map)\('), script)

    def test_reads_no_exception_message_attribute(self):
        # Python 3 exceptions have no "message" attribute
        for script in self.SCRIPTS:
            self.assertEqual([], self._lines(script, r'\b(e|ex|exception|error)\.message\b'), script)

    def test_uses_no_deprecated_utcfromtimestamp(self):
        for script in self.SCRIPTS:
            self.assertEqual([], self._lines(script, r'utcfromtimestamp'), script)


class MaintenanceNginxConfTest(unittest.TestCase):
    # maintenance.py checks the maintenance mode when it is run: its functions are loaded without that last call,
    # with stubs for the "requests" and "urllib3" modules where they are not installed

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.reloads = []
        self.logs = []
        self.module = self._load()

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def _load(self):
        stubs = {}
        for name in ('requests', 'urllib3'):
            try:
                __import__(name)
            except ImportError:
                stub = types.ModuleType(name)
                stub.disable_warnings = lambda *args, **kwargs: None
                stubs[name] = stub
        with open(MAINTENANCE_PY) as f:
            source = f.read()
        self.assertTrue(source.rstrip().endswith('check_maintenance_mode()'), 'maintenance.py no longer ends with '
                                                                            'the maintenance mode check')
        source = source.rstrip()[:-len('check_maintenance_mode()')]
        module = types.ModuleType('maintenance')
        saved_modules = dict((name, sys.modules.get(name)) for name in stubs)
        saved_env = dict((name, os.environ.get(name)) for name in ('API', 'API_TOKEN'))
        sys.modules.update(stubs)
        os.environ.update({'API': 'https://cp-api/pipeline/restapi/', 'API_TOKEN': 'token'})
        try:
            exec(compile(source, MAINTENANCE_PY, 'exec'), module.__dict__)
        finally:
            for name, value in saved_modules.items():
                if value is None:
                    sys.modules.pop(name, None)
                else:
                    sys.modules[name] = value
            for name, value in saved_env.items():
                if value is None:
                    os.environ.pop(name, None)
                else:
                    os.environ[name] = value
        module.check_output = lambda cmd, shell=False: self.reloads.append(cmd)
        module.do_log = self.logs.append
        return module

    def test_switches_the_reverse_proxy_port_and_reloads_nginx(self):
        nginx_conf = os.path.join(self.tmp, 'nginx.conf')
        with open(nginx_conf, 'w') as f:
            f.write('upstream reverse_proxy {\n    server 127.0.0.1:8181;\n}\n')
        self.module.NGINX_CONF_FILE_PATH = nginx_conf
        self.module.correct_nginx_conf(maintenance=True)
        with open(nginx_conf) as f:
            self.assertIn('server 127.0.0.1:8383;', f.read())
        self.assertEqual(['nginx -s reload'], self.reloads)

    def test_logs_an_unreadable_nginx_conf(self):
        # A folder in place of the file: reading it fails with an OSError, which is logged
        nginx_conf = os.path.join(self.tmp, 'nginx.conf')
        os.makedirs(nginx_conf)
        self.module.NGINX_CONF_FILE_PATH = nginx_conf
        self.module.correct_nginx_conf(maintenance=True)
        self.assertEqual([], self.reloads)
        self.assertEqual(1, len(self.logs), self.logs)
        self.assertIn(nginx_conf, self.logs[0])


if __name__ == '__main__':
    unittest.main()
