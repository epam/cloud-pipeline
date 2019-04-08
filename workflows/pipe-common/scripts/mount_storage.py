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
MOUNT_DATA_STORAGES = 'MountDataStorages'
S3_SCHEME = 's3://'
AZ_SCHEME = 'az://'
NFS_SCHEME = 'nfs://'
FUSE_GOOFYS_ID = 'goofys'
FUSE_S3FS_ID = 's3fs'
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


class MountStorageTask:

    def __init__(self, task):
        self.api = PipelineAPI(os.environ['API'], 'logs')
        self.task_name = task
        available_mounters = [NFSMounter, S3Mounter, AzureMounter]
        self.mounters = {mounter.type(): mounter for mounter in available_mounters}
        self.default_regions = {'AWS': 'us-east-1', 'AZURE': 'eastus'}

    def run(self, mount_root, tmp_dir):
        try:
            Logger.info('Starting mounting remote data storages.', task_name=self.task_name)

            Logger.info('Fetching list of allowed storages...', task_name=self.task_name)
            available_storages_with_mounts = self.api.load_available_storages_with_share_mount()
            if not available_storages_with_mounts:
                Logger.success('No remote storages are available', task_name=self.task_name)
                return
            Logger.info('Found {} available storage(s). Checking mount options.'.format(len(available_storages_with_mounts)), task_name=self.task_name)

            for mounter in [mounter for mounter in self.mounters.values() if mounter != NFSMounter]:
                mounter.check_or_install(self.task_name)
                mounter.init_tmp_dir(tmp_dir, self.task_name)

            limited_storages = os.getenv('CP_CAP_LIMIT_MOUNTS')
            if limited_storages:
                try:
                    limited_storages_list = [int(x.strip()) for x in limited_storages.split(',')]
                    available_storages_with_mounts = [x for x in available_storages_with_mounts if x.storage.id in limited_storages_list]
                    Logger.info('Run is launched with mount limits ({}) Only {} storages will be mounted'.format(limited_storages, len(available_storages_with_mounts)), task_name=self.task_name)
                except Exception as limited_storages_ex:
                    Logger.warn('Unable to parse CP_CAP_LIMIT_MOUNTS value({}) with error: {}.'.format(limited_storages, str(limited_storages_ex.message)), task_name=self.task_name)

            nfs_count = len(filter((lambda ds: ds.storage.storage_type == NFS_TYPE), available_storages_with_mounts))
            if nfs_count > 0:
                NFSMounter.check_or_install(self.task_name)
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

    def _get_cloud_region(self):
        cloud_region = os.getenv('CLOUD_REGION')
        return cloud_region if cloud_region else self.default_regions[os.getenv('CLOUD_PROVIDER')]


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
    def execute_and_check_command(command):
        install_check = common.execute_cmd_command(command, silent=False)
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
        result = common.execute_cmd_command(command, silent=True)
        if result == 0:
            Logger.info('-->{path} mounted to {mount}'.format(**params), task_name=task_name)
        else:
            Logger.warn('--> Failed mounting {path} to {mount}'.format(**params), task_name=task_name)

    def get_path(self):
        return self.storage.path.replace(self.scheme(), '', 1)

    def _get_credentials(self, storage):
        region_id = storage.region_id
        account_id = os.getenv('CP_ACCOUNT_ID_{}'.format(region_id))
        account_key = os.getenv('CP_ACCOUNT_KEY_{}'.format(region_id))
        account_region = os.getenv('CP_ACCOUNT_REGION_{}'.format(region_id))
        if not account_id or not account_key or not account_region:
            raise RuntimeError('Account information wasn\'t found in the environment for account with id={}.'
                               .format(region_id))
        return account_id, account_key, account_region

    def _get_credentials_by_region_id(self, region_id):
        account_id = os.getenv('CP_ACCOUNT_ID_{}'.format(region_id))
        account_key = os.getenv('CP_ACCOUNT_KEY_{}'.format(region_id))
        account_region = os.getenv('CP_ACCOUNT_REGION_{}'.format(region_id))
        if not account_id or not account_key or not account_region:
            raise RuntimeError('Account information wasn\'t found in the environment for account with id={}.'
                               .format(region_id))
        return account_id, account_key, account_region


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
        AzureMounter.available = StorageMounter.execute_and_check_command('install_azure_fuse_blobfuse')

    @staticmethod
    def is_available():
        return AzureMounter.available

    @staticmethod
    def init_tmp_dir(tmp_dir, task_name):
        fuse_tmp = os.path.join(tmp_dir, "blobfuse")
        if StorageMounter.create_directory(fuse_tmp, task_name):
            AzureMounter.fuse_tmp = fuse_tmp

    def mount(self, mount_root, task_name):
        super(AzureMounter, self).mount(mount_root, task_name)
        # add resolved ip address for azure blob service to /etc/hosts (only once per account_name)
        params = self.build_mount_params(mount_root)
        command = "grep {account_name} /etc/hosts || getent hosts {account_name}.blob.core.windows.net ".format(**params) \
                  + "| awk '{ printf \"%s\t%s\\n\", $1, $3 }' >> /etc/hosts"
        common.execute_cmd_command(command, silent=True)

    def build_mount_params(self, mount_point):
        account_id, account_key, _ = self._get_credentials(self.storage)
        return {
            'mount': mount_point,
            'path': self.get_path(),
            'tmp_dir': os.path.join(self.fuse_tmp, str(self.storage.id)),
            'account_name': account_id,
            'account_key': account_key,
            'permissions': 'rw' if PermissionHelper.is_storage_writable(self.storage) else 'ro',
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
            fuse_installed = StorageMounter.execute_and_check_command('install_s3_fuse_goofys')
            return FUSE_GOOFYS_ID if fuse_installed else FUSE_NA_ID
        elif fuse_type == FUSE_S3FS_ID:
            fuse_installed = StorageMounter.execute_and_check_command('install_s3_fuse_s3fs')
            if fuse_installed:
                return FUSE_S3FS_ID
            else:
                Logger.warn(
                    "FUSE {fuse_type} was preferred, but failed to install, will try to setup default goofys".format(
                        fuse_type=fuse_type),
                    task_name=task_name)
                fuse_installed = StorageMounter.execute_and_check_command('install_s3_fuse_goofys')
                return FUSE_GOOFYS_ID if fuse_installed else FUSE_NA_ID
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
        aws_key_id, aws_secret, region_name = self._get_credentials(self.storage)
        if not PermissionHelper.is_storage_writable(self.storage):
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
                'tmp_dir': self.fuse_tmp
                }

    def build_mount_command(self, params):
        if params['fuse_type'] == FUSE_GOOFYS_ID:
            return 'AWS_ACCESS_KEY_ID={aws_key_id} AWS_SECRET_ACCESS_KEY={aws_secret} nohup goofys ' \
                   '--dir-mode {mask} --file-mode {mask} -o {permissions} -o allow_other ' \
                    '--stat-cache-ttl {stat_cache} --type-cache-ttl {type_cache} ' \
                    '-f --gid 0 --region "{region_name}" {path} {mount} > /var/log/fuse_{storage_id}.log 2>&1 &'.format(**params)
        elif params['fuse_type'] == FUSE_S3FS_ID:
            return 'AWSACCESSKEYID={aws_key_id} AWSSECRETACCESSKEY={aws_secret} s3fs {path} {mount} -o use_cache={tmp_dir} ' \
                    '-o umask=0000 -o {permissions} -o allow_other -o enable_noobj_cache ' \
                    '-o endpoint="{region_name}" -o url="https://s3.{region_name}.amazonaws.com"'.format(**params)
        else:
            return 'exit 1'


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
        NFSMounter.available = StorageMounter.execute_and_check_command('install_nfs_client')

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
        if os.getenv("CP_CLOUD_PROVIDER_" + region_id) == "AZURE":
            az_acc_id, az_acc_key, _ = self._get_credentials_by_region_id(region_id)
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
            command = command.format(protocol="nfs4")

        permission = 'g+rwx'
        if not PermissionHelper.is_storage_writable(self.storage):
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
