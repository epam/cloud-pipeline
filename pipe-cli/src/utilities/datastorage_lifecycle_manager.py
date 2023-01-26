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

from src.api.datastorage_lifecycle import DataStorageLifecycle


class DataStorageLifecycleManager:

    def __init__(self, storage_id, path, is_file):
        self.storage_id = storage_id
        self.path = path
        self.is_file = is_file
        self.items = None
        self.sorted_paths = []

    def find_lifecycle_status(self, file_path):
        if self.items is None:
            self.load_items()
        if not file_path.startswith('/'):
            file_path = '/' + file_path
        for path in self.sorted_paths:
            if path == file_path or file_path.startswith(path):
                item = self.items.get(path)
                if not item:
                    return None, None
                if item.status == 'SUCCEEDED':
                    restored_till = ' till %s' % item.restored_till\
                        if item.restored_till else ''
                    return ' (Restored%s)' % restored_till, item.restore_versions
                else:
                    return None, item.restore_versions
        return None, None

    def load_items(self):
        items = DataStorageLifecycle.load_hierarchy(self.storage_id, self.path, self.is_file)
        self.items = {}
        if not items:
            return
        for item in items:
            self.items.update({item.path: item})
        self.sorted_paths = sorted([item.path for item in items], key=len, reverse=True)
