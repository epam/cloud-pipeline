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

# CP_S3_FUSE_STAT_CACHE (default: 1m0s)
# CP_S3_FUSE_TYPE_CACHE (default: 1m0s)
# CP_S3_FUSE_ENSURE_DISKFREE (default: None)
# CP_S3_FUSE_TYPE: goofys/s3fs (default: goofys)
# CP_CAP_MOUNT_REFRESH_INTERVAL (default: 300 seconds)

import argparse
import platform
import re
import os
import signal
import socket
import threading
import time
import traceback
from abc import ABCMeta, abstractmethod

from pipeline import PipelineAPI, Logger, common, DataStorageWithShareMount, AclClass, APIError

READ_MASK = 1
WRITE_MASK = 1 << 1

DTS = 'DTS'
EXEC_ENVIRONMENT = 'EXEC_ENVIRONMENT'
NFS_TYPE = 'NFS'
S3_TYPE = 'S3'
AZ_TYPE = 'AZ'
GCP_TYPE = 'GS'
MOUNT_DATA_STORAGES = 'MountDataStorages'
S3_SCHEME = 's3://'
AZ_SCHEME = 'az://'
NFS_SCHEME = 'nfs://'
GCP_SCHEME = 'gs://'
FUSE_GOOFYS_ID = 'goofys'
FUSE_S3FS_ID = 's3fs'
FUSE_PIPE_ID = 'pipefuse'
FUSE_GCSFUSE_ID = 'gcsfuse'
FUSE_NA_ID = None
AZURE_PROVIDER = 'AZURE'
S3_PROVIDER = 'S3'
READ_ONLY_MOUNT_OPT = 'ro'
READ_WRITE_MOUNT_OPT = 'rw'
MOUNT_LIMITS_NONE = 'none'
MOUNT_ATTRIBUTES_SEP = ','
MOUNT_LIMITS_USER_DEFAULT = 'user_default'
MOUNT_LIMITS_PARENT_FOLDER_ID = 'folder:'
SENSITIVE_POLICY_PREFERENCE = 'storage.mounts.nfs.sensitive.policy'
STORAGE_MOUNT_OPTIONS_ENV_PREFIX = 'CP_CAP_MOUNT_OPTIONS_'
STORAGE_MOUNT_PATH_ENV_PREFIX = 'CP_CAP_MOUNT_PATH_'

CP_CAP_MOUNT_REFRESH_INTERVAL = int(os.getenv('CP_CAP_MOUNT_REFRESH_INTERVAL', 300))  # in seconds (5 minutes)
CP_CAP_ALLOWED_MOUNT_TYPES = "${CP_CAP_MOUNT_TYPES:-cifs,fuse,nfs,nfs4,lustre}"

MOUNT_FILE = '/proc/mounts'
MOUNT_LINE_PATTERN = r'^(\S+)\s+(\S+)\s+(\S+)\s+(\S+)\s+\d+\s+\d+'
UNMOUNT_CMD_PATTERN = "umount -l -f \"{}\""
DELETE_CMD_PATTERN = "rm -rf \"{}\""

MOUNT_STATUS_DISABLED = 'MOUNT_DISABLED'
MOUNT_STATUS_READ_ONLY = 'READ_ONLY'
MOUNT_STATUS_ACTIVE = 'ACTIVE'
MOUNT_STATUS_UNKNOWN = 'UNKNOWN'
DEFAULT_MOUNT_STATUS = os.getenv('CP_CAP_NFS_OBSERVER_DEFAULT_STORAGE_STATUS', 'UNKNOWN')
LNET_SPLIT = '@tcp:/'
TARGET_FS_TYPES = os.getenv('CP_CAP_NFS_OBSERVER_TARGET_FS_TYPES', 'nfs,nfs4,lustre')


class MountOptions:
    def __init__(self, mount_params, mount_path):
        self.mount_params = mount_params
        self.mount_path = mount_path


class PermissionHelper:

    def __init__(self):
        pass

    @classmethod
    def is_storage_readable(cls, storage):
        return cls.is_permission_set(storage, READ_MASK)

    @classmethod
    def is_storage_mount_disabled(cls, storage):
        return storage.mount_disabled is True

    # Checks that tool with its version for the current run is allowed to mount this storage
    # or there is no configured restriction for this storage
    @classmethod
    def is_storage_available_for_mount(cls, storage, run):
        if not storage.tools_to_mount:
            return True
        if not run or not run.get("actualDockerImage"):
            return False

        tool = run["actualDockerImage"]
        re_result = re.search(r"([^/]+)/([^:]+):?(:?.*)", tool)
        registry, image, version = re_result.groups()
        for tool_to_mount in storage.tools_to_mount:
            if registry == tool_to_mount["registry"] and image == tool_to_mount["image"]:
                tool_versions_to_mount = tool_to_mount.get('versions', [])
                return not tool_versions_to_mount or version in [v["version"] for v in tool_versions_to_mount]
        return False

    @classmethod
    def is_storage_writable(cls, storage):
        write_permission_granted = cls.is_permission_set(storage, WRITE_MASK)
        if not cls.is_run_sensitive():
            return write_permission_granted
        if storage.sensitive:
            return write_permission_granted
        else:
            return False

    @classmethod
    def is_permission_set(cls, storage, mask):
        return storage.mask & mask == mask

    @classmethod
    def is_run_sensitive(cls):
        sensitive_run = os.getenv('CP_SENSITIVE_RUN', "false")
        return sensitive_run.lower() == 'true'


class MountAttributes:
    def __init__(self, attributes):
        self.permission = READ_WRITE_MOUNT_OPT if READ_WRITE_MOUNT_OPT in attributes else READ_ONLY_MOUNT_OPT

    def __repr__(self):
        return "<MountAttributes: permission=%s>" % self.permission


class MountPointDetails:
    def __init__(self, mount_source, mount_point, mount_type, mount_attributes):
        self.mount_source = mount_source
        self.mount_point = mount_point
        self.mount_type = mount_type
        self.mount_attributes = mount_attributes

    @staticmethod
    def from_array(array):
        if len(array) == 4:
            attributes = MountAttributes(array[3].split(MOUNT_ATTRIBUTES_SEP))
            return MountPointDetails(array[0], array[1], array[2], attributes)
        else:
            return None

    @staticmethod
    def execute_unmounts(mounts, task_name):
        failed_storages = []
        for mount_point, mnt in mounts.items():
            try:
                mnt.unmount(mount_point, task_name)
            except RuntimeError:
                Logger.warn('Data storage {} unmounting has failed: {}'
                            .format(mount_point, traceback.format_exc()),
                            task_name=task_name)
                failed_storages.append(mount_point)
        if failed_storages:
            Logger.fail('The following data storages have not been unmounted: {}'.format(', '.join(failed_storages)),
                        task_name=task_name)

    def unmount(self, mount_point, task_name):
        self.execute_unmount(UNMOUNT_CMD_PATTERN.format(mount_point), mount_point, task_name)
        self.execute_remove(DELETE_CMD_PATTERN.format(mount_point), mount_point, task_name)

    @staticmethod
    def execute_unmount(command, mount_point, task_name):
        exit_code = common.execute_cmd_command(command, executable=None if StorageMounter.is_windows() else '/bin/bash')
        if exit_code == 0:
            Logger.info('-->{} was unmounted'.format(mount_point), task_name=task_name)
        else:
            Logger.warn('--> Failed unmounting {}'.format(mount_point), task_name=task_name)
            raise RuntimeError('Failed unmounting {}'.format(mount_point))

    @staticmethod
    def execute_remove(command, mount_point, task_name):
        exit_code = common.execute_cmd_command(command, executable=None if StorageMounter.is_windows() else '/bin/bash')
        if exit_code == 0:
            Logger.info('--> Cleaning up {} path'.format(mount_point), task_name=task_name)
        else:
            Logger.warn('--> Failed cleaning up {} path'.format(mount_point), task_name=task_name)
            raise RuntimeError('Failed cleaning up {} path'.format(mount_point))

    def get_chmod(self, task_name):
        try:
            mode = os.stat(self.mount_point).st_mode
            # Convert to octal string from permission bits
            return oct(mode & 0o777)[1:]
        except Exception as e:
            Logger.warn('Unable to get chmod value from a mounted directory {}: {}.'.format(self.mount_point, str(e)),
                        task_name=task_name)

    def __repr__(self):
        return "<MountPointDetails: %s %s %s %s>" % (self.mount_source, self.mount_point, self.mount_type,
                                                     self.mount_attributes)


