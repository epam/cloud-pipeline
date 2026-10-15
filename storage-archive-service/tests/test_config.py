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
Tests of reading `storage.download.archive.config`.

The distinction worth testing here is between a field that is absent and a field that is present and
`null`: the first falls back to the platform's default, the second means "no limit". Getting it wrong
in either direction is bad - an unlimited archive, or a limit an administrator thought they had
removed.
"""

import json

import pytest

from sas.config import DEFAULT_DESTINATION_PREFIX, DEFAULT_MAX_FILES_COUNT, \
    DEFAULT_MAX_SIZE_BYTES, StorageDownloadArchiveConfig, normalise_prefix


@pytest.mark.parametrize('configured,expected', [
    # Absent or blank means the default, which is what every installation that never touched the
    # field is running on.
    (None, DEFAULT_DESTINATION_PREFIX),
    ('', DEFAULT_DESTINATION_PREFIX),
    ('   ', DEFAULT_DESTINATION_PREFIX),
    # One trailing slash, however it was written.
    ('archives', 'archives/'),
    ('archives/', 'archives/'),
    ('/archives', 'archives/'),
    ('/archives/', 'archives/'),
    ('team/archives', 'team/archives/'),
    # A lone slash is how the root of the bucket is asked for.
    ('/', ''),
])
def test_normalises_the_destination_prefix(configured, expected):
    # The API derives the same key when it signs a link for a task that never reported one, so the two
    # sides have to agree on every one of these - see `DataStorageDownloadArchiveManager.archivePrefix`.
    assert normalise_prefix(configured) == expected


def test_reads_the_destination_prefix_from_the_preference():
    config = StorageDownloadArchiveConfig.from_preference(
        {'destinationStorageId': 9, 'destinationPrefix': 'team/archives'})

    assert config.destination_prefix == 'team/archives/'


def test_falls_back_to_the_default_prefix_when_the_preference_omits_it():
    # The case of an installation whose preference was saved before the prefix existed as a field.
    config = StorageDownloadArchiveConfig.from_preference({'destinationStorageId': 9})

    assert config.destination_prefix == DEFAULT_DESTINATION_PREFIX


def test_reads_the_preference_as_the_json_string_the_api_sends():
    # The API answers `payload.value` as a string, because the platform stores every preference as
    # text. Accepting the string is the shape the orchestrator relies on.
    raw = json.dumps({
        'destinationStorageId': 9,
        'maxSizeBytes': 1024,
        'maxFilesCount': 10,
        'ttlDays': 2,
        'maxUserTasks': 1,
        'workerMaxRunningMinutes': 60,
        'stuckTasksThresholdMinutes': 120,
    })

    config = StorageDownloadArchiveConfig.from_preference(raw)

    assert config.destination_storage_id == 9
    assert config.max_size_bytes == 1024
    assert config.max_files_count == 10
    assert config.ttl_days == 2
    assert config.worker_max_running_minutes == 60
    assert config.stuck_tasks_threshold_minutes == 120
    assert config.is_usable


def test_an_absent_threshold_falls_back_to_the_platform_default():
    config = StorageDownloadArchiveConfig.from_preference({'destinationStorageId': 9})

    assert config.max_size_bytes == DEFAULT_MAX_SIZE_BYTES
    assert config.max_files_count == DEFAULT_MAX_FILES_COUNT


def test_a_threshold_set_to_null_means_no_limit():
    config = StorageDownloadArchiveConfig.from_preference(
        {'destinationStorageId': 9, 'maxSizeBytes': None, 'maxFilesCount': None})

    assert config.max_size_bytes is None
    assert config.max_files_count is None
    # And it is reported, because an unlimited archive is worth a line in the log even though it is
    # what the administrator asked for.
    assert any('maxFilesCount' in warning for warning in config.warnings())


def test_a_missing_preference_is_the_same_as_an_empty_one():
    # An administrator who clears the field leaves an empty string behind, which is not valid JSON.
    for raw in (None, '', '   ', '{}'):
        config = StorageDownloadArchiveConfig.from_preference(raw)

        assert not config.is_usable
        assert config.ttl_days > 0


def test_a_preference_without_a_bucket_cannot_be_used():
    config = StorageDownloadArchiveConfig.from_preference({'maxSizeBytes': 1})

    assert not config.is_usable
    assert any('destinationStorageId' in warning for warning in config.warnings())


def test_warns_when_the_stuck_threshold_does_not_exceed_the_worker_limit():
    # The combination that makes the cleaner fail tasks whose workers are alive and about to finish.
    config = StorageDownloadArchiveConfig.from_preference({
        'destinationStorageId': 9,
        'workerMaxRunningMinutes': 240,
        'stuckTasksThresholdMinutes': 240,
    })

    assert any('stuckTasksThresholdMinutes' in warning for warning in config.warnings())


def test_a_fully_configured_preference_warns_about_nothing():
    config = StorageDownloadArchiveConfig.from_preference({
        'destinationStorageId': 9,
        'maxSizeBytes': 107374182400,
        'maxFilesCount': 100000,
        'workerMaxRunningMinutes': 240,
        'stuckTasksThresholdMinutes': 360,
    })

    assert config.warnings() == []
