import os
import random

from ..utils import mkdir

seed = 42


def read(path, offset=0, amount=0):
    with open(path) as f:
        f.seek(offset)
        return f.read(amount)


def read_regions(path, *regions):
    return list(iterate_regions(path, *regions))


def iterate_regions(path, *regions):
    with open(path) as f:
        for region in regions:
            f.seek(region.get('offset', 0))
            yield f.read(region.get('amount', 0))


def write(path, offset=0, amount=0, data=None):
    with open(path, 'w+') as f:
        f.seek(offset)
        f.write(data if data else get_random_bytes(amount))


def write_regions(path, *regions):
    with open(path, 'w+') as f:
        for region in regions:
            f.seek(region.get('offset', 0))
            f.write(region.get('data', get_random_bytes(region.get('amount', 0))))


def get_random_bytes(number_of_bytes):
    random.seed(seed)
    return bytearray(map(random.getrandbits, (8,) * number_of_bytes)) if number_of_bytes else bytearray()


def read_dirs(path):
    dirs = {}
    for item in os.listdir(path):
        item_path = os.path.join(path, item)
        if os.path.isfile(item_path):
            dirs[item] = read(item_path, amount=1000) if os.path.getsize(item_path) else ''
        if os.path.isdir(item_path):
            dirs[item] = read_dirs(item_path)
    return dirs


def write_dirs(path, dirs):
    for k, v in dirs.items():
        curr_path = os.path.join(path, k)
        if isinstance(v, str):
            write(curr_path, data=v)
        else:
            mkdir(curr_path)
            write_dirs(curr_path, v)
