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
import unittest

import sls.app.synchronizer.storage_synchronizer_interface
from sls.app.synchronizer.archiving_synchronizer_impl import StorageLifecycleArchivingSynchronizer
from sls.pipelineapi.model.archive_rule_model import StorageLifecycleNotification, StorageLifecycleRuleExecution


class TestNotificationPositive(unittest.TestCase):

    enabled_notification = StorageLifecycleNotification(7, 7, [], True, "", "", False)
    completed_execution = StorageLifecycleRuleExecution(
        1, 1, sls.app.synchronizer.archiving_synchronizer_impl.EXECUTION_SUCCESS_STATUS, "/",
        "GLACIER", datetime.datetime.now())
    failed_execution = StorageLifecycleRuleExecution(
        1, 1, sls.app.synchronizer.archiving_synchronizer_impl.EXECUTION_FAILED_STATUS, "/",
        "GLACIER", datetime.datetime.now())

    today = datetime.datetime.now().date()

    def test_notification_will_be_sent_if_files_are_eligible_for_today_and_no_execution(self):
        self.assertTrue(
            StorageLifecycleArchivingSynchronizer._notification_should_be_sent(
                self.enabled_notification, None, self.today, self.today
            )
        )

    def test_notification_will_be_sent_if_files_are_eligible_for_week_ago_and_no_execution(self):
        self.assertTrue(
            StorageLifecycleArchivingSynchronizer._notification_should_be_sent(
                self.enabled_notification, None, self.today - datetime.timedelta(days=7), self.today
            )
        )

    def test_notification_will_be_sent_if_files_are_eligible_for_week_after_and_no_execution(self):
        self.assertTrue(
            StorageLifecycleArchivingSynchronizer._notification_should_be_sent(
                self.enabled_notification, None, self.today + datetime.timedelta(days=7), self.today
            )
        )

    def test_notification_will_be_sent_if_files_are_eligible_for_week_after_and_execution_with_completed_status(self):
        self.assertTrue(
            StorageLifecycleArchivingSynchronizer._notification_should_be_sent(
                self.enabled_notification, self.completed_execution, self.today + datetime.timedelta(days=7), self.today
            )
        )

    def test_notification_will_be_sent_if_files_are_eligible_for_week_after_and_execution_with_failed_status(self):
        self.assertFalse(
            StorageLifecycleArchivingSynchronizer._notification_should_be_sent(
                self.enabled_notification, self.failed_execution, self.today + datetime.timedelta(days=7), self.today
            )
        )


class TestNotificationNegative(unittest.TestCase):

    enabled_notification = StorageLifecycleNotification(7, 7, [], True, "", "", False)
    disabled_notification = StorageLifecycleNotification(None, None, None, False, None, None, False)
    running_execution = StorageLifecycleRuleExecution(
        1, 1, sls.app.synchronizer.archiving_synchronizer_impl.EXECUTION_RUNNING_STATUS, "/",
        "GLACIER", datetime.datetime.now())
    notification_execution = StorageLifecycleRuleExecution(
        1, 1, sls.app.synchronizer.archiving_synchronizer_impl.EXECUTION_NOTIFICATION_SENT_STATUS, "/",
        "GLACIER", datetime.datetime.now())
    today = datetime.datetime.now().date()

    def test_notification_wont_be_sent_if_files_are_eligible_for_today_and_no_execution_but_disabled(self):
        self.assertFalse(
            StorageLifecycleArchivingSynchronizer._notification_should_be_sent(
                self.disabled_notification, None, self.today, self.today
            )
        )

    def test_notification_wont_be_sent_if_files_are_eligible_for_today_and_execution_with_sent_status(self):
        self.assertFalse(
            StorageLifecycleArchivingSynchronizer._notification_should_be_sent(
                self.enabled_notification, self.notification_execution, self.today, self.today
            )
        )

    def test_notification_wont_be_sent_if_files_are_eligible_for_today_and_execution_with_running_status(self):
        self.assertFalse(
            StorageLifecycleArchivingSynchronizer._notification_should_be_sent(
                self.enabled_notification, self.running_execution, self.today, self.today
            )
        )

    def test_notification__wont_be_sent_if_files_are_eligible_for_two_week_after_and_no_execution(self):
        self.assertFalse(
            StorageLifecycleArchivingSynchronizer._notification_should_be_sent(
                self.enabled_notification, None, self.today + datetime.timedelta(days=14), self.today
            )
        )


if __name__ == '__main__':
    unittest.main()
