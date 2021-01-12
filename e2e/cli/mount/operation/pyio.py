import os
import random


def read(path, offset, amount):
    with open(path) as f:
        f.seek(offset)
        return f.read(amount)


def read_regions(path, *regions):
    return list(iterate_regions(path, *regions))


def iterate_regions(path, *regions):
    with open(path) as f:
        for region in regions:
            f.seek(region['offset'])
            yield f.read(region['amount'])


def write(path, offset, amount, seed=42):
    random.seed(seed)
    with open(path, 'r+') as f:
        f.seek(offset)
        f.write(bytearray(map(random.getrandbits, (8,) * amount)))


def write_with_gaps(path, offset, amount, gap, size=None, seed=42):
    random.seed(seed)
    size = size or os.path.getsize(path)
    current_offset = offset
    with open(path, 'r+') as f:
        while True:
            if current_offset > size - gap - amount:
                break
            f.seek(current_offset)
            f.write(bytearray(map(random.getrandbits, (8,) * amount)))
            current_offset += amount + gap


def write_with_small_write_to_head_before_flush(path, offset, amount, small_amount, seed=42):
    random.seed(seed)
    size = os.path.getsize(path)
    current_offset = offset
    with open(path, 'r+') as f:
        while True:
            if current_offset > size - amount:
                break
            f.seek(current_offset)
            f.write(bytearray(map(random.getrandbits, (8,) * amount)))
            current_offset += amount
        f.seek(0)
        f.write(bytearray(map(random.getrandbits, (8,) * small_amount)))


def write_with_overlapping(path, offset, amount, overlap, seed=42):
    random.seed(seed)
    size = os.path.getsize(path)
    current_offset = offset
    with open(path, 'r+') as f:
        while True:
            if current_offset > size - amount:
                break
            f.seek(max(0, current_offset - overlap))
            f.write(bytearray(map(random.getrandbits, (8,) * amount)))
            current_offset += amount - overlap
