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
from prettytable import prettytable

from src.api.entity import Entity
from src.model.datastorage_usage_model import StorageUsage

from src.api.data_storage import DataStorage
from src.model.data_storage_wrapper import DataStorageWrapper
from src.utilities.user_operations_manager import UserOperationsManager


class DataUsageCommand(object):

    def __init__(self, storage_name, relative_path, depth, perform_on_cloud, output_mode, generation, size_format):
        self.storage_name = storage_name
        self.relative_path = relative_path
        self.depth = depth
        self.perform_on_cloud = perform_on_cloud
        self.output_mode = output_mode
        self.generation = generation
        self.size_format = size_format
        self.show_archive = True

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

        user_manager, storage_owner = self._validate_generation_permissions()
        self._validate_storage_archive_permissions(user_manager, storage_owner)

        return True

    def _validate_generation_permissions(self):
        """
        --generation=current - allowed for all users
        --generation=old - allowed for admin or owner users
        --generation=all - allowed for all users. If user is not ADMIN or OWNER the current versions shall be show.
        """
        if DuOutput.is_current(self.generation):
            return None, None
        user_manager = UserOperationsManager()
        if user_manager.is_admin():
            return user_manager, None
        entity = Entity.load_by_id_or_name(self.storage_name, 'DATA_STORAGE')
        storage_owner = entity.get('owner')
        if user_manager.is_owner(storage_owner):
            return user_manager, storage_owner
        if DuOutput.is_old(self.generation):
            raise RuntimeError('The old versions loading available for ADMIN or storage OWNER only.')
        self.generation = 'current'
        return user_manager, storage_owner

    def _validate_storage_archive_permissions(self, user_manager, storage_owner):
        if not user_manager:
            user_manager = UserOperationsManager()
        self.show_archive = user_manager.has_storage_archive_permissions(self.storage_name, owner=storage_owner)


class DataUsageHelper(object):

    def fetch_data(self, du_command):
        storage = None
        if du_command.storage_name:
            storage = DataStorage.get(du_command.storage_name)
            if storage is None:
                raise RuntimeError('Storage "{}" was not found'.format(du_command.storage_name))
            if storage.type.lower() == 'nfs':
                if du_command.depth:
                    raise RuntimeError('--depth option is not supported for NFS storages')

        result = []
        storage_to_fetch = [storage] if storage else list(DataStorage.list())
        for _storage in storage_to_fetch:
            if du_command.perform_on_cloud and _storage.type != "nfs":
                summary = self.get_cloud_storage_summary(_storage, du_command.relative_path, du_command.depth)
            else:
                summary = self.get_storage_summary(_storage.path, du_command.relative_path)
            for path_summary in summary:
                if path_summary[0] and path_summary[1]:
                    result.append(path_summary)
        return result

    def get_storage_summary(self, storage_name, relative_path):
        path, usage = self.__get_storage_usage(storage_name, relative_path)
        return [[path, usage]]

    def get_cloud_storage_summary(self, root_bucket, relative_path, depth=None):
        items = []
        if depth:
            result_tree = self.__get_summary_with_depth(root_bucket, relative_path, depth)
            for node in sorted(result_tree.nodes):
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


