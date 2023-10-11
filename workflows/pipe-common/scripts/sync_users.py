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
from abc import ABCMeta, abstractmethod
from functools import reduce

import sys
import time

from pipeline.api import PipelineAPI, APIError
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger
from pipeline.utils.account import create_user
from pipeline.utils.path import mkdir
from pipeline.utils.ssh import LocalExecutor, LoggingExecutor

_ROOT_HOME_DIR = '/root'
_ROOT_SSH_DIR = os.path.join(_ROOT_HOME_DIR, '.ssh')
_SSH_FILE_PATHS = ['id_rsa', 'id_rsa.pub', 'authorized_keys']


class UserHandler:
    __metaclass__ = ABCMeta

    @abstractmethod
    def register(self, executor, logger):
        pass

    @abstractmethod
    def create(self, users):
        pass


class LinuxUserHandler(UserHandler):

    def __init__(self, root_home_dir, sudo_enabled, uid_seed):
        self._executor = None
        self._logger = None
        self._root_home_dir = root_home_dir
        self._sudo_enabled = sudo_enabled
        self._uid_seed = uid_seed

    def register(self, executor, logger):
        self._executor = executor
        self._logger = logger

    def create(self, users):
        self._logger.debug('Loading local users...')
        local_user_names = set(self._get_local_user_names())
        self._logger.debug('Loaded {} local users.'.format(len(local_user_names)))
        self._logger.debug('Loaded local users: {}'.format(local_user_names))

        for user in users:
            user_id = user['id']
            user_roles = user['_roles']
            user_login_name = user['userName']
            user_local_name = user['_userLocalName']

            linux_name = user_local_name

            if not linux_name:
                self._logger.debug('Skipping {} ({}) user creation '
                                   'because user name is missing...'.format(linux_name, user_login_name))
                continue

            if linux_name in local_user_names:
                continue

            try:
                self._logger.debug('Creating {} ({}) user...'.format(linux_name, user_login_name))
                user_uid, user_gid, user_home_dir = self._create_account(linux_name, user_id)
                self._configure_ssh_keys(linux_name, user_uid, user_gid, user_home_dir)
                if self._sudo_enabled and 'ROLE_OWNER' in user_roles:
                    self._configure_sudoers(linux_name)
                self._logger.info('User {} ({}) has been created.'.format(linux_name, user_login_name))
            except Exception:
                self._logger.warning('User {} ({}) creation has failed.'.format(linux_name, user_login_name),
                                     trace=True)

    def _get_local_user_names(self):
        with open('/etc/passwd', 'r') as f:
            lines = f.readlines()
        for line in lines:
            stripped_line = line.strip()
            if stripped_line:
                yield stripped_line.split(':')[0]

    def _create_account(self, user, user_id):
        self._logger.debug('Creating {} user account...'.format(user))
        user_uid, user_gid = self._resolve_uid_gid(user_id)
        user_home_dir = os.path.join(self._root_home_dir, user)
        mkdir(os.path.dirname(user_home_dir))
        create_user(user, user, uid=user_uid, gid=user_gid, home_dir=user_home_dir,
                    skip_existing=True, executor=self._executor)
        self._logger.debug('User {} account has been created.'.format(user))
        return user_uid, user_gid, user_home_dir

    def _resolve_uid_gid(self, user_id):
        user_uid = self._uid_seed + user_id
        user_gid = user_uid
        return user_uid, user_gid

    def _configure_ssh_keys(self, user_name, user_uid, user_gid, user_home_dir):
        self._logger.debug('Configuring {} user SSH keys...'.format(user_name))
        user_ssh_dir = os.path.join(user_home_dir, '.ssh')
        user_ssh_private_key_path = os.path.join(user_ssh_dir, 'id_rsa')
        user_ssh_public_key_path = os.path.join(user_ssh_dir, 'id_rsa.pub')
        user_ssh_authorized_keys_path = os.path.join(user_ssh_dir, 'authorized_keys')
        if os.path.exists(user_ssh_private_key_path):
            self._logger.debug('User {} SSH keys are already configured.'.format(user_name))
            return
        mkdir(user_ssh_dir)
        self._executor.execute('ssh-keygen -t rsa -f {ssh_private_key_path} -N "" -q'
                               .format(ssh_private_key_path=user_ssh_private_key_path))
        if not os.path.exists(user_ssh_authorized_keys_path):
            shutil.copy(user_ssh_public_key_path, user_ssh_authorized_keys_path)
        self._set_permissions(user_ssh_dir, user_uid, user_gid)
        for ssh_file_path in _SSH_FILE_PATHS:
            ssh_file_target_path = os.path.join(user_ssh_dir, ssh_file_path)
            if os.path.exists(ssh_file_target_path):
                self._set_permissions(ssh_file_target_path, user_uid, user_gid)
        self._logger.debug('User {} SSH keys have been configured.'.format(user_name))

    def _set_permissions(self, target_path, user_uid, user_gid):
        os.chown(target_path, user_uid, user_gid)
        if os.path.isdir(target_path):
            os.chmod(target_path, stat.S_IRWXU)
        else:
            os.chmod(target_path, stat.S_IRUSR | stat.S_IWUSR)

    def _configure_sudoers(self, user_name):
        self._logger.debug('Configuring {} user sudo access...'.format(user_name))
        self._executor.execute('echo "{user_name} ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers'.format(user_name=user_name))
        self._logger.debug('User {} sudo access has been configured.'.format(user_name))


