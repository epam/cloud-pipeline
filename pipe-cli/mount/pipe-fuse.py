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

from cachetools import TTLCache

source_path = sys._MEIPASS if getattr(sys, 'frozen', False) else os.path.dirname(__file__)
libfuse_path = os.path.abspath(os.path.join(source_path, 'libfuse/libfuse.so.2'))
os.environ["FUSE_LIBRARY_PATH"] = libfuse_path

from pipefuse.fuseutils import MB, GB
from pipefuse.cache import CachingFileSystemClient
from pipefuse.buff import BufferedFileSystemClient
from pipefuse.api import CloudPipelineClient
from pipefuse.webdav import CPWebDavClient
from pipefuse.s3 import S3Client
from pipefuse.pipefs import PipeFS
from fuse import FUSE

_allowed_logging_level_names = logging._levelNames
_allowed_logging_levels = filter(lambda name: isinstance(name, str), _allowed_logging_level_names.keys())
_allowed_logging_levels_string = ', '.join(_allowed_logging_levels)
_default_logging_level = logging.ERROR


def start(mountpoint, webdav, bucket, buffer_size, chunk_size, cache_ttl, cache_size, default_mode, mount_options=None,
          threads=False, monitoring_delay=600):
    if mount_options is None:
        mount_options = {}
    try:
        os.makedirs(mountpoint)
    except OSError as e:
        if e.errno != errno.EEXIST:
            raise

    api = os.environ.get('API', '')
    bearer = os.environ.get('API_TOKEN', '')
    chunk_size = os.environ.get('CP_PIPE_FUSE_CHUNK_SIZE', chunk_size)
    if not bearer:
        raise RuntimeError("Cloud Pipeline API_TOKEN should be specified.")
    if webdav:
        client = CPWebDavClient(webdav_url=webdav, bearer=bearer)
    else:
        if not api:
            raise RuntimeError("Cloud Pipeline API should be specified.")
        pipe = CloudPipelineClient(api=api, token=bearer)
        client = S3Client(bucket, pipe=pipe, chunk_size=chunk_size)
    if cache_ttl > 0 and cache_size > 0:
        cache = TTLCache(maxsize=cache_size, ttl=cache_ttl)
        client = CachingFileSystemClient(client, cache)
    else:
        logging.info('Caching is disabled.')
    if buffer_size > 0:
        client = BufferedFileSystemClient(client, capacity=buffer_size)
    else:
        logging.info('Buffering is disabled.')
    fs = PipeFS(client=client, mode=int(default_mode, 8))
    FUSE(fs, mountpoint, nothreads=True, foreground=True, ro=client.is_read_only(), **mount_options)


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
    parser = argparse.ArgumentParser()
    parser.add_argument("-p", "--mountpoint", type=str, required=True, help="Mount folder")
    parser.add_argument("-w", "--webdav", type=str, required=False, help="Webdav link")
    parser.add_argument("-b", "--bucket", type=str, required=False, help="Bucket name")
    parser.add_argument("-f", "--buffer-size", type=int, required=False, default=512 * MB,
                        help="Writing buffer size for a single file")
    parser.add_argument("-c", "--chunk-size", type=int, required=False, default=10 * MB,
                        help="Multipart upload chunk size. Can be also specified via "
                             "CP_PIPE_FUSE_CHUNK_SIZE environment variable.")
    parser.add_argument("-t", "--cache-ttl", type=int, required=False, default=60,
                        help="Listing cache time to live, seconds")
    parser.add_argument("-s", "--cache-size", type=int, required=False, default=100,
                        help="Number of simultaneous listing caches")
    parser.add_argument("-m", "--mode", type=str, required=False, default="700",
                        help="Default mode for files")
    parser.add_argument("-o", "--options", type=str, required=False,
                        help="String with mount options supported by FUSE")
    parser.add_argument("-l", "--logging-level", type=str, required=False, default=_default_logging_level,
                        help="Logging level.")
    parser.add_argument("-th", "--threads", action='store_true', help="Enables multithreading.")
    parser.add_argument("-d", "--monitoring-delay", type=int, required=False, default=600,
                        help="Delay between path lock monitoring cycles.")
    args = parser.parse_args()

    if not args.webdav and not args.bucket:
        parser.error('Either --webdav or --bucket parameter should be specified.')
    if args.bucket and (args.chunk_size < 5 * MB or args.chunk_size > 5 * GB):
        parser.error('Chunk size can vary from 5 MB to 5 GB due to AWS s3 multipart upload limitations.')
    if args.logging_level not in _allowed_logging_level_names:
        parser.error('Only the following logging level are allowed: %s.' % _allowed_logging_levels_string)
    logging.basicConfig(format='[%(levelname)s] %(asctime)s %(filename)s - %(message)s',
                        level=_allowed_logging_level_names[args.logging_level])

    try:
        start(args.mountpoint, webdav=args.webdav, bucket=args.bucket, buffer_size=args.buffer_size,
              chunk_size=args.chunk_size, cache_ttl=args.cache_ttl, cache_size=args.cache_size, default_mode=args.mode,
              mount_options=parse_mount_options(args.options), threads=args.threads,
              monitoring_delay=args.monitoring_delay)
    except BaseException as e:
        logging.error('Unhandled error: %s' % e.message)
        sys.exit(1)
