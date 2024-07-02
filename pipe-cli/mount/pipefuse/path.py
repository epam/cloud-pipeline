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

from pipefuse import fuseutils
from pipefuse.fsclient import FileSystemClientDecorator


class PathExpandingStorageFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, root_path):
        """
        Path expanding storage file system client.

        Neglects the difference between regular and prefixed data storages.

        :param inner: Decorating file system client.
        :param root_path: Root data storage path prefix.
        """
        super(PathExpandingStorageFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._root_path = root_path
        self._is_read_only = False
        self._delimiter = '/'

    def exists(self, path):
        expanded_path = self._expand_path(path)
        return self._inner.exists(expanded_path)

    def attrs(self, path):
        expanded_path = self._expand_path(path)
        return self._inner.attrs(expanded_path)

    def ls(self, path, depth=1):
        expanded_path = self._expand_path(path)
        return self._inner.ls(expanded_path, depth)

    def upload(self, buf, path):
        expanded_path = self._expand_path(path)
        self._inner.upload(buf, expanded_path)

    def delete(self, path):
        expanded_path = self._expand_path(path)
        self._inner.delete(expanded_path)

    def mv(self, old_path, path):
        expanded_old_path = self._expand_path(old_path)
        expanded_path = self._expand_path(path)
        self._inner.mv(expanded_old_path, expanded_path)

    def mkdir(self, path):
        expanded_path = self._expand_path(path)
        self._inner.mkdir(expanded_path)

    def rmdir(self, path):
        expanded_path = self._expand_path(path)
        self._inner.rmdir(expanded_path)

    def download_range(self, fh, buf, path, offset=0, length=0):
        expanded_path = self._expand_path(path)
        self._inner.download_range(fh, buf, expanded_path, offset, length)

    def upload_range(self, fh, buf, path, offset=0):
        expanded_path = self._expand_path(path)
        self._inner.upload_range(fh, buf, expanded_path, offset)

    def flush(self, fh, path):
        expanded_path = self._expand_path(path)
        self._inner.flush(fh, expanded_path)

    def truncate(self, fh, path, length):
        expanded_path = self._expand_path(path)
        self._inner.truncate(fh, expanded_path, length)

    def download_xattrs(self, path):
        expanded_path = self._expand_path(path)
        return self._inner.download_xattrs(expanded_path)

    def upload_xattrs(self, path, xattrs):
        expanded_path = self._expand_path(path)
        self._inner.upload_xattrs(expanded_path, xattrs)

    def upload_xattr(self, path, name, value):
        expanded_path = self._expand_path(path)
        self._inner.upload_xattr(expanded_path, name, value)

    def remove_xattrs(self, path):
        expanded_path = self._expand_path(path)
        self._inner.remove_xattrs(expanded_path)

    def remove_xattr(self, path, name):
        expanded_path = self._expand_path(path)
        self._inner.remove_xattr(expanded_path, name)

    def _expand_path(self, path):
        return fuseutils.join_path_with_delimiter(self._root_path, path) \
            if self._root_path else path
