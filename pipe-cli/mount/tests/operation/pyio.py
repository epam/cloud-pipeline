import os
import random


def read(path, offset, amount):
    with open(path) as f:
        f.seek(offset)
        f.read(amount)


def write(path, offset, amount, seed=42):
    random.seed(seed)
    with open(path, 'r+') as f:
        f.seek(offset)
        f.write(bytearray(map(random.getrandbits, (8,) * amount)))


def read_with_gaps(path, offset, amount, gap, seed=42):
    random.seed(seed)
    size = os.path.getsize(path)
    current_offset = offset
    with open(path, 'r+') as f:
        while True:
            if current_offset > size - gap - amount:
                break
            f.seek(current_offset)
            yield f.read(amount)
            current_offset += amount + gap


def write_with_gaps(path, offset, amount, gap, seed=42):
    random.seed(seed)
    size = os.path.getsize(path)
    current_offset = offset
    with open(path, 'r+') as f:
        while True:
            if current_offset > size - gap - amount:
                break
            f.seek(current_offset)
            f.write(bytearray(map(random.getrandbits, (8,) * amount)))
            current_offset += amount + gap