class UserSyncDaemon:

    def __init__(self, api, executor, logger, run_id, owner_user_name, sudo_user_names, sudo_group_names,
                 user_name_case, user_name_metadata_key, sync_timeout):
        self._api = api
        self._executor = executor
        self._logger = logger
        self._run_id = run_id
        self._owner_user_name = owner_user_name
        self._sudo_user_names = sudo_user_names
        self._sudo_group_names = sudo_group_names
        self._user_name_case = user_name_case
        self._user_name_metadata_key = user_name_metadata_key
        self._sync_timeout = sync_timeout
        self._handlers = []

    def register(self, handler):
        self._handlers.append(handler)
        handler.register(executor=self._executor, logger=self._logger)

    def sync(self):
        self._logger.debug('Initiating users synchronisation...')
        while True:
            try:
                time.sleep(self._sync_timeout)
                self._logger.debug('Starting users synchronization...')

                self._logger.debug('Loading shared users and groups...')
                run_user_names, run_group_names = self._get_run_shared_users_and_groups_names()
                shared_user_names = set()
                shared_user_names.update(run_user_names)
                shared_user_names.update(self._get_group_user_names(run_group_names))
                if self._owner_user_name in shared_user_names:
                    shared_user_names.remove(self._owner_user_name)
                self._logger.debug('Loaded {} shared users.'.format(len(shared_user_names)))
                self._logger.debug('Loaded shared users: {}'.format(shared_user_names))

                self._logger.debug('Loading owner users and groups...')
                owner_user_names = set()
                owner_user_names.update(self._sudo_user_names)
                owner_user_names.update(self._get_group_user_names(self._sudo_group_names))
                self._logger.debug('Loaded {} owner users.'.format(len(owner_user_names)))

                self._logger.debug('Loading user details...')
                all_users = self._api.load_users()
                self._logger.debug('Loaded {} user details.'.format(len(all_users)))

                self._logger.debug('Resolving shared user details...')
                shared_users_dict = {}
                for user in all_users:
                    user_id = user.get('id')
                    user_name = user.get('userName')
                    if not user_id or not user_name:
                        continue
                    if user_name not in shared_user_names:
                        continue
                    user['_metadata'] = {}
                    user['_roles'] = []
                    user['_userLocalName'] = ''
                    shared_users_dict[user_id] = user
                self._logger.debug('Resolved {} shared user details.'.format(len(shared_users_dict)))

                self._logger.debug('Loading shared users metadata...')
                try:
                    shared_users_metadata = self._api.load_all_metadata_efficiently(shared_users_dict.keys(), 'PIPELINE_USER')
                except APIError:
                    self._logger.warning('Shared users metadata loading has failed.', trace=True)
                    shared_users_metadata = []
                self._logger.debug('Loaded {} shared users metadata.'.format(len(shared_users_metadata)))

                self._logger.debug('Merging shared users details and metadata...')
                for metadata_entry in shared_users_metadata:
                    user_id = metadata_entry.get('entity', {}).get('entityId', 0)
                    user_metadata = metadata_entry.get('data', {})
                    user = shared_users_dict.get(user_id, {})
                    user['_metadata'] = user_metadata
                for user in shared_users_dict.values():
                    user_metadata = user['_metadata']
                    user_login_name = user['userName']
                    user_local_name = user_metadata.get(self._user_name_metadata_key, {}).get('value') \
                        or self._resolve_user_name(user_login_name)
                    user['_userLocalName'] = user_local_name
                    if user_login_name in owner_user_names:
                        user['_roles'].append('ROLE_OWNER')
                self._logger.debug('Merged {} shared users details and metadata.'.format(len(shared_users_metadata)))

                self._logger.debug('Checking {} shared users existence...'.format(len(shared_users_dict)))
                for handler in self._handlers:
                    handler.create(shared_users_dict.values())
                self._logger.debug('Finishing users synchronization...')
            except KeyboardInterrupt:
                self._logger.warning('Interrupted.')
                break
            except Exception:
                self._logger.warning('Users synchronization has failed.', trace=True)
            except BaseException:
                self._logger.error('Users synchronization has failed completely.', trace=True)
                raise

    def _get_run_shared_users_and_groups_names(self):
        run_sids = self._api.load_run_efficiently(self._run_id).get('runSids', [])

        def _split_by_principality(acc, sid):
            return (acc[0] + [sid['name']], acc[1]) if sid['isPrincipal'] else (acc[0], acc[1] + [sid['name']])

        return reduce(_split_by_principality, run_sids, ([], []))

    def _get_group_user_names(self, run_groups):
        groups = (self._api.load_roles() or []) if run_groups else []
        for group in groups:
            group_id = group.get('id')
            group_name = group.get('name')
            if not group_id or group_name not in run_groups:
                continue
            group_users = (self._api.load_role(group_id) or {}).get('users', [])
            for group_user in group_users:
                yield group_user['userName']

    def _resolve_user_name(self, user_name):
        user_name = user_name.split('@')[0]
        if self._user_name_case == 'lower':
            return user_name.lower()
        elif self._user_name_case == 'upper':
            return user_name.upper()
        else:
            return user_name


