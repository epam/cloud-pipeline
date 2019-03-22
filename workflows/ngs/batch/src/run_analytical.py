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
import urlparse
from pipeline import PipelineAPI, Logger, SampleSheetParser

SAMPLE_ID = "Sample_ID"
SAMPLE_NAME = "Sample_Name"
SAMPLE_PROJECT = "Sample_Project"


class RunAnalyticalPipelinesTask(object):

    def __init__(self, task, pipeline, version, instance_type, instance_disk):
        self.api = PipelineAPI(os.environ['API'], 'logs')
        self.task = task
        self.pipeline = self.api.find_pipeline(pipeline)
        self.version = version
        self.instance_type = instance_type
        self.instance_disk = instance_disk

    def run(self):
        analysis_folder = os.environ['ANALYSIS_FOLDER']
        machine_run_folder = os.environ['MACHINE_RUN_FOLDER']
        sample_sheet = os.environ['SAMPLE_SHEET']
        Logger.info('Starting analytical processing for sample sheet %s' % sample_sheet, task_name=self.task)
        samples = SampleSheetParser(sample_sheet, [SAMPLE_ID, SAMPLE_NAME, SAMPLE_PROJECT]).parse_sample_sheet()
        launched_runs = {}
        for sample in samples:
            Logger.info('Starting "%s" sample processing.' % sample[SAMPLE_NAME], task_name=self.task)
            launched_runs[sample[SAMPLE_NAME]] = self.__run_sample(sample[SAMPLE_NAME],
                                                                   analysis_folder, machine_run_folder)
        failed_runs = self.__wait_runs_completion(launched_runs)
        if failed_runs:
            for sample, run_id in failed_runs.iteritems():
                Logger.fail('Processing failed for sample "%s". Check run %d logs for more information.' %
                            (sample, run_id), task_name=self.task)
            sys.exit(1)
        Logger.success("All samples processed successfully.", task_name=self.task)

    def __run_sample(self, sample, analysis_folder, machine_run_folder):
        Logger.info('Launching analytical pipeline "%s" with version "%s" for sample %s.' %
                    (self.pipeline['name'], self.version, sample), task_name=self.task)
        read1, read2 = self.__fetch_reads(sample, analysis_folder, machine_run_folder)
        pipeline_params = {'SAMPLE': {'value': sample},
                           'READ1': {'value': read1, 'type': 'input'},
                           'READ2': {'value': read2, 'type': 'input'},
                           'OUTPUT_FOLDER': {'value': analysis_folder, 'type': 'output'}}
        run = self.api.launch_pipeline(self.pipeline['id'], self.version, pipeline_params,
                                       instance=self.instance_type, disk=self.instance_disk,
                                       parent_run_id=os.environ['RUN_ID'])
        return run['id']

    def __fetch_reads(self, sample, analysis_folder, machine_run_folder):
        run_folder_name = urlparse.urlparse(machine_run_folder).path
        read_folder = self.__get_path_without_trailing_slash(analysis_folder) + \
                      self.__get_path_without_trailing_slash(run_folder_name) + \
                      '/PipelineInputData/FASTQ/'
        return os.path.join(read_folder, sample + '_R1.fastq.gz'), os.path.join(read_folder, sample + '_R2.fastq.gz')

    def __get_path_without_trailing_slash(self, path):
        return path[:-1] if path.endswith('/') else path

    def __wait_runs_completion(self, launched_runs):
        finished = {}
        failed = {}
        while True:
            for sample, run_id in launched_runs.iteritems():
                current_status = self.api.load_run(run_id)['status']
                Logger.info('Processing sample: %s. Run %d status is %s.' % (sample, run_id, current_status),
                            task_name=self.task)
                if current_status != 'RUNNING':
                    finished[sample] = run_id
                    if current_status != 'SUCCESS':
                        failed[sample] = run_id
            if len(finished) == len(launched_runs):
                Logger.info("Processing for all samples completed.", task_name=self.task)
                return failed
            time.sleep(60)


def get_from_env(var_name):
    if var_name in os.environ:
        return os.environ[var_name]
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--task', required=True)
    parser.add_argument('--pipeline', required=True)
    parser.add_argument('--version', required=True)
    args = parser.parse_args()
    RunAnalyticalPipelinesTask(args.task, args.pipeline, args.version, get_from_env('ANALYTICAL_INSTANCE'), get_from_env('ANALYTICAL_DISK')).run()


if __name__ == '__main__':
    main()
