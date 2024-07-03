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

from collections import OrderedDict
import datetime
from distutils.spawn import find_executable
import errno
import logging
import os
import psutil
import re
import subprocess
import shutil
import signal
import socket
import time
from pipeline import PipelineAPI
from watchdog.observers.inotify import InotifyObserver
from watchdog.events import FileSystemEventHandler, FileMovedEvent
from watchdog.observers.api import ObservedWatch
from logging.handlers import RotatingFileHandler

NFS_UNMOUNT_CMD_PATTERN = "umount -l -f \"{}\""
NFS_MOUNT_CMD_PATTERN = 'mount -t {} -o \"{}\" \"{}\" \"{}\"'
REMOUNT_OPTION = 'remount'
READ_WRITE_OPTION = 'rw'
READ_ONLY_OPTION = 'ro'
MODIFIED_MNT_SEPARATOR = '|'
LNET_SPLIT = '@tcp:/'
WRITE_MASK = 1 << 1

MOUNT_STATUS_DISABLED = 'MOUNT_DISABLED'
MOUNT_STATUS_READ_ONLY = 'READ_ONLY'
MOUNT_STATUS_ACTIVE = 'ACTIVE'
MOUNT_STATUS_UNKNOWN = 'UNKNOWN'
DEFAULT_MOUNT_STATUS = os.getenv('CP_CAP_NFS_OBSERVER_DEFAULT_STORAGE_STATUS', 'UNKNOWN')

NEWLINE = '\n'
COMMA = ','
MNT_LISTING_COMMAND = 'mount -t {}'
MNT_PARSING_REGEXP = r"(.*) on (.*) type (.*) \((.*)\)"

DT_FORMAT = '%Y-%m-%d %H:%M:%S:%f'
PIPE_CP_TEMPLATE = 'pipe storage cp \'{}\' \'{}\''
AWS_CP_TEMPLATE = 'aws s3 cp \'{}\' \'{}\' --only-show-errors --sse aws:kms'

CREATE_EVENT = 'c'
MODIFY_EVENT = 'm'
MOVED_FROM_EVENT = 'mf'
MOVED_TO_EVENT = 'mt'
DELETE_EVENT = 'd'

WATCHER_MEM_CONSUMPTION_BYTES = 1024
WATCHERS_USAGE_MEMORY_RATE = int(os.getenv('CP_CAP_NFS_MNT_OBSERVER_WATCHERS_MEM_CONSUMPTION_MAX_RATE', 100))
INOTIFY_MAX_WATCHERS = 'fs.inotify.max_user_instances'
INOTIFY_MAX_WATCHED_DIRS = 'fs.inotify.max_user_watches'
INOTIFY_MAX_QUEUED_EVENTS = 'fs.inotify.max_queued_events'

EVENTS_LIMIT = int(os.getenv('CP_CAP_NFS_OBSERVER_EVENTS_LIMIT', 1000))
MNT_RESYNC_TIMEOUT_SEC = int(os.getenv('CP_CAP_NFS_OBSERVER_MNT_RESYNC_TIMEOUT_SEC', 10))
EVENT_DUMPING_TIMEOUT_SEC = int(os.getenv('CP_CAP_NFS_OBSERVER_DUMPING_TIMEOUT_SEC', 30))
TARGET_FS_TYPES = os.getenv('CP_CAP_NFS_OBSERVER_TARGET_FS_TYPES', 'nfs4,lustre')
EVENT_FILES_LIMIT_MB = int(os.getenv('CP_CAP_NFS_OBSERVER_EVENT_FILES_LIMIT_MB', 155))
EVENT_FILES_BACKUP_COUNT = max(1, int(os.getenv('CP_CAP_NFS_OBSERVER_EVENT_FILES_BACKUP_COUNT', 30)))

IGNORED_MOUNTPOINTS = os.getenv('CP_CAP_NFS_OBSERVER_IGNORE_MOUNTPOINTS', '').split(',')
IGNORED_SOURCES = os.getenv('CP_CAP_NFS_OBSERVER_IGNORE_SOURCES', '').split(',')

logging_format = os.getenv('CP_CAP_NFS_OBSERVER__LOGGING_FORMAT', '%(message)s')
logging_level = os.getenv('CP_CAP_NFS_OBSERVER_LOGGING_LEVEL', 'WARNING')
logging.basicConfig(level=logging_level, format=logging_format)


