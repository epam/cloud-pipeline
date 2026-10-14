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

Everything here is a mirror of a Java class of the platform, named in the docstring of each type. The
mirrors are deliberately narrow: only the fields this service reads are declared, so that a new field
on the Java side does not have to be repeated here.

This module has no behaviour beyond parsing. Nothing in it makes an HTTP call, touches a file or logs.
"""

from __future__ import annotations

import datetime
from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any

# How the platform writes a `LocalDateTime`: `yyyy-MM-dd HH:mm:ss.SSS`, always UTC, never with a zone
# suffix. `%f` means microseconds when parsing and accepts one to six digits, so it reads the three the
# platform sends. Writing needs care - see `format_api_datetime`.
API_DATETIME_FORMAT = '%Y-%m-%d %H:%M:%S.%f'

# The only task type this service claims. The platform's `AsyncTaskType` has exactly this one member
# today, but `/async-task/next` takes the type as a parameter, so it has to be named.
TASK_TYPE = 'DOWNLOAD_ARCHIVE'

# Folder paths in a task come with a trailing slash, files come without one. That is the whole
# distinction, and the API makes no other, so it is a constant rather than a repeated literal.
FOLDER_MARKER = '/'


def parse_api_datetime(raw: str | None) -> datetime.datetime | None:
    """
    Reads one of the platform's timestamps.

    `str | None` is the type hint for "a string or nothing" - the modern spelling of
    `Optional[str]`. Python does not enforce hints at runtime; they are for the reader and for tools
    like mypy. `None` is the single null value of the language.

    The result is marked as UTC explicitly. Python distinguishes "naive" datetimes, which carry no
    zone, from "aware" ones, and comparing the two raises a `TypeError`. The platform's timestamps are
    UTC but say so nowhere, so the zone is attached here, once, at the boundary - otherwise the mistake
    shows up much later, as a comparison that blows up or, worse, a local-time offset silently applied.
    """
    if not raw:
        # An empty string and `None` are both falsy, so one check covers both. This is idiomatic
        # Python; `if raw is None` would be the strict spelling and would miss `''`.
        return None
    return datetime.datetime.strptime(raw, API_DATETIME_FORMAT).replace(tzinfo=datetime.UTC)


def format_api_datetime(value: datetime.datetime) -> str:
    """
    Writes a timestamp the way the platform's deserializer expects it: exactly three fractional digits.

    `strftime('%f')` always prints six, and the platform's pattern is `.SSS`, which rejects six. So the
    microseconds are cut to milliseconds by hand. Integer division `//` is used rather than `/` because
    `/` produces a float in Python 3 and `1000.0` is not what we want to print.

    `:03d` inside an f-string is a format specification: decimal, at least three characters, zero
    padded. An f-string is a literal prefixed with `f` whose `{...}` parts are evaluated and inserted.
    """
    utc = value.astimezone(datetime.UTC)
    return f'{utc.strftime("%Y-%m-%d %H:%M:%S")}.{utc.microsecond // 1000:03d}'


def utc_now() -> datetime.datetime:
    """
    The current time, zone-aware, in UTC.

    `datetime.utcnow()` would give the same instant but naive, and is deprecated in 3.12 for exactly
    the confusion that causes. One helper so the choice is made in one place.
    """
    return datetime.datetime.now(datetime.UTC)


class TaskStatus(StrEnum):
    """
    Mirror of `com.epam.pipeline.dto.async.AsyncTaskStatus`.

    Note the two spellings that have bitten people already: the status is `CANCEL_REQUESTED`, and the
    terminal one is `CANCELLED` with two Ls. The API's deserializer compares case-insensitively but is
    otherwise exact, and answers an unknown value with `Status 'X' does not exist`.

    The transition table is not repeated here: the API is the authority on it and refuses a transition
    it does not allow. The sets below are the few questions the service asks about a status.
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
        True when nothing can move the task any further - with one exception, see below.

        `@property` makes a method readable as a field: `task.status.is_terminal`, no parentheses. The
        Java equivalent would be a getter, except that here the call site cannot tell the difference,
        which is the point.

        `SUCCESS` is listed as terminal although `SUCCESS -> EXPIRED` exists. For a worker it is
        terminal - the work is done and no report will be accepted - and the janitor, which is the only
        thing that expires a task, selects by `finishedBefore` rather than by asking this.
        """
        return self in _TERMINAL

    @property
    def is_active(self) -> bool:
        """True while the task still counts against its owner's quota."""
        return self in _ACTIVE


