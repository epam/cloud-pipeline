import os

import pytest

from ..utils import assert_content
from pyfs import head, cp
from pyio import write, write_regions


def test_cp_file_from_local_folder_to_mount_folder(size, local_file, mount_file, source_path):
    """TC-PIPE-FUSE-50"""
    head(source_path, size=size, write_to=local_file)
    cp(local_file, mount_file)
    assert_content(local_file, mount_file)


def test_append_to_file_end(local_file, mount_file, source_path):
    """TC-PIPE-FUSE-51"""
    head(source_path, append_to=local_file)
    head(source_path, append_to=mount_file)
    assert_content(local_file, mount_file)


def test_override_file_tail(size, local_file, mount_file):
    """TC-PIPE-FUSE-52"""
    if size < 10:
        pytest.skip()
    actual_size = os.path.getsize(local_file)
    write(local_file, offset=actual_size - 10, amount=10)
    write(mount_file, offset=actual_size - 10, amount=10)
    assert_content(local_file, mount_file)


def test_override_file_head(size, local_file, mount_file):
    """TC-PIPE-FUSE-53"""
    if size < 10:
        pytest.skip()
    write(local_file, offset=0, amount=10)
    write(mount_file, offset=0, amount=10)
    assert_content(local_file, mount_file)


def test_write_to_position_that_is_bigger_than_file_length(local_file, mount_file):
    """TC-PIPE-FUSE-54"""
    actual_size = os.path.getsize(local_file)
    write(local_file, offset=actual_size + 10, amount=10)
    write(mount_file, offset=actual_size + 10, amount=10)
    assert_content(local_file, mount_file)


def test_write_region_that_exceeds_file_length(size, local_file, mount_file):
    """TC-PIPE-FUSE-55"""
    if size < 5:
        pytest.skip()
    actual_size = os.path.getsize(local_file)
    write(local_file, offset=actual_size - 5, amount=10)
    write(mount_file, offset=actual_size - 5, amount=10)
    assert_content(local_file, mount_file)


def test_write_region_in_first_chunk(size, local_file, mount_file):
    """TC-PIPE-FUSE-56"""
    if size < 20:
        pytest.skip()
    write(local_file, offset=10, amount=10)
    write(mount_file, offset=10, amount=10)
    assert_content(local_file, mount_file)


