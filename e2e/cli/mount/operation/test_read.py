import os

from ..utils import MB, execute, assert_content
from pyio import read, read_with_gaps

small_read_size = 5


def test_streaming_write(size, local_file, mounted_file, source_path):
    execute('head -c %s %s > %s' % (size, source_path, local_file))
    execute('head -c %s %s > %s' % (size, source_path, mounted_file))
    assert_content(local_file, mounted_file)


def test_cp_from_mount(size, local_file, mounted_file, source_path):
    local_file_tmp = local_file + '.copy'
    execute('cp %s %s' % (mounted_file, local_file_tmp))
    assert_content(local_file, local_file_tmp)


def test_small_read_using_head(size, local_file, mounted_file, source_path):
    local_file_copy = local_file + '.copy'
    mounted_file_copy = mounted_file + '.copy'
    execute('head -c %s %s > %s' % (small_read_size, local_file, local_file_copy))
    execute('head -c %s %s > %s' % (small_read_size, mounted_file, mounted_file_copy))
    assert_content(local_file_copy, mounted_file_copy)


def test_small_read_using_tail(size, local_file, mounted_file, source_path):
    local_file_copy = local_file + '.copy'
    mounted_file_copy = mounted_file + '.copy'
    execute('tail -c %s %s > %s' % (small_read_size, local_file, local_file_copy))
    execute('tail -c %s %s > %s' % (small_read_size, mounted_file, mounted_file_copy))
    assert_content(local_file_copy, mounted_file_copy)


def test_read_beyond_file_size(size, local_file, mounted_file, source_path):
    local_read = read(local_file, offset=os.path.getsize(local_file) * 2, amount=small_read_size)
    mounted_read = read(mounted_file, offset=os.path.getsize(mounted_file) * 2, amount=small_read_size)
    assert local_read == mounted_read


def test_read_which_exceeds_file_size(size, local_file, mounted_file, source_path):
    local_read = read(local_file, offset=os.path.getsize(local_file) - small_read_size, amount=small_read_size * 2)
    mounted_read = read(mounted_file, offset=os.path.getsize(mounted_file) - small_read_size, amount=small_read_size * 2)
    assert local_read == mounted_read


def test_read_with_gaps(size, local_file, mounted_file, source_path):
    local_read = read_with_gaps(local_file, offset=1 * MB, amount=1 * MB, gap=1 * MB)
    mounted_read = read_with_gaps(mounted_file, offset=1 * MB, amount=1 * MB, gap=1 * MB)
    assert all(local_bytes == mounted_bytes for local_bytes, mounted_bytes in zip(local_read, mounted_read))
