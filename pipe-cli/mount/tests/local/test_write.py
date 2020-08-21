from utils import MB, execute, as_size, assert_content
from random_io import random_write

small_write_size = 5


def test_sequential_writing(size, local_file, mounted_file, source_path):
    execute('head -c %s %s > %s' % (size, source_path, local_file))
    execute('head -c %s %s > %s' % (size, source_path, mounted_file))
    assert_content(local_file, mounted_file)


def test_cp_to_mounted_path(size, local_file, mounted_file, source_path):
    execute('cp %s %s' % (local_file, mounted_file))
    assert_content(local_file, mounted_file)


def test_small_append(size, local_file, mounted_file, source_path):
    execute('head -c %s %s >> %s' % (small_write_size, source_path, local_file))
    execute('head -c %s %s >> %s' % (small_write_size, source_path, mounted_file))
    assert_content(local_file, mounted_file)


def test_ordered_random_writing(size, local_file, mounted_file, source_path):
    random_write(local_file, offset=1 * MB, capacity=1 * MB, distance=1 * MB)
    random_write(mounted_file, offset=1 * MB, capacity=1 * MB, distance=1 * MB)
    assert_content(local_file, mounted_file)


def test_small_overwrite_head(size, local_file, mounted_file, source_path):
    random_write(local_file, offset=0, capacity=small_write_size, distance=0, number=1)
    random_write(mounted_file, offset=0, capacity=small_write_size, distance=0, number=1)
    assert_content(local_file, mounted_file)


def test_small_overwrite_tail(size, local_file, mounted_file, source_path):
    actual_size = as_size(size)
    if actual_size > small_write_size:
        random_write(local_file, offset=actual_size - small_write_size, 
                     capacity=small_write_size, distance=0, number=1)
        random_write(mounted_file, offset=actual_size - small_write_size, 
                     capacity=small_write_size, distance=0, number=1)
    assert_content(local_file, mounted_file)
