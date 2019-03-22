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

from common_utils.pipe_cli import *
from ..utils.assertions_utils import *
from ..utils.file_utils import *
from ..utils.utilities_for_test import *


class TestRmWithRoleModel(object):
    epam_test_case = "EPMCMBIBPC-609"
    resources_root = "resources-{}/".format(epam_test_case).lower()
    bucket_name = "epmcmbibpc-it-rm-{}{}".format(epam_test_case, get_test_prefix()).lower()
    token = os.environ['USER_TOKEN']
    user = os.environ['TEST_USER']
    test_file = "test_file.txt"

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name)
        source = os.path.abspath(os.path.join(cls.resources_root, cls.test_file))
        create_test_file(source, TestFiles.DEFAULT_CONTENT)
        pipe_storage_cp(source, "cp://{}/{}".format(cls.bucket_name, cls.test_file), recursive=True)
        logging.info("Uploaded test files to bucket {}".format(cls.bucket_name))

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name)
        clean_test_data(os.path.abspath(cls.resources_root))

    @pytest.mark.run(order=1)
    def test_rm_file_without_permission(self):
        try:
            error_text = pipe_storage_rm("cp://{}/{}".format(self.bucket_name, self.test_file),
                                         expected_status=1, token=self.token)[1]
            assert_error_message_is_present(error_text, 'Access is denied')
            assert_files_skipped(self.bucket_name, self.test_file)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=2)
    def test_rm_file_with_read_permission(self):
        try:
            set_storage_permission(self.user, self.bucket_name, allow='r')
            error_text = pipe_storage_rm("cp://{}/{}".format(self.bucket_name, self.test_file),
                                         expected_status=1, token=self.token)[1]
            assert_error_message_is_present(error_text, 'Access is denied')
            assert_files_skipped(self.bucket_name, self.test_file)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=3)
    def test_rm_file_with_write_permission(self):
        try:
            set_storage_permission(self.user, self.bucket_name, allow='w',)
            pipe_storage_rm("cp://{}/{}".format(self.bucket_name, self.test_file), expected_status=0, token=self.token)
            assert_files_deleted(self.bucket_name, self.test_file)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))
