from integrational_tests.processor.processor import TestCaseProcessor

SETUP_MODE = "SETUP"
CLEAN_UP_MODE = "CLEAN_UP"


class CloudTestCasePreparator(TestCaseProcessor):

    def __init__(self, mode=SETUP_MODE):
        self.cloud_preparators = {
            "AWS": AWSCloudTestCasePreparator(mode)
        }

    def process(self, testcase):
        return self.cloud_preparators[testcase.cloud_provider].process(testcase)


class AWSCloudTestCasePreparator(TestCaseProcessor):

    def __init__(self, mode=SETUP_MODE):
        self.mode = mode

    def process(self, testcase):
        if self.mode == SETUP_MODE:
            return testcase
        elif self.mode == CLEAN_UP_MODE:
            return testcase
        else:
            raise AttributeError("Wrong mode provided: {}. Possible values: {}"
                                 .format(self.mode, [SETUP_MODE, CLEAN_UP_MODE]))


class CloudPipelinePlatformTestCasePreparator(TestCaseProcessor):

    # TODO don't forget to replace datastorageId, ruleId etc in test case object
    def process(self, testcase):
        return testcase
