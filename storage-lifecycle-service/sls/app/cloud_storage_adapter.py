#  Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
#  #
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#  #
#     http://www.apache.org/licenses/LICENSE-2.0
#  #
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
import json

from sls.cloud.cloud import S3StorageOperations

S3_TYPE = "S3"


class PlatformToCloudOperationsAdapter:

    def __init__(self, data_source, logger):
        storage_lifecycle_service_config = self.fetch_storage_lifecycle_service_config(data_source)
        self.cloud_operations = {
            S3_TYPE: S3StorageOperations(storage_lifecycle_service_config[S3_TYPE], logger)
        }

    def is_support(self, storage):
        return storage.storage_type in self.cloud_operations

    def prepare_bucket_if_needed(self, storage):
        storage_cloud_identifier, _ = self._parse_storage_path(storage)
        self.cloud_operations[storage.storage_type].prepare_bucket_if_needed(storage_cloud_identifier)

    def list_objects_by_prefix(self, storage, prefix):
        storage_cloud_identifier, storage_path_prefix = self._parse_storage_path(storage)
        files = self.cloud_operations[storage.storage_type].list_objects_by_prefix(storage_cloud_identifier,
                                                                                   storage_path_prefix + prefix)
        for file in files:
            if storage_path_prefix and file.path.startswith(storage_path_prefix):
                file.path = file.path.replace(storage_path_prefix, "", 1)
        return files

    def tag_files_to_transit(self, storage, files, storage_class, transit_id):
        storage_cloud_identifier, storage_path_prefix = self._parse_storage_path(storage)
        for file in files:
            if storage_path_prefix:
                file.path = storage_path_prefix + file.path
        return self.cloud_operations[storage.storage_type].tag_files_to_transit(
            storage_cloud_identifier, files, storage_class, storage.region_name, transit_id)

    @staticmethod
    def fetch_storage_lifecycle_service_config(data_source):
        config_preference = data_source.load_preference("storage.lifecycle.service.cloud.config")
        if not config_preference or "value" not in config_preference:
            raise RuntimeError("storage.lifecycle.service.cloud.config is not defined in Cloud-Pipeline env!")
        return json.loads(config_preference["value"])

    @staticmethod
    def _parse_storage_path(storage):
        split_storage_path = storage.path.rsplit("/", 1)
        if len(split_storage_path) == 1:
            return split_storage_path[0], ""
        else:
            return split_storage_path[0], "/{}".format(split_storage_path[1])

    # ONLY for testing purposes
    @classmethod
    def _from_provided_cloud_operations(cls, cloud_operations):
        obj = cls.__new__(cls)
        super(PlatformToCloudOperationsAdapter, obj).__init__()
        obj.cloud_operations = cloud_operations
        return obj
