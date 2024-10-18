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

import logging
import os
import traceback
from abc import ABCMeta, abstractmethod

import datetime

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
    def info(self, message, task=None, trace=False):
        pass

    @abstractmethod
    def debug(self, message, task=None, trace=False):
        pass

    @abstractmethod
    def warning(self, message, task=None, trace=False):
        pass

    @abstractmethod
    def success(self, message, task=None, trace=False):
        pass

    @abstractmethod
    def error(self, message, task=None, trace=False):
        pass


class PrintLogger(CloudPipelineLogger):

    _DATE_FORMAT = '%Y-%m-%d %H:%M:%S'
    _DATE_WITH_MILLISECONDS = '%s.%03d'

    def __init__(self, inner=None):
        self._inner = inner

    def info(self, message, task=None, trace=False):
        self._log(message=message, level='INFO', trace=trace)
        if self._inner:
            self._inner.info(message, task=task, trace=trace)

    def debug(self, message, task=None, trace=False):
        self._log(message=message, level='DEBUG', trace=trace)
        if self._inner:
            self._inner.debug(message, task=task, trace=trace)

    def warning(self, message, task=None, trace=False):
        self._log(message=message, level='WARNING', trace=trace)
        if self._inner:
            self._inner.warning(message, task=task, trace=trace)

    def success(self, message, task=None, trace=False):
        self._log(message=message, level='INFO', trace=trace)
        if self._inner:
            self._inner.success(message, task=task, trace=trace)

    def error(self, message, task=None, trace=False):
        self._log(message=message, level='ERROR', trace=trace)
        if self._inner:
            self._inner.error(message, task=task, trace=trace)

    def _log(self, message, level, trace):
        now_utc = datetime.datetime.utcnow()
        formatted_dt = self._DATE_WITH_MILLISECONDS % (now_utc.strftime(self._DATE_FORMAT), now_utc.microsecond / 1000)
        if trace:
            stacktrace = traceback.format_exc()
            message += ' ' + stacktrace
        print(formatted_dt + ' [' + level + '] ' + message)


class LocalLogger(CloudPipelineLogger):

    def __init__(self, logger=None, inner=None):
        self._logger = logger or logging
        self._inner = inner

    def info(self, message, task=None, trace=False):
        self._logger.info(message, exc_info=trace)
        if self._inner:
            self._inner.info(message, task=task, trace=trace)

    def debug(self, message, task=None, trace=False):
        self._logger.debug(message, exc_info=trace)
        if self._inner:
            self._inner.debug(message, task=task, trace=trace)

    def warning(self, message, task=None, trace=False):
        self._logger.warning(message, exc_info=trace)
        if self._inner:
            self._inner.warning(message, task=task, trace=trace)

    def success(self, message, task=None, trace=False):
        self._logger.info(message, exc_info=trace)
        if self._inner:
            self._inner.success(message, task=task, trace=trace)

    def error(self, message, task=None, trace=False):
        self._logger.error(message, exc_info=trace)
        if self._inner:
            self._inner.error(message, task=task, trace=trace)


class ExplicitLogger(CloudPipelineLogger):

    def __init__(self, level, inner):
        self._inner = inner
        self._level = level.strip().lower()

    def info(self, message, task=None, trace=False):
        self._log(message=message, task=task, trace=trace)

    def debug(self, message, task=None, trace=False):
        self._log(message=message, task=task, trace=trace)

    def warning(self, message, task=None, trace=False):
        self._log(message=message, task=task, trace=trace)

    def success(self, message, task=None, trace=False):
        self._log(message=message, task=task, trace=trace)

    def error(self, message, task=None, trace=False):
        self._log(message=message, task=task, trace=trace)

    def _log(self, message, task, trace):
        getattr(self._inner, self._level)(message, task=task, trace=trace)


