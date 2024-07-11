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
import shutil
import stat
import sys
import time
from functools import reduce

from pipeline.api import PipelineAPI, APIError
from pipeline.api.token import RefreshingToken
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger, ResilientLogger
from pipeline.utils.account import create_user
from pipeline.utils.path import mkdir
from pipeline.utils.ssh import LocalExecutor, LoggingExecutor

_ROOT_HOME_DIR = '/root'
_ROOT_SSH_DIR = os.path.join(_ROOT_HOME_DIR, '.ssh')
_SSH_FILE_PATHS = ['id_rsa', 'id_rsa.pub', 'authorized_keys']


def sync_users():
    api_url = os.environ['API']
    run_id = os.environ['RUN_ID']
    shared_dir = os.getenv('SHARED_ROOT', '/common')
    root_home_dir = os.getenv('CP_CAP_SYNC_USERS_HOME_DIR', os.path.join(shared_dir, 'home'))
    uid_seed_default = int(os.getenv('CP_CAP_UID_SEED', '70000'))
    user_name_case = os.getenv('CP_CAP_USER_NAME_CASE', 'default')
    user_name_metadata_key = os.getenv('CP_CAP_USER_NAME_METADATA_KEY', 'local_user_name')
    sync_timeout = int(os.getenv('CP_CAP_SYNC_USERS_TIMEOUT_SECONDS', '60'))
    owner_user_name = os.getenv('OWNER', 'root')
    sudo_enabled = os.getenv('CP_CAP_SUDO_ENABLE', 'true').lower().strip() in ['true', 'yes']
    sudo_user_names = os.getenv('CP_CAP_SUDO_USERS', 'PIPE_ADMIN').split(',')
    sudo_group_names = os.getenv('CP_CAP_SUDO_GROUPS', 'ROLE_ADMIN').split(',')

    logging_dir = os.getenv('CP_CAP_SYNC_USERS_LOG_DIR', os.getenv('LOG_DIR', '/var/log'))
    logging_level_run = os.getenv('CP_CAP_SYNC_USERS_LOGGING_LEVEL_RUN', 'INFO')
    logging_level_file = os.getenv('CP_CAP_SYNC_USERS_LOGGING_LEVEL_LOCAL', 'DEBUG')
    logging_level_console = os.getenv('CP_CAP_SYNC_USERS_LOGGING_LEVEL_CONSOLE', 'INFO')
    logging_format = os.getenv('CP_CAP_SYNC_USERS_LOGGING_FORMAT', '%(asctime)s:%(levelname)s: %(message)s')
    logging_task = os.getenv('CP_CAP_SYNC_USERS_LOGGING_TASK', 'UsersSynchronization')
    logging_file = os.path.join(logging_dir, 'sync_users.log')

    mkdir(os.path.dirname(logging_file))

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

    api = PipelineAPI(api_url=api_url, log_dir=logging_dir, token=RefreshingToken())
    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task=logging_task, inner=logger)
    logger = LevelLogger(level=logging_level_run, inner=logger)
    logger = LocalLogger(logger=logging_logger, inner=logger)
    logger = ResilientLogger(inner=logger, fallback=LocalLogger(logger=logging_logger))

    logger.info('Initiating users synchronisation...')

    executor = LocalExecutor()
    executor = LoggingExecutor(logger=logger, inner=executor)

    while True:
        try:
            time.sleep(sync_timeout)
            logger.info('Starting users synchronization...')

            logger.debug('Loading preferences...')
            try:
                uid_seed = int(api.get_preference_value('launch.uid.seed') or uid_seed_default)
            except APIError:
                uid_seed = uid_seed_default
            logger.debug('Loaded uid seed: {}.', uid_seed)

            logger.debug('Loading local users...')
            local_user_names = set(_get_local_user_names())
            logger.info('Loaded {} local users.'.format(len(local_user_names)))
            logger.debug('Loaded local users: {}'.format(local_user_names))

            logger.debug('Loading shared users and groups...')
            run_user_names, run_group_names = _get_run_shared_users_and_groups_names(api, run_id)
            shared_user_names = set()
            shared_user_names.update(run_user_names)
            shared_user_names.update(_get_group_user_names(api, run_group_names))
            if owner_user_name in shared_user_names:
                shared_user_names.remove(owner_user_name)
            logger.info('Loaded {} shared users.'.format(len(shared_user_names)))
            logger.debug('Loaded shared users: {}'.format(shared_user_names))

            logger.debug('Loading owner users and groups...')
            owner_user_names = set()
            owner_user_names.update(sudo_user_names)
            owner_user_names.update(_get_group_user_names(api, sudo_group_names))
            logger.info('Loaded {} owner users.'.format(len(owner_user_names)))

            logger.debug('Loading user details...')
            all_users = api.load_users()
            logger.debug('Loaded {} user details.'.format(len(all_users)))

            logger.debug('Resolving shared user details...')
            shared_users_dict = {}
            for user in all_users:
                user_id = user.get('id')
                user_name = user.get('userName')
                if not user_id or not user_name:
                    continue
                if user_name not in shared_user_names:
                    continue
                shared_users_dict[user_id] = user
            logger.debug('Resolved {} shared user details.'.format(len(shared_users_dict)))

            logger.debug('Loading shared users metadata...')
            try:
                shared_users_metadata = api.load_all_metadata_efficiently(shared_users_dict.keys(), 'PIPELINE_USER')
            except APIError:
                logger.warning('Shared users metadata loading has failed.', trace=True)
                shared_users_metadata = []
            logger.debug('Loaded {} shared users metadata.'.format(len(shared_users_metadata)))

            logger.debug('Merging shared users details and metadata...')
            for metadata_entry in shared_users_metadata:
                user_id = metadata_entry.get('entity', {}).get('entityId', 0)
                user_metadata = metadata_entry.get('data', {})
                user = shared_users_dict.get(user_id, {})
                user['userLocalName'] = user_metadata.get(user_name_metadata_key, {}).get('value')
            logger.debug('Merged {} shared users details and metadata.'.format(len(shared_users_metadata)))

            logger.debug('Checking {} shared users existence...'.format(len(shared_users_dict)))
            for user in shared_users_dict.values():
                user_id = user['id']
                user_login_name = user['userName']
                user_local_name = user.get('userLocalName')
                user_name = user_local_name or _resolve_user_name(user_login_name, user_name_case)
                if user_name in local_user_names:
                    continue
                try:
                    logger.info('Creating {} ({}) user...'.format(user_name, user_login_name))
                    user_uid, user_gid, user_home_dir = _create_account(user_name, user_id, uid_seed, root_home_dir,
                                                                        executor, logger)
                    _configure_ssh_keys(user_name, user_uid, user_gid, user_home_dir, executor, logger)
                    if sudo_enabled and user_login_name in owner_user_names:
                        _configure_sudoers(user_name, executor, logger)
                except KeyboardInterrupt:
                    logger.warning('Interrupted.')
                    raise
                except Exception:
                    logger.warning('User {} ({}) creation has failed.'.format(user_name, user_login_name), trace=True)
            logger.info('Finishing users synchronization...')
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            break
        except Exception:
            logger.warning('Users synchronization has failed.', trace=True)
        except BaseException:
            logger.error('Users synchronization has failed completely.', trace=True)
            raise


