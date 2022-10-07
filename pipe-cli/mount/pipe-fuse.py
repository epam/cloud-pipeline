# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import ctypes
import errno
import logging
import os
import platform
import sys
import traceback

import future.utils
from cachetools import TTLCache


def is_windows():
    return platform.system() == 'Windows'


is_frozen = getattr(sys, 'frozen', False)

if is_frozen:
    source_path = sys._MEIPASS
    libfuse_library = 'libfuse.so.frozen'
    dokanfuse_library = 'dokanfuse1.dll.frozen'
else:
    source_path = os.path.dirname(__file__)
    libfuse_library = 'libfuse.so.2.9.2'
    dokanfuse_library = 'dokanfuse1.dll.1.5.0.3000'

libfuse_path = os.path.abspath(os.path.join(source_path, 'libfuse',
                                            dokanfuse_library if is_windows() else libfuse_library))
if os.path.exists(libfuse_path):
    os.environ["FUSE_LIBRARY_PATH"] = libfuse_path

import fuse
from fuse import FUSE, fuse_operations, fuse_file_info, c_utimbuf

from pipefuse.api import CloudPipelineClient, CloudType
from pipefuse.buffread import BufferingReadAheadFileSystemClient
from pipefuse.buffwrite import BufferingWriteFileSystemClient
from pipefuse.cache import ListingCache, ThreadSafeListingCache, \
    CachingListingFileSystemClient
from pipefuse.fslock import get_lock
from pipefuse.fuseutils import MB, GB
from pipefuse.gcp import GoogleStorageLowLevelFileSystemClient
from pipefuse.path import PathExpandingStorageFileSystemClient
from pipefuse.pipefs import PipeFS, SupportedOperationsFS
from pipefuse.record import RecordingFileSystemClient, RecordingFS
from pipefuse.s3 import S3StorageLowLevelClient
from pipefuse.storage import StorageHighLevelFileSystemClient
from pipefuse.trunc import CopyOnDownTruncateFileSystemClient, \
    WriteNullsOnUpTruncateFileSystemClient, \
    WriteLastNullOnUpTruncateFileSystemClient
from pipefuse.webdav import WebDavClient, WebDavAdapterFileSystemClient
from pipefuse.xattr import ExtendedAttributesCache, ThreadSafeExtendedAttributesCache, \
    ExtendedAttributesCachingFileSystemClient, RestrictingExtendedAttributesFS

_allowed_logging_level_names = ['CRITICAL', 'ERROR', 'WARNING', 'INFO', 'DEBUG', 'NOTSET']
_allowed_logging_levels = future.utils.lfilter(lambda name: isinstance(name, str), _allowed_logging_level_names)
_allowed_logging_levels_string = ', '.join(_allowed_logging_levels)
_default_logging_level = 'ERROR'
_debug_logging_level = 'DEBUG'
_info_logging_level = 'INFO'
_xattrs_operations = ['setxattr', 'getxattr', 'listxattr', 'removexattr']
_xattrs_include_prefix = 'user'


