# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import time

from datetime import datetime
from threading import RLock

import pytz

from fsclient import File, FileSystemClientDecorator
import fuseutils


_ANY_ERROR = BaseException


class ListingCache:

    def __init__(self, cache):
        """
        Listing cache.

        :param cache: Cache implementation.
        """
        self._cache = cache
        self._delimiter = '/'

    def get(self, path):
        listing = self._cache.get(path, None)
        if listing:
            return dict(listing)

    def set(self, path, listing):
        self._cache[path] = listing

    def replace_in_parent_cache(self, path, item=None):
        if item:
            self._add_to_parent_cache(path, item)
        else:
            self._remove_from_parent_cache(path)

    def _add_to_parent_cache(self, path, item):
        logging.info('Adding to parent cache for %s' % path)
        parent_path, _ = fuseutils.split_path(path)
        parent_listing = self._cache.get(parent_path, None)
        if parent_listing:
            parent_listing[fuseutils.without_prefix(path, parent_path)] = item

    def _remove_from_parent_cache(self, path):
        logging.info('Removing from parent cache for %s' % path)
        parent_path, _ = fuseutils.split_path(path)
        parent_listing = self._cache.get(parent_path, None)
        if parent_listing:
            return parent_listing.pop(fuseutils.without_prefix(path, parent_path), None)

    def invalidate_parent_cache(self, path):
        parent_path, _ = fuseutils.split_path(path)
        self.invalidate_cache(parent_path)

    def invalidate_cache_recursively(self, path):
        for cache_path in self._cache.keys():
            if self._is_relative(cache_path, path):
                self.invalidate_cache(cache_path)

    def invalidate_cache(self, path):
        logging.info('Invalidating cache for %s' % path)
        self._cache.pop(path, None)

    def move_from_parent_cache(self, old_path, path):
        logging.info('Moving from parent cache %s to %s' % (old_path, path))
        cached_item = self._remove_from_parent_cache(old_path)
        parent_path, _ = fuseutils.split_path(path)
        parent_listing = self._cache.get(parent_path, None)
        if cached_item and parent_listing:
            relative_name = fuseutils.without_prefix(path, parent_path)
            parent_listing[relative_name] = cached_item._replace(name=relative_name)
        else:
            logging.info('Moving from parent cache %s to %s was not successful. '
                         'Invalidating both parent caches.' % (old_path, path))
            self.invalidate_parent_cache(old_path)
            self.invalidate_parent_cache(path)

    def _is_relative(self, cache_path, path):
        if cache_path.startswith(path):
            relative_path = fuseutils.without_prefix(cache_path, path)
            return not relative_path or relative_path.startswith(self._delimiter)
        return False


def synchronized(func):
    def wrapper(*args, **kwargs):
        lock = args[0]._lock
        try:
            lock.acquire()
            return_value = func(*args, **kwargs)
            return return_value
        finally:
            lock.release()
    return wrapper


class ThreadSafeListingCache:

    def __init__(self, inner):
        """
        Thread safe listing cache.

        :param inner: Not thread safe listing cache.
        """
        self._inner = inner
        self._lock = RLock()

    @synchronized
    def get(self, path):
        return self._inner.get(path)

    @synchronized
    def set(self, path, listing):
        self._inner.set(path, listing)

    @synchronized
    def replace_in_parent_cache(self, path, item=None):
        return self._inner.replace_in_parent_cache(path, item)

    @synchronized
    def invalidate_parent_cache(self, path):
        self._inner.invalidate_parent_cache(path)

    @synchronized
    def invalidate_cache_recursively(self, path):
        self._inner.invalidate_cache_recursively(path)

    @synchronized
    def move_from_parent_cache(self, old_path, path):
        self._inner.move_from_parent_cache(old_path, path)

    @synchronized
    def invalidate_cache(self, path):
        self._inner.invalidate_cache(path)


class CachingFileSystemClient(FileSystemClientDecorator):

    def __init__(self, inner, cache):
        """
        Caching file system client decorator.

        It caches listing calls to reduce number of calls to an inner file system client.

        :param inner: Decorating file system client.
        :param cache: Listing cache.
        """
        super(CachingFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._cache = cache
        self._delimiter = '/'

    def exists(self, path):
        return self.attrs(path) is not None

    def attrs(self, path):
        logging.info('Getting attributes for %s' % path)
        parent_path, file_name = fuseutils.split_path(path)
        if not file_name:
            return self._root()
        else:
            parent_listing = self._ls_as_dict(parent_path)
            if parent_listing:
                file = self._find_in_listing(parent_listing, file_name)
                if file:
                    logging.info('Attributes found for %s' % path)
                    return file
            logging.info('Attributes not found for %s' % path)

    def _find_in_listing(self, listing, file_name):
        return listing.get(file_name, None)

    def _ls_as_dict(self, path, depth=1):
        listing = self._cache.get(path)
        if listing:
            logging.info('Cached listing found for %s' % path)
        else:
            logging.info('Cached listing not found for %s' % path)
            listing = self._uncached_ls_as_dict(path, depth)
        return listing

    def _uncached_ls_as_dict(self, path, depth=1):
        logging.info('Listing %s' % path)
        listing = {item.name.rstrip(self._delimiter): item for item in self._inner.ls(path, depth)}
        self._cache.set(path, listing)
        return listing

    def _root(self):
        return File(name='root',
                    size=0,
                    mtime=time.mktime(datetime.now(tz=pytz.utc).timetuple()),
                    ctime=None,
                    contenttype=None,
                    is_dir=True)

    def ls(self, path, depth=1):
        return self._ls_as_dict(path, depth).values()

    def upload(self, buf, path):
        try:
            self._inner.upload(buf, path)
            self._cache.replace_in_parent_cache(path, self._inner.attrs(path))
        except _ANY_ERROR:
            logging.exception('Standalone uploading has failed for %s' % path)
            self._cache.invalidate_parent_cache(path)
            raise

    def delete(self, path):
        try:
            self._inner.delete(path)
            self._cache.replace_in_parent_cache(path)
        except _ANY_ERROR:
            logging.exception('Deleting has failed for %s' % path)
            self._cache.invalidate_parent_cache(path)
            raise

    def mv(self, old_path, path):
        try:
            self._inner.mv(old_path, path)
            self._cache.move_from_parent_cache(old_path, path)
            self._cache.invalidate_cache_recursively(old_path)
        except _ANY_ERROR:
            logging.exception('Moving from %s to %s has failed' % (old_path, path))
            self._cache.invalidate_parent_cache(old_path)
            self._cache.invalidate_cache_recursively(old_path)
            self._cache.invalidate_parent_cache(path)
            raise

    def mkdir(self, path):
        try:
            self._inner.mkdir(path)
            self._cache.replace_in_parent_cache(path, self._inner.attrs(path))
        except _ANY_ERROR:
            logging.exception('Mkdir has failed for %s' % path)
            self._cache.invalidate_parent_cache(path)
            raise

    def rmdir(self, path):
        try:
            self._inner.rmdir(path)
            self._cache.replace_in_parent_cache(path)
            self._cache.invalidate_cache_recursively(path)
        except _ANY_ERROR:
            logging.exception('Rmdir has failed for %s' % path)
            self._cache.invalidate_parent_cache(path)
            self._cache.invalidate_cache_recursively(path)
            raise

    def flush(self, fh, path):
        try:
            self._inner.flush(fh, path)
            self._cache.replace_in_parent_cache(path, self._inner.attrs(path))
        except _ANY_ERROR:
            logging.exception('Flushing has failed for %s' % path)
            self._cache.invalidate_parent_cache(path)
            raise
