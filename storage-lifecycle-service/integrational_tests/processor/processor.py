class TestCaseProcessor:

    def process(self, testcase):
        pass


class TestCaseResultGatherer:

    def gather(self, testcase):
        pass


class ProcessorChain:

    def __init__(self, processors, gatherers):
        self.processors = processors
        self.gatherers = gatherers

    def process(self, testcase):
        for processor in self.processors:
            testcase = processor.process(testcase)

    def gather(self, testcase):
        result = None
        for gatherer in self.gatherers:
            result = gatherer.gather(testcase).merge(result)
        return result

