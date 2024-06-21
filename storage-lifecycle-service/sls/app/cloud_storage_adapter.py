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
from sls.cloud.cloud import CloudPipelineStorageContainer
from sls.cloud.s3_cloud import S3StorageOperations
from sls.util import path_utils

# This property contains key of the metadata tag of the storage
# If the metadata with such key is present on the storage,
# StorageLifecycleArchivingSynchronizer will skip such storage
SKIP_ARCHIVING_TAG = "storage_skip_archiving_tag"


def _verify_s3_sls_properties(sls_properties, logger):

    def _verify_or_default_int_value(key, default):
        resulted_value = default
        if key in properties:
            try:
                resulted_value = int(properties[key])
            except BaseException:
                logger.log("Cannot parse int value from batch_operation_job_poll_status_retry_count, using default")
                resulted_value = default

        if resulted_value < 1:
            logger.log(
                "Value {} within S3 storage lifecycle service cloud configuration should be > 1".format(key))
            resulted_value = None
        return resulted_value

    if not sls_properties or not sls_properties.properties:
        logger(
            "There is no configured Storage Lifecycle Service properties!")
        return False

    properties = sls_properties.properties
    if "batch_operation_job_aws_account_id" not in properties:
        logger.log(
            "Please provide batch_operation_job_aws_account_id within S3 storage lifecycle service cloud configuration")
        return False

    if "batch_operation_job_report_bucket" not in properties:
        logger.log(
            "Please provide batch_operation_job_report_bucket within S3 storage lifecycle service cloud configuration")
        return False

    if "batch_operation_job_role_arn" not in properties:
        logger.log(
            "Please provide batch_operation_job_role_arn within S3 storage lifecycle service cloud configuration")
        return False

    if "batch_operation_job_report_bucket_prefix" not in properties:
        properties["batch_operation_job_report_bucket_prefix"] = "cp_storage_lifecycle_tagging"
    properties["batch_operation_job_report_bucket_prefix"] = properties.get("batch_operation_job_report_bucket_prefix").strip(
        "/")

    properties["batch_operation_job_poll_status_retry_count"] = \
        _verify_or_default_int_value("batch_operation_job_poll_status_retry_count", 30)
    if not properties["batch_operation_job_poll_status_retry_count"]:
        return False

    properties["batch_operation_job_poll_status_sleep_sec"] = \
        _verify_or_default_int_value("batch_operation_job_poll_status_sleep_sec", 5)
    if not properties["batch_operation_job_poll_status_sleep_sec"]:
        return False

    return True


S3_TYPE = "S3"


sls_properties_verifiers = {
    "AWS": _verify_s3_sls_properties
}


