from utils import MB, execute, assert_content
from random_io import random_reader

small_read_size = 5


def test_sequential_writing(size, local_file, mounted_file, source_path):
    execute('head -c %s %s > %s' % (size, source_path, local_file))
    execute('head -c %s %s > %s' % (size, source_path, mounted_file))
    assert_content(local_file, mounted_file)


def test_cp_from_mounted_path(size, local_file, mounted_file, source_path):
    local_file_tmp = local_file + '.copy'
    execute('cp %s %s' % (mounted_file, local_file_tmp))
    assert_content(local_file, local_file_tmp)


def test_small_read_head(size, local_file, mounted_file, source_path):
    local_file_copy = local_file + '.copy'
    mounted_file_copy = mounted_file + '.copy'
    execute('head -c %s %s > %s' % (small_read_size, local_file, local_file_copy))
    execute('head -c %s %s > %s' % (small_read_size, mounted_file, mounted_file_copy))
    assert_content(local_file_copy, mounted_file_copy)


def test_small_read_tail(size, local_file, mounted_file, source_path):
    local_file_copy = local_file + '.copy'
    mounted_file_copy = mounted_file + '.copy'
    execute('tail -c %s %s > %s' % (small_read_size, local_file, local_file_copy))
    execute('tail -c %s %s > %s' % (small_read_size, mounted_file, mounted_file_copy))
    assert_content(local_file_copy, mounted_file_copy)


def test_ordered_random_read(size, local_file, mounted_file, source_path):
    local_file_reader = random_reader(local_file, offset=1 * MB, capacity=1 * MB, distance=1 * MB)
    mounted_file_reader = random_reader(mounted_file, offset=1 * MB, capacity=1 * MB, distance=1 * MB)
    assert all(local_bytes == mounted_bytes for local_bytes, mounted_bytes in zip(local_file_reader, mounted_file_reader))
