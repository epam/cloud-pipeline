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
from abc import ABCMeta, abstractmethod
from threading import RLock
import threading
from fuse import fuse_get_context


def get_lock(threads):
    return PathLock() if threads else DummyLock()


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

    def __init__(self):
        self.mutex = RLock()
        self.__locks = {}

    def lock(self, path):
        logging.debug('Locking path %s for %s' % (path, str(fuse_get_context())))
        try:
            self.mutex.acquire()
            if path not in self.__locks:
                self.__locks[path] = RLock()
                logging.debug('Created new lock for %s' % path)
        finally:
            self.mutex.release()

        try:
            path_lock = self.__locks[path]
            logging.debug('Current owner %s %d' % (path_lock._RLock__owner, path_lock._RLock__count))
            logging.debug(str(threading.current_thread().ident))
            self.__locks[path].acquire()
            logging.debug('Acquired lock for %s' % path)
            logging.debug('Current owner %s %d' % (path_lock._RLock__owner, path_lock._RLock__count))
        except:
            self.__locks[path].release()
            raise
        logging.debug('Finished locking for %s' % path)

    def unlock(self, path):
        logging.debug('Unlocking path %s' % path)
        logging.debug(str(fuse_get_context()))
        try:
            self.mutex.acquire()
            if path not in self.__locks:
                logging.debug('Cannot release non-existing lock.')
            else:
                self.__locks[path].release()
                logging.debug('Released lock for %s' % path)
        finally:
            self.mutex.release()
            logging.debug('Finished unlocking for %s' % path)

