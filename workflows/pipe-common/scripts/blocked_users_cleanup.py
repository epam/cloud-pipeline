# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import time
from datetime import datetime, timedelta

from pipeline.api import PipelineAPI, APIError
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger
from pipeline.utils.ssh import LocalExecutor, LoggingExecutor

PIPE_STORAGE_CP = "pipe storage cp"
DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"
NFS_TYPE = "NFS"

def cleanup_users():
    api_url = os.environ['API']
    run_id = os.environ['RUN_ID']

    cleanup_timeout = int(os.getenv('CP_CAP_CLEANUP_USERS_TIMEOUT_DAYS', '60'))
    dry_run = bool(os.getenv('CP_CAP_DRY_RUN', 'true'))

    logging_directory = os.getenv('CP_CAP_CLEANUP_USERS_LOG_DIR', os.getenv('LOG_DIR', '/var/log'))
    logging_level = os.getenv('CP_CAP_CLEANUP_USERS_LOGGING_LEVEL', 'ERROR')
    logging_level_local = os.getenv('CP_CAP_CLEANUP_USERS_LOGGING_LEVEL_LOCAL', 'DEBUG')
    logging_format = os.getenv('CP_CAP_CLEANUP_USERS_LOGGING_FORMAT', '%(asctime)s:%(levelname)s: %(message)s')

    logging.basicConfig(level=logging_level_local, format=logging_format,
                        filename=os.path.join(logging_directory, 'cleanup_users.log'))

    api = PipelineAPI(api_url=api_url, log_dir=logging_directory)
    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task='UsersCleanup', inner=logger)
    logger = LevelLogger(level=logging_level, inner=logger)
    logger = LocalLogger(inner=logger)

    while True:
        try:
            time.sleep(cleanup_timeout)
            logger.info('Starting users cleanup...')
            logger.info('Loading blocked users...')
            users = _get_blocked_users(api)
            logger.info('Loaded {} blocked users.'.format(len(users)))
            if len(users) > 0:
                _cleanup_paused_instances(api, logger, users, dry_run)
                _cleanup_running_instances(api, logger, users, dry_run)
                _cleanup_tools(api, logger, users, dry_run)
                _cleanup_storages(api, logger, users, dry_run)
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            break
        except Exception:
            logger.warning('Users cleanup has failed.', trace=True)
        except BaseException:
            logger.error('Users cleanup has failed completely.', trace=True)
            raise

def _clean_up(user, exp_days):
    return datetime.today() > datetime.strptime(user['blockDate'], DATE_FORMAT) + timedelta(exp_days)

def _get_blocked_users(api):
    users = api.load_users()
    return list(filter(lambda user: user['blocked'], users))

def _get_file_data_storages(data_storages):
    return list(filter(lambda ds: ds['type'] == NFS_TYPE, data_storages))

def _get_cleanup_users(users, exp_date):
    return list(filter(lambda u: _clean_up(u, exp_date), users))

def _flatten(data):
    return [item for sublist in data for item in sublist]

def _get_by_key(list_of_dict, key):
    new_list_of_dict = []
    for item in list_of_dict:
        if key in item.keys():
            new_list_of_dict.append(item[key])
    return new_list_of_dict

def _get_tools(registries):
    groups = _get_by_key(registries, 'groups')
    tools = _get_by_key(_flatten(groups), 'tools')
    return _flatten(tools)

