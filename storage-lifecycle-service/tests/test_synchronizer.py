# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import datetime
import os
import unittest

from sls.app.synchronizer import archiving_synchronizer_impl
from sls.app.synchronizer.archiving_synchronizer_impl import StorageLifecycleArchivingSynchronizer
from sls.cloud.model.cloud_object_model import CloudObject
from sls.app.model.config_model import SynchronizerConfig
from sls.pipelineapi.model.archive_rule_model import StorageLifecycleRule, StorageLifecycleRuleTransition, StorageLifecycleRuleExecution
from sls.util.logger import AppLogger
from tests.mock.cp_data_source_mock import MockCloudPipelineDataSource


class TestSynchronizerBuildsActionsForFiles(unittest.TestCase):

    synchronizer = StorageLifecycleArchivingSynchronizer(None, None, None, None)

    def test_build_actions_for_files_find_files_correctly_with_date_in_transition(self):
        folder = "/datastorage"
        now = datetime.datetime.now()
        rule = StorageLifecycleRule(
            1, 1, "/*",
            archiving_synchronizer_impl.METHOD_LATEST_FILE,
            transitions=[StorageLifecycleRuleTransition("GLACIER", transition_date=now.date())]
        )
        subject_files = [
            CloudObject(os.path.join(folder, "file1.txt"), now, None),
            CloudObject(os.path.join(folder, "file2.txt"), now - datetime.timedelta(days=1), None),
        ]
        actions = self.synchronizer._build_action_items_for_files(folder, subject_files, rule)
        self.assertEqual(
            len(actions.destination_transitions_queues["GLACIER"]),
            2
        )

    def test_build_actions_for_files_find_files_correctly_with_days_in_transition(self):
        folder = "/datastorage"
        now = datetime.datetime.now()
        rule = StorageLifecycleRule(
            1, 1, "/*",
            archiving_synchronizer_impl.METHOD_LATEST_FILE,
            transitions=[StorageLifecycleRuleTransition("GLACIER", transition_after_days=1)]
        )
        subject_files = [
            CloudObject(os.path.join(folder, "file1.txt"), now, None),
            CloudObject(os.path.join(folder, "file2.txt"), now - datetime.timedelta(days=1), None),
        ]
        actions = self.synchronizer._build_action_items_for_files(folder, subject_files, rule)
        self.assertEqual(
            len(actions.destination_transitions_queues["GLACIER"]),
            1
        )


class TestSynchronizerCheckRuleExecutionProgress(unittest.TestCase):

    folder = "/datastorage"
    now = datetime.datetime.now(datetime.timezone.utc)
    synchronizer = \
        StorageLifecycleArchivingSynchronizer(
            SynchronizerConfig(command="archive"), MockCloudPipelineDataSource(), None, AppLogger("archive"))

    def test_check_rule_execution_progress_still_running(self):
        subject_files = {
            "GLACIER":
            [
                CloudObject(os.path.join(self.folder, "file1.txt"), self.now - datetime.timedelta(days=3), "STANDARD"),
                CloudObject(os.path.join(self.folder, "file2.txt"), self.now - datetime.timedelta(days=3), "STANDARD")
            ]
        }
        transition = StorageLifecycleRuleTransition("GLACIER", transition_after_days=0)
        execution = StorageLifecycleRuleExecution(
            1, 1, archiving_synchronizer_impl.EXECUTION_RUNNING_STATUS, self.folder,
            "GLACIER", self.now
        )
        self.assertIsNone(self.synchronizer._check_rule_execution_progress(1, transition, subject_files, execution))

    def test_check_rule_execution_progress_running_overdue(self):
        subject_files = {
            "GLACIER":
            [
                CloudObject(os.path.join(self.folder, "file1.txt"),
                            self.now - datetime.timedelta(days=4), "STANDARD", 12),
                CloudObject(os.path.join(self.folder, "file2.txt"),
                            self.now - datetime.timedelta(days=4), "STANDARD", 16)
            ]
        }
        transition = StorageLifecycleRuleTransition("GLACIER", transition_after_days=0)
        execution = StorageLifecycleRuleExecution(
            1, 1, archiving_synchronizer_impl.EXECUTION_RUNNING_STATUS, self.folder,
            "GLACIER", self.now - datetime.timedelta(days=3)
        )
        updated_execution = self.synchronizer._check_rule_execution_progress(1, transition, subject_files, execution)
        self.assertEqual(archiving_synchronizer_impl.EXECUTION_FAILED_STATUS, updated_execution.status)

    def test_execution_should_succeed_because_files_are_small_for_glacier_ir_to_be_transferred(self):
        subject_files = {
            "GLACIER_IR":
                [
                    CloudObject(os.path.join(self.folder, "file1.txt"), self.now - datetime.timedelta(days=4),
                                "STANDARD", 15),
                    CloudObject(os.path.join(self.folder, "file2.txt"), self.now - datetime.timedelta(days=4),
                                "STANDARD", 13)
                ]
        }
        transition = StorageLifecycleRuleTransition("GLACIER_IR", transition_after_days=0)
        execution = StorageLifecycleRuleExecution(
            1, 1, archiving_synchronizer_impl.EXECUTION_RUNNING_STATUS, self.folder,
            "GLACIER_IR", self.now - datetime.timedelta(days=3)
        )
        updated_execution = self.synchronizer._check_rule_execution_progress(1, transition, subject_files, execution)
        self.assertEqual(archiving_synchronizer_impl.EXECUTION_SUCCESS_STATUS, updated_execution.status)

    def test_check_rule_execution_progress_running_should_succeed(self):
        subject_files = {"GLACIER": []}
        transition = StorageLifecycleRuleTransition("GLACIER", transition_after_days=0)
        execution = StorageLifecycleRuleExecution(
            1, 1, archiving_synchronizer_impl.EXECUTION_RUNNING_STATUS, self.folder,
            "GLACIER", self.now - datetime.timedelta(days=2)
        )
        updated_execution = self.synchronizer._check_rule_execution_progress(1, transition, subject_files, execution)
        self.assertEqual(archiving_synchronizer_impl.EXECUTION_SUCCESS_STATUS, updated_execution.status)

    def test_check_rule_execution_progress_running_should_succeed_if_new_file_appear_after_execution(self):
        subject_files = {
            "GLACIER":
            [
                CloudObject(os.path.join(self.folder, "file1.txt"), self.now, "STANDARD"),
                CloudObject(os.path.join(self.folder, "file2.txt"), self.now, "STANDARD")
            ]
        }
        transition = StorageLifecycleRuleTransition("GLACIER", transition_after_days=0)
        execution = StorageLifecycleRuleExecution(
            1, 1, archiving_synchronizer_impl.EXECUTION_RUNNING_STATUS, self.folder,
            "GLACIER", self.now - datetime.timedelta(days=2)
        )
        updated_execution = self.synchronizer._check_rule_execution_progress(1, transition, subject_files, execution)
        self.assertEqual(archiving_synchronizer_impl.EXECUTION_SUCCESS_STATUS, updated_execution.status)


if __name__ == '__main__':
    unittest.main()
