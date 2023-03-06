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

import datetime

from sls.cloud.cloud import StorageOperations


class AttributesChangingStorageOperations(StorageOperations):

    def __init__(self, cloud_operations, testcase):
        self.cloud_operations = cloud_operations
        self.watched_files_by_storages = {}
        for storage in testcase.cloud.storages:
            self.watched_files_by_storages[storage.storage] = {}
            for file in storage.files:
                self.watched_files_by_storages[storage.storage][file.path if file.path.startswith("/") else "/" + file.path] = file

    def prepare_bucket_if_needed(self, region, storage_container):
        self.cloud_operations.prepare_bucket_if_needed(region, storage_container)

    def list_objects_by_prefix(self, region, storage_container, list_versions=False, convert_paths=True):
        intermediate_result = self.cloud_operations.list_objects_by_prefix(region, storage_container,
                                                                           list_versions=list_versions,
                                                                           convert_paths=convert_paths)
        for file in intermediate_result:
            if file.path in self.watched_files_by_storages[storage_container.bucket]:
                file.storage_class = self.watched_files_by_storages[storage_container.bucket][file.path].storage_class
                file.creation_date = file.creation_date - datetime.timedelta(
                    days=self.watched_files_by_storages[storage_container.bucket][file.path].storage_date_shift)
        return intermediate_result

    def tag_files_to_transit(self, region, storage_container, files, storage_class, transit_id):
        return self.cloud_operations.tag_files_to_transit(region, storage_container, files, storage_class, transit_id)

    def get_storage_class_transition_map(self, storage_classes):
        return self.cloud_operations.get_storage_class_transition_map(storage_classes)
