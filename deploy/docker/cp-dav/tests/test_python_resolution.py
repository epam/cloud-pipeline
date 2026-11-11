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

# Covers the Python 3.12 of cp-dav:
#   - the CP_PYTHON_PATH resolution of "init": the Python 3.12 of CP_PYTHON3_HOME, or an explicit CP_PYTHON_PATH,
#     and nothing else
#   - sync-nfs.sh runs syncnfs.py with the CP_PYTHON_PATH that "init" persists in env.sh, under cron's PATH
# Run with `python3 -m unittest discover -s tests` (or under pytest) from inside deploy/docker/cp-dav.

import os
import shutil
import stat
import subprocess
import tempfile
import unittest

THIS_DIR = os.path.dirname(os.path.abspath(__file__))
CP_DAV_DIR = os.path.dirname(THIS_DIR)
INIT_SCRIPT = os.path.join(CP_DAV_DIR, 'init')
SYNC_NFS_SCRIPT = os.path.join(CP_DAV_DIR, 'sync', 'sync-nfs.sh')
START_MARKER = '# --- Python interpreter selection ---'
END_MARKER = 'function download_file {'
BASH = shutil.which('bash') or '/bin/bash'


def _read(path):
    if not os.path.exists(path):
        return ''
    with open(path) as f:
        return f.read()


def _extract_resolution_block():
    lines = _read(INIT_SCRIPT).splitlines(True)
    try:
        start = next(i for i, line in enumerate(lines) if line.strip() == START_MARKER)
        end = next(i for i, line in enumerate(lines) if line.strip() == END_MARKER)
    except StopIteration:
        raise AssertionError('the markers %r and %r are not found in %s - did they move or get reworded?'
                             % (START_MARKER, END_MARKER, INIT_SCRIPT))
    return ''.join(lines[start:end])


def _write_stub_bin(bin_dir, name, body=None):
    path = os.path.join(bin_dir, name)
    with open(path, 'w') as f:
        f.write('#!/bin/sh\n' + (body or 'echo stub:%s\n' % name))
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


class SyncNfsPythonTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def test_runs_syncnfs_with_cp_python_path_from_sourced_env(self):
        record = os.path.join(self.tmp, 'invocations.txt')
        sync_home = os.path.join(self.tmp, 'sync-home')
        os.makedirs(os.path.join(sync_home, 'nfs-roles-management'))
        with open(os.path.join(sync_home, 'nfs-roles-management', 'syncmounts.sh'), 'w') as f:
            f.write('echo "syncmounts $@" >> "%s"\n' % record)
        python = _write_stub_bin(self.tmp, 'python3.12', 'echo "python3.12 $@" >> "%s"\n' % record)
        # Cron's PATH has a "python" that is not the Python of the image: sync-nfs.sh must not call it
        bin_dir = os.path.join(self.tmp, 'bin')
        os.makedirs(bin_dir)
        _write_stub_bin(bin_dir, 'python', 'echo "python $@" >> "%s"\n' % record)
        log_dir = os.path.join(self.tmp, 'logs')
        env_file = os.path.join(self.tmp, 'env.sh')
        with open(env_file, 'w') as f:
            f.write('API=https://cp-api/pipeline/restapi/\nAPI_TOKEN=token\nCP_DAV_SERVE_DIR=/dav-serve\n'
                    'CP_DAV_MOUNT_POINT=/dav-mount\nSYNC_HOME=%s\nSYNC_LOG_DIR=%s\nCP_PYTHON_PATH=%s\n'
                    % (sync_home, log_dir, python))
        script = _read(SYNC_NFS_SCRIPT).replace('/opt/sync/env.sh', env_file) \
                                       .replace('/var/run/sync-nfs.lock', os.path.join(self.tmp, 'sync-nfs.lock'))
        proc = subprocess.Popen([BASH, '-c', script], env={'PATH': bin_dir + os.pathsep + '/usr/bin:/bin'},
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        self.assertEqual(0, proc.returncode, 'out=%r err=%r log=%r' % (out, err, _read(os.path.join(
            log_dir, 'sync-nfs.log'))))
        self.assertEqual(['syncmounts https://cp-api/pipeline/restapi/ token /dav-mount',
                          'python3.12 %s/nfs-roles-management/syncnfs.py sync --api=https://cp-api/pipeline/restapi/'
                          ' --key=token --users-root=/dav-serve --nfs-root=/dav-mount' % sync_home],
                         _read(record).splitlines())


if __name__ == '__main__':
    unittest.main()
