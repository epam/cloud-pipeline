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

from collections import namedtuple

from abc import ABCMeta, abstractmethod

File = namedtuple('File', ['name', 'size', 'mtime', 'ctime', 'contenttype', 'is_dir'])


class FileSystemClient:
    __metaclass__ = ABCMeta

    @abstractmethod
    def is_available(self):
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
