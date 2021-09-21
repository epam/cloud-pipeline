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
from ..utils.cloud.utilities import *
from ..utils.file_utils import *
from ..utils.utilities_for_test import *


class TestMoveWithFiles(object):

    raw_bucket_name = "mv-files{}".format(get_test_prefix())
    bucket_name = format_name(raw_bucket_name)
    other_bucket_name = format_name("{}-other".format(raw_bucket_name))
    empty_bucket_name = format_name("{}-empty".format(raw_bucket_name))
    current_directory = os.getcwd()
    home_dir = "test_mv_home_dir-storage-6%s/" % get_test_prefix()
    test_prefix = "mv-files-"
    output_folder = test_prefix + TestFiles.TEST_FOLDER_FOR_OUTPUT
    test_file_1 = test_prefix + TestFiles.TEST_FILE1
    test_file_2 = test_prefix + TestFiles.TEST_FILE2
    test_folder = test_prefix + TestFiles.TEST_FOLDER
    source_dir = test_prefix + "sources%s/" % get_test_prefix()
    upload_folder_case = "TC-PIPE-STORAGE-75"

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name, cls.other_bucket_name, cls.empty_bucket_name)
        # ./test_file.txt
        create_test_file(os.path.abspath(cls.source_dir + cls.test_file_1), TestFiles.DEFAULT_CONTENT)
        # ./test_folder
        create_test_folder(os.path.abspath(cls.source_dir + cls.test_folder))
        # ./test_folder_for_outputs
        create_test_folder(os.path.abspath(cls.output_folder))
        # ./test_folder/test_file.txt
        create_test_file(os.path.abspath(cls.source_dir + cls.test_folder + cls.test_file_1), TestFiles.DEFAULT_CONTENT)
        # ./test_file2.txt
        create_test_file(os.path.abspath(cls.source_dir + cls.test_file_2), TestFiles.COPY_CONTENT)
        # ~/sources/test_mv_home_dir-storage-6/test_file.txt
        create_test_file(os.path.join(os.path.expanduser('~'), cls.source_dir, cls.home_dir, cls.test_file_1),
                         TestFiles.DEFAULT_CONTENT)
        create_test_folder(os.path.abspath(os.path.join(cls.source_dir, cls.upload_folder_case)))
        create_test_file(os.path.abspath(os.path.join(cls.source_dir, cls.upload_folder_case, cls.test_file_1)),
                         TestFiles.DEFAULT_CONTENT)
        create_test_file(os.path.abspath(os.path.join(cls.source_dir, cls.upload_folder_case, cls.test_prefix)),
                         TestFiles.DEFAULT_CONTENT)

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name, cls.other_bucket_name, cls.empty_bucket_name)
        clean_test_data(os.path.abspath(cls.output_folder))
        clean_test_data(os.path.join(os.path.expanduser('~'), cls.source_dir))
        clean_test_data(os.path.join(os.path.expanduser('~'), cls.home_dir))
        clean_test_data(cls.source_dir)

    """
    1. test case
    2. source path
    3. relative destination path
    4. additional destination relative path to file (uses for assertion needs)
    5. path to directory if need to switch current directory
    6. relative path to file to rewrite (with --force option)
    """
    test_case_for_upload = [
        ("TC-PIPE-STORAGE-2", os.path.abspath(test_file_1), test_file_1, None, None),
        ("TC-PIPE-STORAGE-4", os.path.abspath(test_file_1), test_folder, test_file_1, None),
        ("TC-PIPE-STORAGE-6", "~/" + home_dir + test_file_1, test_file_1, None, None),
        ("TC-PIPE-STORAGE-8", os.path.abspath(test_file_1), test_file_1, None, test_file_2),
        ("TC-PIPE-STORAGE-10", test_file_1, "test_TC-PIPE-STORAGE-10.txt", None, None),
    ]

    @pytest.mark.run(order=1)
    @pytest.mark.parametrize("test_case,source,relative_path,add_file,force", test_case_for_upload)
    def test_file_should_be_uploaded(self, test_case, source, relative_path, add_file, force):
        key = os.path.join(test_case, relative_path)
        destination = "cp://{}/{}".format(self.bucket_name, key)
        if force:
            create_test_files_on_bucket(os.path.abspath(self.source_dir + force), self.bucket_name, key)
        if add_file:
            destination_key_for_checks = os.path.join(key, add_file)
        else:
            destination_key_for_checks = key
        if source.startswith("~"):
            source_for_checks = os.path.join(os.path.expanduser('~'), source.strip("~/"))
        else:
            source_for_checks = source
        if not force and object_exists(self.bucket_name, destination_key_for_checks):
            pytest.fail("Object {} already exists!".format(destination))
        create_test_file(os.path.abspath(source_for_checks), TestFiles.DEFAULT_CONTENT)
        assert os.path.exists(source_for_checks), "Test file does not exist!"
        source_object = ObjectInfo(True).build(source_for_checks)
        logging.info("Ready to perform mv operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, force=bool(force), expected_status=0)
        assert_copied_object_info(source_object,
                                  ObjectInfo(False).build(self.bucket_name, destination_key_for_checks), test_case)
        assert_files_deleted(None, source_for_checks)

    """
        1. test case
        2. relative source path
        3. destination path
        4. additional source relative path to file (expected file name uses for assertion needs)
        5. relative path to file to rewrite (with --force option)
    """
    test_case_for_download = [
        ("TC-PIPE-STORAGE-2", test_file_1, os.path.abspath(os.path.join(
            output_folder, "TC-PIPE-STORAGE-2", test_file_1)), None, None),
        ("TC-PIPE-STORAGE-4", test_folder + test_file_1, os.path.abspath(os.path.join(
            output_folder, "TC-PIPE-STORAGE-4")) + "/", test_file_1, None),
        ("TC-PIPE-STORAGE-6", test_file_1, "~/" + os.path.join(home_dir, "TC-PIPE-STORAGE-6", test_file_1), None, None),
        ("TC-PIPE-STORAGE-8", test_file_1, os.path.abspath(os.path.join(
            output_folder, "TC-PIPE-STORAGE-8", test_file_1)), None, test_file_2),
        ("TC-PIPE-STORAGE-10", "test_TC-PIPE-STORAGE-10.txt", os.path.join(
            output_folder, "TC-PIPE-STORAGE-10", test_file_1), None, None),
        ("TC-PIPE-STORAGE-10", "test_TC-PIPE-STORAGE-10.txt", os.path.join(
            output_folder, "TC-PIPE-STORAGE-10") + "/", "test_TC-PIPE-STORAGE-10.txt", None),
        ("TC-PIPE-STORAGE-10", "test_TC-PIPE-STORAGE-10.txt", "./", "test_TC-PIPE-STORAGE-10.txt", None),
        ("TC-PIPE-STORAGE-10", "test_TC-PIPE-STORAGE-10.txt", ".", "test_TC-PIPE-STORAGE-10.txt", None),
    ]

    @pytest.mark.run(order=2)
    @pytest.mark.parametrize("test_case,relative_path,destination,add_file,force", test_case_for_download)
    def test_file_should_be_downloaded(self, test_case, relative_path, destination, add_file, force):
        key = os.path.join(test_case, relative_path)
        source = "cp://{}/{}".format(self.bucket_name, key)
        if destination.startswith("~"):
            destination_for_checks = os.path.join(os.path.expanduser('~'), destination.strip("~/"))
        else:
            destination_for_checks = destination
        if force:
            create_test_file(destination_for_checks, TestFiles.COPY_CONTENT)
            assert os.path.exists(destination_for_checks), "File for test --force option doesn't exists"
        if add_file:
            destination_for_checks = os.path.join(destination_for_checks, add_file)
        if not object_exists(self.bucket_name, key):
            pipe_storage_cp(self.source_dir + self.test_file_1, source, expected_status=0)
        if not force and os.path.exists(destination_for_checks):
            pytest.fail("Destination path already exists! Path: {}".format(destination_for_checks))
        source_object = ObjectInfo(False).build(self.bucket_name, key)
        logging.info("Ready to perform mv operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, force=bool(force), expected_status=0)
        assert_copied_object_info(source_object, ObjectInfo(True).build(destination_for_checks), test_case)
        assert_files_deleted(self.bucket_name, key)
        clean_test_data(destination_for_checks)

    """
        1. test case
        2. relative source path
        3. additional destination relative path to file (uses for assertion needs)
        4. relative path to file to rewrite (with --force option)
    """
    test_case_for_move_between_buckets = [
        ("TC-PIPE-STORAGE-2", test_file_1, None, None),
        ("TC-PIPE-STORAGE-4", test_folder, test_file_1, None),
        ("TC-PIPE-STORAGE-8", test_file_1, None, test_file_2),
    ]

    @pytest.mark.run(order=3)
    @pytest.mark.parametrize("test_case,relative_path,add_file,force", test_case_for_move_between_buckets)
    def test_file_should_be_copied(self, test_case, relative_path, add_file, force):
        if add_file:
            source_key_for_checks = os.path.join(test_case, relative_path, add_file)
        else:
            source_key_for_checks = os.path.join(test_case, relative_path)
        source = "cp://{}/{}".format(self.bucket_name, source_key_for_checks)
        destination = "cp://{}/{}/{}".format(self.other_bucket_name, test_case, relative_path)
        if force:
            create_test_files_on_bucket(os.path.abspath(self.source_dir + force), self.other_bucket_name,
                                        os.path.join(test_case, relative_path))
        if not force and object_exists(self.other_bucket_name, source_key_for_checks):
            pytest.fail("Object {} already exists!".format(destination))
        create_test_files_on_bucket(self.source_dir + self.test_file_1, self.bucket_name, source_key_for_checks)
        source_object = ObjectInfo(False).build(self.bucket_name, source_key_for_checks)
        logging.info("Ready to perform operation from {} to {}".format(source, destination))
        pipe_storage_mv(source, destination, force=bool(force), expected_status=0)
        assert_copied_object_info(source_object,
                                  ObjectInfo(False).build(self.other_bucket_name, source_key_for_checks), test_case)
        assert_files_deleted(self.bucket_name, source_key_for_checks)

    test_case_move_to_not_existing = [
        TestFiles.NOT_EXISTS_FILE,
        TestFiles.NOT_EXISTS_FOLDER
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("path", test_case_move_to_not_existing)
    def test_move_to_not_existing_file(self, path):
        """TC-PIPE-STORAGE-34"""
        try:
            error = pipe_storage_mv(os.path.abspath(path), "cp://{}/{}".format(self.bucket_name, path),
                                    expected_status=1)[1]
            assert_error_message_is_present(error, 'Source {} doesn\'t exist'.format(os.path.abspath(path)))
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-34", e.message))

    test_case_move_from_not_existing_bucket = [
        ("cp://{}/{}".format("does-not-exist", test_file_1), os.path.abspath(test_file_1)),
        ("cp://{}/{}".format("does-not-exist", test_file_1),
         "cp://{}/{}".format(bucket_name, test_file_1))
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("source,destination", test_case_move_from_not_existing_bucket)
    def test_move_from_not_existing_bucket(self, source, destination):
        """CP-PIPE-STORAGE-30"""
        try:
            error = pipe_storage_mv(source, destination, expected_status=1)[1]
            assert_error_message_is_present(error, "Error: data storage with id: '{}/{}' was not found."
                                            .format("does-not-exist", self.test_file_1))
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-30", e.message))

    test_case_move_to_not_existing_bucket = [
        (os.path.abspath(source_dir + test_file_1), "cp://{}/{}".format("does-not-exist", test_file_1)),
        ("cp://{}/{}".format(bucket_name, test_file_1),
         "cp://{}/{}".format("does-not-exist", test_file_1))
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("source,destination", test_case_move_to_not_existing_bucket)
    def test_move_to_not_existing_bucket(self, source, destination):
        """CP-PIPE-STORAGE-30"""
        self.upload_file_to_bucket(source)
        try:
            error = pipe_storage_mv(source, destination, expected_status=1)[1]
            assert_error_message_is_present(error, "Error: data storage with id: '{}/{}' was not found."
                                            .format("does-not-exist", self.test_file_1))
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-30", e.message))

    test_case_move_with_wrong_scheme = [
        ("s4://{}/{}".format(bucket_name, test_file_1), os.path.abspath(test_file_1)),
        ("s4://{}/{}".format(bucket_name, test_file_1),
         "cp://{}/{}".format(bucket_name, test_file_1)),
        (os.path.abspath(source_dir + test_file_1), "s4://{}/{}".format(bucket_name, test_file_1)),
        ("cp://{}/{}".format(bucket_name, "{}/{}".format("TC-PIPE-STORAGE-22", test_file_1)),
         "s4://{}/{}".format(bucket_name, test_file_1))
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("source,destination", test_case_move_with_wrong_scheme)
    def test_move_with_wrong_scheme(self, source, destination):
        """TC-PIPE-STORAGE-22"""
        self.upload_file_to_bucket(source)
        try:
            error = pipe_storage_mv(source, destination, force=True, expected_status=1)[1]
            assert_error_message_is_present(error,
                                            'Error: Supported schemes for datastorage are: "cp", "s3", "az", "gs".')
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-22", e.message))

    test_case_move_with_local_paths = [
        (os.path.abspath(source_dir + test_file_1), os.path.abspath(output_folder + "TC-PIPE-STORAGE-36-1.txt")),
        (os.path.abspath(source_dir + test_folder), os.path.abspath("TC-PIPE-STORAGE-36-1/")),
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("source,destination", test_case_move_with_local_paths)
    def test_move_with_local_paths(self, source, destination):
        """TC-PIPE-STORAGE-36"""
        try:
            error = pipe_storage_mv(source, destination, recursive=True, expected_status=1)[1]
            assert_error_message_is_present(error, 'Error: Transferring files between the following storage types '
                                                   'LOCAL -> LOCAL is not supported.')
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-36", e.message))

    @pytest.mark.run(order=6)
    def test_download_data_with_similar_keys(self):
        """TC-PIPE-STORAGE-74"""
        test_case = "TC-PIPE-STORAGE-74"
        file_name1 = "test_TC-PIPE-STORAGE-74.txt"
        file_name2 = "test_TC-PIPE-STORAGE-74"
        path_to_test_folder = os.path.abspath(test_case)
        logging.info("Temporary folder created %s." % path_to_test_folder)
        try:
            source1 = "cp://{}/{}/{}".format(self.bucket_name, test_case, file_name1)
            pipe_storage_cp(os.path.abspath(self.source_dir + self.test_file_1), source1)
            assert object_exists(self.bucket_name, "%s/%s" % (test_case, file_name1))
            logging.info("File uploaded to %s." % source1)

            source2 = "cp://{}/{}/{}".format(self.bucket_name, test_case, file_name2)
            pipe_storage_cp(os.path.abspath(self.source_dir + self.test_file_2), source2)
            assert object_exists(self.bucket_name, "%s/%s" % (test_case, file_name2))
            logging.info("File uploaded to %s." % source2)

            os.makedirs(path_to_test_folder)
            os.chdir(path_to_test_folder)
            logging.info("Current working directory: %s" % os.getcwd())

            pipe_storage_mv(source2, file_name2)
            logging.info("File downloaded from %s to %s." % (source2, file_name2))
            assert os.path.exists(os.path.abspath(file_name2)), "Downloaded file does not exist by expected path %s" \
                                                                % os.path.abspath(file_name2)
            files = [f for f in os.listdir('.') if os.path.isfile(f)]
            assert len(files) == 1, "Folder content: %s " % os.listdir('.')
            assert_files_skipped(self.bucket_name, os.path.join(test_case, file_name1))
            os.chdir(self.current_directory)
            clean_test_data(path_to_test_folder)
        except BaseException as e:
            os.chdir(self.current_directory)
            clean_test_data(path_to_test_folder)
            pytest.fail("Test case {} failed. {}".format(test_case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_data_with_similar_keys(self):
        """TC-PIPE-STORAGE-75"""
        source = os.path.abspath(os.path.join(self.source_dir, self.upload_folder_case, self.test_prefix))
        destination = "cp://%s/%s/" % (self.bucket_name, self.upload_folder_case)
        source_for_check = ObjectInfo(True).build(source)
        try:
            pipe_storage_mv(source, destination)
            logging.info("File uploaded from %s to %s." % (source, destination))
            assert_copied_object_info(source_for_check, ObjectInfo(False)
                                      .build(self.bucket_name, os.path.join(self.upload_folder_case, self.test_prefix)),
                                      self.upload_folder_case)
            assert_files_deleted(None, source)
            assert not object_exists(self.bucket_name, os.path.join(self.upload_folder_case, self.test_file_1))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(self.upload_folder_case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_file_with_skip_existing_option_should_skip(self):
        """TC-PIPE-STORAGE-93"""
        case = "TC-PIPE-STORAGE-93"
        key = os.path.join(case, self.test_file_1)
        source_folder = os.path.abspath(os.path.join(self.test_folder, case))
        source1 = os.path.join(source_folder, self.test_file_1)
        try:
            create_test_folder(source_folder)
            create_test_file(source1, TestFiles.DEFAULT_CONTENT)

            expected = create_file_on_bucket(self.bucket_name, key, source1)

            pipe_storage_mv(source1, "cp://%s/%s" % (self.bucket_name, key), skip_existing=True, force=True)
            assert object_exists(self.bucket_name, key)
            actual = ObjectInfo(False).build(self.bucket_name, key)
            assert expected.size == actual.size, \
                "Sizes must be the same.\nExpected %s\nActual %s" % (expected.size, actual.size)
            assert expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be the same.\n" \
                "Expected %s\nActual %s".format(expected.last_modified, actual.last_modified)
            assert os.path.exists(source1)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_upload_file_with_skip_existing_option_should_not_skip(self):
        """TC-PIPE-STORAGE-95"""
        case = "TC-PIPE-STORAGE-95"
        key = os.path.join(case, self.test_file_1)
        source_folder = os.path.abspath(os.path.join(self.test_folder, case))
        source1 = os.path.join(source_folder, self.test_file_1)
        source2 = os.path.join(source_folder, self.test_file_2)
        destination = "cp://%s/%s" % (self.bucket_name, key)
        try:
            create_test_folder(source_folder)
            create_test_file(source1, TestFiles.DEFAULT_CONTENT)
            create_test_file(source2, TestFiles.COPY_CONTENT)

            expected = create_file_on_bucket(self.bucket_name, key, source2)

            pipe_storage_mv(source1, destination, force=True, recursive=True, skip_existing=True)
            assert object_exists(self.bucket_name, key)
            actual = ObjectInfo(False).build(self.bucket_name, key)
            assert not expected.size == actual.size, "Sizes must be the different."
            assert not expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be different."
            assert not os.path.exists(source1)
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_download_file_with_skip_existing_option_should_skip(self):
        """TC-PIPE-STORAGE-98"""
        case = "TC-PIPE-STORAGE-98"
        key = os.path.join(case, self.test_file_1)
        destination_folder = os.path.abspath(os.path.join(self.output_folder, case))
        destination = os.path.join(destination_folder, key)
        source = "cp://%s/%s" % (self.bucket_name, key)
        try:
            create_test_file(destination, TestFiles.DEFAULT_CONTENT)
            assert os.path.exists(destination)
            expected = ObjectInfo(True).build(destination)

            pipe_storage_cp(os.path.abspath(os.path.join(self.source_dir, self.test_file_1)), source)
            assert object_exists(self.bucket_name, key)

            pipe_storage_mv(source, destination, skip_existing=True, force=True)
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
        """TC-PIPE-STORAGE-99"""
        case = "TC-PIPE-STORAGE-99"
        key = os.path.join(case, self.test_file_1)
        destination_folder = os.path.abspath(os.path.join(self.output_folder, case))
        destination = os.path.join(destination_folder, key)
        source = "cp://%s/%s" % (self.bucket_name, key)
        try:
            create_test_file(destination, TestFiles.COPY_CONTENT)
            assert os.path.exists(destination)
            expected = ObjectInfo(True).build(destination)

            pipe_storage_cp(os.path.abspath(os.path.join(self.source_dir, self.test_file_1)), source)
            assert object_exists(self.bucket_name, key)

            pipe_storage_mv(source, destination, skip_existing=True, force=True)
            assert os.path.exists(destination)
            actual = ObjectInfo(True).build(destination)
            assert not expected.size == actual.size, "Sizes must be the different."
            assert not expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be different."
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=6)
    def test_copy_between_buckets_file_with_skip_existing_option_should_skip(self):
        """TC-PIPE-STORAGE-110"""
        case = "TC-PIPE-STORAGE-110"
        key = os.path.join(case, self.test_file_1)
        destination = "cp://%s/%s" % (self.other_bucket_name, key)
        source = "cp://%s/%s" % (self.bucket_name, key)
        source_file = os.path.abspath(os.path.join(self.source_dir, self.test_file_1))
        try:
            pipe_storage_cp(source_file, source)
            assert object_exists(self.bucket_name, key)

            expected = create_file_on_bucket(self.other_bucket_name, key, source_file)

            pipe_storage_mv(source, destination, skip_existing=True, force=True)
            assert object_exists(self.other_bucket_name, key)
            assert object_exists(self.bucket_name, key)
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
        """TC-PIPE-STORAGE-111"""
        case = "TC-PIPE-STORAGE-111"
        key = os.path.join(case, self.test_file_1)
        destination = "cp://%s/%s" % (self.other_bucket_name, key)
        source = "cp://%s/%s" % (self.bucket_name, key)
        try:
            pipe_storage_cp(os.path.abspath(os.path.join(self.source_dir, self.test_file_2)), source)
            assert object_exists(self.bucket_name, key)

            expected = create_file_on_bucket(self.other_bucket_name, key,
                                             os.path.abspath(os.path.join(self.source_dir, self.test_file_1)))

            pipe_storage_mv(source, destination, skip_existing=True, force=True)
            assert object_exists(self.other_bucket_name, key)
            assert not object_exists(self.bucket_name, key)
            actual = ObjectInfo(False).build(self.other_bucket_name, key)
            assert not expected.size == actual.size, "Sizes must be the different."
            assert not expected.last_modified == actual.last_modified, \
                "Last modified time of destination and source file must be different."
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    def upload_file_to_bucket(self, source):
        if source.startswith("cp://") and not object_exists(self.bucket_name, self.source_dir + self.test_file_1):
            pipe_storage_cp(os.path.abspath(self.source_dir + self.test_file_1), source, expected_status=0)
