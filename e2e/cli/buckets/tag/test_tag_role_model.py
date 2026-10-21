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


from buckets.utils.tag_assertion_utils import *
from buckets.utils.assertions_utils import *
from buckets.utils.file_utils import *
from buckets.utils.listing import *
from common_utils.test_utils import format_name

ERROR_MESSAGE = "An error occurred in case "


class TestS3TaggingRolModel(object):
    test_file = "tagging-role.txt"
    bucket = format_name('tagging{}'.format(get_test_prefix()).lower())
    path_to_bucket = 'cp://{}'.format(bucket)
    tag1 = ("key1", "value1")
    tag2 = ("key2", "value2")
    user_token = os.environ['USER_TOKEN']
    user = os.environ['TEST_USER']

    @classmethod
    def setup_class(cls):
        create_data_storage(cls.bucket, versioning=True, )
        create_test_file(os.path.abspath(cls.test_file), TestFiles.DEFAULT_CONTENT)

    @classmethod
    def teardown_class(cls):
        clean_test_data(os.path.abspath(cls.test_file))
        delete_data_storage(cls.bucket)

    def test_tag_reading(self):
        """TC-PIPE-TAG-26"""
        test_case = 'TC-PIPE-TAG-26'
        path = 'cp://{}/{}{}'.format(self.bucket, self.test_file, test_case)
        try:
            # before test
            # grant read-only permissions on storage for plain user
            set_acl_permissions(self.user, self.bucket, 'data_storage', allow='r')
            # copy utility file
            pipe_storage_cp(self.test_file, path, force=True)

            # admin: updates storag tags
            set_storage_tags(path, [self.tag1])
            # user: can rad this tags
            assert_tags_listing(path, [self.tag1], token=self.user_token)
            # user: cannot update tags
            stderr = set_storage_tags(path, [self.tag2], token=self.user_token, expected_status=1)[1]
            assert_access_denied_error(stderr)
            # user: cannot delete tags
            stderr = delete_storage_tags(path, [self.tag1[0]], token=self.user_token, expected_status=1)[1]
            assert_access_denied_error(stderr)
        except AssertionError as e:
            raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)

    def test_tag_updating(self):
        """TC-PIPE-TAG-27"""
        test_case = 'TC-PIPE-TAG-27'
        path = 'cp://{}/{}{}'.format(self.bucket, self.test_file, test_case)
        try:
            # before test
            # grant read-write permissions on storage for plain user
            set_acl_permissions(self.user, self.bucket, 'data_storage', allow='rw')
            # copy utility file
            pipe_storage_cp(self.test_file, path, force=True)

            # user: can update tags
            set_storage_tags(path, [self.tag1], token=self.user_token)
            # user: can read tags
            assert_tags_listing(path, [self.tag1], token=self.user_token)
            # user: can delete tags
            delete_storage_tags(path, [self.tag1[0]], token=self.user_token)
            assert_tags_listing(path, [], token=self.user_token)
        except AssertionError as e:
            raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)
