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

from collections import namedtuple

from abc import ABCMeta, abstractmethod

File = namedtuple('File', ['name', 'size', 'mtime', 'ctime', 'contenttype', 'is_dir'])


class FileSystemClient:
    __metaclass__ = ABCMeta

    @abstractmethod
    def is_available(self):
        pass

    @abstractmethod
    def is_read_only(self):
        pass

    @abstractmethod
    def exists(self, path):
        pass

    def attrs(self, path):
        """
        Returns a single file or folder by the given path.

        :param path: Relative path to a single file or folder.
        :return: File, folder or None if the given path doesn't exist.
        """
        listing = self.ls(path, depth=0)
        return listing[0] or None

    @abstractmethod
    def ls(self, path, depth):
        """
        Returns all files and folder for the given path.

        Both file and folder paths are supported.

        :param path: Relative path to list its files and folders.
        :param depth: If set to negative value then recursive listing is returned.
        :return: List of files and folders.
        """
        pass

    @abstractmethod
    def upload(self, buf, path):
        pass

    @abstractmethod
    def delete(self, path):
        pass

    @abstractmethod
    def mv(self, old_path, path):
        pass

    @abstractmethod
    def mkdir(self, path):
        pass

    @abstractmethod
    def rmdir(self, path):
        pass

    @abstractmethod
    def download_range(self, fh, buf, path, offset, length):
        """
        Downloads a range of data by the given path into the given buffer.

        :param fh: File handle.
        :param buf: Buffer to write downloaded data to.
        :param path: Path to read data from.
        :param offset: Downloading data offset.
        :param length: Downloading data length.
        """
        pass

    @abstractmethod
    def upload_range(self, fh, buf, path, offset):
        """
        Uploads the given buffer to the given path.

        :param fh: File handle.
        :param buf: Buffer to read uploading data from.
        :param path: Path to write data to.
        :param offset: Uploading data offset.
        """
        pass

    def flush(self, fh, path):
        """
        Flushes downloading or uploading data for the given path.

        :param fh: File handle.
        :param path: Path to flush data for.
        """
        pass

    def utimens(self, path, times=None):
        pass

    def truncate(self, fh, path, length):
        """
        Truncates the given path to the given length.
        The operation can be performed to either decrease or increase the path length.

        :param fh: File handle.
        :param path: Path to truncate.
        :param length: Target size of the truncating path.
        """
        pass


class FileSystemClientDecorator(FileSystemClient):

    def __init__(self, inner):
        """
        File system client decorators base class.

        :param inner: Decorating file system client.
        """
        self._inner = inner

    def is_available(self):
        return self._inner.is_available()

    def is_read_only(self):
        return self._inner.is_read_only()

    def exists(self, path):
        return self._inner.exists(path)

    def attrs(self, path):
        return self._inner.attrs(path)

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
        self._inner.download_range(fh, buf, path, offset, length)

    def upload_range(self, fh, buf, path, offset=0):
        self._inner.upload_range(fh, buf, path, offset)

    def flush(self, fh, path):
        self._inner.flush(fh, path)

    def truncate(self, fh, path, length):
        self._inner.truncate(fh, path, length)

    def __getattr__(self, name):
        if hasattr(self._inner, name):
            return getattr(self._inner, name)
        else:
            raise RuntimeError('File system client %s and its inner client %s don\'t have %s attribute.' % (type(self), type(self._inner), name))
