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
import subprocess
from datetime import datetime, timedelta

from pipeline.api import PipelineAPI, APIError
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger


DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"
NFS_TYPE = "NFS"

PAUSED = "PAUSED"
RUNNING = "RUNNING"


def cleanup_users():
    api_url = os.environ['API']
    run_id = os.environ['RUN_ID']

    cleanup_timeout = int(os.getenv('CP_CLEANUP_USERS_TIMEOUT_DAYS', '60'))
    dry_run = _extract_boolean_parameter('CP_DRY_RUN', 'true')

    logging_directory = os.getenv('CP_CLEANUP_USERS_LOG_DIR', os.getenv('LOG_DIR', '/var/log'))
    logging_level = os.getenv('CP_CLEANUP_USERS_LOGGING_LEVEL', 'ERROR')
    logging_level_local = os.getenv('CP_CLEANUP_USERS_LOGGING_LEVEL_LOCAL', 'DEBUG')
    logging_format = os.getenv('CP_CLEANUP_USERS_LOGGING_FORMAT', '%(asctime)s:%(levelname)s: %(message)s')

    logging.basicConfig(level=logging_level_local, format=logging_format,
                        filename=os.path.join(logging_directory, 'cleanup_users.log'))

    api = PipelineAPI(api_url=api_url, log_dir=logging_directory)
    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task='UsersCleanup', inner=logger)
    logger = LevelLogger(level=logging_level, inner=logger)
    logger = LocalLogger(inner=logger)

    # while True:
    try:
        # time.sleep(cleanup_timeout)
        logger.info('Starting users cleanup...')
        logger.info('Loading blocked users...')
        users = api.load_users()
        blocked_users = [user for user in users if user.get('blocked')]
        logger.info('Loaded {} blocked users.'.format(len(blocked_users)))
        if len(blocked_users) > 0:
            _cleanup_paused_instances(api, logger, blocked_users, dry_run)
            _cleanup_running_instances(api, logger, blocked_users, dry_run)
            _cleanup_tools(api, logger, blocked_users, dry_run)
            _cleanup_storages(api, logger, users, blocked_users, dry_run)
    except KeyboardInterrupt:
        logger.warning('Interrupted.')
        # break
    except Exception:
        logger.warning('Users cleanup has failed.', trace=True)
    except BaseException:
        logger.error('Users cleanup has failed completely.', trace=True)
        raise


def _extract_boolean_parameter(name, default='false'):
    parameter = os.getenv(name, default)
    return parameter.lower() == 'true'


def _clean_up(user, exp_days):
    return datetime.today() > datetime.strptime(user['blockDate'], DATE_FORMAT) + timedelta(exp_days) \
        if 'blockDate' in user.keys() else False


def _get_file_data_storages(data_storages):
    return [storage for storage in data_storages if storage.get('type') == NFS_TYPE]


def _get_cleanup_users(users, exp_date):
    return [user for user in users if _clean_up(user, exp_date)]


def _flatten(data):
    return [item for sublist in data for item in sublist]


def _get_tools(registries):
    groups = [registry.get('groups') for registry in registries]
    groups = _flatten(groups)
    tools = [group.get('tools') for group in groups if group.get('tools') is not None]
    return _flatten(tools)


def _cleanup_paused_instances(api, logger, users, dry_run):
    exp_days = os.getenv('CP_PAUSED_EXP_DAYS')
    if exp_days is None:
        logger.info('CP_PAUSED_EXP_DAYS not defined. Paused instances cleanup skipped...')
        return
    logger.info('Paused instances cleanup...')
    exp_days = int(exp_days)
    paused_instances_users = _get_cleanup_users(users, exp_days)
    if len(paused_instances_users) == 0:
        return
    user_names = [u.get('userName') for u in paused_instances_users]
    logger.debug('Loading paused instances...')
    runs = api.load_pipelines_by_owners(user_names, [PAUSED])
    logger.debug('Loaded {} paused instances.'.format(len(runs)))
    for run in runs:
        try:
            run_id = run.get('id')
            logger.debug('Processing paused instance {}.'.format(run_id))
            if not dry_run:
                api.terminate_run(run_id)
            logger.debug('Paused instance {} terminated.'.format(run_id))
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            raise
        except Exception:
            logger.warning('Paused instances cleanup has failed.')
    logger.info('Finishing paused instances cleanup...')


