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
    task_stop_watcher.py           how any worker learns that it must give up
    cleaner.py                     not a worker: it finishes tasks whose workers are gone

The registry below is the whole dispatch: the orchestrator never names a worker class. A new kind of
task is a module here and one line in `_WORKERS`.
"""

from __future__ import annotations

from ..api import CloudPipelineApi
from ..config import ServiceConfig, StorageDownloadArchiveConfig
from ..model import ArchiveTask, AsyncTaskType
from .archive_preparation_worker import ArchivePreparationWorker
from .base import AsyncTaskWorker
from .cleaner import Cleaner
from .task_stop_watcher import TaskStopWatcher

__all__ = ['ArchivePreparationWorker', 'AsyncTaskWorker', 'Cleaner', 'TaskStopWatcher',
           'SUPPORTED_TASK_TYPES', 'worker_for']

# Task type to the class that runs it, keyed by what each worker declares.
_WORKERS: dict[AsyncTaskType, type[AsyncTaskWorker]] = {
    worker.TASK_TYPE: worker
    for worker in (ArchivePreparationWorker,)
}

# The types this service claims. `/async-task/next` takes one type per call, so the orchestrator asks
# for each in turn.
SUPPORTED_TASK_TYPES: tuple[AsyncTaskType, ...] = tuple(_WORKERS)


def worker_for(api: CloudPipelineApi, service_config: ServiceConfig,
               archive_config: StorageDownloadArchiveConfig,
               task: ArchiveTask) -> AsyncTaskWorker:
    """
    The worker for a claimed task.

    An unknown type means this service and the API disagree about what was claimed, which is worth a
    message rather than a bare `KeyError`.
    """
    worker = _WORKERS.get(task.type)
    if worker is None:
        raise ValueError(f'No worker in this service runs a task of type {task.type}')
    return worker(api, service_config, archive_config, task)
