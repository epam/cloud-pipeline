from __future__ import with_statement
import platform
import argparse
import datetime
import errno
import io
import logging
import os
import stat
import time
import urllib
import xml.etree.cElementTree as xml
from collections import namedtuple

py_majversion, py_minversion, py_revversion = platform.python_version_tuple()

if py_majversion == '2':
    from httplib import responses as HTTP_CODES
    from urlparse import urlparse
else:
    from http.client import responses as HTTP_CODES
    from urllib.parse import urlparse

import easywebdav
import requests
import urllib3
from fuse import FUSE, FuseOSError, Operations
from requests import cookies

File = namedtuple('File', ['name', 'size', 'mtime', 'ctime', 'contenttype', 'is_dir'])


class UnsupportedOperationException(BaseException):
    pass


def logging_decorator(func):
    def func_wrapper(*args, **kwargs):
        logging.error('Executing request: %s ' % func.__name__)
        return func(*args, **kwargs)

    return func_wrapper


class CPWebDavClient(easywebdav.Client):
    C_DATE_FORMAT = "%Y-%m-%dT%H:%M:%SZ"
    # 'Wed, 28 Aug 2019 12:18:02 GMT'
    M_DATE_FORMAT = "%a, %d %b %Y %H:%M:%S %Z"

    def __init__(self, host, auth=None, username=None, password=None,
                 verify_ssl=False, path=None, cert=None, bearer=None):
        self.host = host
        self.baseurl = host.rstrip('/')
        if path:
            self.baseurl = '{0}/{1}'.format(self.baseurl, path.lstrip('/'))
        self.cwd = '/'
        self.root_path = path if path.startswith(self.cwd) else self.cwd + path
        self.session = requests.session()
        self.session.verify = verify_ssl
        self.session.stream = True
        if bearer:
            cookie_obj = cookies.create_cookie(name='bearer', value=bearer)
            self.session.cookies.set_cookie(cookie_obj)
        if cert:
            self.session.cert = cert
        if auth:
            self.session.auth = auth
        elif username and password:
            self.session.auth = (username, password)

    def get_elem_value(self, elem, name):
        return elem.find('.//{DAV:}' + name)

    def prop_value(self, elem, name, default=None):
        child = self.get_elem_value(elem, name)
        return default if child is None or child.text is None else urllib.unquote(child.text).decode('utf8')

    def prop_exists(self, elem, name):
        return self.get_elem_value(elem, name) is not None

    def parse_timestamp(self, value, date_format):
        if not value:
            return
        try:
            time_value = datetime.datetime.strptime(value, date_format)
            return time.mktime(time_value.timetuple())
        except ValueError as e:
            logging.error(
                'Failed to parse date: %s. Expected format: "%s". Error: "%s"' % (value, date_format, e.message))
            return None

    def elem2file(self, elem):
        return File(
            self.prop_value(elem, 'href'),
            int(self.prop_value(elem, 'getcontentlength', 0)),
            self.parse_timestamp(self.prop_value(elem, 'getlastmodified', ''), self.M_DATE_FORMAT),
            self.parse_timestamp(self.prop_value(elem, 'creationdate', ''), self.C_DATE_FORMAT),
            self.prop_value(elem, 'getcontenttype', ''),
            self.prop_exists(elem, 'collection')
        )

    @logging_decorator
    def download_range(self, data, remote_path, offset=0, length=0):
        headers = None
        if offset >= 0 and length >= 0:
            headers = {'Range': 'bytes=%d-%d' % (offset, offset + length - 1)}
        response = self._send('GET', remote_path, (200, 206), stream=True, headers=headers)
        self._download(data, response)

    @logging_decorator
    def upload_range(self, data, remote_path, offset=0):
        end = offset + len(data) - 1
        if end < offset:
            end = offset
        headers = {'Content-Range': 'bytes %d-%d/*' % (offset, end)}
        self._send('PUT', remote_path, (200, 201, 204), data=data, headers=headers)

    @logging_decorator
    def ls(self, remote_path='.', depth=1):
        headers = {'Depth': str(depth)}
        response = self._send('PROPFIND', remote_path, (207, 301), headers=headers)
        # Redirect
        if response.status_code == 301:
            url = urlparse(response.headers['location'])
            return self.ls(url.path)

        tree = xml.fromstring(response.content)
        return [self.elem2file(elem) for elem in tree.findall('{DAV:}response')]

    def _get_url(self, path):
        path = str(path).strip()
        if path.startswith(self.root_path):
            return self.host + path
        if path.startswith('/'):
            return self.baseurl + path
        return "".join((self.baseurl, self.cwd, path))


