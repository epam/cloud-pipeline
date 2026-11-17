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
The two kinds of settings this service has, kept apart on purpose.

`ServiceConfig` is ours: slots, scratch directory, poll intervals. Read once at startup from the
command line and the environment.

`AsyncTasksConfig` is the platform's: the buckets, the thresholds, the time limits. Read from the
`system.async.tasks.config` preference on every round, so a change in the UI applies without a
restart. Its field names mirror the Java classes the preference is read into.

That preference holds a section per task type, so the values this service needs come from two levels
of it: a task type's own from its section, and the two that belong to the service rather than to a
task type from the top. `AsyncTasksConfig` is the whole of it; the per-type sections are
`StorageDownloadArchiveTaskConfig` and `StorageUnpackArchiveTaskConfig`, and each is parsed and
validated on its own, so a task type whose section is missing is simply not claimed while the other
one keeps running.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .model import AsyncTaskType

# Keep in step with `SystemPreferences.SYSTEM_ASYNC_TASKS_CONFIG`.
PREFERENCE_KEY = 'system.async.tasks.config'

# The preference nests one section per task type under `tasks`. `maxUserTasks` and
# `stuckTasksThresholdMinutes` are deliberately NOT in there: they govern the service rather than one
# task type, so they sit at the top level where there can only be one of each.
TASKS_SECTION = 'tasks'
DOWNLOAD_SECTION = 'storageDownloadAsArchive'
UNPACK_SECTION = 'storageUnpackArchive'

# Used when the preference leaves a field out. They repeat the defaults in `SystemPreferences`, so a
# half-filled preference behaves like an untouched one. A field present as `null` is honoured as
# written - see `from_preference`.
DEFAULT_DESTINATION_PREFIX = 'archives/'
DEFAULT_MAX_SIZE_BYTES = 107374182400  # 100 GiB
DEFAULT_MAX_FILES_COUNT = 100000
DEFAULT_TTL_DAYS = 7
DEFAULT_MAX_USER_TASKS = 3
DEFAULT_WORKER_MAX_RUNNING_MINUTES = 240
DEFAULT_STUCK_TASKS_THRESHOLD_MINUTES = 360

# The unpack section's own. `maxSizeBytes` and `maxFilesCount` mean something different here than in
# the download section - the uncompressed size and the entry count of one archive, rather than the
# size and the file count of a selection - so they have defaults of their own.
DEFAULT_UNPACK_MAX_ARCHIVE_SIZE_BYTES = 53687091200  # 50 GiB
DEFAULT_UNPACK_MAX_SIZE_BYTES = 214748364800  # 200 GiB
DEFAULT_UNPACK_MAX_FILES_COUNT = 100000


def _as_object(raw: str | dict[str, Any] | None) -> dict[str, Any]:
    """
    The preference as a dictionary, whichever way it arrived.

    The API answers `payload.value` as a string, because the platform stores every preference as
    text; a caller that has already parsed it passes the dictionary. Anything that is valid JSON but
    not an object - a list, a number - is read as unset rather than reaching an attribute lookup.
    `json.loads` raising is left to the caller: a preference somebody saved as broken JSON is worth
    skipping a round over.
    """
    if raw is None:
        return {}
    if isinstance(raw, str):
        # A blank preference value is not valid JSON. Treat it as unset.
        raw = json.loads(raw) if raw.strip() else {}
    return raw if isinstance(raw, dict) else {}


def _section(parent: dict[str, Any], name: str) -> dict[str, Any]:
    value = parent.get(name)
    return value if isinstance(value, dict) else {}


def normalise_prefix(configured: str | None) -> str:
    """
    The destination prefix with one trailing slash and no leading one.

    Mirrors `DownloadArchiveTaskProcessor.archivePrefix`, and the two must agree: the API derives the
    same key for a task whose executor never reported one. A lone slash means the bucket root.
    """
    if not configured or not configured.strip():
        configured = DEFAULT_DESTINATION_PREFIX
    trimmed = configured.strip('/')
    return f'{trimmed}/' if trimmed else ''


