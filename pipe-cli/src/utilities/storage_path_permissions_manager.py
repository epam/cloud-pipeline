# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import sys
import click
import pygtrie
import os
import logging

from src.api.storage_path_permissions import StoragePathPermissions
from src.api.user import User


def verify_storage_path_permissions_allowed(storage):
    if not storage.path_permissions_enabled:
        return
    current_user = User.whoami()
    if is_user_admin_or_owner(current_user, storage.owner):
        return
    click.echo('Storages with enabled path permissions currently are not available for plain users.',
               err=True)
    sys.exit(1)


def is_user_admin_or_owner(user, storage_owner):
    if user.get('admin', False):
        return True
    if [r for r in user.get('roles', []) if r.get('name', '').upper() == 'ROLE_ADMIN']:
        return True
    if [g for g in user.get('groups', []) if g.upper() == 'ROLE_ADMIN']:
        return True
    if user.get('userName').upper() == str(storage_owner).upper():
        return True
    return False


class StoragePathPermission:

    def __init__(self, folder_path, file_name, mask):
        self.folder_path = folder_path
        self.file_name = file_name
        self.item_path = os.path.join(folder_path, file_name or '')
        self.mask = mask

    @staticmethod
    def from_dict(data):
        return StoragePathPermission(data.get('folderPath'), data.get('fileName'), data.get('mask'))


def get_manager(storage, root_path):
    if not storage.path_permissions_enabled:
        return AbstractStoragePathPermissionsManager()
    current_user = User.whoami()
    if is_user_admin_or_owner(current_user, storage.owner):
        return AbstractStoragePathPermissionsManager()
    return StoragePathPermissionsManager(storage.identifier, root_path)


class AbstractStoragePathPermissionsManager:
    """
    This is a default implementation - shall be used for admins/owners and
    storages that do not support storage path permissions.
    """

    def is_file_allowed(self, file_path):
        return True

    def is_folder_allowed(self, folder_path):
        return True


class StoragePathPermissionsManager(AbstractStoragePathPermissionsManager):


    def __init__(self, storage_id, root_path):
        self._delimiter = '/'
        if root_path and root_path is not self._delimiter:
            self._root_path = self._delimiter + str(root_path).strip(self._delimiter) + self._delimiter
        else:
            self._root_path = self._delimiter
        self._has_permissions_on_root = False
        self._init_permissions(storage_id)

    def _init_permissions(self, storage_id):
        self._raw_permissions = self._fetch(storage_id)
        self._folder_permissions = pygtrie.CharTrie(
            self._permissions_to_dict(self._raw_permissions))

        if self._find_clothest_parent_folders(self._root_path):
            self._has_permissions_on_root = True

        self._file_permissions = pygtrie.CharTrie(
            self._file_permissions_to_dict([p for p in self._raw_permissions if p.file_name]))

    def is_file_allowed(self, file_path):
        if self._has_permissions_on_root:
            logging.debug(u"[%s] Has permissions to list full folder" % self._root_path)
            return True

        # checks if file has explicit permissions
        file_path = self._normalize_path(file_path)
        if self._file_permissions.get(file_path):
            return True

        # checks if any of parent folders have permissions
        folder_path = self._get_folder_path(file_path)
        if self._find_clothest_parent_folders(folder_path):
            return True

        logging.debug(u"Filtering out file '%s' since no permissions found." % folder_path)
        return False

    def is_folder_allowed(self, folder_path):
        if self._has_permissions_on_root:
            logging.debug(u"[%s] Has permissions to list full folder" % self._root_path)
            return True

        # checks if any of parent folders have permissions
        folder_path = self._normalize_path(folder_path, is_dir=True)
        if self._find_clothest_parent_folders(folder_path):
            return True

        # checks if any of child paths have permissions:
        # if permissions are granted to the file in a child hierarchy, the folder shall be listed
        if self._find_child_paths(folder_path):
            return True

        logging.debug(u"Filtering out folder '%s' since no permissions found." % folder_path)
        return False

    def _find_clothest_parent_permissions(self, prefix): # -> [StoragePathPermission]
        permissions = self._folder_permissions.longest_prefix(prefix)
        if not permissions[0]:
            return []
        return permissions.get(prefix)

    def _find_clothest_parent_folders(self, folder_path): # -> [StoragePathPermission]
        parents = self._find_clothest_parent_permissions(folder_path)
        return [p for p in parents if p.file_name is None]

    def _find_child_paths(self, folder_path): # -> [str]
        try:
            return self._folder_permissions.keys(prefix=folder_path, shallow=False)
        except KeyError:
            return []

    def _get_folder_path(self, file_path):
        folder_path = os.path.dirname(file_path)
        folder_path = folder_path.strip(self._delimiter)

        if not folder_path:
            return self._delimiter

        return self._delimiter + folder_path + self._delimiter

    def _normalize_path(self, target_path, is_dir=False):
        target_path = target_path.strip(self._delimiter)
        if not str(target_path).startswith(str(self._root_path).strip(self._delimiter)):
            target_path = os.path.join(self._root_path, target_path)
        if not str(target_path).startswith(self._delimiter):
            target_path = self._delimiter + target_path
        if is_dir and not str(target_path).endswith(self._delimiter):
            target_path = target_path + self._delimiter
        return target_path

    @staticmethod
    def _permissions_to_dict(permissions):
        _dict = {}
        for p in permissions:
            if p.folder_path not in _dict:
                _dict[p.folder_path] = []
            _dict.get(p.folder_path).append(p)
        return _dict

    @staticmethod
    def _file_permissions_to_dict(permissions):
        _dict = {}
        for p in permissions:
            key_path = os.path.join(p.folder_path, p.file_name)
            if key_path not in _dict:
                _dict[key_path] = []
            _dict.get(key_path).append(p)
        return _dict

    @staticmethod
    def _fetch(storage_id):
        return [StoragePathPermission.from_dict(data)
                for data in StoragePathPermissions.get_user_permissions(storage_id)]
