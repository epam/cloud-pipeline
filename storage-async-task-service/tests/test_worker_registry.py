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

The orchestrator names no worker class, so these tests keep `SUPPORTED_TASK_TYPES` and `worker_for`
in step.
"""

import pytest

from sats.config import ServiceConfig, StorageDownloadArchiveTaskConfig, \
    StorageUnpackArchiveTaskConfig
from sats.model import ArchiveTask, AsyncTaskType, TaskStatus, UnpackParams
from sats.worker import SUPPORTED_TASK_TYPES, ArchivePreparationWorker, AsyncTaskWorker, \
    UnpackArchiveWorker, worker_for
from sats.worker.cleaner import CLEANED_TASK_TYPES

from .fakes import FakeApi, task_configs


def _task(task_type=AsyncTaskType.DOWNLOAD_ARCHIVE) -> ArchiveTask:
    return ArchiveTask(id='task-1', status=TaskStatus.RUNNING, owner='USER', storage_id=1,
                       paths=('data/',), type=task_type,
                       # Both halves are filled in, because a worker reads its own in its
                       # constructor and the test is about which worker is chosen.
                       unpack=UnpackParams(source_storage_id=1, source_path='a.zip',
                                           destination_storage_id=2, destination_path='out/'))


def _config(tmp_path) -> ServiceConfig:
    return ServiceConfig(api_url='https://api', api_token='token', work_dir=tmp_path, slots=1)


def test_a_download_archive_task_gets_the_archive_worker(tmp_path):
    api = FakeApi(_task())

    worker = worker_for(api, _config(tmp_path), task_configs(), _task())

    assert isinstance(worker, ArchivePreparationWorker)
    assert isinstance(worker, AsyncTaskWorker)


def test_an_unpack_archive_task_gets_the_unpack_worker(tmp_path):
    api = FakeApi(_task())

    worker = worker_for(api, _config(tmp_path), task_configs(),
                        _task(AsyncTaskType.UNPACK_ARCHIVE))

    assert isinstance(worker, UnpackArchiveWorker)
    assert isinstance(worker, AsyncTaskWorker)


def test_a_worker_is_given_the_preference_section_of_its_own_type(tmp_path):
    # A worker reading another type's section would enforce the wrong limits, and the two sections
    # share field names - `maxSizeBytes` means the size of a selection in one and the uncompressed
    # size of an archive in the other.
    configs = task_configs()
    api = FakeApi(_task())

    unpack = worker_for(api, _config(tmp_path), configs, _task(AsyncTaskType.UNPACK_ARCHIVE))

    assert isinstance(unpack._unpack_config, StorageUnpackArchiveTaskConfig)
    download = worker_for(api, _config(tmp_path), configs, _task())
    assert isinstance(download._archive_config, StorageDownloadArchiveTaskConfig)


def test_every_supported_type_has_a_worker(tmp_path):
    # The claim loop asks the API for all of these at once, so a type listed here without a worker
    # would be claimed and then fail - the task would be marked RUNNING for nothing.
    api = FakeApi(_task())

    for task_type in SUPPORTED_TASK_TYPES:
        assert worker_for(api, _config(tmp_path), task_configs(), _task(task_type)) is not None


def test_the_cleaner_covers_every_type_this_service_runs():
    # The cleaner cannot read the registry - `worker/__init__` imports it - so it repeats the list.
    # A type this service runs and the cleaner does not know would stay `RUNNING` for ever when its
    # worker died: every cleanup filters by type, so nothing else would ever look at it.
    assert set(CLEANED_TASK_TYPES) == set(SUPPORTED_TASK_TYPES)


def test_a_worker_is_registered_under_the_type_it_declares():
    # The registry is built from each class's own `TASK_TYPE`, which is what makes the two impossible
    # to get out of step.
    assert ArchivePreparationWorker.TASK_TYPE in SUPPORTED_TASK_TYPES
    assert UnpackArchiveWorker.TASK_TYPE in SUPPORTED_TASK_TYPES


def test_an_unknown_type_fails_with_the_type_in_the_message(tmp_path):
    api = FakeApi(_task())
    # This service and the API disagreeing about what was claimed.
    foreign = ArchiveTask(id='task-2', status=TaskStatus.RUNNING, owner='USER', storage_id=1,
                          type='UNARCHIVE')

    with pytest.raises(ValueError) as refusal:
        worker_for(api, _config(tmp_path), task_configs(), foreign)

    assert 'UNARCHIVE' in str(refusal.value)