@dataclass(frozen=True, slots=True)
class ServiceConfig:
    """Settings of this process. See `app.py` for the flag and variable behind each one."""

    # Base url of the platform API, including `/restapi`.
    api_url: str
    # A system administrator's JWT. Never logged.
    api_token: str
    # Scratch space, one subdirectory per task. Must hold the biggest allowed archive times `slots`,
    # plus the file being downloaded.
    work_dir: Path
    # How many tasks may run at once. One slot is one thread.
    slots: int = 2
    # How long the loop sleeps when it found nothing to claim.
    poll_interval_seconds: int = 20
    # How often the cleanups run. Separate from the loop, which has to stay fast because it also
    # claims tasks, while the cleanups act on thresholds measured in minutes and days. `0` turns them
    # off for a deployment that runs them elsewhere; the service warns about it at startup, because
    # without them a task whose worker died stays `RUNNING` for ever.
    cleanup_interval_seconds: int = 300
    # How often a running worker asks whether its task is still wanted. The lower bound on how fast a
    # cancellation is noticed, and the load this puts on the API.
    status_poll_seconds: int = 15
    # Part size of the archive upload, and the read block size elsewhere. S3 allows 10000 parts, so
    # 8 MiB caps one archive at 80 GiB before the part size is raised.
    part_size_bytes: int = 8 * 1024 * 1024
    # ZIP deflate level, 0 to 9. 1 on purpose: the input is usually already compressed research data.
    compress_level: int = 1
    # Where NFS shares get mounted. One subdirectory per share.
    nfs_mount_root: Path = Path('/mnt/sats-shares')
    # Hard limit on a `mount` call and on the liveness check of a mount point. A dead NFS server makes
    # both hang for ever by default, and a hung mount in a worker thread cannot be killed.
    nfs_mount_timeout_seconds: int = 30
    # Verify the API's TLS certificate. Off because a deployment normally signs its own.
    verify_ssl: bool = False
    # Run one round of the loop and exit.
    run_once: bool = False


@dataclass(frozen=True, slots=True)
class StorageDownloadArchiveTaskConfig:
    """
    Everything this service reads out of the preference, flattened into one object.

    Mirror of `com.epam.pipeline.entity.async.StorageDownloadArchiveTaskConfig`, plus the two fields
    the platform keeps one level above the section - `max_user_tasks` and
    `stuck_tasks_threshold_minutes`, which are `AsyncTasksConfig`'s on both sides because they govern
    the service rather than one task type. They are kept here so this section can warn about its own
    time limit; read them from `AsyncTasksConfig`.

    `None` in either threshold means "no limit", which is the platform's meaning: an administrator
    removing the value is removing the limit.
    """

    # Named after the Java fields rather than shortened. A shortened name broke this once: the
    # preference parsed, the field stayed `None`, and the service claimed nothing.
    destination_storage_id: int | None
    destination_prefix: str
    max_size_bytes: int | None
    max_files_count: int | None
    ttl_days: int
    max_user_tasks: int
    worker_max_running_minutes: int
    stuck_tasks_threshold_minutes: int

    @classmethod
    def from_preference(cls, raw: str | dict[str, Any] | None) -> StorageDownloadArchiveTaskConfig:
        """
        Parses the preference, which arrives as a JSON string in `payload.value`.

        Two ways of reading a field, and the difference is intentional:

          * `section.get('maxFilesCount', DEFAULT)` - the default applies only when the key is absent,
            so a key present as `null` means unlimited;
          * `section.get('ttlDays') or DEFAULT` - "no limit" is not a meaning these fields have, and a
            missing TTL must not mean "keep archives for ever".
        """
        shared = _as_object(raw)
        download = _section(_section(shared, TASKS_SECTION), DOWNLOAD_SECTION)
        return cls(
            destination_storage_id=download.get('destinationStorageId'),
            destination_prefix=normalise_prefix(download.get('destinationPrefix')),
            max_size_bytes=download.get('maxSizeBytes', DEFAULT_MAX_SIZE_BYTES),
            max_files_count=download.get('maxFilesCount', DEFAULT_MAX_FILES_COUNT),
            ttl_days=download.get('ttlDays') or DEFAULT_TTL_DAYS,
            worker_max_running_minutes=(download.get('workerMaxRunningMinutes')
                                        or DEFAULT_WORKER_MAX_RUNNING_MINUTES),
            max_user_tasks=shared.get('maxUserTasks') or DEFAULT_MAX_USER_TASKS,
            stuck_tasks_threshold_minutes=(shared.get('stuckTasksThresholdMinutes')
                                           or DEFAULT_STUCK_TASKS_THRESHOLD_MINUTES),
        )

    @property
    def is_usable(self) -> bool:
        """
        Whether a task can be executed at all.

        Without a destination storage there is nowhere to put an archive. The orchestrator skips the
        round instead of claiming, so tasks stay pending until an administrator fills it in.
        """
        return bool(self.destination_storage_id)

    def warnings(self) -> list[str]:
        """Settings that are valid but will misbehave. Returned so the caller decides how often to log."""
        section = f'{PREFERENCE_KEY}: {TASKS_SECTION}.{DOWNLOAD_SECTION}'
        found = []
        if not self.destination_storage_id:
            found.append(f'Preference {section}.destinationStorageId is not set, no task can be '
                         f'executed')
        if self.stuck_tasks_threshold_minutes <= self.worker_max_running_minutes:
            # Otherwise the cleaner fails tasks whose workers are alive and about to stop by
            # themselves, and that worker then deletes the archive it has just uploaded.
            found.append(
                f'Preference {PREFERENCE_KEY}: stuckTasksThresholdMinutes '
                f'({self.stuck_tasks_threshold_minutes}) should exceed '
                f'{TASKS_SECTION}.{DOWNLOAD_SECTION}.workerMaxRunningMinutes '
                f'({self.worker_max_running_minutes}) by at least the time a worker needs to notice '
                f'its own deadline, clean up and report. It is one value for the whole service, so it '
                f'must exceed the larger of the two sections')
        if self.max_size_bytes is None:
            found.append(f'Preference {section}.maxSizeBytes is not set, selection size is not '
                         f'limited')
        if self.max_files_count is None:
            found.append(f'Preference {section}.maxFilesCount is not set, selection file count is '
                         f'not limited')
        return found


