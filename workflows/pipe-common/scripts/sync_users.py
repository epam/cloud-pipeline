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
from collections import namedtuple
from functools import reduce

import itertools
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


UserEntity = namedtuple('UserEntity', 'id, roles, login_name, local_name, first_name, last_name, email')


class UserModule:
    __metaclass__ = ABCMeta

    @abstractmethod
    def register(self, api, executor, logger):
        pass


class UserSource(UserModule):
    __metaclass__ = ABCMeta

    @abstractmethod
    def get(self):
        """Returns users"""
        pass


class UserHandler(UserModule):
    __metaclass__ = ABCMeta

    @abstractmethod
    def create(self, users):
        """Creates users"""
        pass


class LinuxUserHandler(UserHandler):

    def __init__(self, root_home_dir, sudo_enabled, uid_seed, case):
        """
        User handler that creates a local Linux account for each user.
        """
        self._executor = None
        self._logger = None
        self._root_home_dir = root_home_dir
        self._sudo_enabled = sudo_enabled
        self._uid_seed = uid_seed
        self._case = case

    def register(self, api, executor, logger):
        self._executor = executor
        self._logger = logger

    def create(self, users):
        self._logger.debug('Loading local users...')
        local_user_names = list(self._get_local_user_names())
        if not local_user_names:
            self._logger.debug('Loaded 0 local users.')
        else:
            self._logger.debug('Loaded {} local users: {}.'
                               .format(len(local_user_names), ', '.join(local_user_names)))

        for user in users:
            if not user.id:
                self._logger.debug('Skipping user creation '
                                   'because user id is missing...')
                continue

            if not user.login_name:
                self._logger.debug('Skipping user creation '
                                   'because user login name is missing...')
                continue

            linux_user_name = user.local_name or self._with_case(self._resolve_user_name(user.login_name))

            if not linux_user_name:
                self._logger.debug('Skipping {} ({}) user creation '
                                   'because user name is missing...'.format(linux_user_name, user.login_name))
                continue

            if linux_user_name.upper() in local_user_names:
                continue

            try:
                self._logger.debug('Creating {} ({}) user...'.format(linux_user_name, user.login_name))
                self._create_user(linux_user_name, user.id, user.roles)
                self._logger.info('User {} ({}) has been created.'.format(linux_user_name, user.login_name))
            except Exception:
                self._logger.warning('User {} ({}) creation has failed.'.format(linux_user_name, user.login_name),
                                     trace=True)

    def _get_local_user_names(self):
        with open('/etc/passwd', 'r') as f:
            lines = f.readlines()
        for line in lines:
            stripped_line = line.strip()
            if stripped_line:
                yield stripped_line.split(':')[0].upper()

    def _resolve_user_name(self, value):
        return value.split('@', 1)[0]

    def _with_case(self, value):
        if not value:
            return value
        elif self._case == 'lower':
            return value.lower()
        elif self._case == 'upper':
            return value.upper()
        else:
            return value

    def _create_user(self, linux_user_name, user_id, user_roles):
        user_uid, user_gid, user_home_dir = self._create_account(linux_user_name, user_id)
        self._configure_ssh_keys(linux_user_name, user_uid, user_gid, user_home_dir)
        if self._sudo_enabled and 'ROLE_OWNER' in user_roles:
            self._configure_sudoers(linux_user_name)

    def _create_account(self, user_name, user_id):
        self._logger.debug('Creating {} user account...'.format(user_name))
        user_uid, user_gid = self._resolve_uid_gid(user_id)
        user_home_dir = os.path.join(self._root_home_dir, user_name)
        mkdir(os.path.dirname(user_home_dir))
        create_user(user_name, user_name, uid=user_uid, gid=user_gid, home_dir=user_home_dir,
                    skip_existing=True, executor=self._executor)
        self._logger.debug('User {} account has been created.'.format(user_name))
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


class RunSidsLocalUserSource(UserSource):

    def __init__(self, run_id):
        """
        User source that returns users which the original run was shared with.

        The implementation returns only usernames. Returned users may not have corresponding
        Cloud Pipeline platform users.
        """
        self._api = None
        self._logger = None
        self._run_id = run_id

    def register(self, api, executor, logger):
        self._api = api
        self._logger = logger

    def get(self):
        self._logger.debug('Loading shared users ignoring groups...')
        user_names = self._load_shared_user_names()
        if not user_names:
            self._logger.debug('Loaded 0 shared users.')
            return
        self._logger.debug('Loaded {} shared users: {}.'.format(len(user_names), ', '.join(user_names)))

        if not user_names:
            return

        self._logger.debug('Generating users...')
        for user_name in user_names:
            yield UserEntity(id=0, roles=[], login_name=user_name, local_name='',
                             first_name='', last_name='', email='')

    def _load_shared_user_names(self):
        user_names, _ = self._get_run_shared_users_and_groups_names()
        return list(sorted(set(user_name.upper() for user_name in user_names)))

    def _get_run_shared_users_and_groups_names(self):
        run_sids = self._api.load_run_efficiently(self._run_id).get('runSids', [])

        def _split_by_principality(acc, sid):
            return (acc[0] + [sid['name']], acc[1]) if sid['isPrincipal'] else (acc[0], acc[1] + [sid['name']])

        return reduce(_split_by_principality, run_sids, ([], []))


