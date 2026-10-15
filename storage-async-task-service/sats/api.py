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
The only module that talks to the platform API, and it talks through the platform's own client.

`pipeline.PipelineAPI`, from `workflows/pipe-common`, does the HTTP: the url, the bearer token, the
retries and the `{"status": ..., "payload": ...}` envelope. The async-task methods this service needs
live there too - `claim_next_async_task`, `load_async_task`, `filter_async_tasks`,
`update_async_task_status` - next to the storage, region and preference methods that client already
had. The lifecycle service uses the same client in the same way, behind an interface of its own
(`sls/pipelineapi/cp_api_interface_impl.py`).

What is left here is the part that belongs to this service, and it is two things.

**Two kinds of failure, which a task queue depends on telling apart.** `PipelineAPI._request` raises
`APIError` when the API answered "no", and never retries it; a transport failure it retries, and
re-raises once the attempts are spent. Those become `ApiRejected` and `ApiError`, and the difference is
not cosmetic: a refused status transition means somebody else - the cleaner, usually - has already
finished this task, and the worker must clean up quietly instead of retrying.

Two limits of that, both of them in the platform client rather than here:

  * the methods written against its *older* request path - `get_preference` and `get_region` - treat an
    HTTP-200 error envelope as a success and answer with no payload at all, so a refusal arrives as
    `None`. Both methods below check for it and raise, which is the only way to tell "the API said no"
    from "there is nothing configured";
  * a non-200 answer becomes an `HTTPError`, which *is* retried before it reaches us, and therefore
    arrives as `ApiError`. The platform reports business failures as 200 with an error envelope, so
    this costs three attempts only for an authentication or gateway problem - which is the one case
    where the delay is harmless.

**The parsing.** Every method answers with this service's own frozen model from `model.py` rather than
with a dictionary, so nothing downstream reads the wire format.

