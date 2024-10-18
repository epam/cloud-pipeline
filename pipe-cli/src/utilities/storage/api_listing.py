# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import os

from src.utilities.storage.common import AbstractListingManager, StorageOperations
from src.api.data_storage import DataStorage


class ApiStorageListingManager(AbstractListingManager):
    """
    This class provides an ability to load storage items directly from API
    """

    PAGE_SIZE = os.getenv('CP_CLI_STORAGE_LIST_API_PAGE_SIZE', 1000)

    def __init__(self, storage):
        self._storage = storage

    def list_items(self, relative_path=None, recursive=False, page_size=StorageOperations.DEFAULT_PAGE_SIZE,
                   show_all=False, show_archive=False):
        raise NotImplementedError()

    def list_paging_items(self, relative_path=None, recursive=False, page_size=StorageOperations.DEFAULT_PAGE_SIZE,
                          start_token=None, show_archive=False):
        raise NotImplementedError()

    def get_summary_with_depth(self, max_depth, relative_path=None):
        raise NotImplementedError()

    def get_listing_with_depth(self, max_depth, relative_path=None):
        for item in self.__list_api_storage_folders(relative_path, max_depth):
            yield item

    def get_summary(self, relative_path=None):
        raise NotImplementedError()

    def get_file_tags(self, relative_path):
        raise NotImplementedError()

    def __process_api_storage_items(self, items, current_depth, max_depth):
        if not items:
            return
        for item in items:
            if item.get('type') == 'Folder':
                folder_prefix = item.get('path')
                if current_depth == max_depth:
                    yield folder_prefix
                else:
                    for child in self.__list_api_storage_folders(folder_prefix, max_depth, current_depth + 1):
                        yield child

    def __list_api_items_page(self, relative_path, next_page_marker=None):
        response = DataStorage.list_items_page(self._storage.identifier, self.PAGE_SIZE, path=relative_path,
                                               marker=next_page_marker)
        return response.get('results'), response.get('nextPageMarker')

    def __list_api_storage_folders(self, relative_path, max_depth, current_depth=1):
        if current_depth > max_depth:
            return

        yield relative_path

        items, next_page_marker = self.__list_api_items_page(relative_path)
        for item in self.__process_api_storage_items(items, current_depth, max_depth):
            yield item

        while next_page_marker is not None:
            items, next_page_marker = self.__list_api_items_page(relative_path, next_page_marker)
            for item in self.__process_api_storage_items(items, current_depth, max_depth):
                yield item