class WebDavFS(Operations):
    FH_START = 2

    def __init__(self, webdav_url, mode=0o755):
        url = urlparse(webdav_url)
        bearer = os.environ.get('API_TOKEN', '')
        self.webdav = CPWebDavClient(url.scheme + '://' + url.netloc, path=url.path, bearer=bearer)
        self.fd = self.FH_START
        self.mode = mode
        self.delimiter = '/'
        self.root = '/'

    def is_skipped_mac_files(self, path):
        return path in ['/.DS_Store', '/.localized', '.DS_Store']

    # Filesystem methods
    # ==================

    def access(self, path, mode):
        if path == self.root:
            return
        if self.is_skipped_mac_files(path) or not self.webdav.exists(path):
            raise FuseOSError(errno.EACCES)

    def chmod(self, path, mode):
        pass

    def chown(self, path, uid, gid):
        pass

    def getattr(self, path, fh=None):
        if self.is_skipped_mac_files(path):
            raise FuseOSError(errno.ENOENT)
        try:
            props = self.webdav.ls(path, depth=0)[0]
            if path == self.root or props.is_dir:
                mode = stat.S_IFDIR
            else:
                mode = stat.S_IFREG
            return {
                'st_size': props.size,
                'st_mtime': props.mtime,
                'st_ctime': props.ctime,
                'st_nlink': 1,
                'st_mode': mode | self.mode,
                'st_gid': os.getgid(),
                'st_uid': os.getuid(),
                'st_atime': time.mktime(datetime.datetime.now().timetuple())
            }
        except easywebdav.OperationFailed as e:
            raise FuseOSError(errno.ENOENT)

    def readdir(self, path, fh):
        dirents = ['.', '..']
        prefix = self.webdav.root_path
        if path != self.root:
            prefix = prefix.rstrip(self.delimiter) + self.delimiter + path.strip(self.delimiter) + self.delimiter
        for f in self.webdav.ls(path):
            f_name = f.name.replace(prefix, '').rstrip(self.delimiter)
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
        self.webdav.rmdir(path)

    def mkdir(self, path, mode):
        try:
            self.webdav.mkdir(path)
        except easywebdav.OperationFailed as e:
            raise FuseOSError(errno.EACCES)

    def statfs(self, path):
        return {
            'f_bavail': 2048,
            'f_blocks': 4096,
            'f_bsize': 4096,
            'f_frsize': 4096,
            'f_namemax': 255
        }

    def unlink(self, path):
        self.webdav.delete(path)

    def symlink(self, name, target):
        raise UnsupportedOperationException("symlink")

    def rename(self, old, new):
        raise UnsupportedOperationException("rename")

    def link(self, target, name):
        raise UnsupportedOperationException("link")

    def utimens(self, path, times=None):
        raise UnsupportedOperationException("utimens")

    # File methods
    # ============

    def open(self, path, flags):
        if self.webdav.exists(path):
            self.fd += 1
            return self.fd
        raise FuseOSError(errno.ENOENT)

    def create(self, path, mode, fi=None):
        self.webdav.upload([], path)
        self.fd += 1
        return self.fd

    def read(self, path, length, offset, fh):
        file_buff = io.BytesIO()
        self.webdav.download_range(file_buff, path, offset=offset, length=length)
        return file_buff.getvalue()

    def write(self, path, buf, offset, fh):
        self.webdav.upload_range(buf, path, offset=offset)
        return len(buf)

    def truncate(self, path, length, fh=None):
        file_size = self.getattr(path)['st_size']
        if file_size > 0 and length == 0:
            self.create(path)
        elif length > file_size:
            self.webdav.upload_range([], path, offset=(length - 1))
        elif length != file_size:
            raise FuseOSError(errno.ERANGE)

    def flush(self, path, fh):
        pass

    def release(self, path, fh):
        if self.fd > self.FH_START:
            self.fd -= 1

    def fsync(self, path, fdatasync, fh):
        raise UnsupportedOperationException("fsync")


def start(mountpoint, webdav):
    FUSE(WebDavFS(webdav), mountpoint, nothreads=True, foreground=True)


if __name__ == '__main__':
    logger = logging.getLogger("fuse")
    streamHandler = logging.StreamHandler()
    streamHandler.setLevel(logging.NOTSET)
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    streamHandler.setFormatter(formatter)
    logger.addHandler(streamHandler)

    parser = argparse.ArgumentParser()
    parser.add_argument("--mountpoint", type=str, required=True)
    parser.add_argument("--webdav", type=str, required=True)
    args = parser.parse_args()
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
    start(args.mountpoint, args.webdav)
