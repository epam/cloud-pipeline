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

# Covers how "sge create" and "sge configure" find the changes a user made to a grid engine profile in the editor
# (scripts/manage_sge_profiles.py). PATH points to an empty folder in every test, as in the tool images that have no
# diff command, e.g. Rocky Linux 8 and 9.

import os
import shutil
import tempfile
import unittest

from mock import Mock, patch

from scripts.manage_sge_profiles import GridEngineProfileManager, Profile

PROFILE_LINES = ['export CP_CAP_AUTOSCALE="true"\n',
                 'export CP_CAP_AUTOSCALE_WORKERS="3"\n',
                 'export CP_CAP_AUTOSCALE_HYBRID="false"\n']


class ProfileChangesTest(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        empty_path_dir = os.path.join(self.tmp_dir, 'bin')
        os.mkdir(empty_path_dir)
        self.path_patcher = patch.dict(os.environ, {'PATH': empty_path_dir})
        self.path_patcher.start()
        self.logger = Mock()
        self.manager = GridEngineProfileManager(executor=Mock(), logger=self.logger, logger_warning=Mock(),
                                                cap_scripts_dir=self.tmp_dir, queue_profile_regexp=None,
                                                autoscaling_script_path='autoscale_grid_engine.py')

    def tearDown(self):
        self.path_patcher.stop()
        shutil.rmtree(self.tmp_dir)

    def _write(self, name, lines):
        path = os.path.join(self.tmp_dir, name)
        with open(path, 'w') as f:
            f.write(''.join(lines))
        return path

    def _write_bytes(self, name, content):
        path = os.path.join(self.tmp_dir, name)
        with open(path, 'wb') as f:
            f.write(content)
        return path

    def _profile(self):
        path_autoscaling = self._write('sge_profile_test.q_autoscaling.sh', PROFILE_LINES)
        path_queue = self._write('sge_profile_test.q_queue.sh', ['source "{}"\n'.format(path_autoscaling)])
        return Profile(name='test.q', path_queue=path_queue, path_autoscaling=path_autoscaling)

    def _configure(self, profile, edit):
        def _editor(args):
            path = args[1]
            with open(path, 'r') as f:
                content = f.read()
            with open(path, 'w') as f:
                f.write(edit(content))
        with patch('scripts.manage_sge_profiles.subprocess.check_call', side_effect=_editor) as check_call:
            modified_profile = self.manager._configure_profile(profile, 'vi')
        check_call.assert_called_once()
        return modified_profile

    def _logged(self):
        return '\n'.join(call_args[0][0] for call_args in self.logger.info.call_args_list)

    def test_compare_file_returns_nothing_for_same_files(self):
        before_path = self._write('before', PROFILE_LINES)
        after_path = self._write('after', PROFILE_LINES)

        self.assertFalse(self.manager._compare_file(before_path, after_path))

    def test_compare_file_returns_changed_line_as_text_in_diff_format(self):
        before_path = self._write('before', PROFILE_LINES)
        after_path = self._write('after', [PROFILE_LINES[0], 'export CP_CAP_AUTOSCALE_WORKERS="5"\n',
                                           PROFILE_LINES[2]])

        changes = self.manager._compare_file(before_path, after_path)

        self.assertIsInstance(changes, str)
        self.assertEqual(['2c2',
                          '< export CP_CAP_AUTOSCALE_WORKERS="3"',
                          '---',
                          '> export CP_CAP_AUTOSCALE_WORKERS="5"'],
                         changes.split('\n'))

    def test_compare_file_returns_added_lines_in_diff_format(self):
        before_path = self._write('before', PROFILE_LINES)
        after_path = self._write('after', PROFILE_LINES[:1] + ['export CP_CAP_AUTOSCALE_HYBRID_FAMILY="c5"\n',
                                                               'export CP_CAP_AUTOSCALE_INSTANCE_DISK="45"\n']
                                 + PROFILE_LINES[1:])

        self.assertEqual(['1a2,3',
                          '> export CP_CAP_AUTOSCALE_HYBRID_FAMILY="c5"',
                          '> export CP_CAP_AUTOSCALE_INSTANCE_DISK="45"'],
                         self.manager._compare_file(before_path, after_path).split('\n'))

    def test_compare_file_returns_deleted_lines_in_diff_format(self):
        before_path = self._write('before', PROFILE_LINES)
        after_path = self._write('after', PROFILE_LINES[:1])

        self.assertEqual(['2,3d1',
                          '< export CP_CAP_AUTOSCALE_WORKERS="3"',
                          '< export CP_CAP_AUTOSCALE_HYBRID="false"'],
                         self.manager._compare_file(before_path, after_path).split('\n'))

    def test_compare_file_marks_last_lines_without_new_line_as_diff_does(self):
        before_path = self._write('before', ['export CP_CAP_AUTOSCALE="true"'])
        after_path = self._write('after', ['export CP_CAP_AUTOSCALE="nontrue"'])

        self.assertEqual(['1c1',
                          '< export CP_CAP_AUTOSCALE="true"',
                          '\\ No newline at end of file',
                          '---',
                          '> export CP_CAP_AUTOSCALE="nontrue"',
                          '\\ No newline at end of file'],
                         self.manager._compare_file(before_path, after_path).split('\n'))

    def test_compare_file_returns_removed_last_new_line(self):
        before_path = self._write('before', PROFILE_LINES)
        after_path = self._write('after', PROFILE_LINES[:2] + [PROFILE_LINES[2].rstrip('\n')])

        self.assertEqual(['3c3',
                          '< export CP_CAP_AUTOSCALE_HYBRID="false"',
                          '---',
                          '> export CP_CAP_AUTOSCALE_HYBRID="false"',
                          '\\ No newline at end of file'],
                         self.manager._compare_file(before_path, after_path).split('\n'))

    def test_compare_file_returns_changed_line_ending(self):
        before_path = self._write_bytes('before', b'export CP_CAP_AUTOSCALE="true"\n')
        after_path = self._write_bytes('after', b'export CP_CAP_AUTOSCALE="true"\r\n')

        self.assertEqual(['1c1',
                          '< export CP_CAP_AUTOSCALE="true"',
                          '---',
                          '> export CP_CAP_AUTOSCALE="true"\r'],
                         self.manager._compare_file(before_path, after_path).split('\n'))

    def test_compare_file_returns_changes_of_profile_with_bytes_that_are_not_utf8(self):
        before_path = self._write_bytes('before', b'# caf\xe9\nexport CP_CAP_AUTOSCALE="true"\n')
        after_path = self._write_bytes('after', b'# caf\xe9\nexport CP_CAP_AUTOSCALE="false"\n')

        self.assertEqual(['2c2',
                          '< export CP_CAP_AUTOSCALE="true"',
                          '---',
                          '> export CP_CAP_AUTOSCALE="false"'],
                         self.manager._compare_file(before_path, after_path).split('\n'))

    def test_compare_file_returns_changed_bytes_that_are_not_utf8(self):
        before_path = self._write_bytes('before', b'# caf\xe9\n')
        after_path = self._write_bytes('after', b'# caf\xe8\n')

        changes = self.manager._compare_file(before_path, after_path).split('\n')

        self.assertEqual(4, len(changes))
        self.assertEqual(['1c1', '---'], [changes[0], changes[2]])
        self.assertNotEqual(changes[1][2:], changes[3][2:])

    def test_configure_profile_returns_edited_profile_and_logs_the_changes(self):
        profile = self._profile()

        modified_profile = self._configure(profile, lambda content: content.replace('"3"', '"5"'))

        self.assertIsNotNone(modified_profile)
        with open(modified_profile.path_autoscaling, 'r') as f:
            self.assertIn('export CP_CAP_AUTOSCALE_WORKERS="5"\n', f.read())
        with open(profile.path_autoscaling, 'r') as f:
            self.assertEqual(''.join(PROFILE_LINES), f.read())
        logged = self._logged()
        self.assertIn('Grid engine test.q profile has been changed:', logged)
        self.assertIn('\n< export CP_CAP_AUTOSCALE_WORKERS="3"\n---\n> export CP_CAP_AUTOSCALE_WORKERS="5"', logged)

    def test_configure_profile_returns_nothing_for_unchanged_profile(self):
        profile = self._profile()

        modified_profile = self._configure(profile, lambda content: content)

        self.assertIsNone(modified_profile)
        self.assertIn('Grid engine test.q profile has not been changed.', self._logged())


if __name__ == '__main__':
    unittest.main()