class RunSidsPlatformUserSource(UserSource):

    def __init__(self, run_id, owner_user_name, sudo_user_names, sudo_group_names, user_name_metadata_key):
        """
        User source that returns users which the original run was shared with.

        Returned users always have corresponding Cloud Pipeline platform users.

        Run's owner is required to have either:
            * ROLE_USER_READER and ROLE_USER_METADATA_READER
            * or ROLE_ADMIN
        """
        self._api = None
        self._logger = None
        self._run_id = run_id
        self._owner_user_name = owner_user_name
        self._sudo_user_names = sudo_user_names
        self._sudo_group_names = sudo_group_names
        self._user_name_metadata_key = user_name_metadata_key

    def register(self, api, executor, logger):
        self._api = api
        self._logger = logger

    def get(self):
        self._logger.debug('Loading shared users...')
        user_names = self._load_shared_user_names()
        if not user_names:
            self._logger.debug('Loaded 0 shared users.')
            return
        self._logger.debug('Loaded {} shared users: {}.'.format(len(user_names), ', '.join(user_names)))

        self._logger.debug('Loading owner users...')
        owner_user_names = self._load_owner_user_names()
        self._logger.debug('Loaded {} owner users.'.format(len(owner_user_names)))

        self._logger.debug('Loading platform users...')
        platform_users_dict = self._load_platform_users()
        self._logger.debug('Loaded {} platform users.'.format(len(platform_users_dict)))
        if len(platform_users_dict) < 2:
            self._logger.warning('Very few platform users have been received. '
                                 'Please ensure that the run owner has ROLE_USER_READER.')

        self._logger.debug('Filtering out users...')
        users_dict = self._filter_out_non_platform_users(user_names, platform_users_dict)
        self._logger.debug('Filtered out {} users.'.format(len(user_names) - len(users_dict)))

        self._logger.debug('Loading user metadata entries...')
        user_metadata_entries_dict = self._load_user_metadata_entries(users_dict.keys())
        self._logger.debug('Loaded {} user metadata entries.'.format(len(user_metadata_entries_dict)))
        if len(user_metadata_entries_dict) < len(users_dict.keys()):
            self._logger.warning('Only a part of user metadata entries have been received. '
                                 'Please ensure that the run owner has ROLE_USER_METADATA_READER.')

        self._logger.debug('Generating users...')
        for user in users_dict.values():
            yield self._build(user, owner_user_names, user_metadata_entries_dict)

    def _load_shared_user_names(self):
        user_names, group_names = self._get_run_shared_users_and_groups_names()
        user_names = set(user_names)
        user_names.update(self._get_group_user_names(group_names))
        if self._owner_user_name in user_names:
            user_names.remove(self._owner_user_name)
        return list(sorted(set(user_name.upper() for user_name in user_names)))

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

    def _load_owner_user_names(self):
        owner_user_names = set()
        owner_user_names.update(self._sudo_user_names)
        owner_user_names.update(self._get_group_user_names(self._sudo_group_names))
        return set(user_name.upper() for user_name in owner_user_names)

    def _load_platform_users(self):
        all_users = self._api.load_users()
        return {user['userName'].upper(): user
                for user in all_users
                if user.get('id') and user.get('userName')}

    def _filter_out_non_platform_users(self, user_names, platform_users_dict):
        users_dict = {}
        for user_name in user_names:
            user = platform_users_dict.get(user_name)
            if not user:
                self._logger.warning('Filtering out user {} because there is no corresponding platform user...'
                                     .format(user_name))
                continue
            users_dict[user['id']] = user
        return users_dict

    def _load_user_metadata_entries(self, user_ids):
        try:
            metadata_entries = self._api.load_all_metadata_efficiently(user_ids, 'PIPELINE_USER')
            return {metadata_entry.get('entity', {}).get('entityId', 0): metadata_entry.get('data', {})
                    for metadata_entry in metadata_entries}
        except APIError:
            self._logger.warning('User metadata entries loading has failed.', trace=True)
            return {}

    def _build(self, user, owner_user_names, user_metadata_entries_dict):
        user_metadata = user_metadata_entries_dict.get(user['id']) or {}
        user_login_name = user['userName']
        user_local_name = user_metadata.get(self._user_name_metadata_key, {}).get('value', '')
        user_roles = []
        if user_login_name.upper() in owner_user_names:
            user_roles.append('ROLE_OWNER')
        user_attributes = user.get('attributes', {})
        return UserEntity(id=user['id'], roles=user_roles,
                          login_name=user_login_name, local_name=user_local_name,
                          first_name=user_attributes.get('FirstName', ''),
                          last_name=user_attributes.get('LastName', ''),
                          email=user_attributes.get('Email', ''))


