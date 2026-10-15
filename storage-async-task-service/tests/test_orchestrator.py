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
Tests of the loop: what gets claimed, and whether the slots come back.

A lost slot is the worst failure this service has, because it is invisible: the process keeps running,
keeps logging that all slots are busy, and never works on anything again until somebody restarts it.
That is what most of this file is about.

Every test runs with `run_once=True`, so `run()` does exactly one round and then waits for whatever it
started - which makes the loop testable without a thread that has to be stopped.
"""

import json
import threading
import time

from sats.config import ServiceConfig
from sats.errors import ApiError
from sats.model import ArchiveTask, TaskStatus
from sats.orchestrator import Orchestrator

from .fakes import FakeApi


def _config(tmp_path, slots=2, cleanup_interval=300):
    return ServiceConfig(api_url='https://api', api_token='token', work_dir=tmp_path, slots=slots,
                         poll_interval_seconds=1, cleanup_interval_seconds=cleanup_interval,
                         run_once=True)


def _api(preference=None):
    api = FakeApi(_task('task-1'))
    # A preference with a bucket, so that the round gets as far as claiming. Without one the
    # orchestrator stops after the cleanups, which is its own behaviour and tested below.
    api.preference = (preference if preference is not None
                      else json.dumps({'destinationStorageId': 9}))
    return api


def _task(task_id):
    return ArchiveTask(id=task_id, status=TaskStatus.RUNNING, owner='USER', storage_id=1,
                       paths=('data/',))


def _free_slots(orchestrator, limit):
    """
    How many slots can be taken right now.

    Reaching into `_slots` is the point of the test: the count is the state that gets corrupted, and
    there is no public way to ask for it.
    """
    taken = 0
    while taken < limit and orchestrator._slots.acquire(blocking=False):
        taken += 1
    return taken


class _RecordingWorker:
    """
    Stands in for whatever worker the registry would return, so that a round can be driven without a
    storage behind it. `worker_for` is replaced rather than a worker class, because that is the only
    name the orchestrator knows - which is the point of the registry.

    `*args` accepts whatever the factory is called with; the task is the fourth argument. A fake that
    listed the real constructor's parameters would have to be edited whenever one is added, and this
    one only cares which task it was given.
    """

    started: list[str] = []

    def __init__(self, *args):
        self._task = args[3]

    def run(self):
        _RecordingWorker.started.append(self._task.id)


class _CountingWorker:
    """
    Stands in for the worker the registry returns, and records how many ran at the same time.

    The counters are class attributes - shared by every instance, like a Java static field - because
    the test needs one number across all the workers of a round. They are guarded by a lock: the
    workers really do run on different threads, and `running += 1` is a read and a write that another
    thread can land in the middle of.
    """

    lock = threading.Lock()
    running = 0
    peak = 0
    started = 0

    @classmethod
    def reset(cls):
        cls.running = 0
        cls.peak = 0
        cls.started = 0

    def __init__(self, *args):
        self._task = args[3]

    def run(self):
        with _CountingWorker.lock:
            _CountingWorker.running += 1
            _CountingWorker.started += 1
            _CountingWorker.peak = max(_CountingWorker.peak, _CountingWorker.running)
        # Long enough that the loop comes back round and tries to claim again while this one is still
        # running, which is the situation being tested.
        time.sleep(0.05)
        with _CountingWorker.lock:
            _CountingWorker.running -= 1


def test_claims_tasks_until_the_queue_is_empty(tmp_path, monkeypatch):
    api = _api()
    queue = [_task('a'), _task('b')]
    api.claim_next = lambda _type: queue.pop(0) if queue else None
    _RecordingWorker.started = []
    monkeypatch.setattr('sats.orchestrator.worker_for', lambda *args: _RecordingWorker(*args))
    orchestrator = Orchestrator(api, _config(tmp_path, slots=2))

    orchestrator.run()

    assert sorted(_RecordingWorker.started) == ['a', 'b']
    # And both slots are back, because `run` has waited for the workers.
    assert _free_slots(orchestrator, 2) == 2


def test_never_runs_more_workers_than_there_are_slots(tmp_path, monkeypatch):
    # The invariant the slots exist for. Asserted on the peak rather than on how many tasks were
    # claimed, because a worker finishing quickly frees its slot and the round claims again - which is
    # correct, and would make a count flaky.
    api = _api()
    queue = [_task('a'), _task('b'), _task('c'), _task('d')]
    api.claim_next = lambda _type: queue.pop(0) if queue else None
    _CountingWorker.reset()
    monkeypatch.setattr('sats.orchestrator.worker_for', lambda *args: _CountingWorker(*args))
    orchestrator = Orchestrator(api, _config(tmp_path, slots=2))

    orchestrator.run()

    assert _CountingWorker.peak <= 2
    assert _CountingWorker.started == 4
    assert _free_slots(orchestrator, 2) == 2


def test_a_claim_that_fails_unexpectedly_does_not_lose_a_slot(tmp_path):
    # The failure the `try/finally` around the claim exists for. `_claim` handles the API's own
    # exceptions; an `AttributeError` out of a mangled response is not one of them, and before the
    # `finally` it would take the slot it was holding with it - permanently.
    api = _api()

    def explode(_task_type):
        raise AttributeError("'list' object has no attribute 'get'")

    api.claim_next = explode
    orchestrator = Orchestrator(api, _config(tmp_path, slots=2))

    orchestrator.run()

    assert _free_slots(orchestrator, 2) == 2


def test_claims_nothing_without_a_bucket(tmp_path, monkeypatch):
    # An administrator who has not filled the preference in yet. Tasks stay pending rather than failing,
    # because the mistake is about to be corrected and failing them would lose the work.
    api = _api(preference=json.dumps({'maxSizeBytes': 1024}))
    claimed = []
    api.claim_next = lambda task_type: claimed.append(task_type)
    _RecordingWorker.started = []
    monkeypatch.setattr('sats.orchestrator.worker_for', lambda *args: _RecordingWorker(*args))
    orchestrator = Orchestrator(api, _config(tmp_path))

    orchestrator.run()

    assert claimed == []


def test_cleans_up_even_when_there_is_no_bucket(tmp_path):
    # The cleanups have to run anyway: three of the four need nothing from the preference but the bucket
    # name, and if clearing the preference stopped them, nothing would ever finish a cancellation or
    # expire an archive again.
    api = _api(preference=json.dumps({'maxSizeBytes': 1024}))
    cleaned = []

    class _RecordingCleaner:
        def cleanup(self, archive_config, active_ids):
            cleaned.append(archive_config)

    orchestrator = Orchestrator(api, _config(tmp_path), cleaner=_RecordingCleaner())

    orchestrator.run()

    assert len(cleaned) == 1


def test_cleans_up_on_the_first_round_and_then_waits_out_the_interval(tmp_path, monkeypatch):
    # The cleanups cost three `async-task/filter` calls and a walk of the work directory, and nothing
    # they look for appears in seconds, so they are throttled apart from the loop. The first round is
    # the exception: a process that has just started is the one that has a dead worker's tasks to
    # finish.
    clock = [1000.0]
    monkeypatch.setattr('sats.orchestrator.time.monotonic', lambda: clock[0])
    orchestrator = Orchestrator(_api(), _config(tmp_path, cleanup_interval=300))

    assert orchestrator._cleanup_due() is True, 'the first round must clean up'
    assert orchestrator._cleanup_due() is False, 'the next round is inside the interval'

    clock[0] += 299
    assert orchestrator._cleanup_due() is False

    clock[0] += 2
    assert orchestrator._cleanup_due() is True, 'the interval has passed'
    assert orchestrator._cleanup_due() is False, 'and the next one is booked again'


def test_the_cleanups_can_be_turned_off_altogether(tmp_path):
    # For a deployment that runs them elsewhere - several replicas of this service, one of them
    # cleaning. `run` says so at WARNING, because the consequence is otherwise silent.
    api = _api()
    cleaned = []

    class _RecordingCleaner:
        def cleanup(self, archive_config, active_ids):
            cleaned.append(archive_config)

    orchestrator = Orchestrator(api, _config(tmp_path, cleanup_interval=0),
                                cleaner=_RecordingCleaner())

    orchestrator.run()

    assert cleaned == []
    assert orchestrator._cleanup_due() is False


def test_skips_the_round_when_the_preference_cannot_be_read(tmp_path):
    # Nothing at all is done under invented settings - not even a cleanup, which would decide what to
    # delete from a time to live nobody configured.
    api = _api()

    def explode(_key):
        raise ApiError('the API is down')

    api.get_preference = explode
    cleaned = []

    class _RecordingCleaner:
        def cleanup(self, archive_config, active_ids):
            cleaned.append(archive_config)

    orchestrator = Orchestrator(api, _config(tmp_path), cleaner=_RecordingCleaner())

    orchestrator.run()

    assert cleaned == []
