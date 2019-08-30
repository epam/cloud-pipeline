import datetime
import logging
from numbers import Number
import platform
import time
import urllib
import xml.etree.cElementTree as xml
from collections import namedtuple

import easywebdav
import requests
import urllib3
from requests import cookies

py_version, _, _ = platform.python_version_tuple()
if py_version == '2':
    from urlparse import urlparse
else:
    from urllib.parse import urlparse


File = namedtuple('File', ['name', 'size', 'mtime', 'ctime', 'contenttype', 'is_dir'])


class CPWebDavClient(easywebdav.Client):
    C_DATE_FORMAT = "%Y-%m-%dT%H:%M:%SZ"
    # 'Wed, 28 Aug 2019 12:18:02 GMT'
    M_DATE_FORMAT = "%a, %d %b %Y %H:%M:%S %Z"

    def __init__(self, host, auth=None, username=None, password=None,
                 verify_ssl=False, path=None, cert=None, bearer=None):
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

    def download_range(self, data, remote_path, offset=0, length=0):
        headers = None
        if offset >= 0 and length >= 0:
            headers = {'Range': 'bytes=%d-%d' % (offset, offset + length - 1)}
        response = self._send('GET', remote_path, (200, 206), stream=True, headers=headers)
        self._download(data, response)

    def upload_range(self, data, remote_path, offset=0):
        end = offset + len(data) - 1
        if end < offset:
            end = offset
        headers = {'Content-Range': 'bytes %d-%d/*' % (offset, end)}
        self._send('PUT', remote_path, (200, 201, 204), data=data, headers=headers)

    def ls(self, remote_path='.', depth=1):
        headers = {'Depth': str(depth)}
        response = self._send('PROPFIND', remote_path, (207, 301), headers=headers, allow_redirects=True)
        # Redirect
        if response.status_code == 301:
            url = urlparse(response.headers['location'])
            return self.ls(url.path)

        tree = xml.fromstring(response.content)
        return [self.elem2file(elem) for elem in tree.findall('{DAV:}response')]

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
        path = str(path).strip()
        if path.startswith(self.root_path):
            return self.host + path
        if path.startswith('/'):
            return self.baseurl + path
        return ''.join((self.baseurl, self.cwd, path))
