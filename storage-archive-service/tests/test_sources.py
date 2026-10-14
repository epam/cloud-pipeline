# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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
Tests of the factory that picks a source for a storage.

The S3 branch is the one with something to get wrong: it builds the client before the source, from the
storage's own cloud region, so the source it returns already talks to the right region through the right
role. The unsupported branch is here because a storage type nobody implemented has to fail with its type
named, rather than reach a source class that cannot read it.
"""

import pytest

from sas.config import ServiceConfig
from sas.errors import UnsupportedSource
from sas.model import ArchiveTask, Storage, StorageType, TaskStatus
from sas.sources import open_source
from sas.sources.s3 import S3Source

from .fakes import FakeApi


def _api() -> FakeApi:
    return FakeApi(ArchiveTask(id='task-1', status=TaskStatus.RUNNING, owner='USER', storage_id=437,
                               paths=('data/',)))


def _config(tmp_path):
    return ServiceConfig(api_url='https://api', api_token='token', work_dir=tmp_path, slots=1)


def test_opens_an_s3_source_with_a_client_for_that_storages_region(tmp_path):
    api = _api()
    storage = Storage(id=437, name='test2222', type=StorageType.S3, path='test-bucket',
                      region_id=3)

    source = open_source(api, storage, _config(tmp_path))

    assert isinstance(source, S3Source)
    # The region of this storage and no other: the client is built before the source, and the region
    # decides both the role it assumes and the region it signs for.
    assert api.region_requests == [3]


def test_refuses_a_storage_type_this_service_cannot_read(tmp_path):
    api = _api()
    storage = Storage(id=7, name='azure-storage', type=StorageType.AZ, path='container')

    with pytest.raises(UnsupportedSource) as refusal:
        open_source(api, storage, _config(tmp_path))

    # The type is in the message because it is the one thing the user needs to be told.
    assert 'AZ' in str(refusal.value)
    assert api.region_requests == []