class MountStorageTask:

    def __init__(self, task):
        self.api = PipelineAPI(os.environ['API'], 'logs')
        self.run_id = int(os.getenv('RUN_ID', 0))
        self.region_id = int(os.getenv('CLOUD_REGION_ID', 0))
        self.task_name = task
        self.available_storages = None
        if platform.system() == 'Windows':
            available_mounters = [S3Mounter, GCPMounter]
        else:
            available_mounters = [NFSMounter, S3Mounter, AzureMounter, GCPMounter]
        self.mounters = {mounter.type(): mounter for mounter in available_mounters}
        self.user_info = self.load_user()
        self.auto_mount_umount = os.getenv('CP_CAP_MOUNT_STORAGE_MONITOR_AUTO_MOUNT_UMOUNT', 'true').lower() == 'true'
        self.auto_permissions = os.getenv('CP_CAP_MOUNT_STORAGE_MONITOR_AUTO_PERMISSIONS', 'true').lower() == 'true'
        self.auto_nfs_status = os.getenv('CP_CAP_MOUNT_STORAGE_MONITOR_AUTO_NFS_STATUS', 'true').lower() == 'true'
        self.shutdown_requested = False

    def parse_storage(self, placeholder, available_storages):
        storage_id = None
        try:
            if placeholder.lower() == MOUNT_LIMITS_USER_DEFAULT:
                if 'defaultStorageId' in self.user_info:
                    storage_id = int(self.user_info['defaultStorageId'])
                    Logger.info('User default storage is parsed as {}'.format(str(storage_id)),
                                task_name=self.task_name)
            elif placeholder.lower() == MOUNT_LIMITS_NONE:
                Logger.info('{} placeholder found while parsing storage id, skipping it'.format(MOUNT_LIMITS_NONE),
                            task_name=self.task_name)
            elif placeholder.lower().startswith(MOUNT_LIMITS_PARENT_FOLDER_ID):
                folder_parts = placeholder.split(':')
                
                parent_folder_id = 0
                if len(folder_parts) == 2:
                    try:
                        parent_folder_id = int(folder_parts[1])
                    except:
                        Logger.warn('Unable to parse {} placeholder to a parent folder ID, '
                                    'cannot convert it to integer.'.format(placeholder), task_name=self.task_name)

                    if parent_folder_id != 0 and self.available_storages:
                        storage_id = [x.storage.id for x in self.available_storages
                                      if x.storage.parentId == parent_folder_id]
                        if len(storage_id) == 0:
                            storage_id = None
                        else:
                            Logger.info('Parent folder ID {} is parsed into {} storage IDs'
                                        .format(placeholder, storage_id), task_name=self.task_name)
                else:
                    Logger.warn('Unable to parse {} placeholder to a parent folder ID, it is malformed.'
                                .format(placeholder), task_name=self.task_name)
            else:
                storage_identifier = placeholder.strip()
                if storage_identifier.isdigit():
                    return int(storage_identifier)
                if available_storages and available_storages.get(storage_identifier):
                    return int(available_storages.get(storage_identifier))
        except Exception as parse_storage_ex:
            Logger.warn('Unable to parse {} placeholder to a storage ID: {}.'
                        .format(placeholder, str(parse_storage_ex)), task_name=self.task_name)
        return storage_id

    def parse_storage_list(self, csv_storages, available_storages):
        result = []
        for item in csv_storages.split(','):
            storage_id = self.parse_storage(item, available_storages)
            if storage_id:
                if type(storage_id) is list:
                    result = result + storage_id
                else:
                    result.append(storage_id)
        return result

    def load_user(self):
        api = PipelineAPI(os.getenv('API'), 'logs')
        try:
            return api.load_current_user()
        except RuntimeError as e:
            Logger.fail('Unable to load current user: {}.'.format(str(e)), task_name=self.task_name)
            return None

    def _is_admin(self):
        if not self.user_info:
            return
        if 'roles' in self.user_info and self.user_info['roles']:
            roles = self.user_info['roles']
            roles_names = [role['name'] for role in roles]
            return 'ROLE_ADMIN' in roles_names
        else:
            return False

    # Any conditions to wait for before starting the mount procedure
    def wait_before_mount(self):
        try:
            wait_before_mount_attempts = int(os.getenv('CP_SENSITIVE_RUN_WAIT_POD_IP_SEC', 10))
            wait_before_mount_timeout_sec = 3

            # 1. If it's a sensitive job - we shall be sure that a "Run" has a Pod IP assigned.
            # Otherwise we won't be able to mount sensitive storages.
            # API will consider this job as "outside of the sensitive context"
            is_sensitive_job = os.getenv('CP_SENSITIVE_RUN')
            if is_sensitive_job == 'true':
                Logger.info('A sensitive job detected, will wait for the Pod IP assignment', task_name=self.task_name)
                current_wait_iteration = 1
                while current_wait_iteration <= wait_before_mount_attempts:
                    current_run = self.api.load_run(self.run_id)
                    if not current_run:
                        Logger.warn('Cannot load run info, while waiting for the sensitive pod IP assignment. '
                                    'Will not wait anymore', task_name=self.task_name)
                        break
                    else:
                        if 'podIP' in current_run and current_run['podIP'] != '' and current_run['podIP'] is not None:
                            Logger.info('Pod IP is assigned, proceeding further', task_name=self.task_name)
                            break
                        Logger.info('Pod IP is NOT available yet, waiting...', task_name=self.task_name)
                        current_wait_iteration = current_wait_iteration + 1
                        time.sleep(wait_before_mount_timeout_sec)
            # 2. ... Add more conditions here ...
            #    ...
        except Exception as e:
            Logger.fail('An error occured while waiting for the mounts prerequisites: {}.'.format(str(e.message)),
                        task_name=self.task_name)

    def run(self, mount_root, tmp_dir):
        try:
            Logger.info('Starting mounting remote data storages.', task_name=self.task_name)

            self.wait_before_mount()

            if not self.region_id:
                Logger.warn('Cannot filter storages by region because cloud region id is not configured. '
                            'No file storages will be mounted and object storages from all regions will be mounted.',
                            task_name=self.task_name)

            available_storages_with_mounts, storages_ids_by_path = self._load_and_filter_storages()
            run = self._load_run_info()

            self._perform_mount(mount_root, tmp_dir, available_storages_with_mounts, storages_ids_by_path, run,
                                monitor=False)
            Logger.success('Finished initial data storage mounting', task_name=self.task_name)

            if self.auto_mount_umount or self.auto_nfs_status:
                self._setup_signal_handlers()
                if self.auto_mount_umount:
                    Logger.info('Starting auto mount/umount monitor...', task_name=self.task_name)
                else:
                    Logger.info("Auto mount/umount disabled. Performing one-time mount.", task_name=self.task_name)
                if self.auto_nfs_status:
                    Logger.info('Starting nfs status monitor...', task_name=self.task_name)
                else:
                    Logger.info("NFS status monitor disabled.", task_name=self.task_name)

            monitor_thread = threading.Thread(
                target=self._monitor_loop,
                args=(mount_root, tmp_dir)
            )
            monitor_thread.start()
        except Exception:
            Logger.fail('Unhandled error during mount task: {}.'.format(traceback.format_exc()),
                        task_name=self.task_name)
            Logger.fail('Failed data storage mounting', task_name=self.task_name)
            exit(1)

    def _monitor_loop(self, mount_root, tmp_dir):
        while not self.shutdown_requested:
            try:
                available_storages_with_mounts, storages_ids_by_path = self._load_and_filter_storages()
                run = self._load_run_info()
                if self.auto_mount_umount:
                    self._perform_mount(mount_root, tmp_dir, available_storages_with_mounts, storages_ids_by_path, run,
                                        monitor=True)
                if self.auto_nfs_status and self.region_id:
                    self._process_modified_mounts(mount_root, available_storages_with_mounts, storages_ids_by_path, run)
            except Exception:
                Logger.fail('Unhandled error during monitor loop: {}.'.format(traceback.format_exc()),
                            task_name=self.task_name)
            time.sleep(CP_CAP_MOUNT_REFRESH_INTERVAL)

    def _perform_mount(self, mount_root, tmp_dir, available_storages_with_mounts, storages_ids_by_path, run,
                       monitor=False):
        if not available_storages_with_mounts:
            return

        self._prepare_mounters(available_storages_with_mounts, tmp_dir)
        if all([not mounter.is_available() for mounter in self.mounters.values()]):
            Logger.success('Mounting of remote storages is not available for this image', task_name=self.task_name)
            return
        initialized_mounters = self._initialize_mounters(available_storages_with_mounts, storages_ids_by_path,
                                                         mount_root, run)
        if not monitor:
            self._execute_mounts(initialized_mounters, mount_root)
            return

        to_mount, to_unmount = self.get_updated_mounters(initialized_mounters, mount_root)

        MountPointDetails.execute_unmounts(to_unmount, self.task_name)
        self._execute_mounts(to_mount.values(), mount_root)

    def _process_modified_mounts(self, mount_root, available_storages_with_mounts, storages_ids_by_path, run):
        nfs_available_storages_dict = {}
        nfs_available_mounters = []
        for x in available_storages_with_mounts:
            if x.storage.storage_type == NFS_TYPE:
                nfs_available_storages_dict[x.storage.path] = x.storage
                nfs_available_mounters.append(x)

        if not nfs_available_storages_dict:
            Logger.info('NFS type storages was not found. Skipping modified mounts monitor.', task_name=self.task_name)
            return
        Logger.info("Processing modified mounts", task_name=self.task_name)
        initialized_nfs_mounters = self._initialize_mounters(nfs_available_mounters,
                                                             storages_ids_by_path, mount_root, run)
        to_unmount, to_mount = self.get_updated_nfs_mounters(nfs_available_storages_dict, initialized_nfs_mounters,
                                                             mount_root)
        MountPointDetails.execute_unmounts(to_unmount, self.task_name)
        self._execute_mounts(to_mount, mount_root)

    def get_updated_nfs_mounters(self, nfs_available_storages_dict, initialized_mounters, mount_root):
        all_mounted = self.get_currently_mounted_storages()
        filtered_mounted = self._filter_mounted_by_fs_type(all_mounted)
        if not filtered_mounted:
            return
        new_mounters = dict((mount.build_mount_point(mount_root), mount) for mount in initialized_mounters)
        to_unmount = {}
        to_mount = []
        for mnt_point, current_mount in filtered_mounted.items():
            if mnt_point in new_mounters:
                mounter = new_mounters.get(mnt_point)
                if not isinstance(mounter, NFSMounter):
                    continue
                current_mount_status = self._get_current_mount_status(current_mount)
                new_mount_status = mounter.get_mount_status(nfs_available_storages_dict, current_mount,
                                                            self._is_admin(), default=current_mount_status)
                if new_mount_status and current_mount_status != new_mount_status:
                    to_unmount[mnt_point] = current_mount
                    to_mount.append(mounter)
        return to_unmount, to_mount

    @staticmethod
    def _get_current_mount_status(current_mount):
        permission = current_mount.mount_attributes.permission
        if permission == READ_WRITE_MOUNT_OPT:
            return MOUNT_STATUS_ACTIVE
        elif permission == READ_ONLY_MOUNT_OPT:
            return MOUNT_STATUS_READ_ONLY
        else:
            return MOUNT_STATUS_UNKNOWN

    @staticmethod
    def _filter_mounted_by_fs_type(all_mounted):
        target_fs_types = set(t.strip().lower() for t in TARGET_FS_TYPES.split(','))
        return {mount_point: details for mount_point, details in all_mounted.items()
                if details.mount_type.lower() in target_fs_types}

    def get_updated_mounters(self, initialized_mounters, mount_root):
        current = dict((mount.build_mount_point(mount_root), mount) for mount in initialized_mounters)
        mounted = self.get_currently_mounted_storages()
        added, updated, removed, updated_from_mounted = {}, {}, {}, {}
        if mounted:
            added = dict((mnt_point, mnt) for mnt_point, mnt in current.items() if mnt_point not in mounted)
            removed = dict((mnt_point, mnt) for mnt_point, mnt in mounted.items() if mnt_point not in current)
            if self.auto_permissions:
                for mnt_point, mnt in current.items():
                    permission_changed = (mnt_point in mounted and
                                          mounted[mnt_point].mount_attributes.permission != mnt.get_permissions())
                    # print("permission_changed: " + str(permission_changed))
                    # chmod_changed = (isinstance(mnt, NFSMounter) and mnt_point in mounted and
                    #                  mounted[mnt_point].get_chmod(task_name=self.task_name) != mnt.get_metadata_chmod())
                    # print("chmod_changed: " + str(chmod_changed))
                    if permission_changed:
                        updated[mnt_point] = mnt
                        updated_from_mounted[mnt_point] = mounted[mnt_point]
            if added or removed or updated:
                Logger.info("Detected changes: added={}, removed={}, updated={}"
                            .format(list(added), list(removed), list(updated)), task_name=self.task_name)
        to_unmount = self.combine_mounters(removed, updated_from_mounted)
        to_mount = self.combine_mounters(added, updated)
        return to_mount, to_unmount

    def _setup_signal_handlers(self):
        def handle_shutdown(signum, frame):
            self.shutdown_requested = True
            Logger.info("Shutdown signal received. Exiting monitoring loop.", task_name=self.task_name)
            exit(0)
        for signal_code in [signal.SIGTERM, signal.SIGINT]:
            signal.signal(signal_code, handle_shutdown)

    @staticmethod
    def combine_mounters(priority_mounters, included_mounters):
        combined_mounters = priority_mounters.copy()
        for mnt_point, mnt in included_mounters.iteritems():
            if mnt_point not in combined_mounters:
                combined_mounters[mnt_point] = mnt
        return combined_mounters

    def get_currently_mounted_storages(self):
        mounted = {}
        if not os.path.exists(MOUNT_FILE):
            Logger.warn("{} file not found. This system may not support mount checks. "
                        "Mount status monitoring may not work as expected.".format(MOUNT_FILE),
                        task_name=self.task_name)
            return mounted
        try:
            with open(MOUNT_FILE, 'r') as mounts:
                for line in mounts:
                    mount_line_pattern = re.compile(MOUNT_LINE_PATTERN)
                    match = mount_line_pattern.match(line)
                    if not match:
                        continue
                    mount_details = MountPointDetails.from_array(match.groups())
                    if mount_details.mount_type.lower() not in CP_CAP_ALLOWED_MOUNT_TYPES:
                        continue
                    mounted[mount_details.mount_point] = mount_details
        except Exception as e:
            Logger.warn("Failed to parse {}: {}".format(MOUNT_FILE, str(e)), task_name=self.task_name)
        return mounted

    def _execute_mounts(self, initialized_mounters, mount_root):
        failed_storages = []
        for mnt in initialized_mounters:
            try:
                mnt.mount(mount_root, self.task_name)
            except RuntimeError:
                Logger.warn('Data storage {} mounting has failed: {}'
                            .format(mnt.storage.name, traceback.format_exc()),
                            task_name=self.task_name)
                failed_storages.append(mnt.storage.name)
        if failed_storages:
            Logger.fail('The following data storages have not been mounted: {}'.format(', '.join(failed_storages)),
                        task_name=self.task_name)

    def _initialize_mounters(self, available_storages_with_mounts, storages_ids_by_path, mount_root, run):
        initialized_mounters = []
        for storage_and_mount in available_storages_with_mounts:
            if not PermissionHelper.is_storage_readable(storage_and_mount.storage):
                Logger.info('Storage is not readable', task_name=self.task_name)
                continue
            if PermissionHelper.is_storage_mount_disabled(storage_and_mount.storage):
                Logger.info('Storage disabled for mounting, skipping.', task_name=self.task_name)
                continue
            if not PermissionHelper.is_storage_available_for_mount(storage_and_mount.storage, run):
                storage_not_allowed_msg = 'Storage {} is not allowed for {} image' \
                    .format(storage_and_mount.storage.name, (run or {}).get("actualDockerImage", ""))
                if storage_and_mount.storage.id in self._get_forced_storages(storages_ids_by_path):
                    Logger.info(storage_not_allowed_msg + ', but it is forced to be mounted', task_name=self.task_name)
                else:
                    Logger.info(storage_not_allowed_msg + ', skipping it', task_name=self.task_name)
                    continue
            storages_metadata = self._collect_storages_metadata(available_storages_with_mounts)
            storage_metadata = storages_metadata.get(storage_and_mount.storage.id, {})
            additional_mount_options = dict(self._load_mount_options_from_environ())
            mounter = self.mounters[storage_and_mount.storage.storage_type](self.api, storage_and_mount.storage,
                                                                            storage_metadata,
                                                                            storage_and_mount.file_share_mount,
                                                                            self._get_sensitive_policy(),
                                                                            additional_mount_options.get(
                                                                                storage_and_mount.storage.id)) \
                if storage_and_mount.storage.storage_type in self.mounters else None
            if not mounter:
                Logger.warn('Unsupported storage type {}.'.format(storage_and_mount.storage.storage_type),
                            task_name=self.task_name)
            elif mounter.is_available():
                initialized_mounters.append(mounter)
        initialized_mounters.sort(key=lambda mnt: mnt.build_mount_point(mount_root))
        return initialized_mounters

    def _prepare_mounters(self, available_storages_with_mounts, tmp_dir):
        sensitive_policy = self._get_sensitive_policy()
        for mounter in [mounter for mounter in self.mounters.values()]:
            storage_count_by_type = len(
                list(filter((lambda dsm: dsm.storage.storage_type == mounter.type()), available_storages_with_mounts)))
            if storage_count_by_type > 0:
                mounter.check_or_install(self.task_name, sensitive_policy)
                mounter.init_tmp_dir(tmp_dir, self.task_name)

    def _get_sensitive_policy(self):
        sensitive_policy_preference = self.api.get_preference(SENSITIVE_POLICY_PREFERENCE)
        if sensitive_policy_preference and 'value' in sensitive_policy_preference:
            return sensitive_policy_preference['value']
        return None

    def _load_run_info(self):
        if self.run_id:
            return self.api.load_run(self.run_id)
        else:
            Logger.warn('Cannot load run info because run id is not configured.', task_name=self.task_name)
            return None

    def _load_and_filter_storages(self):
        Logger.info('Fetching list of allowed storages...', task_name=self.task_name)
        available_storages_with_mounts = self._fetch_available_storages()

        storages_ids_by_path = self._build_storage_path_map(available_storages_with_mounts)

        available_storages_with_mounts = self._apply_skip_mounts(available_storages_with_mounts, storages_ids_by_path)
        available_storages_with_mounts = self._apply_limit_mounts(available_storages_with_mounts, storages_ids_by_path)

        if not available_storages_with_mounts:
            Logger.success('No remote storages are available or CP_CAP_LIMIT_MOUNTS configured to none',
                           task_name=self.task_name)
            return None
        Logger.info(
            'Found {} available storage(s). Checking mount options.'.format(len(available_storages_with_mounts)),
            task_name=self.task_name)
        return available_storages_with_mounts, storages_ids_by_path

    @staticmethod
    def _build_storage_path_map(storages):
        return {x.storage.path: x.storage.id for x in storages}

    def _get_forced_storages(self, storages_ids_by_path):
        # If the storages are limited by the user - we make sure that the "forced" storages are still available
        # This is useful for the tools, which require "databases" or other data from the File/Object storages
        force_storages = os.getenv('CP_CAP_FORCE_MOUNTS')
        if force_storages:
            Logger.info('Storage(s) "{}" forced to be mounted even if the storage mounts list is limited'.format(
                force_storages), task_name=self.task_name)
            return self.parse_storage_list(force_storages, storages_ids_by_path)
        return []

    def _apply_skip_mounts(self, available_storages_with_mounts, storages_ids_by_path):
        # Filter out storages, which are requested to be skipped
        # NOTE: Any storage, defined by CP_CAP_FORCE_MOUNTS will still be mounted
        skip_storages = os.getenv('CP_CAP_SKIP_MOUNTS')
        if skip_storages:
            Logger.info('Storage(s) "{}" requested to be skipped'.format(skip_storages), task_name=self.task_name)
            skip_storages_list = self.parse_storage_list(skip_storages, storages_ids_by_path)
            available_storages_with_mounts = [x for x in available_storages_with_mounts if
                                              x.storage.id not in skip_storages_list]
        return available_storages_with_mounts

    def _apply_limit_mounts(self, available_storages, storages_ids_by_path):
        limited_storages = os.getenv('CP_CAP_LIMIT_MOUNTS')
        if not limited_storages:
            return available_storages
        try:
            limited_storages = self._combine_limited_and_forced_storages(limited_storages, storages_ids_by_path)
            limited_storages_list = self._parse_limited_storages(limited_storages, storages_ids_by_path)
            available_storages = [x for x in available_storages if x.storage.id in limited_storages_list]
            # append sensitive storages since they are not returned in common mounts
            loaded_mounts = {}
            for storage_id in limited_storages_list:
                storage = self.api.find_datastorage(str(storage_id))
                if storage.sensitive:
                    available_storages.append(
                        DataStorageWithShareMount(storage, self._get_storage_mount(storage, loaded_mounts)))
            Logger.info('Run is launched with mount limits ({}) Only {} storages will be mounted'.format(
                limited_storages, len(available_storages)), task_name=self.task_name)
            return available_storages
        except Exception as limited_storages_ex:
            Logger.warn('Unable to parse CP_CAP_LIMIT_MOUNTS value({}) with error: {}.'
                        .format(limited_storages, str(limited_storages_ex)), task_name=self.task_name)
            traceback.print_exc()
            return available_storages

    def _parse_limited_storages(self, limited_storages, storages_ids_by_path):
        if limited_storages.lower() == MOUNT_LIMITS_NONE:
            return []
        limited_storages_list = self.parse_storage_list(limited_storages, storages_ids_by_path)
        # Remove duplicates from the `limited_storages_list`, as they can be introduced by `force_storages`
        # or a user's typo
        return list(set(limited_storages_list))

    def _combine_limited_and_forced_storages(self, limited_storages, storages_ids_by_path):
        # Append "forced" storage to the "limited" list, if it's set
        force_storages = os.getenv('CP_CAP_FORCE_MOUNTS')
        if not force_storages:
            return limited_storages
        forced_storage_ids = self._get_forced_storages(storages_ids_by_path)
        force_storages = ','.join([str(x) for x in forced_storage_ids])
        return ','.join([limited_storages, force_storages])

    def _fetch_available_storages(self):
        available_storages_with_mounts = self.api.load_available_storages_with_share_mount(self.region_id or None)
        # filtering out shared storage folders, as they cause "overlapped" mounts
        # and break the "original" storage mountpoint,
        # storage is shared folder of another storage if it has source_storage_id
        available_storages_with_mounts = [
            x for x in available_storages_with_mounts if not x.storage.source_storage_id
        ]

        # filtering out all nfs storages if region id is missing
        if not self.region_id:
            available_storages_with_mounts = [x for x in available_storages_with_mounts if
                                              x.storage.storage_type != NFS_TYPE]

        # Backup a full filtered list, before removing storage to be skipped
        # We can further refer to this list, if any storage detail is required
        # Otherwise we need to call API once again
        self.available_storages = list(available_storages_with_mounts)
        return available_storages_with_mounts

    def _get_storage_mount(self, storage, loaded_mounts):
        if storage.file_share_mount_id is None:
            return None
        mount_id = int(storage.file_share_mount_id)
        if mount_id in loaded_mounts:
            return loaded_mounts[mount_id]
        file_share_mount = self.api.load_file_share_mount(mount_id)
        loaded_mounts[mount_id] = file_share_mount
        return file_share_mount

    @staticmethod
    def _load_mount_options_from_environ():
        result = {}
        for env_name, env_value in os.environ.items():
            if env_name.startswith(STORAGE_MOUNT_OPTIONS_ENV_PREFIX) and not env_name.endswith('_PARAM_TYPE'):
                storage_id = env_name[len(STORAGE_MOUNT_OPTIONS_ENV_PREFIX):]
                if storage_id.isdigit():
                    storage_id = int(storage_id)
                    if storage_id not in result:
                        result[storage_id] = MountOptions(
                            env_value, os.environ.get(STORAGE_MOUNT_PATH_ENV_PREFIX + str(storage_id), None))
            if env_name.startswith(STORAGE_MOUNT_PATH_ENV_PREFIX) and not env_name.endswith('_PARAM_TYPE'):
                storage_id = env_name[len(STORAGE_MOUNT_PATH_ENV_PREFIX):]
                if storage_id.isdigit():
                    storage_id = int(storage_id)
                    if storage_id not in result:
                        result[storage_id] = MountOptions(
                            os.environ.get(STORAGE_MOUNT_OPTIONS_ENV_PREFIX + str(storage_id), None), env_value)

        return result

    def _collect_storages_metadata(self, available_storages_with_mounts):
        storages_metadata_raw = self._load_storages_metadata_raw(available_storages_with_mounts)
        storages_metadata = dict(self._prepare_storages_metadata(storages_metadata_raw))
        return storages_metadata

    def _load_storages_metadata_raw(self, available_storages_with_mounts):
        try:
            storage_ids = [storage_and_mount.storage.id for storage_and_mount in available_storages_with_mounts]
            return self.api.load_all_metadata_efficiently(storage_ids, AclClass.DATA_STORAGE)
        except APIError as e:
            Logger.warn('Storages metadata loading has failed {}.'.format(str(e)), task_name=self.task_name)
            traceback.print_exc()
            return []

    def _prepare_storages_metadata(self, storages_metadata):
        for metadata_entry in storages_metadata:
            storage_id = metadata_entry.get('entity', {}).get('entityId', 0)
            storage_metadata_raw = metadata_entry.get('data', {})
            storage_metadata = {
                metadata_key: metadata_obj.get('value')
                for metadata_key, metadata_obj in storage_metadata_raw.items()
            }
            yield storage_id, storage_metadata


