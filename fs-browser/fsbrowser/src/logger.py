# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import datetime
import time

from fsbrowser.src.api.cloud_pipeline_api_provider import CloudPipelineApiProvider

FS_BROWSER_TASK = "FsBrowserTask"
DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"


class TaskStatus:
    SUCCESS, FAILURE, RUNNING, STOPPED, PAUSED = range(5)


class BrowserLogger(object):

    def __init__(self, run_id=None, log_dir=None):
        self.run_id = run_id
        self.log_dir = log_dir
        if run_id:
            self.api_url = os.environ.get('API')
            self.api_token = os.environ.get('API_TOKEN')
            self.status = TaskStatus.RUNNING

    def log(self, message):
        if self.run_id:
            self._log_task_event(task_name=FS_BROWSER_TASK,
                                 message='[{}] {}'.format(self.run_id, message),
                                 status=self.status,
                                 run_id=self.run_id)
            if self.log_dir and not os.path.exists(self.log_dir):
                os.makedirs(self.log_dir)
            log_file_name = "{}.log".format(FS_BROWSER_TASK)
            log_text_formatted = "[{}]\t{}\t{}\n".format(self._build_current_date(), self.status, FS_BROWSER_TASK)
            log_path = os.path.join(self.log_dir, log_file_name)
            with open(log_path, "a") as log_file:
                log_file.write(log_text_formatted)
                log_file.write(message)
                if not message.endswith("\n"):
                    log_file.write("\n")
        else:
            print(message)

    def _log_task_event(self, task_name, message, status=TaskStatus.RUNNING, run_id=None):
        _run_id = run_id
        _task_name = task_name
        _pipeline_name = os.environ.get('PIPELINE_NAME')

        if not _pipeline_name:
            _pipeline_name = 'Pipeline-output'

        if not _task_name:
            _task_name = _pipeline_name

        if not _run_id:
            _run_id = os.environ.get('RUN_ID')

        data = {
            'runId': _run_id,
            'date': self._build_current_date(),
            'status': status,
            'taskName': task_name,
            'logText': message
        }
        pipe_api = CloudPipelineApiProvider()
        pipe_api.log_event(_run_id, data)

    @staticmethod
    def _build_current_date():
        current_date = datetime.datetime.utcfromtimestamp(time.time()).strftime(DATE_FORMAT)
        return current_date[0:len(current_date) - 3]

