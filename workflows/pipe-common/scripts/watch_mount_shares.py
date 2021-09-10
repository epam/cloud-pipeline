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
import errno
import os
import re
import subprocess
import time
from watchdog.observers.inotify import InotifyObserver
from watchdog.events import FileSystemEventHandler, FileMovedEvent
from watchdog.observers.api import ObservedWatch

NEWLINE = '\n'

COMMA = ','
EVENTS_LIMIT = int(os.getenv('CP_CAP_NFS_OBSERVER_EVENTS_LIMIT', 3))
TARGET_FS_TYPES = os.getenv('CP_CAP_NFS_OBSERVER_TARGET_FS_TYPES', 'nfs4,lustre').split(COMMA)
MNT_LISTING_COMMAND = 'df -t {} --output=fstype,source,target'
DT_FORMAT = '%Y-%m-%d %H:%M:%S:%f'

CREATE_EVENT = 'c'
MODIFY_EVENT = 'm'
MOVED_FROM_EVENT = 'mf'
MOVED_TO_EVENT = 'mt'
DELETE_EVENT = 'd'


def log(message):
    print('[{}] {}'.format(current_utc_time_str(), message))


def mkdir(path):
    try:
        os.makedirs(path)
    except OSError as e:
        if e.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            raise


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
        log('Execution of [{}] failed due to the reason: {}'.format(command, stderr))
    return stdout, success


def current_utc_time():
    return datetime.datetime.utcnow()


def current_utc_time_millis():
    return int((current_utc_time() - datetime.datetime(1970, 1, 1)).total_seconds() * 1000)


def current_utc_time_str():
    return current_utc_time().strftime(DT_FORMAT)


class Event:

    def __init__(self, path, event_type):
        self.timestamp = current_utc_time_millis()
        self.path = path
        self.event_type = event_type


class S3DumpingEventHandler(FileSystemEventHandler):

    def __init__(self):
        super(FileSystemEventHandler, self).__init__()
        self._active_events = OrderedDict()
        self._target_path_mapping = dict()
        self._activity_logging_local_dir = self._configure_logging_local_dir()
        self._activity_logging_bucket_dir = self._configure_logging_bucket_dir()

    @staticmethod
    def _configure_logging_bucket_dir():
        bucket_dir = os.path.join(os.getenv('CP_CAP_NFS_MNT_OBSERVER_TARGET_BUCKET'),
                                  S3DumpingEventHandler._get_service_name())
        log('Destination bucket location is [{}]'.format(bucket_dir))
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
    def _configure_logging_local_dir():
        local_logging_dir = os.path.join(os.getenv('RUN_DIR', '/tmp'), 'fs_watcher')
        log('Local storage directory is [{}]'.format(local_logging_dir))
        mkdir(local_logging_dir)
        return local_logging_dir

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

    def dump_to_storage(self):
        if len(self._active_events) == 0:
            return
        filename = 'events-' + current_utc_time_str().replace(' ', '_')
        local_file = os.path.join(self._activity_logging_local_dir, filename)
        log('Saving events to {} '.format(local_file))
        with open(local_file, 'w') as outfile:
            # TODO sorting might be skipped in case events will be sorted in the consuming service
            sorted_events = sorted(self._active_events.values(), key=lambda e: e.timestamp)
            outfile.write(NEWLINE.join(map(self._convert_event_to_str, sorted_events)))
        bucket_file = os.path.join(self._activity_logging_bucket_dir, filename)
        log('Dumping events to {} '.format(bucket_file))
        _, result = execute_command('pipe storage cp \'{}\' \'{}\''.format(local_file, bucket_file))
        if result:
            log('Cleaning activity list')
            self._active_events.clear()


class NFSMountWatcher:

    def __init__(self):
        self._target_path_mapping = dict()
        self._event_handler = S3DumpingEventHandler()
        self._event_observer = InotifyObserver()
        self._update_target_mount_points()

    def _update_target_mount_points(self):
        log('Checking active mounts...')
        latest_mounts_mapping = self._get_target_mount_points()
        log('Found [{}] active mounts'.format(len(latest_mounts_mapping)))
        self._remove_unmounted_watchers(latest_mounts_mapping)
        for mnt_dest, mnt_src in latest_mounts_mapping.items():
            if mnt_dest not in self._target_path_mapping:
                self._add_new_mount_watcher(mnt_dest, mnt_src)
            else:
                self._update_existing_mount_watcher(mnt_dest, mnt_src)
        self._event_handler.update_target_mounts_mappings(self._target_path_mapping)

    def _update_existing_mount_watcher(self, mnt_dest, mnt_src):
        active_mount_src = self._target_path_mapping[mnt_dest]
        if active_mount_src != mnt_src:
            log('Updating observer: [{}] mount-source changed from [{}] to [{}]'.format(mnt_dest,
                                                                                        active_mount_src,
                                                                                        mnt_src))
            self._target_path_mapping[mnt_dest] = mnt_src

    def _add_new_mount_watcher(self, mnt_dest, mnt_src):
        log('Assigning [{}] to the observer'.format(mnt_dest))
        if self.try_to_add_path_to_observer(mnt_dest):
            self._target_path_mapping[mnt_dest] = mnt_src

    def _remove_unmounted_watchers(self, latest_mounts_mapping):
        for mnt_dest, mnt_src in self._target_path_mapping.items():
            if mnt_dest not in latest_mounts_mapping:
                log('Removing observer from [{}]'.format(mnt_dest))
                if self.try_to_remove_path_from_observer(mnt_dest):
                    self._target_path_mapping.pop(mnt_dest)

    def try_to_remove_path_from_observer(self, mnt_dest):
        try:
            self._event_observer.unschedule(ObservedWatch(mnt_dest, True))
            return True
        except OSError as e:
            log('Unable to assign [{}], an error occurred: {}'.format(mnt_dest, e.message))
            return False

    def try_to_add_path_to_observer(self, mnt_dest):
        try:
            self._event_observer.schedule(self._event_handler, mnt_dest, recursive=True)
            return True
        except OSError as e:
            log('Unable to assign [{}], an error occurred: {}'.format(mnt_dest, e.message))
            return False

    @staticmethod
    def _get_target_mount_points():
        mount_points = dict()
        for fs_type in TARGET_FS_TYPES:
            out, res = execute_command(MNT_LISTING_COMMAND.format(fs_type))
            if not res:
                log('Unable to retrieve [{}] mounts'.format(fs_type))
                continue
            out = out.split(NEWLINE)
            for index in range(1, len(out)):
                line = out[index]
                if line.strip():
                    mount_point_description = re.split('\\s+', line)
                    mount_source = mount_point_description[1]
                    mount_point = mount_point_description[2]
                    mount_points[mount_point] = mount_source
        return mount_points

    def start(self):
        log('Start monitoring shares state...')
        self._event_observer.start()
        try:
            while True:
                time.sleep(10)
                self._update_target_mount_points()
        finally:
            self._event_observer.stop()
            self._event_observer.join()
            self._event_handler.dump_to_storage()


if __name__ == '__main__':
    NFSMountWatcher().start()
