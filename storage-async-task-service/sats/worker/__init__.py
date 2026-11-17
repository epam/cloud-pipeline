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
The workers, and the one place that says which worker runs which kind of task.

    base.py                        what a worker has to be
    archive_preparation_worker.py  DOWNLOAD_ARCHIVE: build the ZIP and upload it
    unpack_archive_worker.py       UNPACK_ARCHIVE: extract an archive into a storage
    task_stop_watcher.py           how any worker learns that it must give up
    cleaner.py                     not a worker: it finishes tasks whose workers are gone

The registry below is the whole dispatch: the orchestrator never names a worker class. A new kind of
task is a module here and one line in `_WORKERS`.
"""

from __future__ import annotations

from ..api import CloudPipelineApi
from ..config import AsyncTasksConfig, ServiceConfig
from ..model import ArchiveTask, AsyncTaskType
from .archive_preparation_worker import ArchivePreparationWorker
from .base import AsyncTaskWorker
from .cleaner import Cleaner
from .task_stop_watcher import TaskStopWatcher
from .unpack_archive_worker import UnpackArchiveWorker

__all__ = ['ArchivePreparationWorker', 'AsyncTaskWorker', 'Cleaner', 'TaskStopWatcher',
           'UnpackArchiveWorker', 'SUPPORTED_TASK_TYPES', 'worker_for']

# Task type to the class that runs it, keyed by what each worker declares.
_WORKERS: dict[AsyncTaskType, type[AsyncTaskWorker]] = {
    worker.TASK_TYPE: worker
    for worker in (ArchivePreparationWorker, UnpackArchiveWorker)
}

# The types this service runs. The orchestrator asks the API for all of them in one call, so a task
# of a type no worker here claims is never handed over - and the oldest task wins across the types
# rather than one type waiting behind another.
SUPPORTED_TASK_TYPES: tuple[AsyncTaskType, ...] = tuple(_WORKERS)


def worker_for(api: CloudPipelineApi, service_config: ServiceConfig, configs: AsyncTasksConfig,
               task: ArchiveTask) -> AsyncTaskWorker:
    """
    The worker for a claimed task, with the preference section of its own type.

    A worker is given its section and never the whole preference, so one task type cannot read
    another's limits by accident.

    An unknown type means this service and the API disagree about what was claimed, which is worth a
    message rather than a bare `KeyError`.
    """
    worker = _WORKERS.get(task.type)
    if worker is None:
        raise ValueError(f'No worker in this service runs a task of type {task.type}')
    return worker(api, service_config, configs.section_for(task.type), task)
