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

"""
The GCS transfers of pipe storage cp/mv, run with the real google-cloud-storage client against a fake GCS JSON API.

The transfers use only the public API of google-cloud-storage, so they shall work with any version from 2.16 on.
"""

import base64
import hashlib
import io
import json
import re
from datetime import datetime, timedelta
from types import SimpleNamespace
from unittest.mock import MagicMock, patch
from urllib.parse import parse_qs, unquote, urlparse

import pytest
import requests
import requests_mock

from src.utilities.storage import gs

BUCKET = 'bucket'
_API = 'https://storage.googleapis.com'


def _b64_md5(data):
    return base64.b64encode(hashlib.md5(data).digest()).decode('ascii')


def _b64_crc32c(data):
    import google_crc32c
    return base64.b64encode(google_crc32c.value(data).to_bytes(4, 'big')).decode('ascii')


class _FakeGcs(object):
    """The download, multipart upload and resumable upload requests of the GCS JSON API."""

    def __init__(self):
        self.objects = {}
        self.metadata = {}
        self.content_types = {}
        self.download_ranges = []
        self.upload_types = []
        self.corrupted = False
        self._uploads = {}

    def register(self, adapter):
        adapter.register_uri('GET', re.compile(_API + r'/download/storage/v1/b/[^/]+/o/.*'), content=self._download)
        adapter.register_uri('POST', re.compile(_API + r'/upload/storage/v1/b/[^/]+/o\?.*uploadType=multipart'),
                             content=self._multipart_upload)
        adapter.register_uri('POST', re.compile(_API + r'/upload/storage/v1/b/[^/]+/o\?.*uploadType=resumable'),
                             content=self._initiate_resumable_upload)
        adapter.register_uri('PUT', re.compile(_API + r'/upload/storage/v1/b/[^/]+/o\?.*upload_id='),
                             content=self._resumable_upload_chunk)

    def _download(self, request, context):
        name = unquote(urlparse(request.url).path.split('/o/', 1)[1])
        data = self.objects[name]
        context.headers['x-goog-hash'] = 'crc32c=%s,md5=%s' % (_b64_crc32c(data), _b64_md5(data))
        context.headers['x-goog-stored-content-length'] = str(len(data))
        if self.corrupted:
            data = b'X' + data[1:]
        byte_range = request.headers.get('Range')
        self.download_ranges.append(byte_range)
        if not byte_range:
            context.status_code = 200
            return data
        start, end = byte_range.replace('bytes=', '').split('-')
        start, end = int(start), int(end) if end else len(data) - 1
        context.status_code = 206
        context.headers['Content-Range'] = 'bytes %d-%d/%d' % (start, end, len(data))
        return data[start:end + 1]

    def _resource(self, name, data, metadata):
        self.objects[name] = data
        self.metadata[name] = metadata.get('metadata')
        self.content_types[name] = metadata.get('contentType')
        return json.dumps({'bucket': BUCKET, 'name': name, 'size': str(len(data)), 'generation': '1',
                           'crc32c': _b64_crc32c(data), 'md5Hash': _b64_md5(data),
                           'metadata': metadata.get('metadata')}).encode('utf-8')

    def _multipart_upload(self, request, context):
        self.upload_types.append('multipart')
        content_type = request.headers['Content-Type']
        content_type = content_type.decode('ascii') if isinstance(content_type, bytes) else content_type
        boundary = re.search('boundary="?([^";]+)"?', content_type).group(1).encode('ascii')
        parts = [part for part in request.body.split(b'--' + boundary) if part.strip(b'-\r\n')]
        metadata_part, data_part = parts[0], parts[1]
        metadata = json.loads(metadata_part.split(b'\r\n\r\n', 1)[1].decode('utf-8'))
        data_headers, data = data_part.split(b'\r\n\r\n', 1)
        if data.endswith(b'\r\n'):
            data = data[:-2]
        metadata.setdefault('contentType', re.search(b'content-type: ([^\r\n]+)', data_headers, re.I)
                            .group(1).decode('ascii'))
        context.status_code = 200
        return self._resource(metadata['name'], data, metadata)

    def _initiate_resumable_upload(self, request, context):
        self.upload_types.append('resumable')
        metadata = json.loads(request.body.decode('utf-8')) if request.body else {}
        metadata.setdefault('contentType', request.headers.get('X-Upload-Content-Type'))
        name = metadata.get('name') or parse_qs(urlparse(request.url).query)['name'][0]
        upload_id = str(len(self._uploads) + 1)
        self._uploads[upload_id] = (name, metadata, bytearray())
        context.status_code = 200
        context.headers['Location'] = '%s/upload/storage/v1/b/%s/o?uploadType=resumable&upload_id=%s' \
                                      % (_API, BUCKET, upload_id)
        return b''

    def _resumable_upload_chunk(self, request, context):
        upload_id = parse_qs(urlparse(request.url).query)['upload_id'][0]
        name, metadata, data = self._uploads[upload_id]
        content_range = request.headers['Content-Range']
        chunk = request.body or b''
        if hasattr(chunk, 'read'):
            chunk = chunk.read()
        data.extend(chunk)
        total = content_range.rsplit('/', 1)[1]
        if total != '*' and len(data) == int(total):
            context.status_code = 200
            return self._resource(name, bytes(data), metadata)
        context.status_code = 308
        if data:
            context.headers['Range'] = 'bytes=0-%d' % (len(data) - 1)
        return b''


