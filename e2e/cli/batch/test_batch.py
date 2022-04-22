# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import logging
import os
import time

import pytest

from buckets.utils.assertions_utils import assert_local_files_count
from buckets.utils.file_utils import create_test_file, TestFiles, clean_test_data, create_test_folder
from buckets.utils.listing import get_pipe_listing
from buckets.utils.utilities_for_test import create_buckets, delete_buckets, create_batch_items_on_cloud
from common_utils.cmd_utils import get_test_prefix
from common_utils.pipe_cli import pipe_storage_cp, pipe_storage_mv
from common_utils.test_utils import format_name
from utils.pipeline_utils import get_log_filename


class TestBatchFiles(object):
    test_files_count = os.environ.get('TEST_BATCH_FILES_COUNT', 1001)
    raw_bucket_name = "cp-batch{}".format(get_test_prefix())
    bucket_name = format_name(raw_bucket_name)
    test_file_1 = "cp-batch-" + TestFiles.TEST_FILE1
    test_file_path = os.path.abspath(test_file_1)
    test_folder = "cp-batch-" + TestFiles.TEST_FOLDER
    test_folder_path = os.path.abspath(test_folder)

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name)
        create_test_file(cls.test_file_path, TestFiles.DEFAULT_CONTENT)
        create_test_folder(cls.test_folder_path)
        start = time.time()
        create_batch_items_on_cloud(cls.bucket_name, TestFiles.TEST_FILE1, cls.test_file_path, cls.test_files_count)
        end = time.time()
        logging.info("Batch items creation: %d" % (end - start))

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name)
        clean_test_data(os.path.abspath(cls.test_file_1))
        clean_test_data(os.path.abspath(cls.test_folder))

    @pytest.mark.run(order=1)
    def test_batch_copy(self):
        """TC-PIPE-STORAGE-BATCH-CP"""
        case = "TC-PIPE-STORAGE-BATCH-CP"
        try:
            source = "cp://%s/" % self.bucket_name
            destination = os.path.join(self.test_folder_path, case)
            start = time.time()
            pipe_storage_cp(source, destination, force=True, recursive=True)
            end = time.time()
            logging.info("Batch items copy: %d" % (end - start))
            assert_local_files_count(destination, self.test_files_count, case)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=2)
    def test_batch_list(self):
        """TC-PIPE-STORAGE-BATCH-LS"""
        case = "TC-PIPE-STORAGE-BATCH-LS"
        try:
            source = "cp://%s/" % self.bucket_name
            start = time.time()
            ls_results = get_pipe_listing(source)
            end = time.time()
            logging.info("Batch items listing: %d" % (end - start))
            actual_files_count = len(ls_results)
            assert actual_files_count == self.test_files_count, \
                "Expected files count: %d, actual: %d" % (self.test_files_count, actual_files_count)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))

    @pytest.mark.run(order=3)
    def test_batch_move(self):
        """TC-PIPE-STORAGE-BATCH-MV"""
        case = "TC-PIPE-STORAGE-BATCH-MV"
        try:
            source = "cp://%s/" % self.bucket_name
            destination = os.path.join(self.test_folder_path, case)
            start = time.time()
            pipe_storage_mv(source, destination, force=True, recursive=True)
            end = time.time()
            logging.info("Batch items move: %d" % (end - start))
            assert_local_files_count(destination, self.test_files_count, case)
            ls_results = get_pipe_listing(source)
            assert len(ls_results) == 0, "Source path shall be empty after mv operation"
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(case, e.message))



