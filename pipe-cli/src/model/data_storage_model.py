# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
from .data_storage_item_model import DataStorageItemModel


class DataStorageModel(DataStorageItemModel):
    def __init__(self):
        super(DataStorageModel, self).__init__()
        self.identifier = None
        self.description = None
        self.delimiter = None
        self.parent_folder_id = None
        self.mask = 0
        self.policy = StoragePolicy()

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
        if 'mask' in json:
            instance.mask = json['mask']
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

    def root_path(self):
        if self.type is not None and self.type.lower() == 's3':
            return 's3://{}'.format(self.path)
        return self.path

    def absolute_path(self, path):
        if self.delimiter is not None:
            return '{}{}{}'.format(self.root_path(), self.delimiter, path)
        else:
            return '{}/{}'.format(self.root_path(), path)


class StoragePolicy(object):
    def __init__(self):
        self.versioning_enabled = False
        self.backup_duration = None
        self.sts_duration = None
        self.lts_duration = None
