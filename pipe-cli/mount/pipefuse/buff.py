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

import io
import logging

from fsclient import FileSystemClientDecorator
from fuseutils import MB


class _FileBuffer(object):

    def __init__(self, offset, capacity):
        self._offset = offset
        self._current_offset = self._offset
        self._capacity = capacity
        self._buffs = []

    @property
    def offset(self):
        return self._current_offset

    @property
    def size(self):
        return self._current_offset - self._offset

    @property
    def capacity(self):
        return self._capacity

    def append(self, buf):
        self._buffs.append(buf)
        self._current_offset += len(buf)


class _WriteBuffer(_FileBuffer):

    def __init__(self, offset, capacity, inherited_size=0):
        super(_WriteBuffer, self).__init__(offset, capacity)
        self._inherited_size = inherited_size

    @property
    def inherited_size(self):
        return max(self._current_offset, self._inherited_size)

    def is_full(self):
        return self.size >= self.capacity

    def collect(self):
        collected_buf_size = self.size
        collected_buf = bytearray(collected_buf_size)
        current_offset = 0
        for current_buf in self._buffs:
            current_buf_size = len(current_buf)
            collected_buf[current_offset:current_offset + current_buf_size] = current_buf
            current_offset += current_buf_size
        return collected_buf, self._offset

    def suits(self, offset):
        return offset == self._current_offset


class _ReadBuffer(_FileBuffer):

    def view(self, offset, length):
        start = offset
        end = min(start + length, self._capacity)
        second_buf_shift = len(self._buffs[0])
        gap = self._offset + second_buf_shift
        relative_start = start - self._offset
        relative_end = end - self._offset
        if end <= gap:
            return self._buffs[0][relative_start:relative_end]
        elif start >= gap:
            relative_start = relative_start - second_buf_shift
            relative_end = relative_end - second_buf_shift
            return self._buffs[1][relative_start:relative_end]
        else:
            relative_gap = gap - self._offset
            return self._buffs[0][relative_start:relative_gap] \
                   + self._buffs[1][relative_gap - second_buf_shift:relative_end - second_buf_shift]

    def suits(self, offset, length):
        return self._offset <= offset <= self._current_offset and \
               (offset + length <= self._current_offset or self._current_offset == self._capacity)

    def shrink(self):
        if len(self._buffs) > 2:
            old_first_buf = self._buffs.pop(0)
            self._offset += len(old_first_buf)


class BufferedFileSystemClient(FileSystemClientDecorator):

    _READ_AHEAD_SIZE = 20 * MB
    _READ_AHEAD_MULTIPLIER = 1.1

    def __init__(self, inner, capacity):
        """
        Buffering file system client decorator.

        It merges multiple writes to temporary buffers to reduce number of calls to an inner file system client.

        :param inner: Decorating file system client.
        :param capacity: Capacity of single file buffer in bytes.
        """
        super(BufferedFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._capacity = capacity
        self._write_file_buffs = {}
        self._read_file_buffs = {}

    def attrs(self, path):
        attrs = self._inner.attrs(path)
        write_buf = self._write_file_buffs.get(path)
        if write_buf:
            attrs = attrs._replace(size=max(attrs.size, write_buf.inherited_size))
        return attrs

    def download_range(self, fh, buf, path, offset=0, length=0):
        buf_key = fh, path
        file_buf = self._read_file_buffs.get(buf_key)
        if not file_buf:
            file_size = self._inner.attrs(path).size
            if not file_size:
                return
            file_buf = self._new_read_buf(fh, path, file_size, offset, length)
            self._read_file_buffs[buf_key] = file_buf
        if not file_buf.suits(offset, length):
            if file_buf.offset < file_buf.capacity:
                file_buf.append(self._read_ahead(fh, path, file_buf.offset, length))
                file_buf.shrink()
            if not file_buf.suits(offset, length):
                file_buf = self._new_read_buf(fh, path, file_buf.capacity, offset, length)
                self._read_file_buffs[buf_key] = file_buf
        buf.write(file_buf.view(offset, length))

    def _new_read_buf(self, fh, path, file_size, offset, length):
        file_buf = _ReadBuffer(offset, file_size)
        file_buf.append(self._read_ahead(fh, path, offset, length))
        return file_buf

    def _read_ahead(self, fh, path, offset, length):
        size = max(self._READ_AHEAD_SIZE, int(length * self._READ_AHEAD_MULTIPLIER))
        with io.BytesIO() as read_ahead_buf:
            logging.info('Downloading buffer range %d-%d for %d:%s' % (offset, offset + size, fh, path))
            self._inner.download_range(fh, read_ahead_buf, path, offset, length=size)
            return read_ahead_buf.getvalue()

    def upload_range(self, fh, buf, path, offset=0):
        file_buf = self._write_file_buffs.get(path)
        if not file_buf:
            file_buf = self._new_write_buf(self._capacity, offset)
            self._write_file_buffs[path] = file_buf
        if file_buf.suits(offset):
            file_buf.append(buf)
        else:
            logging.info('Upload buffer is not sequential for %d:%s. Buffer will be cleared.' % (fh, path))
            self._flush_write_buf(fh, path)
            old_file_buf = self._remove_write_buf(fh, path)
            file_buf = self._new_write_buf(self._capacity, offset, buf, old_file_buf)
            self._write_file_buffs[path] = file_buf
        if file_buf.is_full():
            logging.info('Upload buffer is full for %d:%s. Buffer will be cleared.' % (fh, path))
            self._flush_write_buf(fh, path)
            self._remove_write_buf(fh, path)
            file_buf = self._new_write_buf(self._capacity, file_buf.offset, buf=None, old_write_buf=file_buf)
            self._write_file_buffs[path] = file_buf

    def _new_write_buf(self, capacity, offset, buf=None, old_write_buf=None):
        write_buf = _WriteBuffer(offset, capacity, inherited_size=old_write_buf.inherited_size if old_write_buf else 0)
        if buf:
            write_buf.append(buf)
        return write_buf

    def flush(self, fh, path):
        logging.info('Flushing buffers for %d:%s' % (fh, path))
        self._flush_write_buf(fh, path)
        self._inner.flush(fh, path)
        self._remove_read_buf(fh, path)
        self._remove_write_buf(fh, path)

    def _remove_read_buf(self, fh, path):
        return self._read_file_buffs.pop((fh, path), None)

    def _remove_write_buf(self, fh, path):
        return self._write_file_buffs.pop(path, None)

    def _flush_write_buf(self, fh, path):
        write_buf = self._write_file_buffs.get(path, None)
        if write_buf:
            collected_buf, collected_offset = write_buf.collect()
            if collected_buf:
                logging.info('Uploading buffer range %d-%d for %d:%s'
                             % (collected_offset, collected_offset + len(collected_buf), fh, path))
                self._inner.upload_range(fh, collected_buf, path, collected_offset)
        return write_buf
