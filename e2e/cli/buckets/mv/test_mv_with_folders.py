# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

from common_utils.pipe_cli import *
from common_utils.test_utils import format_name
from utils.pipeline_utils import get_log_filename
from ..utils.assertions_utils import *
from ..utils.file_utils import *
from ..utils.utilities_for_test import *


class TestMoveWithFolders(object):

    raw_bucket_name = "mv-folders{}".format(get_test_prefix())
    bucket_name = format_name(raw_bucket_name)
    other_bucket_name = format_name("{}-other".format(raw_bucket_name))
    current_directory = os.getcwd()
    home_dir = "test_cp_home_dir-storage-16%s/" % get_test_prefix()
    checkout_dir = "mv-folders-checkout/"
    test_prefix = "%s-mv-folders-" % get_test_prefix()
    output_folder = test_prefix + TestFiles.TEST_FOLDER_FOR_OUTPUT
    test_file_1 = test_prefix + TestFiles.TEST_FILE1
    test_file_with_other_extension = test_prefix + TestFiles.TEST_FILE_WITH_OTHER_EXTENSION
    test_file_2 = test_prefix + TestFiles.TEST_FILE2
    test_file_3 = "cp-folders-" + TestFiles.TEST_FILE3
    test_folder = test_prefix + TestFiles.TEST_FOLDER
    test_folder_2 = test_prefix + TestFiles.TEST_FOLDER2
    source_dir = "mv-folders-sources%s/" % get_test_prefix()

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name, cls.other_bucket_name)
        # /test_folder
        create_test_folder(os.path.abspath(cls.source_dir + cls.test_folder))
        # /cp-files-test_folder_for_outputs
        create_test_folder(os.path.abspath(cls.output_folder))
        # ./test_file.txt
        create_test_file(os.path.abspath(cls.source_dir + cls.test_file_1), TestFiles.DEFAULT_CONTENT)
        # ./test_folder/test_file.txt
        create_test_file(os.path.abspath(cls.source_dir + cls.test_folder + cls.test_file_1), TestFiles.DEFAULT_CONTENT)
        # ./test_folder/test_file2.txt
        create_test_file(os.path.abspath(cls.test_folder + cls.test_file_2), TestFiles.DEFAULT_CONTENT)
        # ./test_folder/test_file3.txt
        create_test_file(os.path.abspath(cls.test_folder + cls.test_file_3), TestFiles.DEFAULT_CONTENT)
        # ./test_folder/test_file.json
        create_test_file(os.path.abspath(cls.source_dir + cls.test_folder + cls.test_file_with_other_extension),
                         TestFiles.DEFAULT_CONTENT)
        # ./test_folder/other/test_file.txt
        create_test_file(os.path.abspath(cls.source_dir + cls.test_folder + cls.test_folder_2 + cls.test_file_1),
                         TestFiles.DEFAULT_CONTENT)
        # ./test_file2.txt
        create_test_file(os.path.abspath(cls.source_dir + cls.test_file_2), TestFiles.COPY_CONTENT)
        # ./test_file3.txt
        create_test_file(os.path.abspath(cls.source_dir + cls.test_file_3), TestFiles.COPY_CONTENT)
        # ~/test_cp_home_dir/test_file.txt
        create_test_file(os.path.join(os.path.expanduser('~'), cls.source_dir + cls.home_dir, cls.test_file_1),
                         TestFiles.DEFAULT_CONTENT)
        # ~/test_cp_home_dir/test_file2.txt
        create_test_file(os.path.join(os.path.expanduser('~'), cls.home_dir, cls.test_file_2),
                         TestFiles.DEFAULT_CONTENT)
        # ~/test_cp_home_dir/other/test_file.txt
        create_test_file(os.path.join(os.path.expanduser('~'), cls.source_dir + cls.home_dir, cls.test_folder_2,
                                      cls.test_file_1), TestFiles.DEFAULT_CONTENT)

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name, cls.other_bucket_name)
        clean_test_data(os.path.abspath(cls.source_dir))
        clean_test_data(os.path.join(os.path.expanduser('~'), cls.output_folder))
        clean_test_data(os.path.join(os.path.expanduser('~'), cls.source_dir))
        clean_test_data(os.path.join(os.path.expanduser('~'), cls.home_dir))
        clean_test_data(os.path.abspath(cls.checkout_dir))
        clean_test_data(os.path.abspath(cls.output_folder))
        clean_test_data(os.path.abspath(cls.test_folder))

    """
        1. test case
        2. source path
        3. with --force option
        4. path to directory if need to switch current directory
    """
    test_case_for_upload_folders = [
        ("TC-PIPE-STORAGE-12", "", os.path.abspath(test_folder), False, None),
        ("TC-PIPE-STORAGE-16", "", "~/" + home_dir, None, None),
        ("TC-PIPE-STORAGE-18", "", "./" + test_folder, False, None),
        ("TC-PIPE-STORAGE-18", "-1", "./", False, True),
        ("TC-PIPE-STORAGE-18", "-2", ".", False, True),
        ("TC-PIPE-STORAGE-20", "", os.path.abspath(test_folder) + "/", True, None),
    ]

    @pytest.mark.run(order=1)
    @pytest.mark.parametrize("test_case,suffix,source,force,switch_dir", test_case_for_upload_folders)
    def test_folder_should_be_uploaded(self, test_case, suffix, source, force, switch_dir):
        case = test_case + suffix
        destination = "cp://{}/{}/".format(self.bucket_name, case)
        if force:
            pipe_storage_cp(os.path.abspath(self.source_dir + self.test_file_2), destination + self.test_file_1,
                            expected_status=0)
            pipe_storage_cp(os.path.abspath(self.source_dir + self.test_file_2), destination + self.test_file_2,
                            expected_status=0)
            pipe_storage_cp(os.path.abspath(self.source_dir + self.test_file_2),
                            destination + self.test_folder + self.test_file_1,
                            expected_status=0)
        if source.startswith("~"):
            source_for_checks = os.path.join(os.path.expanduser('~'), source.strip("~/"))
        else:
            source_for_checks = source
        if switch_dir:
            dir_path = os.path.abspath(self.checkout_dir)
            create_test_files(TestFiles.DEFAULT_CONTENT,
                              os.path.join(dir_path, self.test_file_1),
                              os.path.join(dir_path, self.test_file_2),
                              os.path.join(dir_path, self.test_folder, self.test_file_1))
            assert os.path.exists(os.path.join(dir_path, self.test_file_1))
            assert os.path.exists(os.path.join(dir_path, self.test_folder, self.test_file_1))
            source_for_checks = dir_path
            os.chdir(dir_path)
        else:
            create_test_file(os.path.join(source_for_checks, self.test_file_1), TestFiles.DEFAULT_CONTENT)
            create_test_file(os.path.join(source_for_checks, self.test_file_2), TestFiles.DEFAULT_CONTENT)
            create_test_file(os.path.join(source_for_checks, self.test_folder, self.test_file_1),
                             TestFiles.DEFAULT_CONTENT)
        source_file_object = ObjectInfo(True).build(os.path.join(source_for_checks, self.test_file_1))
        source_folder_file_object = ObjectInfo(True).build(os.path.join(source_for_checks, self.test_folder,
                                                                        self.test_file_1))
        logging.info("Ready to perform mv operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, force=force, recursive=True, expected_status=0)
        assert_copied_object_info(source_file_object,
                                  ObjectInfo(False).build(self.bucket_name, os.path.join(case, self.test_file_1)),
                                  case)
        assert_copied_object_info(source_file_object,
                                  ObjectInfo(False).build(self.bucket_name, os.path.join(case, self.test_file_2)),
                                  case)
        assert_copied_object_info(source_folder_file_object,
                                  ObjectInfo(False).build(self.bucket_name, os.path.join(
                                      case, self.test_folder + self.test_file_1)), case)
        assert_files_deleted(None, os.path.join(source_for_checks, self.test_file_1))
        assert_files_deleted(None, os.path.join(source_for_checks, self.test_file_2))
        assert_files_deleted(None, os.path.join(source_for_checks, self.test_folder, self.test_file_1))
        os.chdir(self.current_directory)

    """
        1. test case
        2. source path
        3. path to directory if need to switch current directory
        4. relative path to file to rewrite (with --force option)
    """
    test_case_for_download_folders = [
        ("TC-PIPE-STORAGE-12", "", os.path.abspath(output_folder + "TC-PIPE-STORAGE-12") + "/", None, None),
        ("TC-PIPE-STORAGE-16", "", "~/" + output_folder + "TC-PIPE-STORAGE-16/", None, None),
        ("TC-PIPE-STORAGE-18", "", "./" + output_folder + "TC-PIPE-STORAGE-18/", None, None),
        ("TC-PIPE-STORAGE-18", "-1", "./", None, True),
        ("TC-PIPE-STORAGE-18", "-2", ".", None, True),
        ("TC-PIPE-STORAGE-20", "", os.path.abspath(output_folder + "TC-PIPE-STORAGE-20") + "/", True, None),
    ]

    @pytest.mark.run(order=2)
    @pytest.mark.parametrize("test_case,suffix,destination,force,switch_dir", test_case_for_download_folders)
    def test_folder_should_be_downloaded(self, test_case, suffix, destination, force, switch_dir):
        case = test_case + suffix
        source = "cp://{}/{}/".format(self.bucket_name, case)
        if force:
            create_test_file(destination + self.test_file_1, TestFiles.COPY_CONTENT)
            create_test_file(destination + self.test_file_2, TestFiles.COPY_CONTENT)
            create_test_file(destination + self.test_folder + self.test_file_1, TestFiles.COPY_CONTENT)
        if destination.startswith("~"):
            destination_for_checks = os.path.join(os.path.expanduser('~'), destination.strip("~/"))
        else:
            destination_for_checks = destination
        if switch_dir:
            dir_path = os.path.abspath(os.path.join(destination, self.checkout_dir, case))
            create_test_folder(dir_path)
            os.chdir(dir_path)
        source_file_object = ObjectInfo(False).build(self.bucket_name, os.path.join(case, self.test_file_1))
        source_folder_file_object = ObjectInfo(False).build(self.bucket_name, os.path.join(
            case, self.test_folder + self.test_file_1))
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, force=force, recursive=True)
        assert_copied_object_info(source_file_object,
                                  ObjectInfo(True).build(os.path.join(destination_for_checks, self.test_file_1)),
                                  case)
        assert_copied_object_info(source_file_object,
                                  ObjectInfo(True).build(os.path.join(destination_for_checks, self.test_file_2)),
                                  case)
        assert_copied_object_info(source_folder_file_object,
                                  ObjectInfo(True).build(os.path.join(destination_for_checks, self.test_folder,
                                                                      self.test_file_1)), case)
        assert_files_deleted(self.bucket_name, os.path.join(case, self.test_file_1))
        assert_files_deleted(self.bucket_name, os.path.join(case, self.test_file_2))
        assert_files_deleted(self.bucket_name, os.path.join(case, self.test_folder + self.test_file_1))
        os.chdir(self.current_directory)

    """
        1. test case
        2. --force option
    """
    test_case_for_copy_between_buckets_folders = [
        ("TC-PIPE-STORAGE-12", False),
        ("TC-PIPE-STORAGE-20", True),
    ]

    @pytest.mark.run(order=3)
    @pytest.mark.parametrize("test_case,force", test_case_for_copy_between_buckets_folders)
    def test_folder_should_be_copied(self, test_case, force):
        source = "cp://{}/{}/".format(self.bucket_name, test_case)
        destination = "cp://{}/{}/".format(self.other_bucket_name, test_case)
        pipe_storage_cp(self.source_dir + self.test_file_1, source + self.test_file_1, expected_status=0)
        pipe_storage_cp(self.source_dir + self.test_file_2, source + self.test_file_2, expected_status=0)
        pipe_storage_cp(self.source_dir + self.test_folder + self.test_file_1, source + self.test_folder +
                        self.test_file_1, expected_status=0)
        source_file_object_1 = ObjectInfo(False).build(self.bucket_name, os.path.join(test_case, self.test_file_1))
        source_file_object_2 = ObjectInfo(False).build(self.bucket_name, os.path.join(test_case, self.test_file_2))
        source_folder_file_object = ObjectInfo(False).build(self.bucket_name, os.path.join(
            test_case, self.test_folder, self.test_file_1))
        if force:
            pipe_storage_cp(self.source_dir + self.test_file_1, destination + self.test_file_1,
                            expected_status=0)
            pipe_storage_cp(self.source_dir + self.test_file_2, destination + self.test_file_2,
                            expected_status=0)
            pipe_storage_cp(self.source_dir + self.test_file_2,
                            destination + self.test_folder + self.test_file_1, expected_status=0)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, force=force, recursive=True, expected_status=0)
        assert_copied_object_info(source_file_object_1,
                                  ObjectInfo(False).build(self.other_bucket_name,
                                                          os.path.join(test_case, self.test_file_1)), test_case)
        assert_copied_object_info(source_file_object_2,
                                  ObjectInfo(False).build(self.other_bucket_name,
                                                          os.path.join(test_case, self.test_file_2)), test_case)

        assert_copied_object_info(source_folder_file_object,
                                  ObjectInfo(False).build(self.other_bucket_name, os.path.join(
                                      test_case, self.test_folder, self.test_file_1)), test_case)
        assert_files_deleted(self.bucket_name, self.source_dir + self.test_file_1)
        assert_files_deleted(self.bucket_name, self.source_dir + self.test_file_2)
        assert_files_deleted(self.bucket_name, self.source_dir + self.test_folder + self.test_file_1)

    @pytest.mark.run(order=1)
    def test_excluded_files_should_be_uploaded(self):
        """TC-PIPE-STORAGE-26"""
        case = "TC-PIPE-STORAGE-26-1"
        source = os.path.abspath(self.test_folder)
        source_test_file_1 = os.path.join(source, self.test_file_1)
        source_test_file_2 = os.path.join(source, self.test_file_with_other_extension)
        source_test_folder_file = os.path.join(source, self.test_folder, self.test_file_1)
        destination = "cp://{}/{}/".format(self.bucket_name, case)
        create_test_files(TestFiles.DEFAULT_CONTENT, source_test_file_1, source_test_file_2, source_test_folder_file)
        source_object = ObjectInfo(True).build(source_test_file_1)
        logging.info("Ready to perform mv operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, recursive=True, exclude=["*json", "{}*".format(self.test_folder)],
                        expected_status=0)
        assert_copied_object_info(source_object, ObjectInfo(False).build(self.bucket_name,
                                                                         os.path.join(case, self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name, os.path.join(
            case, self.test_folder, self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name, os.path.join(
            case, self.test_file_with_other_extension)), case)
        assert_files_skipped(None, source_test_folder_file)
        assert_files_skipped(None, source_test_file_2)

    @pytest.mark.run(order=2)
    def test_excluded_files_should_be_downloaded(self):
        """TC-PIPE-STORAGE-26"""
        case = "TC-PIPE-STORAGE-26-2"
        source = "cp://{}/{}/".format(self.bucket_name, case)
        key_file_1 = os.path.join(case, self.test_file_1)
        key_file_2 = os.path.join(case, self.test_file_with_other_extension)
        key_folder_file = os.path.join(case, self.test_folder, self.test_file_1)
        create_test_files_on_bucket(self.source_dir + self.test_file_1, self.bucket_name, key_file_1,
                                    key_file_2, key_folder_file)
        destination = os.path.abspath(os.path.join(self.output_folder, case))
        source_object = ObjectInfo(False).build(self.bucket_name, key_file_1)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, recursive=True, exclude=["*json", "{}*".format(self.test_folder)],
                        expected_status=0)
        assert_copied_object_info(source_object, ObjectInfo(True).build(os.path.join(destination, self.test_file_1)),
                                  case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_folder,
                                                                                self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(
            destination, self.test_file_with_other_extension)), case)
        assert_files_skipped(self.bucket_name, key_file_2)
        assert_files_skipped(self.bucket_name, key_folder_file)

    @pytest.mark.run(order=3)
    def test_excluded_files_should_be_copied(self):
        """TC-PIPE-STORAGE-26"""
        case = "TC-PIPE-STORAGE-26-3"
        key_file_1 = os.path.join(case, self.test_file_1)
        key_file_2 = os.path.join(case, self.test_file_with_other_extension)
        key_folder_file = os.path.join(case, self.test_folder, self.test_file_1)
        source = "cp://{}/{}/".format(self.bucket_name, case)
        destination = "cp://{}/{}/".format(self.other_bucket_name, case)
        create_test_files_on_bucket(self.source_dir + self.test_file_1, self.bucket_name, key_file_1,
                                    key_folder_file, key_file_2)
        source_object = ObjectInfo(False).build(self.bucket_name, key_file_1)
        logging.info("Ready to perform mv operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, recursive=True, exclude=["*json", "{}*".format(self.test_folder)],
                        expected_status=0)
        assert_copied_object_info(source_object, ObjectInfo(False).build(self.other_bucket_name, key_file_1), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name, key_folder_file), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name, key_file_2), case)
        assert_files_skipped(self.bucket_name, key_folder_file)
        assert_files_skipped(self.bucket_name, key_file_2)

    @pytest.mark.run(order=1)
    def test_included_files_should_be_uploaded(self):
        """TC-PIPE-STORAGE-28"""
        case = "TC-PIPE-STORAGE-28-1"
        source = os.path.abspath(self.test_folder)
        source_test_file_1 = os.path.join(source, self.test_file_1)
        source_test_file_2 = os.path.join(source, self.test_file_with_other_extension)
        source_test_folder_file = os.path.join(source, self.test_folder, self.test_file_1)
        destination = "cp://{}/{}/".format(self.bucket_name, case)
        create_test_files(TestFiles.DEFAULT_CONTENT, source_test_file_1, source_test_file_2, source_test_folder_file)
        source_object = ObjectInfo(True).build(source_test_file_2)
        logging.info("Ready to perform mv operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, recursive=True, include=["*json"], expected_status=0)
        assert_copied_object_info(source_object, ObjectInfo(False).build(
            self.bucket_name, os.path.join(case, self.test_file_with_other_extension)), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name, os.path.join(
            case, self.test_folder, self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name, os.path.join(
            case, self.test_file_1)), case)
        assert_files_skipped(None, source_test_file_1)
        assert_files_skipped(None, source_test_folder_file)

    @pytest.mark.run(order=2)
    def test_included_files_should_be_downloaded(self):
        """TC-PIPE-STORAGE-28"""
        case = "TC-PIPE-STORAGE-28-2"
        source = "cp://{}/{}/".format(self.bucket_name, case)
        key_file_1 = os.path.join(case, self.test_file_1)
        key_file_2 = os.path.join(case, self.test_file_with_other_extension)
        key_folder_file = os.path.join(case, self.test_folder, self.test_file_1)
        create_test_files_on_bucket(self.source_dir + self.test_file_1, self.bucket_name, key_file_1,
                                    key_file_2, key_folder_file)
        destination = os.path.abspath(os.path.join(self.output_folder, case))
        source_object = ObjectInfo(False).build(self.bucket_name, key_file_2)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, recursive=True, include=["*json"], expected_status=0)
        assert_copied_object_info(source_object, ObjectInfo(True).build(os.path.join(
            destination, self.test_file_with_other_extension)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_folder,
                                                                                self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(
            destination, self.test_file_1)), case)
        assert_files_skipped(self.bucket_name, key_file_1)
        assert_files_skipped(self.bucket_name, key_folder_file)

    @pytest.mark.run(order=3)
    def test_excluded_files_should_be_copied(self):
        """TC-PIPE-STORAGE-28"""
        case = "TC-PIPE-STORAGE-28-3"
        key_file_1 = os.path.join(case, self.test_file_1)
        key_file_2 = os.path.join(case, self.test_file_with_other_extension)
        key_folder_file = os.path.join(case, self.test_folder, self.test_file_1)
        source = "cp://{}/{}/".format(self.bucket_name, case)
        destination = "cp://{}/{}/".format(self.other_bucket_name, case)
        create_test_files_on_bucket(self.source_dir + self.test_file_1, self.bucket_name, key_file_1,
                                    key_folder_file, key_file_2)
        source_object = ObjectInfo(False).build(self.bucket_name, key_file_2)
        logging.info("Ready to perform mv operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, recursive=True, include=["*json"], expected_status=0)
        assert_copied_object_info(source_object, ObjectInfo(False).build(self.other_bucket_name, key_file_2), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name, key_folder_file), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name, key_file_1), case)
        assert_files_skipped(self.bucket_name, key_folder_file)
        assert_files_skipped(self.bucket_name, key_file_1)

    @pytest.mark.run(order=1)
    def test_included_excluded_files_should_be_uploaded(self):
        """TC-PIPE-STORAGE-32"""
        case = "TC-PIPE-STORAGE-32-1"
        source = os.path.abspath(self.test_folder) + "/"
        source_test_file_1 = os.path.join(source, self.test_file_1)
        source_test_file_2 = os.path.join(source, self.test_file_with_other_extension)
        source_test_folder_file = os.path.join(source, self.test_folder, self.test_file_1)
        destination = "cp://{}/{}/".format(self.bucket_name, case)
        create_test_files(TestFiles.DEFAULT_CONTENT, source_test_file_1, source_test_file_2, source_test_folder_file)
        source_object = ObjectInfo(True).build(source_test_file_1)
        logging.info("Ready to perform mv operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, recursive=True, include=["*txt"],
                        exclude=["{}*".format(self.test_folder)], expected_status=0)
        assert_copied_object_info(source_object, ObjectInfo(False).build(
            self.bucket_name, os.path.join(case, self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name, os.path.join(
            case, self.test_folder, self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name, os.path.join(
            case, self.test_file_with_other_extension)), case)
        assert_files_skipped(None, source_test_file_2)
        assert_files_skipped(None, source_test_folder_file)

    @pytest.mark.run(order=2)
    def test_included_files_should_be_downloaded(self):
        """TC-PIPE-STORAGE-32"""
        case = "TC-PIPE-STORAGE-32-2"
        source = "cp://{}/{}/".format(self.bucket_name, case)
        key_file_1 = os.path.join(case, self.test_file_1)
        key_file_2 = os.path.join(case, self.test_file_with_other_extension)
        key_folder_file = os.path.join(case, self.test_folder, self.test_file_1)
        create_test_files_on_bucket(self.source_dir + self.test_file_1, self.bucket_name, key_file_1,
                                    key_file_2, key_folder_file)
        destination = os.path.abspath(os.path.join(self.output_folder, case))
        source_object = ObjectInfo(False).build(self.bucket_name, key_file_1)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, recursive=True, include=["*txt"],
                        exclude=["{}*".format(self.test_folder)], expected_status=0)
        assert_copied_object_info(source_object, ObjectInfo(True).build(os.path.join(
            destination, self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_folder,
                                                                                self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(
            destination, self.test_file_with_other_extension)), case)
        assert_files_skipped(self.bucket_name, key_file_2)
        assert_files_skipped(self.bucket_name, key_folder_file)

    @pytest.mark.run(order=3)
    def test_excluded_files_should_be_copied(self):
        """TC-PIPE-STORAGE-32"""
        case = "TC-PIPE-STORAGE-32-3"
        key_file_1 = os.path.join(case, self.test_file_1)
        key_file_2 = os.path.join(case, self.test_file_with_other_extension)
        key_folder_file = os.path.join(case, self.test_folder, self.test_file_1)
        source = "cp://{}/{}/".format(self.bucket_name, case)
        destination = "cp://{}/{}/".format(self.other_bucket_name, case)
        create_test_files_on_bucket(self.source_dir + self.test_file_1, self.bucket_name, key_file_1,
                                    key_folder_file, key_file_2)
        source_object = ObjectInfo(False).build(self.bucket_name, key_file_1)
        logging.info("Ready to perform mv operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, recursive=True, include=["*txt"],
                        exclude=["{}*".format(self.test_folder)], expected_status=0)
        assert_copied_object_info(source_object, ObjectInfo(False).build(self.other_bucket_name, key_file_1), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name, key_folder_file), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name, key_file_2), case)
        assert_files_skipped(self.bucket_name, key_folder_file)
        assert_files_skipped(self.bucket_name, key_file_2)

    @pytest.mark.run(order=4)
    def test_upload_without_recursive(self):
        """TC-PIPE-STORAGE-14"""
        case = "TC-PIPE-STORAGE-14"
        source = os.path.abspath(self.source_dir + self.test_folder)
        destination = "cp://{}/".format(os.path.join(self.bucket_name, case))
        error_text = pipe_storage_mv(source, destination, expected_status=1)[1]
        assert_error_message_is_present(error_text, "Flag --recursive (-r) is required to copy folders.")
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name,
                                                                    os.path.join(case, self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name,
                                                                    os.path.join(case, self.test_folder_2,
                                                                                 self.test_file_1)), case)
        assert_files_skipped(None, os.path.join(source, self.test_file_1))
        assert_files_skipped(None, os.path.join(source, self.test_folder_2, self.test_file_1))

    @pytest.mark.run(order=5)
    def test_download_without_recursive(self):
        """TC-PIPE-STORAGE-14"""
        case = "TC-PIPE-STORAGE-14"
        source = "cp://{}/".format(os.path.join(self.bucket_name, case))
        create_test_files_on_bucket(self.source_dir + self.test_file_1, self.bucket_name,
                                    os.path.join(case, self.test_file_1),
                                    os.path.join(case, self.test_folder, self.test_file_1))
        destination = os.path.abspath(self.output_folder + case) + "/"
        error_text = pipe_storage_mv(source, destination, expected_status=1)[1]
        assert_error_message_is_present(error_text, "Flag --recursive (-r) is required to copy folders.")
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_folder,
                                                                                self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_file_1)), case)
        assert_files_skipped(self.bucket_name, os.path.join(case, self.test_file_1))
        assert_files_skipped(self.bucket_name, os.path.join(case, self.test_folder, self.test_file_1))

    @pytest.mark.run(order=6)
    def test_copy_without_recursive(self):
        """TC-PIPE-STORAGE-14"""
        case = "TC-PIPE-STORAGE-14"
        source = "cp://{}/".format(os.path.join(self.bucket_name, case))
        destination = "cp://{}/".format(os.path.join(self.other_bucket_name, case))
        error_text = pipe_storage_mv(source, destination, expected_status=1)[1]
        assert_error_message_is_present(error_text, "Flag --recursive (-r) is required to copy folders.")
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_folder,
                                                                                self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_file_1)),
                                            case)
        assert_files_skipped(self.bucket_name, os.path.join(case, self.test_file_1))
        assert_files_skipped(self.bucket_name, os.path.join(case, self.test_folder, self.test_file_1))

    @pytest.mark.run(order=6)
    def test_upload_file_to_bucket_with_folder_with_same_name(self):
        """-PIPE-STORAGE-76"""
        case = "TC-PIPE-STORAGE-76"
        source = os.path.abspath(os.path.join(self.source_dir, self.test_folder, case))
        try:
            create_test_folder(source)
            source = os.path.join(source, self.test_file_1)
            create_test_file(source, TestFiles.DEFAULT_CONTENT)
            pipe_storage_cp(source, "cp://%s/%s/%s/" % (self.bucket_name, case, self.test_file_1))
            assert object_exists(self.bucket_name, "%s/%s/%s" % (case, self.test_file_1, self.test_file_1))
            source_for_check = ObjectInfo(True).build(source)
            pipe_storage_mv(source, "cp://%s/%s/" % (self.bucket_name, case))
            assert_copied_object_info(source_for_check, ObjectInfo(False)
                                      .build(self.bucket_name, os.path.join(case, self.test_file_1)), case)
            assert_files_deleted(None, source)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_folder_structure(self):
        """TC-PIPE-STORAGE-77"""
        case = "TC-PIPE-STORAGE-77"
        source = os.path.abspath(os.path.join(self.source_dir, self.test_folder, case))
        try:
            create_test_folder(source)
            create_test_folder(os.path.join(source, "folder1"))
            source1 = os.path.join(os.path.join(source, "folder1"), self.test_file_1)
            create_test_file(source1, TestFiles.DEFAULT_CONTENT)

            create_test_folder(os.path.join(source, "folder2"))
            source2 = os.path.join(os.path.join(source, "folder2"), self.test_file_2)
            create_test_file(source2, TestFiles.DEFAULT_CONTENT)

            source_for_check1 = ObjectInfo(True).build(source1)
            source_for_check2 = ObjectInfo(True).build(source2)

            pipe_storage_mv(source, "cp://%s/%s/" % (self.bucket_name, case), recursive=True)
            assert_copied_object_info(source_for_check1, ObjectInfo(False)
                                      .build(self.bucket_name, os.path.join(case, "folder1", self.test_file_1)), case)
            assert_files_deleted(None, source1)
            assert_copied_object_info(source_for_check2, ObjectInfo(False)
                                      .build(self.bucket_name, os.path.join(case, "folder2", self.test_file_2)), case)
            assert_files_deleted(None, source2)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_download_folder_structure(self):
        """TC-PIPE-STORAGE-79"""
        case = "TC-PIPE-STORAGE-79"
        source = os.path.abspath(os.path.join(self.source_dir, self.test_folder, case))
        try:
            create_test_folder(source)
            source1 = os.path.join(source, self.test_file_1)
            create_test_file(source1, TestFiles.DEFAULT_CONTENT)
            pipe_storage_cp(source1, "cp://%s/%s/%s/" % (self.bucket_name, case, "folder1"))
            assert object_exists(self.bucket_name, "%s/%s/%s" % (case, "folder1", self.test_file_1))
            source_for_check1 = ObjectInfo(True).build(source1)

            source2 = os.path.join(source, self.test_file_2)
            create_test_file(source2, TestFiles.COPY_CONTENT)
            pipe_storage_cp(source2, "cp://%s/%s/%s/" % (self.bucket_name, case, "folder2"))
            assert object_exists(self.bucket_name, "%s/%s/%s" % (case, "folder2", self.test_file_2))
            source_for_check2 = ObjectInfo(True).build(source2)

            destination = os.path.join(source, "output") + "/"
            pipe_storage_mv("cp://%s/%s/" % (self.bucket_name, case), destination, recursive=True)
            assert_copied_object_info(source_for_check1, ObjectInfo(True)
                                      .build(os.path.join(destination, "folder1", self.test_file_1)), case)
            assert not object_exists(self.bucket_name, "%s/%s/%s" % (case, "folder1", self.test_file_1))
            assert_copied_object_info(source_for_check2, ObjectInfo(True)
                                      .build(os.path.join(destination, "folder2", self.test_file_2)), case)
            assert not object_exists(self.bucket_name, "%s/%s/%s" % (case, "folder2", self.test_file_2))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_folders_with_similar_keys(self):
        """TC-PIPE-STORAGE-82"""
        case = "TC-PIPE-STORAGE-82"
        source_folder = os.path.abspath(os.path.join(self.test_folder, case))
        test_folder1 = "folder"
        test_folder2 = "folder2"
        try:
            create_test_folder(source_folder)
            create_test_folder(os.path.join(source_folder, test_folder1))
            create_test_folder(os.path.join(source_folder, test_folder2))
            create_test_file(os.path.join(source_folder, test_folder1, self.test_file_1), TestFiles.DEFAULT_CONTENT)
            create_test_file(os.path.join(source_folder, test_folder2, self.test_file_2), TestFiles.COPY_CONTENT)

            pipe_storage_mv(os.path.join(source_folder, test_folder1), "cp://%s/%s/" % (self.bucket_name, case),
                            recursive=True)
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
            assert not object_exists(self.bucket_name, os.path.join(case, self.test_file_2))
            assert_files_deleted(None, os.path.join(source_folder, test_folder1, self.test_file_1))
            assert os.path.exists(os.path.join(source_folder, test_folder2, self.test_file_2))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_download_folders_with_similar_keys(self):
        """TC-PIPE-STORAGE-83"""
        case = "TC-PIPE-STORAGE-83"
        source_folder = os.path.abspath(os.path.join(self.test_folder, case))
        source1 = os.path.join(source_folder, self.test_file_1)
        source2 = os.path.join(source_folder, self.test_file_2)
        try:
            create_test_folder(source_folder)
            create_test_file(source1, TestFiles.DEFAULT_CONTENT)
            create_test_file(source2, TestFiles.COPY_CONTENT)

            pipe_storage_cp(source1, "cp://%s/%s/folder/" % (self.bucket_name, case))
            assert object_exists(self.bucket_name, os.path.join(case, "folder", self.test_file_1))
            pipe_storage_cp(source2, "cp://%s/%s/folder2/" % (self.bucket_name, case))
            assert object_exists(self.bucket_name, os.path.join(case, "folder2", self.test_file_2))

            pipe_storage_mv("cp://%s/%s/folder" % (self.bucket_name, case),
                            "%s/" % os.path.join(self.output_folder, case), recursive=True)
            assert os.path.exists(os.path.abspath(os.path.join(self.output_folder, case, self.test_file_1)))
            assert not os.path.exists(os.path.abspath(
                os.path.join(self.output_folder, case, self.test_file_2)))
            assert not object_exists(self.bucket_name, os.path.join(case, "folder", self.test_file_1))
            assert object_exists(self.bucket_name, os.path.join(case, "folder2", self.test_file_2))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    test_case_for_destination_slash = [("TC-PIPE-STORAGE-85-1", True, True), ("TC-PIPE-STORAGE-85-2", True, False),
                                       ("TC-PIPE-STORAGE-85-3", False, False), ("TC-PIPE-STORAGE-85-4", False, True)]

    @pytest.mark.run(order=6)
    @pytest.mark.parametrize("case,has_destination_slash,has_source_slash", test_case_for_destination_slash)
    def test_folder_with_slash_should_upload_content_only(self, case, has_destination_slash, has_source_slash):
        """TC-PIPE-STORAGE-85"""
        source_folder = os.path.abspath(os.path.join(self.test_folder, case))
        source1 = os.path.join(source_folder, self.test_file_1)
        source2 = os.path.join(source_folder, self.test_file_2)
        source3 = os.path.join(source_folder, self.test_file_3)
        source = os.path.abspath(os.path.join(self.test_folder, case))
        destination = "cp://%s/%s" % (self.bucket_name, case)
        source, destination = prepare_paths_with_slash(source, destination, has_source_slash, has_destination_slash)
        try:
            create_test_folder(source_folder)
            create_test_file(source1, TestFiles.DEFAULT_CONTENT)
            create_test_file(source2, TestFiles.COPY_CONTENT)
            create_test_file(source3, TestFiles.COPY_CONTENT)

            pipe_storage_mv(source, destination, recursive=True)
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_2))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_3))
            assert not os.path.exists(source1)
            assert not os.path.exists(source2)
            assert not os.path.exists(source3)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    test_case_for_download_with_slash = [("TC-PIPE-STORAGE-104-1", True, True),
                                         ("TC-PIPE-STORAGE-104-2", True, False),
                                         ("TC-PIPE-STORAGE-104-3", False, False),
                                         ("TC-PIPE-STORAGE-104-4", False, True)]

    @pytest.mark.run(order=6)
    @pytest.mark.parametrize("case,has_destination_slash,has_source_slash", test_case_for_download_with_slash)
    def test_folder_with_slash_should_download_content_only(self, case, has_destination_slash, has_source_slash):
        """TC-PIPE-STORAGE-104"""
        source = "cp://%s/%s" % (self.bucket_name, case)
        destination = os.path.abspath(os.path.join(self.output_folder, case))
        source, destination = prepare_paths_with_slash(source, destination, has_source_slash, has_destination_slash)
        try:
            self._create_folder_on_bucket(case)

            pipe_storage_mv(source, destination, recursive=True)
            assert os.path.exists(os.path.join(destination, self.test_file_1))
            assert os.path.exists(os.path.join(destination, self.test_file_2))
            assert os.path.exists(os.path.join(destination, self.test_file_3))

            assert not object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
            assert not object_exists(self.bucket_name, os.path.join(case, self.test_file_2))
            assert not object_exists(self.bucket_name, os.path.join(case, self.test_file_3))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    test_case_for_copy_between_buckets_with_slash = [("TC-PIPE-STORAGE-105-1", True, True),
                                                     ("TC-PIPE-STORAGE-105-2", True, False),
                                                     ("TC-PIPE-STORAGE-105-3", False, False),
                                                     ("TC-PIPE-STORAGE-105-4", False, True)]

    @pytest.mark.run(order=6)
    @pytest.mark.parametrize("case,has_destination_slash,has_source_slash",
                             test_case_for_copy_between_buckets_with_slash)
    def test_folder_with_slash_should_copy_between_buckets_content_only(self, case, has_destination_slash,
                                                                        has_source_slash):
        """TC-PIPE-STORAGE-105"""
        source = "cp://%s/%s" % (self.bucket_name, case)
        destination = "cp://%s/%s" % (self.other_bucket_name, case)
        source, destination = prepare_paths_with_slash(source, destination, has_source_slash, has_destination_slash)
        try:
            self._create_folder_on_bucket(case)

            pipe_storage_mv(source, destination, recursive=True)
            assert object_exists(self.other_bucket_name, os.path.join(case, self.test_file_1))
            assert object_exists(self.other_bucket_name, os.path.join(case, self.test_file_2))
            assert object_exists(self.other_bucket_name, os.path.join(case, self.test_file_3))

            assert not object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
            assert not object_exists(self.bucket_name, os.path.join(case, self.test_file_2))
            assert not object_exists(self.bucket_name, os.path.join(case, self.test_file_3))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_folder_with_skip_existing_option_should_skip(self):
        """TC-PIPE-STORAGE-87"""
        case = "TC-PIPE-STORAGE-87"
        source_folder = os.path.abspath(os.path.join(self.test_folder, case))
        source1 = os.path.join(source_folder, self.test_file_1)
        source2 = os.path.join(source_folder, self.test_file_2)
        source3 = os.path.join(source_folder, self.test_file_3)
        destination = "cp://%s/%s" % (self.bucket_name, case)
        key1 = os.path.join(case, self.test_file_1)
        key2 = os.path.join(case, self.test_file_2)
        key3 = os.path.join(case, self.test_file_3)
        try:
            create_test_folder(source_folder)
            create_test_file(source1, TestFiles.DEFAULT_CONTENT)
            create_test_file(source2, TestFiles.COPY_CONTENT)
            create_test_file(source3, TestFiles.COPY_CONTENT)

            expected = create_file_on_bucket(self.bucket_name, key1, source1)

            pipe_storage_mv(source_folder, destination, force=True, recursive=True, skip_existing=True)
            assert object_exists(self.bucket_name, key1)
            assert object_exists(self.bucket_name, key2)
            assert object_exists(self.bucket_name, key3)
            actual = ObjectInfo(False).build(self.bucket_name, key1)
            assert expected.size == actual.size, \
                "Sizes must be the same.\nExpected %s\nActual %s" % (expected.size, actual.size)
            assert expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be the same.\n" \
                "Expected %s\nActual %s".format(expected.last_modified, actual.last_modified)
            assert not os.path.exists(source2)
            assert not os.path.exists(source3)
            assert os.path.exists(source1)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_folder_with_skip_existing_option_should_not_skip(self):
        """TC-PIPE-STORAGE-90"""
        case = "TC-PIPE-STORAGE-90"
        source_folder = os.path.abspath(os.path.join(self.test_folder, case))
        source1 = os.path.join(source_folder, self.test_file_1)
        source2 = os.path.join(source_folder, self.test_file_2)
        source3 = os.path.join(source_folder, self.test_file_3)
        destination = "cp://%s/%s" % (self.bucket_name, case)
        key1 = os.path.join(case, self.test_file_1)
        key2 = os.path.join(case, self.test_file_2)
        key3 = os.path.join(case, self.test_file_3)
        try:
            create_test_folder(source_folder)
            create_test_file(source1, TestFiles.DEFAULT_CONTENT)
            create_test_file(source2, TestFiles.COPY_CONTENT)
            create_test_file(source3, TestFiles.COPY_CONTENT)

            expected = create_file_on_bucket(self.bucket_name, key1, source2)

            pipe_storage_mv(source_folder, destination, force=True, recursive=True, skip_existing=True)
            assert object_exists(self.bucket_name, key1)
            assert object_exists(self.bucket_name, key2)
            assert object_exists(self.bucket_name, key3)
            actual = ObjectInfo(False).build(self.bucket_name, key1)
            assert not expected.size == actual.size, "Sizes must be the different."
            assert not expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be different."
            assert not os.path.exists(source2)
            assert not os.path.exists(source1)
            assert not os.path.exists(source3)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_download_folder_with_skip_existing_option_should_skip(self):
        """TC-PIPE-STORAGE-100"""
        case = "TC-PIPE-STORAGE-100"
        destination_folder = os.path.abspath(os.path.join(self.output_folder, case))
        destination1 = os.path.join(destination_folder, self.test_file_1)
        destination2 = os.path.join(destination_folder, self.test_file_2)
        destination3 = os.path.join(destination_folder, self.test_file_3)
        source_folder = "cp://%s/%s/" % (self.bucket_name, case)
        try:
            create_test_file(destination1, TestFiles.DEFAULT_CONTENT)
            assert os.path.exists(destination1)
            expected = ObjectInfo(True).build(destination1)

            pipe_storage_cp(os.path.abspath(os.path.join(self.source_dir, self.test_file_1)), source_folder)
            pipe_storage_cp(os.path.abspath(os.path.join(self.source_dir, self.test_file_2)), source_folder)
            pipe_storage_cp(os.path.abspath(os.path.join(self.source_dir, self.test_file_3)), source_folder)
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_2))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_3))

            pipe_storage_mv(source_folder, destination_folder, force=True, recursive=True, skip_existing=True)
            assert os.path.exists(destination1)
            assert os.path.exists(destination2)
            assert os.path.exists(destination3)
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
            actual = ObjectInfo(True).build(destination1)
            assert expected.size == actual.size, \
                "Sizes must be the same.\nExpected %s\nActual %s" % (expected.size, actual.size)
            assert expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be the same.\n" \
                "Expected %s\nActual %s".format(expected.last_modified, actual.last_modified)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_download_folder_with_skip_existing_option_should_not_skip(self):
        """TC-PIPE-STORAGE-101"""
        case = "TC-PIPE-STORAGE-101"
        destination_folder = os.path.abspath(os.path.join(self.output_folder, case))
        destination1 = os.path.join(destination_folder, self.test_file_1)
        destination2 = os.path.join(destination_folder, self.test_file_2)
        destination3 = os.path.join(destination_folder, self.test_file_3)
        source_folder = "cp://%s/%s/" % (self.bucket_name, case)
        try:
            create_test_file(destination1, TestFiles.COPY_CONTENT)
            assert os.path.exists(destination1)
            expected = ObjectInfo(True).build(destination1)

            pipe_storage_cp(os.path.abspath(os.path.join(self.source_dir, self.test_file_1)), source_folder)
            pipe_storage_cp(os.path.abspath(os.path.join(self.source_dir, self.test_file_2)), source_folder)
            pipe_storage_cp(os.path.abspath(os.path.join(self.source_dir, self.test_file_3)), source_folder)
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_2))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_3))

            pipe_storage_mv(source_folder, destination_folder, force=True, recursive=True, skip_existing=True)
            assert os.path.exists(destination1)
            assert os.path.exists(destination2)
            assert os.path.exists(destination3)
            assert not object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
            actual = ObjectInfo(True).build(destination1)
            assert not expected.size == actual.size, "Sizes must be the different."
            assert not expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be different."
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_copy_folder_between_buckets_with_skip_existing_option_should_skip(self):
        """TC-PIPE-STORAGE-112"""
        case = "TC-PIPE-STORAGE-112"
        source_folder = "cp://%s/%s/" % (self.bucket_name, case)
        destination_folder = "cp://%s/%s/" % (self.other_bucket_name, case)
        key1 = os.path.join(case, self.test_file_1)
        key2 = os.path.join(case, self.test_file_2)
        key3 = os.path.join(case, self.test_file_3)
        source_file1 = os.path.abspath(os.path.join(self.source_dir, self.test_file_1))
        source_file2 = os.path.abspath(os.path.join(self.source_dir, self.test_file_2))
        source_file3 = os.path.abspath(os.path.join(self.source_dir, self.test_file_3))
        try:
            expected = create_file_on_bucket(self.other_bucket_name, key1, source_file1)

            pipe_storage_cp(source_file1, "cp://%s/%s" % (self.bucket_name, key1))
            pipe_storage_cp(source_file2, "cp://%s/%s" % (self.bucket_name, key2))
            pipe_storage_cp(source_file2, "cp://%s/%s" % (self.bucket_name, key3))
            assert object_exists(self.bucket_name, key1)
            assert object_exists(self.bucket_name, key2)
            assert object_exists(self.bucket_name, key3)

            pipe_storage_mv(source_folder, destination_folder, force=True, recursive=True, skip_existing=True)
            assert object_exists(self.other_bucket_name, key1)
            assert object_exists(self.other_bucket_name, key2)
            assert object_exists(self.other_bucket_name, key3)
            assert object_exists(self.bucket_name, key1)
            assert not object_exists(self.bucket_name, key2)
            assert not object_exists(self.bucket_name, key3)
            actual = ObjectInfo(False).build(self.other_bucket_name, key1)
            assert expected.size == actual.size, \
                "Sizes must be the same.\nExpected %s\nActual %s" % (expected.size, actual.size)
            assert expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be the same.\n" \
                "Expected %s\nActual %s".format(expected.last_modified, actual.last_modified)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_copy_folder_between_buckets_with_skip_existing_option_should_not_skip(self):
        """TC-PIPE-STORAGE-113"""
        case = "TC-PIPE-STORAGE-113"
        source_folder = "cp://%s/%s/" % (self.bucket_name, case)
        destination_folder = "cp://%s/%s/" % (self.other_bucket_name, case)
        key1 = os.path.join(case, self.test_file_1)
        key2 = os.path.join(case, self.test_file_2)
        key3 = os.path.join(case, self.test_file_3)
        source_file1 = os.path.abspath(os.path.join(self.source_dir, self.test_file_1))
        source_file2 = os.path.abspath(os.path.join(self.source_dir, self.test_file_2))
        source_file3 = os.path.abspath(os.path.join(self.source_dir, self.test_file_3))
        try:
            expected = create_file_on_bucket(self.other_bucket_name, key1, source_file2)

            pipe_storage_cp(source_file1, "cp://%s/%s" % (self.bucket_name, key1))
            pipe_storage_cp(source_file2, "cp://%s/%s" % (self.bucket_name, key2))
            pipe_storage_cp(source_file2, "cp://%s/%s" % (self.bucket_name, key3))
            assert object_exists(self.bucket_name, key1)
            assert object_exists(self.bucket_name, key2)
            assert object_exists(self.bucket_name, key3)

            pipe_storage_mv(source_folder, destination_folder, force=True, recursive=True, skip_existing=True)
            assert object_exists(self.other_bucket_name, key1)
            assert object_exists(self.other_bucket_name, key2)
            assert object_exists(self.other_bucket_name, key3)
            assert not object_exists(self.bucket_name, key1)
            assert not object_exists(self.bucket_name, key2)
            assert not object_exists(self.bucket_name, key3)
            actual = ObjectInfo(False).build(self.other_bucket_name, key1)
            assert not expected.size == actual.size, "Sizes must be the different."
            assert not expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be different."
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    def _create_folder_on_bucket(self, case):
        source_files = os.path.abspath(self.source_dir)
        source1 = os.path.join(source_files, self.test_file_1)
        source2 = os.path.join(source_files, self.test_file_2)
        source3 = os.path.join(source_files, self.test_file_3)
        pipe_storage_cp(source1, "cp://%s/%s/%s" % (self.bucket_name, case, self.test_file_1))
        pipe_storage_cp(source2, "cp://%s/%s/%s" % (self.bucket_name, case, self.test_file_2))
        pipe_storage_cp(source3, "cp://%s/%s/%s" % (self.bucket_name, case, self.test_file_3))
