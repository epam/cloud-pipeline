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
Tests of how a selection is expanded into objects.

The subject is what the listing *reports*, including what it reports about a path that produced nothing.
A path the user ticked and did not get has to end up in the archive's note, because the alternative -
and what this service did until now - is an archive quietly missing a file with nothing anywhere saying
so.

The storage in these tests is `bucket/team`, a storage that is a folder inside a bucket rather than a
whole one, because that is where the prefix handling can go wrong: a path in a task is relative to the
storage, while a key in S3 is not.
"""

from sats.model import Storage, StorageType
from sats.datastorage_providers.s3 import S3Source

from .fakes import FakeS3Client

BUCKET = 'test-data-storage'


def _storage() -> Storage:
    return Storage(id=437, name='test2222', type=StorageType.S3, path=f'{BUCKET}/team',
                   region_id=1)


def _source(client: FakeS3Client) -> S3Source:
    return S3Source(_storage(), client)


def _listed(client: FakeS3Client, *paths: str) -> dict[str, str | None]:
    """The listing as `{path inside the archive: why it was skipped}`."""
    return {found.path: found.skip_reason for found in _source(client).list_files(paths)}


def test_reports_a_file_that_is_not_in_the_bucket():
    # The case that prompted all of this: a selection naming a file that is not there. It used to be
    # dropped without a word, so the user got an archive short of one file and no way to tell why.
    client = FakeS3Client({'team/notes.txt': 12})

    listed = _listed(client, 'notes.txt', 'gone.txt')

    assert listed['notes.txt'] is None
    assert listed['gone.txt'] == f'nothing found at s3://{BUCKET}/team/gone.txt'


def test_reports_a_folder_that_holds_nothing():
    # A missing prefix and an empty one are the same thing in S3, so one wording has to cover both.
    client = FakeS3Client({'team/data/a.txt': 1})

    listed = _listed(client, 'data/', 'empty/')

    # The file keeps its folder in its archive path, because that is the tree the user saw.
    assert listed['data/a.txt'] is None
    assert listed['empty'] == f'nothing found at s3://{BUCKET}/team/empty/'


def test_reports_a_file_that_is_there_but_forbidden():
    # Not the same as absent, and saying "nothing found" would send the user looking in the wrong
    # place. With the role this service assumes, listing is granted alongside reading, so a 403 on a
    # HEAD really is a denial.
    client = FakeS3Client({'team/secret.txt': 5}, denied={'team/secret.txt'})

    listed = _listed(client, 'secret.txt')

    assert listed['secret.txt'] == f'cannot be read at s3://{BUCKET}/team/secret.txt: access denied'


def test_expands_a_folder_into_its_files():
    client = FakeS3Client({'team/data/a.txt': 1, 'team/data/nested/b.txt': 2, 'team/other.txt': 3})

    listed = _listed(client, 'data/')

    # Storage-relative paths, which is what the user saw in the GUI and what goes into the archive.
    assert listed == {'data/a.txt': None, 'data/nested/b.txt': None}


def test_treats_a_folder_written_without_its_slash_as_a_folder():
    # The API normalises this, but a task created through the REST API directly may not have. The
    # fallback must not then report the folder as missing.
    client = FakeS3Client({'team/data/a.txt': 1})

    listed = _listed(client, 'data')

    assert listed == {'data/a.txt': None}


def test_skips_a_folder_marker():
    # A zero-byte key ending in a slash, which a console creates so that an empty folder shows up. It
    # is not a file, and packing it would make an archive entry with an empty name.
    client = FakeS3Client({'team/data/': 0, 'team/data/a.txt': 1})

    listed = _listed(client, 'data/')

    assert listed == {'data/a.txt': None}


def test_reports_an_object_archived_in_glacier():
    client = FakeS3Client({'team/cold.txt': 7}, storage_classes={'team/cold.txt': 'GLACIER'})

    listed = _listed(client, 'cold.txt')

    assert listed['cold.txt'] == 'stored in GLACIER and not restored'