def _cleanup_running_instances(api, logger, users, dry_run):
    exp_days = os.getenv('CP_RUNNING_EXP_DAYS')
    if exp_days is None:
        logger.info('CP_RUNNING_EXP_DAYS not defined. Running instances cleanup skipped...')
        return
    logger.info('Running instances cleanup...')
    exp_days = int(exp_days)
    running_instances_users = _get_cleanup_users(users, exp_days)
    if len(running_instances_users) == 0:
        return
    user_names = [u.get('userName') for u in running_instances_users]
    logger.debug('Loading running instances...')
    runs = api.load_pipelines_by_owners(user_names, [RUNNING])
    logger.debug('Loaded {} running instances.'.format(len(runs)))
    if len(runs) > 0:
        logger.debug('Processing running instances...')
    for run in runs:
        try:
            run_id = run.get('id')
            if not dry_run:
                api.stop_run(run_id)
            logger.debug('Running instance {} stopped.'.format(run_id))
        except KeyboardInterrupt:
            logger.warning('Interrupted.', trace=True)
            raise
        except Exception:
            logger.warning('Running instances cleanup has failed.')
    logger.info('Finishing running instances cleanup...')


def _cleanup_tools(api, logger, users, dry_run):
    exp_days = os.getenv('CP_TOOL_EXP_DAYS')
    if exp_days is None:
        logger.info('CP_TOOL_EXP_DAYS not defined. Tools cleanup skipped...')
        return
    logger.info('Running tools cleanup...')
    exp_days = int(exp_days)
    tools_users = _get_cleanup_users(users, exp_days)
    if len(tools_users) == 0:
        return
    logger.debug('Loading tools...')
    registries = api.docker_registry_load_all()
    tools = _get_tools(registries)
    blocked_user_names = [user.get('userName') for user in tools_users]
    blocked_users_tools = [tool for tool in tools if tool.get('owner') in blocked_user_names]
    logger.debug('Loaded {} tools.'.format(len(blocked_users_tools)))
    if len(blocked_users_tools) > 0:
        logger.debug('Processing tools...')
    for tool in blocked_users_tools:
        try:
            tool_id = tool.get('id')
            logger.debug('Processing tool {}, id {}'.format(tool.get('image'), tool_id))
            logger.debug('Checking permissions')
            permissions = api.get_permissions(tool_id, 'TOOL')
            if permissions is not None:
                logger.debug("Tool  won't be deleted because it is shared with other users.")
                continue
            logger.debug('Checking links')
            if _is_parent(tool_id, tools):
                logger.debug("Tool {} won't be deleted because it has linked tool(s).".format(tool_id))
                continue
            logger.debug('Removing tool {}'.format(tool_id))
            if not dry_run:
                api.delete_tool(tool.get('image'))
            logger.debug('Tool {} deleted.'.format(tool_id))
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            raise
        except Exception:
            logger.warning('Tool cleanup has failed.')
    logger.info('Finishing tools cleanup...')


def _cleanup_storages(api, logger, users, blocked_users, dry_run):
    default_exp_days = os.getenv('CP_DATASTORAGE_DEFAULT_EXP_DAYS')
    general_exp_days = os.getenv('CP_DATASTORAGE_GENERAL_EXP_DAYS')
    if default_exp_days is None and general_exp_days is None:
        return
    logger.info('Running data storages cleanup...')
    logger.debug('Loading data storages...')
    storages = api.data_storage_load_all()
    blocked_user_names = [user.get('userName') for user in blocked_users]
    blocked_user_storages = [storage for storage in storages if storage.get('owner') in blocked_user_names]
    file_storages = _get_file_data_storages(blocked_user_storages)
    logger.debug('Loaded {} file storages.'.format(len(file_storages)))
    if len(file_storages) > 0:
        logger.debug('Processing data storages...')
    for storage in file_storages:
        try:
            storage_id = storage.get('id')
            logger.debug('Processing storage id {}, path {}'.format(storage_id, storage.get('path')))
            logger.debug('Checking permissions')
            permissions = api.get_permissions(storage_id, 'DATA_STORAGE')
            if permissions is not None:
                logger.debug("Storage won't be deleted because it is shared with other users.")
                continue
            logger.debug('Checking shared copies')
            if _has_shared_copy(storages, storage):
                logger.debug("Storage won't be deleted because it has shared copy.")
                continue
            owner = [user for user in users if user.get('userName') == storage.get('owner')].pop(0)
            default_storage_users = _get_default_storage_users(users, storage_id)
            if len(default_storage_users) > 0:
                logger.debug('Storage {} is default storage'.format(storage_id))
                _cleanup_default_datastorage(api, logger, storage, owner, default_storage_users, dry_run,
                                             default_exp_days)
            else:
                logger.debug('Storage {} is general storage'.format(storage_id))
                _cleanup_general_datastorage(api, logger, storage, owner, dry_run, general_exp_days)
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            raise
        except Exception:
            logger.warning('Data storage cleanup has failed.')
    logger.info('Finishing data storages cleanup...')