class UserSyncDaemon:

    def __init__(self, api, executor, logger, sync_timeout):
        self._api = api
        self._executor = executor
        self._logger = logger
        self._sync_timeout = sync_timeout
        self._sources = []
        self._handlers = []

    def register(self, source=None, handler=None):
        if source:
            self._sources.append(source)
            source.register(api=self._api, executor=self._executor, logger=self._logger)
        if handler:
            self._handlers.append(handler)
            handler.register(api=self._api, executor=self._executor, logger=self._logger)

    def sync(self):
        self._logger.debug('Initiating users synchronisation...')
        while True:
            try:
                time.sleep(self._sync_timeout)
                self._logger.debug('Starting users synchronization...')
                users = list(sorted(itertools.chain.from_iterable(source.get() for source in self._sources),
                                    key=lambda user: user.login_name))
                if not users:
                    self._logger.debug('Skipping users synchronization...')
                    continue
                self._logger.debug('Processing {} users: {}.'
                                   .format(len(users), ', '.join(user.login_name for user in users)))
                for handler in self._handlers:
                    handler.create(users)
                self._logger.debug('Finishing users synchronization...')
            except KeyboardInterrupt:
                self._logger.warning('Interrupted.')
                break
            except Exception:
                self._logger.warning('Users synchronization has failed.', trace=True)
            except BaseException:
                self._logger.error('Users synchronization has failed completely.', trace=True)
                raise


def sync_users():
    daemon = get_daemon()
    daemon.register(source=get_run_sids_platform_user_source(),
                    handler=get_linux_user_handler())
    daemon.sync()


def get_daemon():
    api_url = os.environ['API']
    run_id = os.environ['RUN_ID']
    sync_timeout = int(os.getenv('CP_CAP_SYNC_USERS_TIMEOUT_SECONDS', '60'))
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

    api = PipelineAPI(api_url=api_url, log_dir=logging_dir)
    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task=logging_task, inner=logger)
    logger = LevelLogger(level=logging_level_run, inner=logger)
    logger = LocalLogger(logger=logging_logger, inner=logger)

    executor = LocalExecutor()
    executor = LoggingExecutor(logger=logger, inner=executor)

    return UserSyncDaemon(api=api, executor=executor, logger=logger,
                          sync_timeout=sync_timeout)


def get_run_sids_platform_user_source():
    run_id = os.environ['RUN_ID']
    user_name_metadata_key = os.getenv('CP_CAP_USER_NAME_METADATA_KEY', 'local_user_name')
    owner_user_name = os.getenv('OWNER', 'root')
    sudo_user_names = os.getenv('CP_CAP_SUDO_USERS', 'PIPE_ADMIN').split(',')
    sudo_group_names = os.getenv('CP_CAP_SUDO_GROUPS', 'ROLE_ADMIN').split(',')

    return RunSidsPlatformUserSource(run_id=run_id, owner_user_name=owner_user_name,
                                     sudo_user_names=sudo_user_names, sudo_group_names=sudo_group_names,
                                     user_name_metadata_key=user_name_metadata_key)


def get_linux_user_handler():
    shared_dir = os.getenv('SHARED_ROOT', '/common')
    uid_seed_default = int(os.getenv('CP_CAP_UID_SEED', '70000'))
    root_home_dir = os.getenv('CP_CAP_SYNC_USERS_HOME_DIR', os.path.join(shared_dir, 'home'))
    sudo_enabled = os.getenv('CP_CAP_SUDO_ENABLE', 'true').lower().strip() in ['true', 'yes']
    user_name_case = os.getenv('CP_CAP_USER_NAME_CASE', 'upper').strip().lower()

    api = _get_api()
    uid_seed = _get_uid_seed(api, uid_seed_default)

    return LinuxUserHandler(root_home_dir=root_home_dir, sudo_enabled=sudo_enabled, uid_seed=uid_seed,
                            case=user_name_case)


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
