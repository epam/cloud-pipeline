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

import io
from datetime import datetime, timedelta
from types import SimpleNamespace
from unittest.mock import patch

from boto3 import Session
from botocore.config import Config as AwsConfig

from src.utilities.storage import azure, gs
from src.utilities.storage.s3 import S3BucketOperations

PROXIES = {'http': 'http://proxy.example:3128', 'https': 'http://proxy.example:3128'}


def _credentials(**kwargs):
    values = dict(session_token='token', secret_key='account', expiration=datetime.utcnow() + timedelta(hours=1))
    values.update(kwargs)
    return SimpleNamespace(**values)


class TestS3Client(object):

    @patch.object(S3BucketOperations, 'get_proxy_config', return_value=AwsConfig(proxies=PROXIES))
    def test_client_keeps_proxies(self, _):
        session = Session(aws_access_key_id='key', aws_secret_access_key='secret', region_name='us-east-1')

        client = S3BucketOperations._get_client(session, region_name='us-east-1')

        assert client._endpoint.http_session._proxy_config._proxies == PROXIES


class TestGsClient(object):

    @patch.object(gs.StorageOperations, 'get_proxy_config', return_value=None)
    def test_client_is_created_with_temporary_credentials(self, _):
        client = gs._RefreshingClient('bucket', True, False,
                                      lambda bucket, read, write, versioning: _credentials(secret_key='project'))

        assert client.project == 'project'
        assert client._credentials.token == 'token'

    def test_multipart_upload_sends_chunks_in_order(self, tmp_path):
        source = tmp_path / 'source'
        source.write_bytes(b'abcdefghij')
        upload_client = _RecordingUploadClient()

        gs._multipart_upload(upload_client, str(source), 'bucket', 'key',
                             gs.GcsTransferConfig(multipart_chunksize=4))

        assert upload_client.parts == [(1, b'abcd'), (2, b'efgh'), (3, b'ij')]
        assert upload_client.completed_parts == [1, 2, 3]
        assert not upload_client.aborted

    def test_multipart_upload_aborts_on_failure(self, tmp_path):
        source = tmp_path / 'source'
        source.write_bytes(b'abcdefghij')
        upload_client = _RecordingUploadClient(fail_on_part=2)

        try:
            gs._multipart_upload(upload_client, str(source), 'bucket', 'key',
                                 gs.GcsTransferConfig(multipart_chunksize=4))
            assert False, 'the upload failure shall be raised'
        except RuntimeError:
            pass

        assert upload_client.aborted

    def test_multipart_download_writes_ranges_and_reports_progress(self, tmp_path):
        content = b'abcdefghij'
        destination = tmp_path / 'destination'
        progress = []

        gs._multipart_download(_RangeDownloadClient(content), 'bucket', 'key', str(destination), len(content),
                               gs.GcsTransferConfig(multipart_chunksize=4), callback=progress.append)

        assert destination.read_bytes() == content
        assert progress == [4, 4, 2]


class TestAzureClient(object):

    @patch.object(azure.StorageOperations, 'get_proxy_config', return_value=PROXIES)
    def test_client_uses_proxies(self, _):
        client = azure._RefreshingBlobServiceClient('bucket', True, False,
                                                    refresh_credentials=lambda bucket, read, write: _credentials())

        assert client._client._config.proxy_policy.proxies == PROXIES

    @patch.object(azure.StorageOperations, 'get_proxy_config', return_value='http://proxy.example:3128')
    def test_client_uses_proxy_url(self, _):
        client = azure._RefreshingBlobServiceClient('bucket', True, False,
                                                    refresh_credentials=lambda bucket, read, write: _credentials())

        assert client._client._config.proxy_policy.proxies == PROXIES

    @patch.object(azure.StorageOperations, 'get_proxy_config', return_value=None)
    def test_client_without_proxy(self, _):
        client = azure._RefreshingBlobServiceClient('bucket', True, False,
                                                    refresh_credentials=lambda bucket, read, write: _credentials())

        assert not client._client._config.proxy_policy.proxies


class _RecordingUploadClient(object):

    def __init__(self, fail_on_part=None):
        self.parts = []
        self.completed_parts = None
        self.aborted = False
        self._fail_on_part = fail_on_part

    def create_multipart_upload(self, Bucket, Key):
        pass

    def upload_part(self, Bucket, Key, UploadId, PartNumber, Body):
        if PartNumber == self._fail_on_part:
            raise RuntimeError('upload failure')
        self.parts.append((PartNumber, Body.read()))

    def complete_multipart_upload(self, Bucket, Key, UploadId, MultipartUpload):
        self.completed_parts = [part['PartNumber'] for part in MultipartUpload['Parts']]

    def abort_multipart_upload(self, Bucket, Key):
        self.aborted = True


class _RangeDownloadClient(object):

    def __init__(self, content):
        self._content = content

    def get_object(self, Bucket, Key, Range):
        start, end = (int(value) for value in Range.replace('bytes=', '').split('-'))
        return {'Body': io.BytesIO(self._content[start:end + 1])}
