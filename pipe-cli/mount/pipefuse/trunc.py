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

from fsclient import FileSystemClientDecorator


class CopyOnDownTruncateFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, capacity):
        """
        Copy on down-truncate file system client decorator.

        Copies file content up to the given length to a temporary file if a truncating
        length is smaller then the original file length.

        :param inner: Decorating file system client.
        :param capacity: Capacity of single file buffer in bytes.
        """
        super(CopyOnDownTruncateFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._capacity = capacity

    def truncate(self, fh, path, length):
        file_size = self.attrs(path).size
        if not length:
            self._inner.upload(bytearray(), path)
        elif file_size > length:
            remaining_size = length
            current_offset = 0
            temp_path = self._temp_path(path)
            try:
                self._inner.upload([], temp_path)
                while remaining_size:
                    current_size = min(remaining_size, self._capacity)
                    with io.BytesIO() as current_buf:
                        self._inner.download_range(fh, current_buf, path, current_offset, current_size)
                        self._inner.upload_range(fh, bytearray(current_buf.getvalue()), temp_path, current_offset)
                    remaining_size -= current_size
                    current_offset += current_size
                self._inner.flush(fh, temp_path)
                self._inner.mv(temp_path, path)
            except:
                if self._inner.exists(temp_path):
                    self._inner.delete(temp_path)
                raise
        elif file_size < length:
            self._inner.truncate(fh, path, length)

    def _temp_path(self, path):
        return '%s_%s.tmp' % (path, str(abs(hash(path))))


class WriteNullsOnUpTruncateFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, capacity):
        """
        Write nulls on up-truncate file system client decorator.

        Writes null bytes up to the given length to a file if
        truncating length is bigger then the original file length.

        :param inner: Decorating file system client.
        :param capacity: Capacity of single file buffer in bytes.
        """
        super(WriteNullsOnUpTruncateFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._capacity = capacity

    def truncate(self, fh, path, length):
        file_size = self.attrs(path).size
        if not length:
            self._inner.upload(bytearray(), path)
        elif file_size > length:
            self._inner.truncate(fh, path, length)
        elif file_size < length:
            remaining_size = length - file_size
            current_offset = file_size
            while remaining_size:
                current_size = min(remaining_size, self._capacity)
                self._inner.upload_range(fh, bytearray(current_size), path, offset=current_offset)
                remaining_size -= current_size
                current_offset += current_size
            self._inner.flush(fh, path)


class WriteLastNullOnUpTruncateFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner):
        """
        Write last null on up-truncate file system client decorator.

        Writes a single null byte to the last position by the given length to a file if
        truncating length is bigger then the original file length.

        :param inner: Decorating file system client.
        """
        super(WriteLastNullOnUpTruncateFileSystemClient, self).__init__(inner)
        self._inner = inner

    def truncate(self, fh, path, length):
        file_size = self.attrs(path).size
        if not length:
            self._inner.upload(bytearray(), path)
        elif file_size > length:
            self._inner.truncate(fh, path, length)
        elif file_size < length:
            self._inner.upload_range(fh, bytearray(1), path, offset=length - 1)
            self._inner.flush(fh, path)
