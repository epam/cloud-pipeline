# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

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