# Module-private constants: a single leading underscore is the convention for "not part of the public
# surface". Nothing enforces it, no more than a comment would, but every Python reader respects it.
# `frozenset` is an immutable set - the closest thing to an unmodifiable `EnumSet`.
_TERMINAL = frozenset({TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.EXPIRED})
_ACTIVE = frozenset({TaskStatus.PENDING, TaskStatus.RUNNING, TaskStatus.CANCEL_REQUESTED,
                     TaskStatus.STOPPING})


class StorageType(StrEnum):
    """
    Mirror of `com.epam.pipeline.entity.datastorage.DataStorageType`, the members only.

    Of these, the service can read `S3` and `NFS`. The rest are declared so that a task pointing at one
    produces a clear "not supported" rather than an attribute error somewhere in a source class.
    """

    S3 = 'S3'
    NFS = 'NFS'
    AZ = 'AZ'
    GS = 'GS'


class MountType(StrEnum):
    """
    Mirror of `com.epam.pipeline.entity.datastorage.MountType`, including the protocol each one mounts
    with, which is the value `mount -t` wants.

    An enum member may hold more than its value if given a constructor, but a `StrEnum` member *is* its
    value, so the protocol goes into a lookup table next to it instead.
    """

    NFS = 'NFS'
    SMB = 'SMB'
    LUSTRE = 'LUSTRE'


# Matches `MountType`'s `protocol` field in Java. `cifs` for SMB is not a typo: that is the name of the
# kernel module, and `mount -t smb` does not exist.
MOUNT_PROTOCOLS = {
    MountType.NFS: 'nfs',
    MountType.SMB: 'cifs',
    MountType.LUSTRE: 'lustre',
}