def _get_run_shared_users_and_groups_names(api, run_id):
    run_sids = api.load_run_efficiently(run_id).get('runSids', [])

    def _split_by_principality(acc, sid):
        return (acc[0] + [sid['name']], acc[1]) if sid['isPrincipal'] else (acc[0], acc[1] + [sid['name']])

    return reduce(_split_by_principality, run_sids, ([], []))


def _get_group_user_names(api, run_groups):
    groups = (api.load_roles() or []) if run_groups else []
    for group in groups:
        group_id = group.get('id')
        group_name = group.get('name')
        if not group_id or group_name not in run_groups:
            continue
        group_users = (api.load_role(group_id) or {}).get('users', [])
        for group_user in group_users:
            yield group_user['userName']


def _get_local_user_names():
    with open('/etc/passwd', 'r') as f:
        lines = f.readlines()
    for line in lines:
        stripped_line = line.strip()
        if stripped_line:
            yield stripped_line.split(':')[0]


def _resolve_user_name(user_name, user_name_case):
    if user_name_case == 'lower':
        return user_name.lower()
    elif user_name_case == 'upper':
        return user_name.upper()
    else:
        return user_name


def _create_account(user, user_id, uid_seed, root_home_dir, executor, logger):
    logger.debug('Creating {} user account...'.format(user))
    user_uid, user_gid = _resolve_uid_gid(user_id, uid_seed)
    user_home_dir = os.path.join(root_home_dir, user)
    mkdir(os.path.dirname(user_home_dir))
    create_user(user, user, uid=user_uid, gid=user_gid, home_dir=user_home_dir,
                skip_existing=True, executor=executor)
    logger.info('User {} account has been created.'.format(user))
    return user_uid, user_gid, user_home_dir


def _resolve_uid_gid(user_id, uid_seed):
    user_uid = uid_seed + user_id
    user_gid = user_uid
    return user_uid, user_gid


def _set_permissions(target_path, user_uid, user_gid):
    os.chown(target_path, user_uid, user_gid)
    if os.path.isdir(target_path):
        os.chmod(target_path, stat.S_IRWXU)
    else:
        os.chmod(target_path, stat.S_IRUSR | stat.S_IWUSR)


def _configure_ssh_keys(user_name, user_uid, user_gid, user_home_dir, executor, logger):
    logger.debug('Configuring {} user SSH keys...'.format(user_name))
    user_ssh_dir = os.path.join(user_home_dir, '.ssh')
    user_ssh_private_key_path = os.path.join(user_ssh_dir, 'id_rsa')
    user_ssh_public_key_path = os.path.join(user_ssh_dir, 'id_rsa.pub')
    user_ssh_authorized_keys_path = os.path.join(user_ssh_dir, 'authorized_keys')
    if os.path.exists(user_ssh_private_key_path):
        logger.debug('User {} SSH keys are already configured.'.format(user_name))
        return
    mkdir(user_ssh_dir)
    executor.execute('ssh-keygen -t rsa -f {ssh_private_key_path} -N "" -q'
                     .format(ssh_private_key_path=user_ssh_private_key_path))
    if not os.path.exists(user_ssh_authorized_keys_path):
        shutil.copy(user_ssh_public_key_path, user_ssh_authorized_keys_path)
    _set_permissions(user_ssh_dir, user_uid, user_gid)
    for ssh_file_path in _SSH_FILE_PATHS:
        ssh_file_target_path = os.path.join(user_ssh_dir, ssh_file_path)
        if os.path.exists(ssh_file_target_path):
            _set_permissions(ssh_file_target_path, user_uid, user_gid)
    logger.info('User {} SSH keys have been configured.'.format(user_name))


def _configure_sudoers(user_name, executor, logger):
    logger.debug('Configuring {} user sudo access...'.format(user_name))
    executor.execute('echo "{user_name} ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers'.format(user_name=user_name))
    logger.info('User {} sudo access has been configured.'.format(user_name))


if __name__ == '__main__':
    sync_users()
