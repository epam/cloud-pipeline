# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import argparse
import os
from abc import ABCMeta, abstractmethod

from pipeline import PipelineAPI, Logger, common

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
FUSE_NA_ID = None
AZURE_PROVIDER = 'AZURE'
S3_PROVIDER = 'S3'

class PermissionHelper:

    def __init__(self):
        pass

    @classmethod
    def is_storage_readable(cls, storage):
        return cls.is_permission_set(storage, READ_MASK)

    @classmethod
    def is_storage_writable(cls, storage):
        return cls.is_permission_set(storage, WRITE_MASK)

    @classmethod
    def is_permission_set(cls, storage, mask):
        return storage.mask & mask == mask

    @classmethod
    def is_run_sensitive(cls):
        sensitive_run = os.getenv('CP_SENSITIVE_RUN', "false")
        return sensitive_run.lower() == 'true'


class MountStorageTask:

    def __init__(self, task):
        self.api = PipelineAPI(os.environ['API'], 'logs')
        self.task_name = task
        available_mounters = [NFSMounter, S3Mounter, AzureMounter, GCPMounter]
        self.mounters = {mounter.type(): mounter for mounter in available_mounters}

    def run(self, mount_root, tmp_dir):
        try:
            Logger.info('Starting mounting remote data storages.', task_name=self.task_name)

            # use -1 as default in order to don't mount any NFS if CLOUD_REGION_ID is not provided
            cloud_region_id = int(os.getenv('CLOUD_REGION_ID', -1))
            if cloud_region_id == -1:
                Logger.warn('CLOUD_REGION_ID env variable is not provided, no NFS will be mounted, \
                 and no storage will be filtered by mount storage rule of a region', task_name=self.task_name)

            Logger.info('Fetching list of allowed storages...', task_name=self.task_name)
            available_storages_with_mounts = self.api.load_available_storages_with_share_mount(cloud_region_id if cloud_region_id != -1 else None)
            # filtering nfs storages in order to fetch only nfs from the same region
            available_storages_with_mounts = [x for x in available_storages_with_mounts if x.storage.storage_type != NFS_TYPE
                                              or x.file_share_mount.region_id == cloud_region_id]
            if not available_storages_with_mounts:
                Logger.success('No remote storages are available', task_name=self.task_name)
                return
            Logger.info('Found {} available storage(s). Checking mount options.'.format(len(available_storages_with_mounts)), task_name=self.task_name)

            limited_storages = os.getenv('CP_CAP_LIMIT_MOUNTS')
            if limited_storages:
                try:
                    limited_storages_list = [int(x.strip()) for x in limited_storages.split(',')]
                    available_storages_with_mounts = [x for x in available_storages_with_mounts if x.storage.id in limited_storages_list]
                    Logger.info('Run is launched with mount limits ({}) Only {} storages will be mounted'.format(limited_storages, len(available_storages_with_mounts)), task_name=self.task_name)
                except Exception as limited_storages_ex:
                    Logger.warn('Unable to parse CP_CAP_LIMIT_MOUNTS value({}) with error: {}.'.format(limited_storages, str(limited_storages_ex.message)), task_name=self.task_name)

            for mounter in [mounter for mounter in self.mounters.values()]:
                storage_count_by_type = len(filter((lambda dsm: dsm.storage.storage_type == mounter.type()), available_storages_with_mounts))
                if storage_count_by_type > 0:
                    mounter.check_or_install(self.task_name)
                    mounter.init_tmp_dir(tmp_dir, self.task_name)

            if all([not mounter.is_available() for mounter in self.mounters.values()]):
                Logger.success('Mounting of remote storages is not available for this image', task_name=self.task_name)
                return
            for storage_and_mount in available_storages_with_mounts:
                if not PermissionHelper.is_storage_readable(storage_and_mount.storage):
                    continue
                mounter = self.mounters[storage_and_mount.storage.storage_type](self.api, storage_and_mount.storage, storage_and_mount.file_share_mount) \
                    if storage_and_mount.storage.storage_type in self.mounters else None
                if not mounter:
                    Logger.warn('Unsupported storage type {}.'.format(storage_and_mount.storage.storage_type), task_name=self.task_name)
                elif mounter.is_available():
                    try:
                        mounter.mount(mount_root, self.task_name)
                    except RuntimeError as e:
                        Logger.warn('Data storage {} mounting has failed: {}'.format(storage_and_mount.storage.name, e.message),
                                    task_name=self.task_name)
            Logger.success('Finished data storage mounting', task_name=self.task_name)
        except Exception as e:
            Logger.fail('Unhandled error during mount task: {}.'.format(str(e.message)), task_name=self.task_name)


