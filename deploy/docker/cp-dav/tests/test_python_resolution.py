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

# Covers the CP_PYTHON_VERSION/CP_PYTHON_PATH resolution added to cp-dav's "init" (issue #4531),
# ported from cp-edge's own version of the same block (including the CP_PYTHON3_HOME fix cp-edge
# needed after Rocky 8's own /usr/bin/python3.12 shadowed the from-source build), and checks that
# dav-extra-command.py is still importable source under both Python 2 and Python 3. There was no
# test runner in this package before - run with `python -m unittest test_python_resolution` (or
# under pytest) from inside deploy/docker/cp-dav/tests. scripts/nfs-roles-management has its own
# test_nfs_roles_management.py, which already covers syncnfs.py - not duplicated here.
#
# Also covers two bugs found in the NFS mount watcher wiring while diagnosing issue #4531's branch
# (watch_mount_shares.py was never actually runnable on cp-dav):
#   - the NFS observer block in "init" only ever lifted scripts/watch_mount_shares.py out of
#     pipe-common.tar.gz, never installing the "pipeline" package itself or its "watchdog"/"psutil"
#     deps anywhere - the script could not even import.
#   - nfs-watcher.sh never sourced env.sh (unlike sync-nfs.sh), so under cron's near-empty
#     environment $SYNC_HOME/$SYNC_LOG_DIR/$CP_PYTHON_PATH were all unset at runtime.

import io
import os
import py_compile
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest


def _which(name):
    for directory in os.environ.get('PATH', '').split(os.pathsep):
        candidate = os.path.join(directory, name)
        if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
    return None


THIS_DIR = os.path.dirname(os.path.abspath(__file__))
CP_DAV_DIR = os.path.dirname(THIS_DIR)
INIT_SCRIPT = os.path.join(CP_DAV_DIR, 'init')
NFS_WATCHER_SCRIPT = os.path.join(CP_DAV_DIR, 'sync', 'nfs-watcher.sh')
BASH = _which('bash') or '/bin/bash'

START_MARKER = '# --- Python interpreter selection ---'
# The resolution block now sits at the top of "init", immediately followed by this comment.
END_MARKER = '# Setup defaults for the env vars and validate'

NFS_OBSERVER_START_MARKER = '# Unpack and configure NFS observer script'
NFS_OBSERVER_END_MARKER = '# Persist environment for a cron job'


def _extract_block(start_marker, end_marker):
    with open(INIT_SCRIPT) as f:
        lines = f.readlines()
    try:
        start = next(i for i, line in enumerate(lines) if line.strip() == start_marker)
    except StopIteration:
        raise AssertionError('start marker %r not found in %s - did it move or get reworded?'
                              % (start_marker, INIT_SCRIPT))
    try:
        end = next(i for i, line in enumerate(lines) if line.strip() == end_marker)
    except StopIteration:
        raise AssertionError('end marker %r not found in %s - did it move or get reworded?'
                              % (end_marker, INIT_SCRIPT))
    return ''.join(lines[start:end])


def _extract_resolution_block():
    return _extract_block(START_MARKER, END_MARKER)


def _write_stub_bin(bin_dir, name):
    path = os.path.join(bin_dir, name)
    with open(path, 'w') as f:
        f.write('#!/bin/sh\necho stub:%s\n' % name)
    st = os.stat(path)
    os.chmod(path, st.st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)
    return path


