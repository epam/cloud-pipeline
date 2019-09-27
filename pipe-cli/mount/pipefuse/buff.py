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

import io
import logging

from fsclient import FileSystemClient
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


class BufferedFileSystemClient(FileSystemClient):

    _READ_AHEAD_SIZE = 20 * MB

    def __init__(self, inner, capacity):
        """
        Buffering file system client decorator.

        It merges multiple writes to temporary buffers to reduce number of calls to an inner file system client.

        :param inner: Decorating file system client.
        :param capacity: Capacity of single file buffer in bytes.
        """
        self._inner = inner
        self._capacity = capacity
        self._write_file_buffs = {}
        self._read_file_buffs = {}

    def is_available(self):
        return self._inner.is_available()

    def is_read_only(self):
        return self._inner.is_read_only()

    def exists(self, path):
        return self._inner.exists(path)

    def attrs(self, path):
        attrs = self._inner.attrs(path)
        write_buf = self._write_file_buffs.get(path)
        if write_buf:
            attrs = attrs._replace(size=max(attrs.size, write_buf.inherited_size))
        return attrs

    def ls(self, path, depth=1):
        return self._inner.ls(path, depth)

    def upload(self, buf, path):
        self._inner.upload(buf, path)

    def delete(self, path):
        self._inner.delete(path)

    def mv(self, old_path, path):
        self._inner.mv(old_path, path)

    def mkdir(self, path):
        self._inner.mkdir(path)

    def rmdir(self, path):
        self._inner.rmdir(path)

    def download_range(self, fh, buf, path, offset=0, length=0):
        buf_key = fh, path
        file_buf = self._read_file_buffs.get(buf_key)
        if not file_buf:
            file_size = self.attrs(path).size
            if not file_size:
                return
            file_buf = self._new_read_buf(fh, path, file_size, offset)
            self._read_file_buffs[buf_key] = file_buf
        if not file_buf.suits(offset, length):
            file_buf.append(self._read_ahead(fh, path, file_buf.offset))
            file_buf.shrink()
            if not file_buf.suits(offset, length):
                file_buf = self._new_read_buf(fh, path, file_buf.capacity, offset)
                self._read_file_buffs[buf_key] = file_buf
        buf.write(file_buf.view(offset, length))

    def _new_read_buf(self, fh, path, file_size, offset):
        file_buf = _ReadBuffer(offset, file_size)
        file_buf.append(self._read_ahead(fh, path, offset))
        return file_buf

    def _read_ahead(self, fh, path, offset):
        with io.BytesIO() as read_ahead_buf:
            self._inner.download_range(fh, read_ahead_buf, path, offset, length=self._READ_AHEAD_SIZE)
            return read_ahead_buf.getvalue()

    def upload_range(self, fh, buf, path, offset=0):
        logging.debug('Uploading range %d-%d for %d:%s' % (offset, offset + len(buf), fh, path))
        file_buf = self._write_file_buffs.get(path)
        if not file_buf:
            file_buf = self._new_write_buf(self._capacity, offset)
            self._write_file_buffs[path] = file_buf
        if file_buf.suits(offset):
            file_buf.append(buf)
        else:
            logging.info('Uploading buffer is not sequential for %s. Buffer will be cleared.' % path)
            old_file_buf = self._flush_write_buf(fh, path)
            file_buf = self._new_write_buf(self._capacity, offset, buf, old_file_buf)
            self._write_file_buffs[path] = file_buf
        if file_buf.is_full():
            logging.info('Uploading buffer is full for %s. Buffer will be cleared.' % path)
            self._flush_write_buf(fh, path)
            file_buf = self._new_write_buf(self._capacity, file_buf.offset, buf=None, old_write_buf=file_buf)
            self._write_file_buffs[path] = file_buf

    def _new_write_buf(self, capacity, offset, buf=None, old_write_buf=None):
        write_buf = _WriteBuffer(offset, capacity, inherited_size=old_write_buf.inherited_size if old_write_buf else 0)
        if buf:
            write_buf.append(buf)
        return write_buf

    def _flush_write_buf(self, fh, path):
        write_buf = self._write_file_buffs.pop(path, None)
        if write_buf:
            collected_buf, collected_offset = write_buf.collect()
            if collected_buf:
                self._inner.upload_range(fh, collected_buf, path, collected_offset)
        return write_buf

    def flush(self, fh, path):
        logging.info('Flushing buffers for %d:%s' % (fh, path))
        self._flush_read_buf(fh, path)
        self._flush_write_buf(fh, path)
        self._inner.flush(fh, path)

    def _flush_read_buf(self, fh, path):
        return self._read_file_buffs.pop((fh, path), None)

    def __getattr__(self, name):
        if hasattr(self._inner, name):
            return getattr(self._inner, name)
        else:
            raise RuntimeError('BufferedFileSystemClient or its inner client doesn\'t have %s attribute.' % name)
