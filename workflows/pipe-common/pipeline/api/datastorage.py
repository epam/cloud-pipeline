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

from json_parser import JsonParser


class StoragePolicy:
    def __init__(self, versioning=False, backup=None, sts=None, lts=None):
        self.versioning = versioning
        self.backup = int(backup) if backup is not None else backup
        self.sts = int(sts) if sts is not None else sts
        self.lts = int(lts) if lts is not None else lts

    @classmethod
    def from_json(cls, data):
        versioning = JsonParser.get_optional_field(data, 'versioningEnabled', default=False)
        backup = JsonParser.get_optional_field(data, 'backupDuration')
        sts = JsonParser.get_optional_field(data, 'shortTermStorageDuration')
        lts = JsonParser.get_optional_field(data, 'longTermStorageDuration')
        return StoragePolicy(versioning=versioning, backup=backup, sts=sts, lts=lts)


class DataStorage:
    def __init__(self, id, name, description, path, policy, mask, storage_type,
                 owner, region_id, locked, parentId, mount_point, mount_options,
                 region_name=None, sensitive=False):
        self.id = int(id)
        self.name = str(name)
        self.description = str(description)
        self.path = str(path)
        self.policy = policy
        self.mask = int(mask)
        self.storage_type = str(storage_type)
        self.owner = str(owner)
        self.locked = locked
        self.parentId = int(parentId) if parentId is not None else parentId
        self.mount_point = str(mount_point) if mount_point is not None else mount_point
        self.mount_options = str(mount_options) if mount_options is not None else mount_options
        self.region_id = region_id
        self.region_name = region_name
        self.sensitive = sensitive

    @classmethod
    def from_json(cls, data):
        id = JsonParser.get_required_field(data, 'id')
        name = JsonParser.get_required_field(data, 'name')
        path = JsonParser.get_required_field(data, 'path')
        mask = JsonParser.get_required_field(data, 'mask')
        type = JsonParser.get_required_field(data, 'type')
        owner = JsonParser.get_required_field(data, 'owner')
        region_id = JsonParser.get_optional_field(data, 'regionId')
        locked = JsonParser.get_optional_field(data, 'locked', default=False)
        parentId = JsonParser.get_optional_field(data, 'parentFolderId')
        description = JsonParser.get_optional_field(data, 'description')
        mount_options = JsonParser.get_optional_field(data, 'mountOptions')
        mount_point = JsonParser.get_optional_field(data, 'mountPoint')
        region_name = JsonParser.get_optional_field(data, 'regionName')
        sensitive = JsonParser.get_optional_field(data, 'sensitive', default=False)
        policy = StoragePolicy.from_json(data['storagePolicy']) if 'storagePolicy' in data else None
        return DataStorage(id, name, description, path, policy, mask, type, owner, region_id, locked, parentId,
                           mount_point, mount_options, region_name, sensitive=sensitive)


class FileShareMount:
    def __init__(self, id, region_id, mount_root, mount_options, mount_type):
        self.id = id
        self.region_id = region_id
        self.mount_root = mount_root
        self.mount_options = mount_options
        self.mount_type = mount_type

    @classmethod
    def from_json(cls, data):
        id = JsonParser.get_required_field(data, 'id')
        region_id = JsonParser.get_required_field(data, 'regionId')
        mount_root = JsonParser.get_required_field(data, 'mountRoot')
        mount_options = JsonParser.get_optional_field(data, 'mountOptions')
        mount_type = JsonParser.get_required_field(data, 'mountType')

        return FileShareMount(id, region_id, mount_root, mount_options, mount_type)


class DataStorageWithShareMount:
    def __init__(self, storage, file_share_mount):
        self.storage = storage
        self.file_share_mount = file_share_mount

    @classmethod
    def from_json(cls, data):
        storage = DataStorage.from_json(JsonParser.get_required_field(data, 'storage'))
        file_share_mount = None
        if 'shareMount' in data and data['shareMount'] is not None:
            file_share_mount = FileShareMount.from_json(JsonParser.get_optional_field(data, 'shareMount'))

        return DataStorageWithShareMount(storage, file_share_mount)

