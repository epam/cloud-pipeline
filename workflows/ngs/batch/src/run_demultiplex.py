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

import argparse
import os
import time
import sys
from pipeline import PipelineAPI, Logger


class DemultiplexTask(object):

    def __init__(self, task, pipeline, version, instance_type, instance_disk):
        self.task = task
        self.pipeline_name = pipeline
        self.version = version
        self.instance_type = instance_type
        self.instance_disk = instance_disk
        self.api = PipelineAPI(os.environ['API'], 'logs')

    def run(self):
        Logger.info('Launching demultiplex pipeline "%s" with version "%s"' %
                    (self.pipeline_name, self.version), task_name=self.task)
        pipeline = self.api.find_pipeline(self.pipeline_name)
        pipeline_params = {'MACHINE_RUN_FOLDER': {'value': os.environ['MACHINE_RUN_FOLDER'], 'type': 'input'},
                           'SAMPLE_SHEET': {'value': os.environ['SAMPLE_SHEET_ORIGINAL'], 'type': 'input'},
                           'ANALYSIS_FOLDER': {'value': os.environ['ANALYSIS_FOLDER'], 'type': 'output'}}
        run = self.api.launch_pipeline(pipeline['id'], self.version, pipeline_params,
                                       instance=self.instance_type, disk=self.instance_disk,
                                       parent_run_id=os.environ['RUN_ID'])
        demultiplex_run_id = run['id']
        Logger.info('Launched demultiplex run %d.' % demultiplex_run_id, task_name=self.task)
        Logger.info('Waiting till run %d completion.' % demultiplex_run_id, task_name=self.task)
        final_status = self.__wait_run_completion(demultiplex_run_id)
        if final_status != 'SUCCESS':
            Logger.fail('Demultiplex processing does not completed successfully. '
                        'Check run %d logs for more information.' % demultiplex_run_id, task_name=self.task)
            sys.exit(1)
        Logger.success('Demultiplex processing completed sucessfully.', task_name=self.task)

    def __wait_run_completion(self, run_id):
        current_status = self.api.load_run(run_id)['status']
        while current_status == 'RUNNING':
            Logger.info('Run %d status is %s. Waiting for completion...' % (run_id, current_status), task_name=self.task)
            time.sleep(60)
            current_status = self.api.load_run(run_id)['status']
        Logger.info('Run %d finished with status %s' % (run_id, current_status), task_name=self.task)
        return current_status


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--task', required=True)
    parser.add_argument('--pipeline', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--instance-type', required=True)
    parser.add_argument('--instance-disk', required=True)
    args = parser.parse_args()
    DemultiplexTask(args.task, args.pipeline, args.version, args.instance_type, args.instance_disk).run()


if __name__ == '__main__':
    main()
