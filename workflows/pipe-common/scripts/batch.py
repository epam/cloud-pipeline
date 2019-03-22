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
import fnmatch
import time
from urlparse import urlparse
from pipeline import Logger, PipelineAPI, LoggedCommand


class AbstractTask:
    def __init__(self, name):
        self.name = name

    def fail_task(self, message):
        Logger.fail(message, task_name=self.name)
        raise RuntimeError(message)


class AbstractFolderScanner(AbstractTask):
    TASK_NAME = "FindFiles"
    MAX_ATTEMPTS = 3

    def __init__(self, folder, patterns, exclude_patterns):
        AbstractTask.__init__(self, self.TASK_NAME)
        self.folder = folder
        self.patterns = patterns
        self.exclude_patterns = exclude_patterns

    def get_path_without_bucket(self):
        parsed_path = urlparse(self.folder)
        return parsed_path.path

    def match_patterns(self, file_name, globs):
        for pattern in globs:
            if fnmatch.fnmatch(file_name, pattern):
                return True
        return False


class AbstractPipelineLauncher(AbstractTask):
    TASK_NAME = "LaunchSampleProcessing"
    # important! cmd template should be single-quoted to prevent parameter expansion
    CMD_TEMPLATE = "pipe run --yes --quiet --pipeline {pipe_id}@{version} --instance-disk {instance_disk} " \
                   "--instance-type {instance_type} --docker-image {docker_image} --cmd-template '{cmd}' " \
                   "--parent-id {parent}"
    SAMPLE_TEMPLATE = " --sample_name {sample_name} --sample_id {sample_id}"
    POLL_TIMEOUT = 30
    RETRY_COUNT = 10
    SAMPLE_ID = "Sample_ID"
    SAMPLE_NAME = "Sample_Name"

    def __init__(self, run_files, param_names, run_id, pipe_id, version, pipe_params, param_types):
        AbstractTask.__init__(self, self.TASK_NAME)
        self.samples_number = len(run_files)
        self.run_id = run_id
        self.run_files = run_files
        self.param_names = param_names
        self.pipe_id = pipe_id
        self.version = version
        self.api = PipelineAPI(os.environ['API'], 'logs')
        self.pipe_params = pipe_params
        self.child_id = None
        self.param_types = param_types

    def launch_pipeline(self, params, param_names, instance_size, instance_disk, docker_image, cmd,
                        sample=None):
        if not self.child_run_active():
            self.launch_child_run(params, param_names, cmd, instance_size, instance_disk, docker_image, sample=sample)
            return

        command = self.CMD_TEMPLATE.format(
            pipe_id=self.pipe_id,
            version=self.version,
            instance_disk=instance_disk,
            instance_type=instance_size,
            docker_image=docker_image,
            cmd=cmd,
            parent=self.run_id
        )
        if sample:
            command = command + self.SAMPLE_TEMPLATE.format(
                sample_name=sample[self.SAMPLE_NAME],
                sample_id=sample[self.SAMPLE_ID]
            )
        # add all pattern params
        index = 0
        for name in param_names:
            if sample:
                value = ','.join(params[name])
            else:
                value = params[index]
            command += " --{} input?{}".format(name, value)
            index = index + 1
        # add all other params
        for param, value in self.pipe_params.iteritems():
            if param.startswith('i_'):
                command += " --{} input?{}".format(self.change_parameter_name(param), value)
            elif param.startswith('c_'):
                command += " --{} common?{}".format(self.change_parameter_name(param), value)
            elif param.startswith('o_'):
                command += " --{} output?{}".format(self.change_parameter_name(param), value)
            else:
                command += " --{} {}".format(param, value)

        Logger.info('Starting pipeline with command: "{}".'.format(command), task_name=self.TASK_NAME)
        try:
            LoggedCommand(command, None, self.TASK_NAME).execute()
        except Exception as e:
            Logger.warn("Failed to launch sample processing with command: '{}'. Error: '{}'."
                        .format(command, e.message), task_name=self.TASK_NAME)

    def launch_child_run(self, params, param_names, cmd, instance_size, instance_disk, docker_image, sample=None):
        run_params = {'parent-id': self.run_id}
        if sample:
            run_params['sample_name'] = sample[self.SAMPLE_NAME]
            run_params['sample_id'] = sample[self.SAMPLE_ID]
        index = 0
        # add all pattern params
        for name in param_names:
            if sample:
                value = ','.join(params[name])
            else:
                value = params[index]
            run_params[name] = {
                'value': value,
                'type': 'input'
            }
            index = index + 1

        # add all other params
        for param, value in self.pipe_params.iteritems():
            param_type = None
            param_name = param
            real_value = self.normalize_value(value)
            if param.startswith('i_'):
                param_type = 'input'
                param_name = self.change_parameter_name(param)
            elif param.startswith('c_'):
                param_type = 'common'
                param_name = self.change_parameter_name(param)
            elif param.startswith('o_'):
                param_type = 'output'
                param_name = self.change_parameter_name(param)
            run_params[param_name] = {
                'value': real_value
            }
            if param_type is not None:
                run_params[param_name]['type'] = param_type
            else:
                run_params[param_name]['type'] = self.get_type_from_env(param_name)
        Logger.info("Starting child pipeline run on a parent node with parameters: '{}'."
                    .format(str(run_params)), task_name=self.TASK_NAME)
        try:
            run = self.api.launch_pipeline(self.pipe_id, self.version, run_params, parent_node_id=self.run_id, cmd=cmd,
                                           instance=instance_size, disk=instance_disk, docker=docker_image)
            self.child_id = run['id']
        except Exception as e:
            Logger.warn("Failed to launch sample processing with parameters: '{}'. Error: '{}'."
                        .format(str(run_params), e.message), task_name=self.TASK_NAME)
            self.child_id = None

    # to have possibilities to change way of naming new parameter in the batched pipeline
    @staticmethod
    def change_parameter_name(param):
        return param[2:]

    def get_running_samples(self):
        attempts = 0
        while attempts < self.RETRY_COUNT:
            try:
                child_runs = self.api.load_child_pipelines(self.run_id)
                count = 0
                for run in child_runs:
                    if run['status'] == 'RUNNING':
                        count = count + 1
                return count
            except Exception as e:
                Logger.warn("Failed to fetch running samples: {}.".format(e.message), task_name=self.TASK_NAME)
                attempts = attempts + 1
                time.sleep(self.POLL_TIMEOUT)
        Logger.fail("Exceeded maximum attempts to fetch running samples.")
        raise RuntimeError("Exceeded maximum attempts to fetch running samples.")

    def child_run_active(self):
        if self.child_id is None:
            return False
        attempts = 0
        while attempts < self.RETRY_COUNT:
            try:
                run = self.api.load_run(self.child_id)
                return run['status'] == 'RUNNING'
            except Exception as e:
                Logger.warn("Failed to fetch child run ID '' status: {}.".format(str(self.child_id), e.message),
                            task_name=self.TASK_NAME)
                attempts = attempts + 1
                time.sleep(self.POLL_TIMEOUT)
        Logger.fail("Exceeded maximum attempts to fetch child run status.")
        raise RuntimeError("Exceeded maximum attempts to fetch child run status.")

    def wait_all_samples_finish(self):
        running = self.get_running_samples()
        while running != 0:
            time.sleep(self.POLL_TIMEOUT)
            running = self.get_running_samples()

    def get_type_from_env(self, param_name):
        if param_name not in self.param_types or not self.param_types[param_name]:
            return 'string'
        else:
            return self.param_types[param_name]

    # remove escaped ENV values
    def normalize_value(self, value):
        return value.replace("\\$", "$")
