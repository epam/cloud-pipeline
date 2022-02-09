# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import os
from threading import RLock

DEFAULT_DELIMITER = '/'
KB = 1024
MB = KB * KB
GB = MB * KB
TB = GB * KB


def join_path_with_delimiter(parent, child, delimiter=DEFAULT_DELIMITER):
    return parent.rstrip(delimiter) + delimiter + child.lstrip(delimiter)


def append_delimiter(path, delimiter=DEFAULT_DELIMITER):
    return path if path.endswith(delimiter) else path + delimiter


def split_path(path, delimiter=DEFAULT_DELIMITER):
    path_parts = path.rstrip(delimiter).rsplit(delimiter, 1)
    if len(path_parts) == 1:
        return delimiter, path_parts[0]
    else:
        parent_path, file_name = path_parts
        return parent_path + delimiter, __matching_delimiter(file_name, path)


def __matching_delimiter(path, reference_path, delimiter=DEFAULT_DELIMITER):
    return path + delimiter if reference_path.endswith(delimiter) else path


def lazy_range(start, end):
    try:
        return xrange(start, end)
    except NameError:
        return range(start, end)


def without_prefix(string, prefix):
    if string.startswith(prefix):
        return string[len(prefix):]


def get_item_name(path, prefix, delimiter='/'):
    possible_folder_name = prefix if prefix.endswith(delimiter) else \
        prefix + delimiter
    if prefix and path.startswith(prefix) and path != possible_folder_name and path != prefix:
        if not path == prefix:
            splitted = prefix.split(delimiter)
            return splitted[len(splitted) - 1] + path[len(prefix):]
        else:
            return path[len(prefix):]
    elif not path.endswith(delimiter) and path == prefix:
        return os.path.basename(path)
    elif path == possible_folder_name:
        return os.path.basename(path.rstrip(delimiter)) + delimiter
    else:
        return path


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


def get_parent_paths(path, delimiter='/'):
    current_parent_path = ''
    for item in get_parent_dirs(path):
        if current_parent_path:
            current_parent_path += delimiter + item
        else:
            current_parent_path = item
        yield current_parent_path


def get_parent_dirs(path, delimiter='/'):
    items = path.strip(delimiter).split(delimiter)
    yield ''
    for item in items[:-1]:
        yield item


class SimpleCache:

    def __init__(self, cache):
        """
        Simple cache.

        Stores key value pairs using the provided cache implementation.

        :param cache: Cache implementation.
        """
        self._cache = cache

    def get(self, key):
        return self._cache.get(key, None)

    def set(self, key, value):
        self._cache[key] = value

    def clear(self):
        self._cache.clear()


class ThreadSafeCache:

    def __init__(self, inner):
        """
        Thread safe cache decorator.

        Provides basic thread safety for the underlying cache.

        :param inner: Not thread safe cache.
        """
        self._inner = inner
        self._lock = RLock()

    @synchronized
    def get(self, key):
        return self._inner.get(key)

    @synchronized
    def set(self, key, value):
        self._inner.set(key, value)

    @synchronized
    def clear(self):
        self._inner.clear()
