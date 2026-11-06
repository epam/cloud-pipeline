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
What every worker has to be, so the orchestrator need not know what it is running.

`run()` takes nothing, returns nothing and **must never raise**: it is the body of a thread-pool task,
and an exception escaping it would sit in a future nobody reads while the task stayed `RUNNING` until
the cleaner noticed. So every worker catches everything and ends its own task.
"""

from __future__ import annotations

import abc
from typing import ClassVar

from ..model import AsyncTaskType


class AsyncTaskWorker(abc.ABC):
    """
    One claimed task, run to one of its end states.

    `TASK_TYPE` is a class variable because the type a worker serves belongs to the kind of worker.
    `worker_for` reads it to build the registry, so declaring a class registers it.
    """

    TASK_TYPE: ClassVar[AsyncTaskType]

    @abc.abstractmethod
    def run(self) -> None:
        """Runs the task to an end state and reports it. Never raises."""
