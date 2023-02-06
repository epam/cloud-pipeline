# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

from pipefuse.fsclient import FileSystemClientDecorator


class StorageClassFilterFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, classes):
        """
        Filters files that have specific storage class.

        :param inner: Decorating file system client.
        :param classes: Storage classes that shall be filtered.
        """
        super(StorageClassFilterFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._storage_classes = classes

    def ls(self, path, depth=1):
        return filter(self._filter_storage_classes, self._inner.ls(path, depth))

    def _filter_storage_classes(self, item):
        if item.is_dir:
            return True
        return item.storage_class not in self._storage_classes