def start(mountpoint, webdav, bucket,
          read_buffer_size, read_ahead_min_size, read_ahead_max_size, read_ahead_size_multiplier,
          write_buffer_size, trunc_buffer_size, chunk_size,
          listing_cache_ttl, listing_cache_size,
          xattrs_include_prefixes, xattrs_exclude_prefixes,
          xattrs_cache_ttl, xattrs_cache_size,
          disabled_operations, default_mode,
          mount_options, threads=False, monitoring_delay=600, recording=False):
    try:
        os.makedirs(mountpoint)
    except OSError as e:
        if e.errno != errno.EEXIST:
            raise

    api = os.getenv('API', '')
    bearer = os.getenv('API_TOKEN', '')
    chunk_size = int(os.getenv('CP_PIPE_FUSE_CHUNK_SIZE', chunk_size))
    read_ahead_min_size = int(os.getenv('CP_PIPE_FUSE_READ_AHEAD_MIN_SIZE', read_ahead_min_size))
    read_ahead_max_size = int(os.getenv('CP_PIPE_FUSE_READ_AHEAD_MAX_SIZE', read_ahead_max_size))
    read_ahead_size_multiplier = int(os.getenv('CP_PIPE_FUSE_READ_AHEAD_SIZE_MULTIPLIER',
                                               read_ahead_size_multiplier))
    bucket_type = None
    root_path = None
    if not bearer:
        raise RuntimeError('Cloud Pipeline API_TOKEN should be specified.')
    if webdav:
        client = WebDavClient(webdav_url=webdav, bearer=bearer)
        client = WebDavAdapterFileSystemClient(client)
    else:
        if not api:
            raise RuntimeError('Cloud Pipeline API should be specified.')
        pipe = CloudPipelineClient(api=api, token=bearer)
        path_chunks = bucket.rstrip('/').split('/')
        bucket_name = path_chunks[0]
        root_path = '/'.join(path_chunks[1:])
        bucket_object = pipe.get_storage(bucket)
        bucket_type = bucket_object.type
        if bucket_type == CloudType.S3:
            client = S3StorageLowLevelClient(bucket_name, pipe=pipe, chunk_size=chunk_size, storage_path=bucket)
        elif bucket_type == CloudType.GS:
            client = GoogleStorageLowLevelFileSystemClient(bucket_name, pipe=pipe, chunk_size=chunk_size,
                                                           storage_path=bucket)
        else:
            raise RuntimeError('Cloud storage type %s is not supported.' % bucket_object.type)
        client = StorageHighLevelFileSystemClient(client)
    if recording:
        client = RecordingFileSystemClient(client)
    if bucket_type in [CloudType.S3, CloudType.GS]:
        client = PathExpandingStorageFileSystemClient(client, root_path=root_path)
    if listing_cache_ttl > 0 and listing_cache_size > 0:
        listing_cache_implementation = TTLCache(maxsize=listing_cache_size, ttl=listing_cache_ttl)
        listing_cache = ListingCache(listing_cache_implementation)
        if threads:
            listing_cache = ThreadSafeListingCache(listing_cache)
        client = CachingListingFileSystemClient(client, listing_cache)
    else:
        logging.info('Listing caching is disabled.')
    if bucket_type == CloudType.S3:
        if xattrs_cache_ttl > 0 and xattrs_cache_size > 0:
            xattrs_cache_implementation = TTLCache(maxsize=xattrs_cache_size, ttl=xattrs_cache_ttl)
            xattrs_cache = ExtendedAttributesCache(xattrs_cache_implementation)
            if threads:
                xattrs_cache = ThreadSafeExtendedAttributesCache(xattrs_cache)
            client = ExtendedAttributesCachingFileSystemClient(client, xattrs_cache)
        else:
            logging.info('Extended attributes caching is disabled.')
    if read_buffer_size > 0:
        client = BufferingReadAheadFileSystemClient(client,
                                                    read_ahead_min_size=read_ahead_min_size,
                                                    read_ahead_max_size=read_ahead_max_size,
                                                    read_ahead_size_multiplier=read_ahead_size_multiplier,
                                                    capacity=read_buffer_size)
    else:
        logging.info('Read buffering is disabled.')
    if write_buffer_size > 0:
        client = BufferingWriteFileSystemClient(client, capacity=write_buffer_size)
    else:
        logging.info('Write buffering is disabled.')
    if trunc_buffer_size > 0:
        if webdav:
            client = CopyOnDownTruncateFileSystemClient(client, capacity=trunc_buffer_size)
            client = WriteLastNullOnUpTruncateFileSystemClient(client)
        elif bucket_type == CloudType.S3:
            client = WriteNullsOnUpTruncateFileSystemClient(client, capacity=trunc_buffer_size)
        elif bucket_type == CloudType.GS:
            client = CopyOnDownTruncateFileSystemClient(client, capacity=trunc_buffer_size)
            client = WriteNullsOnUpTruncateFileSystemClient(client, capacity=trunc_buffer_size)
    else:
        logging.info('Truncating support is disabled.')

    fs = PipeFS(client=client, lock=get_lock(threads, monitoring_delay=monitoring_delay), mode=int(default_mode, 8))
    if bucket_type == CloudType.S3:
        if xattrs_include_prefixes:
            if xattrs_include_prefixes[0] == '*':
                logging.info('All extended attributes will be processed.')
            else:
                fs = RestrictingExtendedAttributesFS(fs, include_prefixes=xattrs_include_prefixes)
        if xattrs_exclude_prefixes:
            if xattrs_exclude_prefixes[0] == '*':
                logging.info('All extended attributes operations will be disabled.')
                disabled_operations.extend(_xattrs_operations)
            else:
                fs = RestrictingExtendedAttributesFS(fs, exclude_prefixes=xattrs_exclude_prefixes)
    else:
        logging.info('All extended attributes operations will be disabled.')
        disabled_operations.extend(_xattrs_operations)
    fs = SupportedOperationsFS(fs, exclude=disabled_operations)
    if recording:
        fs = RecordingFS(fs)

    logging.info('File system processing chain: \n%s', fs.summary())

    logging.info('Initializing file system...')
    enable_additional_operations()
    ro = client.is_read_only() or mount_options.get('ro', False)
    mount_options.pop('ro', None)
    FUSE(fs, mountpoint, nothreads=not threads, foreground=True, ro=ro, **mount_options)


