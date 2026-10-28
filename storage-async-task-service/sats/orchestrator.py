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
The loop. Reads the preference, runs the cleaner, claims what it has room for, and sleeps.

One round:

    1. read `storage.download.archive.config`      so a change applies without a restart
    2. run the four cleaner cleanups               before claiming, so a stuck task is out of the way
    3. claim tasks while slots are free            one call per task
    4. sleep until the next round                  interruptibly

**One task per claim, and the slot taken before the claim.** `/async-task/next` marks a task `RUNNING`
and hands it over; between that and a worker starting on it, nobody is working on the task. Claiming
into a slot we already hold keeps exactly one task in that window instead of a batch.

**Threads, not processes.** Every slow part of a task waits on a socket, and Python releases its lock
while waiting. Deflate is the one CPU-bound part, which is also why the compression level is low.
"""

from __future__ import annotations

import threading
import time
from concurrent.futures import ThreadPoolExecutor

from .api import CloudPipelineApi
from .config import PREFERENCE_KEY, ServiceConfig, StorageDownloadArchiveConfig
from .util.errors import ApiError, ApiRejected
from .util.log import get_logger
from .model import ArchiveTask, AsyncTaskType
from .worker import SUPPORTED_TASK_TYPES, Cleaner, worker_for

LOG = get_logger(__name__)


class Orchestrator:
    """
    Owns the loop, the worker pool and the slots.

    It runs on the main thread and does no work itself: it decides what gets started and keeps the
    number of running workers at or below `slots`.
    """

    def __init__(self, api: CloudPipelineApi, config: ServiceConfig,
                 cleaner: Cleaner | None = None):
        self._api = api
        self._config = config
        self._cleaner = cleaner if cleaner is not None else Cleaner(api, config)
        # An event rather than a flag, because the sleep below waits on it: a container asked to stop
        # wakes up at once instead of sitting out the poll interval.
        self._shutdown = threading.Event()
        # The count of free slots. `BoundedSemaphore` raises on a release above the maximum, which
        # turns a released-twice bug into a crash rather than an extra slot.
        self._slots = threading.BoundedSemaphore(config.slots)
        # Tasks workers of this process are busy with. Written from a worker thread, read by the
        # cleaner, so both go through the lock.
        self._active: set[str] = set()
        self._active_lock = threading.Lock()
        # So the preference's warnings are logged when they change, not once per round.
        self._reported_warnings: tuple[str, ...] = ()
        # Monotonic time of the next cleanup. `None` makes the first round clean up, which is when
        # there is most to clean.
        self._next_cleanup: float | None = None

    def run(self) -> None:
        """
        Runs rounds until asked to stop.

        Leaving the pool's `with` waits for every running worker, which is what makes a shutdown
        orderly. A task too large to finish inside the container's grace period is left to the cleaner.

        `max_workers` equals the slots, so the pool never queues.
        """
        LOG.info('Starting with %s slot(s), polling every %s s, work directory %s',
                 self._config.slots, self._config.poll_interval_seconds, self._config.work_dir)
        if self._config.cleanup_interval_seconds:
            LOG.info('Cleanups run every %s s', self._config.cleanup_interval_seconds)
        else:
            # Loud, because the consequence is silent.
            LOG.warning('Cleanups are turned off (--cleanup-interval-seconds 0). Nothing in this '
                        'process will finish a task whose worker is gone, delete an expired archive '
                        'or remove an abandoned scratch directory')
        self._config.work_dir.mkdir(parents=True, exist_ok=True)
        with ThreadPoolExecutor(max_workers=self._config.slots,
                                thread_name_prefix='worker') as pool:
            while True:
                try:
                    self._round(pool)
                except Exception as error:
                    # The loop outlives any single round, so a platform restarting does not take this
                    # service with it.
                    LOG.exception('A round failed (%s), continuing', error)
                if self._config.run_once:
                    LOG.info('Single round requested, waiting for the workers to finish')
                    break
                if self._shutdown.wait(self._config.poll_interval_seconds):
                    LOG.info('Shutdown requested, waiting for the workers to finish')
                    break
        LOG.info('Stopped')

    def stop(self) -> None:
        """
        Asks the loop to finish its round and stop.

        Called from a signal handler, so it does as little as possible.
        """
        self._shutdown.set()

    # ------------------------------------------------------------------ one round

    def _round(self, pool: ThreadPoolExecutor) -> None:
        archive_config = self._load_archive_config()
        if archive_config is None:
            return
        if self._cleanup_due():
            self._cleaner.cleanup(archive_config, self._active_ids())
        if self._shutdown.is_set():
            return
        if not archive_config.is_usable:
            # Tasks stay pending, which is the right place for them: filling the preference in makes
            # the backlog run, while failing them here would lose work over a fixable mistake.
            return
        self._claim_while_free(pool, archive_config)

    def _cleanup_due(self) -> bool:
        """
        Whether this round runs the cleanups, and books the next one if it does.

        Throttled apart from the loop, which has to stay fast because it also claims tasks, while the
        cleanups act on thresholds measured in hours. The clock is monotonic, never the wall clock.
        """
        interval = self._config.cleanup_interval_seconds
        if not interval:
            return False
        now = time.monotonic()
        if self._next_cleanup is not None and now < self._next_cleanup:
            return False
        # Booked from now, so a long round does not make the next cleanup immediate.
        self._next_cleanup = now + interval
        return True

    def _load_archive_config(self) -> StorageDownloadArchiveConfig | None:
        """
        Reads and parses the preference. `None` when it cannot be read.

        `None` and not a default: running tasks under invented settings is worse than not running them.
        """
        try:
            raw = self._api.get_preference(PREFERENCE_KEY)
        except (ApiError, ApiRejected) as error:
            LOG.warning('Cannot read preference %s (%s), skipping this round', PREFERENCE_KEY, error)
            return None
        try:
            archive_config = StorageDownloadArchiveConfig.from_preference(raw)
        except ValueError as error:
            # `JSONDecodeError` is a `ValueError`: somebody saved something that is not JSON.
            LOG.error('Preference %s does not parse (%s), skipping this round', PREFERENCE_KEY, error)
            return None
        self._report_warnings(archive_config)
        return archive_config

    def _report_warnings(self, archive_config: StorageDownloadArchiveConfig) -> None:
        """Logs what is wrong with the preference, once per change rather than once per round."""
        warnings = tuple(archive_config.warnings())
        if warnings == self._reported_warnings:
            return
        self._reported_warnings = warnings
        for warning in warnings:
            LOG.warning('%s', warning)

    def _claim_while_free(self, pool: ThreadPoolExecutor,
                          archive_config: StorageDownloadArchiveConfig) -> None:
        """
        Claims tasks one at a time while there is a slot and the queue is not empty.

        The slot is taken first and with `blocking=False`: this asks "is there room?", and waiting for
        room is the loop's job. The `finally` is what keeps a slot from being lost for the life of the
        process - `_claim` cannot promise to raise nothing, since a mangled response reaches it as an
        `AttributeError`.
        """
        while not self._shutdown.is_set():
            if not self._slots.acquire(blocking=False):
                LOG.debug('All %s slots are busy', self._config.slots)
                return
            handed_over = False
            try:
                task = self._claim()
                if task is None:
                    return
                handed_over = self._start(pool, archive_config, task)
            finally:
                if not handed_over:
                    self._slots.release()

    def _claim(self) -> ArchiveTask | None:
        """
        Takes the next task of any type this service runs, or `None` if there is none.

        One call per type: `/async-task/next` has no "give me anything" form. With more than one type,
        the first in `SUPPORTED_TASK_TYPES` gets a free slot first.
        """
        for task_type in SUPPORTED_TASK_TYPES:
            task = self._claim_type(task_type)
            if task is not None:
                return task
        return None

    def _claim_type(self, task_type: AsyncTaskType) -> ArchiveTask | None:
        """Takes the next task of one type, or `None` if there is none or the call failed."""
        try:
            return self._api.claim_next(task_type)
        except (ApiError, ApiRejected) as error:
            LOG.warning('Cannot claim a task (%s), skipping the rest of this round', error)
            return None
        except ValueError as error:
            # Claimed but unreadable, and already RUNNING on the API side. We cannot report a status
            # for a task whose id we could not read, so the cleaner has to fail it.
            LOG.error('Claimed a task that cannot be read (%s); the cleaner will clean it up', error)
            return None

    def _start(self, pool: ThreadPoolExecutor, archive_config: StorageDownloadArchiveConfig,
               task: ArchiveTask) -> bool:
        """
        Hands a claimed task to the pool. Returns whether the pool has taken it.

        The return value says who gives the slot back: `True` the future's callback, `False` the
        caller. `add_done_callback` runs on success, exception and cancellation, so the slot cannot be
        missed; releasing it inside the worker would leak one per way a worker can end early.

        `task_id=task.id` pins the value: a closure captures the variable, so a plain
        `lambda f: self._release(task.id)` in a loop would see the last task.
        """
        self._track(task.id)
        try:
            future = pool.submit(self._run_worker, archive_config, task)
        except RuntimeError as error:
            # The pool is shutting down and the task is already RUNNING: the cleaner's business.
            LOG.error('Cannot start task %s (%s); the cleaner will clean it up', task.id, error)
            self._forget(task.id)
            return False
        future.add_done_callback(lambda _, task_id=task.id: self._release(task_id))
        return True

    def _run_worker(self, archive_config: StorageDownloadArchiveConfig, task: ArchiveTask) -> None:
        """
        The body of a pool thread: the worker for whatever type was claimed.

        `run` is written never to raise, and this is wrapped anyway - otherwise an exception would sit
        in a future nobody reads and vanish without a line in the log.
        """
        try:
            worker_for(self._api, self._config, archive_config, task).run()
        except Exception as error:
            LOG.exception('Worker of task %s crashed (%s)', task.id, error)

    # ------------------------------------------------------------------ slots and bookkeeping

    def _track(self, task_id: str) -> None:
        with self._active_lock:
            self._active.add(task_id)

    def _forget(self, task_id: str) -> None:
        """Takes a task out of the active set without touching its slot."""
        with self._active_lock:
            self._active.discard(task_id)

    def _release(self, task_id: str) -> None:
        """
        Forgets the task and gives its slot back, in that order.

        Runs on the worker's thread. Forgetting first means no cleanup can believe a task is being
        worked on once its slot is free.
        """
        self._forget(task_id)
        self._slots.release()

    def _active_ids(self) -> set[str]:
        """A copy of the active set: the cleaner iterates it while workers remove themselves."""
        with self._active_lock:
            return set(self._active)
