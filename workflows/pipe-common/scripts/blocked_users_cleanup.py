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
import mutex
import os
import subprocess
from datetime import datetime, timedelta

from pipeline.api import PipelineAPI
import pipeline.common
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger, ResilientLogger


DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"
NFS_TYPE = "NFS"

PIPE_STORAGE_CP = "pipe storage cp"

PAUSED = "PAUSED"
RUNNING = "RUNNING"


EMAIL_TEMPLATE = '''
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">

<head>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
    <style>
        table,
        td {{
            border: 1px solid black;
            border-collapse: collapse;
            padding: 5px;
        }}
    </style>
</head>

<body>
<p>Dear user,</p>
<p>*** This is a system generated email, do not reply to this email ***</p>
<p>Please find the list of the assets associated with the blocked users along with asset status. </p>
<p>
<table>
    <tr>
        <td><b>Object</b></td>
        <td><b>Owner</b></td>
        <td><b>Status</b></td>
        <td><b>Message</b></td>
    </tr>
    {events}
</table>
</p>
<p>Best regards,</p>
<p>{deploy_name} Platform</p>
</body>

</html>
'''

EVENT_PATTERN = '''
 <tr>
            <td>{object}</td>
            <td>{owner}</td>
            <td>{status}</td>
            <td>{message}</td>
 </tr>
'''


EMAIL_SUBJECT = '[%s]: Blocked users assets'
RUN_TYPE = 'RUN'
TOOL_TYPE = 'TOOL'
STORAGE_TYPE = 'STORAGE'
DELETED = 'DELETED'


def get_api_link(url):
    return url.rstrip('/').replace('/restapi', '')


class Event(object):

    def __init__(self, id, name, type, owner, status, message):
        self.id = str(id)
        self.name = name
        self.type = type
        self.owner = owner
        self.status = status
        self.message = message

    def get_object_str(self, api_url):
        if self.type == RUN_TYPE:
            return '<a href="{}/#/run/{}/plain">Pipeline Run {}</a>'.format(api_url, self.id, self.id)
        if self.type == TOOL_TYPE:
            if self.status == DELETED:
                return 'Tool {} ({})'.format(self.name, self.id)
            return '<a href="{}/#/tool/{}/description">Tool {}</a>'.format(api_url, self.id, self.name)
        if self.type == STORAGE_TYPE:
            if self.status == DELETED:
                return 'Storage {} ({})'.format(self.name, self.id)
            return '<a href="{}/#/storage/{}">Storage {}</a>'.format(api_url, self.id, self.name)
        return ''


class Notifier(object):

    def __init__(self, api, deploy_name, notify_users):
        self.notifications = []
        self.api = api
        self.deploy_name = deploy_name
        self.notify_users = notify_users

    def add(self, notification):
        self.notifications.append(notification)

    def send_notifications(self):
        if not self.notifications or not self.notify_users:
            return
        self.api.create_notification(EMAIL_SUBJECT % self.deploy_name,
                                     self.build_text(),
                                     self.notify_users[0],
                                     copy_users=self.notify_users[1:] if len(
                                         self.notify_users) > 0 else None,
                                     )

    def build_text(self):
        event_str = ''
        api_link = get_api_link(self.api.api_url)
        for event in self.notifications:
            object_str = event.get_object_str(api_link)
            event_str += EVENT_PATTERN.format(**{'object': object_str,
                                                 'owner': event.owner,
                                                 'status': event.status,
                                                 'message': event.message})

        return EMAIL_TEMPLATE.format(**{'events': event_str,
                                        'deploy_name': self.deploy_name})


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
    logger = ResilientLogger(inner=logger, fallback=LocalLogger())

    notify_users = os.getenv('CP_CLEANUP_NOTIFY_USERS', '').split(',')
    notifier = Notifier(api, os.getenv('CP_DEPLOY_NAME', 'Cloud Pipeline'), notify_users)

    # while True:
    try:
        # time.sleep(cleanup_timeout)
        logger.info('Starting users cleanup...')
        if dry_run:
            logger.info('Running in dry run mode. No actual changes will be made.')
        else:
            logger.info('Running in active mode. User assets will be deleted.')
        logger.info('Loading blocked users...')
        users = api.load_users()
        blocked_users = [user for user in users if user.get('blocked')]
        logger.info('Loaded {} blocked users: {}'.format(len(blocked_users),
                                                         ', '.join([user.get('userName') for user in blocked_users])))
        if len(blocked_users) > 0:
            _cleanup_paused_instances(api, logger, blocked_users, dry_run, notifier)
            _cleanup_running_instances(api, logger, blocked_users, dry_run, notifier)
            _cleanup_tools(api, logger, blocked_users, dry_run, notifier)
            _cleanup_storages(api, logger, users, blocked_users, dry_run, notifier)
        notifier.send_notifications()
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


