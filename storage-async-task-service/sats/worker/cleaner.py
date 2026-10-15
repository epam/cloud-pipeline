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
The cleanups that clean up after things that did not finish on their own.

A worker ends its own task on every path it can reach. What it cannot do is end a task when the worker
itself is gone - the pod was evicted, the process was killed, the node went away. The task stays
`RUNNING` for ever, counts against its owner's quota for ever, and if the worker had already uploaded,
its archive sits in the bucket with nothing pointing at it.

So nothing in this service relies on a worker getting to its own cleanup. Four cleanups, run once per
round of the main loop, in this order:

    1. a cancellation nobody confirmed    CANCEL_REQUESTED stuck -> STOPPING -> CANCELLED
    2. a task whose worker vanished       RUNNING or STOPPING stuck -> FAILED
    3. an archive past its time to live   SUCCESS older than ttlDays -> EXPIRED
    4. scratch directories nobody owns    deleted

The first three select tasks with one `POST /async-task/filter` each, by `statusChangedBefore` or
`finishedBefore`, which is why those two fields exist on the filter. Note what that means: "stuck" is
measured by the API's clock on the API's `status_changed` column, not by anything this service
remembers - so a restart of this service does not reset anybody's timer, and two replicas would agree
on which tasks are stuck.

Every cleanup is independent and each is wrapped on its own. One failing - a storage call timing out,
say - must not cost the other three, and the next round tries again anyway.
"""

from __future__ import annotations

import datetime
import shutil
import uuid
from collections.abc import Callable

from ..api import CloudPipelineApi
from ..archive_bucket import ArchiveBucket, absolute_key, archive_key
from ..cloud.aws import build_s3_client
from ..config import ServiceConfig, StorageDownloadArchiveConfig
from ..errors import ApiError, ApiRejected
from ..log import get_logger
from ..model import ArchiveTask, AsyncTaskType, Storage, TaskStatus, utc_now

LOG = get_logger(__name__)

# How long a scratch directory is left alone before it is treated as abandoned, on top of being
# unknown to every live worker. Purely a safety margin against a directory created a moment before the
# cleanup looked - a task claimed between the cleanup's two steps, for instance. It has nothing to do with
# how long a task may run.
ORPHAN_GRACE_MINUTES = 30

# Upper bound on how many tasks one cleanup handles. A deployment that has been down for a week comes
# back to a long backlog, and working through all of it in one round would hold the loop - and the
# claiming of new tasks - for as long as it takes. The rest is picked up by the next rounds.
CLEANUP_PAGE_SIZE = 200


class Cleaner:
    """
    Runs the cleanups. Holds no state between rounds: everything it needs comes from the API.

    `bucket_factory` takes the destination storage and gives back something that can delete from it. A
    function rather than one bucket, because the storage to clean up is per task: a task records where
    its archive went, and after an administrator changes the destination the cleanups are working through
    tasks that name two different ones. It takes the storage rather than a bucket name because the
    region and the role a delete goes through are the storage's own - see `aws.build_s3_client`. A test
    passes a function returning a stub.
    """

    def __init__(self, api: CloudPipelineApi, config: ServiceConfig,
                 bucket_factory: Callable[[Storage], ArchiveBucket] | None = None):
        self._api = api
        self._config = config
        self._bucket_factory = bucket_factory or _default_bucket_factory(api, config)
        # Storage id to the storage itself. A storage's path does not change in practice, and a cleanup
        # works through a page of up to two hundred tasks - without this, each one costs a
        # `load_storage`.
        self._storages: dict[int, Storage] = {}
        # Storage id to the bucket built for it, which holds a client and its credentials. See
        # `_bucket_for` for why keeping those is safe.
        self._buckets: dict[int, ArchiveBucket] = {}

    def cleanup(self, archive_config: StorageDownloadArchiveConfig, active_ids: set[str]) -> None:
        """
        One pass of all four cleanups.

        `active_ids` is the set of task ids workers of *this* process are busy with. Only the directory
        cleanup needs it, and it needs it absolutely: a directory belonging to a running worker looks
        exactly like one left behind by a dead one.

        The loop over a list of methods keeps the "one failure must not cost the others" rule in one
        place instead of four try blocks. Functions are ordinary values in Python, so a bound method
        can be put in a list and called later; `cleanup_method.__name__` is how the log line names
        whichever one failed.
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
                # Deliberately broad: a cleanup is best-effort maintenance, and the loop that calls it
                # must keep running. The traceback goes to the log so that a cleanup failing every round
                # is diagnosable rather than merely noisy.
                LOG.exception('A cleaner cleanup failed (%s), continuing with the rest', error)

    # ------------------------------------------------------------------ cleanup 1

    def _finish_unconfirmed_cancellations(self, archive_config: StorageDownloadArchiveConfig) -> None:
        """
        Finishes a cancellation whose worker never came back.

        `POST /async-task/{id}/cancel` on a running task does not end it: it moves the task to
        `CANCEL_REQUESTED` and waits for the worker to notice on its next poll, clean up, and confirm.
        A worker that died in the meantime never confirms, and the task would sit in
        `CANCEL_REQUESTED` for ever.

        The task is taken through `STOPPING` to `CANCELLED` rather than failed, because the user did ask
        for it to be cancelled and that is the honest outcome. The task model has no direct
        `CANCEL_REQUESTED -> CANCELLED` edge, which is why it is two calls.

        A worker that was merely slow rather than dead finds its task terminal on its next poll, stops,
        and deletes its own archive. Its `SUCCESS` report, if it gets that far, is refused.
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

        `RUNNING` and `STOPPING` each get their own window, because the threshold is measured from the
        last status change rather than from the start of the task - a worker that announces `STOPPING`
        and then spends a long time deleting a large archive is not stuck.

        This is the cleanup that makes the relation between the two settings matter. A worker stops itself
        at `workerMaxRunningMinutes` and then needs a poll interval, a cleanup and a report on top; if
        the stuck threshold is not comfortably larger, this cleanup fails tasks whose workers are alive
        and about to finish properly. `StorageDownloadArchiveConfig.warnings` says so at startup, and
        the worker's own
        reaction - noticing the terminal status and deleting the archive it just uploaded - is why it
        matters: the user would see a task fail for no visible reason.

        The transition is reported *before* the archive is deleted, and the order is the point. A page
        of two hundred tasks takes a while to work through, and a worker that was only slow may report
        `SUCCESS` during it - the task was selected as stale, but by the time its turn comes it has
        finished properly. Then the `FAILED` move is refused, and because nothing has been deleted yet
        the user keeps a working download. Deleting first would leave that task `SUCCESS` with its
        archive already gone.
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

        The object goes first and the status second, on purpose. If the status went first and the delete
        then failed, the task would say the archive is gone while the object stayed in the bucket, with
        nothing left to find it by. The other order is safe to repeat: deleting an object that is
        already gone succeeds, so the next round simply tries again.

        `EXPIRED` is also what tells the API to stop offering a download link, so the user sees that the
        archive is gone rather than a link that signs successfully and then answers 404.
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

        A worker deletes its own directory in a `finally`, so this only ever finds directories of
        workers that were killed outright. They matter because each holds up to a whole archive, and a
        few of them fill the disk the next task needs.

        A directory is removed only when all three hold: no worker in this process has its task, it was
        last touched a while ago, and the API says its task is finished or does not exist. Each of the
        three alone would be wrong - the first misses a restart, the second misses a long task, and the
        third races with a task claimed a moment ago.

        `iterdir` lists the directory's entries. A directory whose name is not a task id is left alone -
        see `_looks_like_task_id`: the work directory may be shared with something else, and deleting
        what we do not recognise is not ours to do.
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
            # `st_mtime` is seconds since the epoch as a float; `fromtimestamp` with an explicit zone
            # turns it into an aware datetime that can be compared with ours. Without the zone it would
            # be naive and the comparison would raise.
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

        Answers `True` on an API failure. The question is only asked to decide whether to delete
        something, so the cautious answer is the one that deletes nothing: the next round asks again.
        """
        try:
            return not self._api.load_task(task_id).status.is_terminal
        except ApiRejected:
            # No such task. The name is a task id that the API has never heard of, or has forgotten -
            # either way nothing can claim the directory.
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

        Caching it caches its client and the assumed session behind it, and that is safe for exactly
        the reason the session is refreshable: botocore re-assumes the role when it is close to
        expiring, so a client built at startup still signs correctly during a cleanup hours later.
        Without the cache, an expiry cleanup of two hundred archives would read the region and assume the
        role two hundred times to delete two hundred objects from the same bucket.

        The cost is that the cleaner holds a renewable session for each destination storage for as long
        as the process lives, where a worker's dies with its task. It is the archive storage of this
        service's own configuration rather than a user's storage, and deleting from it is the cleaner's
        whole job, so the trade is worth naming rather than hiding.
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

        A refusal is expected rather than exceptional: between the filter call and this one, a worker
        that was only slow may have reported its own result, and the task is no longer in the status the
        transition starts from. Nothing to do about it, and nothing wrong - the task ended properly,
        which is the better outcome.
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

        **The recorded destination wins over the configured one.** A task that succeeded carries the
        bucket and prefix its own worker wrote to, and the key is built from those. Using the current
        setting instead would be wrong for every archive written before an administrator last changed
        it - and wrong silently, because S3 answers the delete of an absent key with success, so this
        would report a clean delete while the real object stayed where it was. That is the failure the
        recorded pair exists to prevent.

        The configured destination is the fallback, and only for a task that never reported: a worker
        that uploaded and then died recorded nothing, so the current setting is the only guess
        available. It is right unless the destination changed in between.

        For a stuck or cancelled task nobody knows whether the worker got as far as uploading, so the
        delete is issued either way - it costs one call and S3 treats a missing object as deleted. For
        an expiring task the object certainly existed, which is what `uploaded` records: a failure there
        is worth an error in the log, while the same failure for a task that may never have had an
        archive is not.
        """
        storage_id = task.result_storage_id or archive_config.destination_storage_id
        if not storage_id:
            LOG.warning('Task %s records no storage and none is configured, cannot clean up after it',
                        task.id)
            return False
        # The recorded key as it stands, or one derived from the current setting for a task that never
        # reported where it went. Derived is right unless the destination changed in between, and that
        # is the whole reason a task records its own.
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

    The API assigns `UUID.randomUUID().toString()` - see `AsyncTaskEntity.assignId` - so anything that
    does not parse as a UUID was not created by a worker of this service. The check exists because
    `--work-dir` may point at a volume shared with something else: without it, a `logs` or `lost+found`
    directory there would be asked about, refused by the API - which the cleanup reads as "no such task,
    nothing can claim it" - and deleted.

    `uuid.UUID(name)` raises `ValueError` on anything it cannot parse, which is the whole test. The
    second comparison rejects the forms it accepts but the API never produces, such as a UUID written
    without its dashes or wrapped in braces.
    """
    try:
        return str(uuid.UUID(name)) == name.lower()
    except ValueError:
        return False


def _default_bucket_factory(api: CloudPipelineApi,
                            config: ServiceConfig) -> Callable[[Storage], ArchiveBucket]:
    """
    Builds the function that makes an archive bucket for a destination storage.

    A function returning a function - a closure. The returned function remembers `api` and `config`
    from the scope it was defined in, which is how Python passes "the service settings" along without a
    class to hold them.

    Each call reads the storage's region and assumes its role, so the cleaner calls it once per storage
    and keeps the result - see `Cleaner._bucket_for`.
    """
    def factory(storage: Storage) -> ArchiveBucket:
        return ArchiveBucket(storage.root, config.part_size_bytes, build_s3_client(api, storage))
    return factory