class LevelLogger(CloudPipelineLogger):

    _levels = ['ERROR', 'WARNING', 'INFO', 'DEBUG']
    _error_idx = _levels.index('ERROR')
    _warn_idx = _levels.index('WARNING')
    _info_idx = _levels.index('INFO')
    _debug_idx = _levels.index('DEBUG')

    def __init__(self, level, inner):
        self._level_idx = self._levels.index(level)
        self._inner = inner

    def info(self, message, task=None, trace=False):
        if self._applies(self._info_idx):
            self._inner.info(message, task=task, trace=trace)

    def debug(self, message, task=None, trace=False):
        if self._applies(self._debug_idx):
            self._inner.debug(message, task=task, trace=trace)

    def warning(self, message, task=None, trace=False):
        if self._applies(self._warn_idx):
            self._inner.warning(message, task=task, trace=trace)

    def success(self, message, task=None, trace=False):
        if self._applies(self._info_idx):
            self._inner.success(message, task=task, trace=trace)

    def error(self, message, task=None, trace=False):
        if self._applies(self._error_idx):
            self._inner.error(message, task=task, trace=trace)

    def _applies(self, level_idx):
        return self._level_idx >= level_idx


class TaskLogger(CloudPipelineLogger):

    def __init__(self, task, inner):
        self._task = task
        self._inner = inner

    def info(self, message, task=None, trace=False):
        self._inner.info(message, task=task or self._task, trace=trace)

    def debug(self, message, task=None, trace=False):
        self._inner.debug(message, task=task or self._task, trace=trace)

    def warning(self, message, task=None, trace=False):
        self._inner.warning(message, task=task or self._task, trace=trace)

    def success(self, message, task=None, trace=False):
        self._inner.success(message, task=task or self._task, trace=trace)

    def error(self, message, task=None, trace=False):
        self._inner.error(message, task=task or self._task, trace=trace)


class RunLogger(CloudPipelineLogger):

    _DATE_FORMAT = '%Y-%m-%d %H:%M:%S'
    _DATE_WITH_MILLISECONDS = '%s.%03d'

    def __init__(self, api, run_id, inner=None):
        self._api = api
        self._run_id = run_id
        self._inner = inner

    def info(self, message, task=None, trace=False):
        self._log(message=message, task=task, status='RUNNING', trace=trace)
        if self._inner:
            self._inner.info(message, task=task, trace=trace)

    def debug(self, message, task=None, trace=False):
        self._log(message=message, task=task, status='RUNNING', trace=trace)
        if self._inner:
            self._inner.debug(message, task=task, trace=trace)

    def warning(self, message, task=None, trace=False):
        self._log(message=message, task=task, status='RUNNING', trace=trace)
        if self._inner:
            self._inner.warning(message, task=task, trace=trace)

    def success(self, message, task=None, trace=False):
        self._log(message=message, task=task, status='SUCCESS', trace=trace)
        if self._inner:
            self._inner.success(message, task=task, trace=trace)

    def error(self, message, task=None, trace=False):
        self._log(message=message, task=task, status='FAILURE', trace=trace)
        if self._inner:
            self._inner.error(message, task=task, trace=trace)

    def _log(self, message, task, status, trace):
        now_utc = datetime.datetime.utcnow()
        formatted_dt = self._DATE_WITH_MILLISECONDS % (now_utc.strftime(self._DATE_FORMAT), now_utc.microsecond / 1000)
        if trace:
            stacktrace = traceback.format_exc()
            message += ' ' + stacktrace
        self._api.log_efficiently(run_id=self._run_id, message=message, task=task, status=status, date=formatted_dt)


class ResilientLogger(CloudPipelineLogger):

    def __init__(self, inner, fallback):
        self._inner = inner
        self._fallback = fallback

    def info(self, message, task=None, trace=False):
        self._log(self._inner.info, self._fallback.error, message, task=task, trace=trace)

    def debug(self, message, task=None, trace=False):
        self._log(self._inner.debug, self._fallback.error, message, task=task, trace=trace)

    def warning(self, message, task=None, trace=False):
        self._log(self._inner.warning, self._fallback.error, message, task=task, trace=trace)

    def success(self, message, task=None, trace=False):
        self._log(self._inner.success, self._fallback.error, message, task=task, trace=trace)

    def error(self, message, task=None, trace=False):
        self._log(self._inner.error, self._fallback.error, message, task=task, trace=trace)

    def _log(self, inner_method, fallback_method, message, task=None, trace=False):
        try:
            inner_method(message, task=task, trace=trace)
        except Exception:
            try:
                fallback_method('Logging error', task=task, trace=True)
            except Exception:
                pass
