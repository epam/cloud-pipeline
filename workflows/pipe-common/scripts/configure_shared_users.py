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

import logging
import os
import traceback

from pipeline.api import PipelineAPI
from pipeline.common.common import execute
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger
from pipeline.utils.package import install_package
from pipeline.utils.path import mkdir

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
    logging_directory = os.getenv('CP_CAP_SHARE_USERS_LOGDIR', os.getenv('LOG_DIR', '/var/log'))
    logging_level = os.getenv('CP_CAP_SHARE_USERS_LOGGING_LEVEL', 'ERROR')
    logging_level_local = os.getenv('CP_CAP_SHARE_USERS_LOGGING_LEVEL_LOCAL', 'DEBUG')
    logging_format = os.getenv('CP_CAP_SHARE_USERS_LOGGING_FORMAT', '%(asctime)s:%(levelname)s: %(message)s')

    logging.basicConfig(level=logging_level_local, format=logging_format,
                        filename=os.path.join(logging_directory, 'configure_shared_users.log'))

    api = PipelineAPI(api_url=api_url, log_dir=logging_directory)
    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task='InitializeSharedUsers', inner=logger)
    logger = LevelLogger(level=logging_level, inner=logger)
    logger = LocalLogger(inner=logger)

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
        execute('sshfs -o ro,allow_other root@{parent_run_host}:{etc_directory} {etc_directory_shared}'
                .format(parent_run_host=parent_run_host,
                        etc_directory=_ETC_DIRECTORY, etc_directory_shared=_ETC_DIRECTORY_SHARED),
                logger=logger)

        logger.info('Mounting users and groups from manager node etc directory...')
        for etc_file_path in _ETC_FILE_PATHS:
            file_path = os.path.join(_ETC_DIRECTORY, etc_file_path)
            file_path_shared = os.path.join(_ETC_DIRECTORY_SHARED, etc_file_path)
            if not os.path.exists(file_path_shared):
                logger.warning('Skipping etc file path because it does not exist...')
                continue
            execute('mount -o ro,bind,allow_other {file_path_shared} {file_path}'
                    .format(file_path=file_path, file_path_shared=file_path_shared),
                    logger=logger)

        logger.success('Shared users and groups management was successfully configured.')
    except BaseException as e:
        traceback.print_exc()
        stacktrace = traceback.format_exc()
        logger.error('Shared users and groups management configuration has failed: {} {}'.format(e, stacktrace))
        raise


if __name__ == '__main__':
    configure_shared_users()
