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

from pipefuse.fsclient import FileSystemClientDecorator
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
RECURSIVE_READ_MASK = 0b1000000
RECURSIVE_NO_READ_MASK = 0b10000000
RECURSIVE_WRITE_MASK = 0b100000000
RECURSIVE_NO_WRITE_MASK = 0b1000000000
SYNTHETIC_READ_MASK = 0b10000000000

INHERIT = 'INHERIT'
READ = 'READ'
NO_READ = 'NO_READ'
WRITE = 'WRITE'
NO_WRITE = 'NO_WRITE'
EXECUTE = 'EXECUTE'
NO_EXECUTE = 'NO_EXECUTE'
RECURSIVE_READ = 'RECURSIVE_READ'
NO_RECURSIVE_READ = 'NO_RECURSIVE_READ'
RECURSIVE_WRITE = 'RECURSIVE_WRITE'
NO_RECURSIVE_WRITE = 'NO_RECURSIVE_WRITE'
SYNTHETIC_READ = 'SYNTHETIC_READ'


class PermissionControlFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, read_manager):
        """
        Permission control file system client decorator.

        Filters file system tree and restricts access to certain resources using permissions from permissions manager.

        Does not translate permissions to fuse POSIX ACLs.

        :param inner: Decorating file system client.
        :param read_manager: Permissions read manager.
        """
        super(PermissionControlFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._read_manager = read_manager

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
        if not self._is_write_allowed(path):
            self._access_denied(path)
        self._inner.delete(path)

    def mv(self, old_path, path):
        if not self._is_recursive_read_write_allowed(old_path):
            self._access_denied(old_path)
        if not self._is_recursive_write_allowed(path):
            self._access_denied(path)
        self._inner.mv(old_path, path)

    def mkdir(self, path):
        if not self._is_write_allowed(path):
            self._access_denied(path)
        self._inner.mkdir(path)

    def rmdir(self, path):
        if not self._is_recursive_write_allowed(path):
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

    def truncate(self, fh, path, length):
        if not self._is_write_allowed(path):
            self._access_denied(path)
        self._inner.truncate(fh, path, length)

    def _is_read_allowed(self, path):
        permissions = self._read_manager.get(path)
        return READ in permissions

    def _is_write_allowed(self, path):
        permissions = self._read_manager.get(path)
        return WRITE in permissions

    def _is_read_write_allowed(self, path):
        permissions = self._read_manager.get(path)
        return READ in permissions and WRITE in permissions

    def _is_recursive_write_allowed(self, path):
        permissions = self._read_manager.get(path)
        return RECURSIVE_WRITE in permissions

    def _is_recursive_read_write_allowed(self, path):
        permissions = self._read_manager.get(path)
        return RECURSIVE_READ in permissions and RECURSIVE_WRITE in permissions

    def _is_synthetic_read_allowed(self, path):
        permissions = self._read_manager.get(path)
        return READ in permissions or SYNTHETIC_READ in permissions

    def _access_denied(self, path):
        self._access_denied_warning(path)
        raise FuseOSError(errno.EACCES)

    def _access_denied_warning(self, path):
        logging.debug('Access denied: %s.', path)


class PermissionManagementFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, write_manager):
        """
        Permission management file system client decorator.

        Updates permissions on certain file system operations.

        :param inner: Decorating file system client.
        :param write_manager: Permissions write manager.
        """
        super(PermissionManagementFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._write_manager = write_manager

    def delete(self, path):
        self._inner.delete(path)
        self._write_manager.delete_file(path)

    def mv(self, old_path, path):
        self._inner.mv(old_path, path)
        self._write_manager.move_folder(old_path, path)
        self._write_manager.move_file(old_path, path)

    def rmdir(self, path):
        self._inner.rmdir(path)
        self._write_manager.delete_folder(path)


class PermissionProvider:
    __metaclass__ = ABCMeta

    @abstractmethod
    def get(self):
        pass


class CloudPipelinePermissionProvider(PermissionProvider):

    def __init__(self, pipe, bucket, verbose=False):
        """
        Cloud Pipeline permissions provider.

        Provides Cloud Pipeline storage permissions.

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
                permission_parent_permissions = \
                    permissions[raw_permission_parent_path] = \
                    permissions.get(raw_permission_parent_path, [])
                if read_permission in [READ or SYNTHETIC_READ]:
                    if NO_READ in permission_parent_permissions:
                        permission_parent_permissions.remove(NO_READ)
                    if READ not in permission_parent_permissions and SYNTHETIC_READ not in permission_parent_permissions:
                        if self._verbose:
                            logging.debug('Resolved uplifted %s permission for %s',
                                          SYNTHETIC_READ, raw_permission_parent_path)
                        permission_parent_permissions.append(SYNTHETIC_READ)
                if read_permission in [NO_READ]:
                    if RECURSIVE_READ in permission_parent_permissions:
                        permission_parent_permissions.remove(RECURSIVE_READ)
                    if NO_RECURSIVE_READ not in permission_parent_permissions:
                        permission_parent_permissions.append(NO_RECURSIVE_READ)
                if write_permission in [NO_WRITE]:
                    if RECURSIVE_WRITE in permission_parent_permissions:
                        permission_parent_permissions.remove(RECURSIVE_WRITE)
                    if NO_RECURSIVE_WRITE not in permission_parent_permissions:
                        permission_parent_permissions.append(NO_RECURSIVE_WRITE)

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
            permission_path = permission.get('path')
            permission_group = permissions_dict[permission_path] = permissions_dict.get(permission_path, [])
            permission_group.append(permission)
        return permissions_dict

    def _get_permissions_sorted_by_sids(self, permissions):
        return sorted(permissions, key=lambda permission: permission.get('sid', {}).get('type', 'USER'))


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
        path = path.strip(self._delimiter)
        if self._verbose:
            logging.debug('Resolving permissions for %s...', path)

        read_permission = None
        write_permission = None
        recursive_read_permission = None
        recursive_write_permission = None

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
        if RECURSIVE_READ in path_permissions:
            recursive_read_permission = RECURSIVE_READ
        if NO_RECURSIVE_READ in path_permissions:
            recursive_read_permission = NO_RECURSIVE_READ
        if RECURSIVE_WRITE in path_permissions:
            recursive_write_permission = RECURSIVE_WRITE
        if NO_RECURSIVE_WRITE in path_permissions:
            recursive_write_permission = NO_RECURSIVE_WRITE

        if self._verbose and read_permission:
            logging.debug('Resolved direct %s permission for %s', read_permission, path)
        if self._verbose and write_permission:
            logging.debug('Resolved direct %s permission for %s', write_permission, path)
        if self._verbose and recursive_read_permission:
            logging.debug('Resolved direct %s permission for %s', recursive_read_permission, path)
        if self._verbose and recursive_write_permission:
            logging.debug('Resolved direct %s permission for %s', recursive_write_permission, path)

        if not read_permission or not write_permission:
            for parent_path in reversed(list(get_parent_paths(path))):
                parent_path_permissions = permissions.get(parent_path, [])
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
        if self._verbose and not recursive_read_permission:
            recursive_read_permission = RECURSIVE_READ if read_permission == READ else NO_RECURSIVE_READ
            logging.debug('Resolved default %s permission for %s', recursive_read_permission, path)
        if self._verbose and not recursive_write_permission:
            recursive_write_permission = RECURSIVE_WRITE if write_permission == WRITE else NO_RECURSIVE_WRITE
            logging.debug('Resolved default %s permission for %s', recursive_write_permission, path)

        if self._verbose:
            logging.debug('Resolved effective %s+%s+%s+%s permissions for %s',
                          read_permission, write_permission,
                          recursive_read_permission, recursive_write_permission,
                          path)
        return [read_permission, write_permission, NO_EXECUTE,
                recursive_read_permission, recursive_write_permission, NO_EXECUTE]


class PermissionReadManager:
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


class BasicPermissionReadManager(PermissionReadManager):

    def __init__(self, provider, resolver):
        """
        Basic permissions read manager.

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


class ExplicitPermissionReadManager(PermissionReadManager):

    def __init__(self, inner, resolver):
        """
        Explicit permissions read manager.

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


class ExplainingTreePermissionReadManager(PermissionReadManager):

    def __init__(self, inner):
        """
        Explaining tree permissions read manager.

        Logs permissions tree for further transparency.

        :param inner: Decorating permissions manager.
        """
        self._inner = inner
        self._delimiter = '/'
        self._acronyms = {
            READ: 'r',
            NO_READ: '-',
            WRITE: 'w',
            NO_WRITE: '-',
            EXECUTE: 'x',
            NO_EXECUTE: '-',
            RECURSIVE_READ: 'r',
            NO_RECURSIVE_READ: '-',
            RECURSIVE_WRITE: 'w',
            NO_RECURSIVE_WRITE: '-',
            SYNTHETIC_READ: '~'
        }

    def get_all(self):
        return self._inner.get_all()

    def get(self, path):
        return self._inner.get(path)

    def refresh(self):
        self._inner.refresh()
        logging.debug('Explaining permissions...')
        self._explain_tree(self._build_tree(self._inner.get_all()))

    def _build_tree(self, permissions):
        permissions_tree = PermissionTree(path='', permissions=permissions.get('', []))
        for current_path, current_permissions in permissions.items():
            current_item_name = current_path.split(self._delimiter)[-1]
            if current_item_name == '':
                continue
            current_node = permissions_tree
            for parent_path in list(get_parent_dirs(current_path))[1:]:
                if parent_path not in current_node.children:
                    child_node = current_node.children[parent_path] = PermissionTree(path=parent_path)
                else:
                    child_node = current_node.children[parent_path]
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
                      ''.join(self._acronyms[permission] for permission in tree.permissions),
                      '  ' * depth, tree.path)
        for subtree in tree.children.values():
            self._explain_tree_recursively(subtree, depth=depth + 1)


class CachingPermissionReadManager(PermissionReadManager):

    def __init__(self, inner, cache):
        """
        Caching permissions read manager.

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


class RefreshingPermissionReadManager(PermissionReadManager):

    def __init__(self, inner, refresh_delay):
        """
        Refreshing permissions read manager.

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


class PermissionWriteManager:
    __metaclass__ = ABCMeta

    @abstractmethod
    def delete_file(self, path):
        pass

    @abstractmethod
    def delete_folder(self, path):
        pass

    @abstractmethod
    def move_file(self, old_path, new_path):
        pass

    @abstractmethod
    def move_folder(self, old_path, new_path):
        pass


class CloudPipelinePermissionWriteManager(PermissionWriteManager):

    def __init__(self, pipe, bucket):
        """
        Cloud Pipeline permissions write manager.

        Updates storage permissions in Cloud Pipeline.

        :param pipe: Cloud Pipeline API client.
        :param bucket: Cloud Pipeline bucket object.
        """
        self._pipe = pipe
        self._bucket = bucket
        self._delimiter = '/'

    def delete_file(self, path):
        self._pipe.delete_all_storage_object_permissions(self._bucket, requests=[{
            'type': FILE,
            'path': path.strip(self._delimiter)
        }])

    def delete_folder(self, path):
        self._pipe.delete_all_storage_object_permissions(self._bucket, requests=[{
            'type': FOLDER,
            'path': path.strip(self._delimiter)
        }])

    def move_file(self, old_path, new_path):
        self._pipe.move_storage_object_permissions(self._bucket, requests=[{
            'source': {
                'type': FILE,
                'path': old_path.strip(self._delimiter)
            },
            'destination': {
                'type': FILE,
                'path': new_path.strip(self._delimiter)
            }
        }])

    def move_folder(self, old_path, new_path):
        self._pipe.move_storage_object_permissions(self._bucket, requests=[{
            'source': {
                'type': FOLDER,
                'path': old_path.strip(self._delimiter)
            },
            'destination': {
                'type': FOLDER,
                'path': new_path.strip(self._delimiter)
            }
        }])
