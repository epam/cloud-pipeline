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

# Covers the two parts of cp-dav's NFS mount watcher wiring that kept watch_mount_shares.py from ever
# running (the NFS observer tests of develop's deploy/docker/cp-dav/tests/test_python_resolution.py,
# without its CP_PYTHON_PATH / Python 3 parts):
#   - the NFS observer block in "init" only lifted scripts/watch_mount_shares.py out of
#     pipe-common.tar.gz, and never installed the "pipeline" package or its "watchdog"/"psutil" deps -
#     the script could not even import.
#   - nfs-watcher.sh never sourced env.sh (unlike sync-nfs.sh), so under cron's near-empty
#     environment $SYNC_HOME/$SYNC_LOG_DIR were unset at runtime.
# Plain unittest, so it runs under Python 2 and 3 with no extra packages - run with
# `python -m unittest test_nfs_observer` (or under pytest) from inside deploy/docker/cp-dav/tests.
# Needs bash and GNU tar, as in the image.

import io
import os
import shutil
import stat
import subprocess
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

NFS_OBSERVER_START_MARKER = '# Unpack and configure NFS observer script'
NFS_OBSERVER_END_MARKER = '# Persist environment for a cron job'

DEFAULT_PIP_INDEX_ARGS = ('--index-url http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com'
                          '/tools/python/pypi/simple'
                          ' --trusted-host cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com')


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


def _read(path):
    if not os.path.exists(path):
        return ''
    with open(path) as f:
        return f.read()