def _run_resolution(cp_python_version, available_interpreters, python3_home=None):
    bin_dir = tempfile.mkdtemp()
    try:
        for name in available_interpreters:
            _write_stub_bin(bin_dir, name)
        script = _extract_resolution_block() + '\necho "RESOLVED=$CP_PYTHON_PATH"\n'
        env = {'PATH': bin_dir, 'CP_PYTHON_VERSION': cp_python_version}
        if python3_home is not None:
            env['CP_PYTHON3_HOME'] = python3_home
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

    def test_version_3_resolves_to_cp_python3_home_over_bare_python3(self):
        tmp = tempfile.mkdtemp()
        try:
            python3_home = _make_python3_home(tmp)
            rc, out, _ = _run_resolution('3', ['python2', 'python3'], python3_home=python3_home)
            self.assertEqual(0, rc)
            self.assertIn('RESOLVED=%s' % os.path.join(python3_home, 'bin', 'python3.12'), out)
        finally:
            shutil.rmtree(tmp)

    def test_version_3_ignores_distro_python3_12_shadowing_on_path(self):
        # Reproduces the cp-edge test-stand failure this block's design already paid for (issue
        # #4531): Rocky 8 can put its own bare /usr/bin/python3.12 (no pip, none of this image's
        # packages) on PATH ahead of the from-source build. Resolution must still prefer
        # CP_PYTHON3_HOME, never a PATH-found "python3.12".
        tmp = tempfile.mkdtemp()
        try:
            python3_home = _make_python3_home(tmp)
            rc, out, _ = _run_resolution('3', ['python2', 'python3.12', 'python3'], python3_home=python3_home)
            self.assertEqual(0, rc)
            self.assertIn('RESOLVED=%s' % os.path.join(python3_home, 'bin', 'python3.12'), out)
        finally:
            shutil.rmtree(tmp)

    def test_version_3_falls_back_to_python3_when_cp_python3_home_missing(self):
        tmp = tempfile.mkdtemp()
        try:
            missing_home = os.path.join(tmp, 'no-such-python3-home')
            rc, out, _ = _run_resolution('3', ['python2', 'python3'], python3_home=missing_home)
            self.assertEqual(0, rc)
            self.assertIn('RESOLVED=', out)
            self.assertTrue(out.strip().endswith('/python3'))
        finally:
            shutil.rmtree(tmp)

    def test_version_2_falls_back_to_bare_python_when_python2_missing(self):
        rc, out, _ = _run_resolution('2', ['python'])
        self.assertEqual(0, rc)
        self.assertTrue(out.strip().endswith('/python'))

    def test_missing_interpreter_exits_nonzero(self):
        tmp = tempfile.mkdtemp()
        try:
            missing_home = os.path.join(tmp, 'no-such-python3-home')
            rc, out, _ = _run_resolution('3', ['python2'], python3_home=missing_home)
            self.assertNotEqual(0, rc)
            self.assertNotIn('RESOLVED=', out)
        finally:
            shutil.rmtree(tmp)

    def test_builds_pypi_mirror_args_for_python2_default(self):
        bin_dir = tempfile.mkdtemp()
        try:
            _write_stub_bin(bin_dir, 'python2')
            script = _extract_resolution_block() + '\necho "MIRROR_ARGS=$CP_PIP_PACKAGE_INDEX_ARGS"\n'
            proc = subprocess.Popen([BASH, '-c', script], env={'PATH': bin_dir},
                                     stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            out, _ = proc.communicate()
            self.assertEqual(0, proc.returncode)
            self.assertIn('--index-url http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com'
                          '/tools/python/pypi/simple', out.decode())
        finally:
            shutil.rmtree(bin_dir)

    def test_builds_pypi3_mirror_args_for_python3_default(self):
        tmp = tempfile.mkdtemp()
        try:
            python3_home = _make_python3_home(tmp)
            script = _extract_resolution_block() + '\necho "MIRROR_ARGS=$CP_PIP_PACKAGE_INDEX_ARGS"\n'
            env = {'PATH': tmp, 'CP_PYTHON_VERSION': '3', 'CP_PYTHON3_HOME': python3_home}
            proc = subprocess.Popen([BASH, '-c', script], env=env,
                                     stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            out, _ = proc.communicate()
            self.assertEqual(0, proc.returncode)
            # Dot-separated host, matching launch.sh's own py3 default literal exactly - not the
            # hyphenated host that only applies to sync.sh's unrelated py2 default.
            self.assertIn('--index-url http://cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com'
                          '/tools/python/pypi3/simple', out.decode())
        finally:
            shutil.rmtree(tmp)

    def test_respects_overridden_pypi_mirror_base_url(self):
        # The point of this block: a restricted-egress deployment pointing CP_REPO_PYPI_BASE_URL_DEFAULT
        # at its own private mirror must be honored here, not just by launch.sh's job containers.
        bin_dir = tempfile.mkdtemp()
        try:
            _write_stub_bin(bin_dir, 'python2')
            script = _extract_resolution_block() + '\necho "MIRROR_ARGS=$CP_PIP_PACKAGE_INDEX_ARGS"\n'
            env = {
                'PATH': bin_dir,
                'CP_REPO_PYPI_BASE_URL_DEFAULT': 'https://private-mirror.internal/simple',
                'CP_REPO_PYPI_TRUSTED_HOST_DEFAULT': 'private-mirror.internal',
            }
            proc = subprocess.Popen([BASH, '-c', script], env=env,
                                     stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            out, _ = proc.communicate()
            self.assertEqual(0, proc.returncode)
            self.assertIn('--index-url https://private-mirror.internal/simple --trusted-host private-mirror.internal',
                          out.decode())
        finally:
            shutil.rmtree(bin_dir)


def _write_stub(bin_dir, name, body):
    path = os.path.join(bin_dir, name)
    with open(path, 'w') as f:
        f.write('#!/bin/sh\n' + body)
    st = os.stat(path)
    os.chmod(path, st.st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)
    return path


def _make_pipe_common_tarball(dest_path):
    with tarfile.open(dest_path, 'w:gz') as tar:
        content = b'# stub watch_mount_shares.py\n'
        info = tarfile.TarInfo('scripts/watch_mount_shares.py')
        info.size = len(content)
        tar.addfile(info, io.BytesIO(content))


class NfsObserverPipeCommonInstallTest(unittest.TestCase):
    """Covers the "init" NFS observer block actually installing pipe-common (issue #4531)."""

    def _run_block(self, work_dir, cp_python_version):
        bin_dir = os.path.join(work_dir, 'bin')
        os.makedirs(bin_dir)
        sync_home = os.path.join(work_dir, 'sync-home')
        os.makedirs(sync_home)
        record_file = os.path.join(work_dir, 'python-invocations.txt')

        # The real container is Rocky Linux (GNU tar), where "tar -xf f member --strip-components=1"
        # (flags after operands) is accepted. macOS's bundled BSD tar rejects that ordering, so shim
        # "tar" to GNU tar here if available, rather than changing the command this test is covering.
        gnu_tar = _which('gtar') or _which('gnutar')
        if gnu_tar:
            _write_stub(bin_dir, 'tar', 'exec "%s" "$@"\n' % gnu_tar)

        python_stub = _write_stub(bin_dir, 'python-stub', 'echo "$@" >> "%s"\n' % record_file)
        _write_stub(bin_dir, 'sysctl', 'exit 0\n')

        _make_pipe_common_tarball(os.path.join(work_dir, 'pipe-common.tar.gz'))

        block = _extract_block(NFS_OBSERVER_START_MARKER, NFS_OBSERVER_END_MARKER)
        # Keep the real logic under test, but redirect its two other hardcoded absolute paths
        # away from the real filesystem: downloading is replaced with a no-op (the tarball is
        # already in place above) and the crontab fragment is written next to it, not to /tmp.
        tmp_sync = os.path.join(work_dir, 'tmp-sync')
        block = block.replace('/tmp/sync', tmp_sync)
        script = 'download_file() { :; }\n' + block + '\necho "DONE=$?"\n'

        env = {
            'PATH': bin_dir + os.pathsep + '/usr/bin' + os.pathsep + '/bin',
            'SYNC_HOME': sync_home,
            'CP_PYTHON_PATH': python_stub,
            'CP_PYTHON_VERSION': cp_python_version,
            # Normally computed by the python-selection block above this one in "init" - set
            # directly here since this test extracts only the NFS observer block in isolation.
            'CP_PIP_PACKAGE_INDEX_ARGS': '--index-url http://test-mirror/simple --trusted-host test-mirror',
        }
        proc = subprocess.Popen([BASH, '-c', script], cwd=work_dir, env=env,
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        invocations = ''
        if os.path.exists(record_file):
            with open(record_file) as f:
                invocations = f.read()
        return proc.returncode, out.decode(), err.decode(), invocations, sync_home

    def test_installs_pipe_common_with_resolved_interpreter(self):
        work_dir = tempfile.mkdtemp()
        try:
            rc, out, err, invocations, sync_home = self._run_block(work_dir, '3')
            self.assertEqual(0, rc, 'block exited non-zero: out=%r err=%r' % (out, err))
            self.assertIn('-m pip install', invocations,
                          'resolved $CP_PYTHON_PATH was never used to install pipe-common: %r' % invocations)
            self.assertIn('setuptools==68.0.0', invocations)
            self.assertIn('--index-url http://test-mirror/simple --trusted-host test-mirror', invocations,
                          'the platform PyPI mirror args were not passed through to pip: %r' % invocations)
            self.assertTrue(os.path.exists(os.path.join(sync_home, 'watch_mount_shares.py')),
                            'watch_mount_shares.py was not copied into $SYNC_HOME')
        finally:
            shutil.rmtree(work_dir)

    def test_pins_setuptools_44_for_python2(self):
        work_dir = tempfile.mkdtemp()
        try:
            rc, out, err, invocations, _ = self._run_block(work_dir, '2')
            self.assertEqual(0, rc, 'block exited non-zero: out=%r err=%r' % (out, err))
            self.assertIn('setuptools==44.1.1', invocations)
            self.assertNotIn('setuptools==68.0.0', invocations)
        finally:
            shutil.rmtree(work_dir)


class NfsWatcherEnvSourcingTest(unittest.TestCase):
    """Covers nfs-watcher.sh sourcing env.sh so it has $SYNC_HOME/$CP_PYTHON_PATH under cron (issue #4531)."""

    def _run_watcher(self, work_dir):
        bin_dir = os.path.join(work_dir, 'bin')
        os.makedirs(bin_dir)
        # Real, existing directories - the script redirects the started watcher's stderr under
        # $SYNC_LOG_DIR, which must exist for that redirection (and so the nohup'd stub) to succeed.
        sync_home = os.path.join(work_dir, 'sync-home')
        sync_log_dir = os.path.join(work_dir, 'sync-home', 'logs')
        os.makedirs(sync_log_dir)
        record_file = os.path.join(work_dir, 'invocations.txt')

        # nfs-watcher.sh runs the watcher via "nohup $CP_PYTHON_PATH -u <script> &" - give it a
        # stub interpreter so we can observe exactly what it was invoked with.
        python_stub = _write_stub(bin_dir, 'python-stub', 'echo "$@" >> "%s"\n' % record_file)
        # Cron's own environment: no SYNC_HOME/SYNC_LOG_DIR/CP_PYTHON_PATH of its own, only PATH.
        _write_stub(bin_dir, 'pgrep', 'exit 1\n')  # "no active observer process found"

        env_file = os.path.join(work_dir, 'env.sh')
        with open(env_file, 'w') as f:
            f.write('SYNC_HOME=%s\nSYNC_LOG_DIR=%s\nCP_PYTHON_PATH=%s\n'
                     % (sync_home, sync_log_dir, python_stub))

        with open(NFS_WATCHER_SCRIPT) as f:
            script = f.read().replace('/opt/sync/env.sh', env_file)

        env = {'PATH': bin_dir + os.pathsep + '/usr/bin' + os.pathsep + '/bin'}
        proc = subprocess.Popen([BASH, '-c', script], cwd=work_dir, env=env,
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        self.assertEqual(0, proc.returncode, 'nfs-watcher.sh exited non-zero: out=%r err=%r'
                          % (out.decode(), err.decode()))

        deadline = time.time() + 2
        while not os.path.exists(record_file) and time.time() < deadline:
            time.sleep(0.05)
        invocations = ''
        if os.path.exists(record_file):
            with open(record_file) as f:
                invocations = f.read()
        return invocations, sync_home

    def test_uses_sync_home_and_cp_python_path_from_sourced_env(self):
        work_dir = tempfile.mkdtemp()
        try:
            invocations, sync_home = self._run_watcher(work_dir)
            self.assertIn('-u %s/watch_mount_shares.py' % sync_home, invocations,
                          'watcher was not started against $SYNC_HOME/watch_mount_shares.py: %r' % invocations)
        finally:
            shutil.rmtree(work_dir)


class CpDavScriptsCompileUnderBothInterpretersTest(unittest.TestCase):

    SCRIPTS = [
        os.path.join(CP_DAV_DIR, 'extra', 'dav-extra-command.py'),
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