class StorageMounter:

    __metaclass__ = ABCMeta
    _cached_regions = []

    def __init__(self, api, storage, metadata, share_mount, sensitive_policy, mount_options=None):
        self.api = api
        self.storage = storage
        self.metadata = metadata
        self.share_mount = share_mount
        self.sensitive_policy = sensitive_policy
        self.mount_options = mount_options

    @staticmethod
    @abstractmethod
    def scheme():
        pass

    @staticmethod
    @abstractmethod
    def type():
        pass

    @staticmethod
    @abstractmethod
    def check_or_install(task_name, sensitive_policy):
        pass

    @staticmethod
    @abstractmethod
    def is_available():
        pass

    @staticmethod
    @abstractmethod
    def init_tmp_dir(tmp_dir, task_name):
        pass

    @staticmethod
    def execute_and_check_command(command, task_name=MOUNT_DATA_STORAGES):
        install_check, _, stderr = common.execute_cmd_command_and_get_stdout_stderr(command, silent=False)
        if install_check != 0:
            Logger.warn('Installation script {command} failed: \n {stderr}'.format(command=command, stderr=stderr),
                        task_name=task_name)
        return install_check == 0

    @staticmethod
    def create_directory(path, task_name):
        try:
            expanded_path = os.path.expandvars(path)
            if not os.path.exists(expanded_path):
                os.makedirs(expanded_path)
            return True
        except:
            Logger.warn('Failed to create mount directory: {path}'.format(path=path), task_name=task_name)
            traceback.print_exc()
            return False

    def mount(self, mount_root, task_name):
        mount_point = self.build_mount_point(mount_root)
        if not self.create_directory(mount_point, task_name):
            return
        params = self.build_mount_params(mount_point)
        mount_command = self.build_mount_command(params)
        self.execute_mount(mount_command, params, task_name)

    def build_mount_point(self, mount_root):
        mount_point = self.storage.mount_point
        if self.mount_options and self.mount_options.mount_path:
            return self.mount_options.mount_path
        if mount_point is None:
            mount_point = os.path.join(mount_root, self.get_path())
        return mount_point

    def get_permissions(self):
        return READ_WRITE_MOUNT_OPT if PermissionHelper.is_storage_writable(self.storage) else READ_ONLY_MOUNT_OPT

    @abstractmethod
    def build_mount_params(self, mount_point):
        pass

    @abstractmethod
    def build_mount_command(self, params):
        pass

    @staticmethod
    def execute_mount(command, params, task_name):
        if os.getenv('CP_CAP_MOUNT_SKIP_EXISTING', 'false').lower() == 'true' and not StorageMounter.is_windows():
            check_command = "/bin/mount | awk '{{ print $1 \" \" $3 }}' | grep '{path} {mount}'".format(**params)
            exit_code = common.execute_cmd_command(
                check_command, executable=None if StorageMounter.is_windows() else '/bin/bash'
            )
            if exit_code == 0:
                Logger.info('-->{path} already mounted to {mount}'.format(**params), task_name=task_name)
                return
        exit_code = common.execute_cmd_command(command, executable=None if StorageMounter.is_windows() else '/bin/bash')
        if exit_code == 0:
            Logger.info('-->{path} mounted to {mount}'.format(**params), task_name=task_name)
        else:
            Logger.warn('--> Failed mounting {path} to {mount}'.format(**params), task_name=task_name)
            raise RuntimeError('Failed mounting {path} to {mount}'.format(**params))

    def get_path(self):
        return self.storage.path.replace(self.scheme(), '', 1)

    def _get_credentials(self, storage):
        return self._get_credentials_by_region_id(storage.region_id)

    def _get_credentials_by_region_id(self, region_id):
        account_id = os.getenv('CP_ACCOUNT_ID_{}'.format(region_id))
        account_key = os.getenv('CP_ACCOUNT_KEY_{}'.format(region_id))
        account_region = os.getenv('CP_ACCOUNT_REGION_{}'.format(region_id))
        account_token = os.getenv('CP_ACCOUNT_TOKEN_{}'.format(region_id))
        if not account_id or not account_key or not account_region:
            raise RuntimeError('Account information wasn\'t found in the environment for account with id={}.'
                               .format(region_id))
        return account_id, account_key, account_region, account_token

    @staticmethod
    def is_windows():
        return platform.system() == 'Windows'