def format_message(message):
    return '[{}] {}'.format(current_utc_time_str(), message)


def mkdir(path):
    try:
        os.makedirs(path)
    except OSError as e:
        if e.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            raise


def configure_kernel_param(key, value):
    execute_cmd_command_and_get_stdout_stderr('sysctl -w {}={} && sysctl -p'.format(key, value), silent=True)


def execute_cmd_command_and_get_stdout_stderr(command, silent=False, executable=None):
    if executable:
        p = subprocess.Popen(command, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE, executable=executable)
    else:
        p = subprocess.Popen(command, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE)
    stdout, stderr = p.communicate()
    if not silent and stderr:
        print(stderr)
    if not silent and stdout:
        print(stdout)
    return p.wait(), stdout, stderr


def execute_command(command, max_attempts=1):
    attempts = 0
    success = False
    stdout = None
    stderr = None
    while attempts < max_attempts and not success:
        attempts += 1
        exit_code, stdout, stderr = execute_cmd_command_and_get_stdout_stderr(command, silent=True)
        success = exit_code == 0
    if not success:
        logging.error(format_message(
            'Execution of [{}] failed due to the reason: {}'.format(command, stderr.rstrip(NEWLINE))))
    return stdout, success


def current_utc_time():
    return datetime.datetime.utcnow()


def current_utc_time_millis():
    return int((current_utc_time() - datetime.datetime(1970, 1, 1)).total_seconds() * 1000)


def current_utc_time_str():
    return current_utc_time().strftime(DT_FORMAT)


class MountPointDetails:

    def __init__(self, mount_source, mount_point, mount_type, mount_attributes):
        self.mount_source = mount_source
        self.mount_point = mount_point
        self.mount_type = mount_type
        self.mount_attributes = mount_attributes

    @staticmethod
    def from_array(array):
        if len(array) == 4:
            return MountPointDetails(array[0], array[1], array[2], array[3])
        else:
            return None


class Event:

    def __init__(self, path, event_type):
        self.timestamp = current_utc_time_millis()
        self.path = path
        self.event_type = event_type