One client is built twice. **Claiming a task must not be retried**: the API moves a task to `RUNNING`
and hands it over in the same transaction, so a lost *response* means the task is ours and nobody knows
it. A retry would claim a second task and leave the first `RUNNING` with no worker until the cleaner's
stuck window expires, hours later. The second client exists only for that one call and is built with a
single attempt.
"""

from __future__ import annotations

import datetime
from typing import Any

from .errors import ApiError, ApiRejected
from .log import get_logger
from .model import (ArchiveTask, AsyncTaskType, CloudRegion, FileShare, Storage, TaskStatus,
                    format_api_datetime)

LOG = get_logger(__name__)


def build_client(url: str, token: str, attempts: int, backoff_seconds: int, timeout_seconds: int):
    """
    Builds a `PipelineAPI`.

    **The import is inside this function rather than at the top of the module, and that is
    deliberate.** Importing `pipeline` pulls in the whole of pipe-common - its `__init__` imports luigi,
    tornado, paramiko and the rest - which a container installs from the API distribution exactly as the
    lifecycle service's does, and which this service's own tests have no use for. Keeping the import
    here means every module can be imported, and the whole suite can run with only `boto3` installed;
    the service itself fails at startup, loudly, if pipe-common is missing.

    `StaticToken` rather than the raw string: `PipelineAPI.header` calls `.get()` on whatever it was
    given, so a `str` token would raise an `AttributeError` on the first request instead of here.
    """
    from pipeline import PipelineAPI
    from pipeline.api.token import StaticToken
    return PipelineAPI(api_url=url, token=StaticToken(token), attempts=attempts,
                       timeout=backoff_seconds, connection_timeout=timeout_seconds)


def _refused(error: BaseException) -> bool:
    """
    Whether this is the API answering "no" rather than failing to answer.

    `APIError` is recognised by the name of a class in its ancestry rather than with `isinstance`,
    because naming the type would mean importing pipe-common here - see `build_client`. The cost is a
    string comparison; the gain is that this module stays importable where pipe-common is not installed,
    and that a test can inject a client of its own and raise its own `APIError`.
    """
    return any(ancestor.__name__ == 'APIError' for ancestor in type(error).__mro__)


class CloudPipelineApi:
    """
    A thin layer over `PipelineAPI`: two error kinds, and this service's own model.

    One instance is shared by the orchestrator thread, the cleaner and every worker thread. There is no
    state to protect - `PipelineAPI` makes a fresh `requests` call per request and keeps nothing between
    them - so nothing here needs a lock either. The price is a TLS handshake per call, which at a
    handful of calls per task plus one status poll per worker per interval is not worth optimising.

    `client` and `claim_client` are the injection points for tests, which pass a stub carrying the same
    methods and so need neither pipe-common nor a network.
    """

    def __init__(self, url: str, token: str, verify_ssl: bool = False,
                 attempts: int = 3, backoff_seconds: int = 5, timeout_seconds: int = 60,
                 client: Any = None, claim_client: Any = None):
        if verify_ssl:
            # Said once, at startup, rather than quietly doing the opposite of what was asked:
            # `PipelineAPI` passes `verify=False` on every call and offers no way to change it.
            LOG.warning('TLS verification was asked for, and the platform API client does not '
                        'support it: certificates will not be verified')
        self._client = client if client is not None else build_client(
            url, token, attempts, backoff_seconds, timeout_seconds)
        self._claim_client = claim_client if claim_client is not None else build_client(
            url, token, 1, backoff_seconds, timeout_seconds)

    # ------------------------------------------------------------------ preferences

    def get_preference(self, key: str) -> str | None:
        """
        Returns the raw value of a preference, as the string the platform stores.

        Parsing it is `StorageDownloadArchiveConfig.from_preference`'s job. Kept apart so that this
        module owes nothing to the shape of any particular preference.
        """
        payload = self._call(f'preference {key}', lambda: self._client.get_preference(key))
        if payload is None:
            # Not an empty value - no answer at all. `get_preference` goes through the platform
            # client's older request path, whose `check_response` treats an HTTP-200 error envelope as
            # a success and hands back no payload, so a refused read arrives here as `None`. Raising
            # keeps the orchestrator's "cannot read the preference, skip this round" behaviour, instead
            # of it parsing an empty configuration and blaming the administrator for an unset field.
            raise ApiRejected(f'Preference {key} could not be read')
        return payload.get('value')

    # ------------------------------------------------------------------ tasks

    def claim_next(self, task_type: AsyncTaskType) -> ArchiveTask | None:
        """
        Takes the oldest pending task of the type and moves it to `RUNNING`, atomically, on the API
        side.

        Returns `None` when the queue is empty. That is the ordinary answer and not a failure: the API
        answers with no payload, because an empty queue is the state this service spends most of its
        life in.

        Goes through the single-attempt client, for the reason in this module's docstring.
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
        """
        Lists tasks. Every argument is optional, and an omitted one is left out of the request body
        rather than sent as null.

        The body is built with a dictionary comprehension so that the "skip what is `None`" rule is
        written once instead of per field. Read `{k: v for k, v in pairs if v is not None}` as "for each
        pair, keep it unless the value is missing".
        """
        candidates = {
            'type': str(task_type) if task_type else None,
            # A list comprehension, the same idea applied to a sequence: `str(s)` of a `StrEnum` member
            # is its value, which is the name the API expects.
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

        The API is strict about which payload goes with which status, and refuses the request rather
        than ignoring the extra field:

            SUCCESS   `result` required, `errorMessage` must be absent
            FAILED    `errorMessage` required, `result` must be absent
            others    neither

        It also refuses a transition the task model does not allow, and a transition from a status the
        task no longer holds. Both come back as `ApiRejected`, and both mean the same thing to a worker:
        somebody else - the cleaner, usually - has already finished this task. Reporting the status the
        task already holds is explicitly allowed and changes nothing, which is what makes a retry after
        a lost response safe.
        """
        payload = self._call(
            f'status {status} of task {task_id}',
            lambda: self._client.update_async_task_status(task_id, str(status), result=result,
                                                          error_message=error_message))
        return ArchiveTask.from_api(payload)

    # ------------------------------------------------------------------ storages and regions

    def load_storage(self, storage_id: int) -> Storage:
        """Reads the storage a task points at: its type, and where its data physically is."""
        payload = self._call(f'storage {storage_id}',
                             lambda: self._client.load_datastorage(storage_id))
        return Storage.from_api(payload)

    def load_file_share(self, share_id: int) -> FileShare:
        """
        Reads the export an NFS storage belongs to. Only called for NFS storages.

        The platform client answers with its own `FileShareMount` object here rather than with a
        dictionary, and with an empty dictionary when there is nothing to return - which is why what
        comes back is checked for a field rather than for emptiness.
        """
        share = self._call(f'file share mount {share_id}',
                           lambda: self._client.load_file_share_mount(share_id))
        if not getattr(share, 'mount_root', None):
            raise ApiRejected(f'File share mount {share_id} was not found')
        return FileShare.from_share_mount(share)

    def load_region(self, region_id: int) -> CloudRegion:
        """
        Reads the cloud region a storage belongs to: how to authenticate there, and where "there" is.

        Everything `cloud/aws.py` needs comes from this one call - the role to assume, the profile to
        assume it from, the region code to sign for and a custom S3 endpoint if the deployment has one.

        Reading a region needs `READ` on it or the administrator role (`CloudRegionApiService.load`),
        and this service has the latter. The static access keys an administrator may have stored on the
        region are *not* in the answer: they are write-only through the region DTO, which is why a role
        is as far as this service can follow the Java provider.
        """
        payload = self._call(f'region {region_id}', lambda: self._client.get_region(region_id))
        if payload is None:
            # `None` rather than an exception for a refusal, for the reason given in `get_preference`.
            # Without this the parsing below raises `TypeError`, which nothing on the way up expects:
            # `cloud.aws` would not turn it into `CredentialsUnavailable` and the user would be told
            # "internal error" about a region they are not allowed to read.
            raise ApiRejected(f'Cloud region {region_id} could not be read')
        return CloudRegion.from_api(payload)

    # ------------------------------------------------------------------ internals

    @staticmethod
    def _call(what: str, action) -> Any:
        """
        Runs one call and turns whatever went wrong into one of this service's two error kinds.

        `action` is a function rather than a method name with arguments, because that keeps the call
        readable where it is made: `lambda: self._client.load_async_task(task_id)` says what is being
        asked for better than any dispatch table would.

        Parsing is deliberately left outside: `ArchiveTask.from_api` raises `ValueError` for an answer
        it cannot read, and the orchestrator handles that differently from an API that would not answer.
        """
        try:
            return action()
        except Exception as error:
            if _refused(error):
                raise ApiRejected(f'{what}: {error}') from error
            raise ApiError(f'{what}: {error}') from error