class NfsObserverPipeCommonInstallTest(unittest.TestCase):
    """Covers the "init" NFS observer block installing pipe-common, as launch.sh does in a run."""

    def setUp(self):
        self.work_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.work_dir)

    def _run_block(self, python_stub_body=None, extra_env=None):
        bin_dir = os.path.join(self.work_dir, 'bin')
        os.makedirs(bin_dir)
        sync_home = os.path.join(self.work_dir, 'sync-home')
        os.makedirs(sync_home)
        record_file = os.path.join(self.work_dir, 'python-invocations.txt')

        # The real container has GNU tar, which accepts "tar -xf f member --strip-components=1"
        # (flags after operands). macOS's bundled BSD tar rejects that ordering, so shim "tar" to GNU
        # tar there if available, rather than changing the command this test is covering.
        gnu_tar = _which('gtar') or _which('gnutar')
        if gnu_tar:
            _write_stub(bin_dir, 'tar', 'exec "%s" "$@"\n' % gnu_tar)

        # "init" calls the image's "python" by name: record its arguments instead of installing.
        _write_stub(bin_dir, 'python', 'echo "$@" >> "%s"\n%s' % (record_file, python_stub_body or ''))
        _write_stub(bin_dir, 'sysctl', 'exit 0\n')

        _make_pipe_common_tarball(os.path.join(self.work_dir, 'pipe-common.tar.gz'))

        block = _extract_block(NFS_OBSERVER_START_MARKER, NFS_OBSERVER_END_MARKER)
        # Keep the real logic under test, but redirect its hardcoded crontab fragment path away from
        # the real filesystem, and replace downloading with a no-op (the tarball is already in place).
        self.tmp_sync = os.path.join(self.work_dir, 'tmp-sync')
        block = block.replace('/tmp/sync', self.tmp_sync)
        script = 'download_file() { :; }\n' + block + '\necho "DONE=$?"\n'

        env = {
            'PATH': bin_dir + os.pathsep + '/usr/bin' + os.pathsep + '/bin',
            'SYNC_HOME': sync_home,
        }
        env.update(extra_env or {})
        proc = subprocess.Popen([BASH, '-c', script], cwd=self.work_dir, env=env,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        self.sync_home = sync_home
        return proc.returncode, out.decode(), err.decode(), _read(record_file).splitlines()

    def test_installs_setuptools_44_then_pipe_common(self):
        rc, out, err, invocations = self._run_block()
        self.assertEqual(0, rc, 'block exited non-zero: out=%r err=%r' % (out, err))
        self.assertEqual(2, len(invocations), 'expected two pip installs: %r' % invocations)
        self.assertTrue(invocations[0].startswith('-m pip install'), invocations)
        self.assertIn('-I -q setuptools==44.1.1', invocations[0])
        self.assertTrue(invocations[1].startswith('-m pip install'), invocations)
        self.assertIn('-I -q pipe-common.tar.gz', invocations[1])
        self.assertTrue(os.path.exists(os.path.join(self.sync_home, 'watch_mount_shares.py')),
                        'watch_mount_shares.py was not copied into $SYNC_HOME')
        self.assertIn('nfs-watcher.sh', _read(self.tmp_sync), 'the observer cron job was not added')

    def test_uses_default_pypi_mirror(self):
        _, out, err, invocations = self._run_block()
        self.assertEqual(2, len(invocations), 'out=%r err=%r' % (out, err))
        for invocation in invocations:
            self.assertIn(DEFAULT_PIP_INDEX_ARGS, invocation)

    def test_respects_overridden_pypi_mirror(self):
        _, out, err, invocations = self._run_block(extra_env={
            'CP_REPO_PYPI_BASE_URL_DEFAULT': 'https://private-mirror.internal/simple',
            'CP_REPO_PYPI_TRUSTED_HOST_DEFAULT': 'private-mirror.internal',
        })
        self.assertEqual(2, len(invocations), 'out=%r err=%r' % (out, err))
        for invocation in invocations:
            self.assertIn('--index-url https://private-mirror.internal/simple'
                          ' --trusted-host private-mirror.internal', invocation)

    def test_no_observer_cron_job_when_setuptools_install_fails(self):
        _, _, _, invocations = self._run_block(python_stub_body='exit 1\n')
        self.assertEqual(1, len(invocations),
                         'expected only the setuptools install, which fails, and no pipe-common install: %r'
                         % invocations)
        self.assertNotIn('nfs-watcher.sh', _read(self.tmp_sync))

    def test_no_observer_cron_job_when_pipe_common_install_fails(self):
        _, _, _, invocations = self._run_block(
            python_stub_body='case "$*" in *pipe-common.tar.gz*) exit 1 ;; esac\n')
        self.assertEqual(2, len(invocations), invocations)
        self.assertNotIn('nfs-watcher.sh', _read(self.tmp_sync))


class NfsWatcherEnvSourcingTest(unittest.TestCase):
    """Covers nfs-watcher.sh sourcing env.sh, so it has $SYNC_HOME/$SYNC_LOG_DIR under cron."""

    def _run_watcher(self, work_dir):
        bin_dir = os.path.join(work_dir, 'bin')
        os.makedirs(bin_dir)
        # Real, existing directories - the script redirects the started watcher's stderr under
        # $SYNC_LOG_DIR, which must exist for that redirection (and so the nohup'd stub) to succeed.
        sync_home = os.path.join(work_dir, 'sync-home')
        sync_log_dir = os.path.join(work_dir, 'sync-home', 'logs')
        os.makedirs(sync_log_dir)
        record_file = os.path.join(work_dir, 'invocations.txt')

        # nfs-watcher.sh runs the watcher via "nohup python -u <script> &" - give it a stub
        # interpreter so we can observe exactly what it was invoked with.
        _write_stub(bin_dir, 'python', 'echo "$@" >> "%s"\n' % record_file)
        # "no active observer process found": the process listing is empty
        _write_stub(bin_dir, 'ps', 'exit 0\n')

        env_file = os.path.join(work_dir, 'env.sh')
        with open(env_file, 'w') as f:
            f.write('SYNC_HOME=%s\nSYNC_LOG_DIR=%s\n' % (sync_home, sync_log_dir))

        with open(NFS_WATCHER_SCRIPT) as f:
            script = f.read().replace('/opt/sync/env.sh', env_file)

        # Cron's own environment: no SYNC_HOME/SYNC_LOG_DIR of its own, only PATH.
        env = {'PATH': bin_dir + os.pathsep + '/usr/bin' + os.pathsep + '/bin'}
        proc = subprocess.Popen([BASH, '-c', script], cwd=work_dir, env=env,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = proc.communicate()
        self.assertEqual(0, proc.returncode, 'nfs-watcher.sh exited non-zero: out=%r err=%r'
                         % (out.decode(), err.decode()))

        deadline = time.time() + 2
        while not os.path.exists(record_file) and time.time() < deadline:
            time.sleep(0.05)
        return _read(record_file), sync_home

    def test_uses_sync_home_from_sourced_env(self):
        work_dir = tempfile.mkdtemp()
        try:
            invocations, sync_home = self._run_watcher(work_dir)
            self.assertIn('-u %s/watch_mount_shares.py' % sync_home, invocations,
                          'watcher was not started against $SYNC_HOME/watch_mount_shares.py: %r' % invocations)
        finally:
            shutil.rmtree(work_dir)


if __name__ == '__main__':
    unittest.main()
