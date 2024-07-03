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
import json
import logging
import platform
import xml.etree.cElementTree as xml
from numbers import Number

import datetime
import easywebdav
import pytz
import time

import requests
from dateutil.tz import tzlocal
from requests import cookies

from pipefuse import fuseutils
from pipefuse.chain import ChainingService
from pipefuse.fsclient import File, FileSystemClient, \
    ForbiddenOperationException, NotFoundOperationException, InvalidOperationException

py_version, _, _ = platform.python_version_tuple()
if py_version == '2':
    from urlparse import urlparse
    from urllib import quote, unquote
else:
    from urllib.parse import urlparse, quote, unquote


class PermissionService(object):

    PERMISSION_SERVICE_PREFIX = '/extra/chown'

    def __init__(self, webdav_url, bearer):
        self._url = self._build_url(webdav_url)
        self._bearer = bearer
        self._headers = {'Content-Type': 'application/json'}
        self._cookies = {'bearer': bearer}
        self._attempts = 3
        self._timeout = 5

    def _build_url(self, webdav_url):
        return webdav_url.rstrip('/').rsplit('/', 1)[0] + self.PERMISSION_SERVICE_PREFIX

    def set_permissions(self, path):
        count = 0
        while count < self._attempts:
            count += 1
            try:
                response = requests.request(method='POST', url=self._url, data=json.dumps({'path': [path]}),
                                            headers=self._headers, verify=False,
                                            cookies=self._cookies)
                if response.status_code != 200:
                    logging.error('Permission service responded with http status %s.' % str(response.status_code))
                    continue
                response_data = response.json()
                status = response_data.get('status') or 'ERROR'
                message = response_data.get('message') or 'No message'
                if status != 'OK':
                    logging.error('%s: %s' % (status, message))
                else:
                    logging.error(response_data.get('payload'))
                    return
            except Exception as e:
                logging.error(str(e.message))
        logging.error('Failed to set permissions for %s in %d attempts' % (path, self._attempts))


class PermissionAwareWebDavFileSystemClient(ChainingService):

    def __init__(self, inner, webdav, bearer):
        """
        Permission aware WebDav File System Client.
        After uploading write operation on a file,
        Fixes file permission using helper service
        """
        self._inner = inner
        self._permission_service = PermissionService(webdav, bearer)

    def __getattr__(self, name):
        if not hasattr(self._inner, name):
            return None
        attr = getattr(self._inner, name)
        if not callable(attr):
            return attr
        return self._wrap(attr)

    def _wrap(self, attr):
        @functools.wraps(attr)
        def _wrapped_attr(*args, **kwargs):
            return attr(*args, **kwargs)
        return _wrapped_attr

    def mkdir(self, path):
        self._inner.mkdir(path)
        self.change_permissions(path)

    def flush(self, fh, path):
        self._inner.flush(fh, path)
        self.change_permissions(path)

    def change_permissions(self, path):
        logging.debug('Changing permissions for path %s' % path)
        self._permission_service.set_permissions(path.lstrip('/'))


class ResilientWebDavFileSystemClient(ChainingService):

    def __init__(self, inner):
        """
        Resilient WebDav File System Client.

        It properly handles underlying WebDav client errors.

        See https://www.rfc-editor.org/rfc/rfc4918.html.

        :param inner: Decorating file system client.
        """
        self._inner = inner
        self._mappings = {
            401: ForbiddenOperationException,
            403: ForbiddenOperationException,
            404: NotFoundOperationException,
            405: NotFoundOperationException,
        }
        self._default = InvalidOperationException

    def __getattr__(self, name):
        if not hasattr(self._inner, name):
            return None
        attr = getattr(self._inner, name)
        if not callable(attr):
            return attr
        return self._wrap(attr)

    def _wrap(self, attr):
        @functools.wraps(attr)
        def _wrapped_attr(*args, **kwargs):
            try:
                return attr(*args, **kwargs)
            except easywebdav.OperationFailed as e:
                logging.exception('WebDav call has failed with %s http code.' % e.actual_code)
                if e.actual_code < 400:
                    logging.warning('WebDav call has failed with non error %s http code. '
                                    'Please send the log to Cloud Pipeline support team.')
                error = self._mappings.get(e.actual_code)
                if error:
                    raise error()
                raise self._default()
        return _wrapped_attr


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


class WebDavClient(easywebdav.Client, FileSystemClient):
    C_DATE_FORMAT = "%Y-%m-%dT%H:%M:%SZ"
    # 'Wed, 28 Aug 2019 12:18:02 GMT'
    M_DATE_FORMAT = "%a, %d %b %Y %H:%M:%S %Z"

    def __init__(self, webdav_url, bearer):
        url = urlparse(webdav_url)
        super(WebDavClient, self).__init__(protocol=url.scheme, host=url.hostname, port=url.port,
                                           path=url.path.lstrip('/'), verify_ssl=False)
        self.session.cookies.set_cookie(cookies.create_cookie(name='bearer', value=bearer))
        self.host_url = url.scheme + '://' + url.netloc
        self.root_path = url.path if url.path.startswith(self.cwd) else self.cwd + url.path

    def is_available(self):
        try:
            self._send('OPTIONS', '/', 200, allow_redirects=False)
            return True
        except Exception:
            logging.exception('WevDav is not available')
            return False

    def is_read_only(self):
        return False

    def get_elem_value(self, elem, name):
        return elem.find('.//{DAV:}' + name)

    def prop_value(self, elem, name, default=None):
        child = self.get_elem_value(elem, name)
        if child is None or child.text is None:
            return default
        else:
            value = unquote(child.text)
            if not isinstance(value, str):
                value = value.decode('utf8')
            return value

    def prop_exists(self, elem, name):
        return self.get_elem_value(elem, name) is not None

    def parse_timestamp(self, value, date_format):
        if not value:
            return
        try:
            time_value = datetime.datetime.strptime(value, date_format)
            return time.mktime(time_value.replace(tzinfo=pytz.UTC).astimezone(tzlocal()).timetuple())
        except ValueError:
            logging.exception('Failed to parse date: %s. Expected format: "%s".' % (value, date_format))
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
            self.prop_exists(elem, 'collection'),
            storage_class=None
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
                   'Overwrite': 'T'}
        self._send('MOVE', old_path, (201, 204), headers=headers, allow_redirects=True)

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
            return self.host_url + path
        return fuseutils.join_path_with_delimiter(self.baseurl, path, delimiter=self.cwd)