class AzureMounter(StorageMounter):
    available = False
    fuse_tmp = '/tmp'

    @staticmethod
    def scheme():
        return AZ_SCHEME

    @staticmethod
    def type():
        return AZ_TYPE

    @staticmethod
    def check_or_install(task_name, sensitive_policy):
        if not AzureMounter.is_available():
            AzureMounter.available = StorageMounter.execute_and_check_command('install_azure_fuse_blobfuse',
                                                                              task_name=task_name)

    @staticmethod
    def is_available():
        return AzureMounter.available

    @staticmethod
    def init_tmp_dir(tmp_dir, task_name):
        fuse_tmp = os.path.join(tmp_dir, "blobfuse")
        if StorageMounter.create_directory(fuse_tmp, task_name):
            AzureMounter.fuse_tmp = fuse_tmp

    def mount(self, mount_root, task_name):
        self.__resolve_azure_blob_service_url(task_name=task_name)
        super(AzureMounter, self).mount(mount_root, task_name)

    def build_mount_params(self, mount_point):
        account_id, account_key, _, _ = self._get_credentials(self.storage)
        mount_options = ''
        if self.mount_options and self.mount_options.mount_params:
            mount_options = self.mount_options.mount_params
        elif self.storage.mount_options:
            mount_options = self.storage.mount_options
        return {
            'mount': mount_point,
            'path': self.get_path(),
            'tmp_dir': os.path.join(self.fuse_tmp, str(self.storage.id)),
            'account_name': account_id,
            'account_key': account_key,
            'permissions': self.get_permissions(),
            'mount_options': mount_options
        }

    def build_mount_command(self, params):
        return 'AZURE_STORAGE_ACCOUNT="{account_name}" ' \
               'AZURE_STORAGE_ACCESS_KEY="{account_key}" ' \
               'blobfuse "{mount}" ' \
               '--container-name="{path}" ' \
               '--tmp-path="{tmp_dir}" ' \
               '-o "{permissions}" ' \
               '-o allow_other ' \
               '{mount_options}'.format(**params)

    def __resolve_azure_blob_service_url(self, task_name=MOUNT_DATA_STORAGES):
        # add resolved ip address for azure blob service to /etc/hosts (only once per account_name)
        account_name, _, _, _ = self._get_credentials(self.storage)
        command = 'etc_hosts_clear="$(sed -E \'/.*{account_name}.blob.core.windows.net.*/d\' /etc/hosts)" ' \
                  '&& cat > /etc/hosts <<< "$etc_hosts_clear" ' \
                  '&& getent hosts {account_name}.blob.core.windows.net >> /etc/hosts'.format(account_name=account_name)
        exit_code, _, stderr = common.execute_cmd_command_and_get_stdout_stderr(command, silent=True)
        if exit_code != 0:
            Logger.warn('Azure BLOB service hostname resolution and writing to /etc/hosts failed: \n {}'.format(stderr),
                        task_name=task_name)


