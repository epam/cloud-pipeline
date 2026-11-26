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

import logging
import os
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from threading import Event, Thread

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


class _DownloadStoppedException(Exception):
    pass


class _StoppableWriter(object):

    def __init__(self, inner, stop):
        """
        File writer that fails as soon as the download of the file is stopped.

        Storage clients write a range in small pieces, so a failed range stops the other ones
        after their next piece instead of after their whole range.

        :param inner: Writable file.
        :param stop: Event that is set once the download of the file is stopped.
        """
        self._inner = inner
        self._stop = stop

    def write(self, data):
        if self._stop.is_set():
            raise _DownloadStoppedException()
        return self._inner.write(data)

    def __getattr__(self, name):
        return getattr(self._inner, name)


class DiskBufferingReadAllFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, read_ahead_size, path, download_workers=1):
        """
        Disk buffering read all file system client decorator.

        It reads whole files and persists them in local file system
        to reduce a number of subsequent calls to an inner file system client.

        :param inner: Decorating file system client.
        :param read_ahead_size: Amount of bytes that will be read ahead from an inner file system client at once.
                               Use 0 or None for auto: when download_workers > 1, split each file into
                               download_workers equal chunks; when download_workers == 1, one chunk per file.
        :param path: Local file system path to persist files.
        :param download_workers: Number of parallel workers for full-file download (1 = sequential).
        """
        super(DiskBufferingReadAllFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._path = path
        self._read_ahead_size = read_ahead_size
        self._download_workers = max(1, int(download_workers))
        self._auto_chunk = read_ahead_size is None or read_ahead_size == 0

    def _download_one_range(self, fh, path, part_path, range_offset, range_length, stop):
        """
        Downloads a single range from the inner client straight into the partial buffer file.

        Each call opens its own handle, so parallel workers do not share a file position,
        and no range is held in memory.
        """
        if stop.is_set():
            raise _DownloadStoppedException()
        logging.info('Downloading buffer range %d-%d for %d:%s'
                     % (range_offset, range_offset + range_length, fh, path))
        with open(part_path, 'r+b') as f:
            f.seek(range_offset)
            self._inner.download_range(fh, _StoppableWriter(f, stop), path, range_offset, length=range_length)
            downloaded_length = f.tell() - range_offset
        if downloaded_length != range_length:
            raise IOError('Buffer range %d-%d for %d:%s has %d bytes instead of %d'
                          % (range_offset, range_offset + range_length, fh, path,
                             downloaded_length, range_length))
        logging.info('Persisted buffer range %d-%d for %d:%s'
                     % (range_offset, range_offset + range_length, fh, path))

    def _get_chunks(self, file_size):
        """Returns the list of (offset, length) chunks the whole file is downloaded in."""
        if self._auto_chunk and self._download_workers > 1:
            # Auto: split file into equal chunks according to download_workers
            desired_chunks = max(1, min(self._download_workers, file_size))
            chunks = []
            for i in range(desired_chunks):
                chunk_start = (i * file_size) // desired_chunks
                chunk_end = ((i + 1) * file_size) // desired_chunks
                chunk_len = chunk_end - chunk_start
                if chunk_len > 0:
                    chunks.append((chunk_start, chunk_len))
            return chunks
        if self._auto_chunk:
            # Auto with one worker: single chunk (whole file)
            return [(0, file_size)]
        # User-defined read_ahead_size: fixed-size chunks
        chunks = []
        current_offset = 0
        remaining_size = file_size
        while remaining_size:
            chunk_length = min(remaining_size, self._read_ahead_size)
            chunks.append((current_offset, chunk_length))
            remaining_size -= chunk_length
            current_offset += chunk_length
        return chunks

    def _download_file(self, fh, path, file_buf_path, file_size):
        """
        Downloads the whole file into its buffer.

        The ranges are written into a partial file next to the buffer, which becomes the buffer
        only once every range is there. So a download that fails, or a mount that is killed during one,
        never leaves a part of the file behind as its buffer.

        The partial file has a short name of its own, as the name of the file may already be
        as long as the local file system allows.
        """
        chunks = self._get_chunks(file_size)
        part_path = os.path.join(os.path.dirname(file_buf_path), '.%s.part' % uuid.uuid4().hex)
        stop = Event()
        try:
            open(part_path, 'wb').close()
            workers = min(self._download_workers, len(chunks))
            if workers <= 1:
                for range_offset, range_length in chunks:
                    self._download_one_range(fh, path, part_path, range_offset, range_length, stop)
            else:
                with ThreadPoolExecutor(max_workers=workers) as executor:
                    futures = [executor.submit(self._download_one_range, fh, path, part_path,
                                               range_offset, range_length, stop)
                               for range_offset, range_length in chunks]
                    try:
                        for future in as_completed(futures):
                            future.result()
                    except Exception:
                        # The other ranges stop after their next piece, and the executor waits for them
                        stop.set()
                        raise
            os.rename(part_path, file_buf_path)
        finally:
            if os.path.exists(part_path):
                os.remove(part_path)

    def download_range(self, fh, buf, path, offset=0, length=0):
        try:
            file_buf_path = os.path.join(self._path, path.lstrip('/'))
            if not os.path.exists(file_buf_path):
                file_size = self._inner.attrs(path).size
                if not file_size or offset >= file_size:
                    return
                mkdir(os.path.dirname(file_buf_path))
                self._download_file(fh, path, file_buf_path, file_size)
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
