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

import logging
import time
from abc import ABCMeta, abstractmethod
from threading import RLock, Thread
from future.utils import iteritems

from fuse import fuse_get_context


def get_lock(threads, monitoring_delay):
    return PathLock(monitoring_delay=monitoring_delay) if threads else DummyLock()


def monitor_locks(monitor_lock, locks, timeout):
    while True:
        try:
            monitor_lock.acquire()
            logging.debug('Updating path lock status')
            free_paths = [path for path, lock in iteritems(locks) if lock.acquire(blocking=False)]
            logging.debug('Releasing %d locks' % len(free_paths))
            for path in free_paths:
                del locks[path]
            logging.debug('Finished path lock status update')
        finally:
            monitor_lock.release()
        time.sleep(timeout)


class FileSystemLock:
    __metaclass__ = ABCMeta

    @abstractmethod
    def lock(self, path):
        pass

    @abstractmethod
    def unlock(self, path):
        pass


class DummyLock(FileSystemLock):

    def lock(self, path):
        pass

    def unlock(self, path):
        pass


class PathLock(FileSystemLock):

    def __init__(self, monitoring_delay=600):
        self._mutex = RLock()
        self._monitor_lock = RLock()
        self._locks = {}
        self._monitor = Thread(target=monitor_locks, args=(self._monitor_lock, self._locks, monitoring_delay,))
        self._monitor.daemon = True
        self._monitor.start()

    def lock(self, path):
        try:
            self._monitor_lock.acquire()
            logging.debug('Locking path %s for %s' % (path, str(fuse_get_context())))
            path_lock = self._get_path_lock(path)
            self._lock_path(path_lock)
            logging.debug('Acquired lock for %s' % path)
        finally:
            self._monitor_lock.release()

    def unlock(self, path):
        logging.debug('Unlocking path %s for %s' % (path, str(fuse_get_context())))
        self._release_path(path)

    def _release_path(self, path):
        try:
            self._mutex.acquire()
            if path not in self._locks:
                logging.debug('Cannot release non-existing lock.')
            else:
                self._locks[path].release()
                logging.debug('Released lock for %s' % path)
        finally:
            self._mutex.release()
            logging.debug('Finished unlocking for %s' % path)

    def _get_path_lock(self, path):
        try:
            self._mutex.acquire()
            if path not in self._locks:
                self._locks[path] = RLock()
                logging.debug('Created new lock for %s' % path)
            return self._locks[path]
        finally:
            self._mutex.release()

    def _lock_path(self, path_lock):
        try:
            path_lock.acquire()
        except:
            path_lock.release()
            raise
