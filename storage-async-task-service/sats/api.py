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
The only module that talks to the platform API, through the platform's own client.

`pipeline.PipelineAPI` from `workflows/pipe-common` does the HTTP: url, token, retries and the
`{"status": ..., "payload": ...}` envelope. What is added here is two things.

**Two kinds of failure.** `ApiRejected` is the API answering "no" - never worth retrying, and for a
status report it usually means the cleaner already finished the task. `ApiError` is no usable answer,
which is worth retrying. Two limits of that live in the platform client: `get_preference` and
`get_region` answer a refusal with no payload at all, so both methods below check for `None`; and a
non-200 answer is retried before it reaches us and arrives as `ApiError`.

**The parsing.** Every method answers with the frozen model from `model.py`, so nothing downstream
reads the wire format.

The client is built twice. **Claiming a task must not be retried**: the API moves a task to `RUNNING`
and hands it over in one transaction, so a lost response means the task is ours and nobody knows it. A
retry would claim a second task and leave the first `RUNNING` until the cleaner's stuck window
expires. The second client exists for that one call and has a single attempt.
"""

from __future__ import annotations

import datetime
from typing import Any

from .util.errors import ApiError, ApiRejected
from .util.log import get_logger
from .model import (ArchiveTask, AsyncTaskType, CloudRegion, FileShare, Storage, TaskStatus,
                    format_api_datetime)

LOG = get_logger(__name__)


def build_client(url: str, token: str, attempts: int, backoff_seconds: int, timeout_seconds: int):
    """
    Builds a `PipelineAPI`.

    The import is inside the function on purpose: importing `pipeline` pulls in the whole of
    pipe-common, which the tests do without. The service fails loudly at startup if it is missing.

    `StaticToken` rather than a plain string, because `PipelineAPI.header` calls `.get()` on it.
    """
    from pipeline import PipelineAPI
    from pipeline.api.token import StaticToken
    return PipelineAPI(api_url=url, token=StaticToken(token), attempts=attempts,
                       timeout=backoff_seconds, connection_timeout=timeout_seconds)


def _refused(error: BaseException) -> bool:
    """
    Whether this is the API answering "no" rather than failing to answer.

    `APIError` is matched by class name rather than with `isinstance`, so that this module stays
    importable without pipe-common.
    """
    return any(ancestor.__name__ == 'APIError' for ancestor in type(error).__mro__)


class CloudPipelineApi:
    """
    A thin layer over `PipelineAPI`: two error kinds, and this service's own model.

    One instance is shared by every thread. `PipelineAPI` keeps no state between requests, so no lock
    is needed. `client` and `claim_client` are the injection points for tests.
    """

    def __init__(self, url: str, token: str, verify_ssl: bool = False,
                 attempts: int = 3, backoff_seconds: int = 5, timeout_seconds: int = 60,
                 client: Any = None, claim_client: Any = None):
        if verify_ssl:
            # `PipelineAPI` passes `verify=False` on every call and offers no way to change it, so say
            # so instead of quietly doing the opposite.
            LOG.warning('TLS verification was asked for, and the platform API client does not '
                        'support it: certificates will not be verified')
        self._client = client if client is not None else build_client(
            url, token, attempts, backoff_seconds, timeout_seconds)
        self._claim_client = claim_client if claim_client is not None else build_client(
            url, token, 1, backoff_seconds, timeout_seconds)

    # ------------------------------------------------------------------ preferences

    def get_preference(self, key: str) -> str | None:
        """Returns the raw value of a preference. Parsing it belongs to `config.py`."""
        payload = self._call(f'preference {key}', lambda: self._client.get_preference(key))
        if payload is None:
            # No answer at all, which is how this call reports a refusal. Raising keeps it apart from
            # "the preference is not configured".
            raise ApiRejected(f'Preference {key} could not be read')
        return payload.get('value')

    # ------------------------------------------------------------------ tasks

    def claim_next(self, task_type: AsyncTaskType) -> ArchiveTask | None:
        """
        Takes the oldest pending task of the type and moves it to `RUNNING`, atomically.

        `None` means the queue is empty, which is the ordinary answer. Goes through the single-attempt
        client - see the module docstring.
        """
        payload = self._call('the next task',
                             lambda: self._claim_client.claim_next_async_task(str(task_type)))
        if not payload:
            return None
        return ArchiveTask.from_api(payload)

    def load_task(self, task_id: str) -> ArchiveTask:
        """Reads one task. Used by a worker to poll whether its own task is still wanted."""
        payload = self._call(f'task {task_id}', lambda: self._client.load_async_task(task_id))
        return ArchiveTask.from_api(payload)

    def filter_tasks(self, task_type: AsyncTaskType | None = None,
                     statuses: list[TaskStatus] | None = None,
                     status_changed_before: datetime.datetime | None = None,
                     finished_before: datetime.datetime | None = None,
                     page_size: int | None = None) -> list[ArchiveTask]:
        """Lists tasks. An omitted argument is left out of the body rather than sent as null."""
        candidates = {
            'type': str(task_type) if task_type else None,
            'statuses': [str(status) for status in statuses] if statuses else None,
            'statusChangedBefore': (format_api_datetime(status_changed_before)
                                    if status_changed_before else None),
            'finishedBefore': format_api_datetime(finished_before) if finished_before else None,
            'pageSize': page_size,
        }
        body = {key: value for key, value in candidates.items() if value is not None}
        payload = self._call('the task filter', lambda: self._client.filter_async_tasks(body))
        return [ArchiveTask.from_api(item) for item in payload or []]

    def report_status(self, task_id: str, status: TaskStatus,
                      result: dict[str, Any] | None = None,
                      error_message: str | None = None) -> ArchiveTask:
        """
        Reports a status change.

        The API refuses the wrong payload for a status:

            SUCCESS   `result` required, `errorMessage` must be absent
            FAILED    `errorMessage` required, `result` must be absent
            others    neither

        It also refuses a transition the task no longer allows, as `ApiRejected`. Reporting the status
        the task already holds is allowed, which makes a retry after a lost response safe.
        """
        payload = self._call(
            f'status {status} of task {task_id}',
            lambda: self._client.update_async_task_status(task_id, str(status), result=result,
                                                          error_message=error_message))
        return ArchiveTask.from_api(payload)

    # ------------------------------------------------------------------ storages and regions

    def load_storage(self, storage_id: int) -> Storage:
        """Reads the storage a task points at: its type, and where its data is."""
        payload = self._call(f'storage {storage_id}',
                             lambda: self._client.load_datastorage(storage_id))
        return Storage.from_api(payload)

    def load_file_share(self, share_id: int) -> FileShare:
        """
        Reads the export an NFS storage belongs to.

        The platform client answers with its own object here, and with an empty dictionary when there
        is nothing to return, so the result is checked for a field.
        """
        share = self._call(f'file share mount {share_id}',
                           lambda: self._client.load_file_share_mount(share_id))
        if not getattr(share, 'mount_root', None):
            raise ApiRejected(f'File share mount {share_id} was not found')
        return FileShare.from_share_mount(share)

    def load_region(self, region_id: int) -> CloudRegion:
        """
        Reads the cloud region of a storage: everything `cloud/aws.py` needs.

        Static access keys stored on a region are not in the answer - they are write-only - so a role
        is as far as this service can follow the Java provider.
        """
        payload = self._call(f'region {region_id}', lambda: self._client.get_region(region_id))
        if payload is None:
            # A refusal, as in `get_preference`. Without this the parsing raises `TypeError`, which
            # nothing on the way up expects.
            raise ApiRejected(f'Cloud region {region_id} could not be read')
        return CloudRegion.from_api(payload)

    # ------------------------------------------------------------------ internals

    @staticmethod
    def _call(what: str, action) -> Any:
        """
        Runs one call and turns whatever went wrong into `ApiRejected` or `ApiError`.

        Parsing stays outside: `from_api` raises `ValueError`, which the orchestrator handles
        differently from an API that would not answer.
        """
        try:
            return action()
        except Exception as error:
            if _refused(error):
                raise ApiRejected(f'{what}: {error}') from error
            raise ApiError(f'{what}: {error}') from error
