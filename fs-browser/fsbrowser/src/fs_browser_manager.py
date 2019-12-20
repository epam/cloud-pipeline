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
import uuid
import shutil
from multiprocessing.pool import ThreadPool

from fsbrowser.src.cloud_pipeline_api_provider import CloudPipelineApiProvider
from fsbrowser.src.model.file import File
from fsbrowser.src.model.folder import Folder
from fsbrowser.src.transfer_task import TransferTask, TaskStatus


class FsBrowserManager(object):

    def __init__(self, working_directory, process_count, logger, storage, follow_symlinks, tmp):
        self.tasks = {}
        self.pool = ThreadPool(processes=process_count)
        self.working_directory = working_directory
        self.logger = logger
        self.storage_name, self.storage_path = self._parse_transfer_storage_path(storage)
        self.follow_symlinks = follow_symlinks
        self._create_tmp_dir_if_needed(tmp)
        self.tmp = tmp

    def list(self, path):
        items = []
        full_path = os.path.join(self.working_directory, path)
        if os.path.isfile(full_path):
            items.append(File(path, full_path).to_json())
        else:
            for item_name in os.listdir(full_path):
                full_item_path = os.path.join(full_path, item_name)
                if os.path.isfile(full_item_path):
                    items.append(File(item_name, full_item_path).to_json())
                else:
                    items.append(Folder(item_name, full_item_path).to_json())
        return items

    def run_download(self, path):
        task_id = str(uuid.uuid4().hex)
        task = TransferTask(task_id, self.storage_name, self.storage_path, self.logger)
        self.tasks.update({task_id: task})
        self.pool.apply_async(task.download, [path, self.working_directory, self.tmp, self.follow_symlinks])
        return task_id

    def init_upload(self, path):
        task_id = str(uuid.uuid4().hex)
        task = TransferTask(task_id, self.storage_name, self.storage_path, self.logger)
        task.upload_path = path
        self.tasks.update({task_id: task})
        pipeline_client = CloudPipelineApiProvider(self.logger.log_dir)
        storage_id = pipeline_client.load_storage_id_by_name(self.storage_name)
        upload_url = pipeline_client.get_upload_url(storage_id, os.path.join(self.storage_path, task_id, path))
        return task_id, upload_url

    def run_upload(self, task_id):
        task = self._check_task_exists(task_id)
        if task.status != TaskStatus.PENDING:
            raise RuntimeError("Failed to start upload task: expected task state 'pending' but actual %s" % task.status)
        self.pool.apply_async(task.upload, [self.working_directory])
        return task_id

    def cancel(self, task_id):
        task = self._check_task_exists(task_id)
        if TaskStatus.is_terminal(task.status):
            raise RuntimeError('The task %s is already completed with status %s' % (task_id, task.status))
        task.cancel(self.working_directory)
        return task.to_json()

    def get_task_status(self, task_id):
        task = self.tasks[task_id]
        return task.to_json()

    def delete(self, path):
        full_path = os.path.join(self.working_directory, path)
        if os.path.isfile(full_path):
            os.remove(full_path)
        else:
            shutil.rmtree(full_path)
        self.logger.log("Data by path '%s' has been successfully deleted" % full_path)
        return path

    @staticmethod
    def _parse_transfer_storage_path(storage):
        if not storage:
            raise RuntimeError('Transfer storage path must be specified')
        parts = storage.split("/", 1)
        storage_name = parts[0]
        storage_path = ''
        if len(parts) > 1 and parts[1]:
            storage_path = parts[1]
        return storage_name, storage_path

    def _check_task_exists(self, task_id):
        if task_id in self.tasks:
            return self.tasks[task_id]
        raise RuntimeError('Requested task %s does not exists' % task_id)

    @staticmethod
    def _create_tmp_dir_if_needed(tmp_dir):
        if os.path.exists(tmp_dir) and os.path.isdir(tmp_dir):
            return
        os.makedirs(tmp_dir)
