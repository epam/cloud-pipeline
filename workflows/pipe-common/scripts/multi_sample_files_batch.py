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

import os
import click
import pipeline.common
from pipeline import Logger, PipelineAPI, S3Bucket, EnvironmentParametersParser
from batch import AbstractFolderScanner, AbstractPipelineLauncher

bucket = S3Bucket()


class FolderScanner(AbstractFolderScanner):
    def __init__(self, folder):
        AbstractFolderScanner.__init__(self, folder, None, None)
        self.folder = folder

    def find_files(self, recursive=False):
        Logger.info("Starting parsing input directory: {}.".format(self.folder), task_name=self.TASK_NAME)
        all_files = bucket.ls_s3(self.folder, self.MAX_ATTEMPTS, recursive=recursive)
        result = [[] for x in xrange(len(all_files))]
        index = 0
        for file in all_files:
            result[index].append(os.path.join(self.folder, file))
            index = index + 1
        Logger.success("Found {} directories to process.".format(len(result)), task_name=self.TASK_NAME)
        return result


class PipelineLauncher(AbstractPipelineLauncher):

    def __init__(self, run_dirs, param_names, run_id, pipe_id, version, pipe_params, param_types):
        AbstractPipelineLauncher.__init__(self, run_dirs, param_names, run_id, pipe_id, version, pipe_params,
                                          param_types)
        self.run_id = run_id
        self.run_dirs = run_dirs
        self.param_names = param_names
        self.pipe_id = pipe_id
        self.version = version
        self.api = PipelineAPI(os.environ['API'], 'logs')
        self.pipe_params = pipe_params
        self.child_id = None
        self.param_types = param_types

    def launch(self, instance_size, instance_disk, docker_image, cmd, wait_finish=False):
        running = 0
        Logger.info('Starting {} sample(s) scheduling.'.format(len(self.run_dirs)), task_name=self.TASK_NAME)
        for folder in self.run_dirs:
            self.launch_pipeline(folder, self.param_names, instance_size, instance_disk, docker_image, cmd)
            running = running + 1
            Logger.info('Processing {} sample(s).'.format(running), task_name=self.TASK_NAME)

        Logger.info('Successfully scheduled {} sample(s).'.format(running), task_name=self.TASK_NAME)
        if wait_finish:
            Logger.info('Waiting for all runs to finish.', task_name=self.TASK_NAME)
            self.wait_all_samples_finish()
        Logger.success('All child pipeline successfully finished.', task_name=self.TASK_NAME)

    @staticmethod
    def change_parameter_name(param):
        return param


@click.command(name='batch', context_settings=dict(ignore_unknown_options=True))
@click.option('-c', '--cmd', help='Command to run sample processing', default=None)
def batch(cmd):
    pipeline_parameters, ignored, ignored_2, param_types = EnvironmentParametersParser({'nodes'}).collect_params_from_env()

    for param, value in pipeline_parameters.iteritems():
        pipeline_parameters[param] = resolve_env_params(param, value)

    pipeline_obj = EnvironmentParametersParser.get_env_value('PIPELINE_ID')
    version = EnvironmentParametersParser.get_env_value('VERSION')
    run_id = EnvironmentParametersParser.get_env_value('RUN_ID')
    instance_size = EnvironmentParametersParser.get_env_value('instance_size')
    instance_disk = EnvironmentParametersParser.get_env_value('instance_disk')
    docker_image = EnvironmentParametersParser.get_env_value('docker_image')

    folder_with_samples_files = os.environ['o_dir']
    folder_with_samples_files = resolve_env_params('o_dir', folder_with_samples_files)
    if not folder_with_samples_files.endswith('/'):
        folder_with_samples_files = folder_with_samples_files + '/'

    node_samples_files = FolderScanner(folder_with_samples_files).find_files()
    samples_dir_param_name = ['samples_file']
    PipelineLauncher(node_samples_files, samples_dir_param_name, run_id, pipeline_obj, version, pipeline_parameters, param_types)\
        .launch(instance_size, instance_disk, docker_image, cmd, True)


def resolve_env_params(name, value):
    exit_code, result = pipeline.common.get_cmd_command_output('echo ' + value)
    if exit_code == 0:
        return result[0]
    else:
        raise RuntimeError("Can't resolve env vars for: {name}, code: {code}".format(name=name, code=exit_code))

if __name__ == '__main__':
    batch()
