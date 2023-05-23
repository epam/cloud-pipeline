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
import os

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
        is_file = not path.endswith(PATH_SEPARATOR)
        for item in items:
            if not item.is_dir and item.storage_class != 'STANDARD' and item.storage_class != 'INTELLIGENT_TIERING':
                path = self._normalize_path(path)
                if restored_paths is None:
                    restored_paths = self._get_restored_paths(path, is_file)
                    folder_restored = self._folder_restored(path, restored_paths)
                if not folder_restored:
                    file_path = path if is_file else path + item.name
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
        return path

    def _get_restored_paths(self, path, is_file=False):
        try:
            response = self._pipe.get_storage_lifecycle(self._bucket, path, is_file)
            items = []
            if not response:
                return set()
            for item in response:
                if item.is_restored():
                    items.append(item.path)
            return set(items)
        except Exception:
            logging.exception('Storage lifecycle retrieving has failed')
            return set()

    @staticmethod
    def _folder_restored(folder_path, storage_lifecycle):
        for path in storage_lifecycle:
            if folder_path.startswith(path):
                return True
        return False


class ArchivedAttributesFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, pipe, bucket):
        """
        Adds storage class attribute

        :param inner: Decorating file system client.
        :param pipe: Cloud Pipeline API client.
        :param bucket: Bucket object.
        """
        super(ArchivedAttributesFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._pipe = pipe
        self._bucket = bucket

    def download_xattrs(self, path):
        try:
            tags = self._inner.download_xattrs(path)
            source_file = self._get_archived_file(path)
            return tags if not source_file else self._add_lifecycle_status_attribute(tags, source_file,
                                                                                     self._get_storage_lifecycle(path))
        except Exception:
            logging.exception('Download archived tags has failed')
            return {}

    def _get_archived_file(self, path):
        items = self._inner.ls(path, depth=1)
        if not items:
            return None
        files = [item for item in items if not item.is_dir and item.storage_class and item.storage_class != 'STANDARD' and item.storage_class != 'INTELLIGENT_TIERING']
        if not files or len(files) != 1:
            return None
        return files[0]

    def _add_lifecycle_status_attribute(self, tags, source_file, lifecycle):
        tag_value = self._get_storage_class_tag_value(lifecycle, source_file.storage_class)
        if tag_value:
            tags.update({'user.system.lifecycle.status': tag_value})
        return tags

    @staticmethod
    def _get_storage_class_tag_value(lifecycle, storage_class):
        if not lifecycle or not storage_class:
            return storage_class
        if lifecycle.is_restored():
            retired_till = (' till ' + lifecycle.restored_till) if lifecycle.restored_till else ''
            return '%s (Restored%s)' % (storage_class, retired_till)
        return storage_class

    def _get_storage_lifecycle(self, path, is_file=True):
        try:
            lifecycle_items = self._pipe.get_storage_lifecycle(self._bucket, path, is_file)
            return None if not lifecycle_items or len(lifecycle_items) == 0 else lifecycle_items[0]
        except Exception:
            logging.exception('Storage last lifecycle retrieving has failed')
            return None
