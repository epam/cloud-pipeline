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
import traceback
import tarfile
import subprocess

from fsbrowser.src.cloud_pipeline_api_provider import CloudPipelineApiProvider
from fsbrowser.src.logger import BrowserLogger

TAF_GZ_EXTENSION = '.tar.gz'
DELIMITER = '/'
TAR_GZ_PERMISSIONS = 0774  # -rwxrwxr--


class TaskStatus(object):
    PENDING = 'pending'
    SUCCESS = 'success'
    RUNNING = 'running'
    FAILURE = 'failure'
    CANCELED = 'canceled'

    @staticmethod
    def is_terminal(status):
        return status == TaskStatus.SUCCESS or status == TaskStatus.FAILURE or status == TaskStatus.CANCELED


class TransferTask(object):

    def __init__(self, task_id, storage_name, storage_path='', logger=BrowserLogger()):
        self.task_id = task_id
        self.storage_name = storage_name
        self.status = TaskStatus.PENDING
        self.process = None
        self.message = None
        self.result = None
        self.path = None
        self.logger = logger
        self.storage_path = storage_path

    def success(self, result=None):
        self.status = TaskStatus.SUCCESS
        if result:
            self.result = result

    def failure(self, e):
        self.status = TaskStatus.FAILURE
        self.message = e.__str__()

    def running(self):
        self.status = TaskStatus.RUNNING

    def to_json(self):
        result = {'status': self.status}
        if self.message:
            result.update({'message': self.message})
        if self.result:
            result.update({'result': self.result})
        return result

    def cancel(self, working_directory):
        self.status = TaskStatus.CANCELED
        if self.process:
            self.logger.log("Stopping process with PID %s" % str(self.process.pid))
            self.process.kill()
            self.logger.log("Process %s has been stopped" % str(self.process.pid))
        if self.path:
            full_path = os.path.join(working_directory, self.path)
            full_directory_path = os.path.dirname(full_path)
            for file_name in os.listdir(full_directory_path):
                tmp_path = os.path.join(full_directory_path, file_name)
                if os.path.isfile(tmp_path) and str(self.task_id) in file_name:
                    os.remove(tmp_path)
                    self.logger.log('File %s has been removed' % tmp_path)

    def download(self, source_path, working_directory):
        try:
            if self.status != TaskStatus.PENDING:
                self.logger.log("Failed to start download task: expected task state 'pending' but actual %s"
                                % self.status)
                return
            self.running()
            source_path = source_path.strip(DELIMITER)
            full_source_path = os.path.join(working_directory, source_path)
            compressed = not os.path.isfile(full_source_path)
            if compressed:
                self.logger.log("Starting to compress folder for download")
                compressed_name = full_source_path + TAF_GZ_EXTENSION
                self.compress_directory(compressed_name, full_source_path)
                full_source_path = compressed_name
                source_path = source_path + TAF_GZ_EXTENSION
            source_file_name = os.path.basename(source_path)
            full_destination_path = os.path.join(self.storage_name, self.storage_path, self.task_id, source_file_name)
            if self.status == TaskStatus.CANCELED:
                self.logger.log('Cancel initiated by user')
                return
            self.pipe_storage_cp(full_source_path, 'cp://%s' % full_destination_path)
            pipeline_api = CloudPipelineApiProvider(self.logger.log_dir)
            storage_id = pipeline_api.load_storage_id_by_name(self.storage_name)
            url = pipeline_api.get_download_url(storage_id, os.path.join(self.storage_path, self.task_id,
                                                                         source_file_name))
            if compressed:
                os.remove(full_source_path)
            self.success(url)
            self.logger.log("Data successfully uploaded to bucket %s" % self.storage_name)
        except Exception as e:
            self.logger.log(traceback.format_exc())
            if self.status != TaskStatus.CANCELED:
                self.failure(e)

    def upload(self, working_directory):
        try:
            if self.status != TaskStatus.PENDING:
                self.logger.log("Failed to start download task: expected task state 'pending' but actual %s"
                                % self.status)
                return
            self.running()
            full_source_path = 'cp://%s' % os.path.join(self.storage_name, self.storage_path, self.task_id, self.path)
            full_destination_path = os.path.join(working_directory, self.path)
            tmp_destination_path = os.path.join(os.path.dirname(full_destination_path), self.task_id)
            self.pipe_storage_cp(full_source_path, tmp_destination_path)
            os.rename(tmp_destination_path, full_destination_path)
            self.success()
            self.logger.log("Data successfully downloaded from bucket %s to %s"
                            % (self.storage_name, full_destination_path))
        except Exception as e:
            self.logger.log(traceback.format_exc())
            if self.status != TaskStatus.CANCELED:
                self.failure(e)

    def pipe_storage_cp(self, source, destination, force=False):
        self.logger.log("Strarting to transfer data from %s to %s" % (source, destination))
        command = ['pipe', 'storage', 'cp', source, destination]
        if force:
            command.append('--force')
        return self.get_command_output(command, True)

    def get_command_output(self, cmd, process_task):
        exit_code, stdout, stderr = self.execute_command(cmd, process_task)
        if exit_code != 0:
            raise RuntimeError("stdout: %s\nstderr: %s" % (stdout, stderr))
        return stdout, stderr

    def execute_command(self, cmd, process_task):
        process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if process_task:
            self.process = process
        stdout = self.read_stdout(process.stdout)
        stderr = self.read_output(process.stderr)
        exit_code = process.wait()
        return exit_code, stdout, stderr

    @staticmethod
    def read_output(output):
        result = []
        line = output.readline()
        while line:
            line = line.strip()
            if line:
                result.append(line)
            line = output.readline()
        return result

    @staticmethod
    def read_stdout(stdout):
        line = stdout.readline()
        result = ""
        while line:
            line = line.strip()
            if line:
                result = line
            line = stdout.readline()
        return result

    @staticmethod
    def set_permissions(tarinfo):
        tarinfo.mode = TAR_GZ_PERMISSIONS
        return tarinfo

    @staticmethod
    def compress_directory(output_filename, source_dir):
        with tarfile.open(output_filename, "w:gz") as tar:
            tar.add(source_dir, filter=TransferTask.set_permissions)
