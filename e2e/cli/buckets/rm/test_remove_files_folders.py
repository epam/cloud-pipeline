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
from ..utils.listing import get_pipe_listing, compare_listing, f
from ..utils.assertions_utils import *
from ..utils.utilities_for_test import *


class TestRmFileFolder(object):
    epam_test_case_rm_file = "TC-PIPE-STORAGE-47"
    epam_test_case_rm_folder = "TC-PIPE-STORAGE-51"
    epam_test_case_rm_root = "TC-PIPE-STORAGE-46"
    epam_test_case_rm_not_existing_item = "TC-PIPE-STORAGE-49"
    epam_test_case_rm_from_non_existing_bucket = "TC-PIPE-STORAGE-52"
    epam_test_case_rm_wrong_scheme = "TC-PIPE-STORAGE-50"
    epam_test_case_rm_with_confirmation = "TC-PIPE-STORAGE-57"

    suffix = "storage-47-51-46-52-49"
    resources_root = "resources-{}/".format(suffix).lower()
    bucket_name = format_name("rm-files{}".format(get_test_prefix()).lower())
    empty_bucket_name = format_name("rm-empty".format(get_test_prefix()).lower())
    file_in_root = "file_in_root.txt"
    root_file_path = os.path.abspath(os.path.join(resources_root, file_in_root))

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name, cls.empty_bucket_name)
        # prepare folder
        create_default_test_folder(cls.resources_root)
        # copy folder into child folder
        pipe_storage_cp(cls.resources_root, "cp://{}/{}/".format(cls.bucket_name, cls.resources_root), recursive=True)
        # copy folder into bucket's root
        pipe_storage_cp(cls.resources_root, "cp://{}/".format(cls.bucket_name), recursive=True, force=True)
        # create and copy file into bucket's root
        create_test_file(cls.root_file_path, TestFiles.DEFAULT_CONTENT)
        pipe_storage_cp(cls.root_file_path, "cp://{}/{}".format(cls.bucket_name, cls.file_in_root))

        logging.info("Uploaded test files to bucket {}".format(cls.bucket_name))

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name, cls.empty_bucket_name)
        clean_test_data(os.path.abspath(cls.resources_root))

    test_rm_file = [
        os.path.join(resources_root, TestFiles.TEST_FILE1),
        file_in_root
    ]

    @pytest.mark.run(order=1)
    @pytest.mark.parametrize("path", test_rm_file)
    def test_remove_file(self, path):
        """TC-PIPE-STORAGE-47"""
        try:
            pipe_storage_rm("cp://{}/{}".format(self.bucket_name, path))
            assert not object_exists(self.bucket_name, path), "Failed to delete s3 file object {}".format(path)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_rm_file, e.message))

    test_rm_folder_without_recursive = [
        os.path.join(resources_root, TestFiles.TEST_FOLDER)[:-1],
        os.path.join(resources_root, TestFiles.TEST_FOLDER),
        TestFiles.TEST_FOLDER[:-1],
        TestFiles.TEST_FOLDER
    ]

    @pytest.mark.run(order=2)
    @pytest.mark.parametrize("path", test_rm_folder_without_recursive)
    def test_remove_folder_without_recursive(self, path):
        """TC-PIPE-STORAGE-51"""
        try:
            error_text = pipe_storage_rm("cp://{}/{}".format(self.bucket_name, path), expected_status=1)[1]
            assert_error_message_is_present(error_text, 'Flag --recursive (-r) is required to remove folders.')
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_rm_folder, e.message))

    test_rm_folder = [
        os.path.join(resources_root, TestFiles.TEST_FOLDER),
        TestFiles.TEST_FOLDER[:-1]
    ]

    @pytest.mark.run(order=3)
    @pytest.mark.parametrize("path", test_rm_folder)
    def test_remove_folder(self, path):
        """TC-PIPE-STORAGE-51"""
        try:
            pipe_storage_rm("cp://{}/{}".format(self.bucket_name, path), recursive=True, expected_status=0)
            assert not folder_exists(self.bucket_name, path), "Failed to delete s3 folder {}".format(path)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_rm_folder, e.message))

    test_rm_root = [
        (bucket_name, True),
        (bucket_name, False),
        (bucket_name + "/", True),
        (bucket_name + "/", False)
    ]

    @pytest.mark.run(order=4)
    @pytest.mark.parametrize("path,recursive", test_rm_root)
    def test_deleting_root_is_not_allowed(self, path, recursive):
        """TC-PIPE-STORAGE-46"""
        try:
            error = pipe_storage_rm("cp://{}".format(path), recursive=recursive, expected_status=1)[1]
            assert_error_message_is_present(error, "Cannot remove root folder 'cp://{}'".format(path))
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_rm_root, e.message))

    test_rm_not_existing = [
        "file_does_not_exists.txt",
        "folder_does_not_exists/"
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("path", test_rm_not_existing)
    def test_deleting_not_existing_file(self, path):
        """TC-PIPE-STORAGE-49"""
        try:
            error = pipe_storage_rm("cp://{}/{}".format(self.bucket_name, path), expected_status=1)[1]
            assert_error_message_is_present(error,
                                            'Storage path "cp://{}/{}" was not found'.format(self.bucket_name, path))
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_rm_not_existing_item, e.message))

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("path", test_rm_not_existing)
    def test_remove_from_empty_bucket(self, path):
        """TC-PIPE-STORAGE-52"""
        try:
            error = pipe_storage_rm("cp://{}/{}".format(self.empty_bucket_name, path), expected_status=1)[1]
            assert_error_message_is_present(error,
                                            'Storage path "cp://{}/{}" was not found'.format(self.empty_bucket_name,
                                                                                             path))
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_rm_from_non_existing_bucket, e.message))

    test_rm_not_existing = [
        "file_does_not_exists.txt",
        "folder_does_not_exists/"
    ]

    @pytest.mark.run(order=5)
    @pytest.mark.parametrize("path", test_rm_not_existing)
    def test_remove_from_not_existing_bucket(self, path):
        """TC-PIPE-STORAGE-52"""
        bucket_name = "does-not-exist"
        try:
            error = pipe_storage_rm("cp://{}/{}".format(bucket_name, path), expected_status=1)[1]
            assert_error_message_is_present(error, "Error: data storage with id: '{}/{}' was not found."
                                            .format(bucket_name, path))
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_rm_from_non_existing_bucket, e.message))

    @pytest.mark.run(order=5)
    def test_remove_from_wrong_scheme(self):
        """TC-PIPE-STORAGE-50"""
        try:
            error = pipe_storage_rm("s4://{}/test.txt".format(self.bucket_name), expected_status=1)[1]
            assert_error_message_is_present(error,
                                            'Error: Supported schemes for datastorage are: "cp", "s3", "az", "gs".')
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_rm_wrong_scheme, e.message))

    @pytest.mark.run(order=5)
    def test_remove_with_confirmation(self):
        """TC-PIPE-STORAGE-57"""
        try:
            source = self.root_file_path
            destination = 'cp://{}/{}'.format(self.bucket_name, self.epam_test_case_rm_with_confirmation + ".txt")
            pipe_storage_cp(source, destination, expected_status=0)
            pipe_storage_rm_piped(destination, delete=False)
            pipe_output = get_pipe_listing(destination)
            aws_output = [f('TC-PIPE-STORAGE-57.txt', 10)]
            compare_listing(pipe_output, aws_output, 1)
            pipe_storage_rm_piped(destination, delete=True)
            pipe_output = get_pipe_listing(destination)
            aws_output = []
            compare_listing(pipe_output, aws_output, 0)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_rm_with_confirmation, e.message))

    @pytest.mark.run(order=5)
    def test_remove_folder_with_same_name_as_file(self):
        """TC-PIPE-STORAGE-72"""
        case = "TC-PIPE-STORAGE-72"
        try:
            pipe_storage_cp(self.root_file_path, "cp://%s/%s/" % (self.bucket_name, case))
            assert object_exists(self.bucket_name, "%s/%s" % (case, self.file_in_root))
            pipe_storage_cp(self.root_file_path, "cp://%s/%s/%s/" % (self.bucket_name, case, self.file_in_root))
            assert object_exists(self.bucket_name, "%s/%s/%s" % (case, self.file_in_root, self.file_in_root))

            pipe_storage_rm("cp://%s/%s/%s/" % (self.bucket_name, case, self.file_in_root), recursive=True)
            assert object_exists(self.bucket_name, "%s/%s" % (case, self.file_in_root))
            assert not object_exists(self.bucket_name, "%s/%s/%s" % (case, self.file_in_root, self.file_in_root))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=5)
    def test_remove_file_with_same_name_as_folder(self):
        """TC-PIPE-STORAGE-71"""
        case = "TC-PIPE-STORAGE-71"
        try:
            pipe_storage_cp(self.root_file_path, "cp://%s/%s/" % (self.bucket_name, case))
            assert object_exists(self.bucket_name, "%s/%s" % (case, self.file_in_root))
            pipe_storage_cp(self.root_file_path, "cp://%s/%s/%s/" % (self.bucket_name, case, self.file_in_root))
            assert object_exists(self.bucket_name, "%s/%s/%s" % (case, self.file_in_root, self.file_in_root))

            pipe_storage_rm("cp://%s/%s/%s" % (self.bucket_name, case, self.file_in_root), recursive=True)
            assert not object_exists(self.bucket_name, "%s/%s" % (case, self.file_in_root))
            assert object_exists(self.bucket_name, "%s/%s/%s" % (case, self.file_in_root, self.file_in_root))
        except BaseException as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))
