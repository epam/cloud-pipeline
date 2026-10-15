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

    1. read `storage.download.archive.config`      so an administrator's change applies without a
                                                   restart, and a task never runs under settings older
                                                   than the round it started in
    2. run the four cleaner cleanups                 before claiming, so a stuck task is out of the way
    3. claim tasks while slots are free            one call per task, see below
    4. sleep until the next round                  interruptibly

**One task per claim, and a slot taken before the claim.** `POST /async-task/next` marks a task
`RUNNING` and hands it over. Between that moment and the moment a worker actually starts on it, nobody
is working on the task and a crash leaves it hanging until the cleaner times it out. Claiming into a
slot we already hold keeps exactly one task in that window, instead of a batch of them.

**Threads, not processes.** Every slow part of a task - listing a bucket, downloading, uploading - is
waiting on a socket, and Python releases its global lock while waiting. The global interpreter lock
only serialises the execution of bytecode, so the one genuinely CPU-bound part, deflate, is the one
thing that does not scale with slots. That is also why the compression level is low: at level 1 the
CPU barely matters, and at level 9 the slots would be competing for one core.
"""

from __future__ import annotations

import threading
from concurrent.futures import ThreadPoolExecutor

from .api import CloudPipelineApi
from .config import PREFERENCE_KEY, ServiceConfig, StorageDownloadArchiveConfig
from .errors import ApiError, ApiRejected
from .log import get_logger
from .model import ArchiveTask, AsyncTaskType
from .worker import SUPPORTED_TASK_TYPES, Cleaner, worker_for

LOG = get_logger(__name__)


class Orchestrator:
    """
    Owns the loop, the worker pool and the slots.

    It runs on the main thread and does no work itself: its whole job is to decide what gets started
    and to keep the number of running workers at or below `slots`.
    """

    def __init__(self, api: CloudPipelineApi, config: ServiceConfig,
                 cleaner: Cleaner | None = None):
        self._api = api
        self._config = config
        self._cleaner = cleaner if cleaner is not None else Cleaner(api, config)
        # `Event` is a flag threads can wait on. Setting it both tells the loop to finish and wakes it
        # up out of its sleep immediately - which is why the sleep below is `wait(timeout)` and not
        # `time.sleep`. With `time.sleep` a container asked to stop would sit there for the rest of the
        # poll interval before noticing.
        self._shutdown = threading.Event()
        # A counting semaphore with `slots` permits. Taken before a task is claimed and released when
        # its worker is done, so it *is* the count of free slots. `BoundedSemaphore` rather than
        # `Semaphore` because it raises on a release that would push the count above the maximum - a
        # released-twice bug otherwise silently grants an extra slot, and that is a very hard bug to
        # find later.
        self._slots = threading.BoundedSemaphore(config.slots)
        # Task ids workers of this process are busy with. Read by the cleaner, written from the done
        # callback, which runs on a worker thread - so both go through the lock.
        self._active: set[str] = set()
        self._active_lock = threading.Lock()
        # Remembered so that the warnings of the preference are logged when they appear or change,
        # rather than once per round for as long as the misconfiguration lasts.
        self._reported_warnings: tuple[str, ...] = ()

    def run(self) -> None:
        """
        Runs rounds until asked to stop.

        The pool is created in a `with`, and leaving it waits for every running worker to finish. That
        is what makes a shutdown orderly: the loop stops claiming, each worker notices the deadline or
        finishes its task, reports, and only then does the process exit. A container stopped with the
        usual grace period may not have long enough for a large task, and that task is left to the
        cleaner - which is the design, not a gap.

        `max_workers` equals the number of slots, so the pool never queues: a submit only happens with
        a slot in hand, and there is always a thread free for it.
        """
        LOG.info('Starting with %s slot(s), polling every %s s, work directory %s',
                 self._config.slots, self._config.poll_interval_seconds, self._config.work_dir)
        self._config.work_dir.mkdir(parents=True, exist_ok=True)
        with ThreadPoolExecutor(max_workers=self._config.slots,
                                thread_name_prefix='worker') as pool:
            while True:
                try:
                    self._round(pool)
                except Exception as error:
                    # The loop outlives any single round. A round that fails - the API down, a
                    # preference that will not parse - must not end the service, or a platform
                    # restarting would take this service with it.
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
        Asks the loop to finish the round it is in and stop.

        Called from a signal handler, which runs on the main thread between two bytecodes - so it must
        do as little as possible. Setting an event is as little as it gets; everything else happens on
        the loop's own thread.
        """
        self._shutdown.set()

    # ------------------------------------------------------------------ one round

    def _round(self, pool: ThreadPoolExecutor) -> None:
        archive_config = self._load_archive_config()
        if archive_config is None:
            return
        self._cleaner.cleanup(archive_config, self._active_ids())
        if self._shutdown.is_set():
            return
        if not archive_config.is_usable:
            # Already said by the warnings above. Tasks stay pending, which is the right place for
            # them: an administrator filling the preference in makes the backlog run, and failing them
            # here would lose work for a mistake that is about to be corrected.
            return
        self._claim_while_free(pool, archive_config)

    def _load_archive_config(self) -> StorageDownloadArchiveConfig | None:
        """
        Reads and parses the preference. Returns `None` when it cannot be read.

        `None` and not a default: running tasks under invented settings is worse than not running them.
        A preference that cannot be read is almost always the API being unreachable, and the next round
        is twenty seconds away.
        """
        try:
            raw = self._api.get_preference(PREFERENCE_KEY)
        except (ApiError, ApiRejected) as error:
            LOG.warning('Cannot read preference %s (%s), skipping this round', PREFERENCE_KEY, error)
            return None
        try:
            archive_config = StorageDownloadArchiveConfig.from_preference(raw)
        except ValueError as error:
            # `json.loads` raises `JSONDecodeError`, which is a `ValueError`. An administrator has
            # saved something that is not JSON, and the only useful thing to do is to say so.
            LOG.error('Preference %s does not parse (%s), skipping this round', PREFERENCE_KEY, error)
            return None
        self._report_warnings(archive_config)
        return archive_config

    def _report_warnings(self, archive_config: StorageDownloadArchiveConfig) -> None:
        """
        Logs what is wrong with the preference, once per change rather than once per round.

        A misconfiguration lasts until someone fixes it, which may be days. Repeating the same two
        lines every twenty seconds is how a log stops being read.
        """
        warnings = tuple(archive_config.warnings())
        if warnings == self._reported_warnings:
            return
        self._reported_warnings = warnings
        for warning in warnings:
            LOG.warning('%s', warning)

    def _claim_while_free(self, pool: ThreadPoolExecutor,
                          archive_config: StorageDownloadArchiveConfig) -> None:
        """
        Claims tasks one at a time for as long as there is a slot and the queue is not empty.

        The slot is taken first, with `blocking=False` - we are asking "is there room?", and waiting
        for room is the loop's job, not this method's. An empty queue releases the slot again and ends
        the round.

        Everything between the acquire and the hand-over is in a `try/finally`, and the flag decides
        who owns the slot when it ends. A slot lost here is lost for the life of the process - the
        failure would be a service that logs "all slots are busy" for ever while nothing is running -
        and `_claim` cannot promise to raise nothing: a mangled response from a proxy in front of the
        API reaches it as an `AttributeError`, which no `except` of its own would catch.
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

        One call per type, because `/async-task/next` takes a single type - there is no "give me
        anything" form of it. The types are asked for in the order `SUPPORTED_TASK_TYPES` declares,
        which with one type is no order at all; when there are two, the first in that tuple is the one
        that gets a free slot first, and that is the place to change if it ever has to be fairer.
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
            # The task was claimed but does not parse - a params shape we do not understand. It is
            # already RUNNING on the API side, and the cleaner will fail it as stuck. Nothing better is
            # available: we cannot report a status for a task we could not read the id of.
            LOG.error('Claimed a task that cannot be read (%s); the cleaner will clean it up', error)
            return None

    def _start(self, pool: ThreadPoolExecutor, archive_config: StorageDownloadArchiveConfig,
               task: ArchiveTask) -> bool:
        """
        Hands a claimed task to the pool. Returns whether the pool has taken it.

        The return value is about one thing only: who gives the slot back. `True` means the future's
        callback will, `False` means the caller must - see `_claim_while_free`, where the slot was
        taken.

        The callback is where the slot goes back, and it has to be set up so that it cannot be missed:
        `add_done_callback` runs it on success, on exception and on cancellation. Releasing the slot in
        the worker instead would leak one for every way a worker can end without reaching that line.

        `task_id=task.id` in the lambda's parameters is not an argument anybody passes - it is how a
        value is captured by a closure in Python. A closure captures the *variable*, not its value, so
        a plain `lambda f: self._release(task.id)` created in a loop would see whatever `task` holds at
        the moment the callback runs, which is the last task of the loop. A default value is evaluated
        when the lambda is defined, which pins it. This is the single most common bug in Python code
        that makes callbacks in a loop.
        """
        self._track(task.id)
        try:
            future = pool.submit(self._run_worker, archive_config, task)
        except RuntimeError as error:
            # The pool is shutting down. The task is RUNNING on the API side and nothing will work on
            # it; the cleaner's business again.
            LOG.error('Cannot start task %s (%s); the cleaner will clean it up', task.id, error)
            self._forget(task.id)
            return False
        future.add_done_callback(lambda _, task_id=task.id: self._release(task_id))
        return True

    def _run_worker(self, archive_config: StorageDownloadArchiveConfig, task: ArchiveTask) -> None:
        """
        The body of a pool thread: the worker for whatever type of task was claimed.

        This class never names a worker. `worker_for` holds the mapping from task type to class, so a
        second kind of task - unarchiving an archive back into a storage - needs nothing here.

        `AsyncTaskWorker.run` is written never to raise, and this is wrapped anyway. An exception here
        would otherwise be stored in a future nobody reads and vanish without a line in the log - the
        failure mode where a task quietly stays RUNNING and nobody can say why.
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
        Gives a slot back and forgets the task.

        Runs on the worker's own thread, as the callback of its future. The order matters a little: the
        task is forgotten before the slot is released, so that the moment a slot is free, no cleaner
        cleanup can still believe that task is being worked on.
        """
        self._forget(task_id)
        self._slots.release()

    def _active_ids(self) -> set[str]:
        """
        A copy of the active set, for the cleaner.

        A copy and not the set itself: the cleaner iterates it while workers finish and remove
        themselves, and changing a set during iteration raises `RuntimeError`.
        """
        with self._active_lock:
            return set(self._active)
