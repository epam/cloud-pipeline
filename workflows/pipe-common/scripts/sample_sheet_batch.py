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

from pipeline import Logger, S3Bucket, SampleSheetParser, EnvironmentParametersParser
from batch import AbstractFolderScanner, AbstractPipelineLauncher
import click
import os
import time

bucket = S3Bucket()
SAMPLE_ID = "Sample_ID"
SAMPLE_NAME = "Sample_Name"
SAMPLE_PROJECT = "Sample_Project"


class FolderScanner(AbstractFolderScanner):

    def __init__(self, folder, patterns, exclude_patterns, samples):
        AbstractFolderScanner.__init__(self, folder, patterns, exclude_patterns)
        self.samples = samples

    def format_sample_patterns(self, sample, patterns):
        result = {}
        for pattern, pattern_list in patterns.iteritems():
            formatted = []
            for value in pattern_list:
                formatted.append(value.format(sample_id=sample[SAMPLE_ID],
                                              sample_name=sample[SAMPLE_NAME],
                                              sample_project=sample[SAMPLE_PROJECT]))
            result[pattern] = formatted
        return result

    def find_files(self, recursive=True):
        Logger.info("Starting parsing input directory: {}.".format(self.folder), task_name=self.TASK_NAME)
        all_files = bucket.ls_s3(self.folder, self.MAX_ATTEMPTS, recursive=recursive)
        patterns_files = {}
        if recursive:
            all_folders = self.get_folders(all_files)
            for folder in all_folders:
                self.check_file_match(self.samples, folder, patterns_files)

        for file in all_files:
            # recursive version of s3 ls returns path from bucket root
            # non-recursive ls returns path relative to the requested folder
            if recursive:
                file_name = file[len(self.get_path_without_bucket()) - 1:]
            else:
                file_name = file
            self.check_file_match(self.samples, file_name, patterns_files)
        Logger.info('Collected batch files: {}.'.format(str(patterns_files)), task_name=self.TASK_NAME)
        if len(patterns_files) != len(self.samples):
            self.fail_task("Failed to find all parameters for all samples.".format())
        Logger.success('Successfully collected batch files: {}.'.format(str(patterns_files)), task_name=self.TASK_NAME)
        return patterns_files

    def check_file_match(self, samples, file_name, patterns_files):
        for sample in samples:
            sample_name = sample[SAMPLE_NAME]
            patterns = self.format_sample_patterns(sample, self.patterns)
            exclude = self.format_sample_patterns(sample, self.exclude_patterns)
            for pattern_name, glob in patterns.iteritems():
                if self.match_patterns(file_name, glob):
                    if pattern_name in exclude:
                        exclude = exclude[pattern_name]
                        if self.match_patterns(file_name, exclude):
                            Logger.info("Skipping filename '{}' since it matches exclude patterns '{}'."
                                        .format(file_name, str(exclude)))
                            continue
                    if sample_name not in patterns_files:
                        patterns_files[sample_name] = {}
                    if pattern_name not in patterns_files[sample_name]:
                        patterns_files[sample_name][pattern_name] = []
                    patterns_files[sample_name][pattern_name].append(os.path.join(self.folder, file_name))

    def get_folders(self, all_files):
        folders = set()
        for file in all_files:
            folders.add(os.path.dirname(file[len(self.get_path_without_bucket()) - 1:]))
        result = set()
        for folder in folders:
            result.add(folder + '/')
            current = folder
            while current:
                result.add(current + '/')
                current = os.path.dirname(current)
        return result


class PipelineLauncher(AbstractPipelineLauncher):

    def __init__(self, run_files, param_names, run_id, pipe_id, version, pipe_params, samples, param_types):
        AbstractPipelineLauncher.__init__(self, run_files, param_names, run_id, pipe_id, version, pipe_params,
                                          param_types)
        self.samples = samples

    def launch(self, nodes, instance_size, instance_disk, docker_image, cmd, wait_finish=False):
        running = 0
        scheduled = 0
        Logger.info('Starting {} sample(s) scheduling.'.format(len(self.samples)), task_name=self.TASK_NAME)
        while scheduled != len(self.samples):
            if running < nodes:
                sample = self.samples[scheduled]
                self.launch_pipeline(self.run_files[sample[SAMPLE_NAME]], self.param_names,
                                     instance_size, instance_disk, docker_image, cmd, sample=sample)
                scheduled = scheduled + 1
                running = running + 1
            else:
                Logger.info('Processing {} sample(s).'.format(running), task_name=self.TASK_NAME)
                Logger.info('Total scheduled  {} sample(s).'.format(scheduled), task_name=self.TASK_NAME)
                time.sleep(self.POLL_TIMEOUT)
                running = self.get_running_samples()
        while self.child_run_active():
            Logger.info('Waiting a child run {} to finish.'.format(self.child_id), task_name=self.TASK_NAME)
            time.sleep(self.POLL_TIMEOUT)
        if wait_finish:
            Logger.info('Waiting for all runs to finish.', task_name=self.TASK_NAME)
            self.wait_all_samples_finish()
        Logger.success('Successfully scheduled {} sample(s).'.format(scheduled), task_name=self.TASK_NAME)


@click.command(name='batch', context_settings=dict(ignore_unknown_options=True))
@click.option('-c', '--cmd', help='Command to run sample processing', default=None)
@click.option('-s', '--sample_sheet', help='SampleSheet to run sample processing', default=None)
def batch(cmd, sample_sheet):
    pipeline_parameters, file_patterns, exclude_patterns, param_types = EnvironmentParametersParser({'nodes'})\
        .collect_params_from_env()
    nodes = int(EnvironmentParametersParser.get_env_value('nodes', 1))
    if nodes < 1:
        raise RuntimeError('Number of nodes should be greater than zero.')
    pipeline = EnvironmentParametersParser.get_env_value('PIPELINE_ID')
    version = EnvironmentParametersParser.get_env_value('PIPELINE_VERSION')
    run_id = EnvironmentParametersParser.get_env_value('RUN_ID')
    fastq_dir = bucket.normalize_path(EnvironmentParametersParser.get_env_value('fastq_dir'))
    instance_size = EnvironmentParametersParser.get_env_value('instance_size')
    instance_disk = EnvironmentParametersParser.get_env_value('instance_disk')
    docker_image = EnvironmentParametersParser.get_env_value('docker_image')
    samples = SampleSheetParser(sample_sheet, [SAMPLE_ID, SAMPLE_NAME, SAMPLE_PROJECT]).parse_sample_sheet()
    run_files = FolderScanner(fastq_dir, file_patterns, exclude_patterns, samples).find_files()
    wait_finish = EnvironmentParametersParser.has_flag('wait_finish')
    PipelineLauncher(run_files, file_patterns.keys(), run_id, pipeline, version, pipeline_parameters,
                     samples, param_types)\
        .launch(nodes, instance_size, instance_disk, docker_image, cmd, wait_finish=wait_finish)


if __name__ == '__main__':
    batch()