class S3Mounter(StorageMounter):
    fuse_type = FUSE_NA_ID
    fuse_tmp = '/tmp'

    @staticmethod
    def scheme():
        return S3_SCHEME

    @staticmethod
    def type():
        return S3_TYPE

    @staticmethod
    def check_or_install(task_name, sensitive_policy):
        if not S3Mounter.fuse_type:
            S3Mounter.fuse_type = S3Mounter._check_or_install(task_name)

    @staticmethod
    def _check_or_install(task_name):
        fuse_type = os.getenv('CP_S3_FUSE_TYPE', FUSE_GOOFYS_ID)
        if fuse_type == FUSE_GOOFYS_ID:
            fuse_installed = StorageMounter.execute_and_check_command('install_s3_fuse_goofys', task_name=task_name)
            return FUSE_GOOFYS_ID if fuse_installed else FUSE_NA_ID
        elif fuse_type == FUSE_S3FS_ID:
            fuse_installed = StorageMounter.execute_and_check_command('install_s3_fuse_s3fs', task_name=task_name)
            if fuse_installed:
                return FUSE_S3FS_ID
            else:
                Logger.warn(
                    "FUSE {fuse_type} was preferred, but failed to install, will try to setup default goofys".format(
                        fuse_type=fuse_type),
                    task_name=task_name)
                fuse_installed = StorageMounter.execute_and_check_command('install_s3_fuse_goofys', task_name=task_name)
                return FUSE_GOOFYS_ID if fuse_installed else FUSE_NA_ID
        elif fuse_type == FUSE_PIPE_ID:
            return FUSE_PIPE_ID
        else:
            Logger.warn("FUSE {fuse_type} type is not defined for S3 fuse".format(fuse_type=fuse_type),
                        task_name=task_name)
            return FUSE_NA_ID

    @staticmethod
    def is_available():
        return S3Mounter.fuse_type is not None

    @staticmethod
    def init_tmp_dir(tmp_dir, task_name):
        fuse_tmp = os.path.join(tmp_dir, "s3fuse")
        if StorageMounter.create_directory(fuse_tmp, task_name):
            S3Mounter.fuse_tmp = fuse_tmp

    def build_mount_params(self, mount_point):
        stat_cache = os.getenv('CP_S3_FUSE_STAT_CACHE', '1m0s')
        type_cache = os.getenv('CP_S3_FUSE_TYPE_CACHE', '1m0s')
        mount_timeout = os.getenv('CP_PIPE_FUSE_MOUNT_TIMEOUT', 10000)
        aws_key_id, aws_secret, region_name, session_token = self._get_credentials(self.storage)
        path_chunks = self.storage.path.split('/')
        bucket = path_chunks[0]
        relative_path = '/'.join(path_chunks[1:]) if len(path_chunks) > 1 else ''
        if self.is_windows():
            logging_file = os.path.join(os.getenv('LOG_DIR', 'c:\\logs'), 'fuse_{}.log'.format(self.storage.id))
        else:
            logging_file = '/var/log/fuse_{}.log'.format(self.storage.id)
        return {'mount': mount_point,
                'storage_id': str(self.storage.id),
                'path': self.get_path(),
                'mask': '0774' if PermissionHelper.is_storage_writable(self.storage) else '0554',
                'permissions': self.get_permissions(),
                'region_name': region_name,
                'stat_cache': stat_cache,
                'type_cache': type_cache,
                'fuse_type': self.fuse_type,
                'aws_key_id': aws_key_id,
                'aws_secret': aws_secret,
                'aws_token': session_token,
                'tmp_dir': self.fuse_tmp,
                'bucket': bucket,
                'relative_path': relative_path,
                'mount_timeout': mount_timeout,
                'logging_file': logging_file
                }

    def remove_prefix(self, text, prefix):
        if text.startswith(prefix):
            return text[len(prefix):]
        return text

    def build_mount_command(self, params):
        if params['aws_token'] is not None or params['fuse_type'] == FUSE_PIPE_ID:
            pipe_mount_options = os.getenv('CP_PIPE_FUSE_MOUNT_OPTIONS')
            mount_options = self.mount_options.mount_params if self.mount_options and self.mount_options.mount_params \
                else os.getenv('CP_PIPE_FUSE_OPTIONS')
            persist_logs = os.getenv('CP_PIPE_FUSE_PERSIST_LOGS', 'false').lower() == 'true'
            debug_libfuse = os.getenv('CP_PIPE_FUSE_DEBUG_LIBFUSE', 'false').lower() == 'true'
            logging_level = os.getenv('CP_PIPE_FUSE_LOGGING_LEVEL')
            merged_options = '-o allow_other'
            if debug_libfuse:
                merged_options = merged_options + ',debug'
            if mount_options:
                if mount_options.startswith('-c'):
                    merged_options = merged_options + ' ' + mount_options
                else:
                    merged_options = merged_options + ',' + self.remove_prefix(mount_options.strip(), '-o').strip()
            if logging_level:
                params['logging_level'] = logging_level
            return ('pipe storage mount {mount} -b {path} -t --mode 775 -w {mount_timeout} '
                    + ('-l {logging_file} ' if persist_logs else '')
                    + ('-v {logging_level} ' if logging_level else '')
                    + merged_options + ' '
                    + (pipe_mount_options if pipe_mount_options else '')).format(**params)
        elif params['fuse_type'] == FUSE_GOOFYS_ID:
            params['path'] = '{bucket}:{relative_path}'.format(**params) if params['relative_path'] else params['path']
            return 'AWS_ACCESS_KEY_ID={aws_key_id} AWS_SECRET_ACCESS_KEY={aws_secret} nohup goofys ' \
                   '--dir-mode {mask} --file-mode {mask} -o {permissions} -o allow_other ' \
                    '--stat-cache-ttl {stat_cache} --type-cache-ttl {type_cache} ' \
                    '--acl "bucket-owner-full-control" ' \
                    '-f --gid 0 --region "{region_name}" {path} {mount} > {logging_file} 2>&1 &'.format(**params)
        elif params['fuse_type'] == FUSE_S3FS_ID:
            params['path'] = '{bucket}:/{relative_path}'.format(**params) if params['relative_path'] else params['path']
            ensure_diskfree_size = os.getenv('CP_S3_FUSE_ENSURE_DISKFREE')
            params["ensure_diskfree_option"] = "" if ensure_diskfree_size is None else "-o ensure_diskfree=" + ensure_diskfree_size
            return 'AWSACCESSKEYID={aws_key_id} AWSSECRETACCESSKEY={aws_secret} s3fs {path} {mount} -o use_cache={tmp_dir} ' \
                    '-o umask=0000 -o {permissions} -o allow_other -o enable_noobj_cache ' \
                    '-o endpoint="{region_name}" -o url="https://s3.{region_name}.amazonaws.com" {ensure_diskfree_option} ' \
                    '-o default_acl="bucket-owner-full-control" ' \
                    '-o dbglevel="info" -f > {logging_file} 2>&1 &'.format(**params)
        else:
            return 'exit 1'


