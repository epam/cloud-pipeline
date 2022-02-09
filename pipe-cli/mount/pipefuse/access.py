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
import threading
from abc import ABCMeta, abstractmethod

import errno
import time
from fuse import FuseOSError

from fsclient import FileSystemClientDecorator
from pipefuse.fuseutils import get_parent_paths, get_parent_dirs

FILE = 'FILE'
FOLDER = 'FOLDER'

INHERIT_MASK = 0b0
READ_MASK = 0b1
NO_READ_MASK = 0b10
WRITE_MASK = 0b100
NO_WRITE_MASK = 0b1000
EXECUTE_MASK = 0b10000
NO_EXECUTE_MASK = 0b100000
SYNTHETIC_READ_MASK = 0b1000000

INHERIT = 'INHERIT'
READ = 'READ'
NO_READ = 'NO_READ'
WRITE = 'WRITE'
NO_WRITE = 'NO_WRITE'
EXECUTE = 'EXECUTE'
NO_EXECUTE = 'NO_EXECUTE'
SYNTHETIC_READ = 'SYNTHETIC_READ'


class AccessControlFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, manager):
        """
        Access file system client decorator.

        Filters file system tree and restricts access to certain resources using permissions from permissions manager.

        Does not translate permissions to fuse POSIX ACLs.

        :param inner: Decorating file system client.
        :param manager: Permissions manager.
        """
        super(AccessControlFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._manager = manager
        self._permissions = {}

    def exists(self, path):
        if not self._is_synthetic_read_allowed(path):
            self._access_denied_warning(path)
            return False
        return self._inner.exists(path)

    def attrs(self, path):
        if not self._is_synthetic_read_allowed(path):
            self._access_denied(path)
        return self._inner.attrs(path)

    def ls(self, path, depth=1):
        if self._is_read_allowed(path):
            return self._inner.ls(path, depth)
        if self._is_synthetic_read_allowed(path):
            allowed_child_items = []
            for child_item in self._inner.ls(path, depth):
                if self._is_synthetic_read_allowed(path + child_item.name):
                    allowed_child_items.append(child_item)
            return allowed_child_items
        self._access_denied(path)

    def upload(self, buf, path):
        if not self._is_write_allowed(path):
            self._access_denied(path)
        self._inner.upload(buf, path)

    def delete(self, path):
        # todo: Delete permissions for a file
        if not self._is_write_allowed(path):
            self._access_denied(path)
        self._inner.delete(path)

    def mv(self, old_path, path):
        # todo: Move permissions from an old directory to a new one with all permission items within
        # todo: Move permissions from an old file to a new one
        if not self._is_write_allowed(old_path):
            self._access_denied(old_path)
        if not self._is_write_allowed(path):
            self._access_denied(path)
        self._inner.mv(old_path, path)

    def mkdir(self, path):
        if not self._is_write_allowed(path):
            self._access_denied(path)
        self._inner.mkdir(path)

    def rmdir(self, path):
        # todo: Do not allow to delete a directory if there are no WRITE permissions for some items within
        # todo: Delete permissions for a directory and all items within
        if not self._is_write_allowed(path):
            self._access_denied(path)
        self._inner.rmdir(path)

    def download_range(self, fh, buf, path, offset=0, length=0):
        if not self._is_read_allowed(path):
            self._access_denied(path)
        self._inner.download_range(fh, buf, path, offset, length)

    def upload_range(self, fh, buf, path, offset=0):
        if not self._is_write_allowed(path):
            self._access_denied(path)
        self._inner.upload_range(fh, buf, path, offset)

    def flush(self, fh, path):
        self._inner.flush(fh, path)

    def truncate(self, fh, path, length):
        if not self._is_write_allowed(path):
            self._access_denied(path)
        self._inner.truncate(fh, path, length)

    def _is_read_allowed(self, path):
        permissions = self._manager.get(path)
        return READ in permissions

    def _is_write_allowed(self, path):
        permissions = self._manager.get(path)
        return WRITE in permissions

    def _is_synthetic_read_allowed(self, path):
        permissions = self._manager.get(path)
        return READ in permissions or SYNTHETIC_READ in permissions

    def _access_denied(self, path):
        self._access_denied_warning(path)
        raise FuseOSError(errno.EACCES)

    def _access_denied_warning(self, path):
        logging.debug('Access denied: %s.', path)


class PermissionProvider:
    __metaclass__ = ABCMeta

    @abstractmethod
    def get(self):
        pass


class CloudPipelinePermissionProvider(PermissionProvider):

    def __init__(self, pipe, bucket, verbose=False):
        """
        Cloud Pipeline permissions provider.

        Downloads and resolves Cloud Pipeline storage permissions.

        :param pipe: Cloud Pipeline API client.
        :param bucket: Cloud Pipeline bucket object.
        :param verbose: Enables verbose logging.
        """
        self._pipe = pipe
        self._bucket = bucket
        self._verbose = verbose
        self._delimiter = '/'

    def get(self):
        logging.debug('Retrieving Cloud Pipeline storage permissions...')
        raw_permissions = self._get_user_permissions()
        logging.debug('Resolving Cloud Pipeline storage permissions...')
        raw_permissions_dict = self._group_permissions_by_path(raw_permissions)
        permissions = {}
        for raw_permission_path in sorted(raw_permissions_dict.keys()):
            path_raw_permissions = raw_permissions_dict[raw_permission_path]
            read_permission = INHERIT
            write_permission = INHERIT
            for raw_permission in self._get_permissions_sorted_by_sids(path_raw_permissions):
                read_permission = READ if READ_MASK & raw_permission.get('mask', INHERIT_MASK) == READ_MASK else read_permission
                read_permission = NO_READ if NO_READ_MASK & raw_permission.get('mask', INHERIT_MASK) == NO_READ_MASK else read_permission
                write_permission = WRITE if WRITE_MASK & raw_permission.get('mask', INHERIT_MASK) == WRITE_MASK else write_permission
                write_permission = NO_WRITE if NO_WRITE_MASK & raw_permission.get('mask', INHERIT_MASK) == NO_WRITE_MASK else write_permission
            if self._verbose:
                logging.debug('Resolved raw %s+%s permissions for %s',
                              read_permission, write_permission, raw_permission_path)

            current_updated_permissions = permissions[raw_permission_path] = permissions.get(raw_permission_path, [])
            if read_permission == READ:
                if READ in current_updated_permissions:
                    current_updated_permissions.remove(READ)
                if NO_READ in current_updated_permissions:
                    current_updated_permissions.remove(NO_READ)
                if SYNTHETIC_READ in current_updated_permissions:
                    current_updated_permissions.remove(SYNTHETIC_READ)
                current_updated_permissions.append(READ)
            if read_permission == NO_READ:
                if READ not in current_updated_permissions and SYNTHETIC_READ not in current_updated_permissions:
                    if NO_READ in current_updated_permissions:
                        current_updated_permissions.remove(NO_READ)
                    current_updated_permissions.append(NO_READ)
            if write_permission == WRITE:
                if WRITE in current_updated_permissions:
                    current_updated_permissions.remove(WRITE)
                if NO_WRITE in current_updated_permissions:
                    current_updated_permissions.remove(NO_WRITE)
                current_updated_permissions.append(WRITE)
            if write_permission == NO_WRITE:
                if WRITE not in current_updated_permissions:
                    if NO_WRITE in current_updated_permissions:
                        current_updated_permissions.remove(NO_WRITE)
                    current_updated_permissions.append(NO_WRITE)
            if self._verbose:
                logging.debug('Resolved effective %s+%s permissions for %s',
                              read_permission, write_permission, raw_permission_path)

            for raw_permission_parent_path in get_parent_paths(raw_permission_path):
                if read_permission in [READ or SYNTHETIC_READ]:
                    permission_parent_permissions = \
                        permissions[raw_permission_parent_path + self._delimiter] = \
                        permissions.get(raw_permission_parent_path + self._delimiter, [])
                    if NO_READ in current_updated_permissions:
                        current_updated_permissions.remove(NO_READ)
                    if READ not in permission_parent_permissions and SYNTHETIC_READ not in permission_parent_permissions:
                        if self._verbose:
                            logging.debug('Resolved uplifted %s permission for %s',
                                          SYNTHETIC_READ, raw_permission_parent_path + self._delimiter)
                        permission_parent_permissions.append(SYNTHETIC_READ)

        return permissions

    def _get_user_permissions(self):
        user = self._pipe.get_user()
        if user.is_admin:
            return [{'type': FOLDER, 'path': '', 'mask': (READ_MASK | WRITE_MASK)}]
        permissions = self._pipe.get_storage_permissions(self._bucket)
        user_permissions = []
        for permission in permissions:
            sid = permission.get('sid', {})
            sid_name = sid.get('name', '')
            sid_type = sid.get('type', '')
            if sid_type == 'USER' and sid_name == user.name:
                user_permissions.append(permission)
            if sid_type == 'GROUP' and (sid_name in user.groups or sid_name in user.roles):
                user_permissions.append(permission)
        return user_permissions

    def _group_permissions_by_path(self, permissions):
        permissions_dict = {}
        for permission in permissions:
            permission_type = permission.get('type', FOLDER)
            permission_path = (permission.get('path') + self._delimiter) if permission_type == FOLDER else permission.get('path')
            permission_group = permissions_dict[permission_path] = permissions_dict.get(permission_path, [])
            permission_group.append(permission)
        return permissions_dict

    def _get_permissions_sorted_by_sids(self, permissions):
        return sorted(permissions, key=lambda permission: permission.get('sid', {}).get('type', 'USER'))


class PermissionTree:

    def __init__(self, path, permissions=None, children=None):
        """
        Permission tree node.

        :param path: Absolute path.
        :param permissions: Permissions.
        :param children: Child permission tree nodes.
        """
        self.path = path
        self.permissions = permissions or []
        self.children = children or {}


class PermissionResolver:
    __metaclass__ = ABCMeta

    @abstractmethod
    def get(self, path, permissions):
        pass


class BasicPermissionResolver(PermissionResolver):

    def __init__(self, is_read_allowed=False, is_write_allowed=False, verbose=False):
        """
        Basic permission resolver.

        Resolves permissions for a specific path using provided permissions.

        :param is_read_allowed: Enables read access by default.
        :param is_write_allowed: Enables write access by default.
        :param verbose: Enables verbose logging.
        """
        self._is_read_allowed = is_read_allowed
        self._is_write_allowed = is_write_allowed
        self._verbose = verbose
        self._delimiter = '/'

    def get(self, path, permissions):
        path = path.lstrip(self._delimiter) or self._delimiter
        if self._verbose:
            logging.debug('Resolving permissions for %s...', path)

        read_permission = None
        write_permission = None

        path_permissions = permissions.get(path, [])
        if READ in path_permissions:
            read_permission = READ
        if SYNTHETIC_READ in path_permissions:
            read_permission = SYNTHETIC_READ
        if NO_READ in path_permissions:
            read_permission = NO_READ
        if WRITE in path_permissions:
            write_permission = WRITE
        if NO_WRITE in path_permissions:
            write_permission = NO_WRITE

        if self._verbose and read_permission:
            logging.debug('Resolved direct %s permission for %s', read_permission, path)
        if self._verbose and write_permission:
            logging.debug('Resolved direct %s permission for %s', write_permission, path)

        if not read_permission or not write_permission:
            for parent_path in reversed(list(get_parent_paths(path))):
                parent_path_permissions = permissions.get(parent_path + self._delimiter, [])
                if not read_permission:
                    if READ in parent_path_permissions:
                        read_permission = READ
                    if NO_READ in parent_path_permissions:
                        read_permission = NO_READ
                    if self._verbose and read_permission:
                        logging.debug('Resolved inherited %s permission for %s from %s',
                                      read_permission, path, parent_path)
                if not write_permission:
                    if WRITE in parent_path_permissions:
                        write_permission = WRITE
                    if NO_WRITE in parent_path_permissions:
                        write_permission = NO_WRITE
                    if self._verbose and write_permission:
                        logging.debug('Resolved inherited %s permission for %s from %s',
                                      write_permission, path, parent_path)
                if read_permission and write_permission:
                    break

        if self._verbose and not read_permission:
            read_permission = READ if self._is_read_allowed else NO_READ
            logging.debug('Resolved default %s permission for %s', read_permission, path)
        if self._verbose and not write_permission:
            write_permission = WRITE if self._is_write_allowed else NO_WRITE
            logging.debug('Resolved default %s permission for %s', write_permission, path)

        if self._verbose:
            logging.debug('Resolved effective %s+%s permissions for %s', read_permission, write_permission, path)
        return [read_permission, write_permission]


class PermissionManager:
    __metaclass__ = ABCMeta

    @abstractmethod
    def get_all(self):
        pass

    @abstractmethod
    def get(self, path):
        pass

    @abstractmethod
    def refresh(self):
        pass


class BasicPermissionManager(PermissionManager):

    def __init__(self, provider, resolver):
        """
        Basic permissions manager.

        Retrieves permissions from a provider and resolves permissions for specific paths using a resolver.

        :param provider: Permissions provider.
        :param resolver: Permissions resolver.
        """
        self._provider = provider
        self._resolver = resolver
        self._permissions = {}

    def get_all(self):
        return dict(self._permissions)

    def get(self, path):
        return self._resolver.get(path, self._permissions)

    def refresh(self):
        self._permissions = self._provider.get()


class ExplicitPermissionManager(PermissionManager):

    def __init__(self, inner, resolver):
        """
        Explicit permissions manager.

        Resolves implicit permissions for increased performance and further transparency.

        :param inner: Decorating permissions manager.
        :param resolver: Permissions resolver.
        """
        self._inner = inner
        self._resolver = resolver
        self._permissions = {}

    def get_all(self):
        return dict(self._permissions)

    def get(self, path):
        return self._resolver.get(path, self._permissions)

    def refresh(self):
        self._inner.refresh()
        logging.debug('Resolving implicit permissions...')
        permissions = self._inner.get_all()
        explicit_permissions = {}
        for permission_path in permissions.keys():
            explicit_permissions[permission_path] = self._resolver.get(permission_path, permissions)
        self._permissions = explicit_permissions


class ExplainingTreePermissionManager(PermissionManager):

    def __init__(self, inner):
        """
        Explaining tree permissions manager.

        Logs permissions tree for further transparency.

        :param inner: Decorating permissions manager.
        """
        self._inner = inner
        self._delimiter = '/'

    def get_all(self):
        return self._inner.get_all()

    def get(self, path):
        return self._inner.get(path)

    def refresh(self):
        self._inner.refresh()
        logging.debug('Explaining permissions...')
        self._explain_tree(self._build_tree(self._inner.get_all()))

    def _build_tree(self, permissions):
        permissions_tree = PermissionTree(path=self._delimiter, permissions=permissions.get(self._delimiter, []))
        for current_path, current_permissions in permissions.items():
            current_item_name = current_path.strip(self._delimiter).split(self._delimiter)[-1]
            if current_path.endswith(self._delimiter):
                current_item_name += self._delimiter
            if current_item_name == self._delimiter:
                continue
            current_node = permissions_tree
            for parent_path in list(get_parent_dirs(current_path))[1:]:
                if (parent_path + self._delimiter) not in current_node.children:
                    child_node = current_node.children[parent_path + self._delimiter] = PermissionTree(path=parent_path + self._delimiter)
                else:
                    child_node = current_node.children[parent_path + self._delimiter]
                current_node = child_node
            if current_item_name in current_node.children:
                existing_node = current_node.children[current_item_name]
                current_node.children[current_item_name] = PermissionTree(path=current_item_name,
                                                                          permissions=current_permissions,
                                                                          children=existing_node.children)
            else:
                current_node.children[current_item_name] = PermissionTree(path=current_item_name,
                                                                          permissions=current_permissions)
        return permissions_tree

    def _explain_tree(self, tree):
        self._explain_tree_recursively(tree)

    def _explain_tree_recursively(self, tree, depth=0):
        logging.debug('%s | %s%s',
                      ' : '.join(['{0: <14}'.format(permission) for permission in tree.permissions]),
                      '  ' * depth, tree.path)
        for subtree in tree.children.values():
            self._explain_tree_recursively(subtree, depth=depth + 1)


class CachingPermissionManager(PermissionManager):

    def __init__(self, inner, cache):
        """
        Caching permissions manager.

        Caches already resolved permissions in order to reduce a number of calls to an inner permissions manager.

        :param inner: Decorating permissions manager.
        :param cache: Cache implementation.
        """
        self._inner = inner
        self._cache = cache

    def get_all(self):
        return self._inner.get_all()

    def get(self, path):
        value = self._cache.get(path)
        if value:
            logging.debug('Cached permissions found for %s', path)
        else:
            logging.debug('Cached permissions not found for %s', path)
            value = self._inner.get(path)
            self._cache.set(path, value)
        return value

    def refresh(self):
        self._inner.refresh()
        logging.debug('Invalidating cached permissions...')
        self._cache.clear()


class RefreshingPermissionManager(PermissionManager):

    def __init__(self, inner, refresh_delay):
        """
        Refreshing permissions manager.

        Constantly refreshes an inner permissions manager.

        :param inner: Decorating permissions manager.
        :param refresh_delay: Delay between consequent refreshes in seconds.
        """
        self._inner = inner
        self._refresh_delay = refresh_delay
        self._daemon = None

    def get_all(self):
        return self._inner.get_all()

    def get(self, path):
        return self._inner.get(path)

    def refresh(self):
        logging.debug('Refreshing storage permissions...')
        self._inner.refresh()
        if not self._daemon:
            logging.debug('Submitting storage permissions refreshing daemon...')
            self._daemon = threading.Thread(name='RefreshPermissionThread', target=self._refresh)
            self._daemon.setDaemon(True)
            self._daemon.start()

    def _refresh(self):
        while True:
            time.sleep(self._refresh_delay)
            try:
                logging.debug('Refreshing storage permissions...')
                self._inner.refresh()
            except RuntimeError:
                logging.exception('Error occurred during storage permissions refreshing.')
