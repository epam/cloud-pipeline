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
Tests of one extraction end to end, with the storages faked and real archives in between.

What is asserted here is the order of the steps, because that is what decides how much a refusal
costs:

  * a refusal **before the download** leaves the storage untouched and fetches nothing;
  * a refusal **in the collision pass** leaves the destination exactly as it was;
  * a failure **during the upload** leaves what it wrote, says how much, and removes nothing.

The source archive is deleted last of everything, so a failure always leaves the user their archive.
"""

import io
import tarfile
import zipfile
from pathlib import Path

import pytest

from sats.config import ServiceConfig
from sats.model import ArchiveTask, AsyncTaskType, Storage, StorageType, TaskStatus, UnpackParams
from sats.util.errors import StopReason, StopRequested
from sats.worker.unpack_archive_worker import UnpackArchiveWorker

from .fakes import FakeApi, FakeDestination, FakeSource, unpack_config

TASK_ID = '00000000-0000-4000-8000-000000000001'
ARCHIVE_PATH = 'incoming/data.zip'
CONTENT = {'notes.txt': b'hello', 'data/values.csv': b'1,2,3\n'}
TOTAL = sum(len(value) for value in CONTENT.values())

SOURCE_STORAGE = Storage(id=1, name='source-bucket', type=StorageType.S3, path='source', region_id=1)
DESTINATION_STORAGE = Storage(id=2, name='target-bucket', type=StorageType.S3, path='target',
                              region_id=1)


def _zip_bytes(content=None) -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        for name, value in (content or CONTENT).items():
            archive.writestr(name, value)
    return buffer.getvalue()


def _tar_bz2_bytes(content) -> bytes:
    # The one format that declares neither an entry count nor a total, so its limits can only be
    # enforced while it is being extracted.
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode='w:bz2') as archive:
        for name, value in content.items():
            info = tarfile.TarInfo(name)
            info.size = len(value)
            archive.addfile(info, io.BytesIO(value))
    return buffer.getvalue()


def _params(**overrides) -> UnpackParams:
    settings = dict(source_storage_id=1, source_path=ARCHIVE_PATH, destination_storage_id=2,
                    destination_path='out/')
    settings.update(overrides)
    return UnpackParams(**settings)


def _task(params: UnpackParams, status=TaskStatus.RUNNING) -> ArchiveTask:
    return ArchiveTask(id=TASK_ID, status=status, owner='USER', storage_id=None,
                       type=AsyncTaskType.UNPACK_ARCHIVE, unpack=params)


def _service_config(tmp_path, status_poll_seconds=15) -> ServiceConfig:
    return ServiceConfig(api_url='https://api', api_token='token', work_dir=tmp_path, slots=1,
                         status_poll_seconds=status_poll_seconds)


def _run(tmp_path, monkeypatch, params=None, archive=None, unpack=None, destination=None,
         source=None, api=None, status_poll_seconds=15):
    """
    Runs one task and hands back everything a test asserts on.

    The two provider factories are replaced rather than the providers themselves: the worker opens
    them by itself, and which storage each is opened for is part of what is being tested.
    """
    params = params or _params()
    archive = archive if archive is not None else _zip_bytes()
    source = source or FakeSource({params.source_path: archive})
    destination = destination if destination is not None else FakeDestination()
    api = api or FakeApi(_task(params))
    api.storages = {1: SOURCE_STORAGE, 2: DESTINATION_STORAGE}
    monkeypatch.setattr('sats.worker.unpack_archive_worker.open_source',
                        lambda _api, _storage, _config: source)
    monkeypatch.setattr('sats.worker.unpack_archive_worker.open_destination',
                        lambda _api, _storage, _config: destination)
    worker = UnpackArchiveWorker(api, _service_config(tmp_path, status_poll_seconds),
                                 unpack or unpack_config(), _task(params))

    worker.run()

    return api, source, destination, worker


def _failure(api: FakeApi) -> str:
    """The error message of the one FAILED report, which every refusal owes."""
    failures = [message for status, _, message in api.reports if status == TaskStatus.FAILED]
    assert len(failures) == 1, api.reports
    return failures[0]


# ---------------------------------------------------------------- the happy path

def test_extracts_into_the_destination_folder_and_reports_what_it_wrote(tmp_path, monkeypatch):
    api, _, destination, _ = _run(tmp_path, monkeypatch)

    # The key is the destination folder plus the path inside the archive: the archive's structure is
    # preserved and no folder of this service's own is added.
    assert sorted(destination.written) == ['out/data/values.csv', 'out/notes.txt']
    assert api.statuses == [TaskStatus.SUCCESS]
    result = api.reports[0][1]
    assert result == {'type': AsyncTaskType.UNPACK_ARCHIVE, 'destinationStorageId': 2,
                      'destinationPath': 'out/', 'filesWritten': 2, 'bytesWritten': TOTAL}


def test_an_empty_destination_path_is_the_root_of_the_storage(tmp_path, monkeypatch):
    # The API records the root as the empty string, and the key is a concatenation - so this is the
    # one case where the worker writes at the top level of the storage.
    _, _, destination, _ = _run(tmp_path, monkeypatch, params=_params(destination_path=''))

    assert sorted(destination.written) == ['data/values.csv', 'notes.txt']


def test_the_destination_folder_is_checked_before_the_first_upload(tmp_path, monkeypatch):
    _, _, destination, _ = _run(tmp_path, monkeypatch)

    assert destination.asserted_prefix == 'out/'


def test_a_destination_folder_that_is_not_there_fails_the_task(tmp_path, monkeypatch):
    # A share cannot be written into a folder that does not exist, and creating one would turn a typo
    # in the path into a folder nobody asked for.
    api, _, destination, _ = _run(tmp_path, monkeypatch,
                                  destination=FakeDestination(missing_prefix=True))

    assert destination.written == []
    assert 'does not exist' in _failure(api)


def test_the_scratch_directory_is_gone_afterwards(tmp_path, monkeypatch):
    _run(tmp_path, monkeypatch)

    assert not (tmp_path / TASK_ID).exists()


# ---------------------------------------------------------------- refused before the download

def test_an_archive_that_is_not_a_file_fails_without_fetching_anything(tmp_path, monkeypatch):
    api, source, destination, _ = _run(tmp_path, monkeypatch,
                                       source=FakeSource({'other.zip': _zip_bytes()}))

    assert source.heartbeats == 0
    assert destination.written == []
    assert 'not a file' in _failure(api)


def test_an_archive_over_the_size_limit_is_refused_before_it_is_fetched(tmp_path, monkeypatch):
    # The cheapest refusal there is: the size comes from the storage listing, so a 400 GB archive
    # costs nothing to turn down.
    api, source, _, _ = _run(tmp_path, monkeypatch,
                             unpack=unpack_config(max_archive_size_bytes=10))

    assert source.heartbeats == 0
    assert 'largest' in _failure(api)


def test_a_declared_entry_count_over_the_limit_is_refused_before_the_download(tmp_path, monkeypatch):
    # A zip keeps its index at the end of the file, so the count is known for the price of a few
    # ranged reads.
    api, source, _, _ = _run(tmp_path, monkeypatch, unpack=unpack_config(max_files_count=1))

    assert source.heartbeats == 0
    assert 'declares 2 entries' in _failure(api)


def test_a_declared_total_over_the_limit_is_refused_before_the_download(tmp_path, monkeypatch):
    api, source, _, _ = _run(tmp_path, monkeypatch, unpack=unpack_config(max_size_bytes=4))

    assert source.heartbeats == 0
    assert 'declares' in _failure(api)


def test_a_format_this_service_cannot_open_is_refused_before_the_download(tmp_path, monkeypatch):
    api, source, _, _ = _run(tmp_path, monkeypatch, archive=b'just text, no archive at all\n')

    assert source.heartbeats == 0
    assert 'ZIP' in _failure(api)


def test_real_content_over_the_limit_is_stopped_while_extracting(tmp_path, monkeypatch):
    # A tar.bz2 declares neither a count nor a total, so nothing could be refused up front: the byte
    # counter during the extraction is the only guard, and nothing reaches the destination.
    archive = _tar_bz2_bytes({'big.bin': b'x' * 5000})
    params = _params(source_path='incoming/data.tar.bz2')

    api, _, destination, _ = _run(tmp_path, monkeypatch, params=params, archive=archive,
                                  unpack=unpack_config(max_size_bytes=1000))

    assert destination.written == []
    assert 'content' in _failure(api)


# ---------------------------------------------------------------- the collision pass

def test_a_collision_leaves_the_destination_completely_untouched(tmp_path, monkeypatch):
    # One collision refuses the whole task, and the refusal comes before the first upload - so the
    # entry that would not have collided is not written either.
    destination = FakeDestination(existing={'out/notes.txt': b'older'})

    api, _, destination, _ = _run(tmp_path, monkeypatch, destination=destination)

    assert destination.written == []
    assert destination.existing == {'out/notes.txt': b'older'}
    message = _failure(api)
    assert 'overwrite' in message and 'Nothing was written' in message


def test_overwrite_replaces_the_colliding_entries_and_nothing_else(tmp_path, monkeypatch):
    destination = FakeDestination(existing={'out/notes.txt': b'older',
                                            'out/untouched.txt': b'mine'})

    api, _, destination, _ = _run(tmp_path, monkeypatch, destination=destination,
                                  params=_params(overwrite=True))

    assert api.statuses == [TaskStatus.SUCCESS]
    assert destination.existing['out/notes.txt'] == b'hello'
    assert destination.existing['out/untouched.txt'] == b'mine'
    assert destination.deleted == []


@pytest.mark.parametrize('overwrite', [False, True])
def test_a_folder_where_the_archive_holds_a_file_fails_either_way(tmp_path, monkeypatch, overwrite):
    # A share cannot hold both; S3 can, and then the GUI shows a file and a folder of one name while
    # `pipe mount` and a download to disk fail the way the share does. One behaviour for both.
    destination = FakeDestination(folders={'out/notes.txt'})

    api, _, destination, _ = _run(tmp_path, monkeypatch, destination=destination,
                                  params=_params(overwrite=overwrite))

    assert destination.written == []
    assert 'folder' in _failure(api)


def test_a_file_where_the_archive_holds_a_folder_fails(tmp_path, monkeypatch):
    archive = _zip_bytes()
    buffer = io.BytesIO(archive)
    with zipfile.ZipFile(buffer, 'a') as opened:
        opened.writestr('empty/', b'')
    destination = FakeDestination(existing={'out/empty': b'a file, not a folder'})

    api, _, destination, _ = _run(tmp_path, monkeypatch, archive=buffer.getvalue(),
                                  destination=destination, params=_params(overwrite=True))

    assert destination.written == []
    assert 'folder' in _failure(api)


@pytest.mark.parametrize('overwrite', [False, True])
def test_an_entry_landing_on_the_archive_itself_fails_even_with_overwrite(
        tmp_path, monkeypatch, overwrite):
    # Replacing the archive with a file from inside it makes two things go wrong: a later failure
    # leaves the user without the archive, and `deleteSourceOnSuccess` then deletes the extracted
    # file instead of the archive.
    params = _params(source_storage_id=1, destination_storage_id=1, destination_path='incoming/',
                     overwrite=overwrite)
    archive = _zip_bytes({'data.zip': b'inner', 'notes.txt': b'hello'})
    source = FakeSource({ARCHIVE_PATH: archive})

    api, _, destination, _ = _run(tmp_path, monkeypatch, params=params, archive=archive,
                                  source=source)

    assert destination.written == []
    message = _failure(api)
    assert 'data.zip' in message and 'archive itself' in message


def test_extracting_into_the_archives_own_folder_is_otherwise_allowed(tmp_path, monkeypatch):
    # Source and destination may be the same storage, and the same folder: the archive is deleted
    # after the success is reported, never before.
    params = _params(source_storage_id=1, destination_storage_id=1, destination_path='incoming/')

    api, _, destination, _ = _run(tmp_path, monkeypatch, params=params)

    assert api.statuses == [TaskStatus.SUCCESS]
    assert sorted(destination.written) == ['incoming/data/values.csv', 'incoming/notes.txt']


def test_a_file_that_appeared_after_the_collision_pass_fails_the_task(tmp_path, monkeypatch):
    # The write itself refuses a key that is taken, rather than a check before it: between the pass
    # and the upload somebody may create the file, and the whole point of the flag is that such a
    # file is not replaced.
    destination = FakeDestination(appearing={'out/notes.txt'})

    api, _, destination, _ = _run(tmp_path, monkeypatch, destination=destination)

    assert 'already exists' in _failure(api)


def test_an_empty_folder_of_the_archive_is_asked_for_in_the_destination(tmp_path, monkeypatch):
    buffer = io.BytesIO(_zip_bytes())
    with zipfile.ZipFile(buffer, 'a') as opened:
        opened.writestr('empty/', b'')

    _, _, destination, _ = _run(tmp_path, monkeypatch, archive=buffer.getvalue())

    assert destination.created_folders == ['out/empty']


# ---------------------------------------------------------------- the source archive

def test_the_source_archive_is_deleted_after_a_success_when_asked(tmp_path, monkeypatch):
    api, _, destination, _ = _run(tmp_path, monkeypatch,
                                  params=_params(delete_source_on_success=True))

    assert api.statuses == [TaskStatus.SUCCESS]
    # After the report, and through a destination opened on the source storage - deleting is a write.
    assert destination.deleted == [ARCHIVE_PATH]


def test_the_source_archive_stays_after_a_failure(tmp_path, monkeypatch):
    destination = FakeDestination(existing={'out/notes.txt': b'older'})

    api, _, destination, _ = _run(tmp_path, monkeypatch, destination=destination,
                                  params=_params(delete_source_on_success=True))

    assert api.statuses == [TaskStatus.FAILED]
    assert destination.deleted == []


# ---------------------------------------------------------------- failures and stopping

def test_a_failed_upload_keeps_what_it_wrote_and_says_how_much(tmp_path, monkeypatch):
    # No rollback, by design - so the message has to say what is in the destination, because that is
    # what decides what the user does next.
    destination = FakeDestination()
    # The second entry of the archive, so that one file is already in the destination when it fails.
    destination.fail_upload_of = 'out/data/values.csv'

    api, _, destination, _ = _run(tmp_path, monkeypatch, destination=destination)

    assert destination.deleted == []
    message = _failure(api)
    assert 'could not be written' in message
    assert '1 file(s) had already been written' in message
    assert 'overwrite' in message


def test_a_cancellation_ends_as_cancelled_and_removes_nothing(tmp_path, monkeypatch):
    destination = FakeDestination()
    uploaded = destination.upload

    def upload_then_cancel(local_path, key, heartbeat, overwrite=False):
        # A cancel noticed between two files, which is where the real watcher raises it.
        size = uploaded(local_path, key, heartbeat, overwrite=overwrite)
        if len(destination.written) == 1:
            raise StopRequested(StopReason.CANCELLED)
        return size

    destination.upload = upload_then_cancel

    api, _, destination, _ = _run(tmp_path, monkeypatch, destination=destination)

    assert api.statuses == [TaskStatus.STOPPING, TaskStatus.CANCELLED]
    # What it had written stays: a cancellation is not a rollback.
    assert len(destination.written) == 1
    assert destination.deleted == []


def test_a_task_already_finished_elsewhere_reports_nothing(tmp_path, monkeypatch):
    # The cleaner failed it as stuck while this worker was still going. Nothing it reported would be
    # accepted, so it only stops.
    api = FakeApi(_task(_params(), status=TaskStatus.FAILED))

    api, _, destination, _ = _run(tmp_path, monkeypatch, api=api, status_poll_seconds=0)

    assert api.reports == []
    assert destination.deleted == []


def test_a_task_without_parameters_fails_with_the_reason(tmp_path, monkeypatch):
    # The API refuses such a request, so this is a task written straight into the queue - or this
    # service and the API disagreeing about the payload.
    api = FakeApi(_task(_params()))
    api.storages = {1: SOURCE_STORAGE, 2: DESTINATION_STORAGE}
    monkeypatch.setattr('sats.worker.unpack_archive_worker.open_source',
                        lambda *_args: FakeSource({}))
    monkeypatch.setattr('sats.worker.unpack_archive_worker.open_destination',
                        lambda *_args: FakeDestination())
    task = ArchiveTask(id=TASK_ID, status=TaskStatus.RUNNING, owner='USER', storage_id=None,
                       type=AsyncTaskType.UNPACK_ARCHIVE, unpack=None)

    UnpackArchiveWorker(api, _service_config(tmp_path), unpack_config(), task).run()

    assert 'no extraction parameters' in _failure(api)


def test_a_storage_deleted_after_the_task_was_created_fails_the_task(tmp_path, monkeypatch):
    api = FakeApi(_task(_params()))
    api.storage_missing = True

    api, _, destination, _ = _run(tmp_path, monkeypatch, api=api)

    assert destination.written == []
    assert 'not available any more' in _failure(api)


def test_a_crafted_entry_name_fails_the_task_before_anything_is_written(tmp_path, monkeypatch):
    # The whole of the extraction's safety in one assertion: the API never opened the archive, so
    # this name was checked by this service alone.
    archive = _zip_bytes({'../../../../etc/cron.d/evil': b'* * * * * root sh -c evil'})

    api, _, destination, _ = _run(tmp_path, monkeypatch, archive=archive)

    assert destination.written == []
    assert 'will not write' in _failure(api)
    assert not Path('/etc/cron.d/evil').exists()


def test_a_failure_says_what_is_in_the_destination_exactly_once(tmp_path, monkeypatch):
    # Two places used to say it: the refusal that knows why, and the note that knows the count. The
    # user saw "Nothing was written. Nothing was written to the destination." The note is the one
    # that stays, because it is the only one that can report a number.
    destination = FakeDestination(existing={'out/notes.txt': b'older'})

    api, _, _, _ = _run(tmp_path, monkeypatch, destination=destination)

    message = _failure(api)
    assert message.count('Nothing was written') == 1
    assert message.endswith('Nothing was written to the destination.')
