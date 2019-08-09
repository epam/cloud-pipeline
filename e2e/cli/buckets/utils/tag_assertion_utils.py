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

from buckets.utils.cloud.utilities import *
from common_utils.pipe_cli import *


def assert_tags_listing(bucket, path, expected_tags, version=None, token=None):
    stdout, stderr = get_storage_tags(path, version=version, token=token)
    output = parse_tag_table(stdout)
    compare_tags('pipe storage', expected_tags, output)
    actual_tags = list_object_tags(bucket, path[len(bucket) + 6:], version=version)
    compare_tags('Getting object tags', expected_tags, actual_tags)


def compare_tags(name, expected_tags, output):
    filtered_output = [tag for tag in output if 'CP_OWNER' not in tag and 'CP_SOURCE' not in tag]
    assert len(filtered_output) == len(expected_tags), '{}: Length of tags listing {} does not match expected {}' \
        .format(name, len(filtered_output), len(expected_tags))
    for tag in expected_tags:
        assert tag[0] in output, '{}: Expected tag {} is not present'.format(name, tag[0])
        assert tag[1] == output[tag[0]], '{}: Tag {} does not have expected value {}, actual value is {}' \
            .format(name, tag[0], tag[1], output[tag[0]])


def parse_tag_table(get_result):
    if len(get_result) == 1:
        assert "No tags available for path" in get_result[0]
        return {}
    get_result.pop(0)
    get_result.pop(0)
    get_result.pop(0)  # skip header
    result_tags = {}
    for item in get_result:
        splitted = filter(None, item.replace(" ", "").split("|"))
        if not str(splitted[0]).startswith("+"):
            result_tags[splitted[0]] = splitted[1]
    return result_tags
