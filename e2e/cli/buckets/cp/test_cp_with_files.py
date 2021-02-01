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

from common_utils.pipe_cli import pipe_storage_rm
from common_utils.test_utils import format_name
from utils.pipeline_utils import get_log_filename

from ..utils.assertions_utils import *
from ..utils.cloud.utilities import *
from ..utils.file_utils import *
from ..utils.tag_assertion_utils import compare_tags
from ..utils.utilities_for_test import *


class TestCopyWithFiles(object):

    raw_bucket_name = "cp-files{}".format(get_test_prefix())
    bucket_name = format_name(raw_bucket_name)
    other_bucket_name = format_name("{}-other".format(raw_bucket_name))
    empty_bucket_name = format_name("{}-empty".format(raw_bucket_name))
    current_directory = os.getcwd()
    home_dir = "test_cp_home_dir-storage-5%s/" % get_test_prefix()
    checkout_dir = "checkout/"
    output_folder = "cp-files-" + TestFiles.TEST_FOLDER_FOR_OUTPUT
    test_file_1 = "cp-files-" + TestFiles.TEST_FILE1
    test_file_2 = "cp-files-" + TestFiles.TEST_FILE2
    test_folder = "cp-files-" + TestFiles.TEST_FOLDER
    test_file_with_spaces = "cp files " + TestFiles.TEST_FILE1

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name, cls.other_bucket_name, cls.empty_bucket_name)
        # /test_folder
        create_test_folder(os.path.abspath(cls.test_folder))
        # /cp-files-test_folder_for_outputs
        create_test_folder(os.path.abspath(cls.output_folder))
        # ./test_file.txt
        create_test_file(os.path.abspath(cls.test_file_1), TestFiles.DEFAULT_CONTENT)
        # ./test_folder/test_file.txt
        create_test_file(os.path.abspath(cls.test_folder + cls.test_file_1), TestFiles.DEFAULT_CONTENT)
        # ./test_file2.txt
        create_test_file(os.path.abspath(cls.test_file_2), TestFiles.COPY_CONTENT)
        # ~/test_cp_home_dir-storage-5/test_file.txt
        create_test_file(os.path.join(os.path.expanduser('~'), cls.home_dir, cls.test_file_1),
                         TestFiles.DEFAULT_CONTENT)
        create_test_file(os.path.abspath(cls.test_file_with_spaces), TestFiles.DEFAULT_CONTENT)

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name, cls.other_bucket_name, cls.empty_bucket_name)
        clean_test_data(os.path.abspath(cls.test_file_1))
        clean_test_data(os.path.abspath(cls.test_file_2))
        clean_test_data(os.path.abspath(cls.test_folder))
        clean_test_data(os.path.abspath(cls.output_folder))
        clean_test_data(os.path.join(os.path.expanduser('~'), cls.home_dir))
        clean_test_data(os.path.abspath(cls.checkout_dir))
        clean_test_data(os.path.abspath(cls.test_file_with_spaces))

    """
    1. test case
    2. source path
    3. relative destination path
    4. additional destination relative path to file (uses for assertion needs)
    5. path to directory if need to switch current directory
    6. relative path to file to rewrite (with --force option)
    """
    test_case_for_upload = [
        ("TC-PIPE-STORAGE-1", os.path.abspath(test_file_1), test_file_1, None, None, None),
        ("TC-PIPE-STORAGE-3", os.path.abspath(test_file_1), test_folder, test_file_1, None, None),
        ("TC-PIPE-STORAGE-5", "~/" + home_dir + test_file_1, test_file_1, None, None, None),
        ("TC-PIPE-STORAGE-7", os.path.abspath(test_file_1), test_file_1, None, None, test_file_2),
        ("TC-PIPE-STORAGE-9", test_file_1, "test_TC-PIPE-STORAGE-9.txt", None, None, None),
        ("TC-PIPE-STORAGE-62", os.path.abspath(test_file_1), "test_TC-PIPE-STORAGE-62.txt", None, None, None),
        ("TC-PIPE-STORAGE-70", os.path.abspath(test_file_with_spaces), "test TC-PIPE-STORAGE-70.txt", None, None, None),
    ]

    @pytest.mark.run(order=1)
    @pytest.mark.parametrize("test_case,source,relative_path,add_file,switch_dir,force", test_case_for_upload)
    def test_file_should_be_uploaded(self, test_case, source, relative_path, add_file, switch_dir, force):
        destination = "cp://{}/{}/{}".format(self.bucket_name, test_case, relative_path)
        if force:
            create_test_files_on_bucket(os.path.abspath(force), self.bucket_name,
                                        os.path.join(test_case, relative_path))
        if add_file:
            path_to_check = "{}/{}/{}".format(test_case, relative_path.strip("/"), add_file)
        else:
            path_to_check = "{}/{}".format(test_case, relative_path)
        if source.startswith("~"):
            source_to_check = os.path.join(os.path.expanduser('~'), source.strip("~/"))
            assert os.path.exists(source_to_check), "The test file does not exist"
        else:
            source_to_check = source
        if not force and object_exists(self.bucket_name, path_to_check):
            pytest.fail("Object {} already exists!".format(destination))
        if switch_dir:
            os.chdir(os.path.dirname(switch_dir))
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, force=bool(force))
        assert_copied_object_info(ObjectInfo(True).build(source_to_check),
                                  ObjectInfo(False).build(self.bucket_name, path_to_check), test_case)
        os.chdir(self.current_directory)

    """
        1. test case
        2. relative source path
        3. destination path
        4. additional source relative path to file (expected file name uses for assertion needs)
        5. relative path to file to rewrite (with --force option)
    """
    test_case_for_download = [
        ("TC-PIPE-STORAGE-1", test_file_1, os.path.abspath(os.path.join(
            output_folder, "TC-PIPE-STORAGE-1", test_file_1)), None, None),
        ("TC-PIPE-STORAGE-3", test_folder + test_file_1,
         os.path.abspath(os.path.join(output_folder, "TC-PIPE-STORAGE-3")) + "/", test_file_1,
         None),
        ("TC-PIPE-STORAGE-5", test_file_1, "~/" + os.path.join(
            home_dir, "TC-PIPE-STORAGE-5", test_file_1), None, None),
        ("TC-PIPE-STORAGE-7", test_file_1, os.path.abspath(os.path.join(
            output_folder, "TC-PIPE-STORAGE-7", test_file_1)), None,
         test_file_2),
        ("TC-PIPE-STORAGE-9", "test_TC-PIPE-STORAGE-9.txt", os.path.join(
            output_folder, "TC-PIPE-STORAGE-9", test_file_1), None, None),
        ("TC-PIPE-STORAGE-9", "test_TC-PIPE-STORAGE-9.txt", os.path.join(
            output_folder, "TC-PIPE-STORAGE-9") + "/", "test_TC-PIPE-STORAGE-9.txt", None),
        ("TC-PIPE-STORAGE-9", "test_TC-PIPE-STORAGE-9.txt", "./", "test_TC-PIPE-STORAGE-9.txt", None),
        ("TC-PIPE-STORAGE-9", "test_TC-PIPE-STORAGE-9.txt", ".", "test_TC-PIPE-STORAGE-9.txt", None),
        ("TC-PIPE-STORAGE-70", "test TC-PIPE-STORAGE-70.txt", os.path.abspath(os.path.join(
            output_folder, "test TC-PIPE-STORAGE-70", test_file_with_spaces)), None, None),
    ]

    @pytest.mark.run(order=2)
    @pytest.mark.parametrize("test_case,relative_path,destination,add_file,force", test_case_for_download)
    def test_file_should_be_downloaded(self, test_case, relative_path, destination, add_file, force):
        source = "cp://{}/{}/{}".format(self.bucket_name, test_case, relative_path)
        if destination.startswith("~"):
            destination_for_checks = os.path.join(os.path.expanduser('~'), destination.strip("~/"))
        else:
            destination_for_checks = destination
        if force:
            create_test_file(destination_for_checks, TestFiles.COPY_CONTENT)
            assert os.path.exists(destination_for_checks), "Test file {} does not exist".format(destination_for_checks)
        if add_file:
            path_to_check = os.path.join(destination_for_checks, add_file)
        else:
            path_to_check = destination_for_checks
        if not object_exists(self.bucket_name, "{}/{}".format(test_case, relative_path)):
            pytest.fail("Object {} to download does not exist!".format(source))
        if not force and os.path.exists(path_to_check):
            pytest.fail("Destination path already exists! Path: {}".format(path_to_check))
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, force=bool(force))
        assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, "{}/{}".format(test_case, relative_path)),
                                  ObjectInfo(True).build(path_to_check), test_case)
        clean_test_data(path_to_check)

    """
        1. test case
        2. relative source path
        3. additional destination relative path to file (uses for assertion needs)
        4. relative path to file to rewrite (with --force option)
    """
    test_case_for_copy_between_buckets = [
        ("TC-PIPE-STORAGE-1", test_file_1, None, None),
        ("TC-PIPE-STORAGE-3", test_folder, test_file_1, None),
        ("TC-PIPE-STORAGE-7", test_file_1, None, test_file_2),
    ]

    @pytest.mark.run(order=3)
    @pytest.mark.parametrize("test_case,relative_path,add_file,force", test_case_for_copy_between_buckets)
    def test_file_should_be_copied(self, test_case, relative_path, add_file, force):
        if add_file:
            path_to_check = "{}/{}/{}".format(test_case, relative_path.strip("/"), add_file)
        else:
            path_to_check = "{}/{}".format(test_case, relative_path)
        source = "cp://{}/{}".format(self.bucket_name, path_to_check)
        destination = "cp://{}/{}/{}".format(self.other_bucket_name, test_case, relative_path)
        if force:
            create_test_files_on_bucket(os.path.abspath(force), self.other_bucket_name,
                                        os.path.join(test_case, relative_path))
        if not object_exists(self.bucket_name, path_to_check):
            pytest.fail("Object {} to copy does not exist!".format(source))
        if not force and object_exists(self.other_bucket_name, path_to_check):
            pytest.fail("Object {} already exists!".format(destination))
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_cp(source, destination, force=bool(force))
        assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, path_to_check),
                                  ObjectInfo(False).build(self.other_bucket_name, path_to_check), test_case)

    test_case_copy_to_not_existing = [
        TestFiles.NOT_EXISTS_FILE,
        TestFiles.NOT_EXISTS_FOLDER
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("path", test_case_copy_to_not_existing)
    def test_copy_to_not_existing_file(self, path):
        """TC-PIPE-STORAGE-33"""
        try:
            error = pipe_storage_cp(os.path.abspath(path), "cp://{}/{}".format(self.bucket_name, path),
                                    expected_status=1)[1]
            assert_error_message_is_present(error, 'Source {} doesn\'t exist'.format(os.path.abspath(path)))
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-33", e.message))

    test_case_copy_from_empty_bucket = [
        ("cp://{}/{}".format(empty_bucket_name, test_file_1), os.path.abspath(test_file_1)),
        ("cp://{}/{}".format(empty_bucket_name, test_file_1),
         "cp://{}/{}/{}".format(bucket_name, "TC-PIPE-STORAGE-33", test_file_1)),
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("source,destination", test_case_copy_from_empty_bucket)
    def test_copy_from_empty_bucket(self, source, destination):
        """TC-PIPE-STORAGE-33"""
        try:
            error = pipe_storage_cp(source, destination, expected_status=1)[1]
            assert_error_message_is_present(error, 'Source {} doesn\'t exist'.format(source))
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-33", e.message))

    test_case_copy_from_not_existing_bucket = [
        ("cp://{}/{}".format("does-not-exist", test_file_1), os.path.abspath(test_file_1)),
        ("cp://{}/{}".format("does-not-exist", test_file_1),
         "cp://{}/{}".format(bucket_name, test_file_1))
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("source,destination", test_case_copy_from_not_existing_bucket)
    def test_copy_from_not_existing_bucket(self, source, destination):
        """TC-PIPE-STORAGE-29"""
        try:
            error = pipe_storage_cp(source, destination, expected_status=1)[1]
            assert_error_message_is_present(error, "Error: data storage with id: '{}/{}' was not found."
                                            .format("does-not-exist", self.test_file_1))
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-29", e.message))

    test_case_copy_to_not_existing_bucket = [
        (os.path.abspath(test_file_1), "cp://{}/{}".format("does-not-exist", test_file_1)),
        ("cp://{}/{}".format(bucket_name, test_file_1),
         "cp://{}/{}".format("does-not-exist", test_file_1))
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("source,destination", test_case_copy_to_not_existing_bucket)
    def test_copy_to_not_existing_bucket(self, source, destination):
        """TC-PIPE-STORAGE-29"""
        self.upload_file_to_bucket(source)
        try:
            error = pipe_storage_cp(source, destination, expected_status=1)[1]
            assert_error_message_is_present(error, "Error: data storage with id: '{}/{}' was not found."
                                            .format("does-not-exist", self.test_file_1))
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-29", e.message))

    test_case_copy_with_wrong_scheme = [
        ("s4://{}/{}".format(bucket_name, test_file_1), os.path.abspath(test_file_1)),
        ("s4://{}/{}".format(bucket_name, test_file_1),
         "cp://{}/{}".format(bucket_name, test_file_1)),
        (os.path.abspath(test_file_1), "s4://{}/{}".format(bucket_name, test_file_1)),
        ("cp://{}/{}".format(bucket_name, "{}/{}".format("TC-PIPE-STORAGE-21", test_file_1)),
         "s4://{}/{}".format(bucket_name, test_file_1))
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("source,destination", test_case_copy_with_wrong_scheme)
    def test_copy_with_wrong_scheme(self, source, destination):
        """TC-PIPE-STORAGE-21"""
        self.upload_file_to_bucket(source)
        try:
            error = pipe_storage_cp(source, destination, force=True, expected_status=1)[1]
            assert_error_message_is_present(error,
                                            'Error: Supported schemes for datastorage are: "cp", "s3", "az", "gs".')
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-21", e.message))

    test_case_copy_with_local_paths = [
        (os.path.abspath(test_file_1), os.path.abspath(test_folder + "TC-PIPE-STORAGE-35-file.txt")),
        (os.path.abspath(test_folder), os.path.abspath("TC-PIPE-STORAGE-35-file/")),
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("source,destination", test_case_copy_with_local_paths)
    def test_copy_with_local_paths(self, source, destination):
        """TC-PIPE-STORAGE-35"""
        try:
            error = pipe_storage_cp(source, destination, recursive=True, expected_status=1)[1]
            assert_error_message_is_present(error, 'Error: Transferring files between the following storage types '
                                                   'LOCAL -> LOCAL is not supported.')
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-35", e.message))

    @pytest.mark.run(order=5)
    def test_copy_with_similar_keys(self):
        """TC-PIPE-STORAGE-59"""
        try:
            source = os.path.abspath(self.test_file_1)
            file_name = "test_file.txt"
            file_name_without_extension = "test_file"
            destination = "cp://{}/{}".format(self.bucket_name, file_name)
            destination_without_extension = "cp://{}/{}".format(self.bucket_name, file_name_without_extension)
            pipe_storage_cp(source, destination)
            assert object_exists(self.bucket_name, file_name)
            pipe_storage_cp(source, destination_without_extension)
            assert object_exists(self.bucket_name, file_name_without_extension)
            pipe_storage_rm(destination_without_extension)
            assert not object_exists(self.bucket_name, file_name_without_extension)
            assert object_exists(self.bucket_name, file_name)
            pipe_storage_rm(destination)
            assert not object_exists(self.bucket_name, file_name)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-59", e.message))

    @pytest.mark.run(order=5)
    def test_copy_with_tags(self):
        """TC-PIPE-STORAGE-61"""
        test_case = "TC-PIPE-STORAGE-61"
        tag1 = ("key1", "value1")
        tag2 = ("key2", "value2")
        file_name = "{}.txt".format(test_case)
        try:
            source = os.path.abspath(self.test_file_1)
            destination = "cp://{}/{}".format(self.bucket_name, file_name)
            pipe_storage_cp(source, destination, tags=[tag1, tag2])
            assert object_exists(self.bucket_name, file_name)
            actual_tags = list_object_tags(self.bucket_name, file_name)
            compare_tags('aws get-object-tagging', [tag1, tag2], actual_tags)
            pipe_storage_rm(destination)
            assert not object_exists(self.bucket_name, file_name)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(test_case, e.message))

    @pytest.mark.run(order=6)
    def test_download_data_with_similar_keys(self):
        """TC-PIPE-STORAGE-63"""
        test_case = "TC-PIPE-STORAGE-63"
        file_name1 = "test_TC-PIPE-STORAGE-63.txt"
        file_name2 = "test_TC-PIPE-STORAGE-63"
        path_to_test_folder = os.path.abspath(test_case)
        logging.info("Temporary folder created %s." % path_to_test_folder)
        try:
            source1 = "cp://{}/{}/{}".format(self.bucket_name, test_case, file_name1)
            pipe_storage_cp(os.path.abspath(self.test_file_1), source1, expected_status=0)
            assert object_exists(self.bucket_name, "%s/%s" % (test_case, file_name1))
            logging.info("File uploaded to %s." % source1)

            source2 = "cp://{}/{}/{}".format(self.bucket_name, test_case, file_name2)
            pipe_storage_cp(os.path.abspath(self.test_file_1), source2, expected_status=0)
            assert object_exists(self.bucket_name, "%s/%s" % (test_case, file_name2))
            logging.info("File uploaded to %s." % source2)

            os.makedirs(path_to_test_folder)
            os.chdir(path_to_test_folder)
            logging.info("Current working directory: %s" % os.getcwd())

            pipe_storage_cp(source2, file_name2)
            logging.info("File downloaded from %s to %s." % (source2, file_name2))
            assert os.path.exists(os.path.abspath(file_name2)), "Downloaded file does not exist by expected path %s" \
                                                                % os.path.abspath(file_name2)
            files = [f for f in os.listdir('.') if os.path.isfile(f)]
            assert len(files) == 1, "Folder content: %s " % os.listdir('.')
            os.chdir(self.current_directory)
            clean_test_data(path_to_test_folder)
        except BaseException as e:
            os.chdir(self.current_directory)
            clean_test_data(path_to_test_folder)
            pytest.fail("Test case {} failed. {}".format(test_case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_files_with_similar_keys(self):
        """TC-PIPE-STORAGE-78"""
        case = "TC-PIPE-STORAGE-78"
        source = os.path.abspath(self.test_folder + "cp-files-test_file")
        try:
            create_test_file(source, TestFiles.DEFAULT_CONTENT)
            pipe_storage_cp(source, "cp://%s/%s/" % (self.bucket_name, case))
            assert object_exists(self.bucket_name, os.path.join(case, "cp-files-test_file"))
            assert not object_exists(self.bucket_name, os.path.join(case, self.test_file_1))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_file_with_skip_existing_option_should_skip(self):
        """TC-PIPE-STORAGE-89"""
        case = "TC-PIPE-STORAGE-89"
        source = os.path.abspath(self.test_file_1)
        key = os.path.join(case, self.test_file_1)
        try:
            expected = create_file_on_bucket(self.bucket_name, key, source)

            pipe_storage_cp(source, "cp://%s/%s" % (self.bucket_name, key), skip_existing=True, force=True)
            assert object_exists(self.bucket_name, key)
            actual = ObjectInfo(False).build(self.bucket_name, key)
            assert expected.size == actual.size, \
                "Sizes must be the same.\nExpected %s\nActual %s" % (expected.size, actual.size)
            assert expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be the same.\n" \
                "Expected %s\nActual %s".format(expected.last_modified, actual.last_modified)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_file_with_skip_existing_option_should_not_skip(self):
        """TC-PIPE-STORAGE-91"""
        case = "TC-PIPE-STORAGE-91"
        key = os.path.join(case, self.test_file_1)
        source = os.path.abspath(self.test_file_1)
        try:
            expected = create_file_on_bucket(self.bucket_name, key, os.path.abspath(self.test_file_2))

            pipe_storage_cp(source, "cp://%s/%s" % (self.bucket_name, key), skip_existing=True, force=True)
            assert object_exists(self.bucket_name, key)
            actual = ObjectInfo(False).build(self.bucket_name, key)
            assert not expected.size == actual.size, "Sizes must be the different."
            assert not expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be different."
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_download_file_with_skip_existing_option_should_skip(self):
        """TC-PIPE-STORAGE-92"""
        case = "TC-PIPE-STORAGE-92"
        key = os.path.join(case, self.test_file_1)
        destination_folder = os.path.abspath(os.path.join(self.output_folder, case))
        destination = os.path.join(destination_folder, key)
        source = "cp://%s/%s" % (self.bucket_name, key)
        try:
            create_test_file(destination, TestFiles.DEFAULT_CONTENT)
            expected = ObjectInfo(True).build(destination)

            pipe_storage_cp(os.path.abspath(self.test_file_1), source)
            assert object_exists(self.bucket_name, key)

            pipe_storage_cp(source, destination, skip_existing=True, force=True)
            assert os.path.exists(destination)
            actual = ObjectInfo(True).build(destination)
            assert expected.size == actual.size, \
                "Sizes must be the same.\nExpected %s\nActual %s" % (expected.size, actual.size)
            assert expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be the same.\n" \
                "Expected %s\nActual %s".format(expected.last_modified, actual.last_modified)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_download_file_with_skip_existing_option_should_not_skip(self):
        """TC-PIPE-STORAGE-94"""
        case = "TC-PIPE-STORAGE-94"
        key = os.path.join(case, self.test_file_1)
        destination_folder = os.path.abspath(os.path.join(self.output_folder, case))
        destination = os.path.join(destination_folder, key)
        source = "cp://%s/%s" % (self.bucket_name, key)
        try:
            create_test_file(destination, TestFiles.COPY_CONTENT)
            expected = ObjectInfo(True).build(destination)

            pipe_storage_cp(os.path.abspath(self.test_file_1), source)
            assert object_exists(self.bucket_name, key)

            pipe_storage_cp(source, destination, skip_existing=True, force=True)
            assert os.path.exists(destination)
            actual = ObjectInfo(True).build(destination)
            assert not expected.size == actual.size, "Sizes must be the different."
            assert not expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be different."
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_copy_between_buckets_file_with_skip_existing_option_should_skip(self):
        """TC-PIPE-STORAGE-106"""
        case = "TC-PIPE-STORAGE-106"
        key = os.path.join(case, self.test_file_1)
        destination = "cp://%s/%s" % (self.other_bucket_name, key)
        source = "cp://%s/%s" % (self.bucket_name, key)
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_1), source)
            assert object_exists(self.bucket_name, key)

            expected = create_file_on_bucket(self.other_bucket_name, key, os.path.abspath(self.test_file_1))

            pipe_storage_cp(source, destination, skip_existing=True, force=True)
            assert object_exists(self.other_bucket_name, key)
            actual = ObjectInfo(False).build(self.other_bucket_name, key)
            assert expected.size == actual.size, \
                "Sizes must be the same.\nExpected %s\nActual %s" % (expected.size, actual.size)
            assert expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be the same.\n" \
                "Expected %s\nActual %s".format(expected.last_modified, actual.last_modified)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_copy_between_buckets_file_with_skip_existing_option_should_not_skip(self):
        """TC-PIPE-STORAGE-107"""
        case = "TC-PIPE-STORAGE-107"
        key = os.path.join(case, self.test_file_1)
        destination = "cp://%s/%s" % (self.other_bucket_name, key)
        source = "cp://%s/%s" % (self.bucket_name, key)
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_2), source)
            assert object_exists(self.bucket_name, key)

            expected = create_file_on_bucket(self.other_bucket_name, key, os.path.abspath(self.test_file_1))

            pipe_storage_cp(source, destination, skip_existing=True, force=True)
            assert object_exists(self.other_bucket_name, key)
            actual = ObjectInfo(False).build(self.other_bucket_name, key)
            assert not expected.size == actual.size, "Sizes must be the different."
            assert not expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be different."
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    def assert_tags_error(self, source, destination, file_name, tags, error_message):
        error = pipe_storage_cp(source, destination, tags=tags, expected_status=1)[1]
        assert not object_exists(self.bucket_name, file_name)
        assert_error_message_is_present(error, error_message)

    def upload_file_to_bucket(self, source):
        if source.startswith("cp://") and not object_exists(self.bucket_name, self.test_file_1):
            pipe_storage_cp(os.path.abspath(self.test_file_1), source, expected_status=0)
