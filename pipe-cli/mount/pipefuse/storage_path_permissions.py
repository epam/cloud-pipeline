#  Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
import functools
import os.path
import time
from threading import Thread, RLock

import pygtrie
import logging

from pipefuse import fuseutils
from pipefuse.api import CloudPipelineClient, DataStorage
from pipefuse.chain import ChainingService
from pipefuse.fsclient import FileSystemClientDecorator, File, ForbiddenOperationException
from pipefuse.lock import synchronized


class StoragePathPermission:

    def __init__(self, folder_path, file_name, mask):
        self.folder_path = folder_path
        self.file_name = file_name
        self.mask = mask

    @staticmethod
    def from_dict(data):
        return StoragePathPermission(data.get("folderPath"), data.get("fileName"), data.get("mask"))


class PermissionsManager:

    def __init__(self, pipe, bucket_object, lock=None):
        self._root = '/'
        self._delimiter = '/'
        self._pipe = pipe
        self._bucket_object = bucket_object
        self._lock = lock or RLock()
        self._trie = None
        self.refresh_trie()

    @synchronized
    def refresh_trie(self):
        self._trie = pygtrie.CharTrie(self._permissions_to_dict(self._fetch_permissions()))

    @synchronized
    def get_trie(self):
        return self._trie

    def can_write_to_root(self):
        parent_folder_permissions = [p for p in self.get_closest_parent_permissions(self._root) if not p.file_name]
        if not parent_folder_permissions:
            raise ForbiddenOperationException()
        item = parent_folder_permissions[0]
        if not self._can_write(item.mask):
            raise ForbiddenOperationException()

    def can_write_to_path(self, path):
        path = path if str(path).startswith(self._delimiter) else self._delimiter + path
        is_folder = path.endswith(self._delimiter)
        closest_parent = self.get_closest_parent_permissions(path)
        if not closest_parent:
            raise ForbiddenOperationException()
        parent_permissions = [p for p in closest_parent]
        if not is_folder:
            file_name = os.path.basename(path)
            for permission in parent_permissions:
                if permission.file_name == file_name:
                    if not self._can_write(permission.mask):
                        raise ForbiddenOperationException()
                    return
        for permission in parent_permissions:
            if not permission.file_name:
                if not self._can_write(permission.mask):
                    raise ForbiddenOperationException()
                return
        raise ForbiddenOperationException()

    def check_path_access(self, path, item):
        path = path if str(path).startswith(self._delimiter) else self._delimiter + path
        item_mask = item.mask
        if item_mask is None:
            permission = self._find_item_permission(path, item)
            if permission:
                item_mask = permission.mask
            else:
                raise ForbiddenOperationException()
        if not self._can_write(item_mask):
            logging.debug("[%s] Item permission denied: %s" % (path, item))
            raise ForbiddenOperationException()

    def get_parent_permissions(self, path):  # -> int, dict[str: int]
        parent = self.closest_parent(path)
        if not parent:
            return None, {}
        parent_permissions = self.get_trie().get(parent)
        folder_mask = self._get_folder_mask(parent_permissions)
        file_masks = self._get_files_masks_by_name(parent_permissions, path)
        return folder_mask, file_masks

    @staticmethod
    def _can_write(mask):
        return mask & 4 != 0

    def _find_item_permission(self, path, item):
        closest_parent = self.get_closest_parent_permissions(path)
        if not closest_parent:
            return None
        parent_permissions = [p for p in closest_parent]
        for permission in parent_permissions:
            if permission.file_name == item.name:
                return permission
        for permission in parent_permissions:
            if not permission.file_name:
                return permission
        return None

    def get_closest_parent_permissions(self, path):  # -> [StoragePathPermission]
        parent = self.closest_parent(path)
        if not parent:
            return None
        return self.get_trie().get(parent)

    def get_children_with_depth_permissions(self, path, depth=1):  # -> dict[str: StoragePathPermission]
        """
        Returns {prepared <folder path>: <folder permission object>}
        """
        results = {}
        children_paths = self.get_children_for_path(path)
        if not children_paths:
            return results
        for child_permission_path in children_paths:
            key_path = None
            if child_permission_path.startswith(path):
                key_path = child_permission_path[len(path):]
                key_path = key_path.lstrip(self._root)
            if not key_path:
                continue
            parts = key_path.split(self._root)
            if 0 < depth < len(parts) - 1:
                key_path = self._root.join(parts[0:depth]) + self._root
            permissions = self.get_trie().get(child_permission_path)
            folder_permissions = [p for p in permissions if p.file_name is None]
            if folder_permissions:
                results.update({key_path: folder_permissions[0]})
                continue
            results.update({key_path: StoragePathPermission(None, None, None)})
        return results

    def closest_parent(self, prefix):
        return self.get_trie().longest_prefix(prefix)[0]

    def get_children_for_path(self, path):  # -> [str]
        try:
            return self.get_trie().keys(prefix=path, shallow=False)
        except KeyError:
            return []

    @staticmethod
    def _get_folder_mask(permissions):  # -> int | None
        if not permissions:
            return None
        for p in permissions:
            if not p.file_name:
                return p.mask
        return None

    @staticmethod
    def _get_files_masks_by_name(permissions, path):  # -> dict[str: int]
        file_masks = {}
        for p in permissions:
            if p.file_name and p.folder_path == path:
                file_masks.update({p.file_name: p.mask})
        return file_masks

    @staticmethod
    def _permissions_to_dict(permissions):
        _dict = {}
        for p in permissions:
            if p.folder_path not in _dict:
                _dict[p.folder_path] = []
            _dict.get(p.folder_path).append(p)
        return _dict

    def _fetch_permissions(self):
        return [StoragePathPermission.from_dict(data)
                for data in self._pipe.get_storage_path_permissions(self._bucket_object.id)]


class StoragePathPermissionsFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, permissions_manager):
        super(StoragePathPermissionsFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._manager = permissions_manager

    def mkdir(self, path):
        logging.debug("[%s] Checking access before folder creation" % path)
        self._manager.can_write_to_path(path)
        logging.debug("[%s] Access to create folder granted" % path)
        self._inner.mkdir(path)

    def mv(self, old, new):
        logging.debug("[%s] Checking access before creation" % new)
        self._manager.can_write_to_path(new)
        logging.debug("[%s] Access to create granted" % new)
        file_item = self._inner.attrs(old)
        if file_item:
            self._can_write(old)
        else:
            folder_path = old if str(old).endswith('/') else '/' + old
            folder_listing = self.ls(folder_path, depth=-1)
            logging.debug("[%s] Folder listing to move: %s" % (old, folder_listing))
            for item in folder_listing:
                item_path = os.path.join(old, item.name)
                logging.debug("[%s] Checking write permissions..." % item_path)
                self._manager.check_path_access(item_path, item)
                logging.debug("[%s] Access to delete granted" % item_path)
        self._inner.mv(old, new)

    def rmdir(self, path):
        path = fuseutils.append_delimiter(path)
        logging.debug("[%s] rmdir permissions checking:" % path)
        for item in self.ls(fuseutils.append_delimiter(path), depth=-1):
            self.delete(item.name)

    def delete(self, path):
        self._can_write(path)
        logging.debug("[%s] Write permission granted. Deleting file." % path)
        self._inner.delete(path)

    def ls(self, path, depth=1):
        listing = self._inner.ls(path, depth)
        if not listing:
            return listing
        folder_mask, files_masks = self._manager.get_parent_permissions(path)

        if folder_mask:
            logging.debug("[%s] Has permissions to list full folder" % path)
            return self._add_masks(listing, folder_mask, files_masks)

        children_permissions = self._manager.get_children_with_depth_permissions(path, depth)
        if not files_masks and not children_permissions:
            logging.debug("[%s] Has no permissions to list." % path)
            return []

        logging.debug("[%s] Has permissions on files %s and folders %s"
                      % (path, files_masks.keys(), children_permissions.keys()))
        filtered_listing = []
        for item in listing:
            folder_permission = children_permissions.get(item.name)
            if item.is_dir:
                if folder_permission:
                    filtered_listing.append(self._item(item, folder_permission.mask))
                else:
                    logging.debug("[%s] Filtering out folder '%s' since no permissions found." % (path, item.name))
                continue
            if files_masks and item.name in files_masks:
                filtered_listing.append(self._item(item, files_masks.get(item.name)))
                continue
            logging.debug("[%s] Filtering out file '%s' since no permissions found." % (path, item.name))
        logging.debug("[%s] Result listing: %s" % (path, filtered_listing))
        return filtered_listing

    def _can_write(self, path):
        item = self._inner.attrs(path)
        logging.debug("[%s] Checking write permissions..." % path)
        self._manager.check_path_access(path, item)

    def _add_masks(self, listing, folder_mask, files_masks):
        new_listing = []
        for item in listing:
            mask = folder_mask
            if item.name in files_masks:
                mask = files_masks.get(item.name)
            new_listing.append(self._item(item, mask))
        return new_listing

    @staticmethod
    def _item(item, mask):
        return File(name=item.name,
                    size=item.size,
                    mtime=item.mtime,
                    ctime=item.ctime,
                    contenttype=item.contenttype,
                    is_dir=item.is_dir,
                    storage_class=item.storage_class,
                    mask=mask)


class StoragePathWritePermissionsFilterFS(ChainingService):

    def __init__(self, inner, permissions_manager, client):
        self._inner = inner
        self._root = '/'
        self._permissions_manager = permissions_manager
        self._client = client

    def __getattr__(self, name):
        if not hasattr(self._inner, name):
            return None
        attr = getattr(self._inner, name)
        if not callable(attr):
            return attr
        return self._wrap(attr, name=name)

    def __call__(self, name, *args, **kwargs):
        if not hasattr(self._inner, name):
            return getattr(self, name)(*args, **kwargs)
        attr = getattr(self._inner, name)
        return self._wrap(attr, name=name)(*args, **kwargs)

    def _wrap(self, attr, name=None):
        @functools.wraps(attr)
        def _wrapped_attr(*args, **kwargs):
            method_name = name
            if method_name == 'access':
                path = args[0]
                mode = args[1]
                self._check_access(path, mode, method_name)
                return attr(*args, **kwargs)
            elif method_name == 'create':
                path = args[0] or kwargs.get('path')
                self._permissions_manager.can_write_to_path(path)
                return attr(*args, **kwargs)
            elif method_name == 'open':
                path = args[0] or kwargs.get('path')
                flags = args[1] or kwargs.get('flags')
                if flags & os.O_CREAT:
                    self._permissions_manager.can_write_to_path(path)
                if (flags & os.O_WRONLY) or (flags & os.O_RDWR) or (flags & os.O_APPEND) or (flags & os.O_TRUNC):
                    self._check_access(path, os.W_OK, method_name)
                return attr(*args, **kwargs)
            else:
                return attr(*args, **kwargs)

        return _wrapped_attr

    def _check_access(self, path, mode, method):
        if path == self._root:
            if self._permissions_manager and mode & os.W_OK:
                logging.debug("[%s] Request root write permissions to %s: %s" % (path, method, mode))
                self._permissions_manager.can_write_to_root()
            return
        if self._permissions_manager is None:
            logging.debug("[%s] Permissions check not required to %s: %s" % (path, method, mode))
            return
        item = self._client.attrs(path)
        logging.debug("[%s] Loading permissions for item to %s: %s" % (path, method, item))
        if not item:
            return
        if mode & os.W_OK:
            logging.debug("[%s] Request write permissions to %s: %s" % (path, method, mode))
            self._permissions_manager.check_path_access(path, item)


class StoragePathPermissionsRefresherDaemon:

    def __init__(self, permissions_manager, delay):
        self._manager = permissions_manager
        self._polling_timeout = delay
        self._thread = Thread(name='StoragePathPermissionsRefresher', target=self.run)
        self._thread.daemon = True

    def start(self):
        self._thread.start()

    def join(self, timeout=None):
        logging.info('Closing storage path permissions refresher daemon...')
        self._thread.join(timeout=timeout)

    def run(self):
        logging.info('Initiating storage path permissions refresher daemon...')
        while True:
            time.sleep(self._polling_timeout)
            try:
                logging.debug('Refreshing storage path permissions...')
                self._manager.refresh_trie()
                logging.debug('Storage path permissions refreshed.')
            except KeyboardInterrupt:
                logging.warning('Interrupted.')
                raise
            except Exception:
                logging.warning('Storage path permissions refresh daemon iteration has failed.', exc_info=True)