@pytest.fixture
def fake_gcs():
    credentials = SimpleNamespace(session_token='token', secret_key='project',
                                  expiration=datetime.utcnow() + timedelta(hours=1))
    with patch.object(gs.StorageOperations, 'get_proxy_config', return_value=None):
        client = gs._RefreshingClient(BUCKET, True, True, lambda bucket, read, write, versioning: credentials)
    adapter = requests_mock.Adapter()
    client._http.mount(_API, adapter)
    server = _FakeGcs()
    server.register(adapter)
    return client, server


def _wrapper(path):
    return SimpleNamespace(path=path, bucket=SimpleNamespace(path=BUCKET), is_file=lambda: True)


def _content(size):
    return bytes(bytearray(i % 251 for i in range(size)))


class _Progress(object):

    def __init__(self):
        self.calls = []

    def __call__(self, bytes_amount):
        self.calls.append(bytes_amount)

    @property
    def total(self):
        return sum(self.calls)


class TestGsDownloadManager(object):

    def _download(self, client, tmp_path, name, size, progress=None, monkeypatch=None):
        destination = tmp_path / 'destination'
        manager = gs.GsDownloadManager(client, MagicMock())
        with patch.object(gs.StorageOperations, 'show_progress', return_value=progress is not None), \
                patch.object(gs, 'ProgressPercentage', return_value=progress):
            manager.transfer(_wrapper(name), _wrapper(str(destination)), size=size)
        return destination

    def test_downloads_a_file_and_checks_its_checksum(self, fake_gcs, tmp_path):
        client, server = fake_gcs
        content = _content(300 * 1024)
        server.objects['folder/file'] = content
        progress = _Progress()

        destination = self._download(client, tmp_path, 'folder/file', len(content), progress=progress)

        assert destination.read_bytes() == content
        assert server.download_ranges == [None]
        assert progress.total == len(content)

    def test_removes_a_corrupted_download(self, fake_gcs, tmp_path):
        client, server = fake_gcs
        server.objects['file'] = _content(1024)
        server.corrupted = True

        with pytest.raises(gs.DataCorruption):
            self._download(client, tmp_path, 'file', 1024)

        assert not (tmp_path / 'destination').exists()

    def test_downloads_a_large_file_by_ranges(self, fake_gcs, tmp_path, monkeypatch):
        client, server = fake_gcs
        content = _content(1000)
        server.objects['file'] = content
        monkeypatch.setenv(gs.CP_CLI_GCP_MULTIPART_THRESHOLD, '100')
        monkeypatch.setenv(gs.CP_CLI_GCP_MULTIPART_CHUNKSIZE, '400')
        progress = _Progress()

        destination = self._download(client, tmp_path, 'file', len(content), progress=progress)

        assert destination.read_bytes() == content
        assert server.download_ranges == ['bytes=0-399', 'bytes=400-799', 'bytes=800-999']
        assert progress.total == len(content)

    def test_downloads_an_empty_file(self, fake_gcs, tmp_path):
        client, server = fake_gcs
        server.objects['empty'] = b''

        destination = self._download(client, tmp_path, 'empty', 0)

        assert destination.read_bytes() == b''