class PlatformToCloudOperationsAdapter:

    def __init__(self, pipeline_api_client, logger, overridden_cloud_operations=None):
        self.regions_by_id = {}
        self.logger = logger
        self.pipeline_api_client = pipeline_api_client

        if overridden_cloud_operations:
            self.cloud_operations = overridden_cloud_operations
        else:
            self.cloud_operations = {
                S3_TYPE: S3StorageOperations(logger)
            }

    def initialize(self):
        self.regions_by_id = self._load_and_filter_regions()

    def is_support(self, storage):
        return storage.storage_type in self.cloud_operations

    def should_be_skipped(self, storage):
        tag_to_skip_storage = self._fetch_skipping_tag_from_region(storage)
        metadata = self.pipeline_api_client.load_entity_metadata(storage.id, "DATA_STORAGE")

        if not metadata or not tag_to_skip_storage:
            return False
        return tag_to_skip_storage in metadata

    def prepare_bucket_if_needed(self, storage):
        region = self.fetch_region(storage.region_id)
        storage_cloud_identifier, storage_path_prefix = self._parse_storage_path(storage)
        storage_container = CloudPipelineStorageContainer(storage_cloud_identifier, storage_path_prefix, storage)
        self.cloud_operations[storage.storage_type].prepare_bucket_if_needed(region, storage_container)

    def list_objects_by_prefix(self, storage, prefix):
        region = self.fetch_region(storage.region_id)
        storage_is_versioned = storage.policy.versioning if storage.policy else False
        storage_cloud_identifier, storage_path_prefix = self._parse_storage_path(storage)
        storage_container = CloudPipelineStorageContainer(storage_cloud_identifier, storage_path_prefix + prefix, storage)
        files = self.cloud_operations[storage.storage_type].list_objects_by_prefix(region, storage_container,
                                                                                   list_versions=storage_is_versioned)
        for file in files:
            if storage_path_prefix and file.path.startswith(storage_path_prefix):
                file.path = file.path.replace(storage_path_prefix, "", 1)
        return files

    def tag_files_to_transit(self, storage, files, storage_class, transit_operation_id):
        try:
            region = self.fetch_region(storage.region_id)
            storage_cloud_identifier, storage_path_prefix = self._parse_storage_path(storage)
            storage_container = CloudPipelineStorageContainer(storage_cloud_identifier, storage_path_prefix, storage)
            for file in files:
                if storage_path_prefix:
                    file.path = storage_path_prefix + file.path
            return self.cloud_operations[storage.storage_type].tag_files_to_transit(
                region, storage_container, files, storage_class, transit_operation_id)
        except Exception as e:
            self.logger.log("Something went wrong when try to tag files from {}. Cause: {}".format(storage.path, e))
            return False

    def run_restore_action(self, storage, action, restore_operation_id):
        try:
            region = self.fetch_region(storage.region_id)
            storage_cloud_identifier, storage_path_prefix = self._parse_storage_path(storage)
            storage_container = CloudPipelineStorageContainer(storage_cloud_identifier,
                                                              path_utils.join_paths(storage_path_prefix, action.path),
                                                              storage)
            files = self.cloud_operations[storage.storage_type].list_objects_by_prefix(
                region, storage_container,
                convert_paths=False, list_versions=action.restore_versions
            )
            self.logger.log("Storage: {}. Action: {}. Path: {}. Listed '{}' files to possible restore."
                            .format(storage.id, action.action_id, action.path, len(files)))
            return self.cloud_operations[storage.storage_type].run_files_restore(
                region, storage_container, files, action.days, action.restore_mode, restore_operation_id
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
            region = self.fetch_region(storage.region_id)
            storage_cloud_identifier, storage_path_prefix = self._parse_storage_path(storage)
            storage_container = CloudPipelineStorageContainer(storage_cloud_identifier,
                                                              storage_path_prefix + action.path, storage)
            files = self.cloud_operations[storage.storage_type].list_objects_by_prefix(
                region, storage_container,
                convert_paths=False, list_versions=action.restore_versions
            )
            return self.cloud_operations[storage.storage_type].check_files_restore(
                region, storage_container, files, action.updated, action.restore_mode
            )
        except Exception as e:
            self.logger.log("Problem with checking restoring files, cause: {}".format(e))
            return {
                "status": False,
                "reason": e,
                "value": None
            }

    def get_storage_class_transition_map(self, storage, rule):
        storage_classes = [transition.storage_class for transition in rule.transitions]
        return self.cloud_operations[storage.storage_type].get_storage_class_transition_map(storage_classes)

    @staticmethod
    def _parse_storage_path(storage):
        split_storage_path = storage.path.rsplit("/", 1)
        if len(split_storage_path) == 1:
            return split_storage_path[0], ""
        else:
            return split_storage_path[0], "/{}".format(split_storage_path[1])

    def fetch_region(self, region_id):
        if region_id in self.regions_by_id:
            return self.regions_by_id[region_id]
        else:
            raise RuntimeError("Can't find region by id: {} in region map, available regions is: {}"
                               .format(region_id, self.regions_by_id.keys()))

    def _load_and_filter_regions(self):
        _regions_by_id = {region.id: region for region in self.pipeline_api_client.load_regions()}
        invalid_region_ids = []
        for region_id, region in _regions_by_id.items():
            validation_result = False
            if region.storage_lifecycle_service_properties:
                validation_result = sls_properties_verifiers[region.provider](
                    region.storage_lifecycle_service_properties, self.logger)
            if not validation_result:
                self.logger.log(
                    "Region: {} hasn't valid storage_lifecycle_service_properties, will filter this region!".format(
                        region.id))
                invalid_region_ids.append(region_id)
        [_regions_by_id.pop(rid, None) for rid in invalid_region_ids]
        return _regions_by_id

    def _fetch_skipping_tag_from_region(self, storage):
        region = self.fetch_region(storage.region_id)
        return region.storage_lifecycle_service_properties.get(SKIP_ARCHIVING_TAG) if region else None
