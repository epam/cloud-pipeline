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
Tests of a worker's run, one end state per test.

What is being checked in almost every case is the *sequence of statuses* the worker reported, because
that sequence is the only thing anybody outside this service can observe about a task, and whether an
archive was left in the bucket.

`tmp_path` and `monkeypatch` are pytest fixtures: a test gets them by naming them as parameters, and
pytest provides them. `tmp_path` is a fresh directory per test, deleted afterwards; `monkeypatch`
replaces something for the duration of one test and puts it back, which is what makes replacing a
module-level function safe to do in a test suite.
"""

import zipfile

from sas.archive_bucket import archive_key
from sas.config import StorageDownloadArchiveConfig, ServiceConfig
from sas.model import ArchiveTask, Storage, StorageType, TaskStatus
from sas.worker.archive_preparation_worker import ArchivePreparationWorker

from .fakes import FakeApi, FakeBucket, FakeSource

TASK_ID = 'c0ffee-1'


def _service_config(tmp_path, status_poll_seconds=0):
    """
    Settings for one test.

    `status_poll_seconds=0` by default, so that the first stop check really asks the API. With the
    production value of fifteen seconds a test would have to sleep to see a cancellation noticed.
    """
    return ServiceConfig(
        api_url='https://api.example.com/pipeline/restapi',
        api_token='token',
        work_dir=tmp_path,
        slots=1,
        poll_interval_seconds=1,
        status_poll_seconds=status_poll_seconds,
        part_size_bytes=5 * 1024 * 1024,
        compress_level=1,
    )


def _archive_config(max_files=None, max_size=None, worker_minutes=10, prefix='archives/', storage=9):
    return StorageDownloadArchiveConfig(
        destination_storage_id=storage,
        destination_prefix=prefix,
        max_size_bytes=max_size,
        max_files_count=max_files,
        ttl_days=7,
        max_user_tasks=3,
        worker_max_running_minutes=worker_minutes,
        stuck_tasks_threshold_minutes=30,
    )


def _task(status=TaskStatus.RUNNING):
    return ArchiveTask(id=TASK_ID, status=status, owner='USER', storage_id=1, paths=('data/',))


def _run(tmp_path, monkeypatch, source, api=None, archive_config=None, bucket=None,
         service_config=None):
    """
    Runs one worker against the fakes and hands back everything a test might assert on.

    The `open_source` function is replaced rather than injected through the constructor. It is a
    module-level function the worker calls by name, and `monkeypatch.setattr` with a dotted string
    rebinds that name inside `sas.worker` only - the real factory is untouched everywhere else, and is
    restored when the test ends.
    """
    api = api or FakeApi(_task())
    archive_config = archive_config or _archive_config()
    bucket = bucket or FakeBucket()
    config = service_config or _service_config(tmp_path)
    monkeypatch.setattr('sas.worker.archive_preparation_worker.open_source', lambda *_args: source)
    ArchivePreparationWorker(api, config, archive_config, api.task, bucket=bucket).run()
    return api, bucket


def test_packs_the_selection_and_reports_success(tmp_path, monkeypatch):
    source = FakeSource({'a.txt': b'hello', 'nested/b.txt': b'world'})

    api, bucket = _run(tmp_path, monkeypatch, source)

    assert api.statuses == [TaskStatus.SUCCESS]
    # `sorted` because the order inside the archive follows the order of the listing, which is not
    # something a test should depend on.
    assert sorted(bucket.uploaded) == ['a.txt', 'nested/b.txt']
    status, result, error = api.reports[0]
    # The recorded location is part of the contract, not an extra: the API signs the link from it, and
    # the expiry cleanup deletes by it. Asserted as a whole dictionary so that a field going missing from
    # the report fails here rather than much later, in a bucket nobody cleans.
    assert result == {'type': 'DOWNLOAD_ARCHIVE',
                      'storageId': 9,
                      'path': archive_key('archives/', TASK_ID),
                      'archiveSizeBytes': bucket.size,
                      'skippedFiles': 0}
    assert error is None
    assert bucket.deleted == []
    # The source is released, and the scratch directory is gone. Both happen in a `finally`, and both
    # are the kind of thing that only breaks silently.
    assert source.closed
    assert not (tmp_path / TASK_ID).exists()


def test_names_the_unreadable_files_in_the_archive(tmp_path, monkeypatch):
    source = FakeSource({'a.txt': b'hello'},
                        unreadable={'cold.bin': 'stored in GLACIER and not restored'})

    api, bucket = _run(tmp_path, monkeypatch, source)

    assert api.statuses == [TaskStatus.SUCCESS]
    assert sorted(bucket.uploaded) == ['README.txt', 'a.txt']
    assert api.reports[0][1]['skippedFiles'] == 1


def test_a_file_that_cannot_be_read_costs_that_file_only(tmp_path, monkeypatch):
    # The case this behaviour exists for: a user asks for a thousand files and one of them has a
    # permission problem. They want the other nine hundred and ninety-nine.
    source = FakeSource({'a.txt': b'hello', 'denied.txt': b'secret'}, failing={'denied.txt'})

    api, bucket = _run(tmp_path, monkeypatch, source)

    assert api.statuses == [TaskStatus.SUCCESS]
    assert 'a.txt' in bucket.uploaded
    assert 'denied.txt' not in bucket.uploaded
    assert 'README.txt' in bucket.uploaded
    assert api.reports[0][1]['skippedFiles'] == 1


def test_the_archive_note_says_why_each_file_is_missing(tmp_path, monkeypatch):
    source = FakeSource({'a.txt': b'hello'}, unreadable={'cold.bin': 'stored in GLACIER'})

    api = FakeApi(_task())
    bucket = FakeBucket()
    monkeypatch.setattr('sas.worker.archive_preparation_worker.open_source', lambda *_args: source)

    # This one needs the archive itself rather than its listing, so the upload is captured by hand.
    captured = {}

    def capture(path, key, heartbeat):
        with zipfile.ZipFile(path) as archive:
            captured['note'] = archive.read('README.txt').decode()
        return path.stat().st_size

    bucket.upload = capture
    ArchivePreparationWorker(api, _service_config(tmp_path), _archive_config(), api.task, bucket=bucket).run()

    assert 'cold.bin: stored in GLACIER' in captured['note']


def test_refuses_a_selection_with_too_many_files(tmp_path, monkeypatch):
    source = FakeSource({'a.txt': b'1', 'b.txt': b'2', 'c.txt': b'3'})

    api, bucket = _run(tmp_path, monkeypatch, source, archive_config=_archive_config(max_files=2))

    assert api.statuses == [TaskStatus.FAILED]
    _, result, error = api.reports[0]
    assert result is None
    # The message is read by the user who asked for the archive, so it has to name the threshold that
    # was hit - that is the whole point of counting both thresholds in the executor.
    assert 'more than 2 files' in error
    assert bucket.uploaded is None


def test_refuses_a_selection_that_is_too_large(tmp_path, monkeypatch):
    source = FakeSource({'big.bin': b'0' * 100})

    api, bucket = _run(tmp_path, monkeypatch, source, archive_config=_archive_config(max_size=50))

    assert api.statuses == [TaskStatus.FAILED]
    assert 'larger than' in api.reports[0][2]
    assert bucket.uploaded is None


def test_an_unlimited_threshold_lets_everything_through(tmp_path, monkeypatch):
    # Both thresholds `None`, which is what an administrator removing them means.
    source = FakeSource({'a.txt': b'1', 'b.txt': b'2'})

    api, _ = _run(tmp_path, monkeypatch, source,
                  archive_config=_archive_config(max_files=None, max_size=None))

    assert api.statuses == [TaskStatus.SUCCESS]


def test_stops_and_cancels_when_the_task_was_cancelled(tmp_path, monkeypatch):
    source = FakeSource({'a.txt': b'hello'})
    api = FakeApi(_task(status=TaskStatus.CANCEL_REQUESTED))

    api, bucket = _run(tmp_path, monkeypatch, source, api=api)

    # STOPPING first, then CANCELLED. The intermediate status is not required by the task model, and it
    # is reported anyway: it tells the user that the service is cleaning up, and it restarts the
    # cleaner's stuck-task window so a long cleanup is not mistaken for a dead worker.
    assert api.statuses == [TaskStatus.STOPPING, TaskStatus.CANCELLED]
    assert bucket.uploaded is None
    assert not (tmp_path / TASK_ID).exists()


def test_fails_itself_when_it_runs_out_of_time(tmp_path, monkeypatch):
    # A zero minute limit means the deadline has already passed when the first check happens.
    source = FakeSource({'a.txt': b'hello'})

    api, _ = _run(tmp_path, monkeypatch, source, archive_config=_archive_config(worker_minutes=0))

    assert api.statuses == [TaskStatus.STOPPING, TaskStatus.FAILED]
    assert 'was not finished within 0 minutes' in api.reports[-1][2]


def test_reports_nothing_for_a_task_that_is_already_finished(tmp_path, monkeypatch):
    # The cleaner failed the task as stuck while this worker was still working. Every transition would
    # be refused, so the only thing left to do is to not leave an archive behind.
    source = FakeSource({'a.txt': b'hello'})
    api = FakeApi(_task(status=TaskStatus.FAILED))

    api, bucket = _run(tmp_path, monkeypatch, source, api=api)

    assert api.statuses == []
    assert bucket.uploaded is None


def test_deletes_the_archive_when_the_success_report_is_refused(tmp_path, monkeypatch):
    # The archive is uploaded and then nothing points at it. Leaving it would be a file no user can
    # reach and no cleanup will remove, because expiry only looks at tasks that did succeed.
    source = FakeSource({'a.txt': b'hello'})
    api = FakeApi(_task())
    api.reject_reports = True

    api, bucket = _run(tmp_path, monkeypatch, source, api=api)

    assert bucket.uploaded is not None
    assert bucket.deleted == [archive_key('archives/', TASK_ID)]


def test_an_unexpected_failure_still_ends_the_task(tmp_path, monkeypatch):
    # `run` must never raise and never leave a task RUNNING: it is the body of a pool task, and an
    # exception escaping it would be stored in a future nobody reads.
    source = FakeSource({'a.txt': b'hello'})
    bucket = FakeBucket()
    bucket.fail_upload = True

    api, bucket = _run(tmp_path, monkeypatch, source, bucket=bucket)

    assert api.statuses == [TaskStatus.FAILED]
    assert 'Internal error' in api.reports[0][2]
    assert not (tmp_path / TASK_ID).exists()


def test_keeps_the_users_own_readme_and_renames_the_note(tmp_path, monkeypatch):
    # The selection contains a file called exactly like the note. `zipfile` would write a second entry
    # under that name and most extraction tools keep the last one, so the user's own README would be
    # replaced by ours - silently, with the task still reporting success.
    source = FakeSource({'README.txt': b'the user wrote this'},
                        unreadable={'cold.bin': 'stored in GLACIER'})

    api = FakeApi(_task())
    bucket = FakeBucket()
    monkeypatch.setattr('sas.worker.archive_preparation_worker.open_source', lambda *_args: source)
    captured = {}

    def capture(path, key, heartbeat):
        with zipfile.ZipFile(path) as archive:
            captured['names'] = archive.namelist()
            captured['readme'] = archive.read('README.txt').decode()
        return path.stat().st_size

    bucket.upload = capture
    ArchivePreparationWorker(api, _service_config(tmp_path), _archive_config(), api.task, bucket=bucket).run()

    assert api.statuses == [TaskStatus.SUCCESS]
    assert captured['readme'] == 'the user wrote this'
    assert 'CLOUD-PIPELINE-SKIPPED-FILES.txt' in captured['names']
    # Exactly two entries: the user's file and the note under its fallback name.
    assert len(captured['names']) == 2


def test_fails_the_task_when_the_storage_is_gone(tmp_path, monkeypatch):
    # The user deletes the storage right after asking for the archive. The API refuses the load, and the
    # refusal must end the task with a message the user can act on - not be mistaken for "the API will
    # not accept anything about this task any more", which reports nothing and leaves it RUNNING until
    # the cleaner fails it hours later.
    source = FakeSource({'a.txt': b'hello'})
    api = FakeApi(_task())
    api.storage_missing = True

    api, bucket = _run(tmp_path, monkeypatch, source, api=api)

    assert api.statuses == [TaskStatus.FAILED]
    assert 'not available any more' in api.reports[0][2]
    assert bucket.uploaded is None


def test_refuses_a_task_that_names_no_paths(tmp_path, monkeypatch):
    source = FakeSource({})
    api = FakeApi(ArchiveTask(id=TASK_ID, status=TaskStatus.RUNNING, owner='USER', storage_id=1))

    api, _ = _run(tmp_path, monkeypatch, source, api=api)

    assert api.statuses == [TaskStatus.FAILED]
    assert 'nothing to archive' in api.reports[0][2]


def test_writes_under_the_configured_prefix_and_reports_where(tmp_path, monkeypatch):
    # The prefix is a preference, so the key cannot be a constant on either side. What makes a later
    # prefix change safe is that the worker reports the key it actually used.
    source = FakeSource({'a.txt': b'hello'})

    api, bucket = _run(tmp_path, monkeypatch, source, archive_config=_archive_config(prefix='team/archives/'))

    assert bucket.uploaded_key == f'team/archives/{TASK_ID}.zip'
    _, result, _ = api.reports[0]
    assert result['storageId'] == 9
    assert result['path'] == f'team/archives/{TASK_ID}.zip'


def test_writes_at_the_bucket_root_when_the_prefix_is_empty(tmp_path, monkeypatch):
    source = FakeSource({'a.txt': b'hello'})

    api, bucket = _run(tmp_path, monkeypatch, source, archive_config=_archive_config(prefix=''))

    assert bucket.uploaded_key == f'{TASK_ID}.zip'


def test_builds_the_destination_client_from_the_destination_storages_region(tmp_path, monkeypatch):
    # The one test that lets the worker resolve its own destination. Every other test injects a bucket,
    # which returns before this happens.
    source = FakeSource({'a.txt': b'hello'})
    api = FakeApi(_task())
    monkeypatch.setattr('sas.worker.archive_preparation_worker.open_source', lambda *_args: source)
    # The real `build_s3_client` runs - that is what is under test, and it reaches nothing but the fake
    # API - while the bucket around it is replaced, so no upload is attempted. The arguments of a call
    # are evaluated before the call, so replacing the class does not skip the client it is given.
    monkeypatch.setattr('sas.worker.archive_preparation_worker.ArchiveBucket',
                        lambda _bucket, _part_size, _client: FakeBucket())

    ArchivePreparationWorker(api, _service_config(tmp_path), _archive_config(), api.task).run()

    # The region of the storage `load_storage` answered with, and the source is faked out entirely -
    # so this one region read is the destination's own.
    assert api.region_requests == [api.storage.region_id]
    assert api.statuses == [TaskStatus.SUCCESS]


def test_fails_the_task_when_the_destination_is_not_an_object_storage(tmp_path, monkeypatch):
    # A misconfigured `destinationStorageId`. Without the check the failure comes out of the client
    # builder as "belongs to no cloud region", which names the wrong problem entirely.
    source = FakeSource({'a.txt': b'hello'})
    api = FakeApi(_task())
    api.storage = Storage(id=9, name='shared-fs', type=StorageType.NFS, path='10.0.0.1:/export')
    monkeypatch.setattr('sas.worker.archive_preparation_worker.open_source', lambda *_args: source)

    ArchivePreparationWorker(api, _service_config(tmp_path), _archive_config(), api.task).run()

    assert api.statuses == [TaskStatus.FAILED]
    assert 'is of type NFS' in api.reports[0][2]


def test_fails_the_task_when_the_destinations_region_cannot_be_read(tmp_path, monkeypatch):
    source = FakeSource({'a.txt': b'hello'})
    api = FakeApi(_task())
    api.region_missing = True
    monkeypatch.setattr('sas.worker.archive_preparation_worker.open_source', lambda *_args: source)

    ArchivePreparationWorker(api, _service_config(tmp_path), _archive_config(), api.task).run()

    # Reported as the task's own failure with the reason in it, not as an internal error: an
    # administrator reading it should see which storage and what the API said.
    assert api.statuses == [TaskStatus.FAILED]
    assert 'Cannot read cloud region' in api.reports[0][2]
    assert 'was not found' in api.reports[0][2]
