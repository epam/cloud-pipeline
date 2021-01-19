import pytest

from ..utils import assert_content
from pyfs import head, tail, cp
from pyio import read, read_regions


def test_cp_from_mount_folder_to_local_folder(size, local_file, mount_file, source_path):
    """TC-PIPE-FUSE-42"""
    head(source_path, size=size, write_to=local_file)
    head(source_path, size=size, write_to=mount_file)
    cp(mount_file, local_file + '.mount.file.copy')
    assert_content(local_file, local_file + '.mount.file.copy')


def test_head_file(local_file, mount_file):
    """TC-PIPE-FUSE-43"""
    assert head(local_file) == head(mount_file)


def test_tail_file(local_file, mount_file):
    """TC-PIPE-FUSE-44"""
    assert tail(local_file) == tail(mount_file)


def test_read_from_position_bigger_than_file_length(size, local_file, mount_file):
    """TC-PIPE-FUSE-45"""
    assert read(local_file, offset=size * 2, amount=10) == read(mount_file, offset=size * 2, amount=10)


def test_read_region_that_exceeds_file_length(size, local_file, mount_file):
    """TC-PIPE-FUSE-46"""
    if size < 5:
        pytest.skip()
    assert read(local_file, offset=size - 5, amount=10) == read(mount_file, offset=size - 5, amount=10)


def test_read_two_non_sequential_regions(size, local_file, mount_file):
    """TC-PIPE-FUSE-47"""
    if size < 30:
        pytest.skip()
    assert read_regions(local_file, {'offset': 4, 'amount': 10}, {'offset': 20, 'amount': 10}) \
           == read_regions(mount_file, {'offset': 4, 'amount': 10}, {'offset': 20, 'amount': 10})


def test_read_head_and_tail(size, local_file, mount_file):
    """TC-PIPE-FUSE-48"""
    if size < 10:
        pytest.skip()
    assert read_regions(local_file, {'offset': 0, 'amount': 10}, {'offset': size - 10, 'amount': 10}) \
           == read_regions(mount_file, {'offset': 0, 'amount': 10}, {'offset': size - 10, 'amount': 10})


def test_read_tail_and_head(size, local_file, mount_file):
    """TC-PIPE-FUSE-49"""
    if size < 10:
        pytest.skip()
    assert read_regions(local_file, {'offset': size - 10, 'amount': 10}, {'offset': 0, 'amount': 10}) \
           == read_regions(mount_file, {'offset': size - 10, 'amount': 10}, {'offset': 0, 'amount': 10})

