# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import sys
import types

import pytest
import requests
from requests.adapters import BaseAdapter

from datetime import datetime, timedelta
from types import SimpleNamespace
from unittest.mock import MagicMock

sys.path.append(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'mount'))

try:
    import fuse  # noqa: F401
except (ImportError, EnvironmentError):
    # fusepy is not in the CLI requirements, and it loads libfuse on import.
    # These tests do not mount anything, so a stand-in is enough.
    _fuse = types.ModuleType('fuse')

    class FuseOSError(OSError):
        def __init__(self, errno):
            super(FuseOSError, self).__init__(errno, os.strerror(errno))

    class Operations(object):
        pass

    _fuse.FuseOSError = FuseOSError
    _fuse.Operations = Operations
    sys.modules['fuse'] = _fuse

from pipefuse.fsclient import NoDataOperationException  # noqa: E402
from pipefuse.pipefs import PipeFS  # noqa: E402
from pipefuse.record import RecordingFileSystemClient  # noqa: E402

TEXT_TYPE = type(u'')
# A non-ASCII value: "value" in Russian
VALUE = u'\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435'
VALUE_BYTES = VALUE.encode('utf-8')


class _RecordingAdapter(BaseAdapter):

    def __init__(self):
        super(_RecordingAdapter, self).__init__()
        self.requests = []
        self.status_code = 201
        self.content = b''

    def send(self, request, **kwargs):
        self.requests.append(request)
        response = requests.Response()
        response.status_code = self.status_code
        response._content = self.content
        response.request = request
        return response

    def close(self):
        pass


@pytest.fixture
def webdav():
    from pipefuse.webdav import WebDavClient
    client = WebDavClient('https://webdav.example/webdav/', 'token')
    adapter = _RecordingAdapter()
    client.session.mount('https://', adapter)
    return client, adapter


class TestWebDavUploadRange(object):

    def test_bytearray_is_sent_as_raw_bytes(self, webdav):
        client, adapter = webdav

        client.upload_range(None, bytearray(b'\x00\x01data'), '/file.bin', offset=4)

        assert len(adapter.requests) == 1
        request = adapter.requests[0]
        assert request.method == 'PUT'
        assert request.body == b'\x00\x01data'
        assert request.headers['Content-Length'] == '6'
        assert request.headers['Content-Range'] == 'bytes 4-9/*'

    def test_bytes_are_sent_unchanged(self, webdav):
        client, adapter = webdav

        client.upload_range(None, VALUE_BYTES, '/file.txt')

        assert adapter.requests[0].body == VALUE_BYTES


class TestWebDavUpload(object):

    def test_create_sends_empty_put(self, webdav):
        client, adapter = webdav
        adapter.status_code = 200
        fs = _pipe_fs(client)

        fs.create('/new.txt', 0o644)

        request = adapter.requests[-1]
        assert request.method == 'PUT'
        assert request.url.endswith('/webdav/new.txt')
        assert not request.body
        assert request.headers['Content-Length'] == '0'

    def test_bytearray_is_sent_as_raw_bytes(self, webdav):
        client, adapter = webdav

        client.upload(bytearray(b'\x00\x01data'), '/file.bin')

        assert adapter.requests[0].method == 'PUT'
        assert adapter.requests[0].body == b'\x00\x01data'


_PROPFIND_RESPONSE = b'''<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:">
  <D:response>
    <D:href>/webdav/folder/</D:href>
    <D:propstat>
      <D:prop>
        <D:creationdate>2019-08-28T12:18:02Z</D:creationdate>
        <D:getlastmodified>Wed, 28 Aug 2019 12:18:02 GMT</D:getlastmodified>
        <D:resourcetype><D:collection/></D:resourcetype>
      </D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
  <D:response>
    <D:href>/webdav/folder/%D0%B7%D0%BD%D0%B0%D1%87%D0%B5%D0%BD%D0%B8%D0%B5.txt</D:href>
    <D:propstat>
      <D:prop>
        <D:getcontentlength>6</D:getcontentlength>
        <D:getcontenttype>text/plain</D:getcontenttype>
        <D:getlastmodified>Wed, 28 Aug 2019 12:18:02 GMT</D:getlastmodified>
        <D:resourcetype/>
      </D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
</D:multistatus>'''


class TestWebDavListing(object):

    def test_ls_returns_unquoted_text_names(self, webdav):
        client, adapter = webdav
        adapter.status_code = 207
        adapter.content = _PROPFIND_RESPONSE

        items = client.ls('/folder')

        assert adapter.requests[0].method == 'PROPFIND'
        assert [(item.name, item.size, item.is_dir) for item in items] == [(u'', 0, True),
                                                                            (VALUE + u'.txt', 6, False)]
        assert all(isinstance(item.name, TEXT_TYPE) for item in items)
        assert items[1].contenttype == u'text/plain'


