import pytest

from ..utils import assert_content
from pyfs import truncate, fallocate

files = []


def test_fallocate_non_existing_file_to_size(size, local_file, mount_file):
    """TC-PIPE-FUSE-32"""
    fallocate(local_file, size)
    fallocate(mount_file, size)
    assert_content(local_file, mount_file)


def test_fallocate_empty_file_to_size(size, local_file, mount_file):
    """TC-PIPE-FUSE-33"""
    truncate(local_file, 0)
    truncate(mount_file, 0)
    fallocate(local_file, size)
    fallocate(mount_file, size)
    assert_content(local_file, mount_file)


def test_fallocate_file_to_its_size(size, local_file, mount_file):
    """TC-PIPE-FUSE-35"""
    fallocate(local_file, size)
    fallocate(mount_file, size)
    assert_content(local_file, mount_file)


def test_fallocate_file_to_half_size(size, local_file, mount_file):
    """TC-PIPE-FUSE-34"""
    if size < 2:
        pytest.skip()
    fallocate(local_file, size / 2)
    fallocate(mount_file, size / 2)
    assert_content(local_file, mount_file)


def test_fallocate_file_to_double_its_size(size, local_file, mount_file):
    """TC-PIPE-FUSE-36"""
    if size < 2:
        pytest.skip()
    fallocate(local_file, size)
    fallocate(mount_file, size)
    assert_content(local_file, mount_file)
