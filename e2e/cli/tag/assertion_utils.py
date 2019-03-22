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

from common_utils.pipe_cli import pipe_tag_get, pipe_tag_set, pipe_tag_delete

OBJECT_1 = ('key_1', 'value_1', 'string')
OBJECT_2 = ('key_2', 'value_2', 'string')
OBJECT_3 = ('key_3', 'value_3', 'string')
OBJECT_4 = ('key_4', 'value_4', 'string')
NON_EXISTING_OBJECT = ('non-existing', 'value_2', 'string')
NEW_OBJECT_2 = ('key_2', 'new_value', 'string')


def access_denied(entity_class, entity_identifier, token):
    expected_tags = prepare_for_role_model_test(entity_class, entity_identifier)
    # try to get tags for object
    error_text = pipe_tag_get(entity_class, entity_identifier, token=token)[1]
    assert_access_is_denied(entity_class, entity_identifier, error_text, expected_tags)
    access_is_denied_for_write_operations(entity_class, entity_identifier, expected_tags, token)


def prepare_for_role_model_test(entity_class, entity_identifier):
    expected_tags = list()
    expected_tags.append(OBJECT_1)
    expected_tags.append(OBJECT_2)
    # create tags
    set_result = pipe_tag_set(entity_class, entity_identifier, args=convert_list_to_input_pairs(expected_tags))
    assert not set_result[1]
    assert set_result[0][0] == "Metadata for {} {} updated.".format(entity_class, entity_identifier)
    get_result = pipe_tag_get(entity_class, entity_identifier)
    result_tags = parse_output_table(get_result[0])
    assert not get_result[1]
    assert result_tags == expected_tags
    return expected_tags


def access_is_denied_for_write_operations(entity_class, entity_identifier, expected_tags, token):
    # try to create tags for object
    error_text = pipe_tag_set(entity_class, entity_identifier, token=token,
                              args=convert_list_to_input_pairs(list(OBJECT_3)))[1]
    assert_access_is_denied(entity_class, entity_identifier, error_text, expected_tags)
    # try to delete keys for object
    keys = convert_list_to_input_keys(expected_tags)
    error_text = pipe_tag_delete(entity_class, entity_identifier, args=keys, token=token)[1]
    assert_access_is_denied(entity_class, entity_identifier, error_text, expected_tags)
    # try to update existing key for object
    error_text = pipe_tag_set(entity_class, entity_identifier, token=token,
                              args=convert_list_to_input_pairs(list(NEW_OBJECT_2)))[1]
    assert_access_is_denied(entity_class, entity_identifier, error_text, expected_tags)
    # try to delete all data for object
    error_text = pipe_tag_delete(entity_class, entity_identifier, token=token)[1]
    assert_access_is_denied(entity_class, entity_identifier, error_text, expected_tags)


def crud_metadata(entity_class, entity_identifier, token=None):
    tags_to_set = list()
    tags_to_set.append(OBJECT_1)
    tags_to_set.append(OBJECT_2)
    # create tags
    set_result = pipe_tag_set(entity_class, entity_identifier, token=token,
                              args=convert_list_to_input_pairs(tags_to_set))
    assert not set_result[1]
    assert set_result[0][0] == "Metadata for {} {} updated.".format(entity_class, entity_identifier)
    assert_tags(entity_class, entity_identifier, tags_to_set, token)
    # delete keys
    tags_to_set.remove(OBJECT_2)
    keys = convert_list_to_input_keys(tags_to_set)
    delete_result = pipe_tag_delete(entity_class, entity_identifier, token=token, args=keys)
    tags_to_set.remove(OBJECT_1)
    tags_to_set.append(OBJECT_2)
    assert not delete_result[1]
    assert delete_result[0][0] == "Deleted keys from metadata for {} {}: {}" \
        .format(entity_class, entity_identifier, ', '.join(keys))
    assert_tags(entity_class, entity_identifier, tags_to_set, token)
    # update existing key
    tags_to_set.append(NEW_OBJECT_2)
    tags_to_set.remove(OBJECT_2)
    set_result = pipe_tag_set(entity_class, entity_identifier, token=token,
                              args=convert_list_to_input_pairs(tags_to_set))
    assert not set_result[1]
    assert set_result[0][0] == "Metadata for {} {} updated.".format(entity_class, entity_identifier)
    assert_tags(entity_class, entity_identifier, tags_to_set, token)
    # add new keys
    tags_to_set.remove(NEW_OBJECT_2)
    tags_to_set.append(OBJECT_4)
    tags_to_set.append(OBJECT_3)
    set_result = pipe_tag_set(entity_class, entity_identifier, token=token,
                              args=convert_list_to_input_pairs(tags_to_set))
    assert not set_result[1]
    assert set_result[0][0] == "Metadata for {} {} updated.".format(entity_class, entity_identifier)
    tags_to_set.append(NEW_OBJECT_2)
    assert_tags(entity_class, entity_identifier, tags_to_set, token)
    # delete all data
    delete_result = pipe_tag_delete(entity_class, entity_identifier, token=token)
    assert not delete_result[1]
    assert delete_result[0][0] == "Metadata for {} {} deleted.".format(entity_class, entity_identifier)
    get_result = pipe_tag_get(entity_class, entity_identifier, token=token)
    assert not get_result[1]
    assert get_result[0][0] == "No metadata available for {} {}.".format(entity_class, entity_identifier)
    # could not delete non existing metadata
    delete_result = pipe_tag_delete(entity_class, entity_identifier, token=token)
    assert "Error: Failed to fetch data from server. Server responded with message:". \
               format(entity_identifier, str(entity_class).upper()) in delete_result[1][0]


def assert_tags(entity_class, entity_identifier, tags_to_set, token):
    get_result = pipe_tag_get(entity_class, entity_identifier, token=token)
    result_tags = parse_output_table(get_result[0])
    assert not get_result[1]
    assert result_tags == tags_to_set


def assert_access_is_denied(entity_class, entity_identifier, error_text, expected_tags):
    assert 'Access is denied' in error_text[0]
    get_result = pipe_tag_get(entity_class, entity_identifier)
    assert not get_result[1]
    result_tags = parse_output_table(get_result[0])
    assert result_tags == expected_tags


def parse_output_table(get_result):
    if len(get_result) == 1:
        assert "No metadata available for" in get_result[0]
        return list()
    get_result.pop(0)
    get_result.pop(0)
    get_result.pop(0)  # skip header
    result_tags = list()
    for item in get_result:
        splitted = filter(None, item.replace(" ", "").split("|"))
        if not str(splitted[0]).startswith("+"):
            result_tags.append((splitted[0], splitted[1], splitted[2]))
    return result_tags


def convert_list_to_input_pairs(tags):
    result = list()
    for tag in tags:
        result.append('='.join([tag[0], tag[1]]))
    return result


def convert_list_to_input_keys(tags):
    result = list()
    for tag in tags:
        result.append(tag[0])
    return result
