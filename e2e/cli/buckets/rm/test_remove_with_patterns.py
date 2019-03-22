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
from ..utils.utilities_for_test import *


class TestRmWithPatterns(object):
    epam_test_case = "EPMCMBIBPC-615"
    resources_root = "resources-{}/".format(epam_test_case).lower()
    bucket_name = "epmcmbibpc-it-rm-{}{}".format(epam_test_case, get_test_prefix()).lower()
    json_file = "test.json"
    text_file = "test.txt"
    json_file_in_folder = "folder.json"
    text_file_in_folder = "test.txt"
    test_folder = TestFiles.TEST_FOLDER

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        create_buckets(cls.bucket_name)
        sources_root = os.path.abspath(cls.resources_root)
        create_test_folder(sources_root)
        create_test_file(os.path.join(sources_root, cls.json_file), TestFiles.DEFAULT_CONTENT)
        create_test_file(os.path.join(sources_root, cls.text_file), TestFiles.DEFAULT_CONTENT)
        create_test_file(os.path.join(sources_root, cls.test_folder, cls.json_file_in_folder),
                         TestFiles.DEFAULT_CONTENT)
        create_test_file(os.path.join(sources_root, cls.test_folder, cls.text_file_in_folder),
                         TestFiles.DEFAULT_CONTENT)
        pipe_storage_cp(cls.resources_root, "cp://{}/{}/".format(cls.bucket_name, cls.resources_root), recursive=True)
        logging.info("Uploaded test files to bucket {}".format(cls.bucket_name))

    @classmethod
    def teardown_class(cls):
        delete_buckets(cls.bucket_name)
        clean_test_data(os.path.abspath(cls.resources_root))

    @pytest.mark.run(order=1)
    def test_rm_with_exclude(self):
        try:
            pipe_storage_rm("cp://{}/{}".format(self.bucket_name, self.resources_root),
                            recursive=True,
                            args=["--exclude", "*.json", "--exclude", self.test_folder + "*"])
            deleted_files = [self.resources_root + self.text_file]
            skipped_files = [self.resources_root + self.json_file,
                             self.resources_root + self.test_folder + self.json_file_in_folder,
                             self.resources_root + self.test_folder + self.text_file_in_folder]
            assert_files_skipped(self.bucket_name, *skipped_files)
            assert_files_deleted(self.bucket_name, *deleted_files)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=2)
    def test_rm_with_include(self):
        try:
            pipe_storage_cp(self.resources_root + self.text_file,
                            "cp://{}/{}/".format(self.bucket_name, self.resources_root), force=True, recursive=True)
            pipe_storage_rm("cp://{}/{}".format(self.bucket_name, self.resources_root),
                            recursive=True,
                            args=["--include", "*.json", "--include", self.test_folder + "*"])
            skipped_files = [self.resources_root + self.text_file]
            deleted_files = [self.resources_root + self.json_file,
                             self.resources_root + self.test_folder + self.json_file_in_folder,
                             self.resources_root + self.test_folder + self.text_file_in_folder]
            assert_files_skipped(self.bucket_name, *skipped_files)
            assert_files_deleted(self.bucket_name, *deleted_files)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))

    @pytest.mark.run(order=3)
    def test_rm_with_both_patterns(self):
        try:
            pipe_storage_cp(self.resources_root, "cp://{}/{}/".format(self.bucket_name, self.resources_root),
                            force=True, recursive=True)
            pipe_storage_rm("cp://{}/{}".format(self.bucket_name, self.resources_root),
                            recursive=True,
                            args=["--include", self.test_folder + "*", "--exclude", "*/*.json"])
            deleted_files = [self.resources_root + self.test_folder + self.text_file_in_folder]
            skipped_files = [self.resources_root + self.text_file,
                             self.resources_root + self.json_file,
                             self.resources_root + self.test_folder + self.json_file_in_folder]
            assert_files_skipped(self.bucket_name, *skipped_files)
            assert_files_deleted(self.bucket_name, *deleted_files)
        except AssertionError as e:
            pytest.fail("Test case {} failed. {}".format(self.epam_test_case, e.message))
