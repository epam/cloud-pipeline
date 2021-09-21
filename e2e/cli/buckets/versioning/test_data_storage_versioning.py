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

import pytest

from buckets.utils.cloud.azure_client import AzureClient
from buckets.utils.cloud.google_client import GsClient
from buckets.utils.cloud.utilities import object_exists, get_versions
from buckets.utils.listing import *
from buckets.utils.file_utils import *
from common_utils.pipe_cli import *
from common_utils.test_utils import format_name

ERROR_MESSAGE = "An error occurred in case "


@pytest.mark.skipif(os.environ['CP_PROVIDER'] == AzureClient.name,
                    reason="Versioning is not supported for AZURE provider")
class TestDataStorageVersioning(object):

    test_file_1 = "versioning1.txt"
    test_file_1_without_extension = "versioning1"
    test_file_2 = "versioning2.txt"
    test_file_3 = "versioning3.txt"
    test_folder_1 = "test_folder1"
    test_folder_2 = "test_folder2"
    test_folder_3 = "test_folder3"
    bucket = format_name('versions{}'.format(get_test_prefix()))
    path_to_bucket = 'cp://{}'.format(bucket)
    token = os.environ['USER_TOKEN']
    user = os.environ['TEST_USER']

    @classmethod
    def setup_class(cls):
        create_data_storage(cls.bucket, versioning=True)
        create_test_file(os.path.abspath(cls.test_file_1), TestFiles.DEFAULT_CONTENT)
        create_test_file(os.path.abspath(cls.test_file_2), TestFiles.COPY_CONTENT)

    @classmethod
    def teardown_class(cls):
        clean_test_data(os.path.abspath(cls.test_file_1))
        clean_test_data(os.path.abspath(cls.test_file_2))
        delete_data_storage(cls.bucket)

    def teardown_method(self, method):
        pipe_output = get_pipe_listing(self.path_to_bucket, recursive=True)
        for record in pipe_output:
            pipe_storage_rm('cp://{}/{}'.format(self.bucket, record.name), args=['--hard-delete'], recursive=True,
                            expected_status=None)
        pipe_output = get_pipe_listing(self.path_to_bucket, recursive=True)
        assert len(pipe_output) == 0
        pipe_storage_rm('cp://{}/{}'.format(self.bucket, self.test_file_1), args=['--hard-delete'], recursive=True,
                        expected_status=None)
        pipe_storage_rm('cp://{}/{}'.format(self.bucket, self.test_folder_1), args=['--hard-delete'], recursive=True,
                        expected_status=None)

    def test_list_marked_for_deletion_object(self):
        """TC-PIPE-STORAGE-114"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        try:
            pipe_storage_cp(self.test_file_1, destination)
            pipe_storage_rm(destination)
            actual_output = get_pipe_listing(self.path_to_bucket)
            assert len(actual_output) == 0
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing(self.path_to_bucket, versioning=True))
            expected_output = [
                f(self.test_file_1, deleted=True, latest=True),
                f(self.test_file_1, 10, added=True)
            ]
            compare_listing(actual_output, expected_output, 2)
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-114:" + "\n" + e.message)

    def test_restore_marked_for_deletion_file(self):
        """TC-PIPE-STORAGE-115"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        try:
            pipe_storage_cp(self.test_file_1, destination)
            pipe_storage_rm(destination)
            output = pipe_storage_ls(self.path_to_bucket, show_details=False)[0]
            assert len(output) == 0
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing(self.path_to_bucket, versioning=True))
            expected_output = [
                f(self.test_file_1, deleted=True, latest=True),
                f(self.test_file_1, 10, added=True)
            ]
            compare_listing(actual_output, expected_output, 2)
            pipe_storage_restore(destination)
            actual_output = get_pipe_listing(self.path_to_bucket)
            expected_output = [
                f(self.test_file_1, 10)
            ]
            compare_listing(actual_output, expected_output, 1)
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-115:" + "\n" + e.message)

    def test_object_versions(self):
        """TC-PIPE-STORAGE-116"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        try:
            pipe_storage_cp(self.test_file_1, destination)
            pipe_storage_cp(self.test_file_2, destination, force=True)  # another file with same name
            actual_output = get_pipe_listing(self.path_to_bucket)
            expected_output = [
                f(self.test_file_1, 14)
            ]
            compare_listing(actual_output, expected_output, 1)
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing(self.path_to_bucket, versioning=True))
            expected_output = [
                f(self.test_file_1, 14, added=True, latest=True),
                f(self.test_file_1, 10, added=True)
            ]
            compare_listing(actual_output, expected_output, 2)
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-116:" + "\n" + e.message)

    def test_restore_specific_version(self):
        """TC-PIPE-STORAGE-117"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        try:
            pipe_storage_cp(self.test_file_1, destination)
            pipe_storage_cp(self.test_file_2, destination, force=True)
            pipe_storage_rm(destination)
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing(self.path_to_bucket, versioning=True))
            expected_output = [
                f(self.test_file_1, deleted=True, latest=True),
                f(self.test_file_1, 14, added=True),
                f(self.test_file_1, 10, added=True)
            ]
            compare_listing(actual_output, expected_output, 3, sort=False)
            version = get_non_latest_version(actual_output)
            assert version, "No version available to restore."
            pipe_storage_restore(destination, version=version, expected_status=0)
            actual_output = get_pipe_listing(self.path_to_bucket)
            expected_output = [
                f(self.test_file_1, 14)
            ]
            compare_listing(actual_output, expected_output, 1)
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-117:" + "\n" + e.message)

    def test_object_hard_deletion(self):
        """TC-PIPE-STORAGE-118"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination)
            pipe_storage_rm(destination, args=['--hard-delete'])
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing(self.path_to_bucket, versioning=True), result_not_empty=False)
            expected_output = []
            compare_listing(actual_output, expected_output, 0)
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-118:" + "\n" + e.message)

    def test_marked_object_hard_deletion(self):
        """TC-PIPE-STORAGE-124"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination)
            pipe_storage_rm(destination)
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing(self.path_to_bucket, versioning=True))
            expected_output = [
                f(self.test_file_1, deleted=True, latest=True),
                f(self.test_file_1, 10, added=True),
            ]
            compare_listing(actual_output, expected_output, 2)
            pipe_storage_rm(destination, args=['--hard-delete'], recursive=True)
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing(self.path_to_bucket, versioning=True), result_not_empty=False)
            expected_output = []
            compare_listing(actual_output, expected_output, 0)
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-124:" + "\n" + e.message)

    @pytest.mark.skipif(os.environ['CP_PROVIDER'] == GsClient.name,
                        reason="Folder restore allowed for S3 provider only")
    def test_mark_for_deletion_non_empty_folder(self):
        """TC-PIPE-STORAGE-119"""
        # TODO: TC-PIPE-STORAGE-120
        destination_1 = 'cp://{}/{}/{}'.format(self.bucket, self.test_folder_1, self.test_file_1)
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination_1)
            pipe_storage_rm('cp://{}/{}'.format(self.bucket, self.test_folder_1), recursive=True)
            actual_output = get_pipe_listing(self.path_to_bucket)
            assert len(actual_output) == 0
            actual_output = get_pipe_listing(self.path_to_bucket, versioning=True)
            assert len(actual_output) == 1 and self.test_folder_1 in actual_output[0].name
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing('cp://{}/{}'.format(self.bucket, self.test_folder_1), versioning=True, recursive=True))
            expected_output = [
                f('{}/{}'.format(self.test_folder_1, self.test_file_1), deleted=True, latest=True),
                f('{}/{}'.format(self.test_folder_1, self.test_file_1), 10, added=True)
            ]
            compare_listing(actual_output, expected_output, 2)
            pipe_storage_restore('cp://{}/{}'.format(self.bucket, self.test_folder_1), expected_status=0,
                                 recursive=True)
            actual_output = get_pipe_listing(self.path_to_bucket)
            assert len(actual_output) == 1 and self.test_folder_1 in actual_output[0].name
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing('cp://{}/{}'.format(self.bucket, self.test_folder_1), versioning=True, recursive=True))
            expected_output = [
                f('{}/{}'.format(self.test_folder_1, self.test_file_1), 10, added=True, latest=True),
                f('{}/{}'.format(self.test_folder_1, self.test_file_1), 10, added=True)
            ]
            compare_listing(actual_output, expected_output, 2, sort=False)
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-119-120:" + "\n" + e.message)

    def test_hard_deletion_non_empty_folder(self):
        """TC-PIPE-STORAGE-122"""
        destination_1 = 'cp://{}/{}/{}/{}'.format(self.bucket, self.test_folder_1, self.test_folder_2, self.test_file_1)
        destination_2 = 'cp://{}/{}/{}'.format(self.bucket, self.test_folder_1, self.test_file_1)
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination_1)
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination_2)
            pipe_storage_rm('cp://{}/{}'.format(self.bucket, self.test_folder_1), args=['--hard-delete'],
                            recursive=True)
            actual_output = get_pipe_listing('cp://{}/{}'.format(self.bucket, self.test_folder_1))
            assert len(actual_output) == 0
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing('cp://{}/{}'.format(self.bucket, self.test_folder_1), versioning=True),
                result_not_empty=False)
            expected_output = []
            compare_listing(actual_output, expected_output, 0)
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-122:" + "\n" + e.message)

    def test_hard_deletion_marked_non_empty_folder(self):
        """TC-PIPE-STORAGE-125"""
        destination_1 = 'cp://{}/{}/{}/{}'.format(self.bucket, self.test_folder_1, self.test_folder_2, self.test_file_1)
        destination_2 = 'cp://{}/{}/{}'.format(self.bucket, self.test_folder_1, self.test_file_1)
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination_1)
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination_2)
            pipe_storage_rm('cp://{}/{}'.format(self.bucket, self.test_folder_1), recursive=True)
            actual_output = get_pipe_listing(self.path_to_bucket)
            assert len(actual_output) == 0
            actual_output = get_pipe_listing(self.path_to_bucket, versioning=True)
            assert len(actual_output) == 1 and self.test_folder_1 in actual_output[0].name
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing('cp://{}/{}'.format(self.bucket, self.test_folder_1), versioning=True, recursive=True))
            expected_output = [
                f('{}/{}'.format(self.test_folder_1, self.test_file_1), deleted=True, latest=True),
                f('{}/{}'.format(self.test_folder_1, self.test_file_1), 10, added=True),
                f('{}/{}/{}'.format(self.test_folder_1, self.test_folder_2, self.test_file_1),
                  deleted=True, latest=True),
                f('{}/{}/{}'.format(self.test_folder_1, self.test_folder_2, self.test_file_1), 10, added=True)
            ]
            compare_listing(actual_output, expected_output, 4)

            pipe_storage_rm('cp://{}/{}'.format(self.bucket, self.test_folder_1), args=['--hard-delete'],
                            recursive=True)
            actual_output = get_pipe_listing('cp://{}/{}'.format(self.bucket, self.test_folder_1))
            assert len(actual_output) == 0
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing('cp://{}/{}'.format(self.bucket, self.test_folder_1), versioning=True),
                result_not_empty=False)
            expected_output = []
            compare_listing(actual_output, expected_output, 0)
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-125:" + "\n" + e.message)

    def test_mark_for_delete_non_existing_file(self):
        """TC-PIPE-STORAGE-132"""
        destination = 'cp://{}/{}'.format(self.bucket, TestFiles.NOT_EXISTS_FILE)
        try:
            error_message = pipe_storage_rm(destination, recursive=True, expected_status=1)[1]
            assert 'Storage path "{}" was not found'.format(destination) in error_message[0]
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-132:" + "\n" + e.message)

    def test_hard_delete_non_existing_file(self):
        """TC-PIPE-STORAGE-133"""
        destination = 'cp://{}/{}'.format(self.bucket, TestFiles.NOT_EXISTS_FILE)
        try:
            error_message = pipe_storage_rm(destination, recursive=True, expected_status=1, args=['--hard-delete'])[1]
            assert 'Storage path "{}" was not found'.format(destination) in error_message[0]
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-133:" + "\n" + e.message)

    def test_restore_non_existing_version(self):
        """TC-PIPE-STORAGE-134"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        not_existing_version = 'does-not-exist'
        try:
            pipe_storage_cp(self.test_file_1, destination)
            pipe_storage_cp(self.test_file_2, destination, force=True)  # another file with same name
            error_message = pipe_storage_restore(destination, expected_status=1, version=not_existing_version)[1]
            assert 'Error: Version "{}" doesn\'t exist.'.format(not_existing_version) in error_message[0]
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-134:" + "\n" + e.message)

    def test_restore_not_removed_object(self):
        """TC-PIPE-STORAGE-135"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        try:
            pipe_storage_cp(self.test_file_1, destination)
            error_message = pipe_storage_restore(destination, expected_status=1)[1]
            assert 'Error: Latest file version is not deleted. Please specify "--version" parameter.'\
                   in error_message[0]
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-135:" + "\n" + e.message)

    def test_restore_latest_version(self):
        """TC-PIPE-STORAGE-136"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        try:
            pipe_storage_cp(self.test_file_1, destination)
            pipe_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing(destination, versioning=True))
            version_id = get_latest_version(pipe_output)
            error_message = pipe_storage_restore(destination, version=version_id, expected_status=1)[1]
            assert 'Version "{}" is already the latest version'.format(version_id)\
                   in error_message[0]
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-136:" + "\n" + e.message)

    def test_role_model_marked_object_deletion(self):
        """TC-PIPE-STORAGE-121"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        try:
            set_storage_permission(self.user, self.bucket, allow='r')
            set_storage_permission(self.user, self.bucket, allow='w')
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination, token=self.token)
            pipe_storage_rm(destination, token=self.token)
            pipe_output = get_pipe_listing(self.path_to_bucket)
            assert len(pipe_output) == 0
            pipe_output = pipe_storage_ls(self.path_to_bucket, expected_status=1, token=self.token, versioning=True)[1]
            assert "Access is denied" in pipe_output[0]
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-121:" + "\n" + e.message)

    def test_role_model_object_versions(self):
        """TC-PIPE-STORAGE-127"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        try:
            set_storage_permission(self.user, self.bucket, allow='r')
            set_storage_permission(self.user, self.bucket, allow='w')
            pipe_storage_cp(self.test_file_1, destination, token=self.token, expected_status=0)
            pipe_storage_cp(self.test_file_2, destination, force=True, token=self.token, expected_status=0)
            actual_output = get_pipe_listing(self.path_to_bucket, token=self.token)
            expected_output = [
                f(self.test_file_1, 14)
            ]
            compare_listing(actual_output, expected_output, 1)
            actual_output = pipe_storage_ls(self.path_to_bucket, expected_status=1, token=self.token,
                                            versioning=True)[1]
            assert "Access is denied" in actual_output[0]
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-127:" + "\n" + e.message)

    def test_role_model_restore_latest_version(self):
        """TC-PIPE-STORAGE-128"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        try:
            set_storage_permission(self.user, self.bucket, allow='r')
            set_storage_permission(self.user, self.bucket, allow='w')
            pipe_storage_cp(self.test_file_1, destination, token=self.token)
            pipe_storage_rm(destination, token=self.token)
            pipe_output = get_pipe_listing(self.path_to_bucket)
            assert len(pipe_output) == 0
            pipe_output = pipe_storage_restore(self.path_to_bucket, expected_status=1, token=self.token)[1]
            assert "Access is denied" in pipe_output[0]
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-128:" + "\n" + e.message)

    def test_role_model_object_hard_deletion(self):
        """TC-PIPE-STORAGE-129"""
        destination = 'cp://{}/{}'.format(self.bucket, self.test_file_1)
        try:
            set_storage_permission(self.user, self.bucket, allow='r')
            set_storage_permission(self.user, self.bucket, allow='w')
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination, token=self.token)
            pipe_output = pipe_storage_rm(destination, args=['--hard-delete'], token=self.token, expected_status=1)[1]
            assert "Access is denied" in pipe_output[0]
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-129:" + "\n" + e.message)

    def test_role_model_restore_marked_for_deletion_non_empty_folder(self):
        """TC-PIPE-STORAGE-130"""
        destination_1 = 'cp://{}/{}/{}/{}'.format(self.bucket, self.test_folder_1, self.test_folder_2, self.test_file_1)
        destination_2 = 'cp://{}/{}/{}'.format(self.bucket, self.test_folder_1, self.test_file_1)
        try:
            set_storage_permission(self.user, self.bucket, allow='r')
            set_storage_permission(self.user, self.bucket, allow='w')
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination_1, expected_status=0)
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination_2, expected_status=0)
            pipe_storage_rm('cp://{}/{}'.format(self.bucket, self.test_folder_1), recursive=True, token=self.token,
                            expected_status=0)
            pipe_output = get_pipe_listing(self.path_to_bucket)
            assert len(pipe_output) == 0
            pipe_output = pipe_storage_restore(self.path_to_bucket, expected_status=1, token=self.token)[1]
            assert "Access is denied" in pipe_output[0]
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-130:" + "\n" + e.message)

    def test_role_model_hard_deletion_marked_non_empty_folder(self):
        """TC-PIPE-STORAGE-131"""
        destination_1 = 'cp://{}/{}/{}/{}'.format(self.bucket, self.test_folder_1, self.test_folder_2, self.test_file_1)
        destination_2 = 'cp://{}/{}/{}'.format(self.bucket, self.test_folder_1, self.test_file_1)
        try:
            set_storage_permission(self.user, self.bucket, allow='r')
            set_storage_permission(self.user, self.bucket, allow='w')
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination_1, expected_status=0)
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination_2, expected_status=0)
            pipe_output = pipe_storage_rm('cp://{}/{}'.format(self.bucket, self.test_folder_1), recursive=True,
                                          token=self.token, expected_status=1, args=['--hard-delete'])[1]
            assert "Access is denied" in pipe_output[0]
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-131:" + "\n" + e.message)

    def test_ls_with_paging(self):
        """TC-PIPE-STORAGE-58"""
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_1), "{}/{}".format(self.path_to_bucket, self.test_file_1))
            pipe_storage_cp(os.path.abspath(self.test_file_1), "{}/{}/{}".format(self.path_to_bucket,
                                                                                 self.test_folder_1, self.test_file_1))
            pipe_storage_cp(os.path.abspath(self.test_file_1), "{}/{}".format(self.path_to_bucket, self.test_file_2))
            pipe_storage_cp(os.path.abspath(self.test_file_1), "{}/{}/{}".format(self.path_to_bucket,
                                                                                 self.test_folder_2, self.test_file_1))
            pipe_storage_cp(os.path.abspath(self.test_file_1), "{}/{}".format(self.path_to_bucket, self.test_file_3))
            pipe_storage_cp(os.path.abspath(self.test_file_1), "{}/{}/{}".format(self.path_to_bucket,
                                                                                 self.test_folder_3, self.test_file_1))
            pipe_output = get_pipe_listing(self.path_to_bucket, show_details=False, paging=str(3))
            assert len(pipe_output) == 3
            pipe_output = get_pipe_listing(self.path_to_bucket, show_details=False, recursive=True, paging=str(3))
            assert len(pipe_output) == 3
            pipe_output = get_pipe_listing(self.path_to_bucket, paging=str(3))
            assert len(pipe_output) == 3
            pipe_output = filter_versioned_lines(get_pipe_listing(self.path_to_bucket, show_details=False,
                                                                  versioning=True, paging=str(3)))
            assert len(pipe_output) == 3
            pipe_output = filter_versioned_lines(get_pipe_listing(self.path_to_bucket, versioning=True, paging=str(3)))
            assert len(pipe_output) == 3
            pipe_output = filter_versioned_lines(get_pipe_listing(self.path_to_bucket, versioning=True, recursive=True,
                                                                  paging=str(3)))
            assert len(pipe_output) == 3
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-58:" + "\n" + e.message)

    def test_copy_with_similar_keys(self):
        """TC-PIPE-STORAGE-60"""
        try:
            source = os.path.abspath(self.test_file_1)
            destination = "cp://{}/{}".format(self.bucket, self.test_file_1)
            destination_without_extension = "cp://{}/{}".format(self.bucket, self.test_file_1_without_extension)
            pipe_storage_cp(source, destination)
            assert object_exists(self.bucket, self.test_file_1)
            pipe_storage_cp(source, destination_without_extension)
            assert object_exists(self.bucket, self.test_file_1_without_extension)
            pipe_storage_rm(destination_without_extension)
            assert not object_exists(self.bucket, self.test_file_1_without_extension)
            assert object_exists(self.bucket, self.test_file_1)
            pipe_storage_rm(destination)
            assert not object_exists(self.bucket, self.test_file_1)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format("TC-PIPE-STORAGE-60", e.message))

    def test_rm_files_with_common_keys(self):
        """TC-PIPE-STORAGE-126"""
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_1), "{}/{}".format(self.path_to_bucket,
                                                                              self.test_file_1_without_extension))
            pipe_storage_cp(os.path.abspath(self.test_file_1), "{}/{}".format(self.path_to_bucket, self.test_file_1))
            pipe_storage_rm('cp://{}/{}'.format(self.bucket, self.test_file_1_without_extension),
                            args=['--hard-delete'], recursive=False, expected_status=None)
            pipe_output = get_pipe_listing(self.path_to_bucket)
            assert len(pipe_output) == 1
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "TC-PIPE-STORAGE-126:" + "\n" + e.message)

    def test_list_version(self):
        destination = "cp://{}/{}".format(self.bucket, self.test_file_1)
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination)
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination, force=True)
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing(destination, show_details=True, versioning=True))
            expected_files = [
                f(self.test_file_1, 10, added=True, latest=True),
                f(self.test_file_1, 10, added=True)
            ]
            expected_versions = get_versions(self.bucket, self.test_file_1)
            for index, expected_file in enumerate(expected_files):
                expected_file.version_id = expected_versions[index]
            compare_listing(actual_output, expected_files, 2, show_details=True, check_version=True, sort=False)
        except AssertionError as e:
            pytest.fail(ERROR_MESSAGE + ":\n" + e.message)

    def test_list_deleted_version(self):
        destination = "cp://{}/{}".format(self.bucket, self.test_file_1)
        try:
            pipe_storage_cp(os.path.abspath(self.test_file_1), destination)
            pipe_storage_rm(destination)
            actual_output = assert_and_filter_first_versioned_listing_line(
                get_pipe_listing(destination, show_details=True, versioning=True))
            expected_files = [
                f(self.test_file_1, deleted=True, latest=True),
                f(self.test_file_1, 10, added=True)
            ]
            expected_versions = get_versions(self.bucket, self.test_file_1)
            for index, expected_file in enumerate(expected_files):
                expected_file.version_id = expected_versions[index]
            compare_listing(actual_output, expected_files, 2, show_details=True, check_version=True)
        except AssertionError as e:
            pytest.fail(ERROR_MESSAGE + ":\n" + e.message)