class CloudBucketDumpingEventHandler(FileSystemEventHandler):

    def __init__(self):
        super(FileSystemEventHandler, self).__init__()
        self._active_events = OrderedDict()
        self._target_path_mapping = dict()
        self._activity_logging_local_dir = self.configure_logging_local_dir()
        self._activity_logging_bucket_dir = self._configure_logging_bucket_dir()
        self._transfer_template = self._get_available_transfer_template()
        self.last_dump_time = current_utc_time()
        self._init_events_logger()

    @staticmethod
    def _get_available_transfer_template():
        if find_executable('pipe'):
            transfer_template = PIPE_CP_TEMPLATE
        elif find_executable('aws'):
            transfer_template = AWS_CP_TEMPLATE
        else:
            raise RuntimeError(
                'Unable to start CloudBucketDumpingEventHandler: no suitable command for transfer available')
        logging.info(format_message('[{}] will be used as a transfer template'.format(transfer_template)))
        return transfer_template

    @staticmethod
    def _configure_logging_bucket_dir():
        bucket_dir = os.path.join(os.getenv('CP_CAP_NFS_MNT_OBSERVER_TARGET_BUCKET', ''),
                                  CloudBucketDumpingEventHandler._get_service_name())
        logging.info(format_message('Destination bucket location is [{}]'.format(bucket_dir)))
        return bucket_dir

    @staticmethod
    def _get_service_name():
        service_name = os.getenv('RUN_ID')
        if not service_name:
            if os.getenv('CP_API_HOME'):
                service_name = 'api'
            elif os.getenv('CP_DAV_MOUNT_POINT'):
                service_name = 'dav'
            else:
                service_name = 'unknown'
        return service_name

    @staticmethod
    def configure_logging_local_dir():
        local_logging_dir = os.path.join(os.getenv('RUN_DIR', '/tmp'), 'fs_watcher')
        logging.info(format_message('Local storage directory is [{}]'.format(local_logging_dir)))
        mkdir(local_logging_dir)
        return local_logging_dir

    def _init_events_logger(self):
        self._events_local_file = os.path.join(self._activity_logging_local_dir, 'nfs_events')
        single_event_file_size_limit_mb = EVENT_FILES_LIMIT_MB / (EVENT_FILES_BACKUP_COUNT + 1)
        events_logging_handler = RotatingFileHandler(self._events_local_file, mode='a',
                                                     backupCount=EVENT_FILES_BACKUP_COUNT,
                                                     maxBytes=single_event_file_size_limit_mb * 1024 * 1024)
        events_logging_handler.setFormatter(logging.Formatter('%(message)s'))
        self._events_logger = logging.getLogger('nfs_events')
        self._events_logger.addHandler(events_logging_handler)
        self._events_logger.setLevel(logging.INFO)
        self._events_logger.propagate = False

    def _convert_event_to_str(self, event):
        for mnt_dest, mnt_src in self._target_path_mapping.items():
            path = event.path
            if path.startswith(mnt_dest):
                return COMMA.join([str(event.timestamp),
                                   event.event_type,
                                   mnt_src,
                                   path[len(mnt_dest) + 1:]])

    def _insert_event(self, file_path, event_type):
        if file_path not in self._active_events:
            event_descriptor = Event(file_path, event_type)
        else:
            last_event = self._active_events[file_path]
            last_event_type = last_event.event_type
            if last_event_type == CREATE_EVENT:
                if event_type == DELETE_EVENT or event_type == MOVED_FROM_EVENT:
                    del self._active_events[file_path]
                return
            else:
                event_descriptor = Event(file_path, event_type)
        self._active_events[file_path] = event_descriptor

    def update_target_mounts_mappings(self, new_mappings):
        self._target_path_mapping.update(new_mappings)

    def dispatch(self, event):
        if len(self._active_events) > EVENTS_LIMIT:
            self.dump_to_storage()
        if not event.is_directory:
            if type(event) is FileMovedEvent:
                self._insert_event(event.src_path, MOVED_FROM_EVENT)
                self._insert_event(event.dest_path, MOVED_TO_EVENT)
            # TODO check if filtering could be applied at observer creation
            elif event.event_type != 'closed':
                self._insert_event(event.src_path, event.event_type[:1])

    def check_timeout_and_dump(self):
        if (current_utc_time() - self.last_dump_time).total_seconds() > EVENT_DUMPING_TIMEOUT_SEC:
            self.dump_to_storage()

    def dump_to_storage(self):
        if len(self._active_events) != 0:
            logging.info(format_message('Saving events to {} '.format(self._events_local_file)))
            # TODO sorting might be skipped in case events will be sorted in the consuming service
            sorted_events = sorted(self._active_events.values(), key=lambda e: e.timestamp)
            for event in sorted_events:
                self._events_logger.info(self._convert_event_to_str(event))
            logging.info(format_message('Cleaning activity list'))
            self._active_events.clear()
        for rollover_backup in range(EVENT_FILES_BACKUP_COUNT, 0, -1):
            self._dump_event_file('{}.{}'.format(self._events_local_file, rollover_backup))
        self._dump_event_file(self._events_local_file)

    def _dump_event_file(self, local_event_file):
        if not os.path.exists(local_event_file) or os.stat(local_event_file).st_size == 0:
            return
        bucket_filename = 'events-' + current_utc_time_str().replace(' ', '_')
        bucket_file = os.path.join(self._activity_logging_bucket_dir, bucket_filename)
        logging.info(format_message('Dumping events to {} '.format(bucket_file)))
        _, result = execute_command(self._transfer_template.format(local_event_file, bucket_file))
        if result:
            self.last_dump_time = current_utc_time()
            with open(local_event_file, 'w'):
                pass


