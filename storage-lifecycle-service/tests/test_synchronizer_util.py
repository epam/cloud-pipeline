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
import re
import unittest

from sls.app.synchronizer import archiving_synchronizer_impl
from sls.app.synchronizer.archiving_synchronizer_impl import StorageLifecycleArchivingSynchronizer
from sls.cloud.model.cloud_object_model import CloudObject
from sls.pipelineapi.model.archive_rule_model import StorageLifecycleRuleTransition, StorageLifecycleRuleProlongation, StorageLifecycleRule, \
    StorageLifecycleTransitionCriterion
from sls.util import path_utils


class TestIdentificationSubjectFolders(unittest.TestCase):

    def test_identify_root_folder_from_root_glob(self):
        glob = "/"
        files = [
            CloudObject("/file.txt", None, None),
            CloudObject("/dir1/file.txt", None, None),
            CloudObject("/dir2/file.txt", None, None),
            CloudObject("/dir1/subdir1/file.txt", None, None),
        ]
        self.assertEqual([glob], StorageLifecycleArchivingSynchronizer._identify_subject_folders(files, glob))

    def test_identify_datasets_from_root(self):
        glob = "/*"
        files = [
            CloudObject("/dataset1/file.txt", None, None),
            CloudObject("/dataset2/file.txt", None, None),
            CloudObject("/dataset3/subdir1/file.txt", None, None),
        ]
        self.assertEqual(
            sorted(["/dataset1", "/dataset2", "/dataset3"]),
            sorted(StorageLifecycleArchivingSynchronizer._identify_subject_folders(files, glob))
        )

    def test_identify_datasets_from_root_glob(self):
        glob = "/**"
        files = [
            CloudObject("/dataset1/file.txt", None, None),
            CloudObject("/dataset2/file.txt", None, None),
            CloudObject("/dataset3/subdir1/file.txt", None, None),
        ]
        self.assertEqual(
            sorted(["/dataset1", "/dataset2", "/dataset3", "/dataset3/subdir1"]),
            sorted(StorageLifecycleArchivingSynchronizer._identify_subject_folders(files, glob))
        )

    def test_identify_datasets_from_wide_root_glob_and_suffix(self):
        glob = "/**/machine*"
        files = [
            CloudObject("/machinerun1/file.txt", None, None),
            CloudObject("/dataset3/machinerun2/file.txt", None, None),
            CloudObject("/dataset3/subdir1/machinerun3/file.txt", None, None),
        ]
        self.assertEqual(
            sorted(["/dataset3/subdir1/machinerun3", "/dataset3/machinerun2", "/machinerun1"]),
            sorted(StorageLifecycleArchivingSynchronizer._identify_subject_folders(files, glob))
        )

    def test_identify_datasets_from_root_by_prefix(self):
        glob = "/data*"
        files = [
            CloudObject("/dataset1/file.txt", None, None),
            CloudObject("/dataset2/file.txt", None, None),
            CloudObject("/folder3/subdir1/file.txt", None, None),
        ]
        self.assertEqual(
            sorted(["/dataset1", "/dataset2"]),
            sorted(StorageLifecycleArchivingSynchronizer._identify_subject_folders(files, glob))
        )

    def test_identify_datasets_from_sub_folder_by_prefix(self):
        glob = "/*/data*"
        files = [
            CloudObject("/dir1/dataset1/file.txt", None, None),
            CloudObject("/dir2/dataset2/file.txt", None, None),
            CloudObject("/folder3/subdir1/file.txt", None, None),
        ]
        self.assertEqual(
            sorted(["/dir1/dataset1", "/dir2/dataset2"]),
            sorted(StorageLifecycleArchivingSynchronizer._identify_subject_folders(files, glob))
        )

    def test_identify_datasets_from_hierarchy_by_prefix(self):
        glob = "/**/data*"
        files = [
            CloudObject("/dir1/subdir1/dataset1/file.txt", None, None),
            CloudObject("/dir2/dataset2/file.txt", None, None),
            CloudObject("/folder3/subdir2/dataset3/file.txt", None, None),
            CloudObject("/folder3/subdir2/subsubdir3/dataset3/file.txt", None, None)

        ]
        self.assertEqual(
            sorted(
                [
                    "/dir1/subdir1/dataset1", "/dir2/dataset2",
                    "/folder3/subdir2/dataset3", "/folder3/subdir2/subsubdir3/dataset3"
                ]
            ),
            sorted(StorageLifecycleArchivingSynchronizer._identify_subject_folders(files, glob))
        )

    def test_identify_files_by_regexp(self):
        glob = "/dir/*.*"
        files = [
            CloudObject("/dir/subdir1/dataset1.txt", None, None),
            CloudObject("/dir/file.txt", None, None)

        ]
        rule_subject_files = [
            file.path for file in files
            if re.compile(path_utils.convert_glob_to_regexp(glob)).match(file.path)
        ]
        self.assertEqual(["/dir/file.txt"], rule_subject_files)

    def test_identify_files_by_regexp_2(self):
        glob = "/dir/*"
        files = [
            CloudObject("/dir/subdir1/dataset1.txt", None, None),
            CloudObject("/dir/file.txt", None, None)

        ]
        rule_subject_files = [
            file.path for file in files
            if re.compile(path_utils.convert_glob_to_regexp(glob)).match(file.path)
        ]
        self.assertEqual(["/dir/file.txt"], rule_subject_files)


