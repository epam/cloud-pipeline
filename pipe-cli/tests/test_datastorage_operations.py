# -*- coding: utf-8 -*-
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

import io
import os

from src.utilities.datastorage_operations import DataStorageOperations

SOURCE_PATH = u'/data/source'
TEXT_TYPE = type(u'')


def _read_file_list(tmpdir, content):
    file_list_path = str(tmpdir.join('files.list'))
    with io.open(file_list_path, 'wb') as file_list:
        file_list.write(content)
    return list(DataStorageOperations._DataStorageOperations__get_file_to_copy(file_list_path, SOURCE_PATH))


def test_file_list_yields_text_paths_and_sizes(tmpdir):
    # <path>\t<size> lines, as the InputData and OutputData steps of a run write them (an integer size).
    # A float size, and a size over 4 GiB, are accepted as well.
    items = _read_file_list(tmpdir, b'a.txt\t5\nsub/b.txt\t12.0\nbig.bin\t5368709120\n')

    assert items == [
        ('File', os.path.join(SOURCE_PATH, u'a.txt'), u'a.txt', 5),
        ('File', os.path.join(SOURCE_PATH, u'sub/b.txt'), u'sub/b.txt', 12),
        ('File', os.path.join(SOURCE_PATH, u'big.bin'), u'big.bin', 5368709120),
    ]
    assert all(isinstance(item[1], TEXT_TYPE) and isinstance(item[2], TEXT_TYPE) for item in items)


def test_file_list_reads_utf8_paths_with_crlf_line_ends(tmpdir):
    items = _read_file_list(tmpdir, u'данные/файл.txt\t3\r\n'.encode('utf-8'))

    assert items == [
        ('File', os.path.join(SOURCE_PATH, u'данные/файл.txt'), u'данные/файл.txt', 3),
    ]


def test_file_list_drops_the_utf8_bom(tmpdir):
    # Windows PowerShell 5.1 and older Notepad save UTF-8 with a BOM at the start of the file.
    items = _read_file_list(tmpdir, b'\xef\xbb\xbfa.txt\t5\r\nb.txt\t3\r\n')

    assert items == [
        ('File', os.path.join(SOURCE_PATH, u'a.txt'), u'a.txt', 5),
        ('File', os.path.join(SOURCE_PATH, u'b.txt'), u'b.txt', 3),
    ]


def test_file_list_skips_blank_lines(tmpdir):
    items = _read_file_list(tmpdir, b'\na.txt\t5\n\n  \t \nb.txt\t3\n\n')

    assert items == [
        ('File', os.path.join(SOURCE_PATH, u'a.txt'), u'a.txt', 5),
        ('File', os.path.join(SOURCE_PATH, u'b.txt'), u'b.txt', 3),
    ]


def test_mvtodir_moves_a_storage_to_the_root_folder():
    from unittest.mock import patch
    from src.utilities import datastorage_operations

    with patch.object(datastorage_operations.Folder, 'load') as load, \
            patch.object(datastorage_operations.DataStorage, 'mvtodir') as mvtodir:
        DataStorageOperations.mvtodir('storage', '/')

    load.assert_not_called()
    mvtodir.assert_called_once_with('storage', None)


def test_mvtodir_moves_a_storage_to_a_folder_by_path():
    from types import SimpleNamespace
    from unittest.mock import patch
    from src.utilities import datastorage_operations

    with patch.object(datastorage_operations.Folder, 'load', return_value=SimpleNamespace(id=7)) as load, \
            patch.object(datastorage_operations.DataStorage, 'mvtodir') as mvtodir:
        DataStorageOperations.mvtodir('storage', 'parent/child')

    load.assert_called_once_with('parent/child')
    mvtodir.assert_called_once_with('storage', 7)