class TestGsUploadManager(object):

    def _upload(self, client, tmp_path, content, name, tags=(), progress=None):
        source = tmp_path / 'source.json'
        source.write_bytes(content)
        manager = gs.GsUploadManager(client, MagicMock())
        with patch.object(gs.StorageOperations, 'show_progress', return_value=progress is not None), \
                patch.object(gs, 'ProgressPercentage', return_value=progress), \
                patch.object(gs.StorageOperations, 'get_user', return_value='user'):
            return manager.transfer(_wrapper(str(source)), _wrapper(name), relative_path=name, size=len(content),
                                    tags=tags)

    def test_uploads_a_small_file_with_its_tags_and_content_type(self, fake_gcs, tmp_path):
        client, server = fake_gcs
        content = _content(1024)
        progress = _Progress()

        self._upload(client, tmp_path, content, 'folder/file.json', tags=('key=value',), progress=progress)

        assert server.objects['folder/file.json'] == content
        assert server.upload_types == ['multipart']
        assert server.metadata['folder/file.json']['key'] == 'value'
        assert server.content_types['folder/file.json'] == 'application/json'
        assert progress.total == len(content)


class TestGsUploadStreams(object):

    def test_resumable_upload_reports_each_chunk_once(self, fake_gcs):
        client, server = fake_gcs
        blob = client.bucket(BUCKET).blob('stream')
        blob.chunk_size = 256 * 1024
        content = _content(600 * 1024)
        progress = _Progress()

        gs._upload_blob_from_file(blob, gs._SourceUrlIO(io.BytesIO(content)), progress_callback=progress)

        assert server.objects['stream'] == content
        assert server.upload_types == ['resumable']
        assert progress.total == len(content)
        assert len(progress.calls) >= 3

    def test_progress_is_not_reported_twice_for_a_resent_chunk(self):
        progress = _Progress()
        stream = gs._ProgressInputStream(io.BytesIO(b'abcdefgh'), progress)

        stream.read(4)
        stream.seek(2)
        stream.read(4)
        stream.read(4)

        assert progress.calls == [4, 2, 2]


class _FailingBlob(object):
    """A blob whose download_to_file writes a part of the content, then fails, as on a network error."""

    def __init__(self, content, failures, chunk):
        self.name = 'file'
        self._content = content
        self._failures = failures
        self._chunk = chunk
        self.calls = []

    def download_to_file(self, file_obj, start=None, end=None, **kwargs):
        self.calls.append((start, end, kwargs))
        first = start or 0
        last = len(self._content) - 1 if end is None else end
        if self._failures:
            self._failures -= 1
            file_obj.write(self._content[first:min(first + self._chunk, last + 1)])
            raise requests.exceptions.ChunkedEncodingError('connection broken')
        file_obj.write(self._content[first:last + 1])


class TestResumableDownload(object):

    def test_resumes_from_the_last_written_byte_without_a_checksum(self):
        content = _content(100)
        blob = _FailingBlob(content, failures=2, chunk=30)
        destination = io.BytesIO()

        gs._download_blob(blob, destination, len(content))

        assert destination.getvalue() == content
        assert [(start, end) for start, end, _ in blob.calls] == [(None, None), (30, None), (60, None)]
        assert [kwargs.get('checksum', 'default') for _, _, kwargs in blob.calls] == ['default', None, None]

    def test_resumes_a_range(self):
        content = _content(100)
        blob = _FailingBlob(content, failures=1, chunk=10)
        destination = io.BytesIO()

        gs._download_blob(blob, destination, 50, start=20, end=69)

        assert destination.getvalue() == content[20:70]
        assert [(start, end) for start, end, _ in blob.calls] == [(20, 69), (30, 69)]

    def test_fails_after_the_allowed_resumes(self, monkeypatch):
        monkeypatch.setenv(gs.CP_CLI_RESUMABLE_DOWNLOAD_ATTEMPTS, '3')
        blob = _FailingBlob(_content(100), failures=10, chunk=1)

        with pytest.raises(RuntimeError, match='after 3 sequential resumes'):
            gs._download_blob(blob, io.BytesIO(), 100)

        assert len(blob.calls) == 3

    def test_fails_when_a_download_stops_without_an_error(self):
        blob = MagicMock()
        blob.name = 'file'

        with pytest.raises(RuntimeError, match='has stopped at 0 bytes of 10'):
            gs._download_blob(blob, io.BytesIO(), 10)


def test_download_chunk_size_is_set_for_the_installed_library():
    modules = []
    for module_name in gs._DOWNLOAD_CHUNK_SIZE_MODULES:
        try:
            modules.append(__import__(module_name, fromlist=['_SINGLE_GET_CHUNK_SIZE']))
        except ImportError:
            pass
    originals = [module._SINGLE_GET_CHUNK_SIZE for module in modules]
    try:
        gs._set_download_chunk_size(4 * gs.MB)

        assert modules
        assert all(module._SINGLE_GET_CHUNK_SIZE == 4 * gs.MB for module in modules)
    finally:
        for module, original in zip(modules, originals):
            module._SINGLE_GET_CHUNK_SIZE = original
