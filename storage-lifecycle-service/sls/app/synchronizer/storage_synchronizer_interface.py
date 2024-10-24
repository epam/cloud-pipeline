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


class StorageLifecycleSynchronizer:

    def __init__(self, config, pipeline_api_client, cloud_bridge, logger):
        self.config = config
        self.cloud_bridge = cloud_bridge
        self.pipeline_api_client = pipeline_api_client
        self.logger = logger

    def sync(self):
        try:
            self.logger.log("Starting object lifecycle synchronization process...")
            available_storages = self._load_storages()
            self.logger.log("{} storages loaded.".format(len(available_storages)))
            self.cloud_bridge.initialize()
            for storage in available_storages:
                self.logger.log(
                    "Starting object lifecycle synchronization process for {} with type {}.".format(
                        storage.path, storage.storage_type)
                )
                try:
                    self._sync_storage(storage)
                except Exception as e:
                    self.logger.log(
                        "Storage: {}. Problems to process storage. Cause: {}".format(storage.id, str(e)))
                self.logger.log(
                    "Finish object lifecycle synchronization process for {} with type {}.".format(
                        storage.path, storage.storage_type)
                )
            self.logger.log("Done object lifecycle synchronization process...")
        except BaseException:
            self.logger.exception("There was a problem in process of object lifecycle synchronization.")

    def _load_storages(self):
        return []

    def _sync_storage(self, storage):
        pass