def _cleanup_paused_instances(api, logger, users, dry_run):
    exp_days = os.getenv('CP_CAP_PAUSED_EXP_DAYS')
    if exp_days is None:
        return
    exp_days = int(exp_days)
    paused_instances_users = _get_cleanup_users(users, exp_days)
    if len(paused_instances_users) == 0:
        return
    user_names = [u['userName'] for u in paused_instances_users]
    logger.info('Loading paused instances...')
    pipelines = api.load_pipelines_by_owners(user_names, list(api.TaskStatus.PAUSED))
    logger.info('Processing paused instances...')
    for pipeline in pipelines:
        try:
            if not dry_run:
                api.terminate_run(pipeline['id'])
            logger.info('Paused instance {} terminated.'.format(pipeline['id'])
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            raise
        except Exception:
            logger.warning('Paused instances cleanup has failed.', trace=True)
    logger.info('Finishing paused instances cleanup...')

def _cleanup_running_instances(api, logger, users, dry_run):
    exp_days = os.getenv('CP_CAP_RUNNING_EXP_DAYS')
    if exp_days is None:
        return
    exp_days = int(exp_days)
    running_instances_users = _get_cleanup_users(users, exp_days)
    if len(running_instances_users) == 0:
        return
    user_names = [u['userName'] for u in running_instances_users]
    logger.info('Loading running instances...')
    pipelines = api.load_pipelines_by_owners(user_names, list(api.TaskStatus.RUNNING))
    logger.info('Processing running instances...')
    for pipeline in pipelines:
        try:
            if not dry_run:
                api.stop_pipeline(pipeline['id'])
            logger.info('Running instance {} stopped.'.format(pipeline['id']))
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            raise
        except Exception:
            logger.warning('Running instances cleanup has failed.', trace=True)
    logger.info('Finishing running instances cleanup...')

def _cleanup_tools(api, logger, users, dry_run):
    exp_days = os.getenv('CP_CAP_TOOL_EXP_DAYS')
    if exp_days is None:
        return
    exp_days = int(exp_days)
    tools_users = _get_cleanup_users(users, exp_days)
    if len(tools_users) == 0:
        return
    logger.info('Loading tools...')
    registries = api.docker_registry_load_all()
    tools = _get_tools(registries)
    logger.info('Processing tools...')
    for tool in tools:
        try:
            if tool['owner'] in tools_users:
                permissions = api.get_permissions(tool['id'], 'TOOL')
                if permissions is None:
                    if not dry_run:
                        api.delete_tool(tool['image'])
                    logger.info('Tool {} deleted.'.format(tool['id']))
                else:
                    logger.info('Tool {} was not deleted because it is shared with other users.'.format(tool['id']))
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            raise
        except Exception:
            logger.warning('Tools cleanup has failed.', trace=True)
    logger.info('Finishing tools cleanup...')

def _cleanup_storages(api, logger, users, dry_run):
    logger.info('Loading data storages...')
    storages = api.data_storage_load_all()
    logger.info('Processing data storages...')
    file_storages = _get_file_data_storages(storages)
    for storage in file_storages:
        try:
            user = next(filter(lambda u: u['userName'] == storage['owner'], users))
            if _is_home_storage(users, storage['id']):
                _cleanup_home_datastorage(api, logger, storage, user, dry_run)
            else:
                _cleanup_general_datastorage(api, logger, storage, user, dry_run)
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            raise
        except Exception:
            logger.warning('Data storage cleanup has failed.', trace=True)
    logger.info('Finishing data storages cleanup...')

def _is_home_storage(users, storage_id):
    return storage_id in [u['defaultStorageId'] for u in users]

def _replace_datastorage_content(logger, source, target):
    executor = LocalExecutor()
    executor = LoggingExecutor(logger=logger, inner=executor)
    executor.execute('{} "{}" "{}" --recursive --force --quiet'.format(PIPE_STORAGE_CP, source, target))

def _cleanup_home_datastorage(api, logger, storage, user, dry_run):
    exp_days = os.getenv('CP_CAP_DATASTORE_DEFAULT_EXP_DAYS')
    if exp_days is None:
        return
    if not _clean_up(user, exp_days):
        return
    storage_items = api.load_datastorage_items(storage['id'])
    if 'payload' in storage_items.keys():
        dedicated_bucket = os.getenv('CP_DEDICATED_BUCKET')
        if dedicated_bucket is None:
            return
        else:
            if not dry_run:
                _replace_datastorage_content(logger, storage['path'], dedicated_bucket)
                api.delete_datastorage(storage['id'], False)
            logger.info('Datastorage {} deleted.'.format(storage['id']))
    else:
        api.delete_datastorage(storage['id'], False)
        logger.info('Datastorage {} deleted.'.format(storage['id']))

def _cleanup_general_datastorage(api, logger, storage, user, dry_run):
    exp_days = os.getenv('CP_CAP_DATASTORE_GENERAL_EXP_DAYS')
    if exp_days is None:
        return
    if not _clean_up(user, exp_days):
        return
    permissions = api.get_permissions(storage['id'], 'DATA_STORAGE')
    if permissions is None:
        if not dry_run:
            api.delete_datastorage(storage['id'], False)
        logger.info('Datastorage {} deleted.'.format(storage['id']))

if __name__ == '__main__':
    cleanup_users()
