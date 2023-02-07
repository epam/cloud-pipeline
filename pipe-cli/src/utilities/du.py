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

from src.model.datastorage_usage_model import StorageUsage

from src.api.data_storage import DataStorage
from src.model.data_storage_wrapper import DataStorageWrapper


class DataUsageCommand(object):
    def __init__(self, storage_name, relative_path, depth, perform_on_cloud, output_mode, generation, size_format):
        self.storage_name = storage_name
        self.relative_path = relative_path
        self.depth = depth
        self.perform_on_cloud = perform_on_cloud
        self.output_mode = output_mode
        self.generation = generation
        self.size_format = size_format

    def validate(self):
        if self.depth and not self.storage_name:
            click.echo("Error: bucket path must be provided with --depth option", err=True)
            return False

        if self.depth and not self.perform_on_cloud:
            click.echo("Using --cloud, because --depth is used", err=True)
            self.perform_on_cloud = True

        if self.storage_name:
            if not self.relative_path or self.relative_path == "/":
                self.relative_path = ''

        if not self.storage_name and self.relative_path:
            raise RuntimeError('--relative-path could be used only with storage')


class DataUsageHelper(object):

    def fetch_data(self, du_command):
        storage = DataStorage.get(du_command.storage_name)
        if storage is None:
            raise RuntimeError('Storage "{}" was not found'.format(du_command.storage_name))
        if storage.type.lower() == 'nfs':
            if du_command.depth:
                raise RuntimeError('--depth option is not supported for NFS storages')

        storage_to_fetch = [storage] if storage else list(DataStorage.list())
        for _storage in storage_to_fetch:
            if du_command.perform_on_cloud and _storage.type != "nfs":
                return self.get_cloud_storage_summary(_storage.path, du_command.relative_path, du_command.depth)
            else:
                return self.get_storage_summary(_storage.path, du_command.relative_path)

    def get_storage_summary(self, storage_name, relative_path):
        path, usage = self.__get_storage_usage(storage_name, relative_path)
        return [path, usage]

    def get_cloud_storage_summary(self, root_bucket, relative_path, depth=None):
        items = []
        if depth:
            result_tree = self.__get_summary_with_depth(root_bucket, relative_path, depth)
            for node in result_tree.nodes:
                usage = result_tree[node].data
                items.append([node, usage])
        else:
            path, usage = self.__get_summary(root_bucket, relative_path)
            items.append([path, usage])
        return items

    @classmethod
    def __get_storage_usage(cls, storage_name, relative_path):
        try:
            usage = DataStorage.get_storage_usage(storage_name, relative_path)
        except Exception as e:
            click.echo("Failed to load usage for datastorage '%s'. Cause: %s" % (storage_name, str(e)), err=True)
            return None, None
        result = StorageUsage()
        for tier, stats in usage.get("usage", {}).items():
            result.populate(
                tier,
                stats.get("size", 0), stats.get("count", 0),
                stats.get("effectiveSize", 0), stats.get("effectiveCount", 0),
                stats.get("oldVersionsSize", 0), stats.get("oldVersionsEffectiveSize", 0)
            )
        if not relative_path:
            path = storage_name
        else:
            path = "/".join([storage_name, relative_path])
        return path, result

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
