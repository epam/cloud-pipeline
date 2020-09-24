#!/usr/bin/env python

# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import argparse
import json
import os
import sys
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
    parser.add_argument('-d', '--debug', action='store_true', help='Enables stacktrace')
    parser.add_argument('-i', '--indent', type=int, help='Output json indent')
    args = parser.parse_args()

    try:
        listing, next_offset = get_listing(args.path, args.offset, args.size)
        print(json.dumps({'results': listing, 'nextPageMarker': next_offset}, indent=args.indent))
    except BaseException as e:
        if not args.debug:
            sys.stderr.write(str(e))
            exit(1)
        else:
            raise
