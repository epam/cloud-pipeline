# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import sys

from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger, ResilientLogger
from pipeline.utils.package import install_package
from pipeline.utils.path import mkdir
from pipeline.utils.ssh import LocalExecutor, LoggingExecutor

_MANAGER_CLUSTER_ROLE = 'master'
_ETC_DIRECTORY = '/etc'
_ETC_DIRECTORY_SHARED = '/etc-shared'
_ETC_FILE_PATHS = ['passwd', 'shadow', 'group', 'gshadow', 'sudoers']


class SharedUsersConfigurationError(RuntimeError):
    pass


def configure_shared_users():
    api_url = os.environ['API']
    run_id = os.environ['RUN_ID']
    parent_id = os.getenv('parent_id')
    cluster_role = os.getenv('cluster_role', _MANAGER_CLUSTER_ROLE)
    logging_dir = os.getenv('CP_CAP_SHARE_USERS_LOGDIR', os.getenv('LOG_DIR', '/var/log'))
    logging_level_run = os.getenv('CP_CAP_SHARE_USERS_LOGGING_LEVEL_RUN', 'ERROR')
    logging_level_file = os.getenv('CP_CAP_SHARE_USERS_LOGGING_LEVEL_FILE', 'DEBUG')
    logging_level_console = os.getenv('CP_CAP_SHARE_USERS_LOGGING_LEVEL_CONSOLE', 'INFO')
    logging_format = os.getenv('CP_CAP_SHARE_USERS_LOGGING_FORMAT', '%(asctime)s:%(levelname)s: %(message)s')
    logging_task = os.getenv('CP_CAP_SHARE_USERS_LOGGING_TASK', 'InitializeSharedUsers')
    logging_file = os.path.join(logging_dir, 'configure_shared_users.log')

    logging_formatter = logging.Formatter(logging_format)

    logging_logger_root = logging.getLogger()
    logging_logger_root.setLevel(logging.WARNING)

    logging_logger = logging.getLogger(name=logging_task)
    logging_logger.setLevel(logging.DEBUG)

    if not logging_logger.handlers:
        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setLevel(logging_level_console)
        console_handler.setFormatter(logging_formatter)
        logging_logger.addHandler(console_handler)

        file_handler = logging.FileHandler(logging_file)
        file_handler.setLevel(logging_level_file)
        file_handler.setFormatter(logging_formatter)
        logging_logger.addHandler(file_handler)

    api = PipelineAPI(api_url=api_url, log_dir=logging_dir)
    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task=logging_task, inner=logger)
    logger = LevelLogger(level=logging_level_run, inner=logger)
    logger = LocalLogger(logger=logging_logger, inner=logger)
    logger = ResilientLogger(inner=logger, fallback=LocalLogger(logger=logging_logger))

    executor = LocalExecutor()
    executor = LoggingExecutor(logger=logger, inner=executor)

    try:
        logger.info('Configuring shared users and groups management...')

        if cluster_role == _MANAGER_CLUSTER_ROLE:
            logger.info('Skipping shared users and groups management because run cluster role is manager...')
            return
        if not parent_id:
            logger.info('Skipping shared users and groups management because parent id was not found...')
            return

        logger.info('Loading parent run pod ip...')
        parent_run = api.load_run_efficiently(parent_id)
        parent_run_host = parent_run.get('podIP')
        if not parent_run_host:
            logger.error('Parent run pod ip was not found.')
            raise SharedUsersConfigurationError('Parent run pod ip was not found.')

        logger.info('Installing sshfs...')
        install_package(rpm='fuse-sshfs', nonrpm='sshfs', logger=logger)

        logger.info('Mounting etc directory from manager node...')
        mkdir(_ETC_DIRECTORY_SHARED)
        executor.execute('sshfs -o ro,allow_other root@{parent_run_host}:{etc_directory} {etc_directory_shared}'
                         .format(parent_run_host=parent_run_host,
                                 etc_directory=_ETC_DIRECTORY, etc_directory_shared=_ETC_DIRECTORY_SHARED))

        logger.info('Mounting users and groups from manager node etc directory...')
        for etc_file_path in _ETC_FILE_PATHS:
            file_path = os.path.join(_ETC_DIRECTORY, etc_file_path)
            file_path_shared = os.path.join(_ETC_DIRECTORY_SHARED, etc_file_path)
            if not os.path.exists(file_path_shared):
                logger.warning('Skipping etc file path because it does not exist...')
                continue
            executor.execute('mount -o ro,bind,allow_other {file_path_shared} {file_path}'
                             .format(file_path=file_path, file_path_shared=file_path_shared))

        logger.success('Shared users and groups management was successfully configured.')
    except BaseException:
        logger.error('Shared users and groups management configuration has failed.', trace=True)
        raise


if __name__ == '__main__':
    configure_shared_users()
