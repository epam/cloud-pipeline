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
Tests of the layer over the platform's own API client.

The HTTP, the retries and the envelope belong to `pipeline.PipelineAPI` and are not tested here. What is
tested is everything this service adds on top, because that is where its own behaviour lives:

  * a refusal is told apart from a failure to answer - the task queue breaks without that;
  * claiming goes through the client that does not retry;
  * the request bodies match what the API expects;
  * every answer comes back as this service's own model.

Neither pipe-common nor a network is needed: the clients are injected. `APIError` is declared here with
exactly that name on purpose - `api._refused` recognises it by name rather than by import, so a stub
named the same way exercises the real code path.
"""

import datetime

import pytest

from sats.api import CloudPipelineApi
from sats.errors import ApiError, ApiRejected
from sats.model import AsyncTaskType, MountType, TaskStatus


class APIError(Exception):
    """Stands in for pipe-common's `APIError`: the API answered, and the answer is "no"."""


class _FakeClient:
    """A `PipelineAPI`, as far as the layer above it can tell."""

    def __init__(self, payload=None, raises=None):
        self.payload = payload
        self.raises = raises
        self.calls = []

    def _answer(self, name, *args, **kwargs):
        self.calls.append((name, args, kwargs))
        if self.raises is not None:
            raise self.raises
        return self.payload

    def get_preference(self, name):
        return self._answer('get_preference', name)

    def claim_next_async_task(self, task_type):
        return self._answer('claim_next_async_task', task_type)

    def load_async_task(self, task_id):
        return self._answer('load_async_task', task_id)

    def filter_async_tasks(self, task_filter):
        return self._answer('filter_async_tasks', task_filter)

    def update_async_task_status(self, task_id, status, result=None, error_message=None):
        return self._answer('update_async_task_status', task_id, status, result=result,
                            error_message=error_message)

    def load_datastorage(self, storage_id):
        return self._answer('load_datastorage', storage_id)

    def load_file_share_mount(self, share_id):
        return self._answer('load_file_share_mount', share_id)

    def get_region(self, region_id):
        return self._answer('get_region', region_id)


class _FakeShareMount:
    """pipe-common answers with an object here rather than with a dictionary."""

    def __init__(self):
        self.id = 1
        self.region_id = 2
        self.mount_root = '10.0.0.1:/export'
        self.mount_options = 'vers=4.1'
        self.mount_type = 'NFS'


def _api(client=None, claim_client=None) -> CloudPipelineApi:
    return CloudPipelineApi('https://api.example.com/pipeline/restapi', 'token',
                            client=client or _FakeClient(),
                            claim_client=claim_client or _FakeClient())


TASK_PAYLOAD = {
    'id': 'task-1',
    'type': 'DOWNLOAD_ARCHIVE',
    'status': 'RUNNING',
    'owner': 'USER',
    'params': {'type': 'DOWNLOAD_ARCHIVE', 'storageId': 437, 'paths': ['data/']},
}


# ---------------------------------------------------------------- how the clients are built

def test_builds_the_claiming_client_with_a_single_attempt(monkeypatch):
    # The property the module leans on hardest: a retried claim takes a second task and orphans the
    # first. Nothing else asserts it, because the two clients are injected everywhere else.
    built = []
    monkeypatch.setattr('sats.api.build_client',
                        lambda url, token, attempts, backoff, timeout: built.append(attempts))

    CloudPipelineApi('https://api.example.com/pipeline/restapi', 'token', attempts=3)

    assert built == [3, 1]


def test_says_so_when_tls_verification_cannot_be_honoured(caplog):
    # The flag exists and the platform's client passes `verify=False` on every call regardless, so the
    # only honest thing is to say it out loud rather than pretend.
    with caplog.at_level('WARNING'):
        _api_with_verification = CloudPipelineApi('https://api.example.com/pipeline/restapi', 'token',
                                                  verify_ssl=True, client=_FakeClient(),
                                                  claim_client=_FakeClient())

    assert _api_with_verification is not None
    assert 'certificates will not be verified' in caplog.text


# ---------------------------------------------------------------- claiming

def test_claims_through_the_client_that_does_not_retry():
    # The one call in this service that must never be repeated: the API hands the task over and marks
    # it RUNNING in one transaction, so a retried request claims a second task and orphans the first.
    client, claim_client = _FakeClient(), _FakeClient(TASK_PAYLOAD)
    api = _api(client, claim_client)

    task = api.claim_next(AsyncTaskType.DOWNLOAD_ARCHIVE)

    assert claim_client.calls == [('claim_next_async_task', ('DOWNLOAD_ARCHIVE',), {})]
    assert client.calls == []
    assert task.id == 'task-1'
    assert task.type is AsyncTaskType.DOWNLOAD_ARCHIVE


