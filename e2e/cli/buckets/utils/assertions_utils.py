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

from common_utils.pipe_cli import pipe_storage_cp
from buckets.utils.object_info import ObjectInfo
from buckets.utils.cloud.utilities import *
from buckets.utils.file_utils import *


def assert_error_message_is_present(output, message):
    text = "".join(output)
    assert message in text, "Command output '{}' doesn't contain expected message: '{}'".format(text, message)


def assert_files_skipped(bucket_name, *files):
    for path in files:
        if bucket_name:
            assert object_exists(bucket_name, path), "File '{}' should be skipped, but was deleted".format(path)
        else:
            assert os.path.exists(os.path.abspath(path)), "File '{}' should be skipped, but was deleted".format(path)


def assert_files_deleted(bucket_name, *files):
    for path in files:
        if bucket_name:
            assert not object_exists(bucket_name, path), "File '{}' should be deleted, but was skipped".format(path)
        else:
            assert not os.path.exists(os.path.abspath(path)), "File '{}' should be deleted, but was skipped".format(path)


def create_test_files_on_bucket(source_file, bucket_name, *keys):
    for key in keys:
        pipe_storage_cp(source_file, "cp://{}/".format(bucket_name) + key, expected_status=0)
        assert object_exists(bucket_name, key), "Test object {} does not exist on bucket {}.".format(key,
                                                                                                     bucket_name)


def assert_copied_object_info(source, destination, case):
    try:
        assert destination.exists, "Object {} does not exist.".format(destination.path)
        assert destination.size == source.size, \
            "Sizes must be the same.\nExpected {}\nActual {}".format(source.size, destination.size)
        assert destination.last_modified >= source.last_modified, \
            "Last modified time of destination file must be more than last modified time for source file.\n" \
            "Expected {}\nActual {}".format(source.last_modified, destination.last_modified)
    except AssertionError as e:
        pytest.fail("The test case {} failed.\n{}".format(case, e.message))


def assert_copied_object_does_not_exist(destination, case):
    try:
        assert not destination.exists, "Object {} exists.".format(destination.path)
    except AssertionError as e:
        pytest.fail("The test case {} failed.\n{}".format(case, e.message))


def create_file_on_bucket(bucket_name, key, source):
    pipe_storage_cp(source, "cp://%s/%s" % (bucket_name, key))
    assert object_exists(bucket_name, key)
    return ObjectInfo(False).build(bucket_name, key)


def assert_local_files_count(folder_path, expected_files_count, test_case):
    command = ['ls "%s" | wc -l' % folder_path]
    ls_process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                  shell=True)
    stdout, stderr = ls_process.communicate()
    actual_files_count = int(str(stdout).strip())
    if stderr:
        pytest.fail("The test case {} failed.\n{}".format(test_case, stderr))
    try:
        assert actual_files_count == expected_files_count, \
            "Expected files count: %d, actual %d" % (expected_files_count, actual_files_count)
    except AssertionError as e:
        pytest.fail("The test case {} failed.\n{}".format(test_case, e.message))
