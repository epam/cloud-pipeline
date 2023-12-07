#  Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import io
import logging
import os
from threading import Thread

import errno
import time
from datetime import datetime, timedelta
from dateutil.tz import tzlocal

from pipefuse.fsclient import FileSystemClientDecorator


def mkdir(path):
    try:
        os.makedirs(path)
    except OSError as e:
        if e.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            raise


class DiskBufferingReadAllFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, read_ahead_size, path):
        """
        Disk buffering read all file system client decorator.

        It reads whole files and persists them in local file system
        to reduce a number of subsequent calls to an inner file system client.

        :param inner: Decorating file system client.
        :param read_ahead_size: Amount of bytes that will be read ahead from an inner file system client at once.
        :param path: Local file system path to persist files.
        """
        super(DiskBufferingReadAllFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._path = path
        self._read_ahead_size = read_ahead_size

    def download_range(self, fh, buf, path, offset=0, length=0):
        try:
            file_buf_path = os.path.join(self._path, path.lstrip('/'))
            if not os.path.exists(file_buf_path):
                file_size = self._inner.attrs(path).size
                if not file_size or offset >= file_size:
                    return
                mkdir(os.path.dirname(file_buf_path))
                with open(file_buf_path, 'wb') as f:
                    remaining_size = file_size
                    current_offset = 0
                    while remaining_size:
                        current_length = min(remaining_size, self._read_ahead_size)
                        with io.BytesIO() as current_buf:
                            logging.info('Downloading buffer range %d-%d for %d:%s'
                                         % (current_offset, current_offset + current_length, fh, path))
                            self._inner.download_range(fh, current_buf, path, current_offset, length=current_length)
                            logging.info('Persisting buffer range %d-%d for %d:%s'
                                         % (current_offset, current_offset + current_length, fh, path))
                            f.write(current_buf.getvalue())
                        remaining_size -= current_length
                        current_offset += current_length
            file_size = os.path.getsize(file_buf_path)
            if not file_size or offset >= file_size:
                return
            with open(file_buf_path, 'rb') as f:
                f.seek(offset)
                buf.write(f.read(min(length, file_size - offset)))
        except Exception:
            logging.exception('Downloading has failed for %s. '
                              'Removing the corresponding buffer.' % path)
            self._remove_buf(path)
            raise

    def _remove_buf(self, path):
        file_buf_path = os.path.join(self._path, path.lstrip('/'))
        if os.path.exists(file_buf_path):
            os.remove(file_buf_path)


class DiskBufferTTLDaemon:

    def __init__(self, path, ttl, delay):
        """
        Disk buffer time to live daemon.

        Monitors local file system path and deletes expired files.

        :param path: Local file system path to monitor.
        :param ttl: Files time to live in seconds.
        :param delay: Local file system monitoring delay.
        """
        self._path = path
        self._ttl = timedelta(seconds=ttl)
        self._polling_timeout = delay
        self._thread = Thread(name='DiskBufferTTL', target=self.run)
        self._thread.daemon = True

    def start(self):
        self._thread.start()

    def join(self, timeout=None):
        logging.info('Closing disk buffer ttl daemon...')
        self._thread.join(timeout=timeout)

    def run(self):
        logging.info('Initiating disk buffer ttl daemon...')
        while True:
            time.sleep(self._polling_timeout)
            try:
                now = datetime.now(tz=tzlocal())
                for subdir, dirs, files in os.walk(self._path):
                    for file in files:
                        file_path = os.path.join(subdir, file)
                        file_mdt = datetime.fromtimestamp(os.path.getmtime(file_path), tz=tzlocal())
                        if now < file_mdt + self._ttl:
                            continue
                        logging.info('Invalidating disk buffer for %s' % file_path)
                        os.remove(file_path)
            except KeyboardInterrupt:
                logging.warning('Interrupted.')
                raise
            except Exception:
                logging.warning('Disk buffer ttl daemon iteration has failed.', exc_info=True)
