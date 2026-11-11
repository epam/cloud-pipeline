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

from datetime import datetime, timedelta
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

import pytest
from boto3 import Session
from botocore.config import Config as AwsConfig

from src.utilities.storage import azure, gs, s3
from src.utilities.storage.s3 import S3BucketOperations, StorageItemManager, TransferBetweenBucketsManager

PROXY = 'http://proxy.example:3128'
PROXIES = {'http': PROXY, 'https': PROXY}
CA_BUNDLE = '/etc/pki/ca-trust/custom.pem'
ENDPOINT = 'https://s3.storage.example'


def _credentials(**kwargs):
    values = dict(session_token='token', secret_key='account', expiration=datetime.utcnow() + timedelta(hours=1))
    values.update(kwargs)
    return SimpleNamespace(**values)


def _session():
    return Session(aws_access_key_id='key', aws_secret_access_key='secret', region_name='us-east-1')


@pytest.fixture
def s3_config():
    """The boto config with the proxies and the CLI configuration with a CA bundle, as `pipe configure` sets them."""
    with patch.object(S3BucketOperations, 'get_boto_config', return_value=AwsConfig(proxies=PROXIES)), \
            patch.object(s3.Config, 'instance', return_value=SimpleNamespace(ca_bundle=CA_BUNDLE)):
        yield


def _http_session(client):
    return client._endpoint.http_session


class TestS3Clients(object):
    """The S3 clients keep the HTTP session botocore builds: it carries the proxies and the CA bundle."""

    def test_bucket_operations_client_keeps_proxies_and_ca_bundle(self, s3_config):
        client = S3BucketOperations._get_client(_session(), region_name='us-east-1')

        assert _http_session(client)._proxy_config._proxies == PROXIES
        assert _http_session(client)._verify == CA_BUNDLE

    def test_bucket_operations_client_does_not_verify_a_custom_endpoint(self, s3_config):
        client = S3BucketOperations._get_client(_session(), region_name='us-east-1', endpoint=ENDPOINT)

        assert _http_session(client)._proxy_config._proxies == PROXIES
        assert _http_session(client)._verify is False

    def test_item_manager_resource_and_client_keep_proxies_and_ca_bundle(self, s3_config):
        manager = StorageItemManager(_session(), region_name='us-east-1')

        for client in [manager.s3.meta.client, manager._get_client()]:
            assert _http_session(client)._proxy_config._proxies == PROXIES
            assert _http_session(client)._verify == CA_BUNDLE

    def test_transfer_source_client_keeps_proxies_and_ca_bundle(self, s3_config):
        manager = TransferBetweenBucketsManager(_session(), 'bucket', events=None, region_name='us-east-1')

        client = manager.build_source_client('eu-central-1', None)

        assert _http_session(client)._proxy_config._proxies == PROXIES
        assert _http_session(client)._verify == CA_BUNDLE


class TestGsClient(object):

    @patch.object(gs.StorageOperations, 'get_proxy_config', return_value=None)
    def test_client_is_created_with_temporary_credentials(self, _):
        client = gs._RefreshingClient('bucket', True, False,
                                      lambda bucket, read, write, versioning: _credentials(secret_key='project'))

        assert client.project == 'project'
        assert client._credentials.token == 'token'


class TestAzureClient(object):

    def _client(self):
        return azure._RefreshingBlobServiceClient('bucket', True, False,
                                                  refresh_credentials=lambda bucket, read, write: _credentials())

    @patch.object(azure.StorageOperations, 'get_proxy_config', return_value=PROXIES)
    def test_client_uses_proxies(self, _):
        assert self._client()._client._config.proxy_policy.proxies == PROXIES

    @patch.object(azure.StorageOperations, 'get_proxy_config', return_value=PROXY)
    def test_client_uses_proxy_url(self, _):
        assert self._client()._client._config.proxy_policy.proxies == PROXIES

    @patch.object(azure.StorageOperations, 'get_proxy_config', return_value=None)
    def test_client_without_proxy(self, _):
        assert not self._client()._client._config.proxy_policy.proxies