def enable_additional_operations():
    class fuse_pollhandle(ctypes.Structure):
        pass

    class fuse_bufvec(ctypes.Structure):
        pass

    # Only the operations required by libfuse are implemented.
    # Notice that the fields order is important.
    # https://github.com/libfuse/libfuse/blob/ad38195a88c80d73cb46507851ebb870f3bd588d/include/fuse.h#L88
    linux_fields = list(fuse_operations._fields_) + [
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

    # Only the operations required by dokany are implemented.
    # Notice that the fields order is important.
    # https://github.com/dokan-dev/dokany/blob/6f8a3472dfbb36bd2340b3b59aa4a72e7d8b8795/dokan_fuse/include/fuse.h#L100
    win_fields = list(fuse_operations._fields_[:-5]) + [
        ('win_get_attributes', ctypes.CFUNCTYPE(
            ctypes.c_uint, ctypes.c_char_p)),

        ('win_set_attributes', ctypes.CFUNCTYPE(
            ctypes.c_int, ctypes.c_char_p, ctypes.c_uint)),

        ('win_set_times', ctypes.CFUNCTYPE(
            ctypes.c_int, ctypes.c_char_p, ctypes.POINTER(fuse_file_info),
            ctypes.POINTER(c_utimbuf), ctypes.POINTER(c_utimbuf), ctypes.POINTER(c_utimbuf)))
    ]

    class extended_fuse_operations(ctypes.Structure):
        _fields_ = win_fields if is_windows() else linux_fields

    fuse.fuse_operations = extended_fuse_operations

    def fallocate(self, path, mode, offset, length, fip):
        fh = fip.contents if self.raw_fi else fip.contents.fh
        return self.operations('fallocate', path.decode(self.encoding), mode, offset, length, fh)

    def win_get_attributes(self, path):
        return self.operations('win_get_attributes', path.decode(self.encoding))

    def win_set_attributes(self, path, attrs, fip):
        fh = fip.contents if self.raw_fi else fip.contents.fh
        return self.operations('win_set_attributes', path.decode(self.encoding), attrs, fh)

    def win_set_times(self, path, fip, creation_time, last_access_time, last_write_time):
        fh = fip.contents if self.raw_fi else fip.contents.fh
        return self.operations('win_set_times', path.decode(self.encoding),
                               creation_time, last_access_time, last_write_time, fh)

    for operation in [fallocate, win_get_attributes, win_set_attributes, win_set_times]:
        setattr(FUSE, operation.__name__, operation)


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
    parser.add_argument("-rb", "--read-buffer-size", type=int, required=False, default=40 * MB,
                        help="Read buffer size for a single file")
    parser.add_argument("--read-ahead-min-size", type=int, required=False, default=1 * MB,
                        help="Min amount of bytes that will be read on each read ahead call. "
                             "Can be configured via CP_PIPE_FUSE_READ_AHEAD_MIN_SIZE environment variable.")
    parser.add_argument("--read-ahead-max-size", type=int, required=False, default=20 * MB,
                        help="Max amount of bytes that will be read on each read ahead call. "
                             "Can be configured via CP_PIPE_FUSE_READ_AHEAD_MAX_SIZE environment variable.")
    parser.add_argument("--read-ahead-size-multiplier", type=int, required=False, default=2,
                        help="Sequential read ahead size multiplier. "
                             "Can be configured via CP_PIPE_FUSE_READ_AHEAD_SIZE_MULTIPLIER environment variable.")
    parser.add_argument("-wb", "--write-buffer-size", type=int, required=False, default=512 * MB,
                        help="Write buffer size for a single file")
    parser.add_argument("-r", "--trunc-buffer-size", type=int, required=False, default=512 * MB,
                        help="Truncating buffer size for a single file")
    parser.add_argument("-c", "--chunk-size", type=int, required=False, default=10 * MB,
                        help="Multipart upload chunk size. Can be also specified via "
                             "CP_PIPE_FUSE_CHUNK_SIZE environment variable.")
    parser.add_argument("-t", "--cache-ttl", "--listing-cache-ttl", dest="listing_cache_ttl",
                        type=int, required=False, default=60,
                        help="Listing cache time to live, seconds")
    parser.add_argument("-s", "--cache-size", "--listing-cache-size", dest="listing_cache_size",
                        type=int, required=False, default=100,
                        help="Number of simultaneous listing caches")
    parser.add_argument("--xattrs-include-prefix", dest="xattrs_include_prefixes",
                        type=str, required=False, action="append", default=[],
                        help="Extended attribute prefixes to be included in processing. "
                             "Use --xattrs-include-prefix=\"*\" to enable all extended attributes processing. "
                             "The argument can be specified multiple times. "
                             "Defaults to \"%s\"." % _xattrs_include_prefix)
    parser.add_argument("--xattrs-exclude-prefix", dest="xattrs_exclude_prefixes",
                        type=str, required=False, action="append", default=[],
                        help="Extended attribute prefixes to be excluded from processing. "
                             "Use --xattrs-exclude-prefix=\"*\" to disable all extended attributes processing. "
                             "The argument can be specified multiple times.")
    parser.add_argument("--xattrs-cache-ttl", type=int, required=False, default=60,
                        help="Extended attributes cache time to live, seconds.")
    parser.add_argument("--xattrs-cache-size", type=int, required=False, default=1000,
                        help="Number of simultaneous extended attributes caches.")
    parser.add_argument("--disabled-operation", dest="disabled_operations",
                        type=str, required=False, action="append", default=[],
                        help="Disabled file system operations. "
                             "The argument can be specified multiple times.")
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

    if args.xattrs_include_prefixes and args.xattrs_exclude_prefixes:
        parser.error('Either --xattrs-include-prefix or --xattrs-exclude-prefix parameter should be specified.')
    if not args.xattrs_include_prefixes and not args.xattrs_exclude_prefixes:
        args.xattrs_include_prefixes = [_xattrs_include_prefix]
    if not args.webdav and not args.bucket:
        parser.error('Either --webdav or --bucket parameter should be specified.')
    if args.bucket and (args.chunk_size < 5 * MB or args.chunk_size > 5 * GB):
        parser.error('Chunk size can vary from 5 MB to 5 GB due to AWS S3 multipart upload limitations.')
    if args.logging_level not in _allowed_logging_levels:
        parser.error('Only the following logging level are allowed: %s.' % _allowed_logging_levels_string)
    recording = args.logging_level in [_info_logging_level, _debug_logging_level]
    logging.basicConfig(format='[%(levelname)s] %(asctime)s %(filename)s - %(message)s',
                        level=args.logging_level)
    logging.getLogger('botocore').setLevel(logging.ERROR)

    if is_frozen:
        logging.info('Frozen installation found. Bundled libfuse will be used.')
    else:
        logging.info('Packaged installation found. Either packaged or host libfuse will be used.')

    try:
        start(args.mountpoint, webdav=args.webdav, bucket=args.bucket,
              read_buffer_size=args.read_buffer_size,
              read_ahead_min_size=args.read_ahead_min_size, read_ahead_max_size=args.read_ahead_max_size,
              read_ahead_size_multiplier=args.read_ahead_size_multiplier,
              write_buffer_size=args.write_buffer_size, trunc_buffer_size=args.trunc_buffer_size,
              chunk_size=args.chunk_size,
              listing_cache_ttl=args.listing_cache_ttl, listing_cache_size=args.listing_cache_size,
              xattrs_include_prefixes=args.xattrs_include_prefixes,
              xattrs_exclude_prefixes=args.xattrs_exclude_prefixes,
              xattrs_cache_ttl=args.xattrs_cache_ttl, xattrs_cache_size=args.xattrs_cache_size,
              disabled_operations=args.disabled_operations,
              default_mode=args.mode, mount_options=parse_mount_options(args.options),
              threads=args.threads, monitoring_delay=args.monitoring_delay, recording=recording)
    except Exception:
        logging.exception('Unhandled error')
        traceback.print_exc()
        sys.exit(1)
