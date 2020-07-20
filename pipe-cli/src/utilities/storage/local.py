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

import os

from src.utilities.storage.common import AbstractTransferManager

try:
    from urllib.request import urlopen  # Python 3
except ImportError:
    from urllib2 import urlopen  # Python 2

import click

from .s3 import StorageItemManager
from src.utilities.progress_bar import ProgressPercentage


class TransferFromHttpOrFtpToLocal(object):
    CHUNK_SIZE = 16 * 1024

    def __init__(self):
        pass

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False,
                 quiet=False, size=None, tags=(), skip_existing=False, lock=None):
        """
        Transfers data from remote resource (only ftp(s) or http(s) protocols supported) to local file system.
        :param source_wrapper: wrapper for ftp or http resource
        :type source_wrapper: FtpSourceWrapper or HttpSourceWrapper
        :param destination_wrapper: wrapper for local file
        :type destination_wrapper: LocalFileSystemWrapper
        :param path: full path to remote file
        :param relative_path: relative path
        :param clean: remove source files (unsupported for this kind of transfer)
        :param quiet: True if quite mode specified
        :param size: the size of the source file
        :param tags: not needed for this kind of transfer
        :param skip_existing: indicates --skip_existing option
        :param lock: The lock object if multithreaded transfer is requested
        :type lock: multiprocessing.Lock
        """
        if clean:
            raise AttributeError("Cannot perform 'mv' operation due to deletion remote files "
                                 "is not supported for ftp/http sources.")
        if path:
            source_key = path
        else:
            source_key = source_wrapper.path
        if destination_wrapper.path.endswith(os.path.sep):
            destination_key = os.path.join(destination_wrapper.path, relative_path)
        else:
            destination_key = destination_wrapper.path
        if skip_existing:
            remote_size = size
            local_size = StorageItemManager.get_local_file_size(destination_key)
            if local_size is not None and remote_size == local_size:
                if not quiet:
                    click.echo('Skipping file %s since it exists in the destination %s' % (source_key, destination_key))
                return
        AbstractTransferManager.create_local_folder(destination_key, lock)
        file_stream = urlopen(source_key)
        if StorageItemManager.show_progress(quiet, size):
            progress_bar = ProgressPercentage(relative_path, size)
        with open(destination_key, 'wb') as f:
            while True:
                chunk = file_stream.read(self.CHUNK_SIZE)
                if not chunk:
                    break
                f.write(chunk)
                if StorageItemManager.show_progress(quiet, size):
                    progress_bar.__call__(len(chunk))
        file_stream.close()


class LocalOperations:

    @classmethod
    def get_transfer_from_http_or_ftp_manager(cls, *_, **__):
        return TransferFromHttpOrFtpToLocal()
