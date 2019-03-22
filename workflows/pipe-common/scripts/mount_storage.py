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
from pipeline import PipelineAPI, Logger, common

READ_MASK = 1
WRITE_MASK = 1 << 1

DTS = 'DTS'
EXEC_ENVIRONMENT = 'EXEC_ENVIRONMENT'
NFS_TYPE = 'NFS'
S3_TYPE = 'S3'
MOUNT_DATA_STORAGES = 'MountDataStorages'
S3_SCHEME = 's3://'
NFS_SCHEME = 'nfs://'
FUSE_GOOFYS_ID = 'goofys'
FUSE_S3FS_ID = 's3fs'
FUSE_NA_ID = None

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

    def run(self, mount_root, tmp_dir):
        try:
            Logger.info('Starting mounting remote data storages.', task_name=self.task_name)
    
            Logger.info('Fetching list of allowed storages...', task_name=self.task_name)
            available_storages = self.api.load_available_storages()
            if not available_storages:
                Logger.success('No remote storages are available', task_name=self.task_name)
                return
            Logger.info('Found {} available storage(s). Checking mount options.'.format(len(available_storages)), task_name=self.task_name)

            fuse_tmp = os.path.join(tmp_dir, "s3fuse")
            if not self.create_directory(fuse_tmp):
                fuse_tmp = '/tmp'

            fuse_available = self.check_or_install_fuse()
            
            aws_default_region = os.getenv('AWS_DEFAULT_REGION', 'us-east-1')
            aws_region = os.getenv('AWS_REGION', aws_default_region)
            limited_storages = os.getenv('CP_CAP_LIMIT_MOUNTS')
            if limited_storages:
                try:
                    limited_storages_list = [int(x.strip()) for x in limited_storages.split(',')]
                    available_storages = [x for x in available_storages if x.id in limited_storages_list]
                    Logger.info('Run is launched with mount limits ({}) Only {} storages will be mounted'.format(limited_storages, len(available_storages)), task_name=self.task_name)
                except Exception as limited_storages_ex:
                    Logger.warn('Unable to parse CP_CAP_LIMIT_MOUNTS value({}) with error: {}.'.format(limited_storages, str(limited_storages_ex.message)), task_name=self.task_name)

            nfs_count = len(filter((lambda ds: ds.storage_type == 'NFS' and ds.region_name == aws_region), available_storages))
            nfs_available = nfs_count > 0 and self.check_or_install_nfs()
            if not fuse_available and not nfs_available:
                Logger.success('Mounting of remote storages is not available for this image', task_name=self.task_name)
                return
            for storage in available_storages:
                if not PermissionHelper.is_storage_readable(storage):
                    continue
                mounter = self.get_mount_manager(storage, nfs_available, fuse_available, fuse_tmp)
                if mounter is not None:
                    self.mount(mounter, mount_root)
                elif storage.storage_type != NFS_TYPE and storage.storage_type != S3_TYPE:
                    Logger.warn('Unsupported storage type {}.'.format(storage.storage_type), task_name=self.task_name)
            Logger.success('Finished data storage mounting', task_name=self.task_name)
        except Exception as e:
            Logger.fail('Unhandled error during mount task: {}.'.format(str(e.message)), task_name=self.task_name)

    def get_mount_manager(self, storage, nfs_available, fuse_available, fuse_tmp):
        if storage.storage_type == NFS_TYPE and nfs_available:
            return NFSMounter(storage)
        elif storage.storage_type == S3_TYPE and fuse_available:
            return S3Mounter(storage, fuse_available, fuse_tmp)
        else:
            return None

    def mount(self, mounter, mount_root):
        mount_point = mounter.build_mount_point(mount_root)
        if not self.create_directory(mount_point):
            return
        params = mounter.build_mount_params(mount_point)
        mount_command = mounter.build_mount_command(params)
        self.execute_mount(mount_command, params)

    def check_or_install_fuse(self):
        fuse_type = os.getenv('CP_S3_FUSE_TYPE', FUSE_GOOFYS_ID)
        if fuse_type == FUSE_GOOFYS_ID:
            fuse_installed = self.execute_and_check_command('install_s3_fuse_goofys')
            return FUSE_GOOFYS_ID if fuse_installed else FUSE_NA_ID
        elif fuse_type == FUSE_S3FS_ID:
            fuse_installed = self.execute_and_check_command('install_s3_fuse_s3fs')
            if fuse_installed:
                return FUSE_S3FS_ID
            else:
                Logger.warn("FUSE {fuse_type} was preferred, but failed to install, will try to setup default goofys".format(fuse_type=fuse_type), 
                            task_name=self.task_name)
                fuse_installed = self.execute_and_check_command('install_s3_fuse_goofys')
                return FUSE_GOOFYS_ID if fuse_installed else FUSE_NA_ID
            fi
        else:
            Logger.warn("FUSE {fuse_type} type is not defined for S3 fuse".format(fuse_type=fuse_type), 
                            task_name=self.task_name)
            return FUSE_NA_ID

    def check_or_install_nfs(self):
        return self.execute_and_check_command('install_nfs_client')

    def execute_and_check_command(self, command):
        install_check = common.execute_cmd_command(command, silent=False)
        return install_check == 0

    def create_directory(self, path):
        result = common.execute_cmd_command('mkdir -p {path}'.format(path=path), silent=True)
        if result != 0:
            Logger.warn('Failed to create mount directory: {path}'.format(path=path), task_name=self.task_name)
            return False
        return True

    def execute_mount(self, command, params):
        result = common.execute_cmd_command(command, silent=True)
        if result == 0:
            Logger.info('-->{path} mounted to {mount}'.format(**params), task_name=self.task_name)
        else:
            Logger.warn('--> Failed mounting {path} to {mount}'.format(**params), task_name=self.task_name)