class TestAzureCredentialsRefresh(object):

    def test_sas_token_is_refreshed_before_a_request_of_a_created_client(self):
        import requests
        import requests_mock
        from azure.core.pipeline.transport import RequestsTransport
        from azure.storage.blob import BlobServiceClient

        tokens = iter(['sig=first', 'sig=second'])
        expirations = iter([datetime.utcnow() + timedelta(minutes=20), datetime.utcnow() + timedelta(hours=1)])
        refreshes = []

        def refresh(bucket, read, write):
            refreshes.append(bucket)
            return _credentials(session_token=next(tokens), expiration=next(expirations))

        session = requests.Session()
        adapter = requests_mock.Adapter()
        adapter.register_uri('HEAD', requests_mock.ANY, status_code=200,
                             headers={'x-ms-blob-type': 'BlockBlob', 'Content-Length': '3', 'ETag': '"0x1"',
                                      'Last-Modified': 'Wed, 11 Nov 2026 10:00:00 GMT'})
        session.mount('https://', adapter)

        def client(account_url, **kwargs):
            return BlobServiceClient(account_url, transport=RequestsTransport(session=session), **kwargs)

        with patch.object(azure.StorageOperations, 'get_proxy_config', return_value=None), \
                patch.object(azure, 'BlobServiceClient', side_effect=client):
            service = azure._RefreshingBlobServiceClient('bucket', True, False, refresh_credentials=refresh)
            blob_client = service.blob_client('container', 'blob')
            blob_client.get_blob_properties()
            # The credentials get close to their expiration while the client is in use
            service.credentials.expiration = datetime.utcnow() + timedelta(minutes=5)
            blob_client.get_blob_properties()

        assert len(refreshes) == 2
        signatures = [request.qs.get('sig') for request in adapter.request_history]
        assert signatures == [['first'], ['second']]


class TestAzureTransfers(object):

    def _service(self):
        service = MagicMock()
        blob_client = MagicMock()
        blob_client.start_copy_from_url.return_value = {'copy_status': 'success'}
        service.blob_client.return_value = blob_client
        service.credentials = _credentials(session_token='?sig=source', secret_key='account')
        return service, blob_client

    def _wrapper(self, path='file'):
        wrapper = MagicMock(path=path)
        wrapper.bucket.path = 'container'
        wrapper.is_file.return_value = True
        return wrapper

    @pytest.mark.parametrize('size, sync', [(100, True), (300 * 1024 * 1024, False), (0, False)])
    def test_copy_is_synchronous_for_the_small_blobs(self, size, sync):
        service, blob_client = self._service()
        manager = azure.TransferBetweenAzureBucketsManager(service, MagicMock())
        with patch.object(azure.AzureBucketOperations, 'get_blob_service', return_value=service), \
                patch.object(manager, '_destination_tags', return_value={}), \
                patch.object(manager, '_wait_for_copying') as wait:
            if not sync:
                blob_client.start_copy_from_url.return_value = {'copy_status': 'pending'}
            manager.transfer(self._wrapper(), self._wrapper(), path='file', relative_path='file', size=size,
                             quiet=True)

        assert blob_client.start_copy_from_url.call_args[1]['requires_sync'] is sync
        assert wait.called is not sync

    def test_upload_reports_progress(self, tmp_path):
        source = tmp_path / 'file'
        source.write_bytes(b'abc')
        service, blob_client = self._service()
        manager = azure.AzureUploadManager(service, MagicMock())
        progress = MagicMock()
        with patch.object(azure.StorageOperations, 'show_progress', return_value=True), \
                patch.object(azure.StorageOperations, 'generate_tags', return_value={}), \
                patch.object(azure.ProgressPercentage, '__init__', return_value=None), \
                patch.object(azure.ProgressPercentage, '__call__', progress):
            manager.transfer(self._wrapper(str(source)), self._wrapper('file'), relative_path='file', size=3)
            progress_hook = blob_client.upload_blob.call_args[1]['progress_hook']
            progress_hook(1, 3)
            progress_hook(3, 3)

        assert [call[0][0] for call in progress.call_args_list] == [1, 2]

    def test_download_uses_two_connections_and_reports_progress(self, tmp_path):
        service, blob_client = self._service()
        downloader = blob_client.download_blob.return_value
        downloader.readinto.side_effect = lambda stream: stream.write(b'abc')
        manager = azure.AzureDownloadManager(service, MagicMock())
        destination = tmp_path / 'file'
        with patch.object(azure.StorageOperations, 'show_progress', return_value=True), \
                patch.object(azure.ProgressPercentage, '__init__', return_value=None), \
                patch.object(azure.ProgressPercentage, '__call__'):
            manager.transfer(self._wrapper('file'), self._wrapper(str(destination)), path='file', size=3)

        kwargs = blob_client.download_blob.call_args[1]
        assert kwargs['max_concurrency'] == 2
        assert callable(kwargs['progress_hook'])
        assert destination.read_bytes() == b'abc'


def test_azure_progress_skips_a_late_smaller_total():
    reported = []
    with patch.object(azure.ProgressPercentage, '__init__', return_value=None), \
            patch.object(azure.ProgressPercentage, '__call__', side_effect=reported.append):
        progress = azure.AzureProgressPercentage('file', 10)
        # Two transfer threads report their totals out of order
        progress(4)
        progress(7)
        progress(6)
        progress(10)

    assert reported == [4, 3, 3]
