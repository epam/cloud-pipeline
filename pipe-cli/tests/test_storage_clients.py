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
from unittest.mock import patch

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
