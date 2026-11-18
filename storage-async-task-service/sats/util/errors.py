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


class UnsupportedArchive(Exception):
    """
    The file is not an archive this service can open: unknown format, encrypted, or damaged.

    Raised once for the archive, not per entry: an encrypted one would otherwise fail on every file
    and say nothing about the cause.
    """


class UnsafeArchiveEntry(Exception):
    """
    An entry inside the archive is named in a way this service refuses to write.

    The archive is readable; one name in it is not acceptable. The task fails on the first such name,
    before anything is written.
    """


class DestinationConflict(Exception):
    """
    Something is already where the extraction would write, and the task did not ask to replace it.

    Always answered the same way: refuse before the first upload, so the destination is left as it
    was.
    """


class DestinationWriteFailed(Exception):
    """
    One file could not be written into the destination, and it is not a collision.

    Not an internal error: the storage refused the write, or went away. One file failing fails the
    task - skipping it would leave a half-extracted archive that looks complete.
    """


class InvalidTask(Exception):
    """
    The task describes work that cannot be carried out as written.

    No storage, no paths, a storage deleted after the task was created, or a destination folder that
    is not on the share.
    """


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
