# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

from assertion_utils import *
from common_utils.entity_managers import EntityManager
from common_utils.pipe_cli import *
from common_utils.test_utils import format_name

ERROR_MESSAGE = "An error occurred in case "
PIPELINE = 'pipeline'
FOLDER = 'folder'
DATA_STORAGE = 'data_storage'
COMMON_TEST_CASE_PREFIX = 'TC-PIPE-'


class TestMetadataOperations(object):

    token = os.environ['USER_TOKEN']
    user = os.environ['TEST_USER']

    """
    1. test case
    2. entity class
    3. true if command runs by object id, otherwise by name
    """
    test_case_for_crud = [
        ("TC-PIPE-TAG-1", PIPELINE, True),
        ("TC-PIPE-TAG-2", PIPELINE, False),
        ("TC-PIPE-TAG-3", FOLDER, True),
        ("TC-PIPE-TAG-4", FOLDER, False),
        ("TC-PIPE-TAG-5", DATA_STORAGE, True),
        ("TC-PIPE-TAG-6", DATA_STORAGE, False)
    ]

    @pytest.mark.parametrize("test_case,entity_class,by_id", test_case_for_crud)
    def test_crud_metadata(self, test_case, entity_class, by_id):
        manager = EntityManager.get_manager(entity_class)
        object_name = self._build_object_name(test_case)
        object_id = manager.create(object_name)
        entity_identifier = str(object_id) if by_id else object_name
        try:
            crud_metadata(entity_class, entity_identifier)
        except AssertionError as e:
            manager.delete(object_id)
            raise AssertionError(ERROR_MESSAGE + test_case + ":" + e.message)
        except BaseException as e:
            manager.delete(object_id)
            raise RuntimeError(ERROR_MESSAGE + test_case + ":" + e.message)
        manager.delete(object_id)

    """
    1. test case
    2. entity class
    3. true if command runs by object id, otherwise by name
    """
    test_case_for_permissions = [
        ("TC-PIPE-TAG-7", PIPELINE, True),
        ("TC-PIPE-TAG-8", PIPELINE, False),
        ("TC-PIPE-TAG-9", FOLDER, True),
        ("TC-PIPE-TAG-10", FOLDER, False),
        ("TC-PIPE-TAG-11", DATA_STORAGE, True),
        ("TC-PIPE-TAG-12", DATA_STORAGE, False)
    ]

    @pytest.mark.parametrize("test_case,entity_class,by_id", test_case_for_permissions)
    def test_metadata_permissions(self, test_case, entity_class, by_id):
        manager = EntityManager.get_manager(entity_class)
        object_name = self._build_object_name(test_case)
        object_id = manager.create(object_name)
        entity_identifier = str(object_id) if by_id else object_name
        try:
            access_denied(entity_class, entity_identifier, self.token)
            expected_tags = prepare_for_role_model_test(entity_class, entity_identifier)
            exit_code = set_acl_permissions(self.user, entity_identifier, entity_class, allow='r')
            assert exit_code == 0, "Failed to set read permissions for {} {}.".format(entity_class, entity_identifier)
            assert_tags(entity_class, entity_identifier, expected_tags, self.token)
            access_is_denied_for_write_operations(entity_class, entity_identifier, expected_tags, self.token)
            exit_code = set_acl_permissions(self.user, entity_identifier, entity_class, allow='rw')
            assert exit_code == 0, "Failed to set read-write permissions for {} {}.".format(entity_class,
                                                                                            entity_identifier)
            access_is_denied_for_write_operations(entity_class, entity_identifier, expected_tags, self.token)
            assert_tags(entity_class, entity_identifier, expected_tags, self.token)
        except AssertionError as e:
            manager.delete(object_id)
            raise AssertionError(ERROR_MESSAGE + test_case + ":" + e.message)
        except BaseException as e:
            manager.delete(object_id)
            raise RuntimeError(ERROR_MESSAGE + test_case + ":" + e.message)
        manager.delete(object_id)

    test_case_for_non_existing_object = [PIPELINE, FOLDER, DATA_STORAGE]

    @pytest.mark.parametrize("entity_class", test_case_for_non_existing_object)
    def test_metadata_non_existing_object(self, entity_class):
        """TC-PIPE-TAG-13"""
        test_case = "TC-PIPE-TAG-13"
        entity_identifier = 'non_existing-' + test_case
        try:
            tags_to_set = list()
            tags_to_set.append(OBJECT_1)
            tags_to_set.append(OBJECT_2)
            set_result = pipe_tag_set(entity_class, entity_identifier, args=convert_list_to_input_pairs(tags_to_set))
            assert "id: '{}' was not found.".format(entity_identifier) in set_result[1][0]
            get_result = pipe_tag_get(entity_class, entity_identifier)
            assert "id: '{}' was not found.".format(entity_identifier) in get_result[1][0]
            delete_result = pipe_tag_delete(entity_class, entity_identifier,
                                            args=convert_list_to_input_keys(tags_to_set))
            assert "id: '{}' was not found.".format(entity_identifier) in delete_result[1][0]
            delete_result = pipe_tag_delete(entity_class, entity_identifier,
                                            args=convert_list_to_input_keys(tags_to_set))
            assert "id: '{}' was not found.".format(entity_identifier) in delete_result[1][0]
        except AssertionError as e:
            raise AssertionError(ERROR_MESSAGE + test_case + ":" + e.message)
        except BaseException as e:
            raise RuntimeError(ERROR_MESSAGE + test_case + ":" + e.message)

    """
            1. test case
            2. entity class
            3. true if command runs by object id, otherwise by name
    """
    test_case_for_non_existing_class = [
        ("TC-PIPE-TAG-14-1", PIPELINE, True),
        ("TC-PIPE-TAG-14-2", PIPELINE, False),
        ("TC-PIPE-TAG-14-3", FOLDER, True),
        ("TC-PIPE-TAG-14-4", FOLDER, False),
        ("TC-PIPE-TAG-14-5", DATA_STORAGE, True),
        ("TC-PIPE-TAG-14-6", DATA_STORAGE, False)
    ]

    @pytest.mark.parametrize("case,entity_class,by_id", test_case_for_non_existing_class)
    def test_metadata_non_existing_class(self, case, entity_class, by_id):
        """TC-PIPE-TAG-14"""
        manager = EntityManager.get_manager(entity_class)
        object_name = self._build_object_name(case)
        object_id = manager.create(object_name)
        entity_identifier = str(object_id) if by_id else object_name
        non_existing = 'non-existing'
        try:
            tags_to_set = list()
            tags_to_set.append(OBJECT_1)
            tags_to_set.append(OBJECT_2)
            set_result = pipe_tag_set(non_existing, entity_identifier, args=convert_list_to_input_pairs(tags_to_set))
            assert "Error: Class '{}' does not exist.".format(non_existing) == set_result[1][0]
            get_result = pipe_tag_get(non_existing, entity_identifier)
            assert "Error: Class '{}' does not exist.".format(non_existing) == get_result[1][0]
            delete_result = pipe_tag_delete(non_existing, entity_identifier,
                                            args=convert_list_to_input_keys(tags_to_set))
            assert "Error: Class '{}' does not exist.".format(non_existing) == delete_result[1][0]
            delete_result = pipe_tag_delete(non_existing, entity_identifier,
                                            args=convert_list_to_input_keys(tags_to_set))
            assert "Error: Class '{}' does not exist.".format(non_existing) == delete_result[1][0]
        except AssertionError as e:
            manager.delete(object_id)
            raise AssertionError(ERROR_MESSAGE + case + ":" + e.message)
        except BaseException as e:
            manager.delete(object_id)
            raise RuntimeError(ERROR_MESSAGE + case + ":" + e.message)
        manager.delete(object_id)

    """
    1. test case
    2. entity class
    3. true if command runs by object id, otherwise by name
    """
    test_case_for_set_incorrect_key_value_pair = [
        ("TC-PIPE-TAG-15-1", PIPELINE, True),
        ("TC-PIPE-TAG-15-2", PIPELINE, False),
        ("TC-PIPE-TAG-15-3", FOLDER, True),
        ("TC-PIPE-TAG-15-4", FOLDER, False),
        ("TC-PIPE-TAG-15-5", DATA_STORAGE, True),
        ("TC-PIPE-TAG-15-6", DATA_STORAGE, False)
    ]

    @pytest.mark.parametrize("case,entity_class,by_id", test_case_for_set_incorrect_key_value_pair)
    def test_metadata_set_incorrect_key_value_pair(self, case, entity_class, by_id):
        """TC-PIPE-TAG-15"""
        manager = EntityManager.get_manager(entity_class)
        object_name = self._build_object_name(case)
        object_id = manager.create(object_name)
        entity_identifier = str(object_id) if by_id else object_name
        try:
            set_result = pipe_tag_set(entity_class, entity_identifier, args=['key_1'])
            assert "Error: Tags must be specified as KEY=VALUE pair." == set_result[1][0]
        except AssertionError as e:
            manager.delete(object_id)
            raise AssertionError(ERROR_MESSAGE + case + ":" + e.message)
        except BaseException as e:
            manager.delete(object_id)
            raise RuntimeError(ERROR_MESSAGE + case + ":" + e.message)
        manager.delete(object_id)

    """
        1. test case
        2. entity class
        3. true if command runs by object id, otherwise by name
        """
    test_case_for_set_incorrect_key_value_pair = [
        ("TC-PIPE-TAG-17-1", PIPELINE, True),
        ("TC-PIPE-TAG-17-2", PIPELINE, False),
        ("TC-PIPE-TAG-17-3", FOLDER, True),
        ("TC-PIPE-TAG-17-4", FOLDER, False),
        ("TC-PIPE-TAG-17-5", DATA_STORAGE, True),
        ("TC-PIPE-TAG-17-6", DATA_STORAGE, False)
    ]

    @pytest.mark.parametrize("case,entity_class,by_id", test_case_for_set_incorrect_key_value_pair)
    def test_delete_keys_from_empty_metadata(self, case, entity_class, by_id):
        """TC-PIPE-TAG-17"""
        manager = EntityManager.get_manager(entity_class)
        object_name = self._build_object_name(case)
        object_id = manager.create(object_name)
        entity_identifier = str(object_id) if by_id else object_name
        try:
            tags_to_set = list()
            tags_to_set.append(OBJECT_1)
            tags_to_set.append(OBJECT_2)
            delete_result = pipe_tag_delete(entity_class, entity_identifier,
                                            args=convert_list_to_input_keys(tags_to_set))
            assert "not found.".format(entity_identifier, entity_class) in delete_result[1][0]
            delete_result = pipe_tag_delete(entity_class, entity_identifier,
                                            args=convert_list_to_input_keys(tags_to_set))
            assert "not found.".format(entity_identifier, entity_class) in delete_result[1][0]
        except AssertionError as e:
            manager.delete(object_id)
            raise AssertionError(ERROR_MESSAGE + case + ":" + e.message)
        except BaseException as e:
            manager.delete(object_id)
            raise RuntimeError(ERROR_MESSAGE + case + ":" + e.message)
        manager.delete(object_id)

    """
    1. test case
    2. entity class
    3. true if command runs by object id, otherwise by name
    """
    test_case_for_set_incorrect_key_value_pair = [
        ("TC-PIPE-TAG-16-1", PIPELINE, True),
        ("TC-PIPE-TAG-16-2", PIPELINE, False),
        ("TC-PIPE-TAG-16-3", FOLDER, True),
        ("TC-PIPE-TAG-16-4", FOLDER, False),
        ("TC-PIPE-TAG-16-5", DATA_STORAGE, True),
        ("TC-PIPE-TAG-16-6", DATA_STORAGE, False)
    ]

    @pytest.mark.parametrize("case,entity_class,by_id", test_case_for_set_incorrect_key_value_pair)
    def test_delete_non_existing_key(self, case, entity_class, by_id):
        """TC-PIPE-TAG-16"""
        manager = EntityManager.get_manager(entity_class)
        object_name = self._build_object_name(case)
        object_id = manager.create(object_name)
        entity_identifier = str(object_id) if by_id else object_name
        non_existing = 'non-existing'
        try:
            tags_to_set = list()
            tags_to_set.append(OBJECT_1)
            pipe_tag_set(entity_class, entity_identifier, args=convert_list_to_input_pairs(tags_to_set))
            delete_result = pipe_tag_delete(entity_class, entity_identifier, args=[non_existing])
            assert "Could not delete non existing key." in delete_result[1][0]
            get_result = pipe_tag_get(entity_class, entity_identifier)
            result_tags = parse_output_table(get_result[0])
            assert not get_result[1]
            assert result_tags == tags_to_set
        except AssertionError as e:
            manager.delete(object_id)
            raise AssertionError(ERROR_MESSAGE + case + ":" + e.message)
        except BaseException as e:
            manager.delete(object_id)
            raise RuntimeError(ERROR_MESSAGE + case + ":" + e.message)
        manager.delete(object_id)

    @staticmethod
    def _build_object_name(test_case):
        return format_name("".join([test_case.replace(COMMON_TEST_CASE_PREFIX, ""), get_test_prefix()]).lower())
