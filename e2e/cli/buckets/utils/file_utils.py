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

import os
import shutil
import time
import datetime
import logging


class TestFiles(object):
    TEST_FILE1 = "test_file.txt"
    TEST_FILE_WITH_OTHER_EXTENSION = "test_file.json"
    TEST_FILE2 = "test_file2.txt"
    TEST_FOLDER = "test_folder/"
    TEST_FOLDER_FOR_OUTPUT = "test_folder_outputs/"
    TEST_FOLDER2 = "other/"
    TEST_FILE_IN_FOLDER1 = TEST_FOLDER + "test_file2.txt"
    TEST_FILE_IN_FOLDER2 = TEST_FOLDER + "test_file3.log"
    DEFAULT_CONTENT = "HelloWorld"
    COPY_CONTENT = "CopyHelloWorld"
    NOT_EXISTS_FILE = "file_does_not_exists.txt"
    NOT_EXISTS_FOLDER = "folder_does_not_exists/"


def create_test_file(path, content):
    create_test_folder(os.path.dirname(path))
    with open(path, 'w') as f:
        f.write(content)


def create_default_test_folder(resources_root):
    full_root_path = os.path.abspath(resources_root)
    create_test_folder(full_root_path)
    relative_path1 = os.path.join(full_root_path, TestFiles.TEST_FILE1)
    relative_path2 = os.path.join(full_root_path, TestFiles.TEST_FILE_IN_FOLDER1)
    relative_path3 = os.path.join(full_root_path, TestFiles.TEST_FILE_IN_FOLDER2)
    return create_test_files(TestFiles.DEFAULT_CONTENT, relative_path1, relative_path2, relative_path3)


def create_test_files(content, *args):
    for path in args:
        create_test_file(path, content)


def clean_test_data(path):
    try:
        if not os.path.exists(path):
            return
        if os.path.isfile(path):
            os.remove(path)
        else:
            shutil.rmtree(path)
    except Exception as e:
        logging.error('Filed to delete data from %s' % path, e)


def create_test_folder(path):
    if not os.path.exists(path):
        os.makedirs(path)


def file_last_modified_time(path):
    return datetime.datetime.strptime(time.ctime(os.path.getmtime(path)), '%a %b %d %H:%M:%S %Y')
