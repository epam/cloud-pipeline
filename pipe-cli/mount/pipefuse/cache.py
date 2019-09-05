import logging
import time

from datetime import datetime

import pytz

from fsclient import FileSystemClient, File
from pipefuse import fuseutils


class CachingFileSystemClient(FileSystemClient):

    def __init__(self, inner, cache):
        """
        Caching file system client decorator.

        It caches listing calls to reduce number of calls to an inner file system client.

        :param inner: Decorating file system client.
        :param cache: Caching dictionary.
        """
        self._inner = inner
        self._cache = cache
        self._delimiter = '/'

    def is_available(self):
        return self._inner.is_available()

    def exists(self, path):
        return self._inner.exists(path)

    def attrs(self, path):
        parent_path, file_name = fuseutils.split_path(path)
        if not file_name:
            return self._root()
        else:
            parent_listing = self._cache.get(fuseutils.append_delimiter(parent_path, self._delimiter), None)
            if parent_listing:
                file = self._find_in_listing(parent_listing, file_name)
                if file:
                    logging.info('Using cached attributes for %s' % path)
                    return file
        logging.info('Getting attributes for %s' % path)
        return self._find_in_listing(self._ls_as_dict(parent_path or self._delimiter), file_name)

    def _find_in_listing(self, listing, file_name):
        return listing.get(file_name, None)

    def _ls_as_dict(self, path, depth=1):
        listing = self._cache.get(path, None)
        if listing:
            logging.info('Using cached listing for %s' % path)
        else:
            logging.info('Listing %s' % path)
            listing = {item.name.rstrip(self._delimiter): item for item in self._inner.ls(path, depth)}
            self._cache[path] = listing
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
        self._inner.upload(buf, path)
        self._invalidate_parent_cache(path)

    def delete(self, path):
        self._inner.delete(path)
        self._invalidate_parent_cache(path)

    def mv(self, old_path, path):
        self._inner.mv(old_path, path)
        self._invalidate_parent_cache(old_path)
        self._invalidate_parent_cache(path)

    def mkdir(self, path):
        self._inner.mkdir(path)
        self._invalidate_parent_cache(path)

    def rmdir(self, path):
        self._inner.rmdir(path)
        self._invalidate_cache(path)

    def download_range(self, buf, path, offset=0, length=0):
        self._inner.download_range(buf, path, offset, length)

    def upload_range(self, buf, path, offset=0):
        self._inner.upload_range(buf, path, offset)

    def flush(self, path):
        self._inner.flush(path)
        self._invalidate_parent_cache(path)

    def _invalidate_parent_cache(self, path):
        parent_path, _ = fuseutils.split_path(path)
        self._invalidate_cache(parent_path or self._delimiter)

    def _invalidate_cache(self, path):
        logging.info('Invalidating cache for %s' % path)
        self._cache.pop(path, None)

    def __getattr__(self, name):
        if hasattr(self._inner, name):
            return getattr(self._inner, name)
        else:
            raise RuntimeError('BufferedFileSystemClient or its inner client doesn\'t have %s attribute.' % name)