def test_an_empty_queue_is_an_answer_rather_than_a_failure():
    api = _api(claim_client=_FakeClient(payload=None))

    assert api.claim_next(AsyncTaskType.DOWNLOAD_ARCHIVE) is None


# ---------------------------------------------------------------- the two kinds of failure

def test_a_refusal_is_not_retryable():
    api = _api(_FakeClient(raises=APIError('ERROR: Task cannot move to SUCCESS')))

    with pytest.raises(ApiRejected) as refusal:
        api.report_status('task-1', TaskStatus.SUCCESS, result={})

    # `ApiRejected` is what tells a worker that somebody else has already finished its task, so it
    # cleans up quietly instead of retrying. The API's own words have to survive into the message.
    assert 'cannot move to SUCCESS' in str(refusal.value)


def test_a_transport_failure_is_retryable():
    api = _api(_FakeClient(raises=RuntimeError('Connection aborted')))

    with pytest.raises(ApiError) as failure:
        api.load_task('task-1')

    assert not isinstance(failure.value, ApiRejected)
    assert 'Connection aborted' in str(failure.value)


# ---------------------------------------------------------------- request bodies

def test_sends_a_result_for_a_success_and_nothing_else():
    client = _FakeClient(TASK_PAYLOAD)
    api = _api(client)

    api.report_status('task-1', TaskStatus.SUCCESS, result={'archiveSizeBytes': 10})

    name, args, kwargs = client.calls[0]
    assert (name, args) == ('update_async_task_status', ('task-1', 'SUCCESS'))
    # The API refuses a request carrying the field that does not belong to the status, so an absent
    # error message has to stay absent rather than become null.
    assert kwargs == {'result': {'archiveSizeBytes': 10}, 'error_message': None}


def test_leaves_out_every_filter_field_that_was_not_asked_for():
    client = _FakeClient([TASK_PAYLOAD])
    api = _api(client)

    api.filter_tasks(task_type=AsyncTaskType.DOWNLOAD_ARCHIVE,
                     statuses=[TaskStatus.RUNNING, TaskStatus.STOPPING],
                     status_changed_before=datetime.datetime(2026, 10, 9, 6, 30, 35,
                                                             tzinfo=datetime.UTC),
                     page_size=200)

    _, (task_filter,), _ = client.calls[0]
    assert task_filter == {
        'type': 'DOWNLOAD_ARCHIVE',
        'statuses': ['RUNNING', 'STOPPING'],
        'statusChangedBefore': '2026-10-09 06:30:35.000',
        'pageSize': 200,
    }
    assert 'finishedBefore' not in task_filter


# ---------------------------------------------------------------- answers become the model

def test_reads_the_value_out_of_a_preference():
    api = _api(_FakeClient({'name': 'storage.download.archive.config', 'value': '{"a": 1}'}))

    assert api.get_preference('storage.download.archive.config') == '{"a": 1}'


def test_reads_a_storage_with_the_role_it_names():
    client = _FakeClient({'id': 437, 'name': 'test2222', 'type': 'S3', 'path': 'bucket/team',
                          'regionId': 1, 'tempCredentialsRole': 'arn:aws:iam::111:role/storage'})
    api = _api(client)

    storage = api.load_storage(437)

    assert client.calls[0][:2] == ('load_datastorage', (437,))
    assert storage.temp_credentials_role == 'arn:aws:iam::111:role/storage'
    assert storage.root == 'bucket'


def test_reads_a_region_by_its_numeric_id():
    client = _FakeClient({'id': 2, 'regionId': 'eu-central-1', 'provider': 'AWS',
                          'tempCredentialsRole': 'arn:aws:iam::222:role/temp'})
    api = _api(client)

    region = api.load_region(2)

    # The numeric id of the platform's region record goes in; the cloud's own name for the region
    # comes back as `code`, which is what a client signs for.
    assert client.calls[0][:2] == ('get_region', (2,))
    assert region.code == 'eu-central-1'
    assert region.temp_credentials_role == 'arn:aws:iam::222:role/temp'


def test_turns_the_platforms_share_mount_into_this_services_own():
    api = _api(_FakeClient(_FakeShareMount()))

    share = api.load_file_share(1)

    # The platform client keeps the mount type as a string; what this service needs is the enum, and
    # the protocol `mount -t` wants, which the enum carries.
    assert share.mount_type is MountType.NFS
    assert share.protocol == 'nfs'
    assert share.mount_root == '10.0.0.1:/export'


def test_a_share_mount_that_is_not_there_is_a_refusal():
    # The platform client answers with an empty dictionary rather than raising for this one.
    api = _api(_FakeClient({}))

    with pytest.raises(ApiRejected):
        api.load_file_share(99)
