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
import time
import unittest
import os
import logging

from parameterized import parameterized
from pipeline import PipelineAPI

from integrational_tests.processor import test_case_preprocessing, environment_preparation
from integrational_tests.processor.environment_preparation import CloudTestCasePreparator, \
    CloudPipelinePlatformTestCasePreparator
from integrational_tests.processor.processor import ProcessorChain
from integrational_tests.processor.result_gathering import PlatformTestCaseResultGatherer, CloudTestCaseResultGatherer
from integrational_tests.processor.test_case_execution import TestCaseExecutor


def build_config():

    def _assert_and_get_value(key):
        if key not in os.environ or not os.environ.get(key):
            raise AttributeError("Please set env var {} to be able to run integration tests.".format(key))
        return os.environ.get(key)

    return {key: _assert_and_get_value(key) for key in INTEGRATION_TEST_CONFIG_ENV_VARS}


INTEGRATION_TEST_CONFIG_ENV_VARS = ["CP_API_URL", "API_TOKEN", "CP_STORAGE_LIFECYCLE_DAEMON_TEST_CASES_PATH",
                                    "CP_STORAGE_LIFECYCLE_DAEMON_AWS_REGION_ID"]

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
config = build_config()


class IntegrationTestsRunner(unittest.TestCase):
    cp_api = PipelineAPI(config.get("CP_API_URL"), "/tmp/")
    aws_region = cp_api.get_region(int(config.get("CP_STORAGE_LIFECYCLE_DAEMON_AWS_REGION_ID")))

    set_uppers = [
        CloudTestCasePreparator(aws_region["regionId"], environment_preparation.SETUP_MODE),
        CloudPipelinePlatformTestCasePreparator(cp_api, aws_region["id"], environment_preparation.SETUP_MODE)
    ]

    clean_uppers = [
        CloudTestCasePreparator(aws_region["regionId"], environment_preparation.CLEAN_UP_MODE),
        CloudPipelinePlatformTestCasePreparator(cp_api, aws_region["id"], environment_preparation.CLEAN_UP_MODE)
    ]

    processors_chain = ProcessorChain(logger=logger)\
        .with_preprocessors(set_uppers)\
        .with_processors([TestCaseExecutor(config, logger)])\
        .with_postprocessors(clean_uppers)\
        .with_gatherers(
            [PlatformTestCaseResultGatherer(cp_api), CloudTestCaseResultGatherer(aws_region["regionId"])]
        )

    @parameterized.expand([case_file for case_file in os.listdir(config.get("CP_STORAGE_LIFECYCLE_DAEMON_TEST_CASES_PATH"))])
    def test_run_integration_test_cases(self, case_file):
        logger.log(logging.INFO, "Start test case: {}".format(case_file))
        test_case = test_case_preprocessing.read_test_case(
            os.path.join(config.get("CP_STORAGE_LIFECYCLE_DAEMON_TEST_CASES_PATH"), case_file))

        expected = test_case.result
        actual = self.processors_chain.process(test_case)

        self.assertCloudState(actual.cloud, expected.cloud)
        self.assert_platform_state(actual.platform, expected.platform)

        logger.log(logging.INFO, "Finish test case: {}\n\n".format(case_file))
        time.sleep(30)

    def assertCloudState(self, actual, expected):
        self.assertEqual(expected is None, actual is None)
        if expected and expected.storages:
            logger.log(logging.INFO, "Comparing cloud states.")
            self.assertEqual(expected is None, actual is None)
            self.assertIsNotNone(actual.storages)
            self.assertEqual(len(expected.storages), len(actual.storages))

            expected_storage_states_by_name = {storage.storage: storage for storage in expected.storages}
            for actual_storage in actual.storages:
                expected_storage = expected_storage_states_by_name.get(actual_storage.storage, None)
                self.assertIsNotNone(expected_storage)
                actual_file_states_by_name = {file.path: file for file in actual_storage.files}
                for expected_file in expected_storage.files:
                    actual_file = actual_file_states_by_name.get(expected_file.path, None)
                    self.assertIsNotNone(actual_file)
                    self.assertDictEqual(expected_file.tags, actual_file.tags)

    def assert_platform_state(self, actual, expected):
        self.assertEqual(expected is None, actual is None)

        if expected:
            logger.log(logging.INFO, "Comparing platform states.")
            self.assertEqual(len(expected.storages), len(actual.storages))
            expected_storage_states_by_name = {storage.storage: storage for storage in expected.storages}
            for actual_storage in actual.storages:
                expected_storage = expected_storage_states_by_name.get(actual_storage.storage, None)
                if expected_storage.executions:
                    self.assertEqual(len(expected_storage.executions), len(actual_storage.executions))
                    for expected_execution in expected_storage.executions:
                        corresponding_expected = next(
                            filter(
                                lambda e: e["path"] == expected_execution["path"]
                                          and e["storageClass"] == expected_execution["storageClass"]
                                          and e["status"] == expected_execution["status"],
                                actual_storage.executions),
                            None
                        )
                        self.assertIsNotNone(corresponding_expected)


if __name__ == '__main__':
    unittest.main()
