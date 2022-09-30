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

import functools
import logging
from threading import RLock

from pipefuse.chain import ChainingService
from pipefuse.fsclient import FileSystemClientDecorator, UnsupportedOperationException
from pipefuse.lock import synchronized


class ExtendedAttributesCache:

    def __init__(self, cache):
        """
        Extended attributes cache.

        :param cache: Cache implementation.
        """
        self._cache = cache

    def get(self, path):
        xattrs = self._cache.get(path, None)
        if xattrs:
            return dict(xattrs)

    def set(self, path, xattrs):
        self._cache[path] = xattrs

    def invalidate(self, path):
        logging.info('Invalidating extended attributes cache for %s' % path)
        self._cache.pop(path, None)


class ThreadSafeExtendedAttributesCache:

    def __init__(self, inner, lock=None):
        """
        Thread safe extended attributes cache.

        :param inner: Not thread safe extended attributes cache.
        :param lock: Reentrant lock.
        """
        self._inner = inner
        self._lock = lock or RLock()

    @synchronized
    def get(self, path):
        return self._inner.get(path)

    @synchronized
    def set(self, path, xattrs):
        self._inner.set(path, xattrs)

    @synchronized
    def invalidate(self, path):
        self._inner.invalidate(path)


class ExtendedAttributesCachingFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, cache):
        """
        Extended attributes caching file system client decorator.

        It caches extended attribute calls to reduce a number of calls to an inner file system client.

        :param inner: Decorating file system client.
        :param cache: Extended attributes cache.
        """
        super(ExtendedAttributesCachingFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._cache = cache

    def download_xattrs(self, path):
        logging.info('Getting extended attributes for %s...' % path)
        xattrs = self._cache.get(path)
        if xattrs is not None:
            logging.info('Cached extended attributes found for %s' % path)
        else:
            logging.info('Cached extended attributes not found for %s' % path)
            xattrs = self._uncached_download_xattrs(path)
        return xattrs

    def _uncached_download_xattrs(self, path):
        logging.info('Downloading extended attributes for %s...' % path)
        xattrs = self._inner.download_xattrs(path) or {}
        self._cache.set(path, xattrs)
        return xattrs

    def upload_xattrs(self, path, xattrs):
        self._inner.upload_xattrs(path, xattrs)
        self._cache.invalidate(path)

    def upload_xattr(self, path, name, value):
        self._inner.upload_xattr(path, name, value)
        self._cache.invalidate(path)

    def remove_xattrs(self, path):
        self._inner.remove_xattrs(path)
        self._cache.invalidate(path)

    def remove_xattr(self, path, name):
        self._inner.remove_xattr(path, name)
        self._cache.invalidate(path)


class RestrictingExtendedAttributesFS(ChainingService):

    def __init__(self, inner, include_prefixes=None, exclude_prefixes=None):
        """
        Restricting extended attributes File System.

        It allows only certain extended attributes processing.

        :param inner: Decorating file system.
        :param include_prefixes: Including extended attribute prefixes.
        :param exclude_prefixes: Excluding extended attribute prefixes.
        """
        self._inner = inner
        self._include_prefixes = include_prefixes or []
        self._exclude_prefixes = exclude_prefixes or []
        self._operations = ['setxattr', 'getxattr', 'removexattr']

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
            method_name = name or args[0]
            if method_name in self._operations:
                xattr_name = kwargs.get('name') or args[1]
                if self._include_prefixes:
                    if not any(xattr_name.startswith(prefix) for prefix in self._include_prefixes):
                        logging.debug('Aborting unincluded extended attribute %s processing...', xattr_name)
                        raise UnsupportedOperationException()
                if self._exclude_prefixes:
                    if any(xattr_name.startswith(prefix) for prefix in self._exclude_prefixes):
                        logging.debug('Aborting excluded extended attribute %s processing...', xattr_name)
                        raise UnsupportedOperationException()
                return attr(*args, **kwargs)
            elif method_name == 'listxattr':
                xattrs = attr(*args, **kwargs) or []
                filtered_xattrs = []
                for xattr_name in xattrs:
                    if self._include_prefixes and not any(xattr_name.startswith(prefix)
                                                          for prefix in self._include_prefixes):
                        logging.debug('Filtering out unincluded extended attribute %s...', xattr_name)
                        continue
                    if self._exclude_prefixes and any(xattr_name.startswith(prefix)
                                                      for prefix in self._exclude_prefixes):
                        logging.debug('Filtering out excluded extended attribute %s...', xattr_name)
                        continue
                    filtered_xattrs.append(xattr_name)
                return filtered_xattrs
            else:
                return attr(*args, **kwargs)
        return _wrapped_attr

    def parameters(self):
        params = {}
        if self._include_prefixes:
            params['include'] = ','.join(self._include_prefixes)
        if self._exclude_prefixes:
            params['exclude'] = ','.join(self._exclude_prefixes)
        return params