def _has_shared_copy(storages, storage):
    path = '{}/'.format(storage.get('path'))
    copies = [s for s in storages if path in s.get('path')
              and s.get('id') != storage.get('id')
              and s.get('owner') != storage.get('owner')]
    return len(copies) > 0


def _is_parent(tool_id, tools):
    links = [t for t in tools if t.get('link') == tool_id]
    return len(links) > 0


def _is_home_storage(users, storage_id):
    return storage_id in [u['defaultStorageId'] for u in users]


def _get_default_storage_users(users, storage_id):
    return [user for user in users if user.get('defaultStorageId') == storage_id]


def _execute_command(cmd):
    process = subprocess.Popen(cmd)
    exit_code = process.wait()
    return exit_code


def _replace_datastorage_content(logger, folder, storage):
    result = _execute_command("/bin/mount -t nfs {} {}".format(folder, storage))
    if result == 0:
        logger.info('Mounted')
    else:
        logger.info('Not mounted')
        return False
    result = _execute_command('{} "{}" "{}" --recursive --force --quiet'.format(PIPE_STORAGE_CP, storage, folder))
    if result == 0:
        logger.info('Copied')
    else:
        logger.info('Not copied')
        return False
    result = _execute_command("/bin/unmount {}".format(folder))
    if result == 0:
        logger.info('Unmounted')
    else:
        logger.info('Not unmounted')
        return False
    return True


def _cleanup_default_datastorage(api, logger, storage, user, default_storage_users, dry_run, exp_days):
    if exp_days is None:
        logger.debug('CP_DATASTORAGE_DEFAULT_EXP_DAYS not defined. Storage {} cleanup skipped...'.format(storage.get('id')))
        return
    if not _clean_up(user, int(exp_days)):
        logger.debug('Not time to cleanup. Storage {} cleanup skipped...'.format(storage.get('id')))
        return
    storage_items = api.load_datastorage_items(storage.get('id'))
    if storage_items is not None:
        dedicated_bucket = os.getenv('CP_DEDICATED_BUCKET')
        if dedicated_bucket is None:
            logger.debug('CP_DEDICATED_BUCKET not defined. Storage {} cleanup skipped...'.format(storage.get('id')))
            return
        if not dry_run:
            result = _replace_datastorage_content(logger, storage.get('path'), dedicated_bucket)
            if not result:
                logger.warning("Data backup failed, storage can't be deleted.")
                return
    for default_storage_user in default_storage_users:
        api.delete_user_home_storage(default_storage_user.get('id'))
    api.delete_datastorage(storage.get('id'), False)
    logger.debug('Datastorage {} deleted.'.format(storage.get('id')))


def _cleanup_general_datastorage(api, logger, storage, user, dry_run, exp_days):
    if exp_days is None:
        logger.debug('CP_DATASTORAGE_GENERAL_EXP_DAYS not defined. Storage {} cleanup skipped...'.format(storage.get('id')))
        return
    if not _clean_up(user, int(exp_days)):
        logger.debug('Not time to cleanup. Storage {} cleanup skipped...'.format(storage.get('id')))
        return
    if not dry_run:
        api.delete_datastorage(storage.get('id'), False)
    logger.debug('Datastorage {} deleted.'.format(storage.get('id')))


if __name__ == '__main__':
    cleanup_users()
