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
        return self.cloud_preparators[testcase.cloud].gather(testcase)


class AWSCloudTestCaseResultGatherer(TestCaseResultGatherer):

    def gather(self, testcase):
        return testcase.result
