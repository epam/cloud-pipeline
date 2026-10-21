import re
from typing import Callable
from time import time
from logging import Logger
from .create_logger import create_logger
from cpaibot.common.settings import settings


def pipeline_log(msg: str,
                 exception: BaseException | None = None,
                 exc_info: BaseException | None = None,
                 task: str | None = None,
                 task_completed: bool | None = False,
                 task_failed: bool | None = False):
    if not settings.CP_RUN_ID:
        return
    try:
        from ..pipeline_api import TaskStatus, Logger as PipelineLogger

        if exc_info:
            exception = exc_info

        status = TaskStatus.RUNNING
        if task_completed:
            if exception is not None:
                status = TaskStatus.FAILURE
            else:
                status = TaskStatus.SUCCESS
        if task_failed:
            status = TaskStatus.FAILURE

        PipelineLogger.log_task_event(task, msg, status, omit_console=True)

        if exception:
            from traceback import format_exc
            message = '%s\n%s' % (exception.__str__(), format_exc())
            PipelineLogger.log_task_event(task, message, status, omit_console=True)
    except:
        pass


class CpLogger:
    @staticmethod
    def from_logger(logger: Logger, task: str | None = None):
        return CpLogger(logger.name, task=task, logger=logger)

    def __init__(self,
                 name: str,
                 task: str | None = None,
                 file_name: str | None = None,
                 logger: Logger | None = None,
                 log_in_cloud_pipeline: bool | None = None):
        self.system_logger = create_logger(name, file_name=file_name) if logger is None else logger
        self.task = task
        if log_in_cloud_pipeline is None:
            log_in_cloud_pipeline = False
        self.log_in_cloud_pipeline = log_in_cloud_pipeline

    @staticmethod
    def get_safe_task_name(task: str) -> str:
        safe = re.sub(r'[^a-zA-Z0-9]', '-', task)
        return safe

    @staticmethod
    def measure_time() -> Callable[[], str]:
        start = time()

        def stop():
            end = time()
            return '%.2f sec' % (end - start)

        return stop

    def debug(self,
              msg: str,
              exception: BaseException | None = None,
              exc_info: BaseException | None = None,
              task: str | None = None,
              task_completed: bool | None = False,
              task_failed: bool | None = False,
              log_in_cloud_pipeline: bool | None  = False):
        message = msg
        task = task or self.task
        if task:
            message = '[%s] %s' % (task, msg)
        self.system_logger.debug(message)
        if exc_info and isinstance(exc_info, BaseException):
            exception = exc_info
        if exception:
            self.system_logger.debug(exception.__str__(),
                                     exc_info=exception is not None)
        if log_in_cloud_pipeline is None:
            log_in_cloud_pipeline = self.log_in_cloud_pipeline
        if log_in_cloud_pipeline:
            pipeline_log(msg,
                         exception=exception,
                         task=task,
                         task_completed=task_completed,
                         task_failed=task_failed)

    def info(self,
             msg: str,
             exception: BaseException | None = None,
             exc_info: BaseException | None = None,
             task: str | None = None,
             task_completed: bool | None = False,
             task_failed: bool | None = False,
             log_in_cloud_pipeline: bool | None = None):
        message = msg
        task = task or self.task
        self.system_logger.info(message)
        if exc_info and isinstance(exc_info, BaseException):
            exception = exc_info
        if exception:
            self.system_logger.info(exception.__str__(),
                                    exc_info=exception is not None)
        if log_in_cloud_pipeline is None:
            log_in_cloud_pipeline = self.log_in_cloud_pipeline
        if log_in_cloud_pipeline:
            pipeline_log(msg,
                         exception=exception,
                         task=task,
                         task_completed=task_completed,
                         task_failed=task_failed)

    def warning(self,
                msg: str,
                exception: BaseException | None = None,
                exc_info: BaseException | None = None,
                task: str | None = None,
                task_completed: bool | None = False,
                task_failed: bool | None = False,
                log_in_cloud_pipeline: bool | None = None):
        message = msg
        task = task or self.task
        if task:
            message = '[%s] %s' % (task, msg)
        self.system_logger.warning(message)
        if exc_info and isinstance(exc_info, BaseException):
            exception = exc_info
        if exception:
            self.system_logger.warning(exception.__str__(),
                                       exc_info=exception is not None)
        if log_in_cloud_pipeline is None:
            log_in_cloud_pipeline = self.log_in_cloud_pipeline
        if log_in_cloud_pipeline:
            pipeline_log(msg,
                         exception=exception,
                         task=task,
                         task_completed=task_completed,
                         task_failed=task_failed)

    def error(self,
              msg: str,
              exception: BaseException | None = None,
              exc_info: BaseException | None = None,
              task: str | None = None,
              task_failed: bool | None = False,
              log_in_cloud_pipeline: bool | None = None):
        message = msg
        task = task or self.task
        if task:
            message = '[%s] %s' % (task, msg)
        self.system_logger.error(message)
        if exc_info and isinstance(exc_info, BaseException):
            exception = exc_info
        if exception:
            self.system_logger.error(exception.__str__(),
                                     exc_info=exception is not None)
        if log_in_cloud_pipeline is None:
            log_in_cloud_pipeline = self.log_in_cloud_pipeline
        if log_in_cloud_pipeline:
            pipeline_log(msg,
                         exception=exception,
                         task=task,
                         task_failed=task_failed)

    def critical(self,
                 msg: str,
                 exception: BaseException | None = None,
                 exc_info: BaseException | None = None,
                 task: str | None = None,
                 task_failed: bool | None = False,
                 log_in_cloud_pipeline: bool | None = None):
        message = msg
        task = task or self.task
        if task:
            message = '[%s] %s' % (task, msg)
        self.system_logger.critical(message)
        if exc_info and isinstance(exc_info, BaseException):
            exception = exc_info
        if exception:
            self.system_logger.critical(exception.__str__(),
                                        exc_info=exception is not None)
        if log_in_cloud_pipeline is None:
            log_in_cloud_pipeline = self.log_in_cloud_pipeline
        if log_in_cloud_pipeline:
            pipeline_log(msg,
                         exception=exception,
                         task=task,
                         task_failed=task_failed)


default_logger = CpLogger('default')
