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
from abc import ABCMeta, abstractmethod

from pipeline.utils.plat import is_windows


def synchronized(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        lock = args[0]._lock
        try:
            lock.acquire()
            return_value = func(*args, **kwargs)
            return return_value
        finally:
            lock.release()
    return wrapper


class CloudPipelineLock:
    __metaclass__ = ABCMeta

    @abstractmethod
    def acquire(self):
        pass

    @abstractmethod
    def release(self):
        pass

    def __enter__(self):
        self.acquire()

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.release()


class FileSystemLock(CloudPipelineLock):

    def __init__(self, lock_path):
        self._lock_path = lock_path
        self._descriptor = None

    def acquire(self):
        self._descriptor = open(self._lock_path, 'w+')
        self._acquire(self._descriptor)

    def _acquire(self, descriptor):
        if is_windows():
            import msvcrt
            msvcrt.locking(descriptor.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.lockf(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)

    def release(self):
        try:
            if self._descriptor:
                self._release(self._descriptor)
        finally:
            self._descriptor.close()

    def _release(self, descriptor):
        if is_windows():
            import msvcrt
            msvcrt.locking(descriptor.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl
            fcntl.lockf(descriptor, fcntl.LOCK_UN)
