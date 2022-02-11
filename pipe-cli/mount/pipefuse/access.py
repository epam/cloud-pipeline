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

USER = 'USER'
GROUP = 'GROUP'

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
NO_RECURSIVE_READ_MASK = 0b10000000
RECURSIVE_WRITE_MASK = 0b100000000
NO_RECURSIVE_WRITE_MASK = 0b1000000000
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
        permission = self._read_manager.get(path)
        return permission & READ_MASK == READ_MASK

    def _is_write_allowed(self, path):
        permission = self._read_manager.get(path)
        return permission & WRITE_MASK == WRITE_MASK

    def _is_read_write_allowed(self, path):
        permission = self._read_manager.get(path)
        return permission & READ_MASK == READ_MASK and permission & WRITE_MASK == WRITE_MASK

    def _is_recursive_write_allowed(self, path):
        permission = self._read_manager.get(path)
        return permission & RECURSIVE_WRITE_MASK == RECURSIVE_WRITE_MASK

    def _is_recursive_read_write_allowed(self, path):
        permission = self._read_manager.get(path)
        return permission & RECURSIVE_READ_MASK == RECURSIVE_READ_MASK \
               and permission & RECURSIVE_WRITE_MASK == RECURSIVE_WRITE_MASK

    def _is_synthetic_read_allowed(self, path):
        permission = self._read_manager.get(path)
        return permission & READ_MASK == READ_MASK \
               or permission & SYNTHETIC_READ_MASK == SYNTHETIC_READ_MASK

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
        permissions = {}
        raw_permissions = self._get_raw_permissions()
        for permission_path in sorted(raw_permissions.keys()):
            raw_permission = self._merge_raw_permissions(raw_permissions[permission_path])
            if self._verbose:
                logging.debug('Resolved raw %s+%s permissions for %s',
                              self._get_read_permission_name(raw_permission),
                              self._get_write_permission_name(raw_permission),
                              permission_path)

            permission = permissions.get(permission_path, INHERIT_MASK)
            if raw_permission & READ_MASK == READ_MASK:
                permission |= READ_MASK
                permission &= ~NO_READ_MASK
                permission &= ~SYNTHETIC_READ_MASK
            if raw_permission & NO_READ_MASK == NO_READ_MASK:
                if permission & READ_MASK != READ_MASK and \
                        permission & SYNTHETIC_READ_MASK != SYNTHETIC_READ_MASK:
                    permission |= NO_READ_MASK
            if raw_permission & WRITE_MASK == WRITE_MASK:
                permission |= WRITE_MASK
                permission &= ~NO_WRITE_MASK
            if raw_permission & NO_WRITE_MASK == NO_WRITE_MASK:
                if permission & WRITE_MASK != WRITE_MASK:
                    permission |= NO_WRITE_MASK
            if self._verbose:
                logging.debug('Resolved effective %s+%s permissions for %s',
                              self._get_read_permission_name(permission),
                              self._get_write_permission_name(permission),
                              permission_path)

            for parent_path in get_parent_paths(permission_path):
                parent_permission = permissions.get(parent_path, INHERIT_MASK)
                if permission & READ_MASK != READ_MASK \
                        and permission & NO_READ_MASK != NO_READ_MASK \
                        and parent_permission & READ_MASK == READ_MASK:
                    permission &= ~SYNTHETIC_READ_MASK
                    permission |= READ_MASK
                if permission & READ_MASK == READ_MASK \
                        or permission & SYNTHETIC_READ_MASK == SYNTHETIC_READ_MASK:
                    parent_permission &= ~NO_READ_MASK
                    if parent_permission & READ_MASK != READ_MASK \
                            and parent_permission & SYNTHETIC_READ_MASK != SYNTHETIC_READ_MASK:
                        if self._verbose:
                            logging.debug('Resolved uplifted %s permission for %s',
                                          SYNTHETIC_READ, parent_path)
                        parent_permission |= SYNTHETIC_READ_MASK
                if permission & NO_READ_MASK == NO_READ_MASK:
                    parent_permission &= ~RECURSIVE_READ_MASK
                    parent_permission |= NO_RECURSIVE_READ_MASK
                if permission & NO_WRITE_MASK == NO_WRITE_MASK:
                    parent_permission &= ~RECURSIVE_WRITE_MASK
                    parent_permission |= NO_RECURSIVE_WRITE_MASK
                permissions[parent_path] = parent_permission

            permissions[permission_path] = permission

        return permissions

    def _get_raw_permissions(self):
        logging.debug('Retrieving Cloud Pipeline storage permissions...')
        all_raw_permissions = self._get_raw_user_permissions()
        logging.debug('Resolving Cloud Pipeline storage permissions...')
        return self._group_raw_permissions_by_path(all_raw_permissions)

    def _get_raw_user_permissions(self):
        user = self._pipe.get_user()
        if user.is_admin:
            return [{'type': FOLDER, 'path': '', 'mask': (READ_MASK | WRITE_MASK)}]
        permissions = self._pipe.get_storage_permissions(self._bucket)
        user_permissions = []
        for permission in permissions:
            sid = permission.get('sid', {})
            sid_name = sid.get('name', '')
            sid_type = sid.get('type', USER)
            if sid_type == USER and sid_name == user.name:
                user_permissions.append(permission)
            if sid_type == GROUP and (sid_name in user.groups or sid_name in user.roles):
                user_permissions.append(permission)
        return user_permissions

    def _group_raw_permissions_by_path(self, permissions):
        permissions_dict = {}
        for permission in permissions:
            permission_path = permission.get('path')
            permission_group = permissions_dict[permission_path] = permissions_dict.get(permission_path, [])
            permission_group.append(permission)
        return permissions_dict

    def _merge_raw_permissions(self, permissions):
        permission = INHERIT_MASK
        for sid_permission_object in self._sort_raw_permissions_by_sid(permissions):
            sid_type = sid_permission_object.get('sid').get('type', USER)
            sid_permission = sid_permission_object.get('mask', INHERIT_MASK)
            permission = self._merge_user_permissions(permission, sid_permission) if sid_type == USER \
                else self._merge_group_permissions(permission, sid_permission)
        return permission

    def _merge_user_permissions(self, permission, merging_permission):
        if merging_permission & READ_MASK == READ_MASK:
            permission &= ~NO_READ_MASK
            permission |= READ_MASK
        if merging_permission & NO_READ_MASK == NO_READ_MASK:
            permission &= ~READ_MASK
            permission |= NO_READ_MASK
        if merging_permission & WRITE_MASK == WRITE_MASK:
            permission &= ~NO_WRITE_MASK
            permission |= WRITE_MASK
        if merging_permission & NO_WRITE_MASK == NO_WRITE_MASK:
            permission &= ~WRITE_MASK
            permission |= NO_WRITE_MASK
        return permission

    def _merge_group_permissions(self, permission, merging_permission):
        if merging_permission & READ_MASK == READ_MASK:
            permission &= ~NO_READ_MASK
            permission |= READ_MASK
        if merging_permission & NO_READ_MASK == NO_READ_MASK \
                and permission & READ_MASK != READ_MASK \
                and permission & NO_READ_MASK != NO_READ_MASK:
            permission &= ~READ_MASK
            permission |= NO_READ_MASK
        if merging_permission & WRITE_MASK == WRITE_MASK:
            permission &= ~NO_WRITE_MASK
            permission |= WRITE_MASK
        if merging_permission & NO_WRITE_MASK == NO_WRITE_MASK \
                and permission & WRITE_MASK != WRITE_MASK \
                and permission & NO_WRITE_MASK != NO_WRITE_MASK:
            permission &= ~WRITE_MASK
            permission |= NO_WRITE_MASK
        return permission

    def _sort_raw_permissions_by_sid(self, permissions):
        return reversed(sorted(permissions, key=lambda permission: permission.get('sid', {}).get('type', USER)))

    def _get_read_permission_name(self, permission):
        return READ if permission & READ_MASK == READ_MASK \
            else SYNTHETIC_READ if permission & SYNTHETIC_READ_MASK == SYNTHETIC_READ_MASK \
            else NO_READ if permission & NO_READ_MASK == NO_READ_MASK \
            else INHERIT

    def _get_write_permission_name(self, current_permission):
        return WRITE if current_permission & WRITE_MASK == WRITE_MASK \
            else NO_WRITE if current_permission & NO_WRITE_MASK == NO_WRITE_MASK \
            else INHERIT


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

        permission = permissions.get(path, INHERIT_MASK)

        if self._verbose and self._is_read_set(permission):
            logging.debug('Resolved direct %s permission for %s',
                          self._get_read_permission_name(permission), path)
        if self._verbose and self._is_write_set(permission):
            logging.debug('Resolved direct %s permission for %s',
                          self._get_write_permission_name(permission), path)
        if self._verbose and self._is_recursive_read_set(permission):
            logging.debug('Resolved direct %s permission for %s',
                          self._get_recursive_read_permission_name(permission), path)
        if self._verbose and self._is_recursive_write_set(permission):
            logging.debug('Resolved direct %s permission for %s',
                          self._get_recursive_write_permission_name(permission), path)

        if self._is_read_not_set(permission) or self._is_write_not_set(permission):
            for parent_path in reversed(list(get_parent_paths(path))):
                parent_permission = permissions.get(parent_path, INHERIT_MASK)
                if self._is_read_not_set(permission):
                    permission |= parent_permission & READ_MASK
                    permission |= parent_permission & NO_READ_MASK
                    if self._verbose and self._is_read_set(permission):
                        logging.debug('Resolved inherited %s permission for %s from %s',
                                      self._get_read_permission_name(permission), path, parent_path)
                if self._is_write_not_set(permission):
                    permission |= parent_permission & WRITE_MASK
                    permission |= parent_permission & NO_WRITE_MASK
                    if self._verbose and self._is_write_set(permission):
                        logging.debug('Resolved inherited %s permission for %s from %s',
                                      self._get_write_permission_name(permission), path, parent_path)
                if self._is_read_set(permission) and self._is_write_set(permission):
                    break

        if self._verbose and self._is_read_not_set(permission):
            permission |= READ_MASK if self._is_read_allowed else NO_READ_MASK
            logging.debug('Resolved default %s permission for %s',
                          self._get_read_permission_name(permission), path)
        if self._verbose and self._is_write_not_set(permission):
            permission |= WRITE_MASK if self._is_write_allowed else NO_WRITE_MASK
            logging.debug('Resolved default %s permission for %s',
                          self._get_write_permission_name(permission), path)
        if self._verbose and self._is_recursive_read_not_set(permission):
            permission |= RECURSIVE_READ_MASK if permission & READ_MASK == READ_MASK else NO_RECURSIVE_READ_MASK
            logging.debug('Resolved default %s permission for %s',
                          self._get_recursive_read_permission_name(permission), path)
        if self._verbose and self._is_recursive_write_not_set(permission):
            permission |= RECURSIVE_WRITE_MASK if permission & WRITE_MASK == WRITE_MASK else NO_RECURSIVE_WRITE_MASK
            logging.debug('Resolved default %s permission for %s',
                          self._get_recursive_write_permission_name(permission), path)

        if self._verbose:
            logging.debug('Resolved effective %s+%s+%s+%s permissions for %s',
                          self._get_read_permission_name(permission),
                          self._get_write_permission_name(permission),
                          self._get_recursive_read_permission_name(permission),
                          self._get_recursive_write_permission_name(permission),
                          path)
        return permission

    def _is_read_set(self, permission):
        return permission & READ_MASK == READ_MASK \
               or permission & NO_READ_MASK == NO_READ_MASK \
               or permission & SYNTHETIC_READ_MASK == SYNTHETIC_READ_MASK

    def _is_read_not_set(self, permission):
        return permission & READ_MASK != READ_MASK \
               and permission & NO_READ_MASK != NO_READ_MASK \
               and permission & SYNTHETIC_READ_MASK != SYNTHETIC_READ_MASK

    def _is_write_set(self, permission):
        return permission & WRITE_MASK == WRITE_MASK \
               or permission & NO_WRITE_MASK == NO_WRITE_MASK

    def _is_write_not_set(self, permission):
        return permission & WRITE_MASK != WRITE_MASK \
               and permission & NO_WRITE_MASK != NO_WRITE_MASK

    def _is_recursive_read_set(self, permission):
        return permission & RECURSIVE_READ_MASK == RECURSIVE_READ_MASK \
               or permission & NO_RECURSIVE_READ_MASK == NO_RECURSIVE_READ_MASK

    def _is_recursive_read_not_set(self, permission):
        return permission & RECURSIVE_READ_MASK != RECURSIVE_READ_MASK \
               and permission & NO_RECURSIVE_READ_MASK != NO_RECURSIVE_READ_MASK

    def _is_recursive_write_set(self, permission):
        return permission & RECURSIVE_WRITE_MASK == RECURSIVE_WRITE_MASK \
               or permission & NO_RECURSIVE_WRITE_MASK == NO_RECURSIVE_WRITE_MASK

    def _is_recursive_write_not_set(self, permission):
        return permission & RECURSIVE_WRITE_MASK != RECURSIVE_WRITE_MASK \
               and permission & NO_RECURSIVE_WRITE_MASK != NO_RECURSIVE_WRITE_MASK

    def _get_read_permission_name(self, permission):
        return READ if permission & READ_MASK == READ_MASK \
            else SYNTHETIC_READ if permission & SYNTHETIC_READ_MASK == SYNTHETIC_READ_MASK \
            else NO_READ

    def _get_write_permission_name(self, permission):
        return WRITE if permission & WRITE_MASK == WRITE_MASK else NO_WRITE

    def _get_recursive_read_permission_name(self, permission):
        return RECURSIVE_READ if permission & RECURSIVE_READ_MASK == RECURSIVE_READ_MASK else NO_RECURSIVE_READ

    def _get_recursive_write_permission_name(self, permission):
        return RECURSIVE_WRITE if permission & RECURSIVE_WRITE_MASK == RECURSIVE_WRITE_MASK else NO_RECURSIVE_WRITE


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

    def __init__(self, path, permission=INHERIT_MASK, children=None):
        """
        Permission tree node.

        :param path: Node path.
        :param permission: Node permission.
        :param children: Child nodes.
        """
        self.path = path
        self.permission = permission
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

    def get_all(self):
        return self._inner.get_all()

    def get(self, path):
        return self._inner.get(path)

    def refresh(self):
        self._inner.refresh()
        logging.debug('Explaining permissions...')
        self._explain_tree(self._build_tree(self._inner.get_all()))

    def _build_tree(self, permissions):
        permissions_tree = PermissionTree(path='', permission=permissions.get('', INHERIT_MASK))
        for current_path, current_permission in permissions.items():
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
                                                                          permission=current_permission,
                                                                          children=existing_node.children)
            else:
                current_node.children[current_item_name] = PermissionTree(path=current_item_name,
                                                                          permission=current_permission)
        return permissions_tree

    def _explain_tree(self, tree):
        self._explain_tree_recursively(tree)

    def _explain_tree_recursively(self, tree, depth=0):
        logging.debug('%s%s- %s%s- | %s%s',
                      'r' if tree.permission & READ_MASK == READ_MASK else
                      '-' if tree.permission & NO_READ_MASK == NO_READ_MASK else
                      '~' if tree.permission & SYNTHETIC_READ_MASK == SYNTHETIC_READ_MASK else
                      '?',
                      'w' if tree.permission & WRITE_MASK == WRITE_MASK else
                      '-' if tree.permission & NO_WRITE_MASK == NO_WRITE_MASK else
                      '?',
                      'r' if tree.permission & RECURSIVE_READ_MASK == RECURSIVE_READ_MASK else
                      '-' if tree.permission & NO_RECURSIVE_READ_MASK == NO_RECURSIVE_READ_MASK else
                      '?',
                      'w' if tree.permission & RECURSIVE_WRITE_MASK == RECURSIVE_WRITE_MASK else
                      '-' if tree.permission & NO_RECURSIVE_WRITE_MASK == NO_RECURSIVE_WRITE_MASK else
                      '?',
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