class TestDefineEffectiveTransitions(unittest.TestCase):

    def test_effective_transition_with_days_is_the_same_if_no_prolongation(self):
        transition = StorageLifecycleRuleTransition("GLACIER", transition_after_days=5)
        empty_prolongation = StorageLifecycleRuleProlongation(prolongation_id=-1, prolonged_date=None, days=0, path=None)
        effective = StorageLifecycleArchivingSynchronizer._define_effective_transitions(empty_prolongation, [transition])[0]
        self.assertEqual(effective.transition_after_days, transition.transition_after_days)

    def test_effective_transition_with_date_is_the_same_if_no_prolongation(self):
        transition = StorageLifecycleRuleTransition("GLACIER", transition_date=datetime.datetime.now().date())
        empty_prolongation = StorageLifecycleRuleProlongation(prolongation_id=-1, prolonged_date=None, days=0, path=None)
        effective = StorageLifecycleArchivingSynchronizer._define_effective_transitions(empty_prolongation, [transition])[0]
        self.assertEqual(effective.transition_date, transition.transition_date)

    def test_effective_transition_with_days_is_calculated_right(self):
        transition = StorageLifecycleRuleTransition("GLACIER", transition_after_days=5)
        prolongation = StorageLifecycleRuleProlongation(prolongation_id=-1, prolonged_date=None, days=1, path=None)
        effective = StorageLifecycleArchivingSynchronizer._define_effective_transitions(prolongation, [transition])[0]
        self.assertEqual(effective.transition_after_days, transition.transition_after_days + 1)

    def test_effective_transition_with_date_is_calculated_right(self):
        transition = StorageLifecycleRuleTransition("GLACIER", transition_date=datetime.datetime.now().date())
        prolongation = StorageLifecycleRuleProlongation(prolongation_id=-1, prolonged_date=None, days=1, path=None)
        effective = StorageLifecycleArchivingSynchronizer._define_effective_transitions(prolongation, [transition])[0]
        self.assertEqual(effective.transition_date, transition.transition_date + datetime.timedelta(days=1))


