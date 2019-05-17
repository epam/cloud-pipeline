# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

from buckets.utils.tag_assertion_utils import *
from buckets.utils.assertions_utils import *
from buckets.utils.listing import *
from common_utils.entity_managers import EntityManager

ERROR_MESSAGE = "An error occurred in case "


class TestS3Tagging(object):
    test_file = "test-tag1.txt"
    test_file2 = "test-tag2.txt"
    test_file_in_folder = "tags/" + test_file
    test_file2_in_folder = "tags/" + test_file2
    bucket = 'epmcmbibpc-s3-tagging-{}'.format(get_test_prefix()).lower()
    path_to_bucket = 'cp://{}'.format(bucket)
    tag1 = ("key1", "value1")
    tag2 = ("key2", "value2")
    tag3 = ("key3", "value3")
    owner_token = os.environ['USER_TOKEN']
    owner = os.environ['TEST_USER']

    @classmethod
    def setup_class(cls):
        folder_id = None
        manager = EntityManager.get_manager('FOLDER')
        try:
            folder_id = manager.create(cls.bucket)
            set_acl_permissions(cls.owner, str(folder_id), 'folder', allow='rw')
            create_data_storage(cls.bucket, versioning=os.environ['CP_PROVIDER'] == S3Client.name,
                                token=cls.owner_token, folder=folder_id)
            create_test_file(os.path.abspath(cls.test_file), TestFiles.DEFAULT_CONTENT)
        except BaseException as e:
            if folder_id is not None:
                manager.delete(folder_id)
            raise RuntimeError(e.message)

    @classmethod
    def teardown_class(cls):
        clean_test_data(os.path.abspath(cls.test_file))
        clean_test_data(os.path.abspath(cls.test_file2))
        clean_test_data(os.path.abspath(cls.test_file2_in_folder))
        clean_test_data(os.path.abspath(cls.test_file))
        delete_data_storage(cls.bucket)
        manager = EntityManager.get_manager('FOLDER')
        folder_id = manager.get_id_by_name(cls.bucket)
        manager.delete(folder_id)

    """
        1. epam test case
        2. path
    """
    test_case_for_tagging = [
        ("epmcmbibpc-982", 'cp://{}/{}'.format(bucket, test_file)),
        ("epmcmbibpc-983", 'cp://{}/{}'.format(bucket, test_file_in_folder)),
    ]

    @pytest.mark.parametrize("test_case,path", test_case_for_tagging)
    def test_tagging(self, test_case, path):
        try:
            self.assert_tag_commands(path)
        except AssertionError as e:
            raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)

    """
          1. epam test case
          2. path
      """
    test_case_for_tagging_owner = [
        ("epmcmbibpc-987", 'cp://{}/{}'.format(bucket, test_file)),
        ("epmcmbibpc-988", 'cp://{}/{}'.format(bucket, test_file_in_folder))
    ]

    @pytest.mark.parametrize("test_case,path", test_case_for_tagging_owner)
    def test_tagging_owner(self, test_case, path):
        try:
            self.assert_tag_commands(path, token=self.owner_token)
        except AssertionError as e:
            raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)

    """
        1. epam test case
        2. path
    """
    test_case_for_tagging_version = [
        ("epmcmbibpc-984", 'cp://{}/{}'.format(bucket, test_file2)),
        ("epmcmbibpc-985", 'cp://{}/{}'.format(bucket, test_file2_in_folder))
    ]

    @pytest.mark.skipif(os.environ['CP_PROVIDER'] == AzureClient.name,
                        reason="Versioning is not supported for AZURE provider")
    @pytest.mark.parametrize("test_case,path", test_case_for_tagging_version)
    def test_tagging_version(self, test_case, path):
        try:
            self.assert_tag_command_version(path)
        except AssertionError as e:
             raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)

    """
        1. epam test case
        2. path
    """
    test_case_for_tagging_version_owner = [
        ("epmcmbibpc-989", 'cp://{}/{}'.format(bucket, test_file2)),
        ("epmcmbibpc-990", 'cp://{}/{}'.format(bucket, test_file2_in_folder))
    ]

    @pytest.mark.skipif(os.environ['CP_PROVIDER'] == AzureClient.name,
                        reason="Versioning is not supported for AZURE provider")
    @pytest.mark.parametrize("test_case,path", test_case_for_tagging_version)
    def test_tagging_version_owner(self, test_case, path):
        try:
            self.assert_tag_command_version(path, token=self.owner_token)
        except AssertionError as e:
            raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)

    """
         1. path
         2. error message
     """
    test_case_for_non_existing = [
        ('cp://non_exisitng/{}'.format(test_file), "data storage with id: 'non_exisitng' was not found"),
        ('cp://{}/non_existing'.format(bucket), "Storage path 'non_existing' for bucket '%s' does not exist" % bucket)
    ]

    @pytest.mark.parametrize("path,message", test_case_for_non_existing)
    def test_non_existing(self, path, message):
        test_case = "epmcmbibpc-1011"
        try:
            stderr = set_storage_tags(path, [self.tag1], expected_status=1)[1]
            assert_error_message_is_present(stderr, message)
            stderr = get_storage_tags(path, expected_status=1)[1]
            assert_error_message_is_present(stderr, message)
            stderr = delete_storage_tags(path, [self.tag1[0]], expected_status=1)[1]
            assert_error_message_is_present(stderr, message)
        except AssertionError as e:
            raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)

    def test_delete_non_existing_tag(self):
        test_case = "epmcmbibpc-1013"
        try:
            path = 'cp://{}/{}'.format(self.bucket, self.test_file)
            pipe_storage_cp(self.test_file, path, force=True)
            set_storage_tags(path, [self.tag1])

            stderr = delete_storage_tags(path, [self.tag2[1]], expected_status=1)[1]
            assert_error_message_is_present(stderr, "Tag '%s' does not exist" % self.tag2[1])

            assert_tags_listing(self.bucket, path, [self.tag1])
            pipe_storage_rm(path, args=self.rm_arguments())
        except AssertionError as e:
            raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)

    def test_set_wrong_format(self):
        test_case = "epmcmbibpc-1012"
        try:
            path = 'cp://{}/{}'.format(self.bucket, self.test_file)
            pipe_storage_cp(self.test_file, path, force=True)
            stderr = set_storage_tags(path, [], args=["key"], expected_status=1)[1]
            assert_error_message_is_present(stderr, 'Tags must be specified as KEY=VALUE pair')
            pipe_storage_rm(path, args=(self.rm_arguments()))
        except AssertionError as e:
            raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)

    def assert_tag_command_version(self, path, token=None):
        pipe_storage_cp(self.test_file, path, force=True)
        pipe_storage_cp(self.test_file, path, force=True)
        version = get_non_latest_version(get_pipe_listing(path, versioning=True, show_details=True))
        mutable_tag = list(self.tag1)
        set_storage_tags(path, [mutable_tag], version=version, token=token)
        assert_tags_listing(self.bucket, path, [], token=token)
        assert_tags_listing(self.bucket, path, [mutable_tag], version=version, token=token)
        set_storage_tags(path, [self.tag2], token=token)
        assert_tags_listing(self.bucket, path, [self.tag2], token=token)
        assert_tags_listing(self.bucket, path, [mutable_tag], version=version, token=token)
        mutable_tag[1] = 'newvalue'
        set_storage_tags(path, [mutable_tag], version=version, token=token)
        assert_tags_listing(self.bucket, path, [self.tag2], token=token)
        assert_tags_listing(self.bucket, path, [mutable_tag], version=version, token=token)
        delete_storage_tags(path, [mutable_tag[0]], version=version, token=token)
        assert_tags_listing(self.bucket, path, [self.tag2], token=token)
        assert_tags_listing(self.bucket, path, [], version=version, token=token)
        pipe_storage_rm(path, args=self.rm_arguments())

    def assert_tag_commands(self, path, token=None):
        pipe_storage_cp(self.test_file, path, force=True)
        mutable_tag = list(self.tag1)
        set_storage_tags(path, [mutable_tag], token=token)
        assert_tags_listing(self.bucket, path, [mutable_tag], token=token)
        set_storage_tags(path, [self.tag2, self.tag3], token=token)
        assert_tags_listing(self.bucket, path, [mutable_tag, self.tag2, self.tag3], token=token)
        mutable_tag[1] = 'newvalue'
        set_storage_tags(path, [mutable_tag], token=token)
        assert_tags_listing(self.bucket, path, [mutable_tag, self.tag2, self.tag3], token=token)
        delete_storage_tags(path, [mutable_tag[0]], token=token)
        assert_tags_listing(self.bucket, path, [self.tag2, self.tag3], token=token)
        pipe_storage_rm(path, args=self.rm_arguments())

    def rm_arguments(self):
        return ['--hard-delete'] if os.environ['CP_PROVIDER'] == S3Client.name else []
