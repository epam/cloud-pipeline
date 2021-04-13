# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

from buckets.utils.cloud.aws_client import S3Client
from buckets.utils.cloud.azure_client import AzureClient
from buckets.utils.cloud.google_client import GsClient
from common_utils.cmd_utils import *

_clients = {
    S3Client.name: S3Client,
    AzureClient.name: AzureClient,
    GsClient.name: GsClient
}


def get_client():
    provider = os.environ['CP_PROVIDER']
    if provider in _clients:
        return _clients[provider]()
    else:
        raise RuntimeError('Provider must be one of %s' % _clients.keys())


def object_exists(bucket_name, key):
    return get_client().object_exists(bucket_name, key)


def get_versions(bucket_name, key):
    return get_client().get_versions(bucket_name, key)


def folder_exists(bucket_name, key):
    return get_client().folder_exists(bucket_name, key)


def assert_policy(bucket_name, sts, lts, backup_duration):
    return get_client().assert_policy(bucket_name, sts, lts, backup_duration)


def get_modification_date(path):
    return get_client().get_modification_date(path)


def wait_for_bucket_creation(bucket_name):
    return get_client().wait_for_bucket_creation(bucket_name)


def wait_for_bucket_deletion(bucket_name):
    return get_client().wait_for_bucket_deletion(bucket_name)
