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
import logging
import platform
import time
import urllib
import xml.etree.cElementTree as xml
from numbers import Number

import easywebdav
import requests
import urllib3
from requests import cookies

import fuseutils
from fsclient import FileSystemClient, File

py_version, _, _ = platform.python_version_tuple()
if py_version == '2':
    from urlparse import urlparse
    from urllib import quote, unquote
else:
    from urllib.parse import urlparse, quote, unquote

# add additional methods
easywebdav.OperationFailed._OPERATIONS = dict(
    HEAD="get header",
    GET="download",
    PUT="upload",
    DELETE="delete",
    MKCOL="create directory",
    PROPFIND="list directory",
    OPTIONS="check availability",
    MOVE="move"
)


class CPWebDavClient(easywebdav.Client, FileSystemClient):
    C_DATE_FORMAT = "%Y-%m-%dT%H:%M:%SZ"
    # 'Wed, 28 Aug 2019 12:18:02 GMT'
    M_DATE_FORMAT = "%a, %d %b %Y %H:%M:%S %Z"

    def __init__(self, webdav_url, auth=None, username=None, password=None,
                 verify_ssl=False, cert=None, bearer=None):
        url = urlparse(webdav_url)
        host = url.scheme + '://' + url.netloc
        path = url.path
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        self.host = host
        self.baseurl = host.rstrip('/')
        if path:
            self.baseurl = '{0}/{1}'.format(self.baseurl, path.lstrip('/'))
        self.cwd = '/'
        self.root_path = path if path.startswith(self.cwd) else self.cwd + path
        self.session = requests.session()
        self.session.verify = verify_ssl
        self.session.stream = True
        if not bearer and not auth:
            logging.warning("Authorization is not configured for Webdav client.")
        if bearer:
            cookie_obj = cookies.create_cookie(name='bearer', value=bearer)
            self.session.cookies.set_cookie(cookie_obj)
        if cert:
            self.session.cert = cert
        if auth:
            self.session.auth = auth
        elif username and password:
            self.session.auth = (username, password)

    def is_available(self):
        try:
            self._send('OPTIONS', '/', 200, allow_redirects=False)
            return True
        except easywebdav.OperationFailed as e:
            logging.error('WevDav is not available: %s' % str(e.reason))
            return False
        except BaseException as e:
            logging.error('WevDav is not available: %s' % str(e.message))
            return False

    def is_read_only(self):
        return False

    def get_elem_value(self, elem, name):
        return elem.find('.//{DAV:}' + name)

    def prop_value(self, elem, name, default=None):
        child = self.get_elem_value(elem, name)
        return default if child is None or child.text is None else unquote(child.text).decode('utf8')

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

    def elem2file(self, elem, path):
        prefix = fuseutils.append_delimiter(fuseutils.join_path_with_delimiter(
            self.root_path, path, delimiter=self.cwd), self.cwd)
        return File(
            self.prop_value(elem, 'href').replace(prefix, ''),
            int(self.prop_value(elem, 'getcontentlength', 0)),
            self.parse_timestamp(self.prop_value(elem, 'getlastmodified', ''), self.M_DATE_FORMAT),
            self.parse_timestamp(self.prop_value(elem, 'creationdate', ''), self.C_DATE_FORMAT),
            self.prop_value(elem, 'getcontenttype', ''),
            self.prop_exists(elem, 'collection')
        )

    def download_range(self, fh, data, remote_path, offset=0, length=0):
        headers = None
        if offset >= 0 and length >= 0:
            headers = {'Range': 'bytes=%d-%d' % (offset, offset + length - 1)}
        response = self._send('GET', remote_path, (200, 206), stream=True, headers=headers)
        self._download(data, response)

    def upload_range(self, fh, data, remote_path, offset=0):
        end = offset + len(data) - 1
        if end < offset:
            end = offset
        headers = {'Content-Range': 'bytes %d-%d/*' % (offset, end)}
        self._send('PUT', remote_path, (200, 201, 204), data=str(data), headers=headers)

    def ls(self, remote_path='.', depth=1):
        headers = {'Depth': str(depth)}
        response = self._send('PROPFIND', remote_path, (207, 301), headers=headers, allow_redirects=True)
        # Redirect
        if response.status_code == 301:
            url = urlparse(response.headers['location'])
            return self.ls(url.path)

        tree = xml.fromstring(response.content)
        return [self.elem2file(elem, remote_path) for elem in tree.findall('{DAV:}response')]

    def mv(self, old_path, new_path):
        headers = {'Destination': self._get_url(new_path),
                   'Overwrite': "T"}
        self._send('MOVE', old_path, 201, headers=headers, allow_redirects=True)

    def _send(self, method, path, expected_code, allow_redirects=False, **kwargs):
        url = self._get_url(path)
        response = self.session.request(method, url, allow_redirects=allow_redirects, **kwargs)
        if isinstance(expected_code, Number) and response.status_code != expected_code \
                or not isinstance(expected_code, Number) and response.status_code not in expected_code:
            raise easywebdav.OperationFailed(method, path, expected_code, response.status_code)
        return response

    def _get_url(self, path):
        path = quote(str(path).strip())
        if path.startswith(self.root_path):
            return self.host + path
        if path.startswith('/'):
            return fuseutils.join_path_with_delimiter(self.baseurl, path)
        return ''.join((self.baseurl, self.cwd, path))