def _cleanup_paused_instances(api, logger, users, dry_run, notifier):
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
                notifier.add(Event(run_id, None, RUN_TYPE, run.get('owner'), 'TERMINATED', ''))
            else:
                notifier.add(Event(Event(run_id, None, RUN_TYPE, run.get('owner'),
                                         'TO BE TERMINATED', 'Skipped due to dry run')))
            logger.debug('Paused instance {} terminated.'.format(run_id))
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            raise
        except Exception as e:
            logger.warning('Paused instances cleanup has failed.')
            notifier.add(Event(run.get('id'), None, RUN_TYPE, run.get('owner'), 'ERROR',
                               'Failed to terminate paused run: ' + e.message))
    logger.info('Finishing paused instances cleanup...')


def _cleanup_running_instances(api, logger, users, dry_run, notifier):
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
                notifier.add(Event(run_id, None, RUN_TYPE, run.get('owner'), 'STOPPED', ''))
            else:
                notifier.add(Event(Event(run_id, None, RUN_TYPE, run.get('owner'),
                                         'TO BE STOPPED', 'Skipped due to dry run')))
            logger.debug('Running instance {} stopped.'.format(run_id))
        except KeyboardInterrupt:
            logger.warning('Interrupted.', trace=True)
            raise
        except Exception as e:
            logger.warning('Running instances cleanup has failed.')
            notifier.add(Event(run.get('id'), None, RUN_TYPE, run.get('owner'), 'ERROR',
                               'Failed to stop active run: ' + e.message))
    logger.info('Finishing running instances cleanup...')


def _cleanup_tools(api, logger, users, dry_run, notifier):
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
                logger.debug("Tool {} won't be deleted because it is shared with other users.".format(tool_id))
                notifier.add(Event(tool_id, tool.get('image'), TOOL_TYPE, tool.get('owner'), 'CANNOT DELETE',
                                   "Tool {} won't be deleted because it is shared with other users.".format(tool_id)))
                continue
            logger.debug('Checking links')
            if _is_parent(tool_id, tools):
                logger.debug("Tool {} won't be deleted because it has linked tool(s).".format(tool_id))
                notifier.add(Event(tool_id, tool.get('image'), TOOL_TYPE, tool.get('owner'), 'CANNOT DELETE',
                                   "Tool {} won't be deleted because it has linked tool(s).".format(tool_id)))
                continue
            logger.debug('Removing tool {}'.format(tool_id))
            if not dry_run:
                api.delete_tool(tool.get('image'))
                notifier.add(Event(tool_id, tool.get('image'), TOOL_TYPE, tool.get('owner'), DELETED, ''))
            else:
                notifier.add(Event(Event(tool_id, tool.get('image'), TOOL_TYPE, tool.get('owner'),
                                   'TO BE DELETED', 'Skipped due to dry run')))
            logger.debug('Tool {} deleted.'.format(tool_id))
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            raise
        except Exception as e:
            logger.warning('Tool cleanup has failed.')
            notifier.add(Event(tool.get('id'), tool.get('image'), TOOL_TYPE, tool.get('owner'), 'ERROR',
                               'Failed to process tool: ' + e.message))
    logger.info('Finishing tools cleanup...')


