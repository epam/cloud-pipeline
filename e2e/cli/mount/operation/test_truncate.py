from ..utils import assert_content
from pyfs import truncate


def test_truncate_non_existing_file_to_size(size, local_file, mount_file):
    truncate(local_file, size)
    truncate(mount_file, size)
    assert_content(local_file, mount_file)


def test_truncate_empty_file_to_size(size, local_file, mount_file):
    truncate(local_file, 0)
    truncate(mount_file, 0)
    truncate(local_file, size)
    truncate(mount_file, size)
    assert_content(local_file, mount_file)


def test_truncate_file_to_its_size(size, local_file, mount_file):
    truncate(local_file, size)
    truncate(mount_file, size)
    assert_content(local_file, mount_file)


def test_truncate_file_to_half_its_size(size, local_file, mount_file):
    truncate(local_file, size / 2)
    truncate(mount_file, size / 2)
    assert_content(local_file, mount_file)


def test_truncate_file_to_double_its_size(size, local_file, mount_file):
    truncate(local_file, size)
    truncate(mount_file, size)
    assert_content(local_file, mount_file)
