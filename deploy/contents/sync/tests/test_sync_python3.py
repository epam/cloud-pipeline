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

# Covers the environment sync tool on Python 3:
#   - sync.sh prepares the Python 3.12 of CP_PYTHON_PATH, with requests and urllib3 from the platform's Python 3 package
#     mirror, and runs the sync scripts with it
#   - the sync scripts import on Python 3, read the shell command output as text, and write a registry certificate,
#     which the API sends as bytes
# sync.sh sources the install scripts, which install packages, so only its prepare_environment function is run here, with
# stubs. Run with `python3 -m unittest discover -s tests` from inside deploy/contents/sync, with "requests" installed.

import os
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest

SYNC_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_DIR = os.path.join(SYNC_DIR, 'app')
SYNC_SH = os.path.join(APP_DIR, 'sync.sh')
BASH = shutil.which('bash') or '/bin/bash'

sys.path.insert(0, APP_DIR)


def _write_stub(path, body):
    with open(path, 'w') as f:
        f.write('#!/bin/sh\n' + body)
    os.chmod(path, os.stat(path).st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)
    return path


def _read(path):
    if not os.path.exists(path):
        return ''
    with open(path) as f:
        return f.read()


def _extract_function(script_path, name):
    lines = _read(script_path).splitlines(True)
    start = next(i for i, line in enumerate(lines) if line.startswith('function %s ' % name))
    end = next(i for i in range(start + 1, len(lines)) if lines[i].rstrip() == '}')
    return ''.join(lines[start:end + 1])


class SyncShPythonTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.record = os.path.join(self.tmp, 'python-invocations.txt')

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def _prepare_environment(self, python_body, extra_env=None):
        python = _write_stub(os.path.join(self.tmp, 'python3.12'),
                             'echo "$@" >> "%s"\n%s' % (self.record, python_body))
        script = '\n'.join([
            'function check_installed { command -v "$1" >/dev/null 2>&1; }',
            'function print_info { echo "INFO: $*"; }',
            'function print_err { echo "ERROR: $*"; }',
            _extract_function(SYNC_SH, 'prepare_environment'),
            'prepare_environment; echo "RESULT=$? CP_PYTHON_PATH=$CP_PYTHON_PATH"'])
        env = {'PATH': '/usr/bin:/bin', 'CP_PYTHON_PATH': python}
        env.update(extra_env or {})
        proc = subprocess.Popen([BASH, '-c', script], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        out, _ = proc.communicate()
        return proc.returncode, out.decode(), _read(self.record).splitlines(), python

    def test_keeps_a_python_with_the_dependencies(self):
        rc, out, invocations, python = self._prepare_environment('exit 0\n')
        self.assertEqual(0, rc, out)
        self.assertIn('RESULT=0 CP_PYTHON_PATH=%s' % python, out)
        self.assertEqual(['-c import requests;import urllib3'], invocations)

    def test_installs_the_dependencies_from_the_python_3_mirror(self):
        rc, out, invocations, _ = self._prepare_environment('case "$1" in -c) exit 1 ;; esac\nexit 0\n')
        self.assertEqual(0, rc, out)
        self.assertEqual(['-c import requests;import urllib3',
                          '-m pip install --index-url http://cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com'
                          '/tools/python/pypi3/simple --trusted-host'
                          ' cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com -q urllib3 requests'],
                         invocations)

    def test_trusts_the_host_of_an_overridden_mirror(self):
        rc, out, invocations, _ = self._prepare_environment(
            'case "$1" in -c) exit 1 ;; esac\nexit 0\n',
            extra_env={'CP_REPO_PYPI3_BASE_URL_DEFAULT': 'http://mirror.internal:8080/pypi3/simple'})
        self.assertEqual(0, rc, out)
        self.assertEqual('-m pip install --index-url http://mirror.internal:8080/pypi3/simple'
                         ' --trusted-host mirror.internal -q urllib3 requests', invocations[-1])

    def test_fails_when_the_dependencies_cannot_be_installed(self):
        rc, out, _, _ = self._prepare_environment('exit 1\n')
        self.assertNotEqual(0, rc, out)
        self.assertIn("ERROR: Can't install dependencies, exiting", out)

    def test_runs_the_sync_scripts_with_cp_python_path(self):
        sync_lines = [line.strip() for line in _read(SYNC_SH).splitlines() if 'sync_users.py' in line
                      or 'sync_tools.py' in line]
        self.assertEqual(2, len(sync_lines), sync_lines)
        for line in sync_lines:
            self.assertTrue(line.startswith('"$CP_PYTHON_PATH" sync_'), line)


class SyncScriptsPython3Test(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def test_imports_on_python_3(self):
        import sync_tools
        import sync_users  # noqa: F401
        from api.tool_api import ToolSyncAPI  # noqa: F401
        from api.users_api import UserSyncAPI  # noqa: F401
        self.assertTrue(callable(sync_tools.execute_shell_cmd))

    def test_shell_command_output_is_text(self):
        import sync_tools
        code, (out, err) = sync_tools.execute_shell_cmd('echo out; echo err 1>&2', return_output=True)
        self.assertEqual((0, 'out\n', 'err\n'), (code, out, err))

    def test_writes_the_registry_certificate_the_api_sends_as_bytes(self):
        import sync_tools

        class _Api(object):
            def load_registry_certificate(self, registry_id):
                return b'-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n'

        synchronizer = sync_tools.ToolSynchronizer.__new__(sync_tools.ToolSynchronizer)
        synchronizer.api_source = _Api()
        saved_certs_dir = sync_tools.DOCKER_CERTS_DIR
        sync_tools.DOCKER_CERTS_DIR = os.path.join(self.tmp, 'certs.d')
        try:
            synchronizer.pull_certificate_for_registry('registry.example.com:443', 1)
        finally:
            sync_tools.DOCKER_CERTS_DIR = saved_certs_dir
        with open(os.path.join(self.tmp, 'certs.d', 'registry.example.com:443', 'ca.crt'), 'rb') as f:
            self.assertEqual(b'-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n', f.read())


if __name__ == '__main__':
    unittest.main()
