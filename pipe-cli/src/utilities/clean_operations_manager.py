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
import shutil

import click
import sys
import traceback

from src.config import Config, is_frozen


class CleanOperationsManager:

    _LOCK_NAME = 'pipe.frozen.lock'

    def __init__(self):
        pass

    def clean(self, force=False, quiet=False):
        config_dir_path = os.path.dirname(Config.get_home_dir_config_path())
        self._clean_pipe_tmp_dirs(config_dir_path, force, quiet)
        self._clean_pipe_fuse_tmp_dirs(config_dir_path)

    def _clean_pipe_tmp_dirs(self, config_dir_path, force, quiet):
        logging.debug('Cleaning pipe temporary directories...')
        current_tmp_dir_path = sys._MEIPASS if is_frozen() else None
        root_tmp_dir_path = os.path.join(config_dir_path, 'tmp')
        if not os.path.isdir(root_tmp_dir_path):
            return
        any_tmp_dir_without_lock = False
        for tmp_dir_name in os.listdir(root_tmp_dir_path):
            tmp_dir_path = os.path.join(root_tmp_dir_path, tmp_dir_name)
            tmp_dir_lock_path = os.path.join(tmp_dir_path, self._LOCK_NAME)
            if not tmp_dir_name.startswith('_MEI') or tmp_dir_path == current_tmp_dir_path:
                continue
            if not os.path.isdir(tmp_dir_path):
                continue
            if not os.path.exists(tmp_dir_lock_path) and not force:
                logging.debug('Skipping pipe temporary directory without lock deletion '
                              'because --force flag is not used %s...', tmp_dir_path)
                any_tmp_dir_without_lock = True
                continue
            if os.path.exists(tmp_dir_lock_path) and self._is_dir_locked(tmp_dir_path, tmp_dir_lock_path):
                logging.debug('Skipping running pipe temporary directory deletion %s...', tmp_dir_path)
                continue
            self._remove_dir(tmp_dir_path)
        if any_tmp_dir_without_lock and not quiet:
            pipe_command = sys.argv[0] if is_frozen() else (sys.executable + sys.argv[0])
            click.echo(click.style('Outdated pipe temporary resources have been detected.\n'
                                   'To free up disk space in temporary directory and get rid of this warning please: \n'
                                   '- stop all running pipe cli processes if there are any \n'
                                   '- and execute the following command once. \n\n'
                                   '{pipe_command} clean --force\n'
                                   .format(pipe_command=pipe_command),
                                   fg='yellow'),
                       err=True)

    def _is_dir_locked(self, dir_path, dir_lock_path):
        logging.debug('Trying to lock temporary directory %s...', dir_path)
        with open(dir_lock_path, 'w+') as tmp_dir_lock_descriptor:
            try:
                self._lock(tmp_dir_lock_descriptor)
                return False
            except (IOError, OSError):
                return True
            finally:
                logging.debug('Unlocking temporary directory %s...', dir_path)
                self._unlock(tmp_dir_lock_descriptor)

    def _clean_pipe_fuse_tmp_dirs(self, config_dir_path):
        logging.debug('Cleaning pipe fuse temporary directories...')
        if not os.path.isdir(config_dir_path):
            return
        for tmp_item_name in os.listdir(config_dir_path):
            tmp_item_path = os.path.join(config_dir_path, tmp_item_name)
            if not tmp_item_name.startswith('pipe-fuse'):
                continue
            if os.path.isdir(tmp_item_path):
                tmp_dir_executable_path = os.path.join(tmp_item_path, 'pipe-fuse')
                if self._any_process_with_argument(tmp_dir_executable_path):
                    logging.debug('Skipping running pipe fuse temporary directory deletion %s...', tmp_item_path)
                    continue
                self._remove_dir(tmp_item_path)
            else:
                if self._any_process_with_argument(tmp_item_path):
                    logging.debug('Skipping running pipe fuse temporary file deletion %s...', tmp_item_path)
                    continue
                self._remove_file(tmp_item_path)

    def _any_process_with_argument(self, argument):
        logging.debug('Searching for any processes with argument %s...', argument)
        import psutil
        for proc in psutil.process_iter():
            try:
                if argument in proc.cmdline():
                    logging.debug('Found process (%s) with argument %s', proc.pid, argument)
                    return True
            except psutil.AccessDenied:
                pass
        return False

    def _remove_dir(self, dir_path):
        try:
            logging.debug('Deleting temporary directory %s...', dir_path)
            shutil.rmtree(dir_path)
        except Exception:
            logging.warn('Temporary directory deletion has failed: %s', traceback.format_exc())

    def _remove_file(self, file_path):
        try:
            logging.debug('Deleting temporary file %s...', file_path)
            os.remove(file_path)
        except Exception:
            logging.warn('Temporary file deletion has failed: %s', traceback.format_exc())

    def lock(self, operation):
        tmp_dir_path = Config.get_base_source_dir()
        tmp_dir_lock_path = os.path.join(tmp_dir_path, self._LOCK_NAME)
        logging.debug('Locking temporary directory %s...', tmp_dir_path)
        with open(tmp_dir_lock_path, 'w+') as tmp_dir_lock_descriptor:
            try:
                self._lock(tmp_dir_lock_descriptor)
                return operation()
            finally:
                logging.debug('Unlocking temporary directory %s...', tmp_dir_path)
                self._unlock(tmp_dir_lock_descriptor)

    def _lock(self, descriptor):
        if platform.system() == 'Windows':
            import msvcrt
            msvcrt.locking(descriptor.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.lockf(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)

    def _unlock(self, descriptor):
        if platform.system() == 'Windows':
            import msvcrt
            msvcrt.locking(descriptor.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl
            fcntl.lockf(descriptor, fcntl.LOCK_UN)