def _pipe_fs(client):
    return PipeFS(client, lock=MagicMock())


class TestPipeFSExtendedAttributes(object):

    def _fs(self, xattrs=None):
        client = MagicMock()
        client.download_xattrs.return_value = xattrs
        return _pipe_fs(client), client

    def test_setxattr_passes_text_to_client(self):
        fs, client = self._fs()

        assert fs.setxattr('/file', 'user.key', VALUE_BYTES, 0) == 0

        client.upload_xattr.assert_called_once_with('/file', 'user.key', VALUE)
        assert isinstance(client.upload_xattr.call_args[0][2], TEXT_TYPE)

    def test_getxattr_returns_text_as_utf8_bytes(self):
        fs, _ = self._fs({'user.key': VALUE})

        value = fs.getxattr('/file', 'user.key')

        assert isinstance(value, bytes)
        assert value == VALUE_BYTES

    def test_getxattr_returns_bytes_unchanged(self):
        fs, _ = self._fs({'user.key': b'value'})

        assert fs.getxattr('/file', 'user.key') == b'value'

    def test_getxattr_of_missing_attribute(self):
        fs, _ = self._fs({})

        with pytest.raises(NoDataOperationException):
            fs.getxattr('/file', 'user.key')


@pytest.fixture
def s3():
    pytest.importorskip('intervals')
    from botocore.stub import Stubber
    from pipefuse.s3 import S3StorageLowLevelClient
    pipe = MagicMock()
    pipe.get_temporary_credentials.return_value = MagicMock(access_key_id='key', secret_key='secret',
                                                            session_token=None, expiration=None)
    bucket = MagicMock(endpoint=None, region_name='us-east-1')
    bucket.is_write_allowed.return_value = True
    client = S3StorageLowLevelClient('bucket', bucket, pipe=pipe, chunk_size=10 * 1024 * 1024)
    stubber = Stubber(client._s3)
    stubber.activate()
    yield _pipe_fs(client), stubber
    stubber.deactivate()


class TestPipeFSExtendedAttributesOnS3(object):

    def test_setxattr_stores_text_tag(self, s3):
        fs, stubber = s3
        stubber.add_response('get_object_tagging', {'TagSet': []}, {'Bucket': 'bucket', 'Key': 'file'})
        stubber.add_response('put_object_tagging', {},
                             {'Bucket': 'bucket', 'Key': 'file',
                              'Tagging': {'TagSet': [{'Key': 'user.key', 'Value': VALUE}]}})

        fs.setxattr('file', 'user.key', VALUE_BYTES, 0)

        stubber.assert_no_pending_responses()

    def test_getxattr_returns_tag_as_utf8_bytes(self, s3):
        fs, stubber = s3
        stubber.add_response('get_object_tagging', {'TagSet': [{'Key': 'user.key', 'Value': VALUE}]},
                             {'Bucket': 'bucket', 'Key': 'file'})

        assert fs.getxattr('file', 'user.key') == VALUE_BYTES


class _ListHandler(logging.Handler):

    def __init__(self):
        logging.Handler.__init__(self)
        self.messages = []

    def emit(self, record):
        message = record.getMessage()
        self.messages.append(message.decode('utf-8') if isinstance(message, bytes) else message)


class TestRecordingFileSystemClient(object):

    def test_records_non_ascii_text_arguments(self):
        inner = MagicMock()
        path = u'/' + VALUE + u'.txt'
        handler = _ListHandler()
        root = logging.getLogger()
        level = root.level
        root.addHandler(handler)
        root.setLevel(logging.INFO)
        try:
            _pipe_fs(RecordingFileSystemClient(inner)).setxattr(path, u'user.key', VALUE_BYTES, 0)
        finally:
            root.removeHandler(handler)
            root.setLevel(level)

        inner.upload_xattr.assert_called_once_with(path, u'user.key', VALUE)
        assert any(u'upload_xattr' in message and path in message and VALUE in message
                   for message in handler.messages)


class TestGcpMountClient(object):

    def test_client_is_created_with_temporary_credentials(self):
        pytest.importorskip('intervals')
        from pipefuse import gcp
        credentials = SimpleNamespace(session_token='token', secret_key='project',
                                      expiration=datetime.utcnow() + timedelta(hours=1))

        client = gcp._RefreshingClient(lambda: credentials)

        assert client.project == 'project'
        assert client._credentials.token == 'token'
