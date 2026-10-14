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
The only module that speaks HTTP to the platform API.

Everything else in the service receives a `CloudPipelineApi` and calls methods on it. That is not
layering for its own sake: it is what lets the worker and the janitor be tested without a platform, by
passing an object with the same methods.

Two things about this API are easy to get wrong, and both are handled in `_unwrap`:

  * **every answer is an envelope.** `{"status": "OK", "payload": ...}` on success, and the interesting
    part is always `payload`. A caller that uses the response body directly gets the envelope;
  * **a refusal is not an HTTP error.** The platform's exception handler answers a business failure -
    a forbidden status transition, a task that does not exist, a quota that is full - with HTTP **200**
    and `{"status": "ERROR", "message": "..."}`. So the status code cannot be used to decide whether a
    call worked, and a client that only checks `raise_for_status()` will treat a refusal as a success.

The split between `ApiError` and `ApiRejected` follows from the second point: a transport failure is
worth retrying, a refusal never is.
"""

from __future__ import annotations

import datetime
import threading
import time
from typing import Any

import requests
import urllib3

from .errors import ApiError, ApiRejected
from .log import get_logger
from .model import ArchiveTask, CloudRegion, FileShare, Storage, TaskStatus, format_api_datetime

# A relative import: the leading dot means "from this package", so `.errors` is `sas/errors.py` and
# never some unrelated `errors` module that happens to be installed. Always use them within a package.

LOG = get_logger(__name__)

STATUS_OK = 'OK'


class CloudPipelineApi:
    """
    A thin client over the endpoints this service needs.

    One instance is shared by the orchestrator thread, the janitor and every worker thread. `requests`
    sessions are not documented as thread-safe, so each thread gets its own - see `_session`. A single
    shared session behind a lock would work too, and was the first version of this; the problem is that
    the lock has to be held across the call, so one request waiting out its sixty second timeout also
    holds up every other thread's. A worker's cancellation poll would then be late by the timeout of a
    call it has nothing to do with. One session per thread costs a handful of connections - there are
    `slots` workers plus the loop - and keeps the TLS handshake out of every call just the same.
    """

    def __init__(self, url: str, token: str, verify_ssl: bool = False,
                 attempts: int = 3, backoff_seconds: int = 5, timeout_seconds: int = 60):
        # `rstrip('/')` plus an explicit slash when joining: without it, a url configured with a
        # trailing slash produces `//async-task/next`, which some proxies answer with a redirect that
        # drops the Authorization header.
        self._url = url.rstrip('/')
        self._verify_ssl = verify_ssl
        self._attempts = max(attempts, 1)
        self._backoff_seconds = backoff_seconds
        self._timeout_seconds = timeout_seconds
        self._headers = {
            # The platform accepts its JWT as a bearer token. The token itself never reaches a log
            # line: `_request` logs the method and the endpoint, never the headers.
            'Authorization': f'Bearer {token}',
            'Content-Type': 'application/json',
        }
        # `threading.local()` is an object whose attributes are per thread: one thread setting
        # `self._local.session` cannot see or disturb another's. It is how a per-thread value is held
        # without a map from thread to value, and without having to clean the map up.
        self._local = threading.local()
        if not verify_ssl:
            # Without this, every single call prints an InsecureRequestWarning, and an operator reading
            # the log stops seeing anything else. The verification is off by choice - a platform
            # deployment normally signs its own certificate - so the warning carries no information.
            urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

    # ------------------------------------------------------------------ preferences

    def get_preference(self, key: str) -> str | None:
        """
        Returns the raw value of a preference, as the string the platform stores.

        Parsing it is `PlatformConfig.from_preference`'s job. Kept apart so that this module owes
        nothing to the shape of any particular preference.
        """
        payload = self._request('GET', f'preferences/{key}')
        if not payload:
            return None
        return payload.get('value')

    # ------------------------------------------------------------------ tasks

    def claim_next(self, task_type: str) -> ArchiveTask | None:
        """
        Takes the oldest pending task of the type and moves it to `RUNNING`, atomically, on the API
        side.

        Returns `None` when the queue is empty. That is the ordinary answer and not a failure: the API
        answers `{"status": "OK", "payload": null}`, because an empty queue is the state the service
        spends most of its life in.

        One task per call is the endpoint's design, not a convenience of this client. Between the moment
        a task is marked running and the moment a worker actually starts on it, nobody is working on it
        and a crash leaves it to time out. Claiming one at a time keeps exactly one task exposed to
        that window.

        `retry=False`, and it is the only call in this client that asks for it. This is the one request
        that is not idempotent: the API moves a task to `RUNNING` and hands it over in the same
        transaction, so a lost *response* means the task was claimed by us and nobody knows it. A retry
        would claim a second task and leave the first `RUNNING` with no worker, invisible until the
        janitor's stuck window expires hours later. Failing instead costs one poll interval.
        """
        payload = self._request('POST', 'async-task/next', params={'type': task_type},
                                retry=False)
        if not payload:
            return None
        return ArchiveTask.from_api(payload)

    def load_task(self, task_id: str) -> ArchiveTask:
        """Reads one task. Used by a worker to poll whether its own task is still wanted."""
        return ArchiveTask.from_api(self._request('GET', f'async-task/{task_id}'))

    def filter_tasks(self, task_type: str | None = None,
                     statuses: list[TaskStatus] | None = None,
                     status_changed_before: datetime.datetime | None = None,
                     finished_before: datetime.datetime | None = None,
                     page_size: int | None = None) -> list[ArchiveTask]:
        """
        Lists tasks. Every argument is optional, and an omitted one is left out of the request body
        rather than sent as null.

        Keyword arguments with defaults are how Python does overloads: a caller names only what it
        cares about - `filter_tasks(statuses=[TaskStatus.RUNNING])` - and the rest keeps its default.

        The body is built with a dictionary comprehension so that the "skip what is `None`" rule is
        written once instead of per field. Read `{k: v for k, v in pairs if v is not None}` as "for each
        pair, keep it unless the value is missing".
        """
        candidates = {
            'type': task_type,
            # A list comprehension, the same idea applied to a sequence: `str(s)` of a `StrEnum` member
            # is its value, which is the name the API expects.
            'statuses': [str(status) for status in statuses] if statuses else None,
            'statusChangedBefore': (format_api_datetime(status_changed_before)
                                    if status_changed_before else None),
            'finishedBefore': format_api_datetime(finished_before) if finished_before else None,
            'pageSize': page_size,
        }
        body = {key: value for key, value in candidates.items() if value is not None}
        payload = self._request('POST', 'async-task/filter', body=body)
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
        somebody else - the janitor, usually - has already finished this task. Reporting the status the
        task already holds is explicitly allowed and changes nothing, which is what makes a retry after
        a lost response safe.
        """
        body: dict[str, Any] = {'status': str(status)}
        if result is not None:
            body['result'] = result
        if error_message is not None:
            body['errorMessage'] = error_message
        return ArchiveTask.from_api(self._request('POST', f'async-task/{task_id}/status', body=body))

    # ------------------------------------------------------------------ storages

    def load_storage(self, storage_id: int) -> Storage:
        """Reads the storage a task points at: its type, and where its data physically is."""
        return Storage.from_api(self._request('GET', f'datastorage/{storage_id}/load'))

    def load_file_share(self, share_id: int) -> FileShare:
        """Reads the export an NFS storage belongs to. Only called for NFS storages."""
        return FileShare.from_api(self._request('GET', f'filesharemount/{share_id}'))

    def load_region(self, region_id: int) -> CloudRegion:
        """
        Reads the cloud region a storage belongs to: how to authenticate there, and where "there" is.

        Everything `aws.py` needs comes from this one call - the role to assume, the profile to assume
        it from, the region code to sign for and a custom S3 endpoint if the deployment has one. It is
        the same call the lifecycle service makes (`PipelineAPI.get_region`).

        Reading a region needs `READ` on it or the administrator role
        (`CloudRegionApiService.load`), and this service has the latter. The static access keys an
        administrator may have stored on the region are *not* in the answer: they are write-only
        through the region DTO, which is why a role is as far as this service can follow the Java
        provider.
        """
        return CloudRegion.from_api(self._request('GET', f'cloud/region/{region_id}'))

    # ------------------------------------------------------------------ internals

    @property
    def _session(self) -> requests.Session:
        """
        This thread's session, created the first time the thread asks for one.

        `@property` makes a method readable as an attribute: `self._session` calls this. It is how a
        lazily created value keeps the syntax of a plain field, and why no caller has to know that the
        session is per thread.
        """
        session = getattr(self._local, 'session', None)
        if session is None:
            session = requests.Session()
            session.headers.update(self._headers)
            self._local.session = session
        return session

    def _request(self, method: str, endpoint: str, params: dict[str, Any] | None = None,
                 body: Any | None = None, retry: bool = True) -> Any:
        """
        Makes one call and returns its payload, retrying only what is worth retrying.

        The retry loop covers connection failures, timeouts and 5xx answers - the platform restarting,
        a proxy dropping a connection, a network blip. It deliberately does not cover `ApiRejected`:
        the API has answered, and the answer will not change.

        `retry=False` makes it a single attempt, for a call whose effect cannot be repeated safely.
        Only `claim_next` passes it, and its docstring says why.

        `range(1, n + 1)` counts attempts from one so that the log line reads naturally. `raise` with
        no argument re-raises the exception currently being handled, keeping its original traceback.
        """
        url = f'{self._url}/{endpoint}'
        attempts = self._attempts if retry else 1
        for attempt in range(1, attempts + 1):
            try:
                LOG.debug('%s %s (attempt %s/%s)', method, endpoint, attempt, attempts)
                response = self._session.request(
                    method, url, params=params, json=body,
                    verify=self._verify_ssl, timeout=self._timeout_seconds)
                return self._unwrap(response, endpoint)
            except ApiRejected:
                # A refusal, not a failure. Hand it straight to the caller - retrying would only
                # collect the same answer three times, and the caller has to decide what the refusal
                # means for the task it was working on.
                raise
            except (ApiError, requests.RequestException) as error:
                if attempt == attempts:
                    # `from error` chains the exceptions, so the traceback shows both this message and
                    # the original failure underneath it. Without it the cause is lost.
                    raise ApiError(f'{method} {endpoint} failed after {attempt} attempts: '
                                   f'{error}') from error
                LOG.warning('%s %s failed (%s), retrying in %s s', method, endpoint, error,
                            self._backoff_seconds)
                time.sleep(self._backoff_seconds)
        # Unreachable: the loop either returns or raises. Python needs no such statement, but a reader
        # checking that every path is covered does.
        raise ApiError(f'{method} {endpoint} produced no answer')

    @staticmethod
    def _unwrap(response: requests.Response, endpoint: str) -> Any:
        """
        Takes the payload out of the envelope, and turns a refusal into `ApiRejected`.

        `@staticmethod` is a method with no `self`: it needs nothing from the instance. Same meaning as
        Java's `static`.

        Order matters here. The body is read first, because the platform puts its own error message
        into the envelope even on some non-200 answers, and that message is far more useful than
        "HTTP 400". Only when the body is not a usable envelope does the status code become the error.
        """
        try:
            envelope = response.json()
        except ValueError:
            # Not JSON at all: an nginx error page, an empty body from a proxy timeout, a redirect to a
            # login form. The status code is all we have.
            raise ApiError(f'{endpoint} answered {response.status_code} with a non-JSON body: '
                           f'{response.text[:200]}') from None
        if not isinstance(envelope, dict):
            # Valid JSON, but not the envelope - a bare list or string, which is what a proxy in front
            # of the API answers with when it serves its own response. Checked rather than assumed,
            # because `.get` on a list raises `AttributeError`, and that is a kind of failure no caller
            # here expects or handles.
            raise ApiError(f'{endpoint} answered {response.status_code} with a body that is not an '
                           f'API envelope: {response.text[:200]}')
        status = envelope.get('status')
        if status == STATUS_OK:
            # `.get` and not `['payload']`: a successful answer with nothing to return omits the key
            # entirely, and `/async-task/next` on an empty queue is exactly that case.
            return envelope.get('payload')
        message = envelope.get('message') or f'HTTP {response.status_code}'
        if response.status_code >= 500:
            # The platform is unwell rather than disagreeing with us - this one is worth retrying.
            raise ApiError(f'{endpoint}: {message}')
        raise ApiRejected(f'{endpoint}: {message}')
