import pytest
from utils import MB, execute, as_size, assert_content
from pyio import write, write_with_gaps

small_write_size = 5


def test_streaming_write(size, local_file, mounted_file, source_path):
    execute('head -c %s %s > %s' % (size, source_path, local_file))
    execute('head -c %s %s > %s' % (size, source_path, mounted_file))
    assert_content(local_file, mounted_file)


def test_cp_to_mount(size, local_file, mounted_file, source_path):
    execute('cp %s %s' % (local_file, mounted_file))
    assert_content(local_file, mounted_file)


def test_small_append_using_bash(size, local_file, mounted_file, source_path):
    execute('head -c %s %s >> %s' % (small_write_size, source_path, local_file))
    execute('head -c %s %s >> %s' % (small_write_size, source_path, mounted_file))
    assert_content(local_file, mounted_file)


def test_small_write_to_tail(size, local_file, mounted_file, source_path):
    actual_size = as_size(size)
    if actual_size > small_write_size:
        write(local_file, offset=actual_size - small_write_size, amount=small_write_size)
        write(mounted_file, offset=actual_size - small_write_size, amount=small_write_size)
        assert_content(local_file, mounted_file)
    else:
        pytest.skip('Too small file (%s) to perform random writing to the end' % size)


def test_small_write_to_head(size, local_file, mounted_file, source_path):
    write(local_file, offset=0, amount=small_write_size)
    write(mounted_file, offset=0, amount=small_write_size)
    assert_content(local_file, mounted_file)


def test_write_with_gaps(size, local_file, mounted_file, source_path):
    write_with_gaps(local_file, offset=1 * MB, amount=1 * MB, gap=1 * MB)
    write_with_gaps(mounted_file, offset=1 * MB, amount=1 * MB, gap=1 * MB)
    assert_content(local_file, mounted_file)
