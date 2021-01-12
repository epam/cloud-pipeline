import os
import random

seed = 42


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


def write(path, offset, amount):
    random.seed(seed)
    with open(path, 'r+') as f:
        f.seek(offset)
        f.write(bytearray(map(random.getrandbits, (8,) * amount)))


def write_regions(path, *regions):
    random.seed(seed)
    with open(path, 'r+') as f:
        for region in regions:
            f.seek(region['offset'])
            f.write(bytearray(map(random.getrandbits, (8,) * region['amount'])))
