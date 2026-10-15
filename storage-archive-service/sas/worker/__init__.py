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

The registry below is the whole dispatch. The orchestrator asks the API for the task types in it, and
hands whatever it claimed to `worker_for`; it never names a worker class. Adding the next kind of task -
unarchiving one of these archives back into a storage - is a module here and one line in `_WORKERS`.

Every worker takes the same four arguments, which is what makes the registry possible. The fourth of
them is the download-archive configuration, and a second kind of task would want its own; when that
happens, the argument becomes "the configuration of this round" rather than this one preference, and
only this file and the workers change.
"""

from __future__ import annotations

from ..api import CloudPipelineApi
from ..config import ServiceConfig, StorageDownloadArchiveConfig
from ..model import ArchiveTask, AsyncTaskType
from .archive_preparation_worker import ArchivePreparationWorker
from .base import AsyncTaskWorker
from .cleaner import Cleaner
from .task_stop_watcher import TaskStopWatcher

# `__all__` lists what `from sas.worker import *` would bring in. Nobody should write that import, but
# the list doubles as a statement of what this package offers - and the re-exports above are otherwise
# flagged as unused by linters.
__all__ = ['ArchivePreparationWorker', 'AsyncTaskWorker', 'Cleaner', 'TaskStopWatcher',
           'SUPPORTED_TASK_TYPES', 'worker_for']

# Task type to the class that runs it. Keyed by what each worker declares, so a class cannot be
# registered under a type it does not serve.
_WORKERS: dict[AsyncTaskType, type[AsyncTaskWorker]] = {
    worker.TASK_TYPE: worker
    for worker in (ArchivePreparationWorker,)
}

# The types this service claims, in a fixed order. The API's `/async-task/next` takes one type per
# call, so the orchestrator asks for each in turn rather than for "anything".
SUPPORTED_TASK_TYPES: tuple[AsyncTaskType, ...] = tuple(_WORKERS)


def worker_for(api: CloudPipelineApi, service_config: ServiceConfig,
               archive_config: StorageDownloadArchiveConfig,
               task: ArchiveTask) -> AsyncTaskWorker:
    """
    The worker for a claimed task.

    `KeyError` would be the natural failure here and is not the useful one: the orchestrator only ever
    claims the types it asked for, so an unknown type means this service and the API disagree about
    what was claimed - or that `ArchiveTask.type` fell back to its default. That is worth a message
    naming the type rather than a bare key error.
    """
    worker = _WORKERS.get(task.type)
    if worker is None:
        raise ValueError(f'No worker in this service runs a task of type {task.type}')
    return worker(api, service_config, archive_config, task)
