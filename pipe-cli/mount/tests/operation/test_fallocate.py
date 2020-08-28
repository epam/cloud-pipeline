import pytest

from utils import MB, execute, as_size, as_literal, assert_content
from pyio import write, write_with_gaps
from pyfs import truncate, fallocate


def test_fallocate_from_empty_to_size(size, local_file, mounted_file, source_path):
    truncate(local_file, '0')
    truncate(mounted_file, '0')
    fallocate(local_file, size)
    fallocate(mounted_file, size)
    assert_content(local_file, mounted_file)


def test_fallocate_from_size_to_half_size(size, local_file, mounted_file, source_path):
    fallocate(local_file, as_literal(as_size(size) / 2))
    fallocate(mounted_file, as_literal(as_size(size) / 2))
    assert_content(local_file, mounted_file)


def test_fallocate_from_half_size_to_size(size, local_file, mounted_file, source_path):
    truncate(local_file, as_literal(as_size(size) / 2))
    truncate(mounted_file, as_literal(as_size(size) / 2))
    fallocate(local_file, size)
    fallocate(mounted_file, size)
    assert_content(local_file, mounted_file)


def test_fallocate_from_size_to_size(size, local_file, mounted_file, source_path):
    fallocate(local_file, size)
    fallocate(mounted_file, size)
    assert_content(local_file, mounted_file)