class GCPMounter(StorageMounter):
    credentials = None
    fuse_type = FUSE_NA_ID
    fuse_tmp = '/tmp'

    @staticmethod
    def scheme():
        return GCP_SCHEME

    @staticmethod
    def type():
        return GCP_TYPE

    @staticmethod
    def check_or_install(task_name, sensitive_policy):
        if not GCPMounter.fuse_type:
            GCPMounter.fuse_type = GCPMounter._check_or_install(task_name)

    @staticmethod
    def _check_or_install(task_name):
        fuse_type = os.getenv('CP_GCS_FUSE_TYPE', FUSE_GCSFUSE_ID)
        if fuse_type == FUSE_GCSFUSE_ID:
            fuse_installed = StorageMounter.execute_and_check_command('install_gcsfuse', task_name=task_name)
            return FUSE_GCSFUSE_ID if fuse_installed else FUSE_NA_ID
        elif fuse_type == FUSE_PIPE_ID:
            return FUSE_PIPE_ID
        else:
            Logger.warn("FUSE {fuse_type} type is not defined for GSC fuse".format(fuse_type=fuse_type),
                        task_name=task_name)
            return FUSE_NA_ID

    @staticmethod
    def is_available():
        return GCPMounter.fuse_type is not None

    @staticmethod
    def init_tmp_dir(tmp_dir, task_name):
        fuse_tmp = os.path.join(tmp_dir, "gcsfuse")
        if StorageMounter.create_directory(fuse_tmp, task_name):
            GCPMounter.fuse_tmp = fuse_tmp

    def mount(self, mount_root, task_name):
        super(GCPMounter, self).mount(mount_root, task_name)

    def build_mount_params(self, mount_point):
        mount_timeout = os.getenv('CP_PIPE_FUSE_MOUNT_TIMEOUT', 10000)
        gcp_creds_content, _ = self._get_credentials(self.storage)
        if gcp_creds_content:
            creds_named_pipe_path = "<(echo \'{gcp_creds_content}\')".format(gcp_creds_content=gcp_creds_content)
        else:
            creds_named_pipe_path = None
        if self.is_windows():
            logging_file = os.path.join(os.getenv('LOG_DIR', 'c:\\logs'), 'fuse_{}.log'.format(self.storage.id))
        else:
            logging_file = '/var/log/fuse_{}.log'.format(self.storage.id)
        return {'mount': mount_point,
                'storage_id': str(self.storage.id),
                'path': self.get_path(),
                'mask': '0774' if PermissionHelper.is_storage_writable(self.storage) else '0554',
                'permissions': self.get_permissions(),
                'fuse_type': self.fuse_type,
                'tmp_dir': self.fuse_tmp,
                'credentials': creds_named_pipe_path,
                'mount_timeout': mount_timeout,
                'logging_file': logging_file
                }

    def build_mount_command(self, params):
        if not params['credentials'] or params['fuse_type'] == FUSE_PIPE_ID:
            persist_logs = os.getenv('CP_PIPE_FUSE_PERSIST_LOGS', 'false').lower() == 'true'
            debug_libfuse = os.getenv('CP_PIPE_FUSE_DEBUG_LIBFUSE', 'false').lower() == 'true'
            logging_level = os.getenv('CP_PIPE_FUSE_LOGGING_LEVEL')
            if logging_level:
                params['logging_level'] = logging_level
            return ('pipe storage mount {mount} -b {path} -t --mode 775 -w {mount_timeout} '
                    + ('-l {logging_file} ' if persist_logs else '')
                    + ('-v {logging_level} ' if logging_level else '')
                    + ('-o allow_other,debug ' if debug_libfuse else '-o allow_other ')
                    ).format(**params)
        else:
            return 'nohup gcsfuse --foreground -o {permissions} -o allow_other --key-file {credentials} --temp-dir {tmp_dir} ' \
                   '--dir-mode {mask} --file-mode {mask} --implicit-dirs {path} {mount} > {logging_file} 2>&1 &'.format(**params)

    def _get_credentials(self, storage):
        account_region = os.getenv('CP_ACCOUNT_REGION_{}'.format(storage.region_id))
        account_cred_file_content = os.getenv('CP_CREDENTIALS_FILE_CONTENT_{}'.format(storage.region_id))
        if not account_region:
            raise RuntimeError('Account information wasn\'t found in the environment for account with id={}.'
                               .format(storage.region_id))
        return account_cred_file_content, account_region


