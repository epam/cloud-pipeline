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
        restored_paths = None
        result = []
        folder_restored = False
        for item in items:
            if not item.is_dir and item.storage_class != 'STANDARD':
                path = self._normalize_path(path)
                if restored_paths is None:
                    restored_paths = self._get_restored_paths(path)
                    folder_restored = self._folder_restored(path, restored_paths)
                if not folder_restored:
                    file_path = path + item.name
                    if not restored_paths.__contains__(file_path):
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

    def _get_restored_paths(self, path):
        try:
            response = self._pipe.get_storage_lifecycle(self._bucket, path)
            items = []
            if not response:
                return set()
            for item in response:
                if item.status and item.status == 'SUCCEEDED':
                    items.append(item.path)
            return set(items)
        except Exception as e:
            logging.info(e)
            return set()

    @staticmethod
    def _folder_restored(folder_path, storage_lifecycle):
        for path in storage_lifecycle:
            if folder_path.startswith(path):
                return True
        return False
