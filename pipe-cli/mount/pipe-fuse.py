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
import errno
import logging
import os
import sys
import future.utils

is_frozen = getattr(sys, 'frozen', False)

if is_frozen:
    source_path = sys._MEIPASS
    libfuse_version = 'frozen'
else:
    source_path = os.path.dirname(__file__)
    libfuse_version = '2.9.2'

libfuse_path = os.path.abspath(os.path.join(source_path, 'libfuse/libfuse.so.%s' % libfuse_version))
if os.path.exists(libfuse_path):
    os.environ["FUSE_LIBRARY_PATH"] = libfuse_path


from pipefuse.fuseutils import MB, GB
from pipefuse.cache import CachingFileSystemClient, ListingCache, ThreadSafeListingCache
from pipefuse.buff import BufferedFileSystemClient
from pipefuse.trunc import CopyOnDownTruncateFileSystemClient, \
    WriteNullsOnUpTruncateFileSystemClient, \
    WriteLastNullOnUpTruncateFileSystemClient
from pipefuse.api import CloudPipelineClient
from pipefuse.webdav import CPWebDavClient
from pipefuse.s3 import S3Client
from pipefuse.pipefs import PipeFS
from pipefuse.record import RecordingFS, RecordingFileSystemClient
from pipefuse.fslock import get_lock
import ctypes
import fuse
from fuse import FUSE, fuse_operations, fuse_file_info
from cachetools import TTLCache

_allowed_logging_level_names = logging._levelNames
_allowed_logging_levels = future.utils.lfilter(lambda name: isinstance(name, str), _allowed_logging_level_names.keys())
_allowed_logging_levels_string = ', '.join(_allowed_logging_levels)
_default_logging_level = _allowed_logging_level_names[logging.ERROR]
_debug_logging_level = _allowed_logging_level_names[logging.DEBUG]
_info_logging_level = _allowed_logging_level_names[logging.INFO]


def start(mountpoint, webdav, bucket, buffer_size, trunc_buffer_size, chunk_size, cache_ttl, cache_size, default_mode,
          mount_options=None, threads=False, monitoring_delay=600, recording=False):
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
    if recording:
        client = RecordingFileSystemClient(client)
    if cache_ttl > 0 and cache_size > 0:
        cache_implementation = TTLCache(maxsize=cache_size, ttl=cache_ttl)
        cache = ListingCache(cache_implementation)
        if threads:
            cache = ThreadSafeListingCache(cache)
        client = CachingFileSystemClient(client, cache)
    else:
        logging.info('Caching is disabled.')
    if buffer_size > 0:
        client = BufferedFileSystemClient(client, capacity=buffer_size)
    else:
        logging.info('Buffering is disabled.')
    if trunc_buffer_size > 0:
        if webdav:
            client = CopyOnDownTruncateFileSystemClient(client, capacity=trunc_buffer_size)
            client = WriteLastNullOnUpTruncateFileSystemClient(client)
        else:
            client = WriteNullsOnUpTruncateFileSystemClient(client, capacity=trunc_buffer_size)
    else:
        logging.info('Truncating support is disabled.')
    fs = PipeFS(client=client, lock=get_lock(threads, monitoring_delay=monitoring_delay), mode=int(default_mode, 8))
    if recording:
        fs = RecordingFS(fs)

    enable_fallocate_support()
    FUSE(fs, mountpoint, nothreads=not threads, foreground=True, ro=client.is_read_only(), **mount_options)


def enable_fallocate_support():
    class fuse_pollhandle(ctypes.Structure):
        pass

    class fuse_bufvec(ctypes.Structure):
        pass

    class extended_fuse_operations(ctypes.Structure):
        _fields_ = list(fuse_operations._fields_) + [
            # All the missing fields besides fallocate are required for some reason
            ('poll', ctypes.CFUNCTYPE(
                ctypes.c_int, ctypes.c_char_p, ctypes.POINTER(fuse_file_info),
                ctypes.POINTER(fuse_pollhandle), ctypes.c_uint)),

            ('write_buf', ctypes.CFUNCTYPE(
                ctypes.c_int, ctypes.c_char_p, ctypes.POINTER(fuse_bufvec), ctypes.c_longlong,
                ctypes.POINTER(fuse_file_info))),

            ('read_buf', ctypes.CFUNCTYPE(
                ctypes.c_int, ctypes.c_char_p, ctypes.POINTER(fuse_bufvec),
                ctypes.c_size_t, ctypes.c_longlong, ctypes.POINTER(fuse_file_info))),

            ('flock', ctypes.CFUNCTYPE(
                ctypes.c_int, ctypes.c_char_p, ctypes.POINTER(fuse_file_info), ctypes.c_int)),

            ('fallocate', ctypes.CFUNCTYPE(
                ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_longlong, ctypes.c_longlong,
                ctypes.POINTER(fuse_file_info))),
        ]

    fuse.fuse_operations = extended_fuse_operations

    def fallocate(self, path, mode, offset, length, fip):
        fh = fip.contents if self.raw_fi else fip.contents.fh
        return self.operations('fallocate', path.decode(self.encoding), mode, offset, length, fh)

    setattr(FUSE, 'fallocate', fallocate)


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
    parser.add_argument("-r", "--trunc-buffer-size", type=int, required=False, default=512 * MB,
                        help="Truncating buffer size for a single file")
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
    if args.logging_level not in _allowed_logging_levels:
        parser.error('Only the following logging level are allowed: %s.' % _allowed_logging_levels_string)
    recording = args.logging_level in [_info_logging_level, _debug_logging_level]
    logging.basicConfig(format='[%(levelname)s] %(asctime)s %(filename)s - %(message)s',
                        level=_allowed_logging_level_names[args.logging_level])
    logging.getLogger('botocore').setLevel(logging.ERROR)

    if is_frozen:
        logging.info('Frozen installation found. Bundled libfuse will be used.')
    else:
        logging.info('Packaged installation found. Either packaged or host libfuse will be used.')

    try:
        start(args.mountpoint, webdav=args.webdav, bucket=args.bucket, buffer_size=args.buffer_size,
              trunc_buffer_size=args.trunc_buffer_size, chunk_size=args.chunk_size, cache_ttl=args.cache_ttl,
              cache_size=args.cache_size, default_mode=args.mode, mount_options=parse_mount_options(args.options),
              threads=args.threads, monitoring_delay=args.monitoring_delay, recording=recording)
    except BaseException as e:
        logging.error('Unhandled error: %s' % str(e))
        sys.exit(1)
