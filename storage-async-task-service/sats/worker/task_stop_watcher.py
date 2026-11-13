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
The one thing that lets a worker be stopped at all.

A Python thread cannot be stopped from outside, so a worker stops itself: it asks this class wherever
abandoning the work is safe, and the class raises when the answer is no.

It is in its own module because the reasons to stop belong to the task rather than to what is being
done with it.
"""

from __future__ import annotations

import time

from ..api import CloudPipelineApi
from ..util.errors import ApiError, ApiRejected, StopReason, StopRequested
from ..util.log import get_logger
from ..model import TaskStatus

LOG = get_logger(__name__)


class TaskStopWatcher:
    """
    Answers one question - may this worker carry on? - and raises when the answer is no.

    Two reasons to stop, with different costs: the deadline is local arithmetic and is checked on
    every call, while a cancellation has to be read from the API and so is checked at most once per
    `status_poll_seconds`. That throttle is what keeps the load at one call per worker per interval.

    Time is measured with `time.monotonic()`, never with the clock, which can jump.
    """

    def __init__(self, api: CloudPipelineApi, task_id: str, deadline_seconds: float,
                 poll_seconds: float):
        self._api = api
        self._task_id = task_id
        self._deadline = time.monotonic() + deadline_seconds
        self._poll_seconds = poll_seconds
        # The first poll is due one interval from now: the task was handed over as RUNNING a moment
        # ago, so asking again at once would learn nothing.
        self._next_poll = time.monotonic() + poll_seconds

    def check(self) -> None:
        """
        Raises `StopRequested` if the task should be abandoned. Returns nothing otherwise.

        Raising rather than returning a boolean: the alternative is a flag every caller has to
        remember to act on, inside a loop that copies a file.
        """
        now = time.monotonic()
        if now >= self._deadline:
            raise StopRequested(StopReason.TIMEOUT)
        if now < self._next_poll:
            return
        self._next_poll = now + self._poll_seconds
        try:
            status = self._api.load_task(self._task_id).status
        except (ApiError, ApiRejected) as error:
            # An API that is briefly unreachable is no reason to throw away work in progress. If it
            # stays down, the deadline stops us anyway.
            LOG.warning('Cannot read the status of task %s (%s), carrying on', self._task_id, error)
            return
        if status == TaskStatus.CANCEL_REQUESTED:
            raise StopRequested(StopReason.CANCELLED)
        if status.is_terminal:
            # Somebody else ended this task - the cleaner failing it as stuck, in practice. Nothing
            # we report would be accepted, and the archive belongs to nobody.
            raise StopRequested(StopReason.ABANDONED)
