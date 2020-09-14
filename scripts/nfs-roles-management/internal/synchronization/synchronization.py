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

from ..config import Config
from ..api.storages_api import Storages
from ..model.storage_model import StorageModel
from ..model.share_mount_model import ShareMountModel

import sys
import traceback
import os
import subprocess
import re
from exceptions import KeyboardInterrupt

# All user's directories and links should be CHMODed to 550
# 550 is an octal number for -r-xr-x--- permissions.
# For python 2 octal number should be presented as '0550',
# but for python 3 it should be '0o550'.
# To support both python 2 & 3 versions, we will pass decimal presentation of octal value:
# For octal 550 this would be decimal 360.
chmod_mask_decimal = 360


class Synchronization(object):
    def __init__(self, config):
        self.__storages_api__ = Storages(config)
        self.__config__ = config if config is not None else Config.instance()
        self.__users__ = []
        self.__storages__ = []

    def synchronize(self, user_ids=None, use_symlinks=False):
        def user_matches_criteria(test_user):
            user_name = test_user.lower()
            return user_ids is None or len(user_ids) == 0 or len([u for u in user_ids if u.lower() == user_name]) > 0

        def validate_storage(test_storage, share_mounts):
            if not test_storage.is_nfs() and test_storage.type != 'AZ' and test_storage.type != 'S3' and test_storage.type != 'GCP':
                return None
            server_name = None
            storage_path = None
            storage_link_destination = None
            if test_storage.is_nfs():
                try:
                    if test_storage.path.lower().startswith('nfs://'):
                        if share_mounts[test_storage.share_mount_id].mount_type == "SMB":
                            search = re.search('([^\/]+)\/(.+)' ,test_storage.path[len('nfs://'):])
                            (server_name, storage_path) = search.group(1), search.group(2)
                        else:
                            (server_name, storage_path) = test_storage.path[len('nfs://'):].split(':')
                except ValueError:
                    pass
                except AttributeError:
                    pass
                if server_name is None or storage_path is None:
                    print 'Wrong storage path: {}'.format(test_storage.path)
                    return None
                storage_path = re.sub('\/[\/]+', '/', storage_path)
                if storage_path.startswith('/'):
                    storage_path = storage_path[1:]
                storage_link_destination = os.path.join(self.__config__.nfs_root, server_name, storage_path)
            elif test_storage.type == 'AZ' or test_storage.type == 'S3' or test_storage.type == "GCP":
                storage_link_destination = os.path.join(self.__config__.nfs_root, test_storage.type, test_storage.name)

            if not os.path.exists(storage_link_destination):
                print 'Storage mount not found at path: {}'.format(storage_link_destination)
                return None
            return storage_link_destination

        try:
            print 'Fetching storages...'
            self.__storages__ = []
            self.__users__ = []
            share_mounts = self.list_share_mounts()
            for storage in self.list_storages():
                storage.mount_source = validate_storage(storage, share_mounts)
                if storage.mount_source is not None:
                    self.__storages__.append(storage)
                    if len(storage.users) > 0:
                        for user in storage.users:
                            if user.username not in self.__users__:
                                self.__users__.append(user.username)
            print '{} NFS storages fetched'.format(len(self.__storages__))
            for user in self.__users__:
                if user_matches_criteria(user):
                    self.synchronize_user(user, use_symlinks=use_symlinks)
                    print ''
        except RuntimeError as error:
            print error.message
        except KeyboardInterrupt:
            raise
        except:
            print 'Error: ', traceback.format_exc()

    def synchronize_user(self, user, use_symlinks=False):
        try:
            print 'Processing user {}.'.format(user)
            user_foler_name = user.split('@')[0]
            user_destination_directory = os.path.join(self.__config__.users_root, user_foler_name)
            if not os.path.exists(self.__config__.users_root):
                print 'Creating users destination directory {}...'.format(self.__config__.users_root)
                os.makedirs(self.__config__.users_root)
                print 'Done.'
            if not os.path.exists(user_destination_directory):
                print 'Creating destination directory {}...'.format(user_destination_directory)
                os.mkdir(user_destination_directory)
                print 'Done.'
            else:
                print 'Destination directory: {}'.format(user_destination_directory)
            try:
                os.chmod(user_destination_directory, chmod_mask_decimal)
            except OSError as error:
                print 'Error modifying destination directory permissions: {}'.format(error.message)

            def user_has_permission_for_storage(test_storage):
                return len([u for u in test_storage.users if u.username.lower() == user.lower()]) > 0

            storages_to_synchronize = [s for s in self.__storages__ if user_has_permission_for_storage(s)]
            mounted_items = os.listdir(user_destination_directory)
            nothing_to_synchronize = len(mounted_items) == 0 and len(storages_to_synchronize) == 0
            for storage in storages_to_synchronize:
                mounted_storage_name = storage.name\
                    .replace(':', '_')\
                    .replace('\\', '_')\
                    .replace('/', '_')\
                    .replace(' ', '_')\
                    .replace('-', '_')
                if mounted_storage_name not in mounted_items:
                    destination = os.path.join(user_destination_directory, mounted_storage_name)
                    if use_symlinks:
                        Synchronization.symlink_storage(destination, storage, mounted_storage_name)
                    else:
                        Synchronization.mount_storage(destination, storage, mounted_storage_name)
                else:
                    print 'Storage #{} {} already linked as {}'.format(
                        storage.identifier,
                        storage.name,
                        mounted_storage_name
                    )
                    mounted_items.remove(mounted_storage_name)
            for mounted_storage_to_remove in mounted_items:
                destination = os.path.join(user_destination_directory, mounted_storage_to_remove)
                if use_symlinks:
                    Synchronization.remove_symlink(destination, mounted_storage_to_remove)
                else:
                    Synchronization.unmount_storage(destination, mounted_storage_to_remove)
            if nothing_to_synchronize:
                print 'Nothing to synchronize'
        except OSError as error:
            print error.message
        except RuntimeError as error:
            print error.message
        except KeyboardInterrupt:
            raise
        # except:
        #     print 'Error: ', sys.exc_info()[0]

    @classmethod
    def mount_storage(cls, destination, storage, mounted_storage_name):
        print 'Mounting storage #{} {}'.format(storage.identifier, storage.name)
        try:
            print 'Creating directory {}...'.format(destination)
            os.mkdir(destination)
            print 'Applying permissions...'
            os.chmod(destination, chmod_mask_decimal)
        except OSError as error:
            print 'Error creating directory: {}'.format(error.message)
            return
        command = ["mount", "-B", storage.mount_source, destination]
        print 'Mounting {} storage as {}...'.format(
            storage.name,
            mounted_storage_name
        )
        code = subprocess.call(command)
        if code == 0:
            print 'Storage mounted: {}'.format(destination)
        else:
            print 'Error mounting storage'

    @classmethod
    def unmount_storage(cls, destination, mounted_storage_to_remove):
        print 'Removing mounted storage {}...'.format(mounted_storage_to_remove)
        print 'Unmounting directory...'
        code = subprocess.call(["umount", destination])
        if code == 0:
            print 'Done'
            command = ["rm", "-rf", destination]
            print 'Removing directory {}...'.format(destination)
            code = subprocess.call(command)
            if code == 0:
                print 'Done'
            else:
                print 'Error removing directory'
        else:
            print 'Error unmounting directory'

    @classmethod
    def symlink_storage(cls, destination, storage, mounted_storage_name):
        print 'Linking storage #{} {}'.format(storage.identifier, storage.name)
        command = ["ln", "-s", storage.mount_source, destination]
        print 'Linking {} storage as {} (symlink)...'.format(
            storage.name,
            mounted_storage_name
        )
        code = subprocess.call(command)
        if code == 0:
            print 'Storage linked: {}'.format(destination)
            try:
                print 'Modifying permissions for link...'
                os.lchmod(destination, chmod_mask_decimal)
                print 'Done'
            except OSError as error:
                print 'Error modifying permissions: {}'.format(error.message)
        else:
            print 'Error linking storage'

    @classmethod
    def remove_symlink(cls, destination, mounted_storage_to_remove):
        print 'Removing linked storage {}...'.format(mounted_storage_to_remove)
        command = ["rm", destination]
        print 'Removing item {}...'.format(destination)
        code = subprocess.call(command)
        if code == 0:
            print 'Storage link removed: {}'.format(destination)
        else:
            print 'Error removing link'

    def list_storages(self):
        page = 0
        page_size = 50
        result = []
        try:
            total = page_size + 1
            while total > page * page_size:
                (storages, total_count) = self.__storages_api__.list(page + 1, page_size)
                total = total_count
                result.extend(storages)
                page += 1
        except RuntimeError as error:
            print error.message
        except:
            print 'Error: ', traceback.format_exc()
        return result

    def list_share_mounts(self):
            try:
                return self.__storages_api__.list_share_mounts()
            except RuntimeError as error:
                print error.message
            except:
                print 'Error: ', traceback.format_exc()
