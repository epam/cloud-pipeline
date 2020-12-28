import pytest

from ..utils import KB, MB, execute, as_size, as_literal, assert_content
from pyio import write, write_with_gaps, write_with_small_write_to_head_before_flush, write_with_overlapping
from pyfs import touch, truncate

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
    if actual_size <= small_write_size:
        pytest.skip('Too small file (%s) to perform small write to tail' % size)
    write(local_file, offset=actual_size - small_write_size, amount=small_write_size)
    write(mounted_file, offset=actual_size - small_write_size, amount=small_write_size)
    assert_content(local_file, mounted_file)


def test_small_write_to_head(size, local_file, mounted_file, source_path):
    actual_size = as_size(size)
    if actual_size <= small_write_size:
        pytest.skip('Too small file (%s) to perform small write to head' % size)
    write(local_file, offset=0, amount=small_write_size)
    write(mounted_file, offset=0, amount=small_write_size)
    assert_content(local_file, mounted_file)


def test_write_with_gaps(size, local_file, mounted_file, source_path):
    actual_size = as_size(size)
    if actual_size <= 1 * MB:
        pytest.skip('Too small file (%s) to perform write with gaps' % size)
    write_with_gaps(local_file, offset=1 * MB, amount=1 * MB, gap=1 * MB)
    write_with_gaps(mounted_file, offset=1 * MB, amount=1 * MB, gap=1 * MB)
    assert_content(local_file, mounted_file)


def test_write_with_small_write_to_head_before_flush(size, local_file, mounted_file, source_path):
    actual_size = as_size(size)
    if actual_size <= 1 * MB:
        pytest.skip('Too small file (%s) to perform write with small write to head before flush' % size)
    write_with_small_write_to_head_before_flush(local_file, offset=0, amount=1 * MB, small_amount=small_write_size)
    write_with_small_write_to_head_before_flush(mounted_file, offset=0, amount=1 * MB, small_amount=small_write_size)
    assert_content(local_file, mounted_file)


def test_write_with_overlapping(size, local_file, mounted_file, source_path):
    actual_size = as_size(size)
    if actual_size <= 1 * MB:
        pytest.skip('Too small file (%s) to perform write with overlapping' % size)
    write_with_overlapping(local_file, offset=0, amount=1 * MB, overlap=small_write_size)
    write_with_overlapping(mounted_file, offset=0, amount=1 * MB, overlap=small_write_size)
    assert_content(local_file, mounted_file)

offset_amount_test_data = [
    [1 * MB, 1 * MB],
    [11 * MB, 1 * MB],
    [11 * MB, 1050 * KB],
    [21 * MB, 1050 * KB],
    [0, 1 * MB],
    [0, 1050 * KB]
]

@pytest.mark.parametrize('offset, amount', offset_amount_test_data, ids=as_literal)
def test_write_with_gaps_to_empty_file(size, local_file, mounted_file, source_path, offset, amount):
    local_file_copy = local_file + '.copy'
    mounted_file_copy = mounted_file + '.copy'
    actual_size = as_size(size)
    if actual_size <= 1 * MB:
        pytest.skip('Too small file (%s) to perform write with gaps' % size)
    touch(local_file_copy)
    touch(mounted_file_copy)
    truncate(local_file_copy, size='0')
    truncate(mounted_file_copy, size='0')
    write_with_gaps(local_file_copy, offset=offset, amount=amount, size=actual_size, gap=1 * MB)
    write_with_gaps(mounted_file_copy, offset=offset, amount=amount, size=actual_size, gap=1 * MB)
    assert_content(local_file_copy, mounted_file_copy)


@pytest.mark.parametrize('offset, amount', offset_amount_test_data, ids=as_literal)
def test_write_with_gaps_more_than_length(size, local_file, mounted_file, source_path, offset, amount):
    local_file_copy = local_file + '.copy'
    mounted_file_copy = mounted_file + '.copy'
    actual_size = as_size(size)
    if actual_size <= 1 * MB:
        pytest.skip('Too small file (%s) to perform write with gaps' % size)
    touch(local_file_copy)
    touch(mounted_file_copy)
    execute('head -c %s %s > %s' % (as_literal(int(actual_size / 2)), source_path, local_file_copy))
    execute('head -c %s %s > %s' % (as_literal(int(actual_size / 2)), source_path, mounted_file_copy))
    write_with_gaps(local_file_copy, offset=offset, amount=amount, size=actual_size, gap=1 * MB)
    write_with_gaps(mounted_file_copy, offset=offset, amount=amount, size=actual_size, gap=1 * MB)
    assert_content(local_file_copy, mounted_file_copy)


@pytest.mark.parametrize('offset, amount', offset_amount_test_data, ids=as_literal)
def test_write_with_gaps_less_than_length(size, local_file, mounted_file, source_path, offset, amount):
    local_file_copy = local_file + '.copy'
    mounted_file_copy = mounted_file + '.copy'
    actual_size = as_size(size)
    if actual_size <= 1 * MB:
        pytest.skip('Too small file (%s) to perform write with gaps' % size)
    touch(local_file_copy)
    touch(mounted_file_copy)
    execute('head -c %s %s > %s' % (size, source_path, local_file_copy))
    execute('head -c %s %s > %s' % (size, source_path, mounted_file_copy))
    write_with_gaps(local_file_copy, offset=offset, amount=amount, size=int(actual_size / 2), gap=1 * MB)
    write_with_gaps(mounted_file_copy, offset=offset, amount=amount, size=int(actual_size / 2), gap=1 * MB)
    assert_content(local_file_copy, mounted_file_copy)
