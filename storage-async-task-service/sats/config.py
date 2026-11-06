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

`StorageDownloadArchiveConfig` is the platform's: the bucket, the thresholds, the time limits. Read
from the `storage.download.archive.config` preference on every round, so a change in the UI applies
without a restart. Its field names mirror `DownloadArchiveConfig` on the Java side.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

# Keep in step with `SystemPreferences.DATA_STORAGE_DOWNLOAD_ARCHIVE_CONFIG`.
PREFERENCE_KEY = 'storage.download.archive.config'

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


def normalise_prefix(configured: str | None) -> str:
    """
    The destination prefix with one trailing slash and no leading one.

    Mirrors `DataStorageDownloadArchiveManager.archivePrefix`, and the two must agree: the API derives
    the same key for a task whose executor never reported one. A lone slash means the bucket root.
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
class StorageDownloadArchiveConfig:
    """
    Mirror of `com.epam.pipeline.entity.datastorage.DownloadArchiveConfig`.

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
    def from_preference(cls, raw: str | dict[str, Any] | None) -> StorageDownloadArchiveConfig:
        """
        Parses the preference, which arrives as a JSON string in `payload.value`.

        Two ways of reading a field, and the difference is intentional:

          * `raw.get('maxFilesCount', DEFAULT)` - the default applies only when the key is absent, so
            a key present as `null` means unlimited;
          * `raw.get('ttlDays') or DEFAULT` - "no limit" is not a meaning these fields have, and a
            missing TTL must not mean "keep archives for ever".
        """
        if raw is None:
            raw = {}
        if isinstance(raw, str):
            # A blank preference value is not valid JSON. Treat it as unset.
            raw = json.loads(raw) if raw.strip() else {}
        return cls(
            destination_storage_id=raw.get('destinationStorageId'),
            destination_prefix=normalise_prefix(raw.get('destinationPrefix')),
            max_size_bytes=raw.get('maxSizeBytes', DEFAULT_MAX_SIZE_BYTES),
            max_files_count=raw.get('maxFilesCount', DEFAULT_MAX_FILES_COUNT),
            ttl_days=raw.get('ttlDays') or DEFAULT_TTL_DAYS,
            max_user_tasks=raw.get('maxUserTasks') or DEFAULT_MAX_USER_TASKS,
            worker_max_running_minutes=(raw.get('workerMaxRunningMinutes')
                                        or DEFAULT_WORKER_MAX_RUNNING_MINUTES),
            stuck_tasks_threshold_minutes=(raw.get('stuckTasksThresholdMinutes')
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
        found = []
        if not self.destination_storage_id:
            found.append(f'Preference {PREFERENCE_KEY} has no destinationStorageId, no task can be '
                         f'executed')
        if self.stuck_tasks_threshold_minutes <= self.worker_max_running_minutes:
            # Otherwise the cleaner fails tasks whose workers are alive and about to stop by
            # themselves, and that worker then deletes the archive it has just uploaded.
            found.append(
                f'Preference {PREFERENCE_KEY}: stuckTasksThresholdMinutes '
                f'({self.stuck_tasks_threshold_minutes}) should exceed workerMaxRunningMinutes '
                f'({self.worker_max_running_minutes}) by at least the time a worker needs to notice '
                f'its own deadline, clean up and report')
        if self.max_size_bytes is None:
            found.append(f'Preference {PREFERENCE_KEY} sets no maxSizeBytes, selection size is not '
                         f'limited')
        if self.max_files_count is None:
            found.append(f'Preference {PREFERENCE_KEY} sets no maxFilesCount, selection file count is '
                         f'not limited')
        return found