def test_write_region_in_single_chunk(size, chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-57"""
    if size < chunk_size + 20:
        pytest.skip()
    write(local_file, offset=chunk_size + 10, amount=10)
    write(mount_file, offset=chunk_size + 10, amount=10)
    assert_content(local_file, mount_file)


def test_write_region_matching_single_chunk(size, chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-58"""
    if size < chunk_size:
        pytest.skip()
    write(local_file, offset=0, amount=chunk_size)
    write(mount_file, offset=0, amount=chunk_size)
    assert_content(local_file, mount_file)


def test_write_region_between_two_chunks(size, chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-59"""
    if size < chunk_size + 5:
        pytest.skip()
    write(local_file, offset=chunk_size - 5, amount=10)
    write(mount_file, offset=chunk_size - 5, amount=10)
    assert_content(local_file, mount_file)


def test_write_two_regions_in_single_chunk(size, chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-60"""
    if size < chunk_size + 110:
        pytest.skip()
    write_regions(local_file, {'offset': chunk_size + 10, 'amount': 10}, {'offset': chunk_size + 100, 'amount': 10})
    write_regions(mount_file, {'offset': chunk_size + 10, 'amount': 10}, {'offset': chunk_size + 100, 'amount': 10})
    assert_content(local_file, mount_file)


def test_write_two_regions_in_two_adjacent_chunks(size, chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-61"""
    if size < chunk_size + 20:
        pytest.skip()
    write_regions(local_file, {'offset': 10, 'amount': 10}, {'offset': chunk_size + 10, 'amount': 10})
    write_regions(mount_file, {'offset': 10, 'amount': 10}, {'offset': chunk_size + 10, 'amount': 10})
    assert_content(local_file, mount_file)


def test_write_two_regions_in_two_non_adjacent_chunks(size, chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-62"""
    if size < chunk_size * 2 + 20:
        pytest.skip()
    write_regions(local_file, {'offset': 10, 'amount': 10}, {'offset': chunk_size * 2 + 10, 'amount': 10})
    write_regions(mount_file, {'offset': 10, 'amount': 10}, {'offset': chunk_size * 2 + 10, 'amount': 10})
    assert_content(local_file, mount_file)


def test_write_two_regions_between_three_chunks(size, chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-63"""
    if size < chunk_size * 2 + 5:
        pytest.skip()
    write_regions(local_file, {'offset': chunk_size - 5, 'amount': 10}, {'offset': chunk_size * 2 - 5, 'amount': 10})
    write_regions(mount_file, {'offset': chunk_size - 5, 'amount': 10}, {'offset': chunk_size * 2 - 5, 'amount': 10})
    assert_content(local_file, mount_file)


def test_write_two_regions_between_four_chunks(size, chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-64"""
    if size < chunk_size * 3 + 5:
        pytest.skip()
    write_regions(local_file, {'offset': chunk_size - 5, 'amount': 10}, {'offset': chunk_size * 3 - 5, 'amount': 10})
    write_regions(mount_file, {'offset': chunk_size - 5, 'amount': 10}, {'offset': chunk_size * 3 - 5, 'amount': 10})
    assert_content(local_file, mount_file)


def test_write_two_regions_with_one_of_them_exceeding_file_length(size, chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-65"""
    if size < 5:
        pytest.skip()
    actual_size = os.path.getsize(local_file)
    write_regions(local_file, {'offset': 10, 'amount': 10}, {'offset': actual_size - 5, 'amount': 10})
    write_regions(mount_file, {'offset': 10, 'amount': 10}, {'offset': actual_size - 5, 'amount': 10})
    assert_content(local_file, mount_file)


def test_write_two_regions_with_one_of_them_starting_from_position_that_is_bigger_than_file_length(size, chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-66"""
    if size < 5:
        pytest.skip()
    actual_size = os.path.getsize(local_file)
    write_regions(local_file, {'offset': 10, 'amount': 10}, {'offset': actual_size + 5, 'amount': 10})
    write_regions(mount_file, {'offset': 10, 'amount': 10}, {'offset': actual_size + 5, 'amount': 10})
    assert_content(local_file, mount_file)


def test_write_two_regions_starting_from_position_that_is_bigger_than_file_length(chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-67"""
    actual_size = os.path.getsize(local_file)
    write_regions(local_file, {'offset': actual_size + 5, 'amount': 10}, {'offset': actual_size + 20, 'amount': 10})
    write_regions(mount_file, {'offset': actual_size + 5, 'amount': 10}, {'offset': actual_size + 20, 'amount': 10})
    assert_content(local_file, mount_file)


def test_write_two_overlapping_regions(size, chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-68"""
    if size < 25:
        pytest.skip()
    write_regions(local_file, {'offset': 10, 'amount': 10}, {'offset': 15, 'amount': 10})
    write_regions(mount_file, {'offset': 10, 'amount': 10}, {'offset': 15, 'amount': 10})
    assert_content(local_file, mount_file)


def test_write_region_to_an_already_written_chunk(size, chunk_size, local_file, mount_file):
    """TC-PIPE-FUSE-69"""
    if size < chunk_size + 10:
        pytest.skip()
    write_regions(local_file, {'offset': 0, 'amount': chunk_size}, {'offset': 10, 'amount': chunk_size})
    write_regions(mount_file, {'offset': 0, 'amount': chunk_size}, {'offset': 10, 'amount': chunk_size})
    assert_content(local_file, mount_file)
