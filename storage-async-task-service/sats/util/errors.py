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
Exception types of the service.

The worker's `run` catches each kind and turns it into a task status.
"""

from enum import StrEnum


class StopReason(StrEnum):
    """Why a worker gives up a task that was going fine."""

    # The owner pressed cancel and we saw CANCEL_REQUESTED on a poll.
    CANCELLED = 'cancelled'
    # Running longer than `workerMaxRunningMinutes`.
    TIMEOUT = 'timeout'
    # Already in a terminal status, so nothing we report would be accepted. Only clean up.
    ABANDONED = 'abandoned'


class StopRequested(Exception):
    """
    Raised by the stop check to unwind a worker out of whatever it is doing.

    A Python thread cannot be killed from outside, so stopping is cooperative: the worker calls
    `check()` where abandoning the work is safe, and the check raises.
    """

    def __init__(self, reason: StopReason):
        super().__init__(f'Worker stop requested: {reason}')
        self.reason = reason


class ThresholdExceeded(Exception):
    """
    The selection is too big for a threshold or for the local scratch space.

    The message is written for the user who asked for the archive.
    """


class UnsupportedSource(Exception):
    """The storage is of a kind this service cannot read."""


class InvalidTask(Exception):
    """The task describes no work - no storage, or no paths."""


class CredentialsUnavailable(Exception):
    """The cloud region of a storage cannot be read, so there is no role to assume."""


class ApiError(RuntimeError):
    """No usable answer from the API: connection refused, timeout, 5xx, broken JSON. Retry."""


class ApiRejected(ApiError):
    """
    The API answered "no". Retrying is pointless.

    Business failures arrive as HTTP 200 with `{"status": "ERROR", ...}`, so `api.py` reads the
    envelope rather than the status code.
    """
