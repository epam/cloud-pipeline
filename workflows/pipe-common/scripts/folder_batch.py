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

from pipeline import Logger, S3Bucket, EnvironmentParametersParser
from batch import AbstractFolderScanner, AbstractPipelineLauncher
import click
import os
import time

bucket = S3Bucket()


class FolderScanner(AbstractFolderScanner):
    def __init__(self, folder, patterns, exclude_patterns):
        AbstractFolderScanner.__init__(self, folder, patterns, exclude_patterns)

    def find_files(self, recursive=True):
        Logger.info("Starting parsing input directory: {}.".format(self.folder), task_name=self.TASK_NAME)
        all_files = bucket.ls_s3(self.folder, self.MAX_ATTEMPTS, recursive=recursive)
        patterns_files = {}
        for file in all_files:
            # recursive version of s3 ls returns path from bucket root
            # non-recursive ls returns path relative to the requested folder
            if recursive:
                file_name = file[len(self.get_path_without_bucket()) - 1:]
            else:
                file_name = file
            for pattern_name, glob in self.patterns.iteritems():
                Logger.info("Matching file {} against patterns {}.".format(file_name, str(glob)), task_name=self.TASK_NAME)
                if self.match_patterns(file_name, glob):
                    if pattern_name in self.exclude_patterns:
                        exclude = self.exclude_patterns[pattern_name]
                        if self.match_patterns(file_name, exclude):
                            Logger.info("Skipping filename '{}' since it matches exclude patterns '{}'."
                                        .format(file_name, str(exclude)))
                            continue
                    if pattern_name not in patterns_files:
                        patterns_files[pattern_name] = []
                    patterns_files[pattern_name].append(os.path.join(self.folder, file_name))
        if len(patterns_files) == 0:
            self.fail_task("Failed to find files matching any of patterns.")
        samples_number = None
        for pattern, files in patterns_files.iteritems():
            current_length = len(files)
            if current_length == 0:
                self.fail_task("Failed to find files matching patterns: {}.".format(str(pattern)))
            if samples_number is None:
                samples_number = current_length
            elif samples_number != current_length:
                self.fail_task("Number of found files differ between patterns. Please check the input data.")
            else:
                files.sort()
        Logger.info("Found files: {}".format(str(patterns_files)), task_name=self.TASK_NAME)
        result = [[] for x in xrange(samples_number)]
        for pattern, files in patterns_files.iteritems():
            index = 0
            for file in files:
                result[index].append(file)
                index = index + 1
        for file_set in result:
            Logger.info('Collected run parameters: {}.'.format(str(file_set)), task_name=self.TASK_NAME)
        Logger.success('Successfully collected batch files.', task_name=self.TASK_NAME)
        return result


class PipelineLauncher(AbstractPipelineLauncher):

    def __init__(self, run_files, param_names, run_id, pipe_id, version, pipe_params, param_types):
        AbstractPipelineLauncher.__init__(self, run_files, param_names, run_id, pipe_id, version, pipe_params,
                                          param_types)

    def launch(self, nodes, instance_size, instance_disk, docker_image, cmd, wait_finish=False):
        running = 0
        current_index = 0
        Logger.info('Starting {} sample(s) scheduling.'.format(self.samples_number), task_name=self.TASK_NAME)
        while current_index != self.samples_number:
            if running < nodes:
                self.launch_pipeline(self.run_files[current_index], self.param_names,
                                     instance_size, instance_disk, docker_image, cmd)
                current_index = current_index + 1
                running = running + 1
            else:
                Logger.info('Processing {} sample(s).'.format(running), task_name=self.TASK_NAME)
                Logger.info('Total scheduled  {} sample(s).'.format(current_index), task_name=self.TASK_NAME)
                time.sleep(self.POLL_TIMEOUT)
                running = self.get_running_samples()
        while self.child_run_active():
            Logger.info('Waiting a child run {} to finish.'.format(self.child_id), task_name=self.TASK_NAME)
            time.sleep(self.POLL_TIMEOUT)

        if wait_finish:
            Logger.info('Waiting for all runs to finish.', task_name=self.TASK_NAME)
            self.wait_all_samples_finish()

        Logger.success('Successfully scheduled {} sample(s).'.format(current_index), task_name=self.TASK_NAME)


@click.command(name='batch', context_settings=dict(ignore_unknown_options=True))
@click.option('-c', '--cmd', help='Command to run sample processing', default=None)
def batch(cmd):
    pipeline_parameters, file_patterns, exclude_patterns, param_types = \
        EnvironmentParametersParser({'nodes'}).collect_params_from_env()
    nodes = int(EnvironmentParametersParser.get_env_value('nodes', 1))
    if nodes < 1:
        raise RuntimeError('Number of nodes should be greater than zero.')
    pipeline = EnvironmentParametersParser.get_env_value('PIPELINE_ID')
    version = EnvironmentParametersParser.get_env_value('VERSION')
    run_id = EnvironmentParametersParser.get_env_value('RUN_ID')
    fastq_dir = bucket.normalize_path(EnvironmentParametersParser.get_env_value('fastq_dir'))
    instance_size = EnvironmentParametersParser.get_env_value('instance_size')
    instance_disk = EnvironmentParametersParser.get_env_value('instance_disk')
    docker_image = EnvironmentParametersParser.get_env_value('docker_image')
    run_files = FolderScanner(fastq_dir, file_patterns, exclude_patterns).find_files()
    wait_finish = EnvironmentParametersParser.has_flag('wait_finish')
    PipelineLauncher(run_files, file_patterns.keys(), run_id, pipeline, version, pipeline_parameters, param_types)\
        .launch(nodes, instance_size, instance_disk, docker_image, cmd, wait_finish=wait_finish)


if __name__ == '__main__':
    batch()
