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

import fuseutils


class UnsupportedOperationException(Exception):
    pass


class PipeFS(Operations):
    FH_START = 2

    def __init__(self, client, mode=0o755):
        self.client = client
        if not self.client.is_available():
            raise RuntimeError("File system server is not available.")
        self.fd = self.FH_START
        self.mode = mode
        self.delimiter = '/'
        self.root = '/'
        self.is_mac = platform.system() == 'Darwin'

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
            logging.exception('Error occurred while getting attributes for %s' % path)
            raise FuseOSError(errno.ENOENT)

    def readdir(self, path, fh):
        dirents = ['.', '..']
        prefix = fuseutils.append_delimiter(path, self.delimiter)
        for f in self.client.ls(prefix):
            f_name = f.name.rstrip(self.delimiter)
            if self.is_skipped_mac_files(f_name):
                continue
            if f_name:
                dirents.append(f_name)
        for f in dirents:
            yield f

    def readlink(self, path):
        raise UnsupportedOperationException("readlink")

    def mknod(self, path, mode, dev):
        raise UnsupportedOperationException("mknod")

    def rmdir(self, path):
        self.client.rmdir(path)

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

    def unlink(self, path):
        self.client.delete(path)

    def symlink(self, name, target):
        raise UnsupportedOperationException("symlink")

    def rename(self, old, new):
        self.client.mv(old, new)

    def link(self, target, name):
        raise UnsupportedOperationException("link")

    def utimens(self, path, times=None):
        raise UnsupportedOperationException("utimens")

    # File methods
    # ============

    def open(self, path, flags):
        if self.client.exists(path):
            self.fd += 1
            return self.fd
        raise FuseOSError(errno.ENOENT)

    def create(self, path, mode, fi=None):
        self.client.upload([], path)
        self.fd += 1
        return self.fd

    def read(self, path, length, offset, fh):
        with io.BytesIO() as file_buff:
            self.client.download_range(file_buff, path, offset=offset, length=length)
            return file_buff.getvalue()

    def write(self, path, buf, offset, fh):
        self.client.upload_range(buf, path, offset=offset)
        return len(buf)

    def truncate(self, path, length, fh=None):
        file_size = self.getattr(path)['st_size']
        if file_size > 0 and length == 0:
            self.create(path, self.mode)
        elif length > file_size:
            self.client.upload_range([], path, offset=(length - 1))
        elif length != file_size:
            raise FuseOSError(errno.ERANGE)

    def flush(self, path, fh):
        self.client.flush(path)

    def release(self, path, fh):
        if self.fd > self.FH_START:
            self.fd -= 1

    def fsync(self, path, fdatasync, fh):
        raise UnsupportedOperationException("fsync")
