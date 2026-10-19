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
The data the service works with, and the parsing of the API's JSON into it.

Each type mirrors a Java class of the platform, named in its docstring, and declares only the fields
this service reads. No behaviour here beyond parsing.
"""

from __future__ import annotations

import datetime
from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any

# How the platform writes a `LocalDateTime`: always UTC, never with a zone suffix.
API_DATETIME_FORMAT = '%Y-%m-%d %H:%M:%S.%f'

# Folder paths in a task end with a slash, files do not.
FOLDER_MARKER = '/'


def parse_api_datetime(raw: str | None) -> datetime.datetime | None:
    """
    Reads one of the platform's timestamps, and marks it as UTC.

    The platform sends UTC but says so nowhere, and comparing a naive datetime with an aware one
    raises. So the zone is attached here, at the boundary.
    """
    if not raw:
        return None
    return datetime.datetime.strptime(raw, API_DATETIME_FORMAT).replace(tzinfo=datetime.UTC)


def format_api_datetime(value: datetime.datetime) -> str:
    """
    Writes a timestamp with exactly three fractional digits.

    `strftime('%f')` prints six, and the platform's `.SSS` pattern rejects six.
    """
    utc = value.astimezone(datetime.UTC)
    return f'{utc.strftime("%Y-%m-%d %H:%M:%S")}.{utc.microsecond // 1000:03d}'


def utc_now() -> datetime.datetime:
    """The current time, zone-aware, in UTC."""
    return datetime.datetime.now(datetime.UTC)


class TaskStatus(StrEnum):
    """
    Mirror of `com.epam.pipeline.dto.async.AsyncTaskStatus`.

    Mind the spelling: `CANCEL_REQUESTED`, and `CANCELLED` with two Ls. The API owns the transition
    table and refuses a transition it does not allow, so it is not repeated here.
    """

    PENDING = 'PENDING'
    RUNNING = 'RUNNING'
    CANCEL_REQUESTED = 'CANCEL_REQUESTED'
    STOPPING = 'STOPPING'
    SUCCESS = 'SUCCESS'
    FAILED = 'FAILED'
    CANCELLED = 'CANCELLED'
    EXPIRED = 'EXPIRED'

    @property
    def is_terminal(self) -> bool:
        """
        True when no report will be accepted any more.

        `SUCCESS` counts as terminal here although `SUCCESS -> EXPIRED` exists: only the cleaner
        expires a task, and it selects by `finishedBefore`.
        """
        return self in _TERMINAL

    @property
    def is_active(self) -> bool:
        """True while the task still counts against its owner's quota."""
        return self in _ACTIVE


_TERMINAL = frozenset({TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.EXPIRED})
_ACTIVE = frozenset({TaskStatus.PENDING, TaskStatus.RUNNING, TaskStatus.CANCEL_REQUESTED,
                     TaskStatus.STOPPING})


class AsyncTaskType(StrEnum):
    """
    Mirror of `com.epam.pipeline.dto.async.AsyncTaskType`.

    One member today. An enum because the orchestrator asks the API per type and workers are
    registered by it - see `worker/__init__`.
    """

    DOWNLOAD_ARCHIVE = 'DOWNLOAD_ARCHIVE'


class StorageType(StrEnum):
    """
    Mirror of `com.epam.pipeline.entity.datastorage.DataStorageType`.

    Only `S3` and `NFS` can be read. The rest are declared so that a task pointing at one fails with
    a clear message.
    """

    S3 = 'S3'
    NFS = 'NFS'
    AZ = 'AZ'
    GS = 'GS'


class MountType(StrEnum):
    """Mirror of `com.epam.pipeline.entity.datastorage.MountType`."""

    NFS = 'NFS'
    SMB = 'SMB'
    LUSTRE = 'LUSTRE'


# The `-t` argument per mount type, as in Java's `MountType.protocol`. `cifs` for SMB is the kernel
# module name; `mount -t smb` does not exist.
MOUNT_PROTOCOLS = {
    MountType.NFS: 'nfs',
    MountType.SMB: 'cifs',
    MountType.LUSTRE: 'lustre',
}


