import sls.datasorce.cp_data_source
from integrational_tests.decorator import cp_api_decator
from integrational_tests.decorator.cloud_decator import AttributesChangingStorageOperations
from integrational_tests.processor.processor import TestCaseProcessor
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

        synchronizer = StorageLifecycleSynchronizer(SynchronizerConfig(), data_source, cloud_operations, logger)
        for storage in testcase.platform.storages:
            loaded_storage = data_source.load_storage(str(storage.datastorage_id))
            synchronizer._sync_storage(loaded_storage)
        return testcase