def _cleanup_storages(api, logger, users, blocked_users, dry_run, notifier):
    default_exp_days = os.getenv('CP_DATASTORAGE_DEFAULT_EXP_DAYS')
    general_exp_days = os.getenv('CP_DATASTORAGE_GENERAL_EXP_DAYS')
    delete_content = _extract_boolean_parameter('CP_DELETE_STORAGE_CONTENT', 'true')
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
            logger.debug('Checking permissions for storage {}'.format(storage_id))
            default_storage_users = _get_default_storage_users(users, storage_id)
            owner = [user for user in users if user.get('userName') == storage.get('owner')].pop(0)

            if len(default_storage_users) > 0:
                logger.debug('Storage {} is a default home storage for user(s) {}'
                             .format(storage_id, ','.join([user.get('userName') for user in default_storage_users])))
                if default_exp_days is None:
                    logger.debug(
                        'CP_DATASTORAGE_DEFAULT_EXP_DAYS not defined. Storage {} cleanup skipped...'.format(storage_id))
                    continue
                if not _clean_up(owner, int(default_exp_days)):
                    logger.debug(
                        'Storage cleanup timeout doesn\'t exceed yet. Storage {} cleanup skipped...'.format(storage_id))
                    continue
            else:
                logger.debug('Storage {} is owned by {}'.format(storage_id, storage.get('owner')))
                if general_exp_days is None:
                    logger.debug(
                        'CP_DATASTORAGE_GENERAL_EXP_DAYS not defined. Storage {} cleanup skipped...'.format(storage_id))
                    continue
                if not _clean_up(owner, int(general_exp_days)):
                    logger.debug(
                        'Storage cleanup timeout doesn\'t exceed yet. Storage {} cleanup skipped...'.format(storage_id))
                    continue

            permissions = api.get_permissions(storage_id, 'DATA_STORAGE')
            if permissions is not None:
                logger.debug("Storage {} won't be deleted because it is shared with other users.".format(storage_id))
                notifier.add(Event(storage_id, storage.get('name'), STORAGE_TYPE, storage.get('owner'), 'CANNOT DELETE',
                                   "Storage {} won't be deleted because it is shared with other users.".format(storage_id)))
                continue
            logger.debug('Storage {} is not shared with any users'.format(storage_id))
            logger.debug('Checking child storages for storage {}'.format(storage_id))
            child_storages = _has_shared_copy(storages, storage)
            if child_storages:
                logger.debug("Storage {} won't be deleted because it has child storage(s): {}"
                             .format(storage_id, ', '.join([s.get('path') for s in child_storages])))
                notifier.add(Event(storage_id, storage.get('name'), STORAGE_TYPE, storage.get('owner'), 'CANNOT DELETE',
                                   "Storage {} won't be deleted because it has child storage(s): {}"
                                   .format(storage_id, ', '.join([s.get('path') for s in child_storages]))
                                   ))
                continue
            logger.debug('Storage {} doesn\'t have any child storages'.format(storage_id))
            if len(default_storage_users) > 0:
                _cleanup_default_datastorage(api, logger, storage, default_storage_users, dry_run, delete_content, notifier)
            else:
                _cleanup_general_datastorage(api, logger, storage, dry_run, delete_content, notifier)
        except KeyboardInterrupt:
            logger.warning('Interrupted.')
            raise
        except Exception as e:
            logger.error('Data storage cleanup has failed: {}'.format(e.message))
            notifier.add(Event(storage.get('id'), storage.get('name'), STORAGE_TYPE, storage.get('owner'), 'ERROR',
                               'Failed to process storage: ' + e.message))
    logger.info('Finishing data storages cleanup...')


def _has_shared_copy(storages, storage):
    path = '{}/'.format(storage.get('path'))
    copies = [s for s in storages if s.get('path').startswith(path)
              and s.get('id') != storage.get('id')]
    return copies


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


