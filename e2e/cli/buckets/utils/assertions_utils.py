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
from buckets.utils.aws.utilities import *
from buckets.utils.file_utils import *


def assert_error_message_is_present(output, message):
    text = "".join(output)
    assert message in text, "Command output '{}' doesn't contain expected message: '{}'".format(text, message)


def assert_files_skipped(bucket_name, *files):
    for path in files:
        if bucket_name:
            assert s3_object_exists(bucket_name, path), "File '{}' should be skipped, but was deleted".format(path)
        else:
            assert os.path.exists(os.path.abspath(path)), "File '{}' should be skipped, but was deleted".format(path)


def assert_files_deleted(bucket_name, *files):
    for path in files:
        if bucket_name:
            assert not s3_object_exists(bucket_name, path), "File '{}' should be deleted, but was skipped".format(path)
        else:
            assert not os.path.exists(os.path.abspath(path)), "File '{}' should be deleted, but was skipped".format(path)


def create_test_files_on_bucket(source_file, bucket_name, *keys):
    for key in keys:
        pipe_storage_cp(source_file, "cp://{}/".format(bucket_name) + key, expected_status=0)
        assert s3_object_exists(bucket_name, key), "Test object {} does not exist on bucket {}.".format(key,
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
    pipe_storage_cp(source, "s3://%s/%s" % (bucket_name, key))
    assert s3_object_exists(bucket_name, key)
    return ObjectInfo(False).build(bucket_name, key)


class ObjectInfo(object):

    def __init__(self, is_local):
        self.is_local = is_local
        self.exists = None
        self.size = None
        self.last_modified = None
        self.path = None

    def build(self, *args):
        if self.is_local:
            return LocalFileInfo(self.is_local).build(args[0])
        else:
            return S3ObjectInfo(self.is_local).build(args[0], args[1])


class S3ObjectInfo(ObjectInfo):

    def __init__(self, is_local):
        super(S3ObjectInfo, self).__init__(is_local)

    def build(self, bucket_name, key):
        self.path = "cp://" + os.path.join(bucket_name, key)
        s3 = boto3.resource('s3')
        bucket = s3.Bucket(bucket_name)
        bucket_entry = list(bucket.objects.filter(Prefix=key))
        if len(bucket_entry) > 0 and bucket_entry[0].key == key:
            self.exists = True
        else:
            self.exists = False
            return self
        obj = s3.Object(bucket_name, key)
        self.size = obj.content_length
        self.last_modified = obj.last_modified.astimezone(get_localzone()).replace(tzinfo=None)
        return self


class LocalFileInfo(ObjectInfo):

    def __init__(self, is_local):
        super(LocalFileInfo, self).__init__(is_local)

    def build(self, path):
        self.path = os.path.abspath(path)
        self.exists = os.path.exists(self.path)
        if not self.exists:
            return self
        self.size = os.path.getsize(self.path)
        self.last_modified = file_last_modified_time(self.path)
        return self
