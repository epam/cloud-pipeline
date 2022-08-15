from integrational_tests.model.testcase import TestCaseResult
from integrational_tests.processor.processor import TestCaseResultGatherer


class PlatformTestCaseResultGatherer(TestCaseResultGatherer):

    def gather(self, testcase):
        return testcase.result


class CloudTestCaseResultGatherer(TestCaseResultGatherer):

    def __init__(self):
        self.cloud_preparators = {
            "AWS": AWSCloudTestCaseResultGatherer()
        }

    def gather(self, testcase):
        result = TestCaseResult()
        if not testcase.result.cloud_state or not testcase.result.cloud_state.storages:
            return result
        for storage in testcase.result.cloud_state.storages:
            result = self.cloud_preparators[storage.cloud_provider].gather(testcase).merge(result)


class AWSCloudTestCaseResultGatherer(TestCaseResultGatherer):

    def gather(self, testcase):
        return testcase.result
