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
import pipeline.api
import pipeline.common

PIPE_STORAGE_CP = "pipe storage cp"


# TODO: rename to CloudBucket
class S3Bucket:

    def execute_command(self, command, max_attempts):
        attempts = 0
        success = False
        stderr = None
        while attempts < max_attempts and not success:
            attempts += 1
            exit_code, stdout, stderr = pipeline.common.execute_cmd_command_and_get_stdout_stderr(command)
            success = exit_code == 0
        if not success:
            raise RuntimeError("Execution failed due to the reason: %s" % stderr)

    def get_cmd_stdout(self, command, max_attempts):
        attempts = 0
        success = False
        result = []
        while attempts < max_attempts and not success:
            attempts += 1
            return_value, result = pipeline.common.get_cmd_command_output(command)
            success = return_value == 0
        if not success:
            raise RuntimeError("Failed to execute command: {}.".format(command))
        return result

    def get_s3_command(self, target, source):
        if self.check_path_is_file(target) or self.check_path_is_file(source):
            return "aws s3 cp"
        else:
            return "aws s3 sync"

    def configure_aws(self, parameter, value):
        config_command = "aws configure set {} {}".format(parameter, value)
        pipeline.common.execute_cmd_command(config_command)

    def ls_s3(self, target, max_attempts, recursive=False):
        ls_file_command = "aws s3 ls {}".format(target)
        if recursive:
            ls_file_command = "{} --recursive".format(ls_file_command)
        listing = self.get_cmd_stdout(ls_file_command, max_attempts)
        result = []
        for file in listing:
            result.append(file.strip().split()[-1])
        return result

    def pipe_ls(self, target, max_attempts, recursive=False, all=False, show_info=False):
        """
        :return: tuple: type, path, size
        """
        ls_file_command = 'pipe storage ls %s' % target
        if recursive:
            ls_file_command += ' -r'
        if all:
            ls_file_command += ' -a'
        if show_info:
            ls_file_command += ' -l'
        listing = self.get_cmd_stdout(ls_file_command, max_attempts)
        result = []
        for path in listing[1:]:
            fields = path.strip().split()
            if not fields:
                continue
            result.append((fields[0], fields[-1], fields[-2]))
        return result

    def copy_s3(self, source, target, max_attempts):
        upload_file_command = "aws s3 cp {} {} --only-show-errors --sse aws:kms".format(source, target)
        self.execute_command(upload_file_command, max_attempts)

    def rm_folder_s3(self, source, max_attempts):
        remove_file_command = "aws s3 rm {} --recursive --only-show-errors".format(source)
        self.execute_command(remove_file_command, max_attempts)

    def sync_s3(self, source, target, max_attempts, exclude=None):
        s3_cmd = self.get_s3_command(source, target)
        upload_file_command = "{} {} {} --only-show-errors --sse aws:kms --no-follow-symlinks".format(s3_cmd, source, target)
        if exclude:
            upload_file_command += " --exclude " + exclude
        self.execute_command(upload_file_command, max_attempts)

    def sync_s3_with_rules(self, source, target, max_attempts, datastorage_rules_file):
        allowed_rules = ""
        rules = pipeline.api.DataStorageRule.read_from_file(datastorage_rules_file)
        for rule in rules:
            if rule.move_to_sts:
                allowed_rules += " --include \"{}\"".format(rule.file_mask)
        s3_cmd = self.get_s3_command(source, target)
        upload_file_command = "{} {} {} --only-show-errors --sse aws:kms --no-follow-symlinks --exclude \"*\" {}".format(s3_cmd, source, target, allowed_rules)
        self.execute_command(upload_file_command, max_attempts)

    def maybe_copy_s3(self, run_dir, path, target, max_attempts, datastorage_rules_file):
        rules = pipeline.api.DataStorageRule.read_from_file(datastorage_rules_file)
        if rules and pipeline.api.DataStorageRule.match_any(rules, path):
            self.copy_s3(run_dir + path, target, max_attempts)

    def maybe_sync_s3(self, run_dir, path, target, max_attempts, datastorage_rules_file, exclude=None):
        rules = pipeline.api.DataStorageRule.read_from_file(datastorage_rules_file)
        if rules and pipeline.api.DataStorageRule.match_any(rules, path):
            self.sync_s3(run_dir + path, target, max_attempts, exclude)

    def pipe_copy(self, source, target, max_attempts, exclude=None):
        upload_file_command = "{} {} {} {} --recursive --force --quiet".format(PIPE_STORAGE_CP, source, target,
                                                                       self.__build_tags_command())
        if exclude:
            upload_file_command += " --exclude " + exclude
        self.execute_command(upload_file_command, max_attempts)

    def pipe_copy_with_rules(self, source, target, max_attempts, datastorage_rules_file):
        allowed_rules = ""
        rules = pipeline.api.DataStorageRule.read_from_file(datastorage_rules_file)
        for rule in rules:
            if rule.move_to_sts:
                allowed_rules += " --include \"{}\"".format(rule.file_mask)
        upload_file_command = "{} {} {} {} {} --recursive --force --quiet".format(
            PIPE_STORAGE_CP, source, target, allowed_rules, self.__build_tags_command())
        self.execute_command(upload_file_command, max_attempts)

    def build_pipe_cp_command(self, source, target, exclude=None, include=None, file_list=None):
        upload_file_command = '%s %s %s %s --recursive --force --quiet' % \
                              (PIPE_STORAGE_CP, source, target, self.__build_tags_command())
        if exclude:
            for glob in exclude:
                upload_file_command += ' --exclude "%s"' % glob
        if include:
            for glob in include:
                upload_file_command += ' --include \'%s\'' % glob
        if file_list:
            upload_file_command += ' --file-list "%s"' % file_list
        return upload_file_command

    def check_path_is_file(self, path):
        if path.endswith('/'):
            return False

        base = os.path.basename(path)
        try:
            path_contents = self.ls_s3(path, 1)

            for contents_item in path_contents:
                if contents_item == base:
                    return True

            return False
        except:
            return False

    def normalize_path(self, path):
        return path if self.check_path_is_file(path) else os.path.join(path, '')

    @classmethod
    def __build_tags_command(cls):
        command = []
        env_vars = os.environ
        if 'RUN_ID' in env_vars:
            command.append("--tags")
            command.append('"CP_RUN_ID={}"'.format(env_vars['RUN_ID']))
        if 'PIPELINE_ID' in env_vars:
            command.append("--tags")
            command.append('"CP_JOB_ID={}"'.format(env_vars['PIPELINE_ID']))
        if 'PIPELINE_NAME' in env_vars:
            command.append("--tags")
            command.append('"CP_JOB_NAME={}"'.format(env_vars['PIPELINE_NAME']))
        if 'PIPELINE_VERSION' in env_vars:
            command.append("--tags")
            command.append('"CP_JOB_VERSION={}"'.format(env_vars['PIPELINE_VERSION']))
        if 'RUN_CONFIG_NAME' in env_vars:
            command.append("--tags")
            command.append('"CP_JOB_CONFIGURATION={}"'.format(env_vars['RUN_CONFIG_NAME']))
        if 'docker_image' in env_vars:
            command.append("--tags")
            command.append('"CP_DOCKER_IMAGE={}"'.format(env_vars['docker_image']))
        instance_size = None
        node_count = 1
        cluster_cores = None
        if 'instance_size' in env_vars:
            instance_size = env_vars['instance_size']
        if 'node_count' in env_vars and env_vars['node_count'].isdigit():
            node_count = node_count + int(env_vars['node_count'])
        if 'CLOUD_PIPELINE_CLUSTER_CORES' in env_vars:
            cluster_cores = env_vars['CLOUD_PIPELINE_CLUSTER_CORES']
        if instance_size:
            command.append("--tags")
            instance_description = instance_size
            if node_count:
                instance_description += ":{}".format(str(node_count))
            if cluster_cores:
                instance_description += ":{}".format(cluster_cores)
            command.append('"CP_CALC_CONFIG={}"'.format(instance_description))
        return " ".join(command)