class NFSMountWatcher:

    WATCHER_LIMIT_FILE = '/tmp/.inotify_limit'

    def __init__(self, target_paths=None):
        self._target_path_mapping = dict()
        self._event_handler = CloudBucketDumpingEventHandler()
        self._event_observer = InotifyObserver()
        self._set_signal_handlers()
        self._watchers_limit = self._configure_watchers_limit()
        self._terminated = False
        if target_paths:
            self._target_paths = target_paths.split(COMMA)
            self._update_static_paths_mapping()
        else:
            self._target_paths = None
            self._update_target_mount_points()

    @staticmethod
    def _calculate_max_allowed_watchers():
        return psutil.virtual_memory().total / WATCHER_MEM_CONSUMPTION_BYTES / 100 * WATCHERS_USAGE_MEMORY_RATE

    @staticmethod
    def _get_watchers_limit():
        if os.path.exists(NFSMountWatcher.WATCHER_LIMIT_FILE):
            logging.info('Reading watchers configuration from the file...')
            try:
                with open(NFSMountWatcher.WATCHER_LIMIT_FILE, "r") as persistent_conf_file:
                    content = persistent_conf_file.readlines()
                    if len(content) > 0:
                        return int(content[0])
            except BaseException as e:
                logging.error('Error during limit retrieval from the file: {}'.format(e.message))
        logging.info('Reading watchers configuration from the env var...')
        return int(os.getenv('CP_CAP_NFS_MNT_OBSERVER_RUN_WATCHERS', 65535))

    def _configure_watchers_limit(self):
        watchers_limit = self._get_watchers_limit()
        logging.info(format_message('Watchers configuration: [{}]'.format(watchers_limit)))
        configure_kernel_param(INOTIFY_MAX_WATCHERS, watchers_limit)
        configure_kernel_param(INOTIFY_MAX_WATCHED_DIRS, watchers_limit)
        configure_kernel_param(INOTIFY_MAX_QUEUED_EVENTS, 2 * watchers_limit)
        return watchers_limit

    def _set_signal_handlers(self):
        for signal_code in [signal.SIGTERM, signal.SIGINT]:
            signal.signal(signal_code, self._shutdown_and_exit)

    def is_terminated(self):
        return self._terminated

    def _shutdown_and_exit(self, signal_code, frame):
        self.release_resources()
        self._terminated = True
        exit(0)

    def _process_active_target_path(self, mnt_dest, mnt_src):
        if mnt_dest not in self._target_path_mapping:
            self._add_new_mount_watcher(mnt_dest, mnt_src)
        else:
            self._update_existing_mount_watcher(mnt_dest, mnt_src)

    def _update_static_paths_mapping(self):
        logging.info(
            format_message('Observing FS events on [{}] target paths specified...'.format(len(self._target_paths))))
        active_mounts = self._get_target_mount_points()
        for mnt_dest in self._target_paths:
            mnt_src = active_mounts.get(mnt_dest, mnt_dest)
            self._process_active_target_path(mnt_dest, mnt_src)
        self._event_handler.update_target_mounts_mappings(self._target_path_mapping)

    def _update_target_mount_points(self):
        logging.info(format_message('Checking active mounts...'))
        latest_mounts_mapping = self._get_target_mount_points()
        logging.info(format_message('Found [{}] active mounts'.format(len(latest_mounts_mapping))))
        self._remove_unmounted_watchers(latest_mounts_mapping)
        for mnt_dest, mnt_src in latest_mounts_mapping.items():
            self._process_active_target_path(mnt_dest, mnt_src)
        self._event_handler.update_target_mounts_mappings(self._target_path_mapping)

    def _update_existing_mount_watcher(self, mnt_dest, mnt_src):
        active_mount_src = self._target_path_mapping[mnt_dest]
        if active_mount_src != mnt_src:
            logging.warning(format_message(
                'Updating observer: [{}] mount-source changed from [{}] to [{}]'.format(mnt_dest,
                                                                                        active_mount_src,
                                                                                        mnt_src)))
            self._target_path_mapping[mnt_dest] = mnt_src

    def _add_new_mount_watcher(self, mnt_dest, mnt_src):
        logging.warning(format_message('Assigning [{}] to the observer'.format(mnt_dest)))
        if self.try_to_add_path_to_observer(mnt_dest):
            self._target_path_mapping[mnt_dest] = mnt_src

    def _remove_unmounted_watchers(self, latest_mounts_mapping):
        for mnt_dest, mnt_src in self._target_path_mapping.items():
            if mnt_dest not in latest_mounts_mapping:
                logging.warning(format_message('Removing observer from [{}]'.format(mnt_dest)))
                if self.try_to_remove_path_from_observer(mnt_dest):
                    self._target_path_mapping.pop(mnt_dest)

    def try_to_remove_path_from_observer(self, mnt_dest):
        try:
            if len(self._target_path_mapping.items()) > 1:
                self._event_observer.unschedule(ObservedWatch(mnt_dest, True))
            return True
        except OSError as e:
            logging.error(
                format_message('Unable to drop observation on [{}], an error occurred: {}'.format(mnt_dest, e.message)))
            return False

    def try_to_add_path_to_observer(self, mnt_dest):
        if not os.path.exists(mnt_dest):
            logging.warning(format_message('Target path [{}] doesn\'t exist, skipping...'.format(mnt_dest)))
            return False
        try:
            self._event_observer.schedule(self._event_handler, mnt_dest, recursive=True)
            return True
        except OSError as e:
            logging.error(format_message('Unable to assign [{}], an error occurred: {}'.format(mnt_dest, e.message)))
            return False

    @staticmethod
    def _get_target_mount_points():
        available_storages_dict = NFSMountWatcher._get_available_storages_dict()
        is_admin = NFSMountWatcher._is_admin()
        out, res = execute_command(MNT_LISTING_COMMAND.format(TARGET_FS_TYPES))
        if not res or not out:
            logging.info(format_message('Unable to retrieve [{}] mounts'.format(TARGET_FS_TYPES)))
        else:
            for line in out.split(NEWLINE):
                if line:
                    mnt_details = re.search(MNT_PARSING_REGEXP, line).groups()
                    if len(mnt_details) == 4:
                        mount_details = MountPointDetails.from_array(mnt_details)
                        if NFSMountWatcher._shall_ignore_mountpoint(mount_details):
                            logging.info(format_message('Ignoring [{}] mount'.format(mount_details.mount_source)))
                            continue
                        mount_attributes = mount_details.mount_attributes.split(COMMA)
                        if READ_WRITE_OPTION in mount_attributes:
                            NFSMountWatcher.save_details_status_active(mount_details)
                        else:
                            NFSMountWatcher.save_details_status_readonly(mount_details)
            NFSMountWatcher.move_mount_statuses_from_tmp_file()
        mount_points = NFSMountWatcher._process_modified_mounts(available_storages_dict, is_admin)
        return mount_points

    @staticmethod
    def _shall_ignore_mountpoint(mountpoint):
        return mountpoint.mount_source in IGNORED_SOURCES or mountpoint.mount_point in IGNORED_MOUNTPOINTS

    @staticmethod
    def is_permission_set(storage, mask):
        return storage.mask & mask == mask

    @staticmethod
    def _get_mount_status(available_storages_dict, mount_details, is_admin, default):
        if is_admin is None:
            return MOUNT_STATUS_UNKNOWN
        elif is_admin:
            return MOUNT_STATUS_ACTIVE
        if available_storages_dict is None:
            return default
        matching_storage = NFSMountWatcher._find_matching_storage(available_storages_dict, mount_details)
        if matching_storage:
            status = matching_storage.mount_status
            if status == MOUNT_STATUS_DISABLED \
                    or (status == MOUNT_STATUS_ACTIVE
                        and not NFSMountWatcher.is_permission_set(matching_storage, WRITE_MASK)):
                return MOUNT_STATUS_READ_ONLY
            else:
                return status
        else:
            return DEFAULT_MOUNT_STATUS

    @staticmethod
    def _get_available_storages_dict():
        api = PipelineAPI(os.getenv('API'), 'logs')
        try:
            available_storages = api.load_available_storages()
            available_storages_dict = {storage.path: storage for storage in available_storages}
            return available_storages_dict
        except RuntimeError as e:
            logging.warning(format_message('Unable to load available storage list: {}'.format(str(e))))
            return None

    @staticmethod
    def _is_admin():
        api = PipelineAPI(os.getenv('API'), 'logs')
        try:
            user = api.load_current_user()
            if 'roles' in user and user['roles']:
                roles = user['roles']
                roles_names = [role['name'] for role in roles]
                return 'ROLE_ADMIN' in roles_names
            else:
                return False
        except RuntimeError as e:
            logging.warning(format_message('Unable to load current user: {}'.format(str(e))))
            return None

    @staticmethod
    def _process_modified_mounts(available_storages_dict, is_admin):
        mount_points = dict()
        modified_mounts_file = NFSMountWatcher.get_modified_mounts_file_path()
        if not os.path.exists(modified_mounts_file):
            logging.info(format_message('No modified mounts to check...'))
            return mount_points
        with open(modified_mounts_file, "r") as modified_mounts:
            lines = modified_mounts.readlines()
        if len(lines) < 1:
            logging.info(format_message('No modified mounts to check...'))
            return mount_points
        else:
            logging.info(format_message('Processing modified mounts'))
        for line in lines:
            line = line.rstrip()
            if not line:
                continue
            modified_mount_summary = line.split(MODIFIED_MNT_SEPARATOR)
            modified_mount_details = MountPointDetails.from_array(modified_mount_summary[0:-1])
            current_mount_status = modified_mount_summary[-1]
            new_mount_status = NFSMountWatcher._get_mount_status(available_storages_dict, modified_mount_details,
                                                                 is_admin, default=current_mount_status)
            if new_mount_status:
                if current_mount_status == new_mount_status \
                        or new_mount_status == MOUNT_STATUS_UNKNOWN:
                    if current_mount_status == MOUNT_STATUS_ACTIVE:
                        mount_points[modified_mount_details.mount_point] = modified_mount_details.mount_source
                    NFSMountWatcher.save_mount_details_to_modified_mounts_file(modified_mount_details,
                                                                               current_mount_status)
                else:
                    NFSMountWatcher._process_modified_mount(current_mount_status, new_mount_status,
                                                            modified_mount_details, mount_points)
            else:
                if current_mount_status == MOUNT_STATUS_ACTIVE:
                    mount_points[modified_mount_details.mount_point] = modified_mount_details.mount_source
                NFSMountWatcher.save_mount_details_to_modified_mounts_file(modified_mount_details, current_mount_status)
        NFSMountWatcher.move_mount_statuses_from_tmp_file()
        return mount_points

    @staticmethod
    def move_mount_statuses_from_tmp_file():
        modified_mounts_file = NFSMountWatcher.get_modified_mounts_file_path()
        tmp_modified_mounts_file = NFSMountWatcher.get_modified_mounts_file_path(use_tmp_file=True)
        if not os.path.exists(tmp_modified_mounts_file):
            os.mknod(tmp_modified_mounts_file)
        logging.info(format_message('Finalizing modified mounts from temp file'))
        shutil.move(tmp_modified_mounts_file, modified_mounts_file)

    @staticmethod
    def _process_modified_mount(current_mount_status, new_mount_status, modified_mount_details, mount_points):
        if current_mount_status == MOUNT_STATUS_DISABLED:
            NFSMountWatcher.process_currently_disabled_mount(modified_mount_details, mount_points, new_mount_status)
        elif current_mount_status == MOUNT_STATUS_READ_ONLY:
            NFSMountWatcher.process_currently_ro_mount(modified_mount_details, mount_points, new_mount_status)
        elif current_mount_status == MOUNT_STATUS_ACTIVE:
            NFSMountWatcher.process_currently_active_mount(modified_mount_details, mount_points, new_mount_status)
        else:
            logging.warning(format_message('Unknown mount status [{}]'.format(new_mount_status)))
            NFSMountWatcher.save_mount_details_to_modified_mounts_file(modified_mount_details, current_mount_status)

    @staticmethod
    def process_currently_ro_mount(modified_mount_details, mount_points, new_mount_status):
        if new_mount_status == MOUNT_STATUS_DISABLED:
            if NFSMountWatcher.try_to_unmount(modified_mount_details):
                NFSMountWatcher.save_details_status_disabled(modified_mount_details)
            else:
                NFSMountWatcher.save_details_status_readonly(modified_mount_details)
        elif new_mount_status == MOUNT_STATUS_ACTIVE:
            if NFSMountWatcher._try_remount_storage(modified_mount_details, False):
                NFSMountWatcher.save_details_status_active(modified_mount_details)
                mount_points[modified_mount_details.mount_point] = modified_mount_details.mount_source
            else:
                NFSMountWatcher.save_details_status_readonly(modified_mount_details)
        else:
            logging.warning(format_message('Unknown mount status [{}]'.format(new_mount_status)))
            NFSMountWatcher.save_details_status_readonly(modified_mount_details)

    @staticmethod
    def process_currently_disabled_mount(modified_mount_details, mount_points, new_mount_status):
        if new_mount_status == MOUNT_STATUS_READ_ONLY:
            if NFSMountWatcher._try_mount_storage(modified_mount_details, True):
                NFSMountWatcher.save_details_status_readonly(modified_mount_details)
            else:
                NFSMountWatcher.save_details_status_disabled(modified_mount_details)
        elif new_mount_status == MOUNT_STATUS_ACTIVE:
            if NFSMountWatcher._try_mount_storage(modified_mount_details, False):
                NFSMountWatcher.save_details_status_active(modified_mount_details)
                mount_points[modified_mount_details.mount_point] = modified_mount_details.mount_source
            else:
                NFSMountWatcher.save_details_status_disabled(modified_mount_details)
        else:
            logging.warning(format_message('Unknown mount status [{}]'.format(new_mount_status)))
            NFSMountWatcher.save_details_status_disabled(modified_mount_details)

    @staticmethod
    def process_currently_active_mount(mount_details, mount_points, new_mount_status):
        if new_mount_status == MOUNT_STATUS_DISABLED:
            if NFSMountWatcher.try_to_unmount(mount_details):
                NFSMountWatcher.save_details_status_disabled(mount_details, use_tmp_file=False)
                return
        elif new_mount_status == MOUNT_STATUS_READ_ONLY:
            if NFSMountWatcher._try_remount_storage(mount_details, True):
                NFSMountWatcher.save_details_status_readonly(mount_details, use_tmp_file=False)
                return
        elif new_mount_status != MOUNT_STATUS_ACTIVE:
                logging.info(format_message('Received unknown status [{}] for [{}]'.format(new_mount_status,
                                                                                           mount_details.mount_point)))
        mount_points[mount_details.mount_point] = mount_details.mount_source

    @staticmethod
    def save_details_status_disabled(mount_details, use_tmp_file=True):
        NFSMountWatcher.save_mount_details_to_modified_mounts_file(mount_details, MOUNT_STATUS_DISABLED,
                                                                   use_tmp_file=use_tmp_file)

    @staticmethod
    def save_details_status_readonly(modified_mount_details, use_tmp_file=True):
        NFSMountWatcher.save_mount_details_to_modified_mounts_file(modified_mount_details, MOUNT_STATUS_READ_ONLY,
                                                                   use_tmp_file=use_tmp_file)

    @staticmethod
    def save_details_status_active(mount_details, use_tmp_file=True):
        NFSMountWatcher.save_mount_details_to_modified_mounts_file(mount_details, MOUNT_STATUS_ACTIVE,
                                                                   use_tmp_file=use_tmp_file)

    @staticmethod
    def save_mount_details_to_modified_mounts_file(mount_details, status, use_tmp_file=True):
        modified_mounts_file = NFSMountWatcher.get_modified_mounts_file_path(use_tmp_file=use_tmp_file)
        logging.info(format_message('Saving status of [{}]: [{}]'.format(mount_details.mount_point, status)))
        with open(modified_mounts_file, "a") as modified_mounts:
            original_mount_summary = MODIFIED_MNT_SEPARATOR.join([mount_details.mount_source,
                                                                  mount_details.mount_point,
                                                                  mount_details.mount_type,
                                                                  mount_details.mount_attributes,
                                                                  status])
            modified_mounts.write(NEWLINE + original_mount_summary)

    @staticmethod
    def _try_remount_storage(mount_details, read_only):
        return NFSMountWatcher._execute_mount_command(mount_details, read_only, True)

    @staticmethod
    def _try_mount_storage(mount_details, read_only):
        return NFSMountWatcher._execute_mount_command(mount_details, read_only, False)

    @staticmethod
    def _execute_mount_command(mount_details, read_only, add_remount_flag):
        mount_options = mount_details.mount_attributes.split(COMMA)
        if read_only:
            mount_options = [READ_ONLY_OPTION if option == READ_WRITE_OPTION else option for option in mount_options]
        else:
            mount_options = [READ_WRITE_OPTION if option == READ_ONLY_OPTION else option for option in mount_options]
        if add_remount_flag:
            mount_options.append(REMOUNT_OPTION)
        mount_options = COMMA.join(mount_options)
        mkdir(mount_details.mount_point)
        mount_command = NFS_MOUNT_CMD_PATTERN.format(
            mount_details.mount_type, mount_options, mount_details.mount_source, mount_details.mount_point)
        logging.info(format_message('Trying to mount [{}] to [{}] in RO=[{}] mode'.format(mount_details.mount_source,
                                                                                          mount_details.mount_point,
                                                                                          read_only)))
        out, res = execute_command(mount_command)
        if not res:
            logging.warning(
                format_message('Unable to mount [{}] to [{}] in RO=[{}] mode: {}'.format(mount_details.mount_source,
                                                                                         mount_details.mount_point,
                                                                                         read_only,
                                                                                         out)))
        return res

    @staticmethod
    def try_to_unmount(mount_details):
        logging.info(format_message('Trying to unmount [{}] from [{}]'.format(mount_details.mount_source,
                                                                               mount_details.mount_point)))
        out, res = execute_command(NFS_UNMOUNT_CMD_PATTERN.format(mount_details.mount_point))
        if not res:
            logging.warning(format_message('Unable to umount [{}]: {}'.format(mount_details.mount_point, out)))
        return res

    @staticmethod
    def get_modified_mounts_file_path(use_tmp_file=False):
        path = os.path.join(CloudBucketDumpingEventHandler.configure_logging_local_dir(), 'modified_mounts')
        if use_tmp_file:
            path += '-tmp'
        return path

    @staticmethod
    def _find_matching_storage(available_storages_dict, mount_details):
        matching_storage = None
        if mount_details.mount_type == 'lustre':
            for path, storage in available_storages_dict.items():
                lustre_path_chunks = path.split(LNET_SPLIT, 1)
                if len(lustre_path_chunks) == 2:
                    lustre_host_ip = socket.gethostbyname(lustre_path_chunks[0])
                    lustre_target_source = lustre_host_ip + LNET_SPLIT + lustre_path_chunks[1]
                    if lustre_target_source == mount_details.mount_source:
                        matching_storage = storage
                        break
        else:
            matching_storage = available_storages_dict.get(mount_details.mount_source)
        return matching_storage

    def _get_mnt_resync_timeout(self):
        if MNT_RESYNC_TIMEOUT_SEC > EVENT_DUMPING_TIMEOUT_SEC:
            logging.warning(
                format_message('Mount scanning timeout [{}] is greater, than dumping timeout [{}]. Using the second one'
                               .format(MNT_RESYNC_TIMEOUT_SEC, EVENT_DUMPING_TIMEOUT_SEC)))
            resync_timeout = EVENT_DUMPING_TIMEOUT_SEC
        else:
            resync_timeout = MNT_RESYNC_TIMEOUT_SEC
        return resync_timeout

    def try_increase_watchers_limit(self):
        new_limit = 2 * self._watchers_limit
        max_allowed_watchers = self._calculate_max_allowed_watchers()
        if new_limit > max_allowed_watchers:
            logging.warning(format_message('Unable to increase limit up to [{}], maximum allowed is [{}]'
                                           .format(new_limit, max_allowed_watchers)))
            return
        logging.warning(format_message('Updating watchers limit in `{}` with [{}]'
                                       .format(NFSMountWatcher.WATCHER_LIMIT_FILE, new_limit)))
        with open(NFSMountWatcher.WATCHER_LIMIT_FILE, "w") as out:
            out.write(str(new_limit))

    def release_resources(self):
        self._event_observer.stop()
        if len(self._target_path_mapping.items()) > 1:
            self._event_observer.unschedule_all()
        self._event_handler.dump_to_storage()

    def start(self):
        logging.info(format_message('Start monitoring shares state...'))
        self._event_observer.start()
        resync_timeout = self._get_mnt_resync_timeout()
        while True:
            time.sleep(resync_timeout)
            if self._target_paths:
                self._update_static_paths_mapping()
            else:
                self._update_target_mount_points()
            self._event_handler.check_timeout_and_dump()


if __name__ == '__main__':
    while True:
        watcher = NFSMountWatcher(target_paths=os.getenv('CP_CAP_NFS_OBSERVER_TARGET_PATHS'))
        try:
            watcher.start()
        except BaseException as e:
            if watcher.is_terminated():
                logging.warning(format_message('Termination signal received, exiting.'))
                exit(0)
            error_message = str(e)
            logging.error(format_message('An exception occurred in the observer: {}'.format(error_message)))
            if 'inotify' in error_message:
                logging.error(format_message('Error is considered related to inotify, trying to increase limits...'))
                watcher.try_increase_watchers_limit()
            logging.error(format_message('Restarting the observer...'))
        finally:
            watcher.release_resources()
