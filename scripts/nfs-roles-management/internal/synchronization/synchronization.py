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
import re
import subprocess

from internal.api.storages_api import Storages
from internal.config import Config
from internal.model.mask import Mask, FullMask

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

    def synchronize(self, user_ids=None, use_symlinks=False, filter_mask=FullMask.READ):
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
                    logging.warning('Wrong storage path: {}'.format(test_storage.path))
                    return None
                storage_path = re.sub('\/[\/]+', '/', storage_path)
                if storage_path.startswith('/'):
                    storage_path = storage_path[1:]
                storage_link_destination = os.path.join(self.__config__.nfs_root, server_name, storage_path)
            elif test_storage.type == 'AZ' or test_storage.type == 'S3' or test_storage.type == "GCP":
                storage_link_destination = os.path.join(self.__config__.nfs_root, test_storage.type, test_storage.name)

            if not os.path.exists(storage_link_destination):
                logging.warning('Storage mount not found at path: {}'.format(storage_link_destination))
                return None
            return storage_link_destination

        try:
            logging.info('Fetching storages...')
            self.__storages__ = []
            self.__users__ = []
            share_mounts = self.list_share_mounts()
            for storage in self.list_storages(filter_mask=filter_mask):
                storage.mount_source = validate_storage(storage, share_mounts)
                if storage.mount_source is not None:
                    self.__storages__.append(storage)
                    for user in storage.users.keys():
                        if user.username not in self.__users__:
                            self.__users__.append(user.username)
            logging.info('{} NFS storages fetched'.format(len(self.__storages__)))
            for user in self.__users__:
                if user_matches_criteria(user):
                    self.synchronize_user(user, use_symlinks=use_symlinks)
                    logging.info('')
        except Exception:
            logging.exception('Storages fetching has failed.')

    def synchronize_user(self, user, use_symlinks=False):
        try:
            logging.info('Processing user {}.'.format(user))
            user_foler_name = user.split('@')[0]
            user_destination_directory = os.path.join(self.__config__.users_root, user_foler_name)
            if not os.path.exists(self.__config__.users_root):
                logging.info('Creating users destination directory {}...'.format(self.__config__.users_root))
                os.makedirs(self.__config__.users_root)
                logging.info('Done.')
            if not os.path.exists(user_destination_directory):
                logging.info('Creating destination directory {}...'.format(user_destination_directory))
                os.mkdir(user_destination_directory)
                logging.info('Done.')
            else:
                logging.info('Destination directory: {}'.format(user_destination_directory))
            try:
                os.chmod(user_destination_directory, chmod_mask_decimal)
            except OSError:
                logging.exception('Error modifying destination directory permissions.')

            storages_to_synchronize = {}
            for storage in self.__storages__:
                for storage_user, storage_user_mask in storage.users.items():
                    if storage_user.username.strip().lower() == user.strip().lower():
                        storages_to_synchronize[storage] = storage_user_mask

            mounted_items = os.listdir(user_destination_directory)
            if not mounted_items and not storages_to_synchronize:
                logging.info('Nothing to synchronize')
                return
            for storage, mask in storages_to_synchronize.items():
                if Mask.is_not_set(mask, Mask.READ):
                    logging.warning('Skipping storage #{} {} linking because of no read permissions...'
                                    .format(storage.identifier, storage.name))
                    continue
                if use_symlinks and Mask.is_not_set(mask, Mask.WRITE):
                    logging.warning('Skipping storage #{} {} linking because read only mounts '
                                    'are not supported in symlinks mode...'
                                    .format(storage.identifier, storage.name))
                    continue
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
                        Synchronization.mount_storage(destination, storage, mounted_storage_name, mask)
                else:
                    logging.info('Storage #{} {} already linked as {}'.format(
                        storage.identifier,
                        storage.name,
                        mounted_storage_name
                    ))
                    mounted_items.remove(mounted_storage_name)
            for mounted_storage_to_remove in mounted_items:
                destination = os.path.join(user_destination_directory, mounted_storage_to_remove)
                if use_symlinks:
                    Synchronization.remove_symlink(destination, mounted_storage_to_remove)
                else:
                    Synchronization.unmount_storage(destination, mounted_storage_to_remove)
        except Exception:
            logging.exception('User processing has failed.')

    @classmethod
    def mount_storage(cls, destination, storage, mounted_storage_name, mask):
        logging.info('Mounting storage #{} {}'.format(storage.identifier, storage.name))
        try:
            logging.info('Creating directory {}...'.format(destination))
            os.mkdir(destination)
            logging.info('Applying permissions...')
            os.chmod(destination, chmod_mask_decimal)
        except OSError:
            logging.exception('Error creating directory.')
            return
        
        command_opts = []
        # Do not allow to WRITE for any storage which has tools limitations
        # We fully rely on the automatic mounting to the jobs for such storages
        if storage.tools_to_mount and os.getenv('CP_DAV_TOOLS_TO_MOUNT_RO', 'false') == 'true' \
                or Mask.is_not_set(mask, Mask.WRITE):
            command_opts = ["-o", "ro"]
        
        command = ["mount", "-B", storage.mount_source, destination]
        command += command_opts
        logging.info('Mounting {} storage as {}...'.format(
            storage.name,
            mounted_storage_name
        ))
        logging.info(command)
        code = subprocess.call(command)
        if code == 0:
            logging.info('Storage mounted: {}'.format(destination))
        else:
            logging.error('Error mounting storage')

    @classmethod
    def unmount_storage(cls, destination, mounted_storage_to_remove):
        logging.info('Removing mounted storage {}...'.format(mounted_storage_to_remove))
        logging.info('Unmounting directory...')
        code = subprocess.call(["umount", destination])
        if code == 0:
            logging.info('Done')
            command = ["rm", "-rf", destination]
            logging.info('Removing directory {}...'.format(destination))
            code = subprocess.call(command)
            if code == 0:
                logging.info('Done')
            else:
                logging.warning('Error removing directory')
        else:
            logging.error('Error unmounting directory')

    @classmethod
    def symlink_storage(cls, destination, storage, mounted_storage_name):
        logging.info('Linking storage #{} {}'.format(storage.identifier, storage.name))
        command = ["ln", "-s", storage.mount_source, destination]
        logging.info('Linking {} storage as {} (symlink)...'.format(
            storage.name,
            mounted_storage_name
        ))
        code = subprocess.call(command)
        if code == 0:
            logging.info('Storage linked: {}'.format(destination))
            try:
                logging.info('Modifying permissions for link...')
                os.lchmod(destination, chmod_mask_decimal)
                logging.info('Done')
            except OSError:
                logging.exception('Error modifying permissions.')
        else:
            logging.error('Error linking storage')

    @classmethod
    def remove_symlink(cls, destination, mounted_storage_to_remove):
        logging.info('Removing linked storage {}...'.format(mounted_storage_to_remove))
        command = ["rm", destination]
        logging.info('Removing item {}...'.format(destination))
        code = subprocess.call(command)
        if code == 0:
            logging.info('Storage link removed: {}'.format(destination))
        else:
            logging.error('Error removing link')

    def list_storages(self, filter_mask):
        page = 0
        page_size = 50
        result = []
        try:
            total = page_size + 1
            while total > page * page_size:
                (storages, total_count) = self.__storages_api__.list(page + 1, page_size, filter_mask=filter_mask)
                total = total_count
                result.extend(storages)
                page += 1
        except Exception:
            logging.exception('Storages listing has failed.')
        return result

    def list_share_mounts(self):
        try:
            return self.__storages_api__.list_share_mounts()
        except Exception:
            logging.exception('Share mounts listing has failed.')
