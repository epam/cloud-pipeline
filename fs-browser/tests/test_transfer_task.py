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

# Covers TransferTask on Python 3:
#   - the "pipe storage cp" output reaches the task status and its error message, so it has to be text, not bytes
#   - a folder download archives the folder without the excluded items: TarFile.add() has no "exclude" since
#     Python 3.7
# Run with `python3 -m unittest discover -s tests` from inside fs-browser, with the packages of requirements.txt.

import os
import shutil
import sys
import tarfile
import tempfile
import unittest

FS_BROWSER_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, FS_BROWSER_DIR)

from fsbrowser.src.transfer_task import TransferTask  # noqa: E402

SHELL = '/bin/sh'


class _Logger(object):

    def __init__(self):
        self.messages = []

    def log(self, message):
        self.messages.append(message)


class TransferTaskCommandTest(unittest.TestCase):

    def test_command_output_is_text(self):
        task = TransferTask('task-1', 'storage', logger=_Logger())
        exit_code, stdout, stderr = task.execute_command([SHELL, '-c', 'echo first; echo last; echo oops 1>&2'],
                                                         False)
        self.assertEqual(0, exit_code)
        self.assertEqual('last', stdout)
        self.assertEqual(['oops'], stderr)

    def test_failed_command_message_has_no_bytes_literals(self):
        task = TransferTask('task-1', 'storage', logger=_Logger())
        with self.assertRaises(RuntimeError) as error:
            task.get_command_output([SHELL, '-c', 'echo out; echo err 1>&2; exit 3'], False)
        self.assertEqual("stdout: out\nstderr: ['err']", str(error.exception))


class TransferTaskCompressTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.source = os.path.join(self.tmp, 'data')
        os.makedirs(os.path.join(self.source, 'keep'))
        os.makedirs(os.path.join(self.source, 'skip'))
        for name in ('keep/a.txt', 'skip/b.txt', 'c.log'):
            with open(os.path.join(self.source, name), 'w') as f:
                f.write(name)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def _compress(self, exclude_list):
        archive = os.path.join(self.tmp, 'out.tar.gz')
        TransferTask.compress_directory(archive, self.source, False, exclude_list)
        with tarfile.open(archive) as tar:
            members = tar.getmembers()
        prefix = self.source.lstrip(os.sep)
        return dict((member.name[len(prefix):] or '/', member.mode) for member in members)

    def test_archives_the_folder_with_the_download_permissions(self):
        members = self._compress([])
        self.assertEqual(['/', '/c.log', '/keep', '/keep/a.txt', '/skip', '/skip/b.txt'], sorted(members))
        self.assertEqual({0o774}, set(members.values()))

    def test_skips_the_excluded_folders_and_files(self):
        members = self._compress([os.path.join(self.source, 'skip') + '/', '*.log'])
        self.assertEqual(['/', '/keep', '/keep/a.txt'], sorted(members))

    def test_downloads_a_folder_through_the_transfer_storage(self):
        # The whole download: the folder is archived without the excluded items, and the archive is copied to the
        # transfer storage with "pipe storage cp", here a stub that keeps a copy of what it was given
        bin_dir = os.path.join(self.tmp, 'bin')
        os.makedirs(bin_dir)
        uploaded = os.path.join(self.tmp, 'uploaded.tar.gz')
        with open(os.path.join(bin_dir, 'pipe'), 'w') as f:
            f.write('#!/bin/sh\necho "$@" > "%s.args"\ncp "$3" "%s"\necho done\n' % (uploaded, uploaded))
        os.chmod(os.path.join(bin_dir, 'pipe'), 0o755)
        tmp_dir = os.path.join(self.tmp, 'tmp')
        os.makedirs(tmp_dir)

        task = TransferTask('task-1', 'transfer-storage', 'fsbrowser/1', logger=_Logger())
        saved_path = os.environ['PATH']
        os.environ['PATH'] = bin_dir + os.pathsep + saved_path
        try:
            import fsbrowser.src.transfer_task as transfer_task_module
            saved_provider = transfer_task_module.CloudPipelineApiProvider

            class _Api(object):
                def load_storage_id_by_name(self, name):
                    return 7

                def get_download_url(self, storage_id, path):
                    return 'https://download/%s/%s' % (storage_id, path)

            transfer_task_module.CloudPipelineApiProvider = _Api
            try:
                task.download('data', self.tmp, tmp_dir, False, [os.path.join(self.source, 'skip') + '/'])
            finally:
                transfer_task_module.CloudPipelineApiProvider = saved_provider
        finally:
            os.environ['PATH'] = saved_path

        self.assertEqual('success', task.status, task.logger.messages)
        self.assertEqual('https://download/7/fsbrowser/1/task-1/data.tar.gz', task.result)
        with open(uploaded + '.args') as f:
            self.assertEqual('storage cp %s/task-1.tar.gz cp://transfer-storage/fsbrowser/1/task-1/data.tar.gz'
                             % tmp_dir, f.read().strip())
        with tarfile.open(uploaded) as tar:
            names = sorted(name[len(self.source.lstrip(os.sep)):] or '/' for name in tar.getnames())
        self.assertEqual(['/', '/c.log', '/keep', '/keep/a.txt'], names)
        self.assertEqual([], os.listdir(tmp_dir), 'the temporary archive was not removed')


if __name__ == '__main__':
    unittest.main()
