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
from pipeline.api import LogEntry, TaskStatus, PipelineAPI

class bcolors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'

    STATUS_COLORS = {TaskStatus.RUNNING: '',
                     TaskStatus.SUCCESS: OKGREEN,
                     TaskStatus.FAILURE: FAIL}

    @staticmethod
    def colored(message, status):
        return bcolors.STATUS_COLORS[status] + message + bcolors.ENDC

class Logger:

    @staticmethod
    def info(message,
             task_name=None,
             run_id=None,
             api_url=None,
             log_dir=None,
             omit_console=False):

        Logger.log_task_event(task_name,
                              message,
                              status=TaskStatus.RUNNING,
                              run_id=run_id,
                              api_url=api_url,
                              log_dir=log_dir,
                              omit_console=omit_console)


    @staticmethod
    def warn(message,
             task_name=None,
             run_id=None,
             api_url=None,
             log_dir=None,
             omit_console=False):

        Logger.log_task_event(task_name,
                              bcolors.WARNING + message + bcolors.ENDC,
                              status=TaskStatus.RUNNING,
                              run_id=run_id,
                              api_url=api_url,
                              log_dir=log_dir,
                              omit_console=omit_console)


    @staticmethod
    def success(message,
                task_name=None,
                run_id=None,
                api_url=None,
                log_dir=None,
                omit_console=False):

        Logger.log_task_event(task_name,
                              message,
                              status=TaskStatus.SUCCESS,
                              run_id=run_id,
                              api_url=api_url,
                              log_dir=log_dir,
                              omit_console=omit_console)

    
    @staticmethod
    def fail(message,
             task_name=None,
             run_id=None,
             api_url=None,
             log_dir=None,
             omit_console=False):

        Logger.log_task_event(task_name,
                              message,
                              status=TaskStatus.FAILURE,
                              run_id=run_id,
                              api_url=api_url,
                              log_dir=log_dir,
                              omit_console=omit_console)



    @staticmethod
    def log_task_event(task_name, message, status=TaskStatus.RUNNING, run_id=None, instance=None, api_url=None, log_dir=None, omit_console=False):
        _run_id = run_id
        _instance = instance
        _api_url = api_url
        _log_dir = log_dir
        _task_name = task_name
        _pipeline_name = os.environ.get('PIPELINE_NAME')

        if not _pipeline_name:
            _pipeline_name = 'Pipeline-output'

        if not _task_name:
            _task_name = _pipeline_name

        if not _run_id:
            _run_id = os.environ.get('RUN_ID')
                  
        if not _instance:
            pipeline_name = _pipeline_name
            _instance = "{}-{}".format(pipeline_name, _run_id)

        if not _api_url:
            _api_url = os.environ.get('API')

        if not _log_dir:
            _log_dir = os.environ.get('LOG_DIR')

        log_entry = LogEntry(_run_id,
                             status,
                             bcolors.colored(message, status),
                             _task_name,
                             instance)
        pipe_api = PipelineAPI(_api_url, _log_dir)
        pipe_api.log_event(log_entry, omit_console=omit_console)
