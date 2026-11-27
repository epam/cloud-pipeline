# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import importlib
import os
import sys

import pytest
import requests
from requests.adapters import BaseAdapter

try:
    from unittest.mock import MagicMock
except ImportError:
    from mock import MagicMock


class _StubEasyWebDavClient(object):

    def __init__(self, protocol, host, path, **kwargs):
        self.baseurl = '%s://%s/%s' % (protocol, host, path)
        self.session = requests.Session()
        self.cwd = '/'


class _RecordingAdapter(BaseAdapter):

    def __init__(self):
        super(_RecordingAdapter, self).__init__()
        self.requests = []

    def send(self, request, **kwargs):
        self.requests.append(request)
        response = requests.Response()
        response.status_code = 201
        response.request = request
        return response

    def close(self):
        pass


@pytest.fixture
def webdav_client_class(monkeypatch):
    monkeypatch.syspath_prepend(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'mount'))
    try:
        import easywebdav  # noqa: F401
    except ImportError:
        # easywebdav is only needed for the real HTTP transport, which the test replaces with a session
        monkeypatch.setitem(sys.modules, 'easywebdav', MagicMock(Client=_StubEasyWebDavClient))
    monkeypatch.delitem(sys.modules, 'pipefuse', raising=False)
    monkeypatch.delitem(sys.modules, 'pipefuse.webdav', raising=False)
    module = importlib.import_module('pipefuse.webdav')
    yield module.WebDavClient
    # Drop the package and module bound to the stub; monkeypatch restores any previously imported ones
    sys.modules.pop('pipefuse.webdav', None)
    sys.modules.pop('pipefuse', None)


def test_upload_range_sends_raw_bytes_as_body(webdav_client_class):
    client = webdav_client_class('https://storage.example.com/webdav', 'token')
    adapter = _RecordingAdapter()
    client.session = requests.Session()
    client.session.mount('https://', adapter)
    client.upload_range(None, bytearray(b'\x00\x01data'), 'file.bin', offset=4)
    assert len(adapter.requests) == 1
    assert adapter.requests[0].body == bytearray(b'\x00\x01data')
    assert adapter.requests[0].headers['Content-Length'] == '6'