class TestDefineCriterionFile(unittest.TestCase):

    def test_define_criterion_file_find_correctly_for_default_criterion_and_latest_method(self):
        rule = StorageLifecycleRule(1, 1, "/", archiving_synchronizer_impl.METHOD_LATEST_FILE)
        folder = "/datastorage"
        now = datetime.datetime.now()
        latest_file = CloudObject(os.path.join(folder, "file1.txt"), now, None)
        listing = [
            latest_file,
            CloudObject(os.path.join(folder, "file2.txt"), now - datetime.timedelta(days=1), None),
            CloudObject(os.path.join(folder, "file2.pdf"), now, None),
            CloudObject(os.path.join(folder, "file2.pdf"), now, None),
        ]
        subject_files = [
            latest_file,
            CloudObject(os.path.join(folder, "file2.txt"), now - datetime.timedelta(days=1), None),
        ]
        criterion_file = StorageLifecycleArchivingSynchronizer._define_criterion_file(rule, listing, folder, subject_files)
        self.assertEqual(criterion_file.path, latest_file.path)

    def test_define_criterion_file_find_correctly_for_default_criterion_and_earliest_method(self):
        rule = StorageLifecycleRule(1, 1, "/", archiving_synchronizer_impl.METHOD_EARLIEST_FILE)
        folder = "/datastorage"
        now = datetime.datetime.now()
        earliest_file = CloudObject(os.path.join(folder, "file1.txt"), now - datetime.timedelta(days=1), None)
        listing = [
            earliest_file,
            CloudObject(os.path.join(folder, "file2.txt"), now, None),
            CloudObject(os.path.join(folder, "file2.pdf"), now, None),
            CloudObject(os.path.join(folder, "file2.pdf"), now, None),
        ]
        subject_files = [
            earliest_file,
            CloudObject(os.path.join(folder, "file2.txt"), now, None),
        ]
        criterion_file = StorageLifecycleArchivingSynchronizer._define_criterion_file(rule, listing, folder, subject_files)
        self.assertEqual(criterion_file.path, earliest_file.path)

    def test_define_criterion_file_find_correctly_for_matching_file_criterion_and_latest_method(self):
        rule = StorageLifecycleRule(1, 1, "/", archiving_synchronizer_impl.METHOD_LATEST_FILE,
                                    transition_criterion=StorageLifecycleTransitionCriterion("MATCHING_FILES", "*.pdf"))
        folder = "/datastorage"
        now = datetime.datetime.now()
        latest_file = CloudObject(os.path.join(folder, "file2.pdf"), now, None)
        listing = [
            CloudObject(os.path.join(folder, "file1.txt"), now, None),
            CloudObject(os.path.join(folder, "file2.txt"), now, None),
            latest_file,
            CloudObject(os.path.join(folder, "file1.pdf"), now - datetime.timedelta(days=1), None),
        ]
        subject_files = [
            CloudObject(os.path.join(folder, "file1.txt"), now, None),
            CloudObject(os.path.join(folder, "file2.txt"), now - datetime.timedelta(days=1), None),
        ]
        criterion_file = StorageLifecycleArchivingSynchronizer._define_criterion_file(rule, listing, folder, subject_files)
        self.assertEqual(criterion_file.path, latest_file.path)

    def test_define_criterion_file_not_find_correctly_for_matching_file_criterion_and_latest_method(self):
        rule = StorageLifecycleRule(1, 1, "/", archiving_synchronizer_impl.METHOD_LATEST_FILE,
                                    transition_criterion=StorageLifecycleTransitionCriterion("MATCHING_FILES", "*.pdf"))
        folder = "/datastorage"
        now = datetime.datetime.now()
        listing = [
            CloudObject(os.path.join(folder, "file1.txt"), now, None),
            CloudObject(os.path.join(folder, "file2.txt"), now, None),
        ]
        subject_files = [
            CloudObject(os.path.join(folder, "file1.txt"), now, None),
            CloudObject(os.path.join(folder, "file2.txt"), now - datetime.timedelta(days=1), None),
        ]
        criterion_file = StorageLifecycleArchivingSynchronizer._define_criterion_file(rule, listing, folder, subject_files)
        self.assertIsNone(criterion_file, )


class TestCalculateTransitionDate(unittest.TestCase):

    def test_calculates_transition_date_with_days_in_transition(self):
        now = datetime.datetime.now()
        transition = StorageLifecycleRuleTransition("GLACIER", transition_after_days=5)
        file = CloudObject(os.path.join("/datastorage", "file1.txt"), now, None)
        self.assertEqual(
            StorageLifecycleArchivingSynchronizer._calculate_transition_date(file, transition),
            now.date() + datetime.timedelta(days=5)
        )

    def test_calculates_transition_date_with_date_in_transition(self):
        now = datetime.datetime.now()
        date_of_action = now.date() - datetime.timedelta(days=4)
        transition = StorageLifecycleRuleTransition("GLACIER", transition_date=date_of_action)
        file = CloudObject(os.path.join("/datastorage", "file1.txt"), now, None)
        self.assertEqual(
            StorageLifecycleArchivingSynchronizer._calculate_transition_date(file, transition),
            date_of_action
        )


if __name__ == '__main__':
    unittest.main()
