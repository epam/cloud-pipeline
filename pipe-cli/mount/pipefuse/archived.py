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
import collections
import logging

from pipefuse.fsclient import FileSystemClientDecorator

PATH_SEPARATOR = '/'


class ArchivedFilesFilterFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, pipe, bucket):
        """
        Filters files that have GLACIER or DEEP_ARCHIVE storage class and not restored.

        :param inner: Decorating file system client.
        :param pipe: Cloud Pipeline API client.
        :param bucket: Bucket object.
        """
        super(ArchivedFilesFilterFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._pipe = pipe
        self._bucket = bucket

    def ls(self, path, depth=1):
        items = self._inner.ls(path, depth)
        storage_lifecycle = None
        result = []
        for item in items:
            if item.storage_class is not None and item.storage_class != 'STANDARD':
                path = self._normalize_path(path)
                if storage_lifecycle is None:
                    storage_lifecycle = self._get_storage_lifecycle(path)
                file_path = path + item.name
                if not self._file_restored(file_path, storage_lifecycle):
                    continue
            result.append(item)
        return result

    @staticmethod
    def _normalize_path(path):
        if not path:
            return PATH_SEPARATOR
        if not path.startswith(PATH_SEPARATOR):
            path = PATH_SEPARATOR + path
        if path != PATH_SEPARATOR and not path.endswith(PATH_SEPARATOR):
            path = path + PATH_SEPARATOR
        return path

    @staticmethod
    def _file_restored(file_path, storage_lifecycle):
        for path, item in storage_lifecycle:
            if path == file_path or file_path.startswith(path):
                if not item:
                    return False
                return item.status == 'SUCCEEDED'
        return False

    def _get_storage_lifecycle(self, path):
        try:
            response = self._pipe.get_storage_lifecycle(self._bucket, path)
            items = {}
            if not response:
                return {}
            for item in response:
                items.update({item.path: item})
            sorted_paths = sorted([item.path for item in response], key=len, reverse=True)
            sorted_results = collections.OrderedDict()
            for path in sorted_paths:
                sorted_results.update({path: items.get(path)})
            return sorted_results
        except Exception as e:
            logging.info(e)
            return {}
