# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

from src.utilities.datastorage_operations import DataStorageOperations

SOURCE_PATH = '/data/source'


def _read_file_list(file_list_path):
    return list(DataStorageOperations._DataStorageOperations__get_file_to_copy(str(file_list_path), SOURCE_PATH))


def test_file_list_yields_text_paths_and_sizes(tmp_path):
    # <path>\t<size> lines, as the InputData and OutputData steps of a run write them (an integer size).
    # A float size is accepted as well.
    file_list = tmp_path / 'files.list'
    file_list.write_bytes(b'a.txt\t5\nsub/b.txt\t12.0\n')

    assert _read_file_list(file_list) == [
        ('File', os.path.join(SOURCE_PATH, 'a.txt'), 'a.txt', 5),
        ('File', os.path.join(SOURCE_PATH, 'sub/b.txt'), 'sub/b.txt', 12),
    ]


def test_file_list_reads_utf8_paths_with_crlf_line_ends(tmp_path):
    file_list = tmp_path / 'files.list'
    file_list.write_bytes('данные/файл.txt\t3\r\n'.encode('utf-8'))

    assert _read_file_list(file_list) == [
        ('File', os.path.join(SOURCE_PATH, 'данные/файл.txt'), 'данные/файл.txt', 3),
    ]
