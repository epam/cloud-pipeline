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
What every worker has to be, so that the orchestrator does not have to know what it is running.

The orchestrator claims a task, looks at its type and runs *a* worker. Which class that is belongs to
this package and to nothing else - `worker_for` in `__init__.py` is the whole mapping. Adding the next
kind of task, unarchiving one of these archives back into a storage, is then a module here and a line
there.

`run()` taking no arguments and returning nothing is the entire contract, and the second half of it is
the harder half: **it must never raise.** A worker is the body of a thread-pool task, and an exception
escaping it would be stored in a future nobody reads - the task would stay `RUNNING` with nobody
working on it until the cleaner noticed hours later, and the log would say nothing. So every worker
catches everything and ends its own task, in whatever status fits.
"""

from __future__ import annotations

import abc
from typing import ClassVar

from ..model import AsyncTaskType


class AsyncTaskWorker(abc.ABC):
    """
    One claimed task, run to one of its end states.

    `ClassVar` says `TASK_TYPE` belongs to the class rather than to an instance - the type a worker
    serves is a property of the kind of worker, not of the task it happens to hold. `worker_for` reads
    it to build the registry, so a new worker class is registered by declaring it.

    `abc.ABC` plus `@abc.abstractmethod` is Python's abstract class: a subclass that left `run`
    unimplemented raises on construction rather than at the call. It is a convention with teeth, but a
    late one - nothing checks it when the module is imported.
    """

    TASK_TYPE: ClassVar[AsyncTaskType]

    @abc.abstractmethod
    def run(self) -> None:
        """Runs the task to an end state and reports it. Never raises."""
