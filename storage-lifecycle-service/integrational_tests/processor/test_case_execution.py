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

import sls.datasorce.cp_data_source
from integrational_tests.decorator import cp_api_decator
from integrational_tests.decorator.cloud_decator import AttributesChangingStorageOperations
from integrational_tests.processor.processor import TestCaseProcessor
from sls.app.cloud_storage_adapter import PlatformToCloudOperationsAdapter
from sls.app.storage_synchronizer import StorageLifecycleSynchronizer
from sls.cloud.cloud import S3StorageOperations
from sls.model.config_model import SynchronizerConfig
from sls.util.logger import AppLogger
from sls.util.parse_utils import parse_config_string


class TestCaseExecutor(TestCaseProcessor):

    def __init__(self, config, logger):
        self.config = config
        self.logger = logger

    def process(self, testcase):
        logger = AppLogger()
        data_source = cp_api_decator.MockedNotificationRESTApiCloudPipelineDataSource(
            sls.datasorce.cp_data_source.configure_cp_data_source(
                self.config.get("CP_API_URL"), self.config.get("CP_API_TOKEN"), "/tmp")
        )

        cloud_operations = {
            "S3": AttributesChangingStorageOperations(
                S3StorageOperations(
                    parse_config_string(self.config.get("CP_STORAGE_LIFECYCLE_DAEMON_AWS_CONFIG")),
                    logger
                ),
                testcase
            )
        }

        cloud_bridge = PlatformToCloudOperationsAdapter(cloud_operations)

        synchronizer = StorageLifecycleSynchronizer(SynchronizerConfig(), data_source, cloud_bridge, logger)
        for storage in testcase.platform.storages:
            loaded_storage = data_source.load_storage(str(storage.datastorage_id))
            synchronizer._sync_storage(loaded_storage)
        return testcase
