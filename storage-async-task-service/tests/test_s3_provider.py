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
Tests of how a selection is expanded into objects, including what is reported for a path that
produced nothing - it has to reach the archive's note.

The storage is `bucket/team`, a folder inside a bucket, because that is where the prefix handling can
go wrong.
"""

import io
import zipfile

import pytest

from sats.model import Storage, StorageType
from botocore.exceptions import ClientError

from sats.util.errors import CredentialsUnavailable, DestinationConflict
from sats.datastorage_providers.base import EntryKind
from sats.datastorage_providers.s3 import S3Destination, S3Source

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
    # Not the same as absent, and "nothing found" would send the user looking in the wrong place.
    # Listing is granted alongside reading here, so a 403 really is a denial.
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


def test_stat_answers_one_file_and_nothing_for_a_folder():
    # Apart from `list_files` because the caller was given one path - the archive of an unpack task -
    # and has to be able to tell "not a file" from "a folder of that name".
    client = FakeS3Client({'team/data.zip': 12, 'team/folder/inside.txt': 1})

    found = _source(client).stat('data.zip')

    assert found.size == 12
    assert found.locator == 'team/data.zip'
    assert _source(client).stat('folder') is None
    assert _source(client).stat('gone.zip') is None


def test_stat_reports_a_file_it_may_not_read():
    client = FakeS3Client({'team/secret.zip': 5}, denied={'team/secret.zip'})

    assert 'access denied' in _source(client).stat('secret.zip').skip_reason


def test_reads_a_zip_index_where_it_lies_in_a_few_requests():
    # The whole point of the ranged reader: a zip keeps its index at the end of the file, so what the
    # archive holds is answered without fetching it. The cost does not grow with the archive - a
    # 500 GB zip answers in the same handful of requests.
    archive = io.BytesIO()
    with zipfile.ZipFile(archive, 'w') as opened:
        for index in range(5):
            opened.writestr(f'file-{index}.txt', b'x' * 100000)
    content = archive.getvalue()
    client = FakeS3Client(bodies={'team/data.zip': content})
    source = _source(client)

    with source.open_stream(source.stat('data.zip')) as stream:
        with zipfile.ZipFile(stream) as opened:
            names = opened.namelist()
        requests = stream.requests
        read = sum(int(one.removeprefix('bytes=').split('-')[1])
                   - int(one.removeprefix('bytes=').split('-')[0]) + 1 for one in client.ranges)

    assert len(names) == 5
    assert requests <= 5
    # A fraction of the archive, and far less than one of its entries.
    assert read < len(content) / 10


def test_the_ranged_reader_answers_a_seek_past_the_end_with_nothing():
    # Legal on a file, and `zipfile` does it while looking for the end of the central directory.
    client = FakeS3Client(bodies={'team/data.zip': b'0123456789'})
    source = _source(client)

    with source.open_stream(source.stat('data.zip')) as stream:
        stream.seek(100)
        assert stream.read(10) == b''
        stream.seek(-4, io.SEEK_END)
        assert stream.read(4) == b'6789'


# ---------------------------------------------------------------- writing into a storage

def _destination(client: FakeS3Client) -> S3Destination:
    return S3Destination(_storage(), 8 * 1024 * 1024, client)


def test_tells_a_file_from_a_folder_from_nothing():
    # Three answers and not two: a file where the archive holds a folder is a conflict of its own,
    # and an object storage is the only backend that would allow it.
    client = FakeS3Client({'team/notes.txt': 1, 'team/data/values.csv': 2})
    destination = _destination(client)

    assert destination.kind('notes.txt') is EntryKind.FILE
    assert destination.kind('data') is EntryKind.FOLDER
    assert destination.kind('gone.txt') is EntryKind.MISSING


def test_writes_under_the_storages_own_folder(tmp_path):
    # Everything outside the provider speaks in storage-relative paths, so a storage that is a prefix
    # of a bucket behaves like one that is a whole bucket.
    client = FakeS3Client()
    local = tmp_path / 'notes.txt'
    local.write_bytes(b'hello')

    _destination(client).upload(local, 'out/notes.txt', lambda: None, overwrite=True)

    assert client.bodies['team/out/notes.txt'] == b'hello'


def test_a_write_that_may_not_replace_anything_is_conditional(tmp_path):
    # The condition is the write itself rather than a HEAD before it: between a check and a write
    # somebody may create the file, and the whole promise of `overwrite: false` is that such a file is
    # not replaced.
    client = FakeS3Client()
    local = tmp_path / 'notes.txt'
    local.write_bytes(b'hello')

    _destination(client).upload(local, 'out/notes.txt', lambda: None, overwrite=False)

    assert client.puts == [('team/out/notes.txt', True)]


def test_a_taken_key_is_reported_as_a_conflict_rather_than_a_client_error(tmp_path):
    # Left as a `ClientError` the failure would read to the user as an internal error of this service
    # instead of "that file is already there".
    client = FakeS3Client({'team/out/notes.txt': 5})
    local = tmp_path / 'notes.txt'
    local.write_bytes(b'hello')

    with pytest.raises(DestinationConflict) as refusal:
        _destination(client).upload(local, 'out/notes.txt', lambda: None, overwrite=False)

    assert 'already exists' in str(refusal.value)


def test_an_overwriting_write_is_unconditional(tmp_path):
    client = FakeS3Client({'team/out/notes.txt': 5})
    local = tmp_path / 'notes.txt'
    local.write_bytes(b'newer')

    _destination(client).upload(local, 'out/notes.txt', lambda: None, overwrite=True)

    assert client.puts == [('team/out/notes.txt', False)]


def test_an_empty_folder_of_the_archive_creates_nothing_on_an_object_storage():
    # An object storage has no folders, and this service does not invent storage content: the
    # alternative - a zero-length key ending in a slash - is drawn as a folder by some tools and as a
    # strange file by others.
    client = FakeS3Client()

    _destination(client).create_folder('out/empty')

    assert client.puts == []
    assert client.objects == {}


def test_a_rejected_role_is_reported_as_a_credentials_failure_and_not_as_access_denied():
    # botocore assumes the storage's role inside the signing path, so a rejected `AssumeRole` comes
    # out of the HEAD carrying status 403 - the same answer as an object the caller may not read. An
    # expired node role would otherwise tell every user "access denied" on their own files, and the
    # real cause would be in nobody's message.
    class _RefusingClient(FakeS3Client):
        def head_object(self, Bucket, Key):
            raise ClientError({'ResponseMetadata': {'HTTPStatusCode': 403},
                               'Error': {'Code': 'InvalidClientTokenId',
                                         'Message': 'The security token is invalid.'}},
                              'AssumeRole')

    with pytest.raises(CredentialsUnavailable) as refusal:
        _source(_RefusingClient()).stat('data.zip')

    assert 'could not obtain credentials' in str(refusal.value)


def test_a_real_access_denial_is_still_reported_against_the_object():
    # The other side of the same fork: a 403 from the HEAD itself is about the object.
    client = FakeS3Client({'team/secret.zip': 5}, denied={'team/secret.zip'})

    assert 'access denied' in _source(client).stat('secret.zip').skip_reason
