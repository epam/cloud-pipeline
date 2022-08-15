import logging


class TestCaseProcessor:

    def process(self, testcase):
        pass


class TestCaseResultGatherer:

    def gather(self, testcase):
        pass


class ProcessorChain:

    def __init__(self, logger):
        self.logger = logger
        self.preprocessors = []
        self.processors = []
        self.postprocessors = []
        self.gatherers = []

    def with_preprocessors(self, processors):
        self.preprocessors.extend(processors)
        return self

    def with_processors(self, processors):
        self.processors.extend(processors)
        return self

    def with_postprocessors(self, processors):
        self.postprocessors.extend(processors)
        return self

    def with_gatherers(self, gatherers):
        self.gatherers.extend(gatherers)
        return self

    def process(self, testcase):
        try:
            self.logger.log(logging.INFO, "Start preprocessing for test case.")
            for processor in self.preprocessors:
                testcase = processor.process(testcase)
            self.logger.log(logging.INFO, "Done preprocessing for test case.")

            self.logger.log(logging.INFO, "Start running test case.")
            for processor in self.processors:
                testcase = processor.process(testcase)
            self.logger.log(logging.INFO, "Finish test case.")

            self.logger.log(logging.INFO, "Gathering result of test case.")
            result = self._gather(testcase)
            self.logger.log(logging.INFO, "Gathering done for test case.")
            return result
        except Exception as e:
            self.logger.exception(e)
        finally:
            self.logger.log(logging.INFO, "Cleanup for test case.")
            for processor in self.postprocessors:
                try:
                    testcase = processor.process(testcase)
                except Exception as e:
                    self.logger.exception(e)
            self.logger.log(logging.INFO, "Finish cleanup for test case.")

    def _gather(self, testcase):
        result = None
        for gatherer in self.gatherers:
            result = gatherer.gather(testcase).merge(result)
        return result

