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
Maintenance nothing else does: finishing tasks whose workers are gone, expiring old archives, and
removing scratch directories nobody owns.

A worker ends its own task on every path it can reach, but not when the worker itself is killed. Such
a task stays `RUNNING` for ever, counts against its owner's quota, and may leave an archive nothing
points at.

Four cleanups, once per round of the main loop, in this order:

    1. a cancellation nobody confirmed    CANCEL_REQUESTED stuck -> STOPPING -> CANCELLED
    2. a task whose worker vanished       RUNNING or STOPPING stuck -> FAILED
    3. an archive past its time to live   SUCCESS older than ttlDays -> EXPIRED
    4. scratch directories nobody owns    deleted

"Stuck" is measured by the API's clock on its own `status_changed` column, so a restart of this
service resets nobody's timer and two replicas would agree.

Each cleanup is wrapped on its own: one failing must not cost the other three.
"""

from __future__ import annotations

import datetime
import shutil
import uuid
from collections.abc import Callable

from ..api import CloudPipelineApi
from ..datastorage_providers.s3 import ArchiveBucket, absolute_key, archive_key
from ..cloud.aws import build_s3_client
from ..config import ServiceConfig, StorageDownloadArchiveConfig
from ..util.errors import ApiError, ApiRejected
from ..util.log import get_logger
from ..model import ArchiveTask, AsyncTaskType, Storage, TaskStatus, utc_now

LOG = get_logger(__name__)

# Safety margin for a directory created just before the cleanup looked. Nothing to do with how long a
# task may run.
ORPHAN_GRACE_MINUTES = 30

# Upper bound per cleanup. A deployment that was down for a week comes back to a backlog, and working
# through all of it in one round would hold up the claiming of new tasks.
CLEANUP_PAGE_SIZE = 200


class Cleaner:
    """
    Runs the cleanups. Holds no state between rounds: everything comes from the API.

    `bucket_factory` takes a destination storage and gives back something that can delete from it. A
    function and not one bucket, because the storage is per task: after an administrator changes the
    destination, the cleanups work through tasks naming two different ones. The region and role of a
    delete are the storage's own - see `aws.build_s3_client`.
    """

    def __init__(self, api: CloudPipelineApi, config: ServiceConfig,
                 bucket_factory: Callable[[Storage], ArchiveBucket] | None = None):
        self._api = api
        self._config = config
        self._bucket_factory = bucket_factory or _default_bucket_factory(api, config)
        # Caches, so that a page of two hundred tasks does not cost two hundred `load_storage` calls.
        self._storages: dict[int, Storage] = {}
        self._buckets: dict[int, ArchiveBucket] = {}

    def cleanup(self, archive_config: StorageDownloadArchiveConfig, active_ids: set[str]) -> None:
        """
        One pass of all four cleanups.

        `active_ids` holds the tasks workers of *this* process are busy with. Only the directory
        cleanup needs it, and it needs it absolutely: a live worker's directory looks exactly like a
        dead one's.
        """
        cleanups = (
            lambda: self._finish_unconfirmed_cancellations(archive_config),
            lambda: self._fail_stuck_tasks(archive_config),
            lambda: self._expire_finished_tasks(archive_config),
            lambda: self._remove_orphan_directories(active_ids),
        )
        for cleanup in cleanups:
            try:
                cleanup()
            except Exception as error:
                # Broad on purpose: a cleanup is best-effort, and the loop must keep running.
                LOG.exception('A cleaner cleanup failed (%s), continuing with the rest', error)

    # ------------------------------------------------------------------ cleanup 1

    def _finish_unconfirmed_cancellations(self, archive_config: StorageDownloadArchiveConfig) -> None:
        """
        Finishes a cancellation whose worker never came back.

        Cancelling a running task only moves it to `CANCEL_REQUESTED` and waits for the worker to
        confirm, so a worker that died leaves the task there for ever.

        `CANCELLED` and not `FAILED`, because the user did ask for it. Two calls, because the task
        model has no direct `CANCEL_REQUESTED -> CANCELLED` edge.
        """
        stale = self._api.filter_tasks(
            task_type=AsyncTaskType.DOWNLOAD_ARCHIVE,
            statuses=[TaskStatus.CANCEL_REQUESTED],
            status_changed_before=self._stuck_before(archive_config),
            page_size=CLEANUP_PAGE_SIZE)
        for task in stale:
            LOG.warning('Task %s has been waiting to be cancelled since %s, finishing it here',
                        task.id, task.status_changed)
            if not self._move(task, TaskStatus.STOPPING):
                continue
            self._delete_archive(archive_config, task)
            self._move(task, TaskStatus.CANCELLED)

    # ------------------------------------------------------------------ cleanup 2

    def _fail_stuck_tasks(self, archive_config: StorageDownloadArchiveConfig) -> None:
        """
        Fails a task whose status has not changed for longer than `stuckTasksThresholdMinutes`.

        `RUNNING` and `STOPPING` each get their own window, since the threshold runs from the last
        status change: a worker deleting a large archive after announcing `STOPPING` is not stuck.

        This is why the threshold must exceed `workerMaxRunningMinutes` by a margin - see
        `StorageDownloadArchiveConfig.warnings`.

        The transition is reported before the archive is deleted. A worker that was only slow may
        report `SUCCESS` meanwhile; then the `FAILED` move is refused and the user keeps a working
        download. Deleting first would leave that task `SUCCESS` with its archive gone.
        """
        stuck = self._api.filter_tasks(
            task_type=AsyncTaskType.DOWNLOAD_ARCHIVE,
            statuses=[TaskStatus.RUNNING, TaskStatus.STOPPING],
            status_changed_before=self._stuck_before(archive_config),
            page_size=CLEANUP_PAGE_SIZE)
        for task in stuck:
            LOG.warning('Task %s has been %s since %s with nothing reported, failing it', task.id,
                        task.status, task.status_changed)
            moved = self._move(task, TaskStatus.FAILED,
                               error_message=f'The archive service stopped working on this task '
                                             f'without reporting a result, and no progress was made '
                                             f'for {archive_config.stuck_tasks_threshold_minutes} minutes. '
                                             f'Please try again.')
            if moved:
                self._delete_archive(archive_config, task)

    # ------------------------------------------------------------------ cleanup 3

    def _expire_finished_tasks(self, archive_config: StorageDownloadArchiveConfig) -> None:
        """
        Deletes archives older than `ttlDays` and marks their tasks `EXPIRED`.

        The object goes first: with the status first, a failed delete would leave a task saying the
        archive is gone while the object stayed, with nothing to find it by. This order is safe to
        repeat, since deleting an absent object succeeds.

        `EXPIRED` is also what makes the API stop offering a download link.
        """
        expired = self._api.filter_tasks(
            task_type=AsyncTaskType.DOWNLOAD_ARCHIVE,
            statuses=[TaskStatus.SUCCESS],
            finished_before=utc_now() - datetime.timedelta(days=archive_config.ttl_days),
            page_size=CLEANUP_PAGE_SIZE)
        for task in expired:
            LOG.info('Archive of task %s finished at %s, past its %s day lifetime', task.id,
                     task.finished, archive_config.ttl_days)
            if self._delete_archive(archive_config, task, uploaded=True):
                self._move(task, TaskStatus.EXPIRED)

    # ------------------------------------------------------------------ cleanup 4

    def _remove_orphan_directories(self, active_ids: set[str]) -> None:
        """
        Deletes scratch directories that no worker owns.

        A directory goes only when all three hold: no worker in this process has its task, it was
        last touched a while ago, and the API says the task is finished or unknown. Each alone would
        be wrong - the first misses a restart, the second a long task, the third races with a task
        claimed a moment ago.

        A directory whose name is not a task id is left alone: the work directory may be shared.
        """
        if not self._config.work_dir.exists():
            return
        cutoff = utc_now() - datetime.timedelta(minutes=ORPHAN_GRACE_MINUTES)
        for entry in self._config.work_dir.iterdir():
            if not entry.is_dir() or entry.name in active_ids:
                continue
            if not _looks_like_task_id(entry.name):
                LOG.debug('Leaving %s alone, its name is not a task id', entry)
                continue
            # The explicit zone makes the result comparable with ours; without it the comparison
            # would raise.
            touched = datetime.datetime.fromtimestamp(entry.stat().st_mtime, tz=datetime.UTC)
            if touched > cutoff:
                continue
            if self._is_task_unfinished(entry.name):
                continue
            LOG.warning('Removing scratch directory %s, which no worker owns', entry)
            shutil.rmtree(entry, ignore_errors=True)

    def _is_task_unfinished(self, task_id: str) -> bool:
        """
        Whether the task behind a directory name is still in play.

        `True` on an API failure: the cautious answer deletes nothing, and the next round asks again.
        """
        try:
            return not self._api.load_task(task_id).status.is_terminal
        except ApiRejected:
            # No such task, so nothing can claim the directory.
            return False
        except (ApiError, ValueError) as error:
            LOG.warning('Cannot tell whether task %s is finished (%s), leaving its directory alone',
                        task_id, error)
            return True

    # ------------------------------------------------------------------ helpers

    def _storage_of(self, storage_id: int) -> Storage:
        """A destination storage, asked for once per storage per process."""
        if storage_id not in self._storages:
            self._storages[storage_id] = self._api.load_storage(storage_id)
        return self._storages[storage_id]

    def _bucket_for(self, storage: Storage) -> ArchiveBucket:
        """
        The archive bucket of a destination storage, built once per storage per process.

        Caching the client is safe because its session is refreshable: botocore re-assumes the role
        before it expires. Without the cache, expiring two hundred archives would assume the role two
        hundred times. The cost is a renewable session held for as long as the process lives.
        """
        if storage.id not in self._buckets:
            self._buckets[storage.id] = self._bucket_factory(storage)
        return self._buckets[storage.id]

    @staticmethod
    def _stuck_before(archive_config: StorageDownloadArchiveConfig) -> datetime.datetime:
        """The moment a status change has to predate for its task to count as stuck."""
        return utc_now() - datetime.timedelta(minutes=archive_config.stuck_tasks_threshold_minutes)

    def _move(self, task: ArchiveTask, status: TaskStatus,
              error_message: str | None = None) -> bool:
        """
        Reports a status for a task the cleaner is finishing. Returns whether it was accepted.

        A refusal is expected: between the filter call and this one, a slow worker may have reported
        its own result, which is the better outcome.
        """
        try:
            self._api.report_status(task.id, status, error_message=error_message)
            return True
        except ApiRejected as rejected:
            LOG.info('Task %s would not move to %s (%s); it has probably just finished on its own',
                     task.id, status, rejected)
            return False
        except ApiError as error:
            LOG.warning('Cannot move task %s to %s (%s), will retry next round', task.id, status,
                        error)
            return False

    def _delete_archive(self, archive_config: StorageDownloadArchiveConfig, task: ArchiveTask,
                        uploaded: bool = False) -> bool:
        """
        Deletes the archive of a task, if it has one. Returns whether the bucket is now clean.

        **The recorded destination wins over the configured one.** A succeeded task carries the
        storage and key its own worker wrote to. Using the current setting instead would be wrong for
        every archive written before the destination last changed - and silently so, because S3
        answers the delete of an absent key with success.

        The configured destination is the fallback for a task that never reported.

        For a stuck or cancelled task nobody knows whether the upload happened, so the delete is
        issued either way. `uploaded` says the object certainly existed, which is what makes a
        failure worth an error rather than a warning.
        """
        storage_id = task.result_storage_id or archive_config.destination_storage_id
        if not storage_id:
            LOG.warning('Task %s records no storage and none is configured, cannot clean up after it',
                        task.id)
            return False
        relative = task.result_path or archive_key(archive_config.destination_prefix, task.id)
        bucket = None
        key = None
        try:
            storage = self._storage_of(storage_id)
            bucket = storage.root
            key = absolute_key(storage.prefix, relative)
            self._bucket_for(storage).delete(key)
            return True
        except Exception as error:
            log = LOG.error if uploaded else LOG.warning
            log('Cannot delete the archive s3://%s/%s of task %s (%s)', bucket, key, task.id, error)
            return False


def _looks_like_task_id(name: str) -> bool:
    """
    Whether a directory name could be a task id at all.

    The API assigns a UUID (`AsyncTaskEntity.assignId`). Without this check a `logs` or `lost+found`
    directory in a shared work directory would be asked about, refused by the API - which reads as
    "no such task" - and deleted. The second comparison rejects UUID forms the API never produces.
    """
    try:
        return str(uuid.UUID(name)) == name.lower()
    except ValueError:
        return False


def _default_bucket_factory(api: CloudPipelineApi,
                            config: ServiceConfig) -> Callable[[Storage], ArchiveBucket]:
    """
    Builds the function that makes an archive bucket for a destination storage.

    Each call reads the storage's region and assumes its role, so the cleaner calls it once per
    storage and keeps the result.
    """
    def factory(storage: Storage) -> ArchiveBucket:
        return ArchiveBucket(storage.root, config.part_size_bytes, build_s3_client(api, storage))
    return factory
