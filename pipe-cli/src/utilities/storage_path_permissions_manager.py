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


def get_permissions_manager(storage, root_path, write_required=False, quite=True, root_file_flag=False,
                            is_destination=False):
    if not storage.path_permissions_enabled:
        return DefaultStoragePathPermissionsManager()
    current_user = User.whoami()
    if is_user_admin_or_owner(current_user, storage.owner):
        return DefaultStoragePathPermissionsManager()
    if write_required:
        return StoragePathWritePermissionsManager(storage, root_path, quite, root_file_flag, is_destination)
    return StoragePathReadPermissionsManager(storage, root_path, quite, root_file_flag)


class DefaultStoragePathPermissionsManager:
    """
    This is a default implementation - shall be used for admins/owners and
    storages that do not support storage path permissions.
    """

    def is_file_allowed(self, file_path):
        return True

    def is_folder_allowed(self, folder_path):
        return True


class StoragePathReadPermissionsManager(DefaultStoragePathPermissionsManager):

    def __init__(self, storage, root_path, quite, root_file_flag):
        self._inner = StoragePathPermissionsManager(storage, root_path,
                                                    quite=quite,
                                                    root_file_flag=root_file_flag)

    def is_file_allowed(self, file_path):
        return self._inner.is_file_allowed(file_path)

    def is_folder_allowed(self, folder_path):
        return self._inner.is_folder_allowed(folder_path)


class StoragePathWritePermissionsManager(DefaultStoragePathPermissionsManager):

    def __init__(self, storage, root_path, quite, root_file_flag, is_destination=False):
        self._inner = StoragePathPermissionsManager(storage, root_path,
                                                    read_only=False,
                                                    quite=quite,
                                                    root_file_flag=root_file_flag,
                                                    is_destination=is_destination)

    def is_file_allowed(self, file_path):
        return self._inner.is_file_allowed(file_path)

    def is_folder_allowed(self, folder_path):
        return self._inner.is_folder_allowed(folder_path)


class StoragePathPermissionsManager:
    ACCESS_DENIED_MSG = "Failed to write to the path '%s': access is denied."
    SOURCE_NOT_FOUND_MSG = "Source %s doesn't exist"

    def __init__(self, storage, root_path, read_only=True, quite=True, root_file_flag=False, is_destination=False):
        self._delimiter = '/'
        self._has_permissions_on_root = False
        self._quite = quite
        self._read_only = read_only
        self._storage = storage
        self._raw_permissions = self._fetch(storage.identifier)
        self._root_file_flag = root_file_flag
        self._is_destination = is_destination

        if not self._raw_permissions:
            self._log_source_not_found(root_path)
            sys.exit(1)

        self._permissions = pygtrie.CharTrie(self._permissions_to_dict(self._raw_permissions))
        self._file_permissions = pygtrie.CharTrie(
            self._file_permissions_to_dict([p for p in self._raw_permissions if p.file_name]))

        if not self._root_file_flag and not self.is_folder_allowed(root_path, source_check=True):
            # No permissions on root folder. Exiting.
            sys.exit(1)

    def is_file_allowed(self, file_path):
        if not self._raw_permissions:
            return False

        # checks if file has explicit permissions
        file_path = self._normalize_path(file_path)
        file_permissions = self._file_permissions.get(file_path)
        if file_permissions:
            if self._read_only:
                return True
            if self._write_ok(file_permissions[0].mask):
                return True
            self._log_access_denied(file_path)
            return False

        # checks if any of parent folders have permissions
        folder_path = self._get_folder_path(file_path)
        parents = self._find_clothest_parent_folders(folder_path)
        if parents:
            if self._read_only:
                return True
            if self._write_ok(parents[0].mask):
                return True
            self._log_access_denied(file_path)
            return False

        if self._is_destination:
            # when no permissions granted to the destination target path an <access is denied> error shall be shown
            self._log_access_denied(file_path)
            return False

        # indicates that root source is a file
        if self._root_file_flag:
            self._log_source_not_found(file_path)
        logging.debug(u"Filtering out file '%s' since no permissions found." % folder_path)
        return False

    def is_folder_allowed(self, folder_path, source_check=False):
        if not self._raw_permissions:
            return False

        # checks if any of parent folders have permissions
        folder_path = self._normalize_path(folder_path, is_dir=True)
        parents = self._find_clothest_parent_folders(folder_path)
        if parents and self._read_only:
            return True

        # checks if any of child paths have permissions:
        # if permissions are granted to the file in a child hierarchy, the folder shall be listed
        children = self._find_child_paths(folder_path)
        if children and self._read_only:
            return True

        if not self._read_only:
            # checks if write permissions granted to clothest parent
            has_parent_write = parents and self._write_ok(parents[0].mask)
            # checks if at least one child with read-only permissions
            has_child_read_only = (children and
                                   [c for c in children if not self._write_ok(self._permissions.get(c)[0].mask)])
            if has_parent_write and not has_child_read_only:
                return True
            if parents or children:
                self._log_access_denied(folder_path)
                return False

        if source_check:
            # The source_check flag indicates that an <source not found> error message shall be shown
            # if the specified source does not exist.
            # Shall not be specified for cases when a path shall be just filtered out.
            self._log_source_not_found(folder_path)
            return False

        logging.debug(u"Filtering out folder '%s' since no permissions found." % folder_path)
        return False

    def _build_storage_path(self, prefix):
        _prefix = str(prefix or self._delimiter).lstrip(self._delimiter)
        return str(self._storage.type).lower() + "://" + self._storage.name + self._delimiter + _prefix

    def _find_clothest_parent_permissions(self, prefix): # -> [StoragePathPermission]
        permissions = self._permissions.longest_prefix(prefix)
        if not permissions[0]:
            return []
        return permissions.get(prefix)

    def _log_access_denied(self, item_path):
        message = self.ACCESS_DENIED_MSG % self._build_storage_path(item_path)
        if not self._quite:
            click.echo(message, err=True)
        else:
            logging.debug(message)

    def _log_source_not_found(self, item_path):
        click.echo(self.SOURCE_NOT_FOUND_MSG % self._build_storage_path(item_path), err=True)

    def _find_clothest_parent_folders(self, folder_path): # -> [StoragePathPermission]
        parents = self._find_clothest_parent_permissions(folder_path)
        return [p for p in parents if p.file_name is None]

    def _find_child_paths(self, folder_path): # -> [str]
        try:
            return self._permissions.keys(prefix=folder_path, shallow=False)
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
        if not target_path:
            return self._delimiter
        target_path = self._delimiter + target_path
        if is_dir:
            target_path = target_path + self._delimiter
        return target_path

    @staticmethod
    def _write_ok(mask):
        return mask & 4 != 0

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
