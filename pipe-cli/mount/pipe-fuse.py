# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import errno
import logging
import os
import sys

from pipefuse.webdavfs import WebDavFS
from fuse import FUSE


def start(mountpoint, webdav, default_mode, mount_options=None):
    if mount_options is None:
        mount_options = {}
    try:
        os.makedirs(mountpoint)
    except OSError as e:
        if e.errno != errno.EEXIST:
            raise
    FUSE(WebDavFS(webdav, int(default_mode, 8)), mountpoint, nothreads=True, foreground=True, **mount_options)


def parse_mount_options(options_string):
    options = {}
    if not options_string:
        return options
    for option in options_string.split(","):
        option_string = option.strip()
        chunks = option_string.split("=")
        key = chunks[0]
        value = True if len(chunks) == 1 else chunks[1]
        options[key] = value
    return options


if __name__ == '__main__':
    logging.basicConfig(format='[%(levelname)s] %(asctime)s %(filename)s - %(message)s',
                        level=logging.ERROR)
    parser = argparse.ArgumentParser()
    parser.add_argument("-p", "--mountpoint", type=str, required=True, help="Mount folder")
    parser.add_argument("-w", "--webdav", type=str, required=True, help="Webdav link")
    parser.add_argument("-m", "--mode", type=str, required=False, default="775",
                        help="Default mode for webdav files")
    parser.add_argument("-o", "--options", type=str, required=False,
                        help="String with mount options supported by FUSE")
    args = parser.parse_args()
    try:
        start(args.mountpoint, args.webdav, default_mode=args.mode, mount_options=parse_mount_options(args.options))
    except BaseException as e:
        logging.error('Unhandled error: %s' % e.message)
        sys.exit(1)
