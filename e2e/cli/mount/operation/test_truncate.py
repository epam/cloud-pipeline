import pytest

from ..utils import assert_content
from pyfs import truncate


def test_truncate_non_existing_file_to_size(size, local_file, mount_file):
    """TC-PIPE-FUSE-37"""
    truncate(local_file, size)
    truncate(mount_file, size)
    assert_content(local_file, mount_file)


def test_truncate_empty_file_to_size(size, local_file, mount_file):
    """TC-PIPE-FUSE-38"""
    truncate(local_file, 0)
    truncate(mount_file, 0)
    truncate(local_file, size)
    truncate(mount_file, size)
    assert_content(local_file, mount_file)


def test_truncate_file_to_its_size(size, local_file, mount_file):
    """TC-PIPE-FUSE-40"""
    truncate(local_file, size)
    truncate(mount_file, size)
    assert_content(local_file, mount_file)


def test_truncate_file_to_half_its_size(size, local_file, mount_file):
    """TC-PIPE-FUSE-39"""
    if size < 2:
        pytest.skip()
    truncate(local_file, size / 2)
    truncate(mount_file, size / 2)
    assert_content(local_file, mount_file)


def test_truncate_file_to_double_its_size(size, local_file, mount_file):
    """TC-PIPE-FUSE-41"""
    if size < 2:
        pytest.skip()
    truncate(local_file, size)
    truncate(mount_file, size)
    assert_content(local_file, mount_file)
