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
Tests of the parsing layer.
"""

import datetime

import pytest

from sats.model import ArchiveTask, AsyncTaskType, CloudRegion, Storage, StorageType, TaskStatus, \
    format_api_datetime, parse_api_datetime


def test_parses_a_platform_timestamp_as_utc():
    parsed = parse_api_datetime('2026-09-25 13:11:48.658')

    assert parsed == datetime.datetime(2026, 9, 25, 13, 11, 48, 658000, tzinfo=datetime.UTC)
    # The zone is the point of the assertion above, so it gets one of its own: a naive datetime would
    # compare equal to nothing here and would blow up the first time it met an aware one.
    assert parsed.tzinfo is not None


def test_formats_a_timestamp_with_exactly_three_fractional_digits():
    # The platform's pattern is `.SSS`, and `strftime('%f')` would produce six digits, which it
    # rejects. This is the test that pins the hand-rolled formatting in `format_api_datetime`.
    formatted = format_api_datetime(
        datetime.datetime(2026, 9, 25, 13, 11, 48, 658123, tzinfo=datetime.UTC))

    assert formatted == '2026-09-25 13:11:48.658'


def test_a_timestamp_survives_a_round_trip():
    original = '2026-10-09 06:30:35.000'

    assert format_api_datetime(parse_api_datetime(original)) == original


def test_reads_a_task_with_its_params():
    task = ArchiveTask.from_api({
        'id': 'e3b0c442',
        'status': 'RUNNING',
        'owner': 'USER',
        'created': '2026-10-09 06:30:35.000',
        'params': {
            'type': 'DOWNLOAD_ARCHIVE',
            'storageId': 42,
            'paths': ['data/', 'notes.txt'],
            'archiveName': 'results.zip',
        },
    })

    assert task.id == 'e3b0c442'
    assert task.status is TaskStatus.RUNNING
    assert task.storage_id == 42
    assert task.paths == ('data/', 'notes.txt')
    assert task.archive_name == 'results.zip'


def test_reads_a_task_without_params():
    # What the cleaner's filter returns in some orders of events, and what a task of another type would
    # look like if one existed. It has to parse, because the cleaner only needs the id and the status.
    task = ArchiveTask.from_api({'id': 'x', 'status': 'PENDING'})

    assert task.paths == ()
    assert task.archive_name == 'archive.zip'
    assert task.storage_id is None


def test_refuses_params_of_another_type():
    # `pytest.raises` is the "this must fail" assertion. Without the `with` block the test would pass
    # whether the call raised or not.
    with pytest.raises(ValueError):
        ArchiveTask.from_api({
            'id': 'x',
            'status': 'PENDING',
            'params': {'type': 'SOMETHING_ELSE'},
        })


def test_an_unknown_status_is_refused():
    with pytest.raises(ValueError):
        ArchiveTask.from_api({'id': 'x', 'status': 'CANCELED'})


# `CANCELED` appears twice on purpose: it is the misspelling the specification carried, and the
# API refuses it.
@pytest.mark.parametrize('status, terminal', [
    (TaskStatus.PENDING, False),
    (TaskStatus.RUNNING, False),
    (TaskStatus.CANCEL_REQUESTED, False),
    (TaskStatus.STOPPING, False),
    (TaskStatus.SUCCESS, True),
    (TaskStatus.FAILED, True),
    (TaskStatus.CANCELLED, True),
    (TaskStatus.EXPIRED, True),
])
def test_knows_which_statuses_are_terminal(status, terminal):
    assert status.is_terminal is terminal


@pytest.mark.parametrize('path, root, prefix', [
    ('bucket', 'bucket', ''),
    ('bucket/team', 'bucket', 'team'),
    ('bucket/team/project', 'bucket', 'team/project'),
])
def test_splits_a_storage_path_into_bucket_and_prefix(path, root, prefix):
    storage = Storage(id=1, name='s', type=StorageType.S3, path=path)

    assert storage.root == root
    assert storage.prefix == prefix


def test_strips_a_trailing_slash_from_a_storage_path():
    # A trailing slash left in the prefix produces an empty folder level in every key, and therefore in
    # every archive built from that storage.
    storage = Storage.from_api({'id': 1, 'name': 's', 'type': 'S3', 'path': 'bucket/team/'})

    assert storage.prefix == 'team'


def test_reads_the_recorded_archive_location_from_a_finished_task():
    # What the cleaner deletes by and the API signs from. Absent on an unfinished task, so both fields
    # are read with `get` and both are allowed to be missing.
    task = ArchiveTask.from_api({
        'id': 'task-1',
        'status': 'SUCCESS',
        'params': {'type': 'DOWNLOAD_ARCHIVE', 'storageId': 1, 'paths': ['data/']},
        'result': {'type': 'DOWNLOAD_ARCHIVE', 'storageId': 9,
                   'path': 'old-prefix/task-1.zip', 'archiveSizeBytes': 10,
                   'skippedFiles': 0},
    })

    assert task.result_storage_id == 9
    assert task.result_path == 'old-prefix/task-1.zip'


def test_reads_no_location_from_a_task_that_has_not_finished():
    task = ArchiveTask.from_api({'id': 'task-1', 'status': 'RUNNING'})

    assert task.result_storage_id is None
    assert task.result_path is None


def test_ignores_a_result_of_a_foreign_task_type():
    # Unlike foreign params, which are an error: the cleaner lists tasks, and a type this service does
    # not execute simply has no archive for it to clean up.
    task = ArchiveTask.from_api({
        'id': 'task-1',
        'status': 'SUCCESS',
        'result': {'type': 'SOMETHING_ELSE', 'storageId': 99, 'path': 'not/ours.zip'},
    })

    assert task.result_storage_id is None
    assert task.result_path is None


def test_reads_the_type_the_task_carries():
    task = ArchiveTask.from_api({'id': 'task-1', 'type': 'DOWNLOAD_ARCHIVE', 'status': 'RUNNING'})

    assert task.type is AsyncTaskType.DOWNLOAD_ARCHIVE


def test_a_task_without_a_type_falls_back_to_the_only_one_there_is():
    # The platform always sends it; the fallback keeps a task built by hand dispatchable. It is also
    # what will hide a mistake when a second type arrives.
    task = ArchiveTask.from_api({'id': 'task-1', 'status': 'RUNNING'})

    assert task.type is AsyncTaskType.DOWNLOAD_ARCHIVE


def test_a_type_this_service_does_not_know_is_refused():
    # `ValueError` rather than a silent default: the orchestrator catches it and leaves the task to the
    # cleaner, which is better than running the wrong worker over somebody's data.
    with pytest.raises(ValueError):
        ArchiveTask.from_api({'id': 'task-1', 'type': 'SOMETHING_ELSE', 'status': 'RUNNING'})


def test_reads_a_storages_own_credentials_role():
    # A storage in another account names the role it has to be reached through. Most name none and are
    # reached through their region's - see `aws.resolve_role_arn`.
    storage = Storage.from_api({
        'id': 437,
        'name': 'test2222',
        'type': 'S3',
        'path': 'test-bucket/team',
        'regionId': 1,
        'tempCredentialsRole': 'arn:aws:iam::111:role/storage',
    })

    assert storage.temp_credentials_role == 'arn:aws:iam::111:role/storage'
    assert storage.region_id == 1


def test_reads_a_region_keeping_its_two_ids_apart():
    # `regionId` in the JSON is the cloud's own region name, while `id` is the platform's numeric
    # one. The Java side swaps them, and confusing the two points a client at a region called "1".
    region = CloudRegion.from_api({
        'id': 2,
        'regionId': 'eu-central-1',
        'provider': 'AWS',
        'profile': 'cloud-pipeline',
        'iamRole': 'arn:aws:iam::222:role/iam',
        'tempCredentialsRole': 'arn:aws:iam::222:role/temp',
        's3Endpoint': 'https://minio.example.com',
    })

    assert region.id == 2
    assert region.code == 'eu-central-1'
    assert region.profile == 'cloud-pipeline'
    assert region.iam_role == 'arn:aws:iam::222:role/iam'
    assert region.temp_credentials_role == 'arn:aws:iam::222:role/temp'
    assert region.s3_endpoint == 'https://minio.example.com'


def test_reads_a_region_that_carries_none_of_the_aws_fields():
    # A region of another provider, or an AWS one in a single-account deployment. Everything optional
    # is absent, and the parsing must not insist on any of it.
    region = CloudRegion.from_api({'id': 2, 'regionId': 'us-east-1', 'provider': 'AZURE'})

    assert region.code == 'us-east-1'
    assert region.profile is None
    assert region.iam_role is None
    assert region.temp_credentials_role is None
    assert region.s3_endpoint is None
