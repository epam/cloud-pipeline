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

from buckets.utils.listing import *
from buckets.utils.file_utils import *
from buckets.utils.utilities_for_test import *


class TestLsSingleFile(object):
    epam_test_case = "EPMCMBIBPC-617"
    resources_root = "resources-{}/".format(epam_test_case)
    relative_path = os.path.join(resources_root, "test_file.txt")
    bucket_name = "epmcmbibpc-it-{}{}".format(epam_test_case, get_test_prefix()).lower()

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name)
        source_path = os.path.abspath(os.path.join(cls.resources_root, cls.relative_path))
        create_test_folder(os.path.abspath(cls.resources_root))
        create_test_file(source_path, "Hello World")
        # copy file in folder
        pipe_storage_cp(source_path, "cp://{}/{}".format(cls.bucket_name, cls.relative_path))
        # copy file in root
        pipe_storage_cp(source_path, "cp://{}/{}".format(cls.bucket_name, os.path.basename(cls.relative_path)))
        logging.info("Uploaded test files to bucket {}".format(cls.bucket_name))

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name)
        clean_test_data(os.path.abspath(cls.resources_root))

    test_paths = [
        (relative_path, True, False),
        (relative_path, False, False),
        (relative_path, False, True),
        (relative_path, True, True),
        (os.path.basename(relative_path), True, False),
        (os.path.basename(relative_path), False, False),
        (os.path.basename(relative_path), False, True),
        (os.path.basename(relative_path), True, True)
    ]

    @pytest.mark.run()
    @pytest.mark.parametrize("path,show_details,recursive", test_paths)
    def test_list_file(self, path, show_details, recursive):
        try:
            pipe_files = get_pipe_listing("cp://{}/{}".format(self.bucket_name, path),
                                          show_details=show_details, recursive=recursive)
            aws_files = get_aws_listing("s3://{}/{}".format(self.bucket_name, path), recursive=recursive)
            compare_listing(pipe_files, aws_files, 1, show_details=show_details)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

