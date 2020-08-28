import os

import pytest

from utils import MB, execute, as_size, as_literal, assert_content
from pyio import write, write_with_gaps
from pyfs import truncate, fallocate, touch, rm

files = []


def teardown_module(module):
    for file in files:
        if os.path.isfile(file):
            rm(file)


def test_touch_non_existing_file(mount_path):
    file_path = os.path.join(mount_path, 'file')
    touch(file_path)
    rm(file_path)
    touch(file_path)
    assert os.path.isfile(file_path)
    assert os.path.getsize(file_path) == 0
    files.append(file_path)


def test_touch_existing_empty_file(mount_path):
    file_path = os.path.join(mount_path, 'file')
    touch(file_path)
    assert os.path.isfile(file_path)
    assert os.path.getsize(file_path) == 0
    files.append(file_path)


def test_touch_existing_non_empty_file(mount_path):
    file_path = os.path.join(mount_path, 'file')
    fallocate(file_path, '5')
    touch(file_path)
    assert os.path.isfile(file_path)
    assert os.path.getsize(file_path) == 5
    files.append(file_path)
