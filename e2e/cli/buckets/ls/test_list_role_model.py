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

from buckets.utils.listing import *
from buckets.utils.assertions_utils import *
from buckets.utils.utilities_for_test import *


class TestLsWithRoleModel(object):
    epam_test_case = "EPMCMBIBPC-629"
    resources_root = "resources-{}/".format(epam_test_case).lower()
    bucket_name = "epmcmbibpc-it-{}{}".format(epam_test_case, get_test_prefix()).lower()
    token = os.environ['USER_TOKEN']
    user = os.environ['TEST_USER']

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name)
        create_default_test_folder(cls.resources_root)
        pipe_storage_cp(cls.resources_root, "cp://{}/{}/".format(cls.bucket_name, cls.resources_root), recursive=True)
        logging.info("Uploaded test files to bucket {}".format(cls.bucket_name))

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name)
        clean_test_data(os.path.abspath(cls.resources_root))

    @pytest.mark.run(order=1)
    def test_list_folder_without_permission(self):
        try:
            error_text = pipe_storage_ls("cp://{}/{}".format(self.bucket_name, self.resources_root),
                                         expected_status=1, token=self.token)[1]
            assert_error_message_is_present(error_text, 'Access is denied')
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=2)
    def test_list_from_root(self):
        case = "EPMCMBIBPC-699"
        try:
            buckets = get_pipe_listing(None, show_details=False)
            available_buckets = map(lambda bucket: filter(None, bucket.name.split(" ")[0]), buckets)
            assert self.bucket_name in available_buckets, "Bucket {} must be listed.".format(self.bucket_name)
            set_storage_permission(self.user, self.bucket_name, deny='r')
            buckets = get_pipe_listing(None, show_details=False, token=self.token)
            available_buckets = map(lambda bucket: filter(None, bucket.name.split(" ")[0]), buckets)
            assert self.bucket_name not in available_buckets, "Bucket {} must be not listed.".format(self.bucket_name)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=3)
    def test_list_folder_with_permission(self):
        try:
            set_storage_permission(self.user, self.bucket_name, allow='r')
            output = pipe_storage_ls("cp://{}/{}".format(self.bucket_name, self.resources_root),
                                     expected_status=0, token=self.token)[0]
            assert "".join(output), "Command output is empty"
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=4)
    def test_list_folder_with_write_permission(self):
        try:
            set_storage_permission(self.user, self.bucket_name, allow='w', deny='r')
            error_text = pipe_storage_ls("cp://{}/{}".format(self.bucket_name, self.resources_root),
                                         expected_status=1, token=self.token)[1]
            assert_error_message_is_present(error_text, 'Access is denied')
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))
