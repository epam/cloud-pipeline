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

import io
import logging

from fsclient import FileSystemClientDecorator


class _ReadBuffer:

    def __init__(self, offset, capacity, file_size):
        self._offset = offset
        self._capacity = capacity
        self._file_size = file_size
        self._current_offset = self._offset
        self._buf = bytearray()
        self._last_read_size = 0

    @property
    def start(self):
        return self._offset

    @property
    def end(self):
        return self._current_offset

    @property
    def size(self):
        return self._current_offset - self._offset

    @property
    def capacity(self):
        return self.capacity

    @property
    def file_size(self):
        return self._file_size

    @property
    def last_read_size(self):
        return self._last_read_size

    def append(self, buf):
        self._buf += buf
        self._last_read_size = len(buf)
        self._current_offset += self._last_read_size

    def suits(self, offset, length):
        return self._offset <= offset <= self._current_offset \
               and (offset + length <= self._current_offset
                    or self._current_offset == self._file_size)

    def view(self, offset, length):
        relative_start = offset - self._offset
        relative_end = min(offset + length, self._file_size) - self._offset
        return self._buf[relative_start:relative_end]

    def shrink(self):
        size = self.size
        if size > self._capacity:
            shrink_size = size - self._capacity
            self._buf = self._buf[shrink_size:]
            self._offset += shrink_size


class BufferingReadAheadFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, read_ahead_min_size, read_ahead_max_size, read_ahead_size_multiplier, capacity):
        """
        Buffering read ahead file system client decorator.

        It performs reading ahead of requested regions in order to reduce a number of subsequent calls
        to an inner file system client.

        :param inner: Decorating file system client.
        :param read_ahead_min_size: Min amount of bytes that will be read ahead.
        :param read_ahead_max_size: Max amount of bytes that will be read ahead.
        :param read_ahead_size_multiplier: Sequential read ahead size multiplier.
        :param capacity: Single file buffer capacity in bytes.
        """
        super(BufferingReadAheadFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._read_ahead_min_size = read_ahead_min_size
        self._read_ahead_max_size = read_ahead_max_size
        self._read_ahead_size_multiplier = read_ahead_size_multiplier
        self._capacity = capacity
        self._buffs = {}

    def download_range(self, fh, buf, path, offset=0, length=0):
        try:
            buf_key = fh, path
            file_buf = self._buffs.get(buf_key)
            if not file_buf:
                file_size = self._inner.attrs(path).size
                if not file_size or offset >= file_size:
                    return
                file_buf = self._new_read_buf(fh, path, file_size, offset, length)
                self._buffs[buf_key] = file_buf
            if not file_buf.suits(offset, length):
                read_length = max(min(file_buf.last_read_size * self._read_ahead_size_multiplier,
                                      self._read_ahead_max_size), length)
                if offset >= file_buf.start and offset + length <= file_buf.end + read_length:
                    file_buf.append(self._read_ahead(fh, path, file_buf.end, length=read_length))
                    file_buf.shrink()
                else:
                    logging.info('Downloading is not sequential for %d:%s. New buffer will be used.' % (fh, path))
                    file_buf = self._new_read_buf(fh, path, file_buf.file_size, offset, length)
                    self._buffs[buf_key] = file_buf
            buf.write(file_buf.view(offset, length))
        except Exception:
            logging.exception('Downloading has failed for %d:%s. '
                              'Removing the corresponding buffer.' % (fh, path))
            self._remove_buf(fh, path)
            raise

    def _new_read_buf(self, fh, path, file_size, offset, length):
        read_length = max(self._read_ahead_min_size, length)
        file_buf = _ReadBuffer(offset, self._capacity, file_size)
        file_buf.append(self._read_ahead(fh, path, offset, length=read_length))
        return file_buf

    def _read_ahead(self, fh, path, offset, length):
        with io.BytesIO() as read_ahead_buf:
            logging.info('Downloading buffer range %d-%d for %d:%s' % (offset, offset + length, fh, path))
            self._inner.download_range(fh, read_ahead_buf, path, offset, length=length)
            return read_ahead_buf.getvalue()

    def flush(self, fh, path):
        try:
            logging.info('Flushing the corresponding buffer for %d:%s' % (fh, path))
            self._inner.flush(fh, path)
            self._remove_buf(fh, path)
        except Exception:
            logging.exception('Flushing has failed for %d:%s. '
                              'Removing the corresponding buffer.' % (fh, path))
            self._remove_buf(fh, path)
            raise

    def _remove_buf(self, fh, path):
        return self._buffs.pop((fh, path), None)
