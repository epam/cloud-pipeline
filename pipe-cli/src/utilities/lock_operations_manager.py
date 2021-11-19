# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import os
import platform


class LockOperationsManager:
    _DEFAULT_LOCK_NAME = 'pipe.lock'

    def __init__(self, lock_name=_DEFAULT_LOCK_NAME):
        self._lock_name = lock_name
        self._is_windows = platform.system() == 'Windows'

    def execute(self, dir_path, func):
        logging.debug('Locking directory %s...', dir_path)
        dir_lock_path = self.get_lock_path(dir_path)
        with open(dir_lock_path, 'w+') as dir_lock_descriptor:
            try:
                self._lock(dir_lock_descriptor)
                return func()
            finally:
                logging.debug('Unlocking directory %s...', dir_path)
                self._unlock(dir_lock_descriptor)

    def is_locked(self, dir_path):
        logging.debug('Trying to lock directory %s...', dir_path)
        dir_lock_path = self.get_lock_path(dir_path)
        with open(dir_lock_path, 'w+') as dir_lock_descriptor:
            try:
                self._lock(dir_lock_descriptor)
                return False
            except (IOError, OSError):
                return True
            finally:
                logging.debug('Unlocking directory %s...', dir_path)
                self._unlock(dir_lock_descriptor)

    def get_lock_path(self, dir_path):
        return os.path.join(dir_path, self._lock_name)

    def _lock(self, descriptor):
        if self._is_windows:
            import msvcrt
            msvcrt.locking(descriptor.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.lockf(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)

    def _unlock(self, descriptor):
        if self._is_windows:
            import msvcrt
            msvcrt.locking(descriptor.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl
            fcntl.lockf(descriptor, fcntl.LOCK_UN)
