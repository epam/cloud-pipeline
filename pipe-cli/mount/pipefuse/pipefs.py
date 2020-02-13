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

import datetime
import errno
import io
import logging
import os
import platform
import stat
import time

from fuse import FuseOSError, Operations
from threading import RLock

import fuseutils


class UnsupportedOperationException(Exception):
    pass


class FileHandleContainer(object):
    FH_START = 2
    FH_END = 10000

    def __init__(self):
        self._container = set()
        self._range = fuseutils.lazy_range(self.FH_START, self.FH_END)
        self._fh_lock = RLock()

    def get(self):
        try:
            self._fh_lock.acquire()
            for fh in self._range:
                if fh not in self._container:
                    self._container.add(fh)
                    return fh
        finally:
            self._fh_lock.release()

    def release(self, fh):
        try:
            self._fh_lock.acquire()
            if fh in self._container:
                self._container.remove(fh)
        finally:
            self._fh_lock.release()


def syncronized(func):
    def wrapper(*args, **kwargs):
        lock = args[0]._lock
        path = args[1]
        try:
            lock.lock(path)
            return_value = func(*args, **kwargs)
            return return_value
        finally:
            lock.unlock(path)
    return wrapper


class PipeFS(Operations):

    def __init__(self, client, lock, mode=0o755):
        self.client = client
        if not self.client.is_available():
            raise RuntimeError("File system server is not available.")
        self.container = FileHandleContainer()
        self.mode = mode
        self.delimiter = '/'
        self.root = '/'
        self.is_mac = platform.system() == 'Darwin'
        self._system_files = ['/.Trash', '/.Trash-1000', '/.xdg-volume-info', '/autorun.inf']
        self._lock = lock

    def is_skipped_mac_files(self, path):
        if not self.is_mac:
            return False
        filename = os.path.basename(path)
        return filename in ['.DS_Store', '.localized'] or filename.startswith('._')

    # Filesystem methods
    # ==================

    def access(self, path, mode):
        if path == self.root:
            return
        if self.is_skipped_mac_files(path) or not self.client.exists(path):
            raise FuseOSError(errno.EACCES)

    def chmod(self, path, mode):
        pass

    def chown(self, path, uid, gid):
        pass

    def getattr(self, path, fh=None):
        if self.is_skipped_mac_files(path):
            raise FuseOSError(errno.ENOENT)
        try:
            props = self.client.attrs(path)
            if not props:
                raise RuntimeError('Cannot read attributes for a path because it doesn\'t exist %s' % path)
            if path == self.root or props.is_dir:
                mode = stat.S_IFDIR
            else:
                mode = stat.S_IFREG
            attrs = {
                'st_size': props.size,
                'st_nlink': 1,
                'st_mode': mode | self.mode,
                'st_gid': os.getgid(),
                'st_uid': os.getuid(),
                'st_atime': time.mktime(datetime.datetime.now().timetuple())
            }
            if props.mtime:
                attrs['st_mtime'] = props.mtime
            if props.ctime:
                attrs['st_ctime'] = props.ctime
            return attrs
        except Exception:
            if path not in self._system_files:
                logging.exception('Error occurred while getting attributes for %s' % path)
            raise FuseOSError(errno.ENOENT)

    def readdir(self, path, fh):
        dirents = ['.', '..']
        prefix = fuseutils.append_delimiter(path, self.delimiter)
        for f in self.client.ls(prefix):
            f_name = f.name.rstrip(self.delimiter)
            if self.is_skipped_mac_files(f_name) or f_name == '.DS_Store':
                continue
            if f_name:
                dirents.append(f_name)
        for f in dirents:
            yield f

    def readlink(self, path):
        raise UnsupportedOperationException("readlink")

    def mknod(self, path, mode, dev):
        raise UnsupportedOperationException("mknod")

    @syncronized
    def rmdir(self, path):
        self.client.rmdir(path)

    @syncronized
    def mkdir(self, path, mode):
        try:
            self.client.mkdir(path)
        except Exception:
            logging.exception('Error occurred while creating directory %s' % path)
            raise FuseOSError(errno.EACCES)

    def statfs(self, path):
        # some magic, check if we need to customize this
        return {
            'f_bavail': 2048,
            'f_blocks': 4096,
            'f_bsize': 4096,
            'f_frsize': 4096,
            'f_namemax': 255
        }

    @syncronized
    def unlink(self, path):
        self.client.delete(path)

    def symlink(self, name, target):
        raise UnsupportedOperationException("symlink")

    @syncronized
    def rename(self, old, new):
        self.client.mv(old, new)

    def link(self, target, name):
        raise UnsupportedOperationException("link")

    def utimens(self, path, times=None):
        self.client.utimens(path, times)

    # File methods
    # ============

    @syncronized
    def open(self, path, flags):
        if self.client.exists(path):
            return self.container.get()
        raise FuseOSError(errno.ENOENT)

    @syncronized
    def create(self, path, mode, fi=None):
        self.client.upload([], path)
        return self.container.get()

    @syncronized
    def read(self, path, length, offset, fh):
        with io.BytesIO() as file_buff:
            self.client.download_range(fh, file_buff, path, offset=offset, length=length)
            return file_buff.getvalue()

    @syncronized
    def write(self, path, buf, offset, fh):
        self.client.upload_range(fh, buf, path, offset=offset)
        return len(buf)

    @syncronized
    def truncate(self, path, length, fh=None):
        self.client.truncate(fh, path, length)

    @syncronized
    def flush(self, path, fh):
        self.client.flush(fh, path)

    @syncronized
    def release(self, path, fh):
        self.container.release(fh)

    def fsync(self, path, fdatasync, fh):
        pass

    def fallocate(self, path, mode, offset, length, fh):
        pass
