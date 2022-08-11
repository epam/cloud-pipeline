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

from slm.app import storage_synchronizer
from slm.app.storage_synchronizer import StorageLifecycleSynchronizer
from slm.model.cloud_object_model import CloudObject
from slm.model.config_model import SynchronizerConfig
from slm.model.rule_model import StorageLifecycleRule, StorageLifecycleRuleTransition, StorageLifecycleRuleExecution
from slm.util.logger import AppLogger
from tests.mock.cp_data_source_mock import MockCloudPipelineDataSource


class TestSynchronizerBuildsActionsForFiles(unittest.TestCase):

    synchronizer = StorageLifecycleSynchronizer(None, None, None, None)

    def test_build_actions_for_files_find_files_correctly_with_date_in_transition(self):
        folder = "/datastorage"
        now = datetime.datetime.now()
        rule = StorageLifecycleRule(
            1, 1, "/*",
            storage_synchronizer.METHOD_LATEST_FILE,
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
            storage_synchronizer.METHOD_LATEST_FILE,
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


class TestSynchronizerGetEligibleTransition(unittest.TestCase):

    folder = "/datastorage"
    now = datetime.datetime.now(datetime.timezone.utc)
    today = now.date()
    synchronizer = StorageLifecycleSynchronizer(None, None, None, None)

    def test_get_eligible_transition_when_no_execution(self):
        criterion_file = CloudObject(os.path.join(self.folder, "file2.txt"), self.now - datetime.timedelta(days=1), None)
        subject_files = [
            criterion_file,
            CloudObject(os.path.join(self.folder, "file1.txt"), self.now, None)
        ]
        transitions = [
            StorageLifecycleRuleTransition("GLACIER", transition_after_days=1),
            StorageLifecycleRuleTransition("DEEP_ARCHIVE", transition_after_days=3)
        ]
        transition_class, transition_date, transition_execution, trn_subject_file = \
            self.synchronizer._get_eligible_transition(criterion_file, transitions, [], subject_files, self.today)
        self.assertEqual(transition_class, "GLACIER")
        self.assertEqual(transition_date, self.today)
        self.assertEqual(transition_execution, None)
        self.assertEqual(len(trn_subject_file), len(subject_files))

    def test_get_eligible_transition_when_execution_notification(self):
        criterion_file = CloudObject(os.path.join(self.folder, "file2.txt"), self.now - datetime.timedelta(days=1), "STANDARD")
        subject_files = [
            criterion_file,
            CloudObject(os.path.join(self.folder, "file1.txt"), self.now, None)
        ]
        transitions = [
            StorageLifecycleRuleTransition("GLACIER", transition_after_days=1),
            StorageLifecycleRuleTransition("DEEP_ARCHIVE", transition_after_days=3)
        ]
        executions = [
            StorageLifecycleRuleExecution(1, 1, storage_synchronizer.EXECUTION_NOTIFICATION_SENT_STATUS, self.folder,
                                          "GLACIER", None)
        ]
        transition_class, transition_date, transition_execution, trn_subject_file = \
            self.synchronizer._get_eligible_transition(
                criterion_file, transitions,
                executions,
                subject_files, self.today)
        self.assertEqual("GLACIER", transition_class)
        self.assertEqual(self.today, transition_date)
        self.assertEqual(storage_synchronizer.EXECUTION_NOTIFICATION_SENT_STATUS, transition_execution.status)
        self.assertEqual(len(subject_files), len(trn_subject_file))

    def test_get_eligible_transition_when_execution_running(self):
        criterion_file = CloudObject(os.path.join(self.folder, "file2.txt"), self.now - datetime.timedelta(days=1),
                                     "STANDARD")
        subject_files = [
            criterion_file,
            CloudObject(os.path.join(self.folder, "file1.txt"), self.now, None)
        ]
        transitions = [
            StorageLifecycleRuleTransition("GLACIER", transition_after_days=1),
            StorageLifecycleRuleTransition("DEEP_ARCHIVE", transition_after_days=3)
        ]
        executions = [
            StorageLifecycleRuleExecution(1, 1, storage_synchronizer.EXECUTION_RUNNING_STATUS, self.folder,
                                          "GLACIER", None)
        ]
        transition_class, transition_date, transition_execution, trn_subject_file = \
            self.synchronizer._get_eligible_transition(
                criterion_file, transitions,
                executions,
                subject_files, self.today)
        self.assertEqual("GLACIER", transition_class)
        self.assertEqual(self.today, transition_date)
        self.assertEqual(storage_synchronizer.EXECUTION_RUNNING_STATUS, transition_execution.status)
        self.assertEqual(len(subject_files), len(trn_subject_file))

    def test_get_eligible_transition_when_execution_complete(self):
        criterion_file = CloudObject(os.path.join(self.folder, "file2.txt"), self.now - datetime.timedelta(days=2),
                                     "GLACIER")
        subject_files = [
            criterion_file,
            CloudObject(os.path.join(self.folder, "file1.txt"), self.now, "GLACIER")
        ]
        transitions = [
            StorageLifecycleRuleTransition("GLACIER", transition_after_days=1),
            StorageLifecycleRuleTransition("DEEP_ARCHIVE", transition_after_days=3)
        ]
        executions = [
            StorageLifecycleRuleExecution(1, 1, storage_synchronizer.EXECUTION_SUCCESS_STATUS, self.folder,
                                          "GLACIER", self.now - datetime.timedelta(days=1))
        ]
        transition_class, transition_date, transition_execution, trn_subject_file = \
            self.synchronizer._get_eligible_transition(
                criterion_file, transitions,
                executions,
                subject_files, self.today)
        self.assertEqual("DEEP_ARCHIVE", transition_class)
        self.assertEqual(criterion_file.creation_date.date() + datetime.timedelta(days=3), transition_date)
        self.assertEqual(None, transition_execution)
        self.assertEqual(len(subject_files), len(trn_subject_file))


class TestSynchronizerCheckRuleExecutionProgress(unittest.TestCase):

    folder = "/datastorage"
    now = datetime.datetime.now(datetime.timezone.utc)
    synchronizer = StorageLifecycleSynchronizer(SynchronizerConfig(), MockCloudPipelineDataSource(), None, AppLogger())

    def test_check_rule_execution_progress_still_running(self):
        subject_files = [
            CloudObject(os.path.join(self.folder, "file1.txt"), self.now, "STANDARD"),
            CloudObject(os.path.join(self.folder, "file2.txt"), self.now, "STANDARD")
        ]
        execution = StorageLifecycleRuleExecution(
            1, 1, storage_synchronizer.EXECUTION_RUNNING_STATUS, self.folder,
            "GLACIER", self.now
        )
        self.assertIsNone(self.synchronizer._check_rule_execution_progress(1, subject_files, execution))

    def test_check_rule_execution_progress_running_overdue(self):
        subject_files = [
            CloudObject(os.path.join(self.folder, "file1.txt"), self.now, "STANDARD"),
            CloudObject(os.path.join(self.folder, "file2.txt"), self.now, "STANDARD")
        ]
        execution = StorageLifecycleRuleExecution(
            1, 1, storage_synchronizer.EXECUTION_RUNNING_STATUS, self.folder,
            "GLACIER", self.now - datetime.timedelta(days=3)
        )
        updated_execution = self.synchronizer._check_rule_execution_progress(1, subject_files, execution)
        self.assertEqual(updated_execution.status, storage_synchronizer.EXECUTION_FAILED_STATUS)

    def test_check_rule_execution_progress_running_should_succeed(self):
        subject_files = [
            CloudObject(os.path.join(self.folder, "file1.txt"), self.now, "GLACIER"),
            CloudObject(os.path.join(self.folder, "file2.txt"), self.now, "GLACIER")
        ]
        execution = StorageLifecycleRuleExecution(
            1, 1, storage_synchronizer.EXECUTION_RUNNING_STATUS, self.folder,
            "GLACIER", self.now - datetime.timedelta(days=2)
        )
        updated_execution = self.synchronizer._check_rule_execution_progress(1, subject_files, execution)
        self.assertEqual(updated_execution.status, storage_synchronizer.EXECUTION_SUCCESS_STATUS)

if __name__ == '__main__':
    unittest.main()
