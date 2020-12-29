# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
from common_utils.test_utils import format_name


class TestLsFolder(object):
    epam_test_case_ls_folder_with_delimiter = "EPMCMBIBPC-634"
    epam_test_case_ls_folder_without_delimiter = "EPMCMBIBPC-633"
    epam_test_case_ls_non_existing_bucket = "EPMCMBIBPC-651"
    epam_test_case_ls_non_existing_path = "EPMCMBIBPC-653"
    epam_test_case_ls_wrong_scheme = "EPMCMBIBPC-654"
    suffix = "EPMCMBIBPC-633-634"
    resources_root = "resources-{}/".format(suffix).lower()
    bucket_name = format_name("epmcmbibpc-it-{}{}".format(suffix, get_test_prefix()).lower())

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name)
        create_default_test_folder(cls.resources_root)
        pipe_storage_cp(cls.resources_root, "cp://{}/{}/".format(cls.bucket_name, cls.resources_root),  recursive=True)
        logging.info("Uploaded test files to bucket {}".format(cls.bucket_name))

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name)
        clean_test_data(os.path.abspath(cls.resources_root))

    test_paths_without_delimiter = [
        (resources_root[:-1], True, False, 1, epam_test_case_ls_folder_without_delimiter, [
            d('resources-epmcmbibpc-633-634/')
        ]),
        (resources_root[:-1], False, False, 1, epam_test_case_ls_folder_without_delimiter, [
            d('resources-epmcmbibpc-633-634/')
        ]),
        (resources_root[:-1], False, True, 3, epam_test_case_ls_folder_without_delimiter, [
            f('resources-epmcmbibpc-633-634/test_file.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file2.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file3.log', 10)
        ]),
        (resources_root[:-1], True, True, 3, epam_test_case_ls_folder_without_delimiter, [
            f('resources-epmcmbibpc-633-634/test_file.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file2.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file3.log', 10)
        ]),
        ("%st" % resources_root, True, True, 3, "EPMCMBIBPC-1968", [
            f('resources-epmcmbibpc-633-634/test_file.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file2.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file3.log', 10)
        ]),
        ("%st" % resources_root, False, True, 3, "EPMCMBIBPC-1968", [
            f('resources-epmcmbibpc-633-634/test_file.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file2.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file3.log', 10)
        ]),
        ("%st" % resources_root, True, False, 2, "EPMCMBIBPC-1968", [
            d('test_folder/'),
            f('test_file.txt', 10)
        ]),
        ("%st" % resources_root, False, False, 2, "EPMCMBIBPC-1968", [
            d('test_folder/'),
            f('test_file.txt', 10)
        ])
    ]

    @pytest.mark.run()
    @pytest.mark.parametrize("path,show_details,recursive,length,case,expected_listing", test_paths_without_delimiter)
    def test_list_folder_without_trailing_delimiter(self, path, show_details, recursive, length, case, expected_listing):
        try:
            actual_listing = get_pipe_listing("cp://{}/{}".format(self.bucket_name, path), show_details=show_details,
                                              recursive=recursive)
            compare_listing(actual_listing, expected_listing, length, show_details=show_details)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    bucket_parameters_without_delimiter = [
        (True, False, 1, [
            d('resources-epmcmbibpc-633-634/')
        ]),
        (False, False, 1, [
            d('resources-epmcmbibpc-633-634/')
        ]),
        (False, True, 3, [
            f('resources-epmcmbibpc-633-634/test_file.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file2.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file3.log', 10)
        ]),
        (True, True, 3, [
            f('resources-epmcmbibpc-633-634/test_file.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file2.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file3.log', 10)
        ])
    ]

    @pytest.mark.run()
    @pytest.mark.parametrize("show_details,recursive,length,expected_listing", bucket_parameters_without_delimiter)
    def test_list_bucket_without_trailing_delimiter(self, show_details, recursive, length, expected_listing):
        try:
            actual_listing = get_pipe_listing("cp://{}".format(self.bucket_name), show_details=show_details,
                                              recursive=recursive)
            compare_listing(actual_listing, expected_listing, length, show_details=show_details)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_ls_folder_without_delimiter, e.message))

    test_paths_with_delimiter = [
        (resources_root, True, False, 2, [
            d('test_folder/'),
            f('test_file.txt', 10)
        ]),
        (resources_root, False, False, 2, [
            d('test_folder/'),
            f('test_file.txt', 10)
        ]),
        (resources_root, False, True, 3, [
            f('resources-epmcmbibpc-633-634/test_file.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file2.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file3.log', 10)
        ]),
        (resources_root, True, True, 3, [
            f('resources-epmcmbibpc-633-634/test_file.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file2.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file3.log', 10)
        ])
    ]

    @pytest.mark.run()
    @pytest.mark.parametrize("path,show_details,recursive,length,expected_listing", test_paths_with_delimiter)
    def test_list_folder_with_trailing_delimiter(self, path, show_details, recursive, length, expected_listing):
        try:
            actual_listing = get_pipe_listing("cp://{}/{}".format(self.bucket_name, path), show_details=show_details,
                                              recursive=recursive)
            compare_listing(actual_listing, expected_listing, length, show_details=show_details)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_ls_folder_with_delimiter, e.message))

    bucket_parameters_with_delimiter = [
        (True, False, 1, [
            d('resources-epmcmbibpc-633-634/')
        ]),
        (False, False, 1, [
            d('resources-epmcmbibpc-633-634/')
        ]),
        (False, True, 3, [
            f('resources-epmcmbibpc-633-634/test_file.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file2.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file3.log', 10)
        ]),
        (True, True, 3, [
            f('resources-epmcmbibpc-633-634/test_file.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file2.txt', 10),
            f('resources-epmcmbibpc-633-634/test_folder/test_file3.log', 10)
        ])
    ]

    @pytest.mark.run()
    @pytest.mark.parametrize("show_details,recursive,length,expected_listing", bucket_parameters_with_delimiter)
    def test_list_bucket_with_trailing_delimiter(self, show_details, recursive, length, expected_listing):
        try:
            actual_listing = get_pipe_listing("cp://{}/".format(self.bucket_name), show_details=show_details,
                                              recursive=recursive)
            compare_listing(actual_listing, expected_listing, length, show_details=show_details)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_ls_folder_with_delimiter, e.message))

    @pytest.mark.run()
    def test_list_not_existing_bucket(self):
        bucket_name = "does-not-exist"
        file_name = "file.txt"
        try:
            error = pipe_storage_ls("cp://{}/{}".format(bucket_name, file_name), expected_status=1)[1]
            assert_error_message_is_present(error, "Error: data storage with id: '{}/{}' was not found."
                                            .format(bucket_name, file_name))
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_ls_non_existing_bucket, e.message))

    @pytest.mark.run()
    def test_list_with_wrong_scheme(self):
        try:
            error = pipe_storage_ls("s4://{}/".format(self.bucket_name), recursive=True, expected_status=1)[1]
            assert_error_message_is_present(error, 'Supported schemes for datastorage are: "cp", "s3", "az", "gs".')
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_ls_wrong_scheme, e.message))

    non_existing_paths = [
        ("does-not-exist.txt", False),
        ("does-not-exist/", False),
        ("does-not-exist/", True)
    ]

    @pytest.mark.run()
    @pytest.mark.parametrize("path,recursive", non_existing_paths)
    def test_list_not_existing_path(self, path, recursive):
        try:
            listing = get_pipe_listing("cp://{}/{}".format(self.bucket_name, path), recursive=recursive)
            assert len(listing) == 0, \
                "Returned non empty result: '{}' for non existing path '{}'".format(str(listing), path)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_ls_non_existing_path, e.message))

    @pytest.mark.run()
    def test_last_modification_date_listing(self):
        try:
            listing_path = "cp://{}/{}/".format(self.bucket_name, 'resources-epmcmbibpc-633-634')
            actual_listing = get_pipe_listing(listing_path, show_details=True)
            expected_file = f('test_file.txt', 10)
            expected_file.last_modified = get_modification_date(listing_path + 'test_file.txt')
            expected_listing = [expected_file, d('test_folder/')]
            compare_listing(actual_listing, expected_listing, 2, show_details=True, check_last_modified=True)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case_ls_folder_with_delimiter, e.message))
