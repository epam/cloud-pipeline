import unittest
import os

from parameterized import parameterized

from integrational_tests.processor import test_case_preprocessing
from integrational_tests.processor.processor import ProcessorChain
from integrational_tests.processor.result_gathering import PlatformTestCaseResultGatherer, CloudTestCaseResultGatherer

TEST_CASES_PATH = "resources/testcases"


class IntegrationTestsRunner(unittest.TestCase):

    @parameterized.expand([case_file for case_file in os.listdir(TEST_CASES_PATH)])
    def test_run_integration_test_cases(self, case_file):

        processors_chain = ProcessorChain(
            processors=[],
            gatherers=[PlatformTestCaseResultGatherer(), CloudTestCaseResultGatherer()]
        )

        print("Start test case: {}".format(case_file))
        test_case = test_case_preprocessing.read_test_case(os.path.join(TEST_CASES_PATH, case_file))

        expected = test_case.result
        actual = None
        try:
            processors_chain.process(test_case)
            actual = processors_chain.gather(test_case)
        except Exception as e:
            print("Something went wrong while executing test case: {}, problem: {}".format(case_file, str(e)))

        self.assertCloudState(actual.cloud_state, expected.cloud_state)
        self.assert_platform_state(actual.platform_state, expected.platform_state)

        print("Finish test case: {}\n\n".format(case_file))

    def assertCloudState(self, actual, expected):
        self.assertEqual(expected is None, actual is None)
        if expected and expected.storages:
            print("Comparing cloud states.")
            self.assertEqual(expected is None, actual is None)
            self.assertIsNotNone(actual.storages)
            self.assertEqual(len(expected.storages), len(actual.storages))

            expected_storage_states_by_name = {storage.storage: storage for storage in expected.storages}
            for actual_storage in actual.storages:
                expected_storage = expected_storage_states_by_name.get(actual_storage.storage, None)
                self.assertIsNotNone(expected_storage)
                self.assertEqual(len(expected_storage.files), len(actual_storage.files))
                expected_file_states_by_name = {file.path: file for file in expected_storage.files}
                for actual_file in actual_storage.files:
                    expected_file = expected_file_states_by_name.get(actual_file.path, None)
                    self.assertIsNotNone(expected_file)
                    self.assertEqual(sorted(expected_file.tags), sorted(actual_file.tags))

    def assert_platform_state(self, actual, expected):
        self.assertEqual(expected is None, actual is None)

        if expected:
            print("Comparing platform states.")
            self.assertEqual(len(expected.storages), len(actual.storages))
            expected_storage_states_by_name = {storage.storage: storage for storage in expected.storages}
            for actual_storage in actual.storages:
                expected_storage = expected_storage_states_by_name.get(actual_storage.storage, None)
                self.assertEqual(expected_storage.executions is None, actual_storage.executions is None)
                if expected_storage.executions:
                    for actual_execution in actual_storage.executions:
                        corresponding_expected = next(
                            filter(
                                lambda e: e.path == actual_execution.path and e.storage_class == actual_execution.storage_class and e.status == actual_execution.status,
                                expected_storage.executions),
                            None
                        )
                        self.assertIsNotNone(corresponding_expected)

                if expected_storage.notifications:
                    for actual_notification in actual_storage.notifications:
                        corresponding_expected = next(
                            filter(
                                lambda n: n["template_parameters"] == actual_notification["template_parameters"],
                                expected_storage.executions),
                            None
                        )
                        self.assertIsNotNone(corresponding_expected)


if __name__ == '__main__':
    unittest.main()
