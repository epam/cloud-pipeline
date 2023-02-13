# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import datetime
import logging
import os
from abc import ABCMeta, abstractmethod

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


class CloudPipelineLogger:
    __metaclass__ = ABCMeta

    @abstractmethod
    def info(self, message, task=None):
        pass

    @abstractmethod
    def debug(self, message, task=None):
        pass

    @abstractmethod
    def warning(self, message, task=None):
        pass

    @abstractmethod
    def success(self, message, task=None):
        pass

    @abstractmethod
    def error(self, message, task=None):
        pass


class LocalLogger(CloudPipelineLogger):

    def __init__(self, inner=None):
        self._inner = inner

    def info(self, message, task=None):
        logging.info(message)
        if self._inner:
            self._inner.info(message, task)

    def debug(self, message, task=None):
        logging.debug(message)
        if self._inner:
            self._inner.debug(message, task)

    def warning(self, message, task=None):
        logging.warning(message)
        if self._inner:
            self._inner.warning(message, task)

    def success(self, message, task=None):
        logging.info(message)
        if self._inner:
            self._inner.success(message, task)

    def error(self, message, task=None):
        logging.error(message)
        if self._inner:
            self._inner.error(message, task)


class LevelLogger(CloudPipelineLogger):

    _levels = ['ERROR', 'WARNING', 'INFO', 'DEBUG']
    _error_idx = _levels.index('ERROR')
    _warn_idx = _levels.index('WARNING')
    _info_idx = _levels.index('INFO')
    _debug_idx = _levels.index('DEBUG')

    def __init__(self, level, inner):
        self._level_idx = self._levels.index(level)
        self._inner = inner

    def info(self, message, task=None):
        if self._applies(self._info_idx):
            self._inner.info(message, task)

    def debug(self, message, task=None):
        if self._applies(self._debug_idx):
            self._inner.debug(message, task)

    def warning(self, message, task=None):
        if self._applies(self._warn_idx):
            self._inner.warning(message, task)

    def success(self, message, task=None):
        if self._applies(self._info_idx):
            self._inner.success(message, task)

    def error(self, message, task=None):
        if self._applies(self._error_idx):
            self._inner.error(message, task)

    def _applies(self, level_idx):
        return self._level_idx >= level_idx


class TaskLogger(CloudPipelineLogger):

    def __init__(self, task, inner):
        self._task = task
        self._inner = inner

    def info(self, message, task=None):
        self._inner.info(message, task or self._task)

    def debug(self, message, task=None):
        self._inner.debug(message, task or self._task)

    def warning(self, message, task=None):
        self._inner.warning(message, task or self._task)

    def success(self, message, task=None):
        self._inner.success(message, task or self._task)

    def error(self, message, task=None):
        self._inner.error(message, task or self._task)


class RunLogger(CloudPipelineLogger):

    _DATE_FORMAT = '%Y-%m-%d %H:%M:%S'
    _DATE_WITH_MILLISECONDS = '%s.%03d'

    def __init__(self, api, run_id, inner=None):
        self._api = api
        self._run_id = run_id
        self._inner = inner

    def info(self, message, task=None):
        self._log(message=message, task=task, status='RUNNING')
        if self._inner:
            self._inner.info(message, task)

    def debug(self, message, task=None):
        self._log(message=message, task=task, status='RUNNING')
        if self._inner:
            self._inner.debug(message, task)

    def warning(self, message, task=None):
        self._log(message=message, task=task, status='RUNNING')
        if self._inner:
            self._inner.warning(message, task)

    def success(self, message, task=None):
        self._log(message=message, task=task, status='SUCCESS')
        if self._inner:
            self._inner.success(message, task)

    def error(self, message, task=None):
        self._log(message=message, task=task, status='FAILURE')
        if self._inner:
            self._inner.error(message, task)

    def _log(self, message, task, status):
        now_utc = datetime.datetime.utcnow()
        formatted_dt = self._DATE_WITH_MILLISECONDS % (now_utc.strftime(self._DATE_FORMAT), now_utc.microsecond / 1000)
        self._api.log_efficiently(run_id=self._run_id, message=message, task=task, status=status, date=formatted_dt)
