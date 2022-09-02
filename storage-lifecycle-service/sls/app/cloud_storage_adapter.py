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
        self.logger = logger
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
        storage_is_versioned = storage.policy.versioning if storage.policy else False
        files = self.cloud_operations[storage.storage_type].list_objects_by_prefix(storage_cloud_identifier,
                                                                                   storage_path_prefix + prefix,
                                                                                   list_versions=storage_is_versioned)
        for file in files:
            if storage_path_prefix and file.path.startswith(storage_path_prefix):
                file.path = file.path.replace(storage_path_prefix, "", 1)
        return files

    def tag_files_to_transit(self, storage, files, storage_class, transit_operation_id):
        try:
            storage_cloud_identifier, storage_path_prefix = self._parse_storage_path(storage)
            for file in files:
                if storage_path_prefix:
                    file.path = storage_path_prefix + file.path
            return self.cloud_operations[storage.storage_type].tag_files_to_transit(
                storage_cloud_identifier, files, storage_class, storage.region_name, transit_operation_id)
        except Exception as e:
            self.logger.log("Something went wrong when try to tag files from {}. Cause: {}".format(storage.path, e))
            return False

    def run_restore_action(self, storage, action, restore_operation_id):
        try:
            storage_cloud_identifier, storage_path_prefix = self._parse_storage_path(storage)
            files = self.cloud_operations[storage.storage_type].list_objects_by_prefix(
                storage_cloud_identifier, storage_path_prefix + action.path,
                convert_paths=False, list_versions=action.restore_versions
            )
            self.logger.log("Storage: {}. Action: {}. Path: {}. Listed '{}' files to possible restore."
                            .format(storage.id, action.action_id, action.path, len(files)))
            return self.cloud_operations[storage.storage_type].run_files_restore(
                storage_cloud_identifier, files, action.days, action.restore_mode,
                storage.region_name, restore_operation_id
            )
        except Exception as e:
            self.logger.log("Storage: {}. Path: {}. Problem with restoring files, cause: {}"
                            .format(storage.id, action.path, e))
            return {
                "status": False,
                "reason": e,
                "value": None
            }

    def check_restore_action(self, storage, action):
        try:
            storage_cloud_identifier, storage_path_prefix = self._parse_storage_path(storage)
            files = self.cloud_operations[storage.storage_type].list_objects_by_prefix(
                storage_cloud_identifier, storage_path_prefix + action.path,
                convert_paths=False, list_versions=action.restore_versions
            )
            return self.cloud_operations[storage.storage_type].check_files_restore(
                storage_cloud_identifier, files, action.updated, action.restore_mode
            )
        except Exception as e:
            self.logger.log("Problem with cheking restoring files, cause: {}".format(e))
            return {
                "status": False,
                "reason": e,
                "value": None
            }

    @staticmethod
    def fetch_storage_lifecycle_service_config(data_source):
        config_preference = data_source.load_preference("storage.lifecycle.service.cloud.config")
        if not config_preference or "value" not in config_preference:
            raise RuntimeError("storage.lifecycle.service.cloud.config is not defined in Cloud-Pipeline env!")
        if S3_TYPE not in config_preference["value"]:
            raise RuntimeError("storage.lifecycle.service.cloud.config doesn't contain S3 section!")
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
