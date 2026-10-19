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
Tests of how an archive's object key is built: the storage's own folder plus the preference prefix.
"""

import pytest

from sats.datastorage_providers.s3 import absolute_key, archive_key

TASK_ID = '4f1a2c90-0000-4000-8000-000000000001'


@pytest.mark.parametrize('prefix,expected', [
    ('archives/', f'archives/{TASK_ID}.zip'),
    ('team/archives/', f'team/archives/{TASK_ID}.zip'),
    # Empty is the root of the storage, and must not grow a leading slash.
    ('', f'{TASK_ID}.zip'),
])
def test_builds_the_storage_relative_key(prefix, expected):
    # This is the key the API derives too - see `DataStorageDownloadArchiveManager.archivePath`.
    assert archive_key(prefix, TASK_ID) == expected


@pytest.mark.parametrize('storage_prefix,relative,expected', [
    # A storage that is a whole bucket: nothing to prepend.
    ('', f'archives/{TASK_ID}.zip', f'archives/{TASK_ID}.zip'),
    # A storage that is a folder inside one.
    ('system', f'archives/{TASK_ID}.zip', f'system/archives/{TASK_ID}.zip'),
    ('system/data', f'archives/{TASK_ID}.zip', f'system/data/archives/{TASK_ID}.zip'),
    # The archive at the root of its storage.
    ('', f'{TASK_ID}.zip', f'{TASK_ID}.zip'),
    ('system', f'{TASK_ID}.zip', f'system/{TASK_ID}.zip'),
])
def test_prepends_the_storages_own_folder(storage_prefix, relative, expected):
    assert absolute_key(storage_prefix, relative) == expected
