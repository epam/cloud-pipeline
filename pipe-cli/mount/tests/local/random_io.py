import os
import random


def random_reader(path, offset, capacity, distance, number=None, seed=42):
    random.seed(seed)
    size = os.path.getsize(path)
    current_offset = offset
    current_iteration = 0
    with open(path, 'r+') as f:
        while True:
            if current_offset > size - distance - capacity or number and current_iteration >= number:
                break
            f.seek(current_offset)
            yield f.read(capacity)
            current_offset += capacity + distance
            current_iteration += 1


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