class DuOutput(object):

    STANDARD_TIER = "STANDARD"

    @staticmethod
    def __mb():
        return ['M', 'MB', 'Mb']

    @staticmethod
    def __gb():
        return ['G', 'GB', 'Gb']

    @staticmethod
    def __kb():
        return ['K', 'KB', 'Kb']

    @staticmethod
    def possible_size_types():
        return DuOutput.__mb() + DuOutput.__gb() + DuOutput.__kb()

    @staticmethod
    def __brief():
        return ['brief', 'b', 'B']

    @staticmethod
    def __full():
        return ['full', 'f', 'F']

    @staticmethod
    def possible_modes():
        return DuOutput.__brief() + DuOutput.__full()

    @staticmethod
    def __all():
        return ['all', 'a', 'A']

    @staticmethod
    def is_all(generation):
        return generation in DuOutput.__all()

    @staticmethod
    def __current():
        return ['current', 'c', 'C']

    @staticmethod
    def is_current(generation):
        return generation in DuOutput.__current()

    @staticmethod
    def __old():
        return ['old', 'o', 'O']

    @staticmethod
    def is_old(generation):
        return generation in DuOutput.__old()

    @staticmethod
    def possible_generations():
        return DuOutput.__all() + DuOutput.__current() + DuOutput.__old()

    @staticmethod
    def format_table(du_command, du_leafs):
        def configure_table(_header):
            _table = prettytable.PrettyTable()
            _table.field_names = _header
            _table.align = "r"
            _table.align['Storage'] = 'l'
            _table.border = False
            _table.padding_width = 2
            return _table

        def build_header():
            _header = [
                "Storage", "Files count", "Size (%s)" % DuOutput.pretty_size(du_command.size_format)
            ]
            if not du_command.show_archive:
                return _header
            if du_command.output_mode in DuOutput.__brief():
                _header.append("Archive size (%s)" % DuOutput.pretty_size(du_command.size_format))
            else:
                possible_additional_columns = set()
                for _item in du_leafs:
                    possible_additional_columns.update(filter(lambda t: t != DuOutput.STANDARD_TIER, _item[1].get_tiers()))
                for _column in possible_additional_columns:
                    if _column not in _header:
                        _header.append(_column + " (%s)" % DuOutput.pretty_size(du_command.size_format))
            return _header

        def build_row(_header, _item):
            def _get_size(tier_usage):
                if not tier_usage:
                    return 0
                if DuOutput.is_current(du_command.generation):
                    return tier_usage.size
                elif DuOutput.is_old(du_command.generation):
                    return tier_usage.old_versions_size
                else:
                    return tier_usage.size + tier_usage.old_versions_size

            item_usage = _item[1]
            _row = [_item[0], item_usage.get_total_count()]
            usage_by_tiers = item_usage.get_usage()
            _row.append(
                DuOutput.pretty_size_value(
                    _get_size(usage_by_tiers.get(DuOutput.STANDARD_TIER)), du_command.size_format
                )
            )

            if not du_command.show_archive:
                return _row

            if du_command.output_mode in DuOutput.__brief():
                archive_size = 0
                for archive_tier in filter(lambda t: t != DuOutput.STANDARD_TIER, item_usage.get_tiers()):
                    archive_size += _get_size(usage_by_tiers.get(archive_tier))
                _row.append(DuOutput.pretty_size_value(archive_size, du_command.size_format))
            else:
                for _header_column in _header[3:]:
                    _row.append(
                        DuOutput.pretty_size_value(
                            _get_size(
                                next((t for t in usage_by_tiers.values() if _header_column.startswith(t.tier)), None)
                            ),
                            du_command.size_format
                        )
                    )
            return _row

        header = build_header()
        table = configure_table(header)
        for item in du_leafs:
            table.add_row(build_row(header, item))
        return table

    @staticmethod
    def pretty_size(size_format):
        if size_format not in DuOutput.possible_size_types():
            raise RuntimeError("Type '%s' is not supported yet" % size_format)
        if size_format in DuOutput.__gb():
            return 'Gb'
        if size_format in DuOutput.__mb():
            return 'Mb'
        if size_format in DuOutput.__kb():
            return 'Kb'

    @staticmethod
    def pretty_value(usage, output_mode, measurement_type):

        def _build_pretty_value(value, optional_value):
            if output_mode in DuOutput.__full():
                _prettified = DuOutput.pretty_size_value(value, measurement_type)
                if optional_value > 0:
                    _prettified += " ({})".format(DuOutput.pretty_size_value(optional_value, measurement_type))
                return _prettified
            else:
                return DuOutput.pretty_size_value(value, measurement_type)

        prettified_data = {}
        if output_mode in DuOutput.__full():
            prettified_data["Total size"] = \
                _build_pretty_value(usage.get_total_size(), usage.get_total_old_versions_size())
            for storage_tier, stats in usage.get_usage().items():
                prettified_data[storage_tier] = _build_pretty_value(stats.size, stats.old_versions_size)
        else:
            prettified_data["Total size"] = _build_pretty_value(usage.get_total_size(), 0)
        return prettified_data

    @staticmethod
    def pretty_size_value(value, measurement_type):
        if measurement_type in DuOutput.__gb():
            return DuOutput.__to_string(value / float(1 << 30))
        if measurement_type in DuOutput.__mb():
            return DuOutput.__to_string(value / float(1 << 20))
        if measurement_type in DuOutput.__kb():
            return DuOutput.__to_string(value / float(1 << 10))

    @staticmethod
    def __to_string(value):
        return "%.1f" % value
