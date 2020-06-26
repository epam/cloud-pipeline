import os
import random

from pytest import fail

from utils import MB, execute

small_write_size = 5


def test_sequential_writing(size, local_file, mounted_file, source_path):
    execute('head -c %s %s > %s' % (size, source_path, local_file))
    execute('head -c %s %s > %s' % (size, source_path, mounted_file))
    assert_content(local_file, mounted_file)


def test_cp_to_mounted_path(size, local_file, mounted_file, source_path):
    execute('cp %s %s' % (local_file, mounted_file))
    assert_content(local_file, mounted_file)


def test_small_append_to_end(size, local_file, mounted_file, source_path):
    execute('head -c %s %s >> %s' % (small_write_size, source_path, local_file))
    execute('head -c %s %s >> %s' % (small_write_size, source_path, mounted_file))
    assert_content(local_file, mounted_file)


def test_ordered_random_writing(size, local_file, mounted_file, source_path):
    random_write(local_file, offset=1 * MB, capacity=1 * MB, distance=1 * MB)
    random_write(mounted_file, offset=1 * MB, capacity=1 * MB, distance=1 * MB)
    assert_content(local_file, mounted_file)


def test_small_overwrite_in_beginning(size, local_file, mounted_file, source_path):
    random_write(local_file, offset=0, capacity=small_write_size, distance=0, number=1)
    random_write(mounted_file, offset=0, capacity=small_write_size, distance=0, number=1)
    assert_content(local_file, mounted_file)


def random_write(path, offset, capacity, distance, number=None, seed=42):
    random.seed(seed)
    size = os.path.getsize(path)
    current_offset = offset
    current_iteration = 0
    with open(path, 'r+') as f:
        while True:
            if current_offset > size - distance - capacity or number and current_iteration >= number:
                break
            f.seek(current_offset)
            f.write(bytearray(map(random.getrandbits, (8,) * capacity)))
            current_offset += capacity + distance
            current_iteration += 1


def assert_content(local_file, mounted_file):
    local_sha = execute('shasum %s | awk \'{ print $1 }\'' % local_file)
    mounted_sha = execute('shasum %s | awk \'{ print $1 }\'' % mounted_file)
    if local_sha != mounted_sha:
        fail('Local and mounted file shas do not matches: %s %s' % (local_sha, mounted_sha))