class NFSMounter(StorageMounter):
    available = False

    @staticmethod
    def scheme():
        return NFS_SCHEME

    @staticmethod
    def type():
        return NFS_TYPE

    @staticmethod
    def check_or_install(task_name, sensitive_policy):
        if NFSMounter.is_available():
            return
        NFSMounter.available = False if PermissionHelper.is_run_sensitive() and sensitive_policy == "SKIP" \
            else StorageMounter.execute_and_check_command('install_nfs_client', task_name=task_name)

    @staticmethod
    def init_tmp_dir(tmp_dir, task_name):
        pass

    @staticmethod
    def is_available():
        return NFSMounter.available

    @staticmethod
    def is_permission_set(storage, mask):
        return storage.mask & mask == mask

    # def get_permissions(self):
    #     if not PermissionHelper.is_storage_writable(self.storage):
    #         return READ_ONLY_MOUNT_OPT
    #     chmod_permission = self.get_metadata_chmod()
    #     readonly_patterns = {
    #         'r', 'ro', 'r--', 'r-x',
    #         '400', '440', '444', '555',
    #         'a=r', 'ugo=r', 'u=r', 'go=r', 'g=r', 'o=r'
    #     }
    #     if chmod_permission.lower() in readonly_patterns:
    #         return READ_ONLY_MOUNT_OPT
    #     if any(x in chmod_permission.lower() for x in ['w', 'rw', '7', '6']):
    #         return READ_WRITE_MOUNT_OPT
    #     return READ_WRITE_MOUNT_OPT

    def get_metadata_chmod(self):
        print("self.metadata.get('chmod'): " + str(self.metadata.get('chmod', 'g+rwx')))
        return str(self.metadata.get('chmod', 'g+rwx'))

    def build_mount_point(self, mount_root):
        if self.mount_options and self.mount_options.mount_path:
            return self.mount_options.mount_path
        mount_point = self.storage.mount_point
        if mount_point is None:
            # NFS path will look like srv:/some/path. Remove the first ':' from it
            mount_point = os.path.join(mount_root, self.get_path().replace(':', '', 1))
        return mount_point

    def build_mount_params(self, mount_point):
        return {'mount': mount_point,
                'path': self.get_path()}

    def build_mount_command(self, params):
        command = '/bin/mount -t {protocol}'

        if self.mount_options and self.mount_options.mount_params:
            mount_options = self.mount_options.mount_params
        else:
            mount_options = self.storage.mount_options if self.storage.mount_options else self.share_mount.mount_options

        region_id = str(self.share_mount.region_id) if self.share_mount.region_id is not None else ""
        if os.getenv("CP_CLOUD_PROVIDER_" + region_id) == "AZURE" and self.share_mount.mount_type == "SMB":
            az_acc_id, az_acc_key, _, _ = self._get_credentials_by_region_id(region_id)
            creds_options = ",".join(["username=" + az_acc_id, "password=" + az_acc_key])

            if mount_options:
                mount_options += "," + creds_options
            else:
                mount_options = creds_options

        if self.share_mount.mount_type == "SMB":
            command = command.format(protocol="cifs")
            if not params['path'].startswith("//"):
                params['path'] = '//' + params['path']
        elif self.share_mount.mount_type == "LUSTRE":
            command = command.format(protocol="lustre")
        else:
            command = command.format(protocol="nfs")

        permission = self.get_metadata_chmod()

        mask = '0774'
        if not PermissionHelper.is_storage_writable(self.storage):
            mask = '0554'
            if not mount_options:
                mount_options = READ_ONLY_MOUNT_OPT
            else:
                options = mount_options.split(',')
                if READ_ONLY_MOUNT_OPT not in options:
                    mount_options += ',{0}'.format(READ_ONLY_MOUNT_OPT)
        if self.share_mount.mount_type == "SMB":
            file_mode_options = 'file_mode={mode},dir_mode={mode}'.format(mode=mask)
            if not mount_options:
                mount_options = file_mode_options
            else:
                mount_options += ',' + file_mode_options
        mount_options = self.append_timeout_options(mount_options)
        if mount_options:
            command += ' -o {}'.format(mount_options)
        command += ' {path} {mount}'.format(**params)
        if PermissionHelper.is_storage_writable(self.storage) and not self.storage.sensitive:
            command += ' && ( chmod {permission} {mount} || true )'.format(permission=permission, **params)
        return command

    def append_timeout_options(self, mount_options):
        if self.share_mount.mount_type == 'SMB' or not PermissionHelper.is_run_sensitive() \
                or self.sensitive_policy != "TIMEOUT":
            return mount_options
        if not mount_options or 'retry' not in mount_options:
            mount_retry = os.getenv('CP_FS_MOUNT_ATTEMPT', 0)
            retry_option = 'retry={}'.format(mount_retry)
            mount_options = retry_option if not mount_options else mount_options + ',' + retry_option
        if self.share_mount.mount_type == 'LUSTRE':
            return mount_options
        if not mount_options or 'timeo' not in mount_options:
            mount_timeo = os.getenv('CP_FS_MOUNT_TIMEOUT', 7)
            timeo_option = 'timeo={}'.format(mount_timeo)
            mount_options = timeo_option if not mount_options else mount_options + ',' + timeo_option
        return mount_options

    def get_mount_status(self, available_storages_dict, details, is_admin, default):
        if is_admin is None:
            return MOUNT_STATUS_UNKNOWN
        elif is_admin:
            return MOUNT_STATUS_ACTIVE
        if available_storages_dict is None:
            return default
        matching_storage = self._find_matching_storage(available_storages_dict, details)
        if matching_storage:
            status = matching_storage.mount_status
            if status == MOUNT_STATUS_DISABLED \
                    or (status == MOUNT_STATUS_ACTIVE
                        and not NFSMounter.is_permission_set(matching_storage, WRITE_MASK)):
                return MOUNT_STATUS_READ_ONLY
            else:
                return status
        else:
            return DEFAULT_MOUNT_STATUS

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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--mount-root', required=True)
    parser.add_argument('--tmp-dir', required=True)
    parser.add_argument('--task', required=False, default=MOUNT_DATA_STORAGES)
    args = parser.parse_args()
    if EXEC_ENVIRONMENT in os.environ and os.environ[EXEC_ENVIRONMENT] == DTS:
        Logger.success('Skipping cloud storage mount for execution environment %s' % DTS, task_name=args.task)
        return
    MountStorageTask(args.task).run(args.mount_root, args.tmp_dir)


if __name__ == '__main__':
    main()
