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
Tests of the dispatch: which worker runs which kind of task.

The orchestrator names no worker class - it asks the API for the types in `SUPPORTED_TASK_TYPES` and
hands whatever it claimed to `worker_for`. These tests are what keeps those two halves in step, which
matters most on the day the second task type arrives.
"""

import pytest

from sats.config import ServiceConfig, StorageDownloadArchiveConfig
from sats.model import ArchiveTask, AsyncTaskType, TaskStatus
from sats.worker import SUPPORTED_TASK_TYPES, ArchivePreparationWorker, AsyncTaskWorker, worker_for

from .fakes import FakeApi


def _task(task_type=AsyncTaskType.DOWNLOAD_ARCHIVE) -> ArchiveTask:
    return ArchiveTask(id='task-1', status=TaskStatus.RUNNING, owner='USER', storage_id=1,
                       paths=('data/',), type=task_type)


def _config(tmp_path) -> ServiceConfig:
    return ServiceConfig(api_url='https://api', api_token='token', work_dir=tmp_path, slots=1)


def _archive_config() -> StorageDownloadArchiveConfig:
    # A real one, because a worker reads the destination prefix in its constructor.
    return StorageDownloadArchiveConfig(destination_storage_id=9, destination_prefix='archives/',
                                        max_size_bytes=None, max_files_count=None, ttl_days=7,
                                        max_user_tasks=3, worker_max_running_minutes=240,
                                        stuck_tasks_threshold_minutes=360)


def test_a_download_archive_task_gets_the_archive_worker(tmp_path):
    api = FakeApi(_task())

    worker = worker_for(api, _config(tmp_path), _archive_config(), _task())

    assert isinstance(worker, ArchivePreparationWorker)
    assert isinstance(worker, AsyncTaskWorker)


def test_every_supported_type_has_a_worker(tmp_path):
    # The claim loop asks the API for each of these in turn, so a type listed here without a worker
    # would be claimed and then fail - the task would be marked RUNNING for nothing.
    api = FakeApi(_task())

    for task_type in SUPPORTED_TASK_TYPES:
        assert worker_for(api, _config(tmp_path), _archive_config(), _task(task_type)) is not None


def test_a_worker_is_registered_under_the_type_it_declares():
    # The registry is built from each class's own `TASK_TYPE`, which is what makes the two impossible
    # to get out of step.
    assert ArchivePreparationWorker.TASK_TYPE in SUPPORTED_TASK_TYPES


def test_an_unknown_type_fails_with_the_type_in_the_message(tmp_path):
    api = FakeApi(_task())
    # Reachable two ways: this service and the API disagree about what was claimed, or `ArchiveTask`
    # fell back to its default type for an answer that carried none.
    foreign = ArchiveTask(id='task-2', status=TaskStatus.RUNNING, owner='USER', storage_id=1,
                          type='UNARCHIVE')

    with pytest.raises(ValueError) as refusal:
        worker_for(api, _config(tmp_path), _archive_config(), foreign)

    assert 'UNARCHIVE' in str(refusal.value)