class S3Mounter:

    def __init__(self, storage, fuse_available, fuse_tmp):
        self.storage = storage
        self.fuse_tmp = fuse_tmp
        self.fuse_type = fuse_available

    def build_mount_point(self, mount_root):
        mount_point = self.storage.mount_point
        if mount_point is None:
            mount_point = os.path.join(mount_root, self.get_path())
        return mount_point

    def build_mount_params(self, mount_point):
        mask = '0774'
        permissions = 'rw'
        stat_cache = os.getenv('CP_S3_FUSE_STAT_CACHE', '1m0s')
        type_cache = os.getenv('CP_S3_FUSE_TYPE_CACHE', '1m0s')
        aws_key_id = os.getenv('AWS_ACCESS_KEY_ID', '')
        aws_secret = os.getenv('AWS_SECRET_ACCESS_KEY', '')
        if not PermissionHelper.is_storage_writable(self.storage):
            mask = '0554'
            permissions = 'ro'
        return {'mount': mount_point,
                'storage_id': str(self.storage.id),
                'path': self.get_path(),
                'mask': mask,
                'permissions': permissions,
                'region_name': self.storage.region_name,
                'stat_cache': stat_cache,
                'type_cache': type_cache,
                'fuse_type': self.fuse_type,
                'aws_key_id': aws_key_id,
                'aws_secret': aws_secret,
                'tmp_dir': self.fuse_tmp
                }

    def build_mount_command(self, params):
        if params['fuse_type'] == FUSE_GOOFYS_ID:
            return 'nohup goofys --dir-mode {mask} --file-mode {mask} -o {permissions} -o allow_other ' \
                    '--stat-cache-ttl {stat_cache} --type-cache-ttl {type_cache} ' \
                    '-f --gid 0 --region "{region_name}" {path} {mount} > /var/log/fuse_{storage_id}.log 2>&1 &'.format(**params)
        elif params['fuse_type'] == FUSE_S3FS_ID:
            return 'AWSACCESSKEYID={aws_key_id} AWSSECRETACCESSKEY={aws_secret} s3fs {path} {mount} -o use_cache={tmp_dir} ' \
                    '-o umask=0000 -o {permissions} -o allow_other -o enable_noobj_cache ' \
                    '-o endpoint="{region_name}" -o url="https://s3.{region_name}.amazonaws.com"'.format(**params)
        else:
            return 'exit 1'

    def get_path(self):
        return self.storage.path.replace(S3_SCHEME, '', 1)


class NFSMounter:

    def __init__(self, storage):
        self.storage = storage

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
        command = 'mount -t nfs4'

        mount_options = self.storage.mount_options
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

    def get_path(self):
        return self.storage.path.replace(NFS_SCHEME, '', 1)


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