def sync_users():
    daemon = get_daemon()
    daemon.register(_get_linux_user_handler())
    daemon.sync()


def get_daemon():
    api_url = os.environ['API']
    run_id = os.environ['RUN_ID']
    user_name_case = os.getenv('CP_CAP_USER_NAME_CASE', 'default').strip().lower()
    user_name_metadata_key = os.getenv('CP_CAP_USER_NAME_METADATA_KEY', 'local_user_name')
    sync_timeout = int(os.getenv('CP_CAP_SYNC_USERS_TIMEOUT_SECONDS', '60'))
    logging_dir = os.getenv('CP_CAP_SYNC_USERS_LOG_DIR', os.getenv('LOG_DIR', '/var/log'))
    logging_level_run = os.getenv('CP_CAP_SYNC_USERS_LOGGING_LEVEL_RUN', 'INFO')
    logging_level_file = os.getenv('CP_CAP_SYNC_USERS_LOGGING_LEVEL_LOCAL', 'DEBUG')
    logging_level_console = os.getenv('CP_CAP_SYNC_USERS_LOGGING_LEVEL_CONSOLE', 'INFO')
    logging_format = os.getenv('CP_CAP_SYNC_USERS_LOGGING_FORMAT', '%(asctime)s:%(levelname)s: %(message)s')
    logging_task = os.getenv('CP_CAP_SYNC_USERS_LOGGING_TASK', 'UsersSynchronization')
    logging_file = os.path.join(logging_dir, 'sync_users.log')
    owner_user_name = os.getenv('OWNER', 'root')
    sudo_user_names = os.getenv('CP_CAP_SUDO_USERS', 'PIPE_ADMIN').split(',')
    sudo_group_names = os.getenv('CP_CAP_SUDO_GROUPS', 'ROLE_ADMIN').split(',')

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

    api = PipelineAPI(api_url=api_url, log_dir=logging_dir)
    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task=logging_task, inner=logger)
    logger = LevelLogger(level=logging_level_run, inner=logger)
    logger = LocalLogger(logger=logging_logger, inner=logger)

    executor = LocalExecutor()
    executor = LoggingExecutor(logger=logger, inner=executor)

    return UserSyncDaemon(api=api, executor=executor, logger=logger,
                          run_id=run_id, owner_user_name=owner_user_name,
                          sudo_user_names=sudo_user_names, sudo_group_names=sudo_group_names,
                          user_name_case=user_name_case, user_name_metadata_key=user_name_metadata_key,
                          sync_timeout=sync_timeout)


def _get_linux_user_handler():
    shared_dir = os.getenv('SHARED_ROOT', '/common')
    uid_seed_default = int(os.getenv('CP_CAP_UID_SEED', '70000'))
    root_home_dir = os.getenv('CP_CAP_SYNC_USERS_HOME_DIR', os.path.join(shared_dir, 'home'))
    sudo_enabled = os.getenv('CP_CAP_SUDO_ENABLE', 'true').lower().strip() in ['true', 'yes']

    api = _get_api()
    uid_seed = _get_uid_seed(api, uid_seed_default)

    return LinuxUserHandler(root_home_dir=root_home_dir, sudo_enabled=sudo_enabled, uid_seed=uid_seed)


def _get_api():
    api_url = os.environ['API']
    logging_directory = os.getenv('CP_CAP_SYNC_USERS_LOG_DIR', os.getenv('LOG_DIR', '/var/log'))
    return PipelineAPI(api_url=api_url, log_dir=logging_directory)


def _get_uid_seed(api, uid_seed_default):
    try:
        return int(api.get_preference_value('launch.uid.seed') or uid_seed_default)
    except APIError:
        return uid_seed_default


if __name__ == '__main__':
    sync_users()
