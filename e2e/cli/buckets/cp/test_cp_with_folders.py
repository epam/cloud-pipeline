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

from common_utils.test_utils import format_name
from ..utils.assertions_utils import *
from ..utils.file_utils import *
from ..utils.utilities_for_test import *


class TestCopyWithFolders(object):

    raw_bucket_name = "cp-folders{}".format(get_test_prefix())
    bucket_name = format_name(raw_bucket_name)
    other_bucket_name = format_name("{}-other".format(raw_bucket_name))
    current_directory = os.getcwd()
    home_dir = "test_cp_home_dir-storage-15%s/" % get_test_prefix()
    checkout_dir = "checkout/"
    output_folder = "cp-folders-" + TestFiles.TEST_FOLDER_FOR_OUTPUT
    test_file_1 = "cp-folders-" + TestFiles.TEST_FILE1
    test_file_with_other_extension = "cp-folders-" + TestFiles.TEST_FILE_WITH_OTHER_EXTENSION
    test_file_2 = "cp-folders-" + TestFiles.TEST_FILE2
    test_folder = "cp-folders-" + TestFiles.TEST_FOLDER
    test_folder_2 = "cp-folders-" + TestFiles.TEST_FOLDER2
    test_folder_structure = "cp-folders-structure"
    test_folder_structure_output = "cp-folders-structure-output"

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name, cls.other_bucket_name)
        # /test_folder
        create_test_folder(os.path.abspath(cls.test_folder))
        # /cp-files-test_folder_for_outputs
        create_test_folder(os.path.abspath(cls.output_folder))
        # ./test_file.txt
        create_test_file(os.path.abspath(cls.test_file_1), TestFiles.DEFAULT_CONTENT)
        # ./test_folder/test_file.txt
        create_test_file(os.path.abspath(cls.test_folder + cls.test_file_1), TestFiles.DEFAULT_CONTENT)
        # ./test_folder/test_file.json
        create_test_file(os.path.abspath(cls.test_folder + cls.test_file_with_other_extension),
                         TestFiles.DEFAULT_CONTENT)
        # ./test_folder/other/test_file.txt
        create_test_file(os.path.abspath(cls.test_folder + cls.test_folder + cls.test_file_1),
                         TestFiles.DEFAULT_CONTENT)
        # ./test_file2.txt
        create_test_file(os.path.abspath(cls.test_file_2), TestFiles.COPY_CONTENT)
        # ~/test_cp_home_dir/test_file.txt
        create_test_file(os.path.join(os.path.expanduser('~'), cls.home_dir, cls.test_file_1),
                         TestFiles.DEFAULT_CONTENT)
        # ~/test_cp_home_dir/other/test_file.txt
        create_test_file(os.path.join(os.path.expanduser('~'), cls.home_dir, cls.test_folder,
                                      cls.test_file_1), TestFiles.DEFAULT_CONTENT)
        # /test_folder_structure
        create_test_folder(os.path.abspath(cls.test_folder_structure))
        # /test_folder_structure/test_folder/
        create_test_folder(os.path.join(os.path.abspath(cls.test_folder_structure), cls.test_folder))
        # /test_folder_structure/other/
        create_test_folder(os.path.join(os.path.abspath(cls.test_folder_structure), cls.test_folder_2))
        # /test_folder_structure/test_folder/test_file.txt
        create_test_file(os.path.join(os.path.abspath(cls.test_folder_structure), cls.test_folder, cls.test_file_1),
                         TestFiles.DEFAULT_CONTENT)
        # /test_folder_structure/other/test_file.txt
        create_test_file(os.path.join(os.path.abspath(cls.test_folder_structure), cls.test_folder_2, cls.test_file_1),
                         TestFiles.DEFAULT_CONTENT)
        # /test_folder_structure_output
        create_test_folder(os.path.abspath(cls.test_folder_structure_output))

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name, cls.other_bucket_name)
        clean_test_data(os.path.abspath(cls.test_file_1))
        clean_test_data(os.path.abspath(cls.test_file_2))
        clean_test_data(os.path.abspath(cls.test_folder))
        clean_test_data(os.path.abspath(cls.output_folder))
        clean_test_data(os.path.join(os.path.expanduser('~'), cls.home_dir))
        clean_test_data(os.path.abspath(cls.checkout_dir))
        clean_test_data(os.path.abspath(cls.test_folder_structure))
        clean_test_data(os.path.abspath(cls.test_folder_structure_output))

    """
        1. test case
        2. source path
        3. with --force option
        4. flag if need to switch current directory
    """
    test_case_for_upload_folders = [
        ("TC-PIPE-STORAGE-11", os.path.abspath(test_folder), False, None),
        ("TC-PIPE-STORAGE-15", "~/" + home_dir, None, None),
        ("TC-PIPE-STORAGE-17", "./" + test_folder, False, None),
        ("TC-PIPE-STORAGE-17-1", "./", False, True),
        ("TC-PIPE-STORAGE-17-2", ".", False, True),
        ("TC-PIPE-STORAGE-19", os.path.abspath(test_folder) + "/", True, None),
    ]

    @pytest.mark.run(order=1)
    @pytest.mark.parametrize("test_case,source,force,switch_dir", test_case_for_upload_folders)
    def test_folder_should_be_uploaded(self, test_case, source, force, switch_dir):
        destination = "cp://{}/{}/".format(self.bucket_name, test_case)
        if force:
            create_test_files_on_bucket(os.path.abspath(self.test_file_2), self.bucket_name,
                                        os.path.join(test_case, self.test_file_1),
                                        os.path.join(test_case, self.test_folder, self.test_file_1))
        if source.startswith("~"):
            source_to_check = os.path.join(os.path.expanduser('~'), source.strip("~/"))
        else:
            source_to_check = source
        if switch_dir:
            dir_path = os.path.abspath(os.path.join(self.checkout_dir))
            create_test_files(TestFiles.DEFAULT_CONTENT, os.path.join(dir_path, self.test_file_1),
                              os.path.join(dir_path, self.test_folder, self.test_file_1))
            os.chdir(dir_path)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, force=force, recursive=True)
        assert_copied_object_info(ObjectInfo(True).build(os.path.join(source_to_check, self.test_file_1)),
                                  ObjectInfo(False).build(self.bucket_name, os.path.join(test_case, self.test_file_1)),
                                  test_case)
        assert_copied_object_info(ObjectInfo(True).build(os.path.join(source_to_check, self.test_folder,
                                                                      self.test_file_1)),
                                  ObjectInfo(False).build(self.bucket_name, os.path.join(
                                      test_case, self.test_folder + self.test_file_1)), test_case)
        os.chdir(self.current_directory)

    """
        1. test case
        2. source path
        3. path to directory if need to switch current directory
        4. relative path to file to rewrite (with --force option)
    """
    test_case_for_download_folders = [
        ("TC-PIPE-STORAGE-11", os.path.abspath(output_folder + "TC-PIPE-STORAGE-11") + "/", None, None),
        ("TC-PIPE-STORAGE-15", "~/" + home_dir + output_folder, None, None),
        ("TC-PIPE-STORAGE-17", "./" + output_folder + "TC-PIPE-STORAGE-17/", None, None),
        ("TC-PIPE-STORAGE-17-1", "./", None, True),
        ("TC-PIPE-STORAGE-17-2", ".", None, True),
        ("TC-PIPE-STORAGE-19", os.path.abspath(output_folder + "TC-PIPE-STORAGE-19") + "/", True, None),
    ]

    @pytest.mark.run(order=2)
    @pytest.mark.parametrize("test_case,destination,force,switch_dir", test_case_for_download_folders)
    def test_folder_should_be_downloaded(self, test_case, destination, force, switch_dir):
        source = "cp://{}/{}/".format(self.bucket_name, test_case)
        if force:
            create_test_file(destination + self.test_file_1, TestFiles.COPY_CONTENT)
            assert os.path.exists(destination + self.test_file_1), \
                "Test file {} does not exist".format(destination + self.test_file_1)
            create_test_file(destination + self.test_folder + self.test_file_1, TestFiles.COPY_CONTENT)
            assert os.path.exists(destination + self.test_folder + self.test_file_1), \
                "Test file {} does not exist".format(destination + self.test_folder + self.test_file_1)
        if destination.startswith("~"):
            destination_to_check = os.path.join(os.path.expanduser('~'), destination.strip("~/"))
        else:
            destination_to_check = destination
        if switch_dir:
            dir_path = os.path.abspath(os.path.join(destination, self.checkout_dir, test_case))
            create_test_folder(dir_path)
            os.chdir(dir_path)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, force=force, recursive=True)
        assert_copied_object_info(ObjectInfo(False).build(self.bucket_name,
                                                          "{}/{}".format(test_case, self.test_file_1)),
                                  ObjectInfo(True).build(os.path.join(destination_to_check, self.test_file_1)),
                                  test_case)
        assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, "{}/{}".format(
            test_case, self.test_folder + self.test_file_1)),
                                  ObjectInfo(True).build(os.path.join(destination_to_check, self.test_folder,
                                                                      self.test_file_1)),
                                  test_case)
        os.chdir(self.current_directory)

    """
        1. test case
        2. --force option
    """
    test_case_for_copy_between_buckets_folders = [
        ("TC-PIPE-STORAGE-11", False),
        ("TC-PIPE-STORAGE-19", True),
    ]

    @pytest.mark.run(order=3)
    @pytest.mark.parametrize("test_case,force", test_case_for_copy_between_buckets_folders)
    def test_folder_should_be_copied(self, test_case, force):
        source = "cp://{}/{}/".format(self.bucket_name, test_case)
        destination = "cp://{}/{}/".format(self.other_bucket_name, test_case)
        if force:
            create_test_files_on_bucket(os.path.abspath(self.test_file_2), self.other_bucket_name,
                                        os.path.join(test_case, self.test_file_1),
                                        os.path.join(test_case, self.test_folder, self.test_file_1))
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, force=force, recursive=True)
        assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, os.path.join(test_case, self.test_file_1)),
                                  ObjectInfo(False).build(self.other_bucket_name,
                                                          os.path.join(test_case, self.test_file_1)), test_case)
        assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, os.path.join(
            test_case, self.test_folder, self.test_file_1)),
                                  ObjectInfo(False).build(self.other_bucket_name, os.path.join(
                                      test_case, self.test_folder, self.test_file_1)), test_case)

    @pytest.mark.run(order=1)
    def test_excluded_files_should_be_uploaded(self):
        """TC-PIPE-STORAGE-25"""
        source = os.path.abspath(self.test_folder)
        case = "TC-PIPE-STORAGE-25-1"
        destination = "cp://{}/{}/".format(self.bucket_name, case)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, recursive=True, exclude=["*json", "{}*".format(self.test_folder)],
                        expected_status=0)
        assert_copied_object_info(ObjectInfo(True).build(os.path.join(source, self.test_file_1)),
                                  ObjectInfo(False).build(self.bucket_name, os.path.join(case, self.test_file_1)),
                                  case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name, os.path.join(
            case, self.test_folder, self.test_file_1)),
                                  case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name, os.path.join(
            case, self.test_file_with_other_extension)),
                                            case)

    @pytest.mark.run(order=2)
    def test_excluded_files_should_be_downloaded(self):
        """TC-PIPE-STORAGE-25"""
        case = "TC-PIPE-STORAGE-25-2"
        source = "cp://{}/{}/".format(self.bucket_name, case)
        key_file_1 = os.path.join(case, self.test_file_1)
        key_file_2 = os.path.join(case, self.test_file_with_other_extension)
        key_file_folder = os.path.join(case, self.test_folder, self.test_file_1)
        create_test_files_on_bucket(self.test_file_1, self.bucket_name, key_file_1, key_file_2, key_file_folder)
        destination = os.path.abspath(os.path.join(self.output_folder, case))
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, recursive=True, exclude=["*json", "{}*".format(self.test_folder)],
                        expected_status=0)
        assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, key_file_1),
                                  ObjectInfo(True).build(os.path.join(destination, self.test_file_1)),
                                  case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_folder,
                                                                                self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(
            destination, self.test_file_with_other_extension)), case)

    @pytest.mark.run(order=3)
    def test_excluded_files_should_be_copied(self):
        """TC-PIPE-STORAGE-25"""
        case = "TC-PIPE-STORAGE-25-3"
        source = "cp://{}/{}/".format(self.bucket_name, case)
        key_file_1 = os.path.join(case, self.test_file_1)
        key_file_2 = os.path.join(case, self.test_file_with_other_extension)
        key_file_folder = os.path.join(case, self.test_folder, self.test_file_1)
        create_test_files_on_bucket(self.test_file_1, self.bucket_name, key_file_1, key_file_2, key_file_folder)
        destination = "cp://{}/{}/".format(self.other_bucket_name, case)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, recursive=True, exclude=["*json", "{}*".format(self.test_folder)],
                        expected_status=0)
        assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, key_file_1),
                                  ObjectInfo(False).build(self.other_bucket_name, key_file_1), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name, key_file_folder), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name, key_file_2),
                                            case)

    @pytest.mark.run(order=1)
    def test_included_files_should_be_uploaded(self):
        """TC-PIPE-STORAGE-27"""
        source = os.path.abspath(self.test_folder)
        case = "TC-PIPE-STORAGE-27-1"
        destination = "cp://{}/{}/".format(self.bucket_name, case)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, recursive=True, include=["*json"],
                        expected_status=0)
        assert_copied_object_info(
            ObjectInfo(True).build(os.path.join(source, self.test_file_with_other_extension)),
            ObjectInfo(False).build(self.bucket_name, os.path.join(case, self.test_file_with_other_extension)),
            case)
        assert_copied_object_does_not_exist(
            ObjectInfo(False).build(self.bucket_name, os.path.join(case, self.test_folder, self.test_file_1)),
            case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(
            self.bucket_name, os.path.join(case, self.test_file_1)), case)

    @pytest.mark.run(order=2)
    def test_included_files_should_be_downloaded(self):
        """TC-PIPE-STORAGE-27"""
        case = "TC-PIPE-STORAGE-27-2"
        source = "cp://{}/{}/".format(self.bucket_name, case)
        key_file_1 = os.path.join(case, self.test_file_1)
        key_file_2 = os.path.join(case, self.test_file_with_other_extension)
        key_file_folder = os.path.join(case, self.test_folder, self.test_file_1)
        create_test_files_on_bucket(self.test_file_1, self.bucket_name, key_file_1, key_file_2, key_file_folder)
        destination = os.path.abspath(os.path.join(self.output_folder, case))
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, recursive=True, include=["*json"], expected_status=0)
        assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, key_file_2),
                                  ObjectInfo(True).build(os.path.join(destination,
                                                                      self.test_file_with_other_extension)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_folder,
                                                                                self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, case, self.test_file_1)),
                                            case)

    @pytest.mark.run(order=3)
    def test_included_files_be_copied(self):
        """TC-PIPE-STORAGE-27"""
        case = "TC-PIPE-STORAGE-27-3"
        source = "cp://{}/{}/".format(self.bucket_name, case)
        destination = "cp://{}/{}/".format(self.other_bucket_name, case)
        key_file_1 = os.path.join(case, self.test_file_1)
        key_file_2 = os.path.join(case, self.test_file_with_other_extension)
        key_file_folder = os.path.join(case, self.test_folder, self.test_file_1)
        create_test_files_on_bucket(self.test_file_1, self.bucket_name, key_file_1, key_file_2, key_file_folder)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, recursive=True, include=["*json"], expected_status=0)
        assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, key_file_2),
                                  ObjectInfo(False).build(self.other_bucket_name, key_file_2), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name, key_file_folder), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name, key_file_1), case)

    @pytest.mark.run(order=1)
    def test_included_excluded_files_should_be_uploaded(self):
        """TC-PIPE-STORAGE-31"""
        source = os.path.abspath(self.test_folder)
        case = "TC-PIPE-STORAGE-31-1"
        destination = "cp://{}/{}/".format(self.bucket_name, case)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, recursive=True, include=["*txt"],
                        exclude=["{}*".format(self.test_folder)], expected_status=0)
        assert_copied_object_info(
            ObjectInfo(True).build(os.path.join(source, self.test_file_1)),
            ObjectInfo(False).build(self.bucket_name, os.path.join(case, self.test_file_1)), case)
        assert_copied_object_does_not_exist(
            ObjectInfo(False).build(self.bucket_name, os.path.join(case, self.test_folder,
                                                                   self.test_file_with_other_extension)), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(
            self.bucket_name, os.path.join(case, self.test_file_with_other_extension)), case)

    @pytest.mark.run(order=2)
    def test_included_excluded_files_should_be_downloaded(self):
        """TC-PIPE-STORAGE-31"""
        case = "TC-PIPE-STORAGE-31-2"
        source = "cp://{}/{}/".format(self.bucket_name, case)
        key_file_1 = os.path.join(case, self.test_file_1)
        key_file_2 = os.path.join(case, self.test_file_with_other_extension)
        key_file_folder = os.path.join(case, self.test_folder, self.test_file_1)
        create_test_files_on_bucket(self.test_file_1, self.bucket_name, key_file_1, key_file_2, key_file_folder)
        destination = os.path.abspath(os.path.join(self.output_folder, case))
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, recursive=True, include=["*txt"],
                        exclude=["{}*".format(self.test_folder)], expected_status=0)
        assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, key_file_1),
                                  ObjectInfo(True).build(os.path.join(destination, self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(
                                      destination, self.test_folder, self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(
            destination, self.test_file_with_other_extension)), case)

    @pytest.mark.run(order=3)
    def test_included_excluded_files_be_copied(self):
        """TC-PIPE-STORAGE-31"""
        case = "TC-PIPE-STORAGE-31-3"
        source = "cp://{}/{}/".format(self.bucket_name, case)
        destination = "cp://{}/{}/".format(self.other_bucket_name, case)
        key_file_1 = os.path.join(case, self.test_file_1)
        key_file_2 = os.path.join(case, self.test_file_with_other_extension)
        key_file_folder = os.path.join(case, self.test_folder, self.test_file_1)
        create_test_files_on_bucket(self.test_file_1, self.bucket_name, key_file_1, key_file_2, key_file_folder)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, recursive=True, include=["*txt"],
                        exclude=["{}*".format(self.test_folder)], expected_status=0)
        assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, key_file_1),
                                  ObjectInfo(False).build(self.other_bucket_name, key_file_1), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name, key_file_folder), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name, key_file_2), case)

    @pytest.mark.run(order=4)
    def test_upload_without_recursive(self):
        """TC-PIPE-STORAGE-13"""
        case = "TC-PIPE-STORAGE-13"
        source = os.path.abspath(self.test_folder)
        destination = "cp://{}/".format(os.path.join(self.bucket_name, case))
        error_text = pipe_storage_cp(source, destination, expected_status=1)[1]
        assert_error_message_is_present(error_text, "Flag --recursive (-r) is required to copy folders.")
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name,
                                                                    os.path.join(case, self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name,
                                                                    os.path.join(case, self.test_folder,
                                                                                 self.test_file_1)), case)

    @pytest.mark.run(order=5)
    def test_download_without_recursive(self):
        """TC-PIPE-STORAGE-13"""
        case = "TC-PIPE-STORAGE-13"
        source = "cp://{}/".format(os.path.join(self.bucket_name, case))
        create_test_files_on_bucket(self.test_file_1, self.bucket_name, os.path.join(case, self.test_file_1),
                                    os.path.join(case, self.test_folder, self.test_file_1))
        destination = os.path.abspath(self.output_folder + case) + "/"
        error_text = pipe_storage_cp(source, destination, expected_status=1)[1]
        assert_error_message_is_present(error_text, "Flag --recursive (-r) is required to copy folders.")
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_folder,
                                                                                self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_file_1)),
                                            case)

    @pytest.mark.run(order=6)
    def test_copy_without_recursive(self):
        """TC-PIPE-STORAGE-13"""
        case = "TC-PIPE-STORAGE-13"
        source = "cp://{}/".format(os.path.join(self.bucket_name, case))
        destination = "cp://{}/".format(os.path.join(self.other_bucket_name, case))
        error_text = pipe_storage_cp(source, destination, expected_status=1)[1]
        assert_error_message_is_present(error_text, "Flag --recursive (-r) is required to copy folders.")
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_folder,
                                                                                self.test_file_1)), case)
        assert_copied_object_does_not_exist(ObjectInfo(True).build(os.path.join(destination, self.test_file_1)),
                                            case)

    @pytest.mark.run(order=6)
    def test_copy_to_bucket_root(self):
        """TC-PIPE-STORAGE-66"""
        case = "TC-PIPE-STORAGE-66"
        source = os.path.abspath(self.test_folder)
        destination = "cp://%s/" % self.bucket_name
        logging.info("Test case: %s. Ready to perform operation from %s to %s" % (case, source, destination))
        pipe_storage_cp(source, destination, recursive=True, force=True)
        assert_copied_object_info(ObjectInfo(True).build(os.path.join(source, self.test_file_1)),
                                  ObjectInfo(False).build(self.bucket_name, self.test_file_1), case)
        assert_copied_object_info(ObjectInfo(True).build(os.path.join(source, self.test_folder, self.test_file_1)),
                                  ObjectInfo(False).build(self.bucket_name, self.test_folder + self.test_file_1), case)

    @pytest.mark.run(order=6)
    def test_copy_file_to_bucket_with_folder_with_same_name(self):
        """TC-PIPE-STORAGE-67"""
        case = "TC-PIPE-STORAGE-67"
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_1), "cp://%s/%s/%s/" % (self.bucket_name, case,
                                                                                   self.test_file_1))
            assert object_exists(self.bucket_name, "%s/%s/%s" % (case, self.test_file_1, self.test_file_1))
            pipe_storage_cp(os.path.abspath(self.test_file_1), "cp://%s/%s/" % (self.bucket_name, case))
            assert object_exists(self.bucket_name, "%s/%s" % (case, self.test_file_1))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_copy_folder_structure(self):
        """TC-PIPE-STORAGE-68"""
        # TODO: TC-PIPE-STORAGE-69 implemented via this test
        case = "TC-PIPE-STORAGE-68"
        source = os.path.abspath(self.test_folder_structure)
        destination = "cp://%s/%s/" % (self.bucket_name, case)
        logging.info("Test case: %s. Ready to perform operation from %s to %s" % (case, source, destination))
        try:
            pipe_storage_cp(source, destination, recursive=True)
            assert object_exists(self.bucket_name, os.path.join(case, self.test_folder, self.test_file_1))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_folder_2, self.test_file_1))

            source = destination
            destination = os.path.abspath(self.test_folder_structure_output) + "/"
            logging.info("Test case: %s. Ready to perform operation from %s to %s" % (case, source, destination))
            pipe_storage_cp(source, destination, recursive=True, expected_status=0)
            assert_copied_object_info(ObjectInfo(False).build(self.bucket_name,
                                                              os.path.join(case, self.test_folder, self.test_file_1)),
                                      ObjectInfo(True).build(os.path.join(destination, self.test_folder,
                                                                          self.test_file_1)), case)
            assert_copied_object_info(ObjectInfo(False).build(self.bucket_name,
                                                              os.path.join(case, self.test_folder_2, self.test_file_1)),
                                      ObjectInfo(True).build(os.path.join(destination, self.test_folder_2,
                                                                          self.test_file_1)), case)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_file_to_not_empty_folder(self):
        """TC-PIPE-STORAGE-73"""
        test_case = "TC-PIPE-STORAGE-73"
        try:
            source1 = "cp://{}/{}/{}".format(self.bucket_name, test_case, self.test_file_1)
            pipe_storage_cp(os.path.abspath(self.test_file_1), source1, expected_status=0)
            assert object_exists(self.bucket_name, "%s/%s" % (test_case, self.test_file_1))

            source2 = "cp://{}/{}/".format(self.bucket_name, test_case)
            pipe_storage_cp(os.path.abspath(self.test_file_2), source2, expected_status=0)
            assert object_exists(self.bucket_name, "%s/%s" % (test_case, self.test_file_2))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(test_case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_folders_with_similar_keys(self):
        """TC-PIPE-STORAGE-80"""
        case = "TC-PIPE-STORAGE-80"
        source_folder = os.path.abspath(os.path.join(self.test_folder, case))
        test_folder1 = "folder"
        test_folder2 = "folder2"
        try:
            create_test_folder(source_folder)
            create_test_folder(os.path.join(source_folder, test_folder1))
            create_test_folder(os.path.join(source_folder, test_folder2))
            create_test_file(os.path.join(source_folder, test_folder1, self.test_file_1), TestFiles.DEFAULT_CONTENT)
            create_test_file(os.path.join(source_folder, test_folder2, self.test_file_2), TestFiles.COPY_CONTENT)

            pipe_storage_cp(os.path.join(source_folder, test_folder1), "cp://%s/%s/" % (self.bucket_name, case),
                            recursive=True)
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
            assert not object_exists(self.bucket_name, os.path.join(case, self.test_file_2))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_download_folders_with_similar_keys(self):
        """TC-PIPE-STORAGE-81"""
        case = "TC-PIPE-STORAGE-81"
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_1), "cp://%s/%s/folder/" % (self.bucket_name, case))
            assert object_exists(self.bucket_name, os.path.join(case, "folder", self.test_file_1))
            pipe_storage_cp(os.path.abspath(self.test_file_2), "cp://%s/%s/folder2/" % (self.bucket_name, case))
            assert object_exists(self.bucket_name, os.path.join(case, "folder2", self.test_file_2))

            pipe_storage_cp("cp://%s/%s/folder" % (self.bucket_name, case),
                            "%s/" % os.path.join(self.output_folder, case), recursive=True)
            assert os.path.exists(os.path.abspath(os.path.join(self.output_folder, case, self.test_file_1)))
            assert not os.path.exists(os.path.abspath(
                os.path.join(self.output_folder, case, self.test_file_2)))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    test_case_for_upload_with_slash = [("TC-PIPE-STORAGE-84-1", True, True), ("TC-PIPE-STORAGE-84-2", True, False),
                                       ("TC-PIPE-STORAGE-84-3", False, False), ("TC-PIPE-STORAGE-84-4", False, True)]

    @pytest.mark.run(order=6)
    @pytest.mark.parametrize("case,has_destination_slash,has_source_slash", test_case_for_upload_with_slash)
    def test_folder_with_slash_should_upload_content_only(self, case, has_destination_slash, has_source_slash):
        """TC-PIPE-STORAGE-84"""
        source = os.path.abspath(self.test_folder)
        destination = "cp://%s/%s" % (self.bucket_name, case)
        source, destination = prepare_paths_with_slash(source, destination, has_source_slash, has_destination_slash)
        try:
            pipe_storage_cp(source, destination, recursive=True)
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_with_other_extension))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_folder, self.test_file_1))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    test_case_for_download_with_slash = [("TC-PIPE-STORAGE-102-1", True, True),
                                         ("TC-PIPE-STORAGE-102-2", True, False),
                                         ("TC-PIPE-STORAGE-102-3", False, False),
                                         ("TC-PIPE-STORAGE-102-4", False, True)]

    @pytest.mark.run(order=6)
    @pytest.mark.parametrize("case,has_destination_slash,has_source_slash", test_case_for_download_with_slash)
    def test_folder_with_slash_should_download_content_only(self, case, has_destination_slash, has_source_slash):
        """TC-PIPE-STORAGE-102"""
        source = "cp://%s/%s" % (self.bucket_name, case)
        destination = os.path.abspath(os.path.join(self.output_folder, case))
        source, destination = prepare_paths_with_slash(source, destination, has_source_slash, has_destination_slash)
        try:
            self._create_folder_on_bucket(case)

            pipe_storage_cp(source, destination, recursive=True)
            assert os.path.exists(os.path.join(destination, self.test_file_1))
            assert os.path.exists(os.path.join(destination, self.test_file_with_other_extension))
            assert os.path.exists(os.path.join(destination, self.test_folder, self.test_file_1))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    test_case_for_copy_between_buckets_with_slash = [("TC-PIPE-STORAGE-103-1", True, True),
                                                     ("TC-PIPE-STORAGE-103-2", True, False),
                                                     ("TC-PIPE-STORAGE-103-3", False, False),
                                                     ("TC-PIPE-STORAGE-103-4", False, True)]

    @pytest.mark.run(order=6)
    @pytest.mark.parametrize("case,has_destination_slash,has_source_slash",
                             test_case_for_copy_between_buckets_with_slash)
    def test_folder_with_slash_should_copy_between_buckets_content_only(self, case, has_destination_slash,
                                                                        has_source_slash):
        """TC-PIPE-STORAGE-103"""
        source = "cp://%s/%s" % (self.bucket_name, case)
        destination = "cp://%s/%s" % (self.other_bucket_name, case)
        source, destination = prepare_paths_with_slash(source, destination, has_source_slash, has_destination_slash)
        try:
            self._create_folder_on_bucket(case)

            pipe_storage_cp(source, destination, recursive=True)
            assert object_exists(self.other_bucket_name, os.path.join(case, self.test_file_1))
            assert object_exists(self.other_bucket_name, os.path.join(case, self.test_file_with_other_extension))
            assert object_exists(self.other_bucket_name, os.path.join(case, self.test_folder, self.test_file_1))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_folder_with_skip_existing_option_should_skip(self):
        """TC-PIPE-STORAGE-86"""
        case = "TC-PIPE-STORAGE-86"
        key = os.path.join(case, self.test_file_1)
        source = os.path.abspath(self.test_folder)
        destination = "cp://%s/%s" % (self.bucket_name, case)
        try:
            expected = create_file_on_bucket(self.bucket_name, key,
                                             os.path.abspath(os.path.join(self.test_folder, self.test_file_1)))

            pipe_storage_cp(source, destination, force=True, recursive=True, skip_existing=True)
            assert object_exists(self.bucket_name, key)
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_with_other_extension))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_folder, self.test_file_1))
            actual = ObjectInfo(False).build(self.bucket_name, key)
            assert expected.size == actual.size, \
                "Sizes must be the same.\nExpected %s\nActual %s" % (expected.size, actual.size)
            assert expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be the same.\n" \
                "Expected %s\nActual %s".format(expected.last_modified, actual.last_modified)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_folder_with_skip_existing_option_should_not_skip(self):
        """TC-PIPE-STORAGE-88"""
        case = "TC-PIPE-STORAGE-88"
        key1 = os.path.join(case, self.test_file_1)
        source = os.path.abspath(self.test_folder)
        destination = "cp://%s/%s" % (self.bucket_name, case)
        try:
            expected = create_file_on_bucket(self.bucket_name, key1, os.path.abspath(self.test_file_2))

            pipe_storage_cp(source, destination, force=True, recursive=True, skip_existing=True)
            assert object_exists(self.bucket_name, key1)
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_with_other_extension))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_folder, self.test_file_1))
            actual = ObjectInfo(False).build(self.bucket_name, key1)
            assert not expected.size == actual.size, "Sizes must be the different."
            assert not expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be different."
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_download_folder_with_skip_existing_option_should_skip(self):
        """TC-PIPE-STORAGE-96"""
        case = "TC-PIPE-STORAGE-96"
        destination_folder = os.path.abspath(os.path.join(self.output_folder, case))
        destination1 = os.path.join(destination_folder, self.test_file_1)
        destination2 = os.path.join(destination_folder, self.test_file_2)
        source_folder = "cp://%s/%s/" % (self.bucket_name, case)
        try:
            create_test_file(destination1, TestFiles.DEFAULT_CONTENT)
            expected = ObjectInfo(True).build(destination1)

            pipe_storage_cp(os.path.abspath(self.test_file_1), source_folder)
            pipe_storage_cp(os.path.abspath(self.test_file_2), source_folder)
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_2))

            pipe_storage_cp(source_folder, destination_folder, force=True, recursive=True, skip_existing=True)
            assert os.path.exists(destination1)
            assert os.path.exists(destination2)
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
        """TC-PIPE-STORAGE-97"""
        case = "TC-PIPE-STORAGE-97"
        destination_folder = os.path.abspath(os.path.join(self.output_folder, case))
        destination1 = os.path.join(destination_folder, self.test_file_1)
        destination2 = os.path.join(destination_folder, self.test_file_2)
        source_folder = "cp://%s/%s/" % (self.bucket_name, case)
        try:
            create_test_file(destination1, TestFiles.COPY_CONTENT)
            expected = ObjectInfo(True).build(destination1)

            pipe_storage_cp(os.path.abspath(self.test_file_1), source_folder)
            pipe_storage_cp(os.path.abspath(self.test_file_2), source_folder)
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
            assert object_exists(self.bucket_name, os.path.join(case, self.test_file_2))

            pipe_storage_cp(source_folder, destination_folder, force=True, recursive=True, skip_existing=True)
            assert os.path.exists(destination1)
            assert os.path.exists(destination2)
            actual = ObjectInfo(True).build(destination1)
            assert not expected.size == actual.size, "Sizes must be the different."
            assert not expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be different."
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_copy_folder_between_buckets_with_skip_existing_option_should_skip(self):
        """TC-PIPE-STORAGE-108"""
        case = "TC-PIPE-STORAGE-108"
        source_folder = "cp://%s/%s/" % (self.bucket_name, case)
        destination_folder = "cp://%s/%s/" % (self.other_bucket_name, case)
        key1 = os.path.join(case, self.test_file_1)
        key2 = os.path.join(case, self.test_file_2)
        try:
            expected = create_file_on_bucket(self.other_bucket_name, key1, os.path.abspath(self.test_file_1))

            pipe_storage_cp(os.path.abspath(self.test_file_1), "cp://%s/%s" % (self.bucket_name, key1))
            pipe_storage_cp(os.path.abspath(self.test_file_2), "cp://%s/%s" % (self.bucket_name, key2))
            assert object_exists(self.bucket_name, key1)
            assert object_exists(self.bucket_name, key2)

            pipe_storage_cp(source_folder, destination_folder, force=True, recursive=True, skip_existing=True)
            assert object_exists(self.other_bucket_name, key1)
            assert object_exists(self.other_bucket_name, key2)
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
        """TC-PIPE-STORAGE-109"""
        case = "TC-PIPE-STORAGE-109"
        key1 = os.path.join(case, self.test_file_1)
        key2 = os.path.join(case, self.test_file_2)
        source_folder = "cp://%s/%s/" % (self.bucket_name, case)
        destination_folder = "cp://%s/%s/" % (self.other_bucket_name, case)
        try:
            expected = create_file_on_bucket(self.other_bucket_name, key1, os.path.abspath(self.test_file_2))

            pipe_storage_cp(os.path.abspath(self.test_file_1), "cp://%s/%s" % (self.bucket_name, key1))
            pipe_storage_cp(os.path.abspath(self.test_file_2), "cp://%s/%s" % (self.bucket_name, key2))
            assert object_exists(self.bucket_name, key1)
            assert object_exists(self.bucket_name, key2)

            pipe_storage_cp(source_folder, destination_folder, force=True, recursive=True, skip_existing=True)
            assert object_exists(self.other_bucket_name, key1)
            assert object_exists(self.other_bucket_name, key2)
            actual = ObjectInfo(False).build(self.other_bucket_name, key1)
            assert not expected.size == actual.size, "Sizes must be the different."
            assert not expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be different."
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    def _create_folder_on_bucket(self, case):
        source_files = os.path.abspath(self.test_folder)
        source1 = os.path.join(source_files, self.test_file_1)
        source2 = os.path.join(source_files, self.test_file_with_other_extension)
        source3 = os.path.join(source_files, self.test_folder, self.test_file_1)
        pipe_storage_cp(source1, "cp://%s/%s/%s" % (self.bucket_name, case, self.test_file_1))
        pipe_storage_cp(source2, "cp://%s/%s/%s" % (self.bucket_name, case, self.test_file_with_other_extension))
        pipe_storage_cp(source3, "cp://%s/%s/%s/%s" % (self.bucket_name, case, self.test_folder, self.test_file_1))
