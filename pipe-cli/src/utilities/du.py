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
import click

from src.utilities.du_format_type import DuFormatType

from src.api.data_storage import DataStorage
from src.model.data_storage_wrapper import DataStorageWrapper


class DataUsageHelper(object):

    def __init__(self, format):
        self.format = format

    def get_total_summary(self):
        items = []
        storages = list(DataStorage.list())
        if not storages:
            return None
        for storage in storages:
            path, count, size = self.__get_storage_usage(storage.path, None)
            if path is not None:
                items.append([path, count, DuFormatType.pretty_value(size, self.format)])
        return items

    def get_cloud_storage_summary(self, root_bucket, relative_path, depth=None):
        items = []
        if depth:
            result_tree = self.__get_summary_with_depth(root_bucket, relative_path, depth)
            for node in result_tree.nodes:
                size = result_tree[node].data.get_size()
                count = result_tree[node].data.get_count()
                items.append([node, count, DuFormatType.pretty_value(size, self.format)])
        else:
            path, count, size = self.__get_summary(root_bucket, relative_path)
            items.append([path, count, DuFormatType.pretty_value(size, self.format)])
        return items

    def get_nfs_storage_summary(self, storage_name, relative_path):
        path, count, size = self.__get_storage_usage(storage_name, relative_path)
        return [path, count, DuFormatType.pretty_value(size, self.format)]

    @classmethod
    def __get_storage_usage(cls, storage_name, relative_path):
        try:
            usage = DataStorage.get_storage_usage(storage_name, relative_path)
        except Exception as e:
            click.echo("Failed to load usage for datastorage '%s'. Cause: %s" % (storage_name, e.message), err=True)
            return None, None, None
        size = 0
        count = 0
        if 'size' in usage:
            size = usage['size']
        if 'count' in usage:
            count = usage['count']
        if not relative_path:
            path = storage_name
        else:
            path = "/".join([storage_name, relative_path])
        return path, count, size

    @classmethod
    def __get_summary_with_depth(cls, root_bucket, relative_path, depth):
        wrapper = DataStorageWrapper.get_cloud_wrapper_for_bucket(root_bucket, relative_path)
        manager = wrapper.get_list_manager(show_versions=False)
        return manager.get_summary_with_depth(depth, relative_path)

    @classmethod
    def __get_summary(cls, root_bucket, relative_path):
        wrapper = DataStorageWrapper.get_cloud_wrapper_for_bucket(root_bucket, relative_path)
        manager = wrapper.get_list_manager(show_versions=False)
        return manager.get_summary(relative_path)
