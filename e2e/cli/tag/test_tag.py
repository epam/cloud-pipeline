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

import pytest

from assertion_utils import *
from common_utils.entity_managers import EntityManager
from common_utils.pipe_cli import *

ERROR_MESSAGE = "An error occurred in case "
PIPELINE = 'pipeline'
FOLDER = 'folder'
DATA_STORAGE = 'data_storage'


class TestMetadataOperations(object):

    token = os.environ['USER_TOKEN']
    user = os.environ['TEST_USER']

    """
    1. epam test case
    2. entity class
    3. true if command runs by object id, otherwise by name
    """
    test_case_for_crud = [
        ("epmcmbibpc-918", PIPELINE, True),
        ("epmcmbibpc-922", PIPELINE, False),
        ("epmcmbibpc-924", FOLDER, True),
        ("epmcmbibpc-925", FOLDER, False),
        ("epmcmbibpc-926", DATA_STORAGE, True),
        ("epmcmbibpc-930", DATA_STORAGE, False)
    ]

    @pytest.mark.parametrize("test_case,entity_class,by_id", test_case_for_crud)
    def test_crud_metadata(self, test_case, entity_class, by_id):
        manager = EntityManager.get_manager(entity_class)
        object_name = "".join([test_case, get_test_prefix()])
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
    1. epam test case
    2. entity class
    3. true if command runs by object id, otherwise by name
    """
    test_case_for_permissions = [
        ("epmcmbibpc-965", PIPELINE, True),
        ("epmcmbibpc-966", PIPELINE, False),
        ("epmcmbibpc-967", FOLDER, True),
        ("epmcmbibpc-968", FOLDER, False),
        ("epmcmbibpc-969", DATA_STORAGE, True),
        ("epmcmbibpc-970", DATA_STORAGE, False)
    ]

    @pytest.mark.parametrize("test_case,entity_class,by_id", test_case_for_permissions)
    def test_metadata_permissions(self, test_case, entity_class, by_id):
        manager = EntityManager.get_manager(entity_class)
        object_name = "".join([get_test_prefix(), "-", test_case])
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
        test_case = "epmcmbibpc-999"
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
            1. epam test case
            2. entity class
            3. true if command runs by object id, otherwise by name
    """
    test_case_for_non_existing_class = [
        ("epmcmbibpc-1000-1", PIPELINE, True),
        ("epmcmbibpc-1000-2", PIPELINE, False),
        ("epmcmbibpc-1000-3", FOLDER, True),
        ("epmcmbibpc-1000-4", FOLDER, False),
        ("epmcmbibpc-1000-5", DATA_STORAGE, True),
        ("epmcmbibpc-1000-6", DATA_STORAGE, False)
    ]

    @pytest.mark.parametrize("test_case,entity_class,by_id", test_case_for_non_existing_class)
    def test_metadata_non_existing_class(self, test_case, entity_class, by_id):
        manager = EntityManager.get_manager(entity_class)
        object_name = "".join([test_case, get_test_prefix()])
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
            raise AssertionError(ERROR_MESSAGE + test_case + ":" + e.message)
        except BaseException as e:
            manager.delete(object_id)
            raise RuntimeError(ERROR_MESSAGE + test_case + ":" + e.message)
        manager.delete(object_id)

    """
    1. epam test case
    2. entity class
    3. true if command runs by object id, otherwise by name
    """
    test_case_for_set_incorrect_key_value_pair = [
        ("epmcmbibpc-1001-1", PIPELINE, True),
        ("epmcmbibpc-1001-2", PIPELINE, False),
        ("epmcmbibpc-1001-3", FOLDER, True),
        ("epmcmbibpc-1001-4", FOLDER, False),
        ("epmcmbibpc-1001-5", DATA_STORAGE, True),
        ("epmcmbibpc-1001-6", DATA_STORAGE, False)
    ]

    @pytest.mark.parametrize("test_case,entity_class,by_id", test_case_for_set_incorrect_key_value_pair)
    def test_metadata_set_incorrect_key_value_pair(self, test_case, entity_class, by_id):
        manager = EntityManager.get_manager(entity_class)
        object_name = "".join([test_case, get_test_prefix()])
        object_id = manager.create(object_name)
        entity_identifier = str(object_id) if by_id else object_name
        try:
            set_result = pipe_tag_set(entity_class, entity_identifier, args=['key_1'])
            assert "Error: Tags must be specified as KEY=VALUE pair." == set_result[1][0]
        except AssertionError as e:
            manager.delete(object_id)
            raise AssertionError(ERROR_MESSAGE + test_case + ":" + e.message)
        except BaseException as e:
            manager.delete(object_id)
            raise RuntimeError(ERROR_MESSAGE + test_case + ":" + e.message)
        manager.delete(object_id)

    """
        1. epam test case
        2. entity class
        3. true if command runs by object id, otherwise by name
        """
    test_case_for_set_incorrect_key_value_pair = [
        ("epmcmbibpc-1003-1", PIPELINE, True),
        ("epmcmbibpc-1003-2", PIPELINE, False),
        ("epmcmbibpc-1003-3", FOLDER, True),
        ("epmcmbibpc-1003-4", FOLDER, False),
        ("epmcmbibpc-1003-5", DATA_STORAGE, True),
        ("epmcmbibpc-1003-6", DATA_STORAGE, False)
    ]

    @pytest.mark.parametrize("test_case,entity_class,by_id", test_case_for_set_incorrect_key_value_pair)
    def test_delete_keys_from_empty_metadata(self, test_case, entity_class, by_id):
        manager = EntityManager.get_manager(entity_class)
        object_name = "".join([test_case, get_test_prefix()])
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
            raise AssertionError(ERROR_MESSAGE + test_case + ":" + e.message)
        except BaseException as e:
            manager.delete(object_id)
            raise RuntimeError(ERROR_MESSAGE + test_case + ":" + e.message)
        manager.delete(object_id)

    """
    1. epam test case
    2. entity class
    3. true if command runs by object id, otherwise by name
    """
    test_case_for_set_incorrect_key_value_pair = [
        ("epmcmbibpc-1002-1", PIPELINE, True),
        ("epmcmbibpc-1002-2", PIPELINE, False),
        ("epmcmbibpc-1002-3", FOLDER, True),
        ("epmcmbibpc-1002-4", FOLDER, False),
        ("epmcmbibpc-1002-5", DATA_STORAGE, True),
        ("epmcmbibpc-1002-6", DATA_STORAGE, False)
    ]

    @pytest.mark.parametrize("test_case,entity_class,by_id", test_case_for_set_incorrect_key_value_pair)
    def test_delete_non_existing_key(self, test_case, entity_class, by_id):
        manager = EntityManager.get_manager(entity_class)
        object_name = "".join([test_case, get_test_prefix()])
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
            raise AssertionError(ERROR_MESSAGE + test_case + ":" + e.message)
        except BaseException as e:
            manager.delete(object_id)
            raise RuntimeError(ERROR_MESSAGE + test_case + ":" + e.message)
        manager.delete(object_id)
