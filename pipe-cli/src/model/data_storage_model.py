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

from ..utilities import date_utilities
from .data_storage_wrapper_type import WrapperType
from .data_storage_item_model import DataStorageItemModel


class DataStorageModel(DataStorageItemModel):
    def __init__(self):
        super(DataStorageModel, self).__init__()
        self.identifier = None
        self.description = None
        self.delimiter = None
        self.parent_folder_id = None
        self.source_storage_id = None
        self.mask = 0
        self.policy = StoragePolicy()
        self.region = None
        self.mount_status = None
        self.tools_to_mount = set()

    @classmethod
    def load(cls, json):
        instance = DataStorageModel()
        instance.initialize(json)
        instance.identifier = json['id']
        if 'description' in json:
            instance.description = json['description']
        if 'type' in json:
            instance.type = json['type']
        if 'delimiter' in json:
            instance.delimiter = json['delimiter']
        if 'createdDate' in json:
            instance.changed = date_utilities.server_date_representation(json['createdDate'])
        if 'parentFolderId' in json:
            instance.parent_folder_id = json['parentFolderId']
        if 'sourceStorageId' in json:
            instance.source_storage_id = json['source_storage_id']
        if 'mask' in json:
            instance.mask = json['mask']
        instance.policy = StoragePolicy()
        if 'storagePolicy' in json:
            cls.parse_policy(instance.policy, json['storagePolicy'])
        if 'toolsToMount' in json:
            cls.parse_tool_to_mount(instance, json)
        cls.parse_mount_status(instance, json)
        return instance

    @classmethod
    def load_with_region(cls, json, region_data):
        instance = DataStorageModel()
        instance.initialize(json)
        instance.identifier = json['id']
        if 'description' in json:
            instance.description = json['description']
        if 'type' in json:
            instance.type = json['type']
        if 'delimiter' in json:
            instance.delimiter = json['delimiter']
        if 'createdDate' in json:
            instance.changed = date_utilities.server_date_representation(json['createdDate'])
        if 'parentFolderId' in json:
            instance.parent_folder_id = json['parentFolderId']
        if 'mask' in json:
            instance.mask = json['mask']
        if 'regionId' in json:
            instance.region = cls._find_region_code(json['regionId'], region_data)
        instance.policy = StoragePolicy()
        if 'storagePolicy' in json:
            cls.parse_policy(instance.policy, json['storagePolicy'])
        return instance

    @classmethod
    def parse_policy(cls, policy, json):
        if 'versioningEnabled' in json and json['versioningEnabled']:
            policy.versioning_enabled = json['versioningEnabled']
        if 'backupDuration' in json:
            policy.backup_duration = int(json['backupDuration'])
        if 'shortTermStorageDuration' in json:
            policy.sts_duration = int(json['shortTermStorageDuration'])
        if 'longTermStorageDuration' in json:
            policy.lts_duration = int(json['longTermStorageDuration'])

    @classmethod
    def parse_tool_to_mount(cls, instance, json):
        tools_to_mount = json['toolsToMount']
        for tool in tools_to_mount:
            tool_name = '{}/{}'.format(tool['registry'], tool['image'])
            if 'versions' in tool:
                tool_versions = tool['versions']
                if tool_versions:
                    instance.tools_to_mount.update(['{}:{}'.format(tool_name, version['version'])
                                                    for version in tool_versions])
                    continue
            instance.tools_to_mount.add(tool_name)

    @classmethod
    def parse_mount_status(cls, instance, json):
        is_mount_disabled = json['mountDisabled'] if 'mountDisabled' in json else False
        if is_mount_disabled:
            instance.mount_status = 'DISABLED'
        elif instance.type == 'NFS' and 'mountStatus' in json:
            instance.mount_status = json['mountStatus']
        else:
            instance.mount_status = 'ACTIVE'

    def root_path(self):
        if self.type is not None and self.type in WrapperType.cloud_types():
            return '{}://{}'.format(WrapperType.cloud_scheme(self.type), self.path)
        return self.path

    def absolute_path(self, path):
        if self.delimiter is not None:
            return '{}{}{}'.format(self.root_path(), self.delimiter, path)
        else:
            return '{}/{}'.format(self.root_path(), path)

    @staticmethod
    def _find_region_code(region_id, region_data):
        for region in region_data:
            if int(region.get('id', 0)) == int(region_id):
                return region.get('regionId', None)
        return None


class StoragePolicy(object):
    def __init__(self):
        self.versioning_enabled = False
        self.backup_duration = None
        self.sts_duration = None
        self.lts_duration = None
