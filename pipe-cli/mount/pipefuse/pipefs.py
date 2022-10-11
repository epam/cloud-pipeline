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

import datetime
import errno
import io
import logging
import os
import platform
import stat
import time

import easywebdav
from fuse import FuseOSError, Operations
from threading import RLock

from pipefuse import fuseutils


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


def errorlogged(func):
    def wrapper(*args, **kwargs):
        path = args[1]
        try:
            return func(*args, **kwargs)
        except FuseOSError:
            raise
        except Exception:
            logging.exception('Error occurred while %s for %s', func.__name__, path)
            raise
    return wrapper


class PipeFS(Operations):

    def __init__(self, client, lock, mode=0o755):
        self._client = client
        if not self._client.is_available():
            raise RuntimeError('File system server is not available.')
        self._container = FileHandleContainer()
        self._mode = mode
        self._delimiter = '/'
        self._root = '/'
        self._is_mac = platform.system() == 'Darwin'
        self._is_win = platform.system() == 'Windows'
        self._gid = 1 if self._is_win else os.getgid()
        self._uid = 1 if self._is_win else os.getuid()
        self._lock = lock

    def _is_skipped_mac_files(self, path):
        if not self._is_mac:
            return False
        filename = os.path.basename(path)
        return filename in ['.DS_Store', '.localized'] or filename.startswith('._')

    # Filesystem methods
    # ==================

    @errorlogged
    def win_get_attributes(self, path):
        # See https://docs.microsoft.com/en-us/windows/win32/fileio/file-attribute-constants
        attrs = self.getattr(path)
        return 0x10 if attrs['st_mode'] & stat.S_IFDIR == stat.S_IFDIR else 0

    def win_set_attributes(self, path, attrs, fh=None):
        pass

    def win_set_times(self, path, creation_time, last_access_time, last_write_time, fh=None):
        pass

    @errorlogged
    def access(self, path, mode):
        if path == self._root:
            return
        if self._is_skipped_mac_files(path) or not self._client.exists(path):
            raise FuseOSError(errno.EACCES)

    def chmod(self, path, mode):
        pass

    def chown(self, path, uid, gid):
        pass

    @errorlogged
    def getattr(self, path, fh=None):
        try:
            if self._is_skipped_mac_files(path):
                raise FuseOSError(errno.ENOENT)
            props = self._client.attrs(path)
            if not props:
                raise FuseOSError(errno.ENOENT)
            if path == self._root or props.is_dir:
                mode = stat.S_IFDIR
            else:
                mode = stat.S_IFREG
            attrs = {
                'st_size': props.size,
                'st_nlink': 1,
                'st_mode': mode | self._mode,
                'st_gid': self._gid,
                'st_uid': self._uid,
                'st_atime': time.mktime(datetime.datetime.now(tz=tzlocal()).timetuple())
            }
            if props.mtime:
                attrs['st_mtime'] = props.mtime
            if props.ctime:
                attrs['st_ctime'] = props.ctime
            return attrs
        except easywebdav.OperationFailed:
            raise FuseOSError(errno.ENOENT)

    @errorlogged
    def readdir(self, path, fh):
        dirents = ['.', '..']
        prefix = fuseutils.append_delimiter(path, self._delimiter)
        for f in self._client.ls(prefix):
            f_name = f.name.rstrip(self._delimiter)
            if self._is_skipped_mac_files(f_name) or f_name == '.DS_Store':
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
    @errorlogged
    def rmdir(self, path):
        self._client.rmdir(path)

    @syncronized
    @errorlogged
    def mkdir(self, path, mode):
        try:
            self._client.mkdir(path)
        except easywebdav.OperationFailed:
            raise FuseOSError(errno.EACCES)

    @errorlogged
    def statfs(self, path):
        # some magic, check if we need to customize this
        if self._is_win:
            return {
                'f_bsize': 1000000,
                'f_blocks': 1000000,
                'f_bfree': 1000000,
                'f_bavail': 1000000
            }
        else:
            return {
                'f_bavail': 2048,
                'f_blocks': 4096,
                'f_bsize': 4096,
                'f_frsize': 4096,
                'f_namemax': 255
            }

    @syncronized
    @errorlogged
    def unlink(self, path):
        self._client.delete(path)

    def symlink(self, name, target):
        raise UnsupportedOperationException("symlink")

    @syncronized
    @errorlogged
    def rename(self, old, new):
        self._client.mv(old, new)

    def link(self, target, name):
        raise UnsupportedOperationException("link")

    @errorlogged
    def utimens(self, path, times=None):
        self._client.utimens(path, times)

    # File methods
    # ============

    @syncronized
    @errorlogged
    def open(self, path, flags):
        if self._client.exists(path):
            return self._container.get()
        raise FuseOSError(errno.ENOENT)

    @syncronized
    @errorlogged
    def create(self, path, mode, fi=None):
        self._client.upload([], path)
        return self._container.get()

    @syncronized
    @errorlogged
    def read(self, path, length, offset, fh):
        with io.BytesIO() as file_buff:
            self._client.download_range(fh, file_buff, path, offset=offset, length=length)
            return file_buff.getvalue()

    @syncronized
    @errorlogged
    def write(self, path, buf, offset, fh):
        self._client.upload_range(fh, buf, path, offset=offset)
        return len(buf)

    @syncronized
    @errorlogged
    def truncate(self, path, length, fh=None):
        self._client.truncate(fh, path, length)

    @syncronized
    @errorlogged
    def flush(self, path, fh):
        self._client.flush(fh, path)

    @syncronized
    @errorlogged
    def release(self, path, fh):
        self._container.release(fh)

    @syncronized
    @errorlogged
    def fsync(self, path, fdatasync, fh):
        self._client.flush(fh, path)

    @syncronized
    @errorlogged
    def fallocate(self, path, mode, offset, length, fh):
        props = self._client.attrs(path)
        if not props:
            raise FuseOSError(errno.ENOENT)
        if mode:
            # See http://man7.org/linux/man-pages/man2/fallocate.2.html.
            # https://elixir.bootlin.com/linux/latest/source/include/uapi/linux/falloc.h
            logging.warn('Fallocate mode (%s) is not supported yet.' % mode)
        if offset + length >= props.size:
            self._client.truncate(fh, path, offset + length)