def _replace_datastorage_content(logger, storage, mount_point, destination):
    if not os.path.exists(mount_point):
        os.makedirs(mount_point)
    logger.debug('Mounting {} to {}'.format(storage, mount_point))
    cmd = '/bin/mount -t nfs -o ro {} {}'.format(storage, mount_point)
    logger.debug('Executing "{}"'.format(cmd))
    exit_code, stdout, stderr = pipeline.common.execute_cmd_command_and_get_stdout_stderr(cmd)
    if exit_code == 0:
        logger.debug('Mounted {} successfully'.format(storage))
    else:
        logger.debug('Failed to mount {} to {}: {}'.format(storage, mount_point, str(stderr)))
        return False

    logger.debug('Transferring data from {} to {}'.format(mount_point, destination))
    cmd = '{} "{}" "{}" --recursive --force --quiet'.format(PIPE_STORAGE_CP, mount_point,
                                                                                destination)
    logger.debug('Executing "{}"'.format(cmd))
    exit_code, stdout, stderr = pipeline.common.execute_cmd_command_and_get_stdout_stderr(cmd)
    if exit_code == 0:
        logger.debug('Successfully copied data from {} to {}'.format(mount_point, destination))
    else:
        logger.debug('Failed to copy data from {} to {}: {}'.format(mount_point, destination, str(stderr)))
        return False

    logger.debug('Unmounting storage from {}'.format(mount_point))
    cmd = '/bin/umount {}'.format(mount_point)
    logger.debug('Executing "{}"'.format(cmd))
    exit_code, stdout, stderr = pipeline.common.execute_cmd_command_and_get_stdout_stderr(cmd)
    if exit_code == 0:
        logger.debug('Successfully unmounted {}'.format(mount_point))
    else:
        logger.debug('Failed to umount {}: {}'.format(mount_point, str(stderr)))
        return False
    return True


def _cleanup_default_datastorage(api, logger, storage, default_storage_users, dry_run, delete_content, notifier):
    storage_id = storage.get('id')
    logger.debug('Storage {} is eligible for deletion. Checking storage content'.format(storage_id))
    storage_items = api.load_datastorage_items(storage_id)
    if storage_items is not None:
        logger.debug('Storage {} has content to backup.'.format(storage_id))
        dedicated_bucket = os.getenv('CP_DEDICATED_BUCKET')
        mount_path = os.getenv('CP_MOUNT_POINT', '/opt/mount/')
        if dedicated_bucket is None:
            logger.debug('CP_DEDICATED_BUCKET not defined. Storage {} cleanup skipped...'.format(storage_id))
            return
        source = os.path.join(mount_path, str(storage.get('id')))
        destination = '{}/{}/'.format(dedicated_bucket.rstrip('/'), str(storage.get('id')))
        logger.debug('Copying storage content from {} to {}'.format(storage.get('path'), destination))
        if not dry_run:
            result = _replace_datastorage_content(logger, storage.get('path'), source, destination)
            if not result:
                logger.warning("Data backup failed, storage {} can't be deleted.".format(storage_id))
                notifier.add(Event(storage.get('id'), storage.get('name'), STORAGE_TYPE, storage.get('owner'), 'ERROR',
                                   "Data backup failed"))
                return
    logger.debug('Cleaning user default storage attribute {} for users {} '.format(storage_id,
                                                                                   ','.join([user.get('userName') for user in default_storage_users])))
    if not dry_run:
        for default_storage_user in default_storage_users:
            api.delete_user_home_storage(default_storage_user.get('id'))
    logger.debug('Deleting storage {}'.format(storage_id))
    if not dry_run:
        api.delete_datastorage(storage_id, delete_content)
        notifier.add(Event(storage.get('id'), storage.get('name'), STORAGE_TYPE, storage.get('owner'), DELETED, ''))
    else:
        notifier.add(Event(storage.get('id'), storage.get('name'), STORAGE_TYPE, storage.get('owner'),
                           'TO BE DELETED', 'Skipped due to dry run'))
    logger.debug('Datastorage {} deleted successfully'.format(storage_id))


def _cleanup_general_datastorage(api, logger, storage, dry_run, delete_content, notifier):
    storage_id = storage.get('id')
    logger.debug('Deleting storage {}'.format(storage_id))
    if not dry_run:
        api.delete_datastorage(storage.get('id'), delete_content)
        notifier.add(Event(storage.get('id'), storage.get('name'), STORAGE_TYPE, storage.get('owner'), DELETED, ''))
    else:
        notifier.add(Event(storage.get('id'), storage.get('name'), STORAGE_TYPE, storage.get('owner'),
                           'TO BE DELETED', 'Skipped due to dry run'))
    logger.debug('Datastorage {} deleted.'.format(storage_id))


if __name__ == '__main__':
    cleanup_users()
