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


class TestCpWithRoleModel(object):
    epam_test_case = "EPMCMBIBPC-600"
    bucket_name = "epmcmbibpc-it-cp-roles{}".format(get_test_prefix())
    other_bucket_name = "{}-other".format(bucket_name)
    token = os.environ['USER_TOKEN']
    user = os.environ['TEST_USER']
    output_folder = epam_test_case + "-" + TestFiles.TEST_FOLDER_FOR_OUTPUT
    test_file = "cp-role-" + TestFiles.TEST_FILE1

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name, cls.other_bucket_name)
        source = os.path.abspath(cls.test_file)
        create_test_file(source, TestFiles.DEFAULT_CONTENT)
        pipe_storage_cp(source, "cp://{}/{}".format(cls.bucket_name, cls.test_file), force=True)
        logging.info("Uploaded test files to bucket {}".format(cls.bucket_name))

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name, cls.other_bucket_name)
        clean_test_data(os.path.abspath(cls.test_file))
        clean_test_data(os.path.abspath(cls.output_folder))

    @pytest.mark.run(order=1)
    def test_download_file_without_permission(self):
        try:
            set_storage_permission(self.user, self.bucket_name, deny='rw')
            error_text = pipe_storage_cp("cp://{}/{}".format(self.bucket_name, self.test_file),
                                         os.path.join(self.output_folder, self.test_file),
                                         expected_status=1, token=self.token)[1]
            assert_error_message_is_present(error_text, 'Access is denied')
            assert_copied_object_does_not_exist(ObjectInfo(True).build(
                self.output_folder + self.test_file), self.epam_test_case)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=1)
    def test_upload_file_without_permission(self):
        try:
            case = self.epam_test_case + "-no-permissions"
            error_text = pipe_storage_cp(self.test_file, "cp://{}/{}/{}".format(self.bucket_name, case,
                                                                                      self.test_file),
                                         expected_status=1, token=self.token)[1]
            assert_error_message_is_present(error_text, 'Access is denied')
            assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name,
                                                                        os.path.join(case, self.test_file)),
                                                self.epam_test_case)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=2)
    def test_copy_between_buckets_without_permission(self):
        try:
            case = self.epam_test_case + "-no-permissions"
            # set permissions only for source bucket
            set_storage_permission(self.user, self.bucket_name, allow='r')
            error_text = pipe_storage_cp("cp://{}/{}".format(self.bucket_name, self.test_file),
                                         "cp://{}/{}/{}".format(self.other_bucket_name, case, self.test_file),
                                         expected_status=1, token=self.token)[1]
            assert_error_message_is_present(error_text, 'Access is denied')
            assert_copied_object_does_not_exist(ObjectInfo(False).build(
                self.other_bucket_name, os.path.join(case, self.test_file)),
                                                self.epam_test_case)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=2)
    def test_download_from_bucket_with_read_permission(self):
        try:
            case = self.epam_test_case + "-download-from-bucket-with-read-permission"
            # set permissions only for source bucket
            set_storage_permission(self.user, self.bucket_name, allow='r')
            pipe_storage_cp("cp://{}/{}".format(self.bucket_name, self.test_file),
                            os.path.join(self.output_folder, case, self.test_file),
                            expected_status=0, token=self.token)
            assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, self.test_file),
                                      ObjectInfo(True).build(os.path.join(self.output_folder, case,
                                                                          self.test_file)),
                                      self.epam_test_case)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=2)
    def test_upload_file_with_read_permission(self):
        try:
            case = self.epam_test_case + "-upload-file-with-read-permission"
            set_storage_permission(self.user, self.bucket_name, allow='r')
            error_text = pipe_storage_cp(self.test_file, "cp://{}/{}/{}".format(self.bucket_name, case,
                                                                                      self.test_file),
                                         expected_status=1, token=self.token)[1]
            assert_error_message_is_present(error_text, 'Access is denied')
            assert_copied_object_does_not_exist(ObjectInfo(False).build(self.bucket_name,
                                                                        os.path.join(case, self.test_file)),
                                                self.epam_test_case)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=2)
    def test_write_from_bucket_to_bucket_with_read_permission(self):
        try:
            case = self.epam_test_case + "-write-from-bucket-to-bucket-with-read-permission"
            set_storage_permission(self.user, self.bucket_name, allow='r')
            set_storage_permission(self.user, self.other_bucket_name, allow='r')
            error_text = pipe_storage_cp("cp://{}/{}".format(self.bucket_name, self.test_file),
                                         "cp://{}/{}/{}".format(self.other_bucket_name, case, self.test_file),
                                         expected_status=1, token=self.token)[1]
            assert_error_message_is_present(error_text, 'Access is denied')
            assert_copied_object_does_not_exist(ObjectInfo(False).build(self.other_bucket_name,
                                                                        os.path.join(case, self.test_file)),
                                                self.epam_test_case)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=3)
    def test_upload_file_with_write_permission(self):
        try:
            case = self.epam_test_case + "-upload-file-with-write-permission"
            set_storage_permission(self.user, self.bucket_name, allow='w')
            pipe_storage_cp(self.test_file, "cp://{}/{}/{}".format(self.bucket_name, case, self.test_file),
                            expected_status=0, token=self.token)
            assert_copied_object_info(ObjectInfo(True).build(self.test_file),
                                      ObjectInfo(False).build(self.bucket_name,
                                                              os.path.join(case, self.test_file)),
                                      self.epam_test_case)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=3)
    def test_write_from_bucket_to_bucket_with_write_permission(self):
        try:
            case = self.epam_test_case + "-write-from-bucket-to-bucket-with-write-permission"
            set_storage_permission(self.user, self.other_bucket_name, allow='w')
            pipe_storage_cp("cp://{}/{}".format(self.bucket_name, self.test_file),
                            "cp://{}/{}/{}".format(self.other_bucket_name, case, self.test_file),
                            expected_status=0, token=self.token)
            assert_copied_object_info(ObjectInfo(False).build(self.bucket_name, self.test_file),
                                      ObjectInfo(False).build(self.other_bucket_name,
                                                              os.path.join(case, self.test_file)),
                                      self.epam_test_case)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))