@dataclass(frozen=True, slots=True)
class StorageUnpackArchiveTaskConfig:
    """
    The `storageUnpackArchive` section: the three limits of one extraction, and its time limit.

    Mirror of `com.epam.pipeline.entity.async.StorageUnpackArchiveTaskConfig`. It names no
    destination and no time to live, and that is the whole difference from the download section: an
    extraction writes into the storage the caller named, so the platform owns no artifact afterwards
    and there is nothing to expire.

    `None` in a limit means "no limit", which is the platform's meaning. `configured` is not a field
    of the preference - see `from_preference`.
    """

    # The archive object itself, checked against the storage listing before a byte of it is fetched.
    max_archive_size_bytes: int | None
    # The total *uncompressed* size that may be written. Checked against what the archive declares,
    # where the format reveals it, and again against the bytes actually written.
    max_size_bytes: int | None
    # The number of entries one archive may hold.
    max_files_count: int | None
    worker_max_running_minutes: int
    # Whether the section is in the preference at all. An absent section means the limits were never
    # reviewed by an administrator, so the service claims no unpack task rather than extracting
    # under invented ones - the same stance as a download task with no destination.
    configured: bool

    @classmethod
    def from_preference(cls, raw: str | dict[str, Any] | None) -> StorageUnpackArchiveTaskConfig:
        """Parses the `tasks.storageUnpackArchive` section out of the preference."""
        shared = _as_object(raw)
        tasks = _section(shared, TASKS_SECTION)
        unpack = _section(tasks, UNPACK_SECTION)
        return cls(
            # `get(key, DEFAULT)` rather than `get(key) or DEFAULT`: a key present as `null` means
            # unlimited, and only an absent key falls back.
            max_archive_size_bytes=unpack.get('maxArchiveSizeBytes',
                                              DEFAULT_UNPACK_MAX_ARCHIVE_SIZE_BYTES),
            max_size_bytes=unpack.get('maxSizeBytes', DEFAULT_UNPACK_MAX_SIZE_BYTES),
            max_files_count=unpack.get('maxFilesCount', DEFAULT_UNPACK_MAX_FILES_COUNT),
            worker_max_running_minutes=(unpack.get('workerMaxRunningMinutes')
                                        or DEFAULT_WORKER_MAX_RUNNING_MINUTES),
            configured=isinstance(tasks.get(UNPACK_SECTION), dict),
        )

    @property
    def is_usable(self) -> bool:
        """Whether an unpack task may be claimed at all."""
        return self.configured

    def warnings(self) -> list[str]:
        """Settings that are valid but will misbehave. The caller decides how often to log them."""
        section = f'{PREFERENCE_KEY}: {TASKS_SECTION}.{UNPACK_SECTION}'
        found = []
        if not self.configured:
            found.append(f'Preference {section} is not set, no archive can be extracted')
        if self.max_archive_size_bytes is None:
            found.append(f'Preference {section}.maxArchiveSizeBytes is not set, archive size is not '
                         f'limited')
        if self.max_size_bytes is None:
            # The one limit counted while writing, so without it a crafted archive is unbounded.
            found.append(f'Preference {section}.maxSizeBytes is not set, extracted size is not '
                         f'limited')
        if self.max_files_count is None:
            found.append(f'Preference {section}.maxFilesCount is not set, archive entry count is '
                         f'not limited')
        return found


