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

# Covers the Azure blob storage ("AZ") branch of syncmounts.sh. The cp-dav image moved to
# Rocky Linux 9, which has no blobfuse 1 package, so the branch mounts with blobfuse2's v1-compatible
# "blobfuse2 mountv1". The test runs the real script with stub "curl", "mountpoint" and "blobfuse2" first on
# PATH, and checks the exact blobfuse2 command line and the credentials in its environment. Run with
# `python3 -m unittest test_syncmounts` from inside scripts/nfs-roles-management/tests. It needs bash and jq on PATH.

import json
import os
import shutil
import stat
import subprocess
import tempfile
import unittest

THIS_DIR = os.path.dirname(os.path.abspath(__file__))
PACKAGE_DIR = os.path.dirname(THIS_DIR)
SYNCMOUNTS_SCRIPT = os.path.join(PACKAGE_DIR, 'syncmounts.sh')

API_URL = 'https://cp-api.internal:8443/pipeline/restapi/'
API_TOKEN = 'test-api-token'
REGION_ID = 7
STORAGE_ID = 42
STORAGE_NAME = 'az-storage'
CONTAINER_NAME = 'azcontainer'
STORAGE_ACCOUNT = 'cpteststorage'
STORAGE_KEY = 'dGVzdC1zdG9yYWdlLWtleQ=='


def _which(name):
    for directory in os.environ.get('PATH', '').split(os.pathsep):
        candidate = os.path.join(directory, name)
        if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
    return None


def _write_stub(bin_dir, name, body):
    path = os.path.join(bin_dir, name)
    with open(path, 'w') as f:
        f.write('#!/bin/sh\n' + body)
    st = os.stat(path)
    os.chmod(path, st.st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)
    return path


def _write_json(path, payload):
    with open(path, 'w') as f:
        json.dump(payload, f)


