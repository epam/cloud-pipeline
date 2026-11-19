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
Tests of how an S3 client is built: which role it assumes, and which region it talks to.

Nothing reaches AWS: assumed credentials are deferred until a request needs signing, so what is
asserted is the configuration. Two private attributes are read because they are the only record of it.
"""

import pytest
from botocore.credentials import DeferredRefreshableCredentials

from sats.cloud.aws import ROLE_SESSION_NAME, build_s3_client, resolve_role_arn
from sats.util.errors import CredentialsUnavailable
from sats.model import ArchiveTask, CloudRegion, Storage, StorageType, TaskStatus

from .fakes import FakeApi

STORAGE_ID = 437


def _api() -> FakeApi:
    """A fake API with a task nothing in these tests looks at - `FakeApi` wants one."""
    return FakeApi(ArchiveTask(id='task-1', status=TaskStatus.RUNNING, owner='USER',
                               storage_id=STORAGE_ID, paths=('data/',)))


def _storage(region_id: int | None = 1, role: str | None = None,
             assumed: bool = False) -> Storage:
    return Storage(id=STORAGE_ID, name='test2222', type=StorageType.S3,
                   path='test-data-storage/team', region_id=region_id,
                   use_assumed_credentials=assumed, temp_credentials_role=role)


def _region(**fields) -> CloudRegion:
    defaults = {'id': 1, 'code': 'eu-central-1'}
    return CloudRegion(**{**defaults, **fields})


def _assumed_role(client) -> str | None:
    """The role the client would assume, or `None` if it was built without one."""
    credentials = client._request_signer._credentials
    if not isinstance(credentials, DeferredRefreshableCredentials):
        return None
    # The fetcher is bound as the refresh callable, so the role it would assume is on its instance.
    return credentials._refresh_using.__self__._role_arn


# ---------------------------------------------------------------- role resolution

def test_assumes_the_storages_own_role_when_it_insists_on_one():
    # `useAssumedCredentials` is the switch, and the role is then the storage's own - it usually lives
    # in another account, and that role is the only one which can reach it.
    role = resolve_role_arn(_storage(assumed=True, role='arn:aws:iam::111:role/storage'),
                            _region(temp_credentials_role='arn:aws:iam::222:role/temp',
                                    iam_role='arn:aws:iam::222:role/iam'))

    assert role == 'arn:aws:iam::111:role/storage'


def test_falls_back_to_the_regions_temp_role_for_a_storage_that_names_none():
    role = resolve_role_arn(_storage(assumed=True),
                            _region(temp_credentials_role='arn:aws:iam::222:role/temp',
                                    iam_role='arn:aws:iam::222:role/iam'))

    assert role == 'arn:aws:iam::222:role/temp'


def test_an_ordinary_storage_is_reached_through_the_regions_iam_role():
    # The ordinary case, since `useAssumedCredentials` is off by default. `iamRole` is what the
    # platform assumes to reach a storage, while `tempCredentialsRole` is what it narrows for a
    # user - and it is always set, so preferring it would silently use the wrong role.
    role = resolve_role_arn(_storage(role='arn:aws:iam::111:role/storage'),
                            _region(temp_credentials_role='arn:aws:iam::222:role/temp',
                                    iam_role='arn:aws:iam::222:role/iam'))

    assert role == 'arn:aws:iam::222:role/iam'


def test_falls_back_to_the_regions_storage_role_when_it_has_no_iam_role():
    # This service's own last step, and what makes a deployment work where only
    # `tempCredentialsRole` is filled in. The platform falls back to the host there instead.
    role = resolve_role_arn(_storage(), _region(temp_credentials_role='arn:aws:iam::222:role/temp'))

    assert role == 'arn:aws:iam::222:role/temp'


def test_no_role_when_the_region_records_none():
    # A single-account installation: nothing to assume, and the host's own access is the right answer.
    assert resolve_role_arn(_storage(), _region()) is None


# ---------------------------------------------------------------- the client

def test_assumes_the_role_of_the_storages_region():
    api = _api()
    api.region = _region(iam_role='arn:aws:iam::222:role/region')

    client = build_s3_client(api, _storage())

    assert _assumed_role(client) == 'arn:aws:iam::222:role/region'
    # Deferred, so no call to STS was made while building this client - and refreshing it later is
    # botocore's job rather than this service's.
    assert isinstance(client._request_signer._credentials, DeferredRefreshableCredentials)


def test_names_the_session_as_the_platform_does():
    api = _api()
    api.region = _region(iam_role='arn:aws:iam::222:role/region')

    client = build_s3_client(api, _storage())

    # So that a CloudTrail entry made by this service is recognisable as the platform's. The Java side
    # uses the same string, `AWSUtils.ROLE_SESSION_NAME`.
    fetcher = client._request_signer._credentials._refresh_using.__self__
    assert fetcher._assume_kwargs['RoleSessionName'] == ROLE_SESSION_NAME


def test_signs_for_the_region_of_the_storage():
    api = _api()
    api.region = _region(code='us-west-2', iam_role='arn:aws:iam::222:role/region')

    client = build_s3_client(api, _storage())

    # Read from the API and not guessed: a signature is computed for a region, so a client pointed at
    # the wrong one fails on the redirect rather than following it.
    assert client.meta.region_name == 'us-west-2'
    assert client.meta.endpoint_url == 'https://s3.us-west-2.amazonaws.com'


def test_each_storage_gets_its_own_region_and_role():
    # The case a single set of host credentials cannot express at all: one process, one region. Here the
    # source and the destination are in different regions behind different roles.
    api = _api()
    api.regions = {
        1: _region(id=1, code='eu-central-1', iam_role='arn:aws:iam::111:role/source'),
        2: _region(id=2, code='us-east-1', iam_role='arn:aws:iam::222:role/archive'),
    }

    source = build_s3_client(api, _storage(region_id=1))
    destination = build_s3_client(api, _storage(region_id=2))

    assert (source.meta.region_name, _assumed_role(source)) == ('eu-central-1',
                                                                'arn:aws:iam::111:role/source')
    assert (destination.meta.region_name, _assumed_role(destination)) == ('us-east-1',
                                                                          'arn:aws:iam::222:role/archive')


def test_uses_the_custom_endpoint_of_a_region_that_has_one():
    api = _api()
    api.region = _region(s3_endpoint='https://minio.example.com')

    client = build_s3_client(api, _storage())

    assert client.meta.endpoint_url == 'https://minio.example.com'
    # Path addressing, because such a service has no wildcard DNS entry per bucket.
    assert client.meta.config.s3['addressing_style'] == 'path'


def test_falls_back_to_the_hosts_credentials_without_a_role_or_profile():
    api = _api()
    api.region = _region()

    client = build_s3_client(api, _storage())

    # Nothing to assume, so nothing is deferred: the host's own credentials are used, which is the
    # right answer for a single-account installation and for nothing else.
    assert _assumed_role(client) is None
    assert client.meta.region_name == 'eu-central-1'


def test_refuses_a_region_profile_this_host_does_not_have():
    api = _api()
    api.region = _region(profile='cloud-pipeline')

    with pytest.raises(CredentialsUnavailable) as refusal:
        build_s3_client(api, _storage())

    # botocore raises `ProfileNotFound` out of the session constructor, which would reach the user as
    # an internal error of this service. Named instead, because an administrator can fix it.
    assert "profile 'cloud-pipeline'" in str(refusal.value)


def test_refuses_a_storage_that_belongs_to_no_region():
    api = _api()

    with pytest.raises(CredentialsUnavailable) as refusal:
        build_s3_client(api, _storage(region_id=None))

    assert 'belongs to no cloud region' in str(refusal.value)
    assert api.region_requests == []


def test_refuses_when_the_region_cannot_be_read():
    api = _api()
    api.region_missing = True

    with pytest.raises(CredentialsUnavailable) as refusal:
        build_s3_client(api, _storage())

    # Guessing the region from the environment is what this module exists to stop doing, so an
    # unreadable region ends the task with the reason named instead.
    assert 'Cannot read cloud region 1 of storage test2222' in str(refusal.value)
    assert 'was not found' in str(refusal.value)
