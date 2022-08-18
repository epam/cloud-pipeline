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

    def prepare_bucket_if_needed(self, bucket):
        self.cloud_operations.prepare_bucket_if_needed(bucket)

    def list_objects_by_prefix(self, bucket, prefix, convert_paths=True):
        intermediate_result = self.cloud_operations.list_objects_by_prefix(bucket, prefix, convert_paths)
        for file in intermediate_result:
            if file.path in self.watched_files_by_storages[bucket]:
                file.storage_class = self.watched_files_by_storages[bucket][file.path].storage_class
                file.creation_date = file.creation_date - datetime.timedelta(
                    days=self.watched_files_by_storages[bucket][file.path].storage_date_shift)
        return intermediate_result

    def process_files_on_cloud(self, bucket, region, rule, folder, storage_class, files):
        return self.cloud_operations.process_files_on_cloud(bucket, region, rule, folder, storage_class, files)
