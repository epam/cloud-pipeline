import pytest

from ..utils import execute, assert_content
from pyio import read, read_regions


def test_cp_from_mount_folder_to_local_folder(size, local_file, mount_file, source_path):
    execute('head -c %s %s > %s' % (size, source_path, local_file))
    execute('head -c %s %s > %s' % (size, source_path, mount_file))
    execute('cp %s %s' % (mount_file, (local_file + '.mount.tmp')))
    assert_content(local_file, local_file + '.mount.tmp')


def test_head_file(local_file, mount_file):
    assert execute('head -c 10 %s' % local_file) == execute('head -c 10 %s' % mount_file)


def test_tail_file(local_file, mount_file):
    assert execute('tail -c 10 %s' % local_file) == execute('tail -c 10 %s' % mount_file)


def test_read_from_position_bigger_than_file_length(size, local_file, mount_file):
    assert read(local_file, offset=size * 2, amount=10) == read(mount_file, offset=size * 2, amount=10)


def test_read_region_that_exceeds_file_length(size, local_file, mount_file):
    if size < 5:
        pytest.skip()
    assert read(local_file, offset=size - 5, amount=10) == read(mount_file, offset=size - 5, amount=10)


def test_read_two_non_sequential_regions(size, local_file, mount_file):
    if size < 30:
        pytest.skip()
    assert read_regions(local_file, {'offset': 4, 'amount': 10}, {'offset': 20, 'amount': 10}) \
           == read_regions(mount_file, {'offset': 4, 'amount': 10}, {'offset': 20, 'amount': 10})


def test_read_head_and_tail(size, local_file, mount_file):
    if size < 10:
        pytest.skip()
    assert read_regions(local_file, {'offset': 0, 'amount': 10}, {'offset': size - 10, 'amount': 10}) \
           == read_regions(mount_file, {'offset': 0, 'amount': 10}, {'offset': size - 10, 'amount': 10})


def test_read_tail_and_head(size, local_file, mount_file):
    if size < 10:
        pytest.skip()
    assert read_regions(local_file, {'offset': size - 10, 'amount': 10}, {'offset': 0, 'amount': 10}) \
           == read_regions(mount_file, {'offset': size - 10, 'amount': 10}, {'offset': 0, 'amount': 10})

