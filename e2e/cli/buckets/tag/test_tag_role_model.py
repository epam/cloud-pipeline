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
from buckets.utils.file_utils import *
from buckets.utils.listing import *

ERROR_MESSAGE = "An error occurred in case "


class TestS3TaggingRolModel(object):
    test_file = "s3-tagging-role.txt"
    bucket = 'epmcmbibpc-s3-tagging-role-{}'.format(get_test_prefix()).lower()
    path_to_bucket = 'cp://{}'.format(bucket)
    tag1 = ("key1", "value1")
    user_token = os.environ['USER_TOKEN']
    user = os.environ['TEST_USER']

    @classmethod
    def setup_class(cls):
        create_data_storage(cls.bucket, versioning=True, )
        create_test_file(os.path.abspath(cls.test_file), TestFiles.DEFAULT_CONTENT)
        set_acl_permissions(cls.user, cls.bucket, 'data_storage', allow='rw')
        path = 'cp://{}/{}'.format(cls.bucket, cls.test_file)
        pipe_storage_cp(cls.test_file, path, force=True)

    @classmethod
    def teardown_class(cls):
        clean_test_data(os.path.abspath(cls.test_file))
        delete_data_storage(cls.bucket)

    def test_tag_reading(self):
        test_case = 'epmcmbibpc-986'
        path = 'cp://{}/{}'.format(self.bucket, self.test_file)
        try:
            set_storage_tags(path, [self.tag1])
            assert_tags_listing(self.bucket, path, [self.tag1], token=self.user_token)
        except AssertionError as e:
            raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)

    def test_tag_updating(self):
        test_case = 'epmcmbibpc-991'
        path = 'cp://{}/{}'.format(self.bucket, self.test_file)
        try:
            stderr = set_storage_tags(path, [self.tag1], token=self.user_token, expected_status=1)[1]
            assert_error_message_is_present(stderr, 'Access is denied')
            stderr = delete_storage_tags(path, [self.tag1[0]], token=self.user_token, expected_status=1)[1]
            assert_error_message_is_present(stderr, 'Access is denied')
        except AssertionError as e:
            raise AssertionError(ERROR_MESSAGE + test_case, e.message)
        except BaseException as e:
            raise RuntimeError(ERROR_MESSAGE + test_case, e.message)

