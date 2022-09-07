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
import time
from functools import reduce

from pipeline.api import PipelineAPI, APIError
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger
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
    sync_timeout = int(os.getenv('CP_CAP_SYNC_USERS_TIMEOUT_SECONDS', '60'))
    logging_directory = os.getenv('CP_CAP_SYNC_USERS_LOG_DIR', os.getenv('LOG_DIR', '/var/log'))
    logging_level = os.getenv('CP_CAP_SYNC_USERS_LOGGING_LEVEL', 'INFO')
    logging_level_local = os.getenv('CP_CAP_SYNC_USERS_LOGGING_LEVEL_LOCAL', 'DEBUG')
    logging_format = os.getenv('CP_CAP_SYNC_USERS_LOGGING_FORMAT', '%(asctime)s:%(levelname)s: %(message)s')
    logging_task = os.getenv('CP_CAP_SYNC_USERS_LOGGING_TASK', 'UsersSynchronization')

    logging.basicConfig(level=logging_level_local, format=logging_format,
                        filename=os.path.join(logging_directory, 'sync_users.log'))

    api = PipelineAPI(api_url=api_url, log_dir=logging_directory)
    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task=logging_task, inner=logger)
    logger = LevelLogger(level=logging_level, inner=logger)
    logger = LocalLogger(inner=logger)

    executor = LocalExecutor()
    executor = LoggingExecutor(logger=logger, inner=executor)

    while True:
        try:
            time.sleep(sync_timeout)
            logger.debug('Starting users synchronization...')

            logger.debug('Loading preferences...')
            try:
                uid_seed = int(api.get_preference_efficiently('launch.uid.seed') or uid_seed_default)
            except APIError:
                uid_seed = uid_seed_default
            logger.debug('Loaded uid seed: {}.', uid_seed)

            logger.debug('Loading shared users and groups...')
            run_users, run_groups = _get_run_shared_users_and_groups(api, run_id)
            shared_users = set()
            shared_users.update(run_users)
            shared_users.update(_get_group_users(api, run_groups))
            logger.debug('Loaded {} shared users: {}'.format(len(shared_users), shared_users))

            logger.debug('Loading local users...')
            local_users = set(_get_local_users())
            logger.debug('Loaded {} local users: {}'.format(len(local_users), local_users))

            logger.debug('Loading user details...')
            user_details = {user['userName']: user for user in api.load_users()}
            logger.debug('Loaded {} user details.'.format(len(user_details.keys())))

            users_to_create = shared_users - local_users
            logger.debug('Creating {} users {}...'.format(len(users_to_create), users_to_create))

            for user in users_to_create:
                try:
                    user_id = user_details.get(user, {}).get('id', 0)
                    if not user_id:
                        logger.warning('Skipping {} user creation since the corresponding details have not been found.')
                        continue
                    user_uid, user_gid, user_home_dir = _create_account(user, user_id, uid_seed, root_home_dir,
                                                                        executor, logger)
                    _configure_ssh_keys(user, user_uid, user_gid, user_home_dir, executor, logger)
                    logger.info('User {} has been created.')
                except KeyboardInterrupt:
                    logger.warning('Interrupted.')
                    raise
                except Exception:
                    logger.warning('User {} creation has failed.'.format(user), trace=True)
            logger.debug('Finishing users synchronization...')
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            break
        except Exception:
            logger.warning('Users synchronization has failed.', trace=True)
        except BaseException:
            logger.error('Users synchronization has failed completely.', trace=True)
            raise


def _get_run_shared_users_and_groups(api, run_id):
    run_sids = api.load_run_efficiently(run_id).get('runSids', [])
    return reduce(
        lambda (us, gs), sid: (us + [sid['name']], gs) if sid['isPrincipal'] else (us, gs + [sid['name']]),
        run_sids,
        ([], []))


def _get_group_users(api, run_groups):
    groups = (api.load_roles() or []) if run_groups else []
    for group in groups:
        group_id = group.get('id')
        group_name = group.get('name')
        if not group_id or group_name not in run_groups:
            continue
        group_users = (api.load_role(group_id) or {}).get('users', [])
        for group_user in group_users:
            yield group_user['userName']


def _get_local_users():
    with open('/etc/passwd', 'r') as f:
        lines = f.readlines()
    for line in lines:
        stripped_line = line.strip()
        if stripped_line:
            yield stripped_line.split(':')[0]


def _create_account(user, user_id, uid_seed, root_home_dir, executor, logger):
    logger.debug('Creating {} user account...'.format(user))
    user_uid, user_gid = _resolve_uid_gid(user_id, uid_seed)
    user_home_dir = os.path.join(root_home_dir, user)
    mkdir(os.path.dirname(user_home_dir))
    create_user(user, user, uid=user_uid, gid=user_gid, home_dir=user_home_dir,
                skip_existing=True, executor=executor)
    logger.debug('User {} account has been created.'.format(user))
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


def _configure_ssh_keys(user, user_uid, user_gid, user_home_dir, executor, logger):
    logger.debug('Configuring {} user SSH keys...'.format(user))
    user_ssh_dir = os.path.join(user_home_dir, '.ssh')
    user_ssh_private_key_path = os.path.join(user_ssh_dir, 'id_rsa')
    user_ssh_public_key_path = os.path.join(user_ssh_dir, 'id_rsa.pub')
    user_ssh_authorized_keys_path = os.path.join(user_ssh_dir, 'authorized_keys')
    if os.path.exists(user_ssh_private_key_path):
        logger.debug('User {} SSH keys are already configured.'.format(user))
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
    logger.debug('User {} SSH keys have been configured.'.format(user))


if __name__ == '__main__':
    sync_users()
