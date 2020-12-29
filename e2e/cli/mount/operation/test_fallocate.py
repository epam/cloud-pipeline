import os

from ..utils import as_size, as_literal, assert_content
from pyfs import truncate, fallocate, rm

files = []


def teardown_module():
    for file in files:
        if os.path.isfile(file):
            rm(file)


def test_fallocate_from_empty_to_size(size, local_file, mount_file, source_path):
    truncate(local_file, '0')
    truncate(mount_file, '0')
    fallocate(local_file, size)
    fallocate(mount_file, size)
    assert_content(local_file, mount_file)
    files.extend([local_file, mount_file])


def test_fallocate_from_size_to_half_size(size, local_file, mount_file, source_path):
    fallocate(local_file, as_literal(as_size(size) / 2))
    fallocate(mount_file, as_literal(as_size(size) / 2))
    assert_content(local_file, mount_file)


def test_fallocate_from_half_size_to_size(size, local_file, mount_file, source_path):
    truncate(local_file, as_literal(as_size(size) / 2))
    truncate(mount_file, as_literal(as_size(size) / 2))
    fallocate(local_file, size)
    fallocate(mount_file, size)
    assert_content(local_file, mount_file)


def test_fallocate_from_size_to_size(size, local_file, mount_file, source_path):
    fallocate(local_file, size)
    fallocate(mount_file, size)
    assert_content(local_file, mount_file)