class StorageMounter:

    __metaclass__ = ABCMeta
    _cached_regions = []

    def __init__(self, api, storage, share_mount):
        self.api = api
        self.storage = storage
        self.share_mount = share_mount

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
    def check_or_install(task_name):
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
            Logger.warn('Installation script {command} failed: \n {stderr}'.format(command=command, stderr=stderr), task_name=task_name)
        return install_check == 0

    @staticmethod
    def create_directory(path, task_name):
        result = common.execute_cmd_command('mkdir -p {path}'.format(path=path), silent=True)
        if result != 0:
            Logger.warn('Failed to create mount directory: {path}'.format(path=path), task_name=task_name)
            return False
        return True

    def mount(self, mount_root, task_name):
        mount_point = self.build_mount_point(mount_root)
        if not self.create_directory(mount_point, task_name):
            return
        params = self.build_mount_params(mount_point)
        mount_command = self.build_mount_command(params)
        self.execute_mount(mount_command, params, task_name)

    def build_mount_point(self, mount_root):
        mount_point = self.storage.mount_point
        if mount_point is None:
            mount_point = os.path.join(mount_root, self.get_path())
        return mount_point

    @abstractmethod
    def build_mount_params(self, mount_point):
        pass

    @abstractmethod
    def build_mount_command(self, params):
        pass

    @staticmethod
    def execute_mount(command, params, task_name):
        result = common.execute_cmd_command(command, silent=True, executable="/bin/bash")
        if result == 0:
            Logger.info('-->{path} mounted to {mount}'.format(**params), task_name=task_name)
        else:
            Logger.warn('--> Failed mounting {path} to {mount}'.format(**params), task_name=task_name)

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
    def check_or_install(task_name):
        AzureMounter.available = StorageMounter.execute_and_check_command('install_azure_fuse_blobfuse', task_name=task_name)

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
        return {
            'mount': mount_point,
            'path': self.get_path(),
            'tmp_dir': os.path.join(self.fuse_tmp, str(self.storage.id)),
            'account_name': account_id,
            'account_key': account_key,
            'permissions': 'rw' if PermissionHelper.is_storage_writable(self.storage) and not PermissionHelper.is_run_sensitive() else 'ro',
            'mount_options': self.storage.mount_options if self.storage.mount_options else ''
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
            Logger.warn('Azure BLOB service hostname resolution and writing to /etc/hosts failed: \n {}'.format(stderr), task_name=task_name)


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
    def check_or_install(task_name):
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
        mask = '0774'
        permissions = 'rw'
        stat_cache = os.getenv('CP_S3_FUSE_STAT_CACHE', '1m0s')
        type_cache = os.getenv('CP_S3_FUSE_TYPE_CACHE', '1m0s')
        aws_key_id, aws_secret, region_name, session_token = self._get_credentials(self.storage)
        path_chunks = self.storage.path.split('/')
        bucket = path_chunks[0]
        relative_path = '/'.join(path_chunks[1:]) if len(path_chunks) > 1 else ''
        if not PermissionHelper.is_storage_writable(self.storage) or PermissionHelper.is_run_sensitive():
            mask = '0554'
            permissions = 'ro'
        return {'mount': mount_point,
                'storage_id': str(self.storage.id),
                'path': self.get_path(),
                'mask': mask,
                'permissions': permissions,
                'region_name': region_name,
                'stat_cache': stat_cache,
                'type_cache': type_cache,
                'fuse_type': self.fuse_type,
                'aws_key_id': aws_key_id,
                'aws_secret': aws_secret,
                'aws_token': session_token,
                'tmp_dir': self.fuse_tmp,
                'bucket': bucket,
                'relative_path': relative_path
                }

    def build_mount_command(self, params):
        if params['aws_token'] is not None or params['fuse_type'] == FUSE_PIPE_ID:
            return 'pipe storage mount {mount} -b {path} -t --mode 775'.format(**params)
        elif params['fuse_type'] == FUSE_GOOFYS_ID:
            params['path'] = '{bucket}:{relative_path}'.format(**params) if params['relative_path'] else params['path']
            return 'AWS_ACCESS_KEY_ID={aws_key_id} AWS_SECRET_ACCESS_KEY={aws_secret} nohup goofys ' \
                   '--dir-mode {mask} --file-mode {mask} -o {permissions} -o allow_other ' \
                    '--stat-cache-ttl {stat_cache} --type-cache-ttl {type_cache} ' \
                    '-f --gid 0 --region "{region_name}" {path} {mount} > /var/log/fuse_{storage_id}.log 2>&1 &'.format(**params)
        elif params['fuse_type'] == FUSE_S3FS_ID:
            params['path'] = '{bucket}:/{relative_path}'.format(**params) if params['relative_path'] else params['path']
            ensure_diskfree_size = os.getenv('CP_S3_FUSE_ENSURE_DISKFREE')
            params["ensure_diskfree_option"] = "" if ensure_diskfree_size is None else "-o ensure_diskfree=" + ensure_diskfree_size
            return 'AWSACCESSKEYID={aws_key_id} AWSSECRETACCESSKEY={aws_secret} s3fs {path} {mount} -o use_cache={tmp_dir} ' \
                    '-o umask=0000 -o {permissions} -o allow_other -o enable_noobj_cache ' \
                    '-o endpoint="{region_name}" -o url="https://s3.{region_name}.amazonaws.com" {ensure_diskfree_option} ' \
                    '-o dbglevel="info" -f > /var/log/fuse_{storage_id}.log 2>&1 &'.format(**params)
        else:
            return 'exit 1'


class GCPMounter(StorageMounter):
    available = False
    credentials = None
    fuse_tmp = '/tmp'

    @staticmethod
    def scheme():
        return GCP_SCHEME

    @staticmethod
    def type():
        return GCP_TYPE

    @staticmethod
    def check_or_install(task_name):
        AzureMounter.available = StorageMounter.execute_and_check_command('install_gcsfuse')
        GCPMounter.available = True

    @staticmethod
    def is_available():
        return GCPMounter.available

    @staticmethod
    def init_tmp_dir(tmp_dir, task_name):
        fuse_tmp = os.path.join(tmp_dir, "gcsfuse")
        if StorageMounter.create_directory(fuse_tmp, task_name):
            GCPMounter.fuse_tmp = fuse_tmp

    def mount(self, mount_root, task_name):
        super(GCPMounter, self).mount(mount_root, task_name)

    def build_mount_params(self, mount_point):
        gcp_creds_content, _ = self._get_credentials(self.storage)
        if not gcp_creds_content:
            print("GCP credentials is not available, GCP file storage won't be mounted")
            return {}

        creds_named_pipe_path = "<(echo \'{gcp_creds_content}\')".format(gcp_creds_content=gcp_creds_content)
        mask = '0774'
        permissions = 'rw'
        if not PermissionHelper.is_storage_writable(self.storage) or PermissionHelper.is_run_sensitive():
            mask = '0554'
            permissions = 'ro'
        return {'mount': mount_point,
                'storage_id': str(self.storage.id),
                'path': self.get_path(),
                'mask': mask,
                'permissions': permissions,
                'tmp_dir': self.fuse_tmp,
                'credentials': creds_named_pipe_path
                }

    def build_mount_command(self, params):
        if not params:
            return ""
        return 'nohup gcsfuse --foreground -o {permissions} --key-file {credentials} --temp-dir {tmp_dir} ' \
               '--dir-mode {mask} --file-mode {mask} --implicit-dirs {path} {mount} > /var/log/fuse_{storage_id}.log 2>&1 &'.format(**params)

    def _get_credentials(self, storage):
        account_region = os.getenv('CP_ACCOUNT_REGION_{}'.format(storage.region_id))
        account_cred_file_content = os.getenv('CP_CREDENTIALS_FILE_CONTENT_{}'.format(storage.region_id))
        if not account_cred_file_content or not account_region:
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
    def check_or_install(task_name):
        NFSMounter.available = StorageMounter.execute_and_check_command('install_nfs_client', task_name=task_name)

    @staticmethod
    def init_tmp_dir(tmp_dir, task_name):
        pass

    @staticmethod
    def is_available():
        return NFSMounter.available

    def build_mount_point(self, mount_root):
        mount_point = self.storage.mount_point
        if mount_point is None:
            # NFS path will look like srv:/some/path. Remove the first ':' from it
            mount_point = os.path.join(mount_root, self.get_path().replace(':', '', 1))
        return mount_point

    def build_mount_params(self, mount_point):
        return {'mount': mount_point,
                'path': self.get_path()}

    def build_mount_command(self, params):
        command = 'mount -t {protocol}'

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
        else:
            command = command.format(protocol="nfs")

        permission = 'g+rwx'
        if not PermissionHelper.is_storage_writable(self.storage) or PermissionHelper.is_run_sensitive():
            permission = 'g+rx'
            if not mount_options:
                mount_options = 'ro'
            else:
                options = mount_options.split(',')
                if 'ro' not in options:
                    mount_options += ',ro'

        if mount_options:
            command += ' -o {}'.format(mount_options)
        command += ' {path} {mount}'.format(**params)
        if PermissionHelper.is_storage_writable(self.storage):
            command += ' && chmod {permission} {mount}'.format(permission=permission, **params)
        return command


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