class SyncMountsAzureBlobfuse2Test(unittest.TestCase):

    def setUp(self):
        self.bash = _which('bash')
        self.jq = _which('jq')
        if not self.bash or not self.jq:
            raise unittest.SkipTest('bash and jq must be on PATH to run syncmounts.sh')
        self.work_dir = tempfile.mkdtemp()
        self.bin_dir = self._make_dir('bin')
        self.api_dir = self._make_dir('api')
        self.creds_dir = self._make_dir('regioncreds')
        self.blobfuse_tmp_dir = os.path.join(self.work_dir, 'blobfusetmp')
        self.mount_root = os.path.join(self.work_dir, 'dav-mount')
        self.cwd_dir = self._make_dir('cwd')
        self.record_dir = self._make_dir('records')

        # The three API calls the script makes before it reaches the AZ branch: no file shares, one AZ
        # storage, and a "dav-mount" tag on it that requests the mount
        _write_json(os.path.join(self.api_dir, 'region.json'),
                    {'payload': [{'id': REGION_ID, 'fileShareMounts': []}]})
        _write_json(os.path.join(self.api_dir, 'storages.json'),
                    {'payload': [{'id': STORAGE_ID, 'name': STORAGE_NAME, 'path': CONTAINER_NAME,
                                  'type': 'AZ', 'regionId': REGION_ID, 'sensitive': False}]})
        _write_json(os.path.join(self.api_dir, 'metadata.json'),
                    {'payload': [{'entity': {'entityId': STORAGE_ID},
                                  'data': {'dav-mount': {'value': 'true'}}}]})
        _write_stub(self.bin_dir, 'curl',
                    'for arg; do url="$arg"; done\n'
                    'case "$url" in\n'
                    '    */cloud/region) cat "%(dir)s/region.json" ;;\n'
                    '    */datastorage/loadAll) cat "%(dir)s/storages.json" ;;\n'
                    '    */metadata/search/entry*) cat "%(dir)s/metadata.json" ;;\n'
                    '    *) exit 22 ;;\n'
                    'esac\n' % {'dir': self.api_dir})
        _write_stub(self.bin_dir, 'mountpoint', 'exit 1\n')
        _write_stub(self.bin_dir, 'blobfuse2',
                    'printf "%%s\\n" "$@" > "%(dir)s/blobfuse2.argv"\n'
                    'printf "AZURE_STORAGE_ACCOUNT=%%s\\nAZURE_STORAGE_ACCESS_KEY=%%s\\n" '
                    '"$AZURE_STORAGE_ACCOUNT" "$AZURE_STORAGE_ACCESS_KEY" > "%(dir)s/blobfuse2.env"\n'
                    'exit 0\n' % {'dir': self.record_dir})

    def tearDown(self):
        shutil.rmtree(self.work_dir)

    def _make_dir(self, name):
        path = os.path.join(self.work_dir, name)
        os.makedirs(path)
        return path

    def _write_region_creds(self):
        _write_json(os.path.join(self.creds_dir, str(REGION_ID)),
                    {'storage_account': STORAGE_ACCOUNT, 'storage_key': STORAGE_KEY})

    def _run_syncmounts(self, override_creds_dir=True):
        env = {
            'PATH': os.pathsep.join([self.bin_dir, os.path.dirname(self.jq), '/usr/bin', '/bin']),
            'CP_DAV_BLOBFUSE_TMP_DIR': self.blobfuse_tmp_dir,
        }
        if override_creds_dir:
            env['CP_DAV_REGION_CREDS_DIR'] = self.creds_dir
        proc = subprocess.Popen([self.bash, SYNCMOUNTS_SCRIPT, API_URL, API_TOKEN, self.mount_root],
                                cwd=self.cwd_dir, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        out, _ = proc.communicate()
        return proc.returncode, out.decode()

    def _read_record(self, name):
        path = os.path.join(self.record_dir, name)
        if not os.path.exists(path):
            return None
        with open(path) as f:
            return f.read().splitlines()

    def test_mounts_container_with_blobfuse2_mountv1(self):
        self._write_region_creds()
        rc, out = self._run_syncmounts()
        self.assertEqual(0, rc, 'syncmounts.sh exited non-zero: %s' % out)

        tmp_path = os.path.join(self.blobfuse_tmp_dir, CONTAINER_NAME)
        argv = self._read_record('blobfuse2.argv')
        self.assertEqual([
            'mountv1',
            os.path.join(self.mount_root, 'AZ', STORAGE_NAME),
            '--container-name=%s' % CONTAINER_NAME,
            '--tmp-path=%s' % tmp_path,
            '--output-file=%s.yaml' % tmp_path,
            '--disable-version-check=true',
            '--empty-dir-check=false',
            '-o', 'attr_timeout=1',
            '-o', 'entry_timeout=1',
            '-o', 'negative_timeout=1',
        ], argv, 'unexpected blobfuse2 command line, script output: %s' % out)
        self.assertTrue(os.path.isdir(tmp_path), 'the blobfuse2 tmp path was not created')
        self.assertEqual(['AZURE_STORAGE_ACCOUNT=%s' % STORAGE_ACCOUNT,
                          'AZURE_STORAGE_ACCESS_KEY=%s' % STORAGE_KEY],
                         self._read_record('blobfuse2.env'))
        self.assertIn('[DONE] %s is mounted' % CONTAINER_NAME, out)

    def test_skips_container_without_region_creds(self):
        # Runs without CP_DAV_REGION_CREDS_DIR, so the default location is the one looked up (and reported)
        rc, out = self._run_syncmounts(override_creds_dir=False)
        self.assertEqual(0, rc, 'syncmounts.sh exited non-zero: %s' % out)
        self.assertIsNone(self._read_record('blobfuse2.argv'), 'blobfuse2 ran without the region creds')
        self.assertIn('[ERROR] Cred file for Azure region %s, not found: /root/.cloud/regioncreds/%s'
                      % (REGION_ID, REGION_ID), out)


if __name__ == '__main__':
    unittest.main()
