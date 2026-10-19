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
Tests of the four cleanups - the only place a killed worker is exercised at all.
"""

import datetime
import os

from sats.config import StorageDownloadArchiveConfig, ServiceConfig
from sats.worker.cleaner import ORPHAN_GRACE_MINUTES, Cleaner
from sats.model import ArchiveTask, TaskStatus

from .fakes import FakeApi, FakeBucket


def _service_config(tmp_path):
    return ServiceConfig(api_url='https://api', api_token='token', work_dir=tmp_path, slots=1)


def _archive_config():
    return StorageDownloadArchiveConfig(destination_storage_id=9, destination_prefix='archives/',
                                        max_size_bytes=None,
                                        max_files_count=None, ttl_days=7, max_user_tasks=3,
                                        worker_max_running_minutes=240,
                                        stuck_tasks_threshold_minutes=360)


def _task(task_id, status):
    return ArchiveTask(id=task_id, status=status, owner='USER', storage_id=1, paths=('data/',))


def _cleaner(tmp_path, api, bucket):
    # The factory takes the destination storage, because the region and the role a delete goes through
    # are that storage's own. A test needs nothing from it and hands back one stub for every storage.
    return Cleaner(api, _service_config(tmp_path), bucket_factory=lambda _storage: bucket)


# Directory names have to be real task ids: the cleanup leaves anything else alone. The API
# assigns a UUID, so these are fixed ones, legible by their last digit.
def _uuid(last: int) -> str:
    return f'00000000-0000-4000-8000-00000000000{last}'


def test_fails_a_task_whose_worker_vanished(tmp_path):
    api = FakeApi(_task('stuck-1', TaskStatus.RUNNING))
    api.filtered = {TaskStatus.RUNNING: [_task('stuck-1', TaskStatus.RUNNING)]}
    bucket = FakeBucket()

    _cleaner(tmp_path, api, bucket).cleanup(_archive_config(), active_ids=set())

    assert api.statuses == [TaskStatus.FAILED]
    # The archive is deleted whether or not the dead worker got as far as uploading one: nobody can
    # tell, and a delete of a missing object succeeds.
    assert bucket.deleted == ['archives/stuck-1.zip']
    assert 'without reporting a result' in api.reports[0][2]


def test_keeps_the_archive_of_a_task_that_refused_to_fail(tmp_path):
    # The race this ordering exists for: a slow worker reported SUCCESS between the filter and the
    # move. The FAILED move is refused, and the archive the user can download is still there.
    api = FakeApi(_task('stuck-2', TaskStatus.RUNNING))
    api.filtered = {TaskStatus.RUNNING: [_task('stuck-2', TaskStatus.RUNNING)]}
    api.reject_reports = True
    bucket = FakeBucket()

    _cleaner(tmp_path, api, bucket).cleanup(_archive_config(), active_ids=set())

    assert bucket.deleted == []


def test_finishes_a_cancellation_nobody_confirmed(tmp_path):
    api = FakeApi(_task('cancel-1', TaskStatus.CANCEL_REQUESTED))
    api.filtered = {TaskStatus.CANCEL_REQUESTED: [_task('cancel-1', TaskStatus.CANCEL_REQUESTED)]}
    bucket = FakeBucket()

    _cleaner(tmp_path, api, bucket).cleanup(_archive_config(), active_ids=set())

    # Cancelled rather than failed: the user did ask for it. The task model has no direct
    # CANCEL_REQUESTED -> CANCELLED edge, which is why STOPPING is in the middle.
    assert api.statuses == [TaskStatus.STOPPING, TaskStatus.CANCELLED]
    assert bucket.deleted == ['archives/cancel-1.zip']


def test_expires_an_archive_past_its_lifetime(tmp_path):
    api = FakeApi(_task('old-1', TaskStatus.SUCCESS))
    api.filtered = {TaskStatus.SUCCESS: [_task('old-1', TaskStatus.SUCCESS)]}
    bucket = FakeBucket()

    _cleaner(tmp_path, api, bucket).cleanup(_archive_config(), active_ids=set())

    assert api.statuses == [TaskStatus.EXPIRED]
    assert bucket.deleted == ['archives/old-1.zip']


def test_expires_an_archive_from_the_location_its_worker_recorded(tmp_path):
    # Why the task carries its location at all. The destination has changed since, so a derived key
    # names an object that was never there - and deleting an absent key succeeds.
    old = ArchiveTask(id='old-3', status=TaskStatus.SUCCESS, owner='USER', storage_id=1,
                      paths=('data/',), result_storage_id=9,
                      result_path='previous-prefix/old-3.zip')
    api = FakeApi(old)
    api.filtered = {TaskStatus.SUCCESS: [old]}
    bucket = FakeBucket()
    cleaner = Cleaner(api, _service_config(tmp_path), bucket_factory=lambda _storage: bucket)

    cleaner.cleanup(_archive_config(), active_ids=set())

    assert bucket.deleted == ['previous-prefix/old-3.zip']
    assert api.statuses == [TaskStatus.EXPIRED]


def test_derives_the_location_for_a_task_that_recorded_none(tmp_path):
    # A worker that uploaded and then died recorded nothing, so the key derived from the task id is the
    # only guess available - and the configured prefix is what it is derived with.
    api = FakeApi(_task('stuck-3', TaskStatus.RUNNING))
    api.filtered = {TaskStatus.RUNNING: [_task('stuck-3', TaskStatus.RUNNING)]}
    bucket = FakeBucket()
    cleaner = Cleaner(api, _service_config(tmp_path), bucket_factory=lambda _storage: bucket)

    cleaner.cleanup(_archive_config(), active_ids=set())

    assert bucket.deleted == ['archives/stuck-3.zip']


def test_does_not_expire_a_task_whose_archive_could_not_be_deleted(tmp_path):
    # The object goes first: with the status first, a failed delete would leave the task saying the
    # archive is gone while the object stayed.
    api = FakeApi(_task('old-2', TaskStatus.SUCCESS))
    api.filtered = {TaskStatus.SUCCESS: [_task('old-2', TaskStatus.SUCCESS)]}
    bucket = FakeBucket()

    def refuse(_key):
        raise RuntimeError('access denied')

    bucket.delete = refuse

    _cleaner(tmp_path, api, bucket).cleanup(_archive_config(), active_ids=set())

    assert api.statuses == []


def test_removes_a_scratch_directory_no_worker_owns(tmp_path):
    api = FakeApi(_task(_uuid(1), TaskStatus.FAILED))
    orphan = tmp_path / _uuid(1)
    orphan.mkdir()
    (orphan / 'archive.zip').write_bytes(b'leftover')
    _age(orphan)

    _cleaner(tmp_path, api, FakeBucket()).cleanup(_archive_config(), active_ids=set())

    assert not orphan.exists()


def test_leaves_the_directory_of_a_running_worker_alone(tmp_path):
    # The case that makes `active_ids` necessary: a directory belonging to a live worker looks exactly
    # like one left behind by a dead one.
    api = FakeApi(_task(_uuid(2), TaskStatus.RUNNING))
    mine = tmp_path / _uuid(2)
    mine.mkdir()
    _age(mine)

    _cleaner(tmp_path, api, FakeBucket()).cleanup(_archive_config(), active_ids={_uuid(2)})

    assert mine.exists()


def test_leaves_a_directory_alone_while_its_task_is_unfinished(tmp_path):
    # A worker in another replica, or this one just after a restart. The API is the authority.
    api = FakeApi(_task(_uuid(3), TaskStatus.RUNNING))
    other = tmp_path / _uuid(3)
    other.mkdir()
    _age(other)

    _cleaner(tmp_path, api, FakeBucket()).cleanup(_archive_config(), active_ids=set())

    assert other.exists()


def test_leaves_a_fresh_directory_alone(tmp_path):
    # Not aged, so within the grace period: a task claimed between the cleanup's two steps.
    api = FakeApi(_task(_uuid(4), TaskStatus.FAILED))
    fresh = tmp_path / _uuid(4)
    fresh.mkdir()

    _cleaner(tmp_path, api, FakeBucket()).cleanup(_archive_config(), active_ids=set())

    assert fresh.exists()


def test_removes_the_directory_of_a_task_the_api_has_forgotten(tmp_path):
    api = FakeApi(_task(_uuid(5), TaskStatus.FAILED))
    api.task_missing = True
    unknown = tmp_path / _uuid(5)
    unknown.mkdir()
    _age(unknown)

    _cleaner(tmp_path, api, FakeBucket()).cleanup(_archive_config(), active_ids=set())

    assert not unknown.exists()


def test_leaves_a_directory_that_is_not_a_task_alone(tmp_path):
    # `--work-dir` may be shared. Asking the API about `lost+found` gets a refusal, which reads as
    # "no such task", so without the name check somebody else's directory would go.
    api = FakeApi(_task(_uuid(6), TaskStatus.FAILED))
    api.task_missing = True
    for name in ('lost+found', 'logs', 'archive-tmp'):
        stranger = tmp_path / name
        stranger.mkdir()
        _age(stranger)

    _cleaner(tmp_path, api, FakeBucket()).cleanup(_archive_config(), active_ids=set())

    assert sorted(entry.name for entry in tmp_path.iterdir()) == ['archive-tmp', 'logs', 'lost+found']


def test_one_failing_cleanup_does_not_cost_the_others(tmp_path):
    api = FakeApi(_task('old-3', TaskStatus.SUCCESS))
    api.filtered = {TaskStatus.SUCCESS: [_task('old-3', TaskStatus.SUCCESS)]}
    orphan = tmp_path / _uuid(7)
    orphan.mkdir()
    _age(orphan)

    # The first two cleanups blow up on the filter call, the third and fourth must still run.
    failing_statuses = {TaskStatus.CANCEL_REQUESTED, TaskStatus.RUNNING}
    original = api.filter_tasks

    def sometimes_failing(statuses=None, **kwargs):
        if set(statuses or []) & failing_statuses:
            raise RuntimeError('the API is having a moment')
        return original(statuses=statuses, **kwargs)

    api.filter_tasks = sometimes_failing

    _cleaner(tmp_path, api, FakeBucket()).cleanup(_archive_config(), active_ids=set())

    assert api.statuses == [TaskStatus.EXPIRED]
    assert not orphan.exists()


def test_builds_one_bucket_per_storage_and_keeps_it(tmp_path):
    # The bucket holds a client and its assumed session, so one per archive would mean a region read
    # and an assume-role per archive. This pins the "once per storage" part.
    expired = [_task(_uuid(n), TaskStatus.SUCCESS) for n in (1, 2, 3)]
    api = FakeApi(expired[0])
    api.filtered = {TaskStatus.SUCCESS: expired}
    bucket = FakeBucket()
    built = []

    def factory(storage):
        built.append(storage.id)
        return bucket

    cleaner = Cleaner(api, _service_config(tmp_path), bucket_factory=factory)
    cleaner.cleanup(_archive_config(), active_ids=set())

    assert len(bucket.deleted) == 3
    assert built == [api.storage.id]


def test_builds_its_client_from_the_destination_storages_region(tmp_path, monkeypatch):
    # The default factory, which every other test replaces. A delete goes through the region and the
    # role of the storage the archive is in, exactly as the upload did.
    expired = _task(_uuid(1), TaskStatus.SUCCESS)
    api = FakeApi(expired)
    api.filtered = {TaskStatus.SUCCESS: [expired]}
    # The real client is built - that is what is under test - and the bucket around it is replaced, so
    # nothing is sent anywhere.
    monkeypatch.setattr('sats.worker.cleaner.ArchiveBucket',
                        lambda _bucket, _part_size, _client: FakeBucket())

    Cleaner(api, _service_config(tmp_path)).cleanup(_archive_config(), active_ids=set())

    assert api.region_requests == [api.storage.region_id]


def _age(directory):
    """A timestamp that many minutes in the past."""
    past = (datetime.datetime.now(datetime.UTC)
            - datetime.timedelta(minutes=ORPHAN_GRACE_MINUTES + 10)).timestamp()
    os.utime(directory, (past, past))
