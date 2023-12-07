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

import logging

from pipefuse.fsclient import FileSystemClientDecorator


class _WriteBuffer:

    def __init__(self, offset, capacity, file_size=0):
        self._offset = offset
        self._capacity = capacity
        self._file_size = file_size
        self._current_offset = self._offset
        self._buf = bytearray()

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
        return self._capacity

    @property
    def file_size(self):
        return max(self._current_offset, self._file_size)

    def append(self, buf):
        self._buf += buf
        self._current_offset += len(buf)

    def suits(self, offset):
        return offset == self._current_offset

    def is_full(self):
        return self.size >= self.capacity

    def collect(self):
        return self._buf, self._offset


class MemoryBufferingWriteFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, capacity):
        """
        Memory buffering write file system client decorator.

        It merges multiple writes to temporary buffers in order to reduce a number of subsequent calls
        to an inner file system client.

        :param inner: Decorating file system client.
        :param capacity: Single file buffer capacity in bytes.
        """
        super(MemoryBufferingWriteFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._capacity = capacity
        self._buffs = {}

    def attrs(self, path):
        attrs = self._inner.attrs(path)
        write_buf = self._buffs.get(path)
        if write_buf:
            attrs = attrs._replace(size=max(attrs.size, write_buf.file_size))
        return attrs

    def download_range(self, fh, buf, path, offset=0, length=0):
        write_buf = self._buffs.get(path, None)
        if write_buf:
            logging.info('Flushing inside read %d:%s.' % (fh, path))
            self.flush(fh, path)
        self._inner.download_range(fh, buf, path, offset, length)

    def upload_range(self, fh, buf, path, offset=0):
        try:
            file_buf = self._buffs.get(path)
            if not file_buf:
                file_buf = self._new_write_buf(self._capacity, offset)
                self._buffs[path] = file_buf
            if file_buf.suits(offset):
                file_buf.append(buf)
            else:
                logging.info('Uploading is not sequential for %d:%s. New buffer will be used.' % (fh, path))
                self._flush_write_buf(fh, path)
                old_file_buf = self._remove_write_buf(fh, path)
                file_buf = self._new_write_buf(self._capacity, offset, buf, old_file_buf)
                self._buffs[path] = file_buf
            if file_buf.is_full():
                logging.info('Uploading buffer is full for %d:%s. New buffer will be used.' % (fh, path))
                self._flush_write_buf(fh, path)
                self._remove_write_buf(fh, path)
                file_buf = self._new_write_buf(self._capacity, file_buf.end, buf=None, old_write_buf=file_buf)
                self._buffs[path] = file_buf
        except Exception:
            logging.exception('Uploading has failed for %d:%s. '
                              'Removing the corresponding buffer.' % (fh, path))
            self._remove_write_buf(fh, path)
            raise

    def _new_write_buf(self, capacity, offset, buf=None, old_write_buf=None):
        write_buf = _WriteBuffer(offset, capacity, file_size=old_write_buf.file_size if old_write_buf else 0)
        if buf:
            write_buf.append(buf)
        return write_buf

    def flush(self, fh, path):
        try:
            logging.info('Flushing the corresponding buffer for %d:%s' % (fh, path))
            self._flush_write_buf(fh, path)
            self._inner.flush(fh, path)
            self._remove_write_buf(fh, path)
        except Exception:
            logging.exception('Flushing has failed for %d:%s. '
                              'Removing the corresponding buffer.' % (fh, path))
            self._remove_write_buf(fh, path)
            raise

    def _remove_write_buf(self, fh, path):
        return self._buffs.pop(path, None)

    def _flush_write_buf(self, fh, path):
        write_buf = self._buffs.get(path, None)
        if write_buf:
            collected_buf, collected_offset = write_buf.collect()
            if collected_buf:
                logging.info('Uploading buffer range %d-%d for %d:%s'
                             % (collected_offset, collected_offset + len(collected_buf), fh, path))
                self._inner.upload_range(fh, collected_buf, path, collected_offset)
        return write_buf