@dataclass(frozen=True, slots=True)
class AsyncTasksConfig:
    """
    The whole preference: the two values shared by every task type, and a section per type.

    This is what the orchestrator reads and hands around. The shared values live here because there
    can only be one of each: `max_user_tasks` is one budget across packing and extracting, and
    `stuck_tasks_threshold_minutes` governs the cleaner rather than a task type, so it must exceed
    the *larger* of the sections' `worker_max_running_minutes`.

    `StorageDownloadArchiveTaskConfig` keeps its own copy of both, read from the same top level, so
    that section can warn about its own time limit. The two cannot disagree - they are parsed from
    one dictionary - and this class is the one to read them from.
    """

    download: StorageDownloadArchiveTaskConfig
    unpack: StorageUnpackArchiveTaskConfig
    max_user_tasks: int
    stuck_tasks_threshold_minutes: int

    @classmethod
    def from_preference(cls, raw: str | dict[str, Any] | None) -> AsyncTasksConfig:
        """
        Parses the preference once and hands the same object to every section.

        Parsed once because the value arrives as a JSON string and a section parsing it again would
        make two readings of one preference possible.
        """
        shared = _as_object(raw)
        return cls(
            download=StorageDownloadArchiveTaskConfig.from_preference(shared),
            unpack=StorageUnpackArchiveTaskConfig.from_preference(shared),
            max_user_tasks=shared.get('maxUserTasks') or DEFAULT_MAX_USER_TASKS,
            stuck_tasks_threshold_minutes=(shared.get('stuckTasksThresholdMinutes')
                                           or DEFAULT_STUCK_TASKS_THRESHOLD_MINUTES),
        )

    def section_for(self, task_type: AsyncTaskType):
        """
        The section a task of this type is executed under.

        A worker is given its own section and never the whole preference, so one task type cannot
        read another's limits by accident.
        """
        match task_type:
            case AsyncTaskType.DOWNLOAD_ARCHIVE:
                return self.download
            case AsyncTaskType.UNPACK_ARCHIVE:
                return self.unpack
            case _:
                raise ValueError(f'Preference {PREFERENCE_KEY} holds no section for task type '
                                 f'{task_type}')

    def is_usable(self, task_type: AsyncTaskType) -> bool:
        """
        Whether a task of this type can be executed with the preference as it is.

        Per type on purpose: a download section with no destination must not stop extractions, and
        an absent unpack section must not stop downloads. A type that is not usable is left
        unclaimed, so its tasks stay `PENDING` until the preference is filled in - filling it in
        then makes the backlog run, while failing the tasks would lose work over a fixable mistake.
        """
        match task_type:
            case AsyncTaskType.DOWNLOAD_ARCHIVE:
                return self.download.is_usable
            case AsyncTaskType.UNPACK_ARCHIVE:
                return self.unpack.is_usable
            case _:
                return False

    def warnings(self) -> list[str]:
        """Everything wrong with the preference, section by section, plus the shared rules."""
        found = self.download.warnings() + self.unpack.warnings()
        if self.stuck_tasks_threshold_minutes <= self.unpack.worker_max_running_minutes:
            # The download section raises the same objection about its own limit. Both are reported,
            # because each names the section whose value has to change.
            found.append(
                f'Preference {PREFERENCE_KEY}: stuckTasksThresholdMinutes '
                f'({self.stuck_tasks_threshold_minutes}) should exceed '
                f'{TASKS_SECTION}.{UNPACK_SECTION}.workerMaxRunningMinutes '
                f'({self.unpack.worker_max_running_minutes}) by at least the time a worker needs to '
                f'notice its own deadline, clean up and report. It is one value for the whole '
                f'service, so it must exceed the larger of the two sections')
        return found
