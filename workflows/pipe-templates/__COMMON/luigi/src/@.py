import luigi
from luigi.util import inherits
import pipeline
from pipeline import LogEntry, TaskStatus


class DefaultPipeline(pipeline.Pipeline):
    def requires(self):
        yield self.clone(Task)


@inherits(DefaultPipeline)
class Task(pipeline.HelperTask):
    helper = False

    def output(self):
        return luigi.LocalTarget("./tmp.txt")

    def run(self):
        self.log_event(LogEntry(self.run_id, 
                                TaskStatus.RUNNING, "Running luigi pipeline",
                                self.__repr__(), 
                                self.uu_name))
        with open(self.output().path, "w") as result:
            result.write("Running luigi pipeline")
                                
if __name__ == '__main__':
    val = luigi.run()
    if not val:
        sys.exit(1)
