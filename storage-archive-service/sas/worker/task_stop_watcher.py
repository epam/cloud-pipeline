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

**A Python thread cannot be stopped from outside.** There is no `Thread.stop()`, no interrupt, nothing
an orchestrator can do to a worker that must give up its task. So a worker stops itself: it asks this
class at every point where abandoning the work is safe, and the class raises when the answer is no.

It lives in its own module because every kind of worker needs it, not only the one that prepares
archives: the reasons to stop - the user cancelled, we ran too long, somebody else finished the task -
belong to the task, not to what is being done with it.
"""

from __future__ import annotations

import time

from ..api import CloudPipelineApi
from ..errors import ApiError, ApiRejected, StopReason, StopRequested
from ..log import get_logger
from ..model import TaskStatus

LOG = get_logger(__name__)


class TaskStopWatcher:
    """
    Answers one question - may this worker carry on? - and raises when the answer is no.

    Two independent reasons to stop, with deliberately different costs:

      * **the deadline** is local arithmetic, so it is checked on every call;
      * **a cancellation** has to be read from the API, so it is checked at most once per
        `status_poll_seconds`. With one poll per worker per interval, the load this puts on the API is
        the number of slots divided by the interval, and that is the whole reason the throttle exists.

    Time is measured with `time.monotonic()`, never with the clock. A monotonic clock only ever moves
    forward at a steady rate; the wall clock can jump - NTP correcting a drift, a container resuming,
    a daylight-saving change - and a deadline measured against it can jump an hour into the past or
    the future.
    """

    def __init__(self, api: CloudPipelineApi, task_id: str, deadline_seconds: float,
                 poll_seconds: float):
        self._api = api
        self._task_id = task_id
        self._deadline = time.monotonic() + deadline_seconds
        self._poll_seconds = poll_seconds
        # The first poll is due one interval from now: the task was handed to us as RUNNING a moment
        # ago, so asking again immediately would learn nothing.
        self._next_poll = time.monotonic() + poll_seconds

    def check(self) -> None:
        """
        Raises `StopRequested` if the task should be abandoned. Returns nothing otherwise.

        A method called for its exceptions rather than its value reads oddly at first, and it is the
        right shape here: the alternative is a boolean every caller has to remember to act on, in a
        loop that copies a file, where forgetting is both easy and invisible.
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
            # The API being briefly unreachable is not a reason to throw away work in progress. The
            # next check tries again; if the API stays down, the deadline eventually stops us anyway.
            LOG.warning('Cannot read the status of task %s (%s), carrying on', self._task_id, error)
            return
        if status == TaskStatus.CANCEL_REQUESTED:
            raise StopRequested(StopReason.CANCELLED)
        if status.is_terminal:
            # Somebody else ended this task - the cleaner failing it as stuck, in practice. Nothing we
            # report will be accepted, and the archive we are building belongs to nobody.
            raise StopRequested(StopReason.ABANDONED)
