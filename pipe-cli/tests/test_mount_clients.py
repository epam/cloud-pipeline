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

import os
import sys
import types
from datetime import datetime, timedelta
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'mount'))

try:
    import fuse  # noqa: F401
except EnvironmentError:
    # fusepy loads libfuse on import. These tests do not mount anything, so a stand-in is enough without libfuse.
    _fuse = types.ModuleType('fuse')

    class FuseOSError(OSError):
        def __init__(self, errno):
            super(FuseOSError, self).__init__(errno, os.strerror(errno))

    class Operations(object):
        pass

    _fuse.FuseOSError = FuseOSError
    _fuse.Operations = Operations
    _fuse.ENOTSUP = 95
    sys.modules['fuse'] = _fuse

from fuse import FuseOSError  # noqa: E402
from pipefuse import gcp  # noqa: E402
from pipefuse.api import CloudPipelineClient  # noqa: E402
from pipefuse.pipefs import PipeFS  # noqa: E402
from pipefuse.webdav import CPWebDavClient  # noqa: E402


def _response(status_code):
    return SimpleNamespace(status_code=status_code, headers={}, content=b'')


class TestWebDavClient(object):

    def _client(self, status_code=201):
        client = CPWebDavClient(webdav_url='https://webdav.example/webdav/', bearer='token')
        client.session.request = MagicMock(return_value=_response(status_code))
        return client

    def test_upload_range_sends_bytes(self):
        client = self._client()

        client.upload_range(None, bytearray(b'abc'), '/file', offset=3)

        kwargs = client.session.request.call_args[1]
        assert kwargs['data'] == b'abc'
        assert isinstance(kwargs['data'], bytes)
        assert kwargs['headers'] == {'Content-Range': 'bytes 3-5/*'}

    def test_upload_sends_bytes(self):
        client = self._client()

        client.upload(bytearray(b'abc'), '/file')

        method, _ = client.session.request.call_args[0]
        assert method == 'PUT'
        assert client.session.request.call_args[1]['data'] == b'abc'

    def test_exists(self):
        assert self._client(status_code=200).exists('/file')
        assert not self._client(status_code=404).exists('/file')

    def test_exists_raises_other_failures(self):
        with pytest.raises(Exception):
            self._client(status_code=500).exists('/file')

    def test_mkdir_and_delete(self):
        client = self._client()
        client.session.request.side_effect = [_response(201), _response(204)]

        client.mkdir('/folder')
        client.rmdir('/folder')

        methods = [call[0][0] for call in client.session.request.call_args_list]
        assert methods == ['MKCOL', 'DELETE']


class TestGcpMountClient(object):

    def test_client_is_created_with_temporary_credentials(self):
        credentials = SimpleNamespace(session_token='token', secret_key='project',
                                      expiration=datetime.utcnow() + timedelta(hours=1))

        client = gcp._RefreshingClient(lambda: credentials)

        assert client.project == 'project'
        assert client._credentials.token == 'token'


class TestPipeFSXattrs(object):

    def _fs(self, xattrs=None):
        client = MagicMock()
        client.download_xattrs.return_value = xattrs or {}
        return PipeFS(client, lock=MagicMock()), client

    def test_setxattr_stores_string(self):
        fs, client = self._fs()

        fs.setxattr('/file', 'user.key', b'value', 0)

        client.upload_xattr.assert_called_once_with('/file', 'user.key', 'value')

    def test_getxattr_returns_bytes(self):
        fs, _ = self._fs(xattrs={'user.key': 'value'})

        assert fs.getxattr('/file', 'user.key') == b'value'

    def test_getxattr_of_missing_attribute(self):
        fs, _ = self._fs()

        with pytest.raises(FuseOSError):
            fs.getxattr('/file', 'user.key')


class TestCloudPipelineClient(object):

    def test_write_is_not_allowed(self):
        client = CloudPipelineClient(api='https://cp.example/pipeline/restapi/', token='token')

        with patch.object(client, 'get_temporary_credentials',
                          side_effect=RuntimeError('Write operations are forbidden for the storage')):
            assert not client._check_write_allowed(SimpleNamespace())

    def test_other_failures_are_raised(self):
        client = CloudPipelineClient(api='https://cp.example/pipeline/restapi/', token='token')

        with patch.object(client, 'get_temporary_credentials', side_effect=RuntimeError('Not found')):
            with pytest.raises(RuntimeError):
                client._check_write_allowed(SimpleNamespace())
