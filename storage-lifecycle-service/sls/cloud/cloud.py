# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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


class StorageOperations:
    def prepare_bucket_if_needed(self, region, bucket):
        pass

    def list_objects_by_prefix(self, region, bucket, prefix, list_versions=False, convert_paths=True):
        pass

    def tag_files_to_transit(self, region, bucket, files, storage_class, transit_id):
        pass

    def run_files_restore(self, region, bucket, files, days, restore_tear, operation_id):
        pass

    def check_files_restore(self, region, bucket, files, restore_timestamp, restore_mode):
        pass
