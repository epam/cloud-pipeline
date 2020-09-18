#!/usr/bin/env python

import argparse
import json
import os
from collections import namedtuple
from datetime import datetime

ListingItem = namedtuple('ListingItem', 'path, permission, type, name, size, changed')


def Folder(**kwargs):
    return ListingItem(type='Folder', size=None, changed=None, **kwargs)._asdict()


def File(**kwargs):
    return ListingItem(type='File', **kwargs)._asdict()


def get_listing(path, offset, size):
    listing = []
    next_offset = None
    if not os.path.exists(path):
        raise RuntimeError('Required folder or file %s not found' % path)
    if not os.access(path, os.R_OK):
        raise RuntimeError('No "READ" permission for required folder or file %s' % path)
    if os.path.islink(path):
        pass
    elif os.path.isfile(path):
        listing.append(File(path=os.path.basename(path), permission=get_permissions(path), name=os.path.basename(path),
                            size=os.path.getsize(path), changed=get_changed(path)))
    elif os.path.isdir(path):
        # todo: Both os.walk and os.listdir loads the entire directory in memory.
        #  Therefore probably it is better to use scandir which is hopefully lazy.
        #  https://github.com/benhoyt/scandir
        items = list(filter(lambda path: not os.path.islink(path), sorted(os.listdir(path))))
        for item in items[offset - 1:offset - 1 + size]:
            item_path = os.path.join(path, item)
            if os.path.isfile(item_path):
                listing.append(File(path=os.path.basename(item_path), permission=get_permissions(item_path),
                                    name=os.path.basename(item_path), size=os.path.getsize(item_path),
                                    changed=get_changed(path)))
            elif os.path.isdir(item_path):
                listing.append(Folder(path=os.path.basename(item_path), permission=get_permissions(item_path),
                                      name=os.path.basename(item_path)))
        if items[offset - 1 + size:]:
            next_offset = offset + size
    return listing, next_offset


def get_permissions(path):
    return (1 if os.access(path, os.R_OK) else 0) \
           | (1 << 1 if os.access(path, os.W_OK) else 0) \
           | (1 << 2 if os.access(path, os.X_OK) else 0)


def get_changed(path):
    return datetime.fromtimestamp(os.path.getmtime(path)).strftime('%Y-%m-%dT%H:%M:%S.%fZ')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('-p', '--path', type=str, required=True, help='Listing path')
    parser.add_argument('-o', '--offset', type=int, required=True, help='Listing offset')
    parser.add_argument('-s', '--size', type=int, required=True, help='Listing size')
    args = parser.parse_args()

    listing, next_offset = get_listing(args.path, args.offset, args.size)

    print(json.dumps({'results': listing, 'nextPageMarker': next_offset}, indent=4))
