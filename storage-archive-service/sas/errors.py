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

Python has no checked exceptions: nothing in a signature says what a call may raise, and nothing forces
a caller to handle it. So the contract lives here instead - this module is the list of what the service
throws at itself on purpose, and the worker's `run` is the single place that catches each kind and
turns it into a task status.

`class Foo(Bar)` is inheritance. Deriving from `Exception` is all it takes to make a raisable type;
`pass` as the body means "nothing to add", because the useful part is the type name itself.
"""

from enum import StrEnum


class StopReason(StrEnum):
    """
    Why a worker is giving up on a task that was going fine.

    `StrEnum` (Python 3.11 and later) is an enum whose members *are* strings: `StopReason.CANCELLED ==
    "cancelled"` is true and f-string interpolation prints the value, not `StopReason.CANCELLED`. Handy
    for anything that ends up in a log line or in an API payload.
    """

    # The task's owner pressed cancel: the API moved it to CANCEL_REQUESTED and we noticed on a poll.
    CANCELLED = 'cancelled'
    # We have been working on this task longer than `workerMaxRunningMinutes` allows.
    TIMEOUT = 'timeout'
    # The task is already in a terminal status - the cleaner failed it as stuck while we were working,
    # or someone else reported it. Nothing we report now would be accepted, so we only clean up.
    ABANDONED = 'abandoned'


class StopRequested(Exception):
    """
    Raised from a heartbeat check to unwind a worker out of whatever it is doing.

    A Python thread cannot be killed from the outside - there is no `Thread.stop()`, and for good
    reason, as a thread killed mid-write leaves half-written files and locked resources behind. The
    only way to stop one is to ask it to stop and have it agree. This exception is that agreement: the
    worker calls `check()` at every point where it is safe to abandon the work, and the check raises
    when it is time to go. Everything in between - open files, temporary directories, multipart
    uploads - is wrapped in `with` blocks or `try/finally`, so unwinding cleans up on the way out.
    """

    def __init__(self, reason: StopReason):
        # `super().__init__(...)` calls the base constructor, like Java's `super(...)`. The argument
        # becomes the exception's `args` and therefore its `str()`.
        super().__init__(f'Worker stop requested: {reason}')
        self.reason = reason


class ThresholdExceeded(Exception):
    """
    The selection cannot be packed, because it is too big for a limit that applies to it: one of the
    two thresholds in `storage.download.archive.config`, or the scratch space this service has.

    A hard failure rather than a stop: the task cannot succeed however long we wait, and its message is
    meant to be read by the user who asked for the archive, so it names the limit that was hit and what
    to do about it.
    """


class UnsupportedSource(Exception):
    """The storage the task points at is of a kind this service cannot read."""


class InvalidTask(Exception):
    """
    The task itself does not describe a piece of work - no storage, no paths.

    The API refuses to create such a task, so this is a guard against a task written to the database by
    something else, and against a change on the Java side that stops enforcing it. Separate from the
    catch-all so that it is reported as the task's own problem rather than as an internal error of this
    service.
    """


class CredentialsUnavailable(Exception):
    """
    There is no way to tell how to reach a storage this task needs.

    Raised when the cloud region of a storage cannot be had: the storage belongs to none, or the region
    cannot be read. Without it there is neither a role to assume nor a region to sign for, and the only
    alternative would be to guess from the environment - see `cloud/aws.py`.

    Separate from `InvalidTask`, which is about a task that describes no work, and from the catch-all,
    which reads as a defect of this service. This one is a deployment that is not finished, so the
    message says which storage and what went wrong: the person who can fix it is an administrator
    rather than the user who asked for the archive.
    """


class ApiError(RuntimeError):
    """
    The API call did not produce an answer we can use: connection refused, timeout, 5xx, broken JSON.

    Worth retrying - the platform may simply have been restarting.
    """


class ApiRejected(ApiError):
    """
    The API answered, and the answer is "no".

    The platform reports business failures as HTTP 200 with `{"status": "ERROR", "message": ...}`, so
    this is not detectable by status code - `api.py` looks into the envelope. Retrying is pointless:
    the request will be refused the same way every time. For a status report it usually means somebody
    else has already finished the task.
    """