@dataclass(frozen=True, slots=True)
class ArchiveTask:
    """
    Mirror of `AsyncTask` plus its `DownloadArchiveParams`, flattened into one object.

    Frozen because the object crosses from the orchestrator thread to a worker thread.
    """

    id: str
    status: TaskStatus
    owner: str | None
    storage_id: int | None
    # Which worker runs this task - see the registry in `worker/__init__`. The default is the only
    # type that exists today; remove it when a second one is added.
    type: AsyncTaskType = AsyncTaskType.DOWNLOAD_ARCHIVE
    paths: tuple[str, ...] = field(default_factory=tuple)
    archive_name: str = 'archive.zip'
    # Where a finished archive went. Both stay `None` for a task whose worker died before reporting;
    # the cleaner then derives the key from the configured destination.
    result_storage_id: int | None = None
    result_path: str | None = None
    created: datetime.datetime | None = None
    started: datetime.datetime | None = None
    status_changed: datetime.datetime | None = None
    finished: datetime.datetime | None = None

    @classmethod
    def from_api(cls, raw: dict[str, Any]) -> ArchiveTask:
        """
        Builds a task from one element of an API payload.

        `params` is polymorphic and may be absent. Only our own type is read; the id and the status
        are required.
        """
        task_id = raw.get('id')
        if not task_id:
            raise ValueError(f'Task without an id in the API answer: {raw}')
        status = raw.get('status')
        if not status:
            raise ValueError(f'Task {task_id} has no status in the API answer')
        params = raw.get('params') or {}
        if params.get('type') not in (None, AsyncTaskType.DOWNLOAD_ARCHIVE):
            raise ValueError(f'Task {task_id} carries params of a foreign type {params.get("type")}')
        paths = params.get('paths') or []
        # A foreign result type is not an error: the cleaner lists tasks of types it does not run, and
        # there is simply nothing to clean up for them.
        result = raw.get('result') or {}
        if result.get('type') != AsyncTaskType.DOWNLOAD_ARCHIVE:
            result = {}
        return cls(
            id=task_id,
            status=TaskStatus(status),
            type=AsyncTaskType(raw.get('type') or AsyncTaskType.DOWNLOAD_ARCHIVE),
            owner=raw.get('owner'),
            storage_id=params.get('storageId'),
            paths=tuple(paths),
            archive_name=params.get('archiveName') or 'archive.zip',
            result_storage_id=result.get('storageId'),
            result_path=result.get('path'),
            created=parse_api_datetime(raw.get('created')),
            started=parse_api_datetime(raw.get('started')),
            status_changed=parse_api_datetime(raw.get('statusChanged')),
            finished=parse_api_datetime(raw.get('finished')),
        )


@dataclass(frozen=True, slots=True)
class Storage:
    """
    Mirror of `AbstractDataStorage`, as far as a reader of files needs it.

    `path` differs per type:

        S3   `bucket` or `bucket/some/prefix`
        NFS  `host:/export/folder`, or `host:/export` for a whole share
    """

    id: int
    name: str
    type: StorageType
    path: str
    file_share_mount_id: int | None = None
    mount_options: str | None = None
    mount_point: str | None = None
    region_id: int | None = None
    # With `use_assumed_credentials` set, the storage is reached through a role of its own, usually
    # because it lives in another account. See `aws.resolve_role_arn`.
    use_assumed_credentials: bool = False
    temp_credentials_role: str | None = None

    @property
    def root(self) -> str:
        """The bucket name of an object storage."""
        return self.path.split('/', 1)[0]

    @property
    def prefix(self) -> str:
        """The key prefix of an object storage, without the bucket and without slashes."""
        _, _, rest = self.path.partition('/')
        return rest.strip('/')

    @classmethod
    def from_api(cls, raw: dict[str, Any]) -> Storage:
        return cls(
            id=raw['id'],
            name=raw.get('name') or '',
            type=StorageType(raw['type']),
            # The API is not consistent about a trailing slash, and one in an S3 prefix produces an
            # empty folder level in the archive.
            path=(raw.get('path') or '').strip('/'),
            file_share_mount_id=raw.get('fileShareMountId'),
            mount_options=raw.get('mountOptions'),
            mount_point=raw.get('mountPoint'),
            region_id=raw.get('regionId'),
            # `or False`: the field arrives as `null` for a storage older than it.
            use_assumed_credentials=raw.get('useAssumedCredentials') or False,
            temp_credentials_role=raw.get('tempCredentialsRole'),
        )


@dataclass(frozen=True, slots=True)
class FileShare:
    """
    Mirror of `FileShareMount`: the export an NFS storage lives in.

    `mount_root` is the share as the server offers it. Several storages may share one, which is why
    this service mounts the root rather than a storage's own folder.
    """

    id: int
    mount_root: str
    mount_type: MountType
    mount_options: str | None = None
    region_id: int | None = None

    @property
    def protocol(self) -> str:
        """The `-t` argument for `mount`."""
        return MOUNT_PROTOCOLS[self.mount_type]

    @classmethod
    def from_api(cls, raw: dict[str, Any]) -> FileShare:
        return cls(
            id=raw['id'],
            mount_root=raw['mountRoot'],
            mount_type=MountType(raw['mountType']),
            mount_options=raw.get('mountOptions'),
            region_id=raw.get('regionId'),
        )

    @classmethod
    def from_share_mount(cls, share: Any) -> FileShare:
        """
        Builds a share from pipe-common's own `FileShareMount`, which its client answers with.

        `Any` rather than the real type, because naming it would import pipe-common here.
        """
        return cls(
            id=share.id,
            mount_root=share.mount_root,
            mount_type=MountType(share.mount_type),
            mount_options=share.mount_options,
            region_id=share.region_id,
        )


@dataclass(frozen=True, slots=True)
class CloudRegion:
    """
    Mirror of `AwsRegion` - the fields that decide how a client reaches the cloud.

    `code` is the cloud's own region name and arrives as `regionId`; `id` is the platform's numeric
    id. Only the AWS fields are declared, so a region of another provider parses with them all `None`.
    """

    id: int
    code: str
    profile: str | None = None
    iam_role: str | None = None
    temp_credentials_role: str | None = None
    # A deployment running its own S3-compatible storage. See `aws.build_s3_client`.
    s3_endpoint: str | None = None

    @classmethod
    def from_api(cls, raw: dict[str, Any]) -> CloudRegion:
        return cls(
            id=raw['id'],
            code=raw['regionId'],
            profile=raw.get('profile'),
            iam_role=raw.get('iamRole'),
            temp_credentials_role=raw.get('tempCredentialsRole'),
            s3_endpoint=raw.get('s3Endpoint'),
        )
