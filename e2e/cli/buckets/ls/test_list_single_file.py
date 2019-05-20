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

import pytest

from buckets.utils.cloud.utilities import get_modification_date
from buckets.utils.listing import *
from buckets.utils.file_utils import *
from buckets.utils.utilities_for_test import *

# TODO: disable test until the GCP support is merged
@pytest.mark.skip()
class TestLsSingleFile(object):
    epam_test_case = "EPMCMBIBPC-617"
    resources_root = "resources-{}/".format(epam_test_case)
    relative_path = os.path.join(resources_root, "test_file.txt")
    bucket_name = "epmcmbibpc-it-{}{}".format(epam_test_case, get_test_prefix()).lower()
    another_bucket_alias = "{}-alias".format(bucket_name)
    another_bucket_path = "{}-path".format(bucket_name)

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name)
        create_bucket(cls.another_bucket_alias, path=cls.another_bucket_path)
        source_path = os.path.abspath(os.path.join(cls.resources_root, cls.relative_path))
        create_test_folder(os.path.abspath(cls.resources_root))
        create_test_file(source_path, "Hello World")
        # copy file in folder
        pipe_storage_cp(source_path, "cp://{}/{}".format(cls.bucket_name, cls.relative_path))
        pipe_storage_cp(source_path, "cp://{}/{}".format(cls.another_bucket_alias, cls.relative_path))
        # copy file in root
        pipe_storage_cp(source_path, "cp://{}/{}".format(cls.bucket_name, os.path.basename(cls.relative_path)))
        logging.info("Uploaded test files to bucket {}".format(cls.bucket_name))

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name, cls.another_bucket_alias)
        clean_test_data(os.path.abspath(cls.resources_root))

    test_paths = [
        (bucket_name, relative_path, True, False, [f('test_file.txt', 11)]),
        (bucket_name, relative_path, False, False, [f('test_file.txt', 11)]),
        (bucket_name, relative_path, False, True, [f('resources-EPMCMBIBPC-617/test_file.txt', 11)]),
        (bucket_name, relative_path, True, True, [f('resources-EPMCMBIBPC-617/test_file.txt', 11)]),
        (bucket_name, os.path.basename(relative_path), True, False, [f('test_file.txt', 11)]),
        (bucket_name, os.path.basename(relative_path), False, False, [f('test_file.txt', 11)]),
        (bucket_name, os.path.basename(relative_path), False, True, [f('test_file.txt', 11)]),
        (bucket_name, os.path.basename(relative_path), True, True, [f('test_file.txt', 11)]),
        (another_bucket_alias, relative_path, True, False, [f('test_file.txt', 11)]),
        (another_bucket_path, relative_path, True, False, [f('test_file.txt', 11)])
    ]

    @pytest.mark.run()
    @pytest.mark.parametrize("bucket,path,show_details,recursive,expected_listing", test_paths)
    def test_list_file(self, bucket, path, show_details, recursive, expected_listing):
        try:
            actual_listing = get_pipe_listing("cp://{}/{}".format(bucket, path), show_details=show_details,
                                              recursive=recursive)
            compare_listing(actual_listing, expected_listing, 1, show_details=show_details)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run()
    def test_last_modification_date_listing(self):
        try:
            listing_path = "cp://{}/{}".format(self.bucket_name, 'resources-EPMCMBIBPC-617/test_file.txt')
            actual_listing = get_pipe_listing(listing_path, show_details=True)
            expected_file = f('test_file.txt', 11)
            expected_file.last_modified = get_modification_date(listing_path)
            compare_listing(actual_listing, [expected_file], 1, show_details=True, check_last_modified=True)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))
