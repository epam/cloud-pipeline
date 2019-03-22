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
from common_utils.entity_managers import EntityManager
from common_utils.pipe_cli import *

ERROR_MESSAGE = "An error accrued in case "


class TestMkdirMvtodir(object):
    test_folder = "mkdir-test-folder"
    test_folder_1 = "test-folder1"
    test_folder_2 = "test-folder2"
    bucket = 'epmcmbibpc-mkdir-mvtodir-it{}'.format(get_test_prefix())
    path_to_bucket = 'cp://{}'.format(bucket)
    negative_test_case = "epmcmbibpc-1022"

    @classmethod
    def setup_class(cls):
        create_data_storage(cls.bucket)

    @classmethod
    def teardown_class(cls):
        delete_data_storage(cls.bucket)

    def teardown_method(self, method):
        pipe_storage_rm('cp://{}/{}'.format(self.bucket, self.test_folder), recursive=True, expected_status=None)

    @pytest.mark.skip()
    def test_mkdir_without_name(self):
        destination = 'cp://{}/'.format(self.bucket)
        try:
            pipe_output = pipe_storage_mkdir([destination], expected_status=1)[1]
            assert "Error" in pipe_output[0]
            pipe_output = get_pipe_listing(self.path_to_bucket)
            assert len(pipe_output) == 0
            destination = 'cp://{}//'.format(self.bucket)
            pipe_output = pipe_storage_mkdir([destination], expected_status=1)[1]
            assert "Error" in pipe_output[0]
            pipe_output = get_pipe_listing(self.path_to_bucket)
            assert len(pipe_output) == 0
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "epmcmbibpc-1018:" + "\n" + e.message)

    def test_mkdir(self):
        destination = 'cp://{}/{}'.format(self.bucket, self.test_folder)
        try:
            pipe_storage_mkdir([destination], expected_status=0)
            pipe_output = get_pipe_listing(self.path_to_bucket)
            aws_output = get_aws_listing(self.bucket)
            compare_listing(pipe_output, aws_output, 1)
            pipe_output = pipe_storage_mkdir([destination], expected_status=1)[1]
            assert "already exists" in pipe_output[0]
            destination = 'cp://{}/{}'.format(self.bucket, self.test_folder.upper())
            pipe_storage_mkdir([destination], expected_status=0)
            pipe_output = get_pipe_listing(self.path_to_bucket)
            aws_output = get_aws_listing(self.bucket)
            compare_listing(pipe_output, aws_output, 2)
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + "epmcmbibpc-1015:" + "\n" + e.message)

    def test_mvtodir_with_path(self):
        test_case = "epmcmbibpc-1020"
        path_to_folder_1 = "{}/{}".format(self.test_folder, self.test_folder_1)
        path_to_folder_2 = "{}/{}".format(self.test_folder, self.test_folder_2)
        folder_manager = EntityManager.get_manager('FOLDER')
        data_storage_manager = EntityManager.get_manager('DATA_STORAGE')
        data_storage_id = data_storage_manager.create(test_case)
        root_folder_id = folder_manager.create(self.test_folder)
        folder_id_1 = folder_manager.create(self.test_folder_1, root_folder_id)
        folder_id_2 = folder_manager.create(self.test_folder_2, root_folder_id)
        try:
            pipe_storage_mvtodir(test_case, directory=path_to_folder_1, expected_status=0)
            parent_id = data_storage_manager.get_parent_id_by_name(test_case)
            assert folder_id_1 == parent_id
            pipe_storage_mvtodir(test_case, directory=path_to_folder_2, expected_status=0)
            parent_id = data_storage_manager.get_parent_id_by_name(test_case)
            assert folder_id_2 == parent_id
        except BaseException as e:
            self.cleanup(data_storage_id, root_folder_id, folder_id_1, folder_id_2)
            pytest.fail(ERROR_MESSAGE + test_case + ":\n" + e.message)
        self.cleanup(data_storage_id, root_folder_id, folder_id_1, folder_id_2)

    @staticmethod
    def cleanup(data_storage_id, root_folder_id, folder_id_1, folder_id_2):
        folder_manager = EntityManager.get_manager('FOLDER')
        data_storage_manager = EntityManager.get_manager('DATA_STORAGE')
        data_storage_manager.delete(data_storage_id)
        folder_manager.delete(folder_id_1)
        folder_manager.delete(folder_id_2)
        folder_manager.delete(root_folder_id)

    def test_move_data_storage_to_nonexistent_folder(self):
        folder_name = "nonexistent_folder"
        try:
            pipe_output = pipe_storage_mvtodir(self.bucket, directory=folder_name, expected_status=1)[1]
            assert pipe_output[0] == "Directory with name {} does not exist!".format(folder_name)
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + self.negative_test_case + "\n" + e.message)

    def test_move_nonexistent_data_storage(self):
        data_storage_name = "nonexistent-data-storage"
        folder_name = "nonexistent-data-storage-test-{}".format(self.test_folder)
        folder_manager = EntityManager.get_manager('FOLDER')
        folder_id = folder_manager.create(folder_name)
        try:
            pipe_output = pipe_storage_mvtodir(data_storage_name, folder_name, expected_status=1)[1]
            assert pipe_output[0] == "Error: Datastorage with name {} does not exist!".format(data_storage_name)
        except BaseException as e:
            folder_manager.delete(folder_id)
            pytest.fail(ERROR_MESSAGE + self.negative_test_case + "\n" + e.message)
        folder_manager.delete(folder_id)

    def test_move_nonexistent_data_storage_to_nonexistent_folder(self):
        folder_name = "nonexistent_folder"
        data_storage_name = "nonexistent-data-storage"
        try:
            pipe_output = pipe_storage_mvtodir(data_storage_name, directory=folder_name, expected_status=1)[1]
            assert pipe_output[0] == "Directory with name {} does not exist!".format(folder_name)
        except BaseException as e:
            pytest.fail(ERROR_MESSAGE + self.negative_test_case + "\n" + e.message)