@dataclass(frozen=True, slots=True)
class ArchiveTask:
    """
    Mirror of `AsyncTask` plus its `DownloadArchiveParams`, flattened into one object.

    `@dataclass` writes the constructor, `__repr__` and `__eq__` from the annotated fields - the same
    service lombok's `@Value` provides. `frozen=True` makes instances immutable: assigning to a field
    raises. `slots=True` (3.10 and later) stores the fields in a fixed layout instead of a dictionary,
    which also means a typo like `task.statuss = ...` fails loudly instead of quietly adding a field.

    Immutability is not decoration here. A task object is handed from the orchestrator thread to a
    worker thread, and an object nobody can modify needs no lock.
    """

    id: str
    status: TaskStatus
    owner: str | None
    storage_id: int | None
    # `tuple[str, ...]` is an immutable sequence of strings of any length. A `list` would be mutable
    # and would break `frozen=True`'s promise - frozen stops field assignment, not mutation of what a
    # field points at. `field(default_factory=tuple)` gives each instance its own empty tuple; a plain
    # `= ()` would also work for an immutable default, but the factory form is the habit to keep,
    # because for a mutable default Python shares one object across every instance ever created.
    paths: tuple[str, ...] = field(default_factory=tuple)
    archive_name: str = 'archive.zip'
    # Where the archive of a finished task actually went: the storage, and the key inside it,
    # storage-relative. Both are `None` until a task succeeds, and stay `None` for a task whose worker
    # died before reporting; the janitor derives the key from the configured destination for that case.
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

        `@classmethod` with a `cls` first argument is a named constructor - Java's static factory. It
        is the usual way to keep parsing next to the type without putting it in `__init__`.

        `raw.get('x')` returns `None` for a missing key, where `raw['x']` would raise. Everything
        optional is read with `get`; only the id is required, because a task without one cannot be
        reported about.

        `params` is absent for a task listed by the janitor's filter in some orders of events, and is
        polymorphic - its `type` discriminator selects the shape. Only our own type is read; anything
        else is left as an empty selection, and the worker refuses it.
        """
        task_id = raw.get('id')
        if not task_id:
            raise ValueError(f'Task without an id in the API answer: {raw}')
        # The status is required too, and it is read with `get` so that a missing one is the same
        # `ValueError` as an unknown one. `raw['status']` would raise `KeyError` instead, which every
        # caller here handles differently - the orchestrator would let it escape while holding a slot.
        status = raw.get('status')
        if not status:
            raise ValueError(f'Task {task_id} has no status in the API answer')
        params = raw.get('params') or {}
        if params.get('type') not in (None, TASK_TYPE):
            raise ValueError(f'Task {task_id} carries params of a foreign type {params.get("type")}')
        paths = params.get('paths') or []
        # The result is read only for the location it records, and only our own type carries one. A
        # foreign type is not an error here, unlike in the params: the janitor lists tasks and a type it
        # does not execute simply has nothing for it to clean up.
        result = raw.get('result') or {}
        if result.get('type') != TASK_TYPE:
            result = {}
        return cls(
            id=task_id,
            status=TaskStatus(status),
            owner=raw.get('owner'),
            storage_id=params.get('storageId'),
            paths=tuple(paths),
            # `or` returns its left side when that is truthy, the right side otherwise - so it reads as
            # "the archive name, or the default if it is missing or empty". The idiom replaces Java's
            # `StringUtils.defaultIfBlank`.
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
    Mirror of `AbstractDataStorage` - the part of it a reader of files needs.

    `path` means different things per type, which is why `root` and `prefix` below are only meaningful
    for object storages:

        S3   `bucket` or `bucket/some/prefix`
        NFS  `host:/export/folder`, or `host:/export` when the storage is the whole share
    """

    id: int
    name: str
    type: StorageType
    path: str
    file_share_mount_id: int | None = None
    mount_options: str | None = None
    mount_point: str | None = None
    region_id: int | None = None
    # `AbstractAWSDataStorage`'s own credentials fields. `useAssumedCredentials` is the switch: with it
    # set, the storage is reached through a role of its own - usually because it lives in another
    # account - and `tempCredentialsRole` is that role, falling back to the region's. Without it the
    # region decides alone. `aws.resolve_role_arn` is the whole rule.
    use_assumed_credentials: bool = False
    temp_credentials_role: str | None = None

    @property
    def root(self) -> str:
        """The bucket name of an object storage: everything before the first slash."""
        return self.path.split('/', 1)[0]

    @property
    def prefix(self) -> str:
        """
        The key prefix of an object storage, without the bucket and without surrounding slashes.

        `partition` splits once and always returns three parts, so no index can be out of range: for
        `bucket` it gives `('bucket', '', '')` and the prefix is empty, which is what we want.
        """
        _, _, rest = self.path.partition('/')
        return rest.strip('/')

    @classmethod
    def from_api(cls, raw: dict[str, Any]) -> Storage:
        return cls(
            id=raw['id'],
            name=raw.get('name') or '',
            type=StorageType(raw['type']),
            # `strip('/')` on both ends: the API is not consistent about a trailing slash on a
            # storage path, and one slipping into an S3 key prefix produces an empty folder level in
            # the listing - and therefore in the archive.
            path=(raw.get('path') or '').strip('/'),
            file_share_mount_id=raw.get('fileShareMountId'),
            mount_options=raw.get('mountOptions'),
            mount_point=raw.get('mountPoint'),
            region_id=raw.get('regionId'),
            # `or False`, because the API sends the field as `null` for a storage created before it
            # existed, and `None` is not a boolean.
            use_assumed_credentials=raw.get('useAssumedCredentials') or False,
            temp_credentials_role=raw.get('tempCredentialsRole'),
        )


@dataclass(frozen=True, slots=True)
class FileShare:
    """
    Mirror of `FileShareMount`: the export an NFS storage lives in.

    `mount_root` is the share as the server offers it, `10.0.0.1:/export` - several storages may share
    one, which is why it is a separate entity on the platform side and why this service mounts the root
    rather than a storage's own folder.
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


@dataclass(frozen=True, slots=True)
class CloudRegion:
    """
    Mirror of `AwsRegion` - the fields that decide how a client reaches the cloud.

    A platform region is not merely a place. It carries how the platform authenticates there, and the
    role this service assumes to read a storage is taken from here when the storage itself names none.
    `AWSRegionAttributes` in `workflows/pipe-common/pipeline/api/region.py` is the same mirror for the
    lifecycle service, and these field names follow it rather than inventing new ones.

    `code` is the cloud's own name for the region, `eu-central-1`. It arrives as `regionId` in the JSON
    while `id` is the platform's numeric id - `AbstractCloudRegion` carries a `@JsonProperty` to swap
    them, and they are named apart here so the two cannot be confused.

    Only the AWS fields are declared. A region of another provider parses into an instance with them all
    `None`, which is correct: there is nothing in it an S3 client could use.
    """

    id: int
    code: str
    profile: str | None = None
    iam_role: str | None = None
    temp_credentials_role: str | None = None
    # A deployment that runs its own S3-compatible storage rather than AWS. Set, it replaces the
    # endpoint, the addressing style and the certificate check - see `aws.build_s3_client`.
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
