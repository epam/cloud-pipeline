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
Tests of reading `system.async.tasks.config`.

Two distinctions matter here. An absent field falls back to the default while a field present as
`null` means no limit - and a field read from the wrong level of the preference is absent, so the
level each one comes from is asserted rather than assumed.
"""

import json

import pytest

from sats.config import DEFAULT_DESTINATION_PREFIX, DEFAULT_MAX_FILES_COUNT, \
    DEFAULT_MAX_SIZE_BYTES, DEFAULT_MAX_USER_TASKS, DEFAULT_STUCK_TASKS_THRESHOLD_MINUTES, \
    DEFAULT_TTL_DAYS, StorageDownloadArchiveTaskConfig, normalise_prefix


def _preference(download=None, **shared):
    value = dict(shared)
    if download is not None:
        value['tasks'] = {'storageDownloadAsArchive': download}
    return value


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
    # sides have to agree on every one of these - see `DownloadArchiveTaskProcessor.archivePrefix`.
    assert normalise_prefix(configured) == expected


def test_reads_the_destination_prefix_from_the_download_section():
    config = StorageDownloadArchiveTaskConfig.from_preference(
        _preference({'destinationStorageId': 9, 'destinationPrefix': 'team/archives'}))

    assert config.destination_prefix == 'team/archives/'


def test_falls_back_to_the_default_prefix_when_the_section_omits_it():
    # The case of an installation whose preference was saved before the prefix existed as a field.
    config = StorageDownloadArchiveTaskConfig.from_preference(_preference({'destinationStorageId': 9}))

    assert config.destination_prefix == DEFAULT_DESTINATION_PREFIX


def test_reads_the_preference_as_the_json_string_the_api_sends():
    # The API answers `payload.value` as a string, because the platform stores every preference as
    # text. Accepting the string is the shape the orchestrator relies on.
    raw = json.dumps(_preference({
        'destinationStorageId': 9,
        'maxSizeBytes': 1024,
        'maxFilesCount': 10,
        'ttlDays': 2,
        'workerMaxRunningMinutes': 60,
    }, maxUserTasks=1, stuckTasksThresholdMinutes=120))

    config = StorageDownloadArchiveTaskConfig.from_preference(raw)

    assert config.destination_storage_id == 9
    assert config.max_size_bytes == 1024
    assert config.max_files_count == 10
    assert config.ttl_days == 2
    assert config.worker_max_running_minutes == 60
    assert config.max_user_tasks == 1
    assert config.stuck_tasks_threshold_minutes == 120
    assert config.is_usable


def test_reads_the_shared_values_from_the_top_level_and_not_from_the_section():
    config = StorageDownloadArchiveTaskConfig.from_preference(_preference(
        {'destinationStorageId': 9, 'maxUserTasks': 99, 'stuckTasksThresholdMinutes': 99},
        maxUserTasks=7, stuckTasksThresholdMinutes=600))

    assert config.max_user_tasks == 7
    assert config.stuck_tasks_threshold_minutes == 600


def test_a_download_setting_written_at_the_top_level_is_not_picked_up():
    config = StorageDownloadArchiveTaskConfig.from_preference(
        _preference({}, destinationStorageId=9, ttlDays=99))

    assert config.destination_storage_id is None
    assert config.ttl_days == DEFAULT_TTL_DAYS
    assert not config.is_usable


def test_a_section_written_without_the_tasks_wrapper_is_not_picked_up():
    config = StorageDownloadArchiveTaskConfig.from_preference(
        {'storageDownloadAsArchive': {'destinationStorageId': 9, 'ttlDays': 99}})

    assert config.destination_storage_id is None
    assert config.ttl_days == DEFAULT_TTL_DAYS
    assert not config.is_usable


def test_a_download_setting_written_directly_under_tasks_is_not_picked_up():
    # One level too shallow: `tasks` holds sections, never settings.
    config = StorageDownloadArchiveTaskConfig.from_preference(
        {'tasks': {'destinationStorageId': 9, 'ttlDays': 99}})

    assert config.destination_storage_id is None
    assert config.ttl_days == DEFAULT_TTL_DAYS
    assert not config.is_usable


def test_an_absent_threshold_falls_back_to_the_platform_default():
    config = StorageDownloadArchiveTaskConfig.from_preference(_preference({'destinationStorageId': 9}))

    assert config.max_size_bytes == DEFAULT_MAX_SIZE_BYTES
    assert config.max_files_count == DEFAULT_MAX_FILES_COUNT
    assert config.max_user_tasks == DEFAULT_MAX_USER_TASKS
    assert config.stuck_tasks_threshold_minutes == DEFAULT_STUCK_TASKS_THRESHOLD_MINUTES


def test_a_threshold_set_to_null_means_no_limit():
    config = StorageDownloadArchiveTaskConfig.from_preference(
        _preference({'destinationStorageId': 9, 'maxSizeBytes': None, 'maxFilesCount': None}))

    assert config.max_size_bytes is None
    assert config.max_files_count is None
    # And it is reported, because an unlimited archive is worth a line in the log even though it is
    # what the administrator asked for.
    assert any('maxFilesCount' in warning for warning in config.warnings())


def test_a_missing_preference_is_the_same_as_an_empty_one():
    # An administrator who clears the field leaves an empty string behind, which is not valid JSON.
    # The nested empties are members of the same family: the key exists and there is nothing in it.
    for raw in (None, '', '   ', '{}', '{"tasks": {}}',
                '{"tasks": {"storageDownloadAsArchive": {}}}'):
        config = StorageDownloadArchiveTaskConfig.from_preference(raw)

        assert not config.is_usable
        assert config.ttl_days > 0


@pytest.mark.parametrize('raw', [
    # Valid JSON that is not an object. Without the guard these fail on an attribute of `list` or
    # `int` rather than on the value, and every level of the nesting can be wrong on its own.
    '[1, 2, 3]',
    '42',
    '{"tasks": [1, 2, 3]}',
    '{"tasks": "storageDownloadAsArchive"}',
    '{"tasks": {"storageDownloadAsArchive": [1, 2, 3]}}',
    '{"tasks": {"storageDownloadAsArchive": "archives/"}}',
])
def test_a_preference_that_is_not_an_object_is_read_as_unset(raw):
    config = StorageDownloadArchiveTaskConfig.from_preference(raw)

    assert not config.is_usable
    assert config.ttl_days > 0


def test_a_preference_without_a_bucket_cannot_be_used():
    config = StorageDownloadArchiveTaskConfig.from_preference(_preference({'maxSizeBytes': 1}))

    assert not config.is_usable
    assert any('destinationStorageId' in warning for warning in config.warnings())


def test_warns_when_the_stuck_threshold_does_not_exceed_the_worker_limit():
    # The combination that makes the cleaner fail tasks whose workers are alive and about to finish.
    config = StorageDownloadArchiveTaskConfig.from_preference(_preference(
        {'destinationStorageId': 9, 'workerMaxRunningMinutes': 240},
        stuckTasksThresholdMinutes=240))

    assert any('stuckTasksThresholdMinutes' in warning for warning in config.warnings())


def test_every_warning_names_the_level_the_setting_is_read_from():
    # So that a setting put in the wrong section is recognisable from the log alone.
    config = StorageDownloadArchiveTaskConfig.from_preference(
        _preference({'maxSizeBytes': None, 'maxFilesCount': None}))

    warnings = config.warnings()
    assert warnings
    assert all('system.async.tasks.config' in warning for warning in warnings)
    assert all('tasks.storageDownloadAsArchive' in warning for warning in warnings)


def test_a_fully_configured_preference_warns_about_nothing():
    config = StorageDownloadArchiveTaskConfig.from_preference(_preference({
        'destinationStorageId': 9,
        'maxSizeBytes': 107374182400,
        'maxFilesCount': 100000,
        'workerMaxRunningMinutes': 240,
    }, maxUserTasks=3, stuckTasksThresholdMinutes=360))

    assert config.warnings() == []
