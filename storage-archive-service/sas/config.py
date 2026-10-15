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

`ServiceConfig` is ours: how many workers, where the scratch directory is, how often to poll. It comes
from the command line and the environment, it is read once at startup, and changing it means restarting
the container. Nobody but an operator of this service cares about any of it.

`StorageDownloadArchiveConfig` is the platform's: the bucket, the thresholds, the time limits. It
comes from the
`storage.download.archive.config` preference, it is read anew on every round of the loop, and an
administrator changing it in the UI takes effect without a restart. The field names mirror
`DownloadArchiveConfig` on the Java side one for one.

Keeping them in separate types makes the reload boundary visible: anything in
`StorageDownloadArchiveConfig` may
differ from one task to the next, anything in `ServiceConfig` cannot.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

# The preference that holds everything tunable about archive downloads. Keep in step with
# `SystemPreferences.DATA_STORAGE_DOWNLOAD_ARCHIVE_CONFIG`.
PREFERENCE_KEY = 'storage.download.archive.config'

# Fallbacks used when the preference exists but leaves a field out. They repeat the defaults declared
# in `SystemPreferences`, so that a half-filled preference behaves like an untouched one rather than
# like an unlimited one. A field present and set to `null`, on the other hand, is honoured as written -
# see `from_preference`.
DEFAULT_DESTINATION_PREFIX = 'archives/'
DEFAULT_MAX_SIZE_BYTES = 107374182400  # 100 GiB
DEFAULT_MAX_FILES_COUNT = 100000
DEFAULT_TTL_DAYS = 7
DEFAULT_MAX_USER_TASKS = 3
DEFAULT_WORKER_MAX_RUNNING_MINUTES = 240
DEFAULT_STUCK_TASKS_THRESHOLD_MINUTES = 360


def normalise_prefix(configured: str | None) -> str:
    """
    The destination prefix with exactly one trailing slash and no leading one.

    Mirrors `DataStorageDownloadArchiveManager.archivePrefix`, and the two have to agree: the API
    derives the same key when it signs a link for a task whose executor never reported one. Blank means
    `archives/`; a lone slash means the archives sit at the root of the bucket.
    """
    if not configured or not configured.strip():
        configured = DEFAULT_DESTINATION_PREFIX
    trimmed = configured.strip('/')
    return f'{trimmed}/' if trimmed else ''


@dataclass(frozen=True, slots=True)
class ServiceConfig:
    """Settings of this process. See `app.py` for the flag and environment variable behind each one."""

    # Base url of the platform API, including the `/restapi` part, e.g.
    # `https://cp.example.com/pipeline/restapi`.
    api_url: str
    # A system administrator's JWT. Never logged - see `api.py`, which keeps it out of its own
    # debug output.
    api_token: str
    # Scratch space. One subdirectory per task, deleted when the task ends. Must be large enough for
    # the biggest archive the thresholds allow, times the number of slots, plus the one file being
    # downloaded at that moment.
    work_dir: Path
    # How many tasks may run at once. One slot is one thread.
    slots: int = 2
    # How long the loop sleeps when it found nothing to claim.
    poll_interval_seconds: int = 20
    # How often a running worker asks the API whether its task is still wanted. The lower bound on how
    # fast a cancellation is noticed; also, with as many workers as slots, the load this puts on the
    # API.
    status_poll_seconds: int = 15
    # Part size for the multipart upload of the finished archive, and the read block size everywhere
    # else. 8 MiB is a compromise: S3 allows at most 10000 parts, so this caps one archive at 80 GiB,
    # and parts this size keep a single stream reasonably fast.
    part_size_bytes: int = 8 * 1024 * 1024
    # ZIP deflate level, 0 (store only) to 9. 1 is deliberate: the input is usually already compressed
    # research data, and level 6 would spend minutes of CPU per gigabyte to gain a few percent.
    compress_level: int = 1
    # Where NFS shares get mounted. One subdirectory per share.
    nfs_mount_root: Path = Path('/mnt/sas-shares')
    # Hard limit on a `mount` call and on the liveness check of an existing mount point. A dead NFS
    # server makes both hang forever by default, and a hung mount in a worker thread is unkillable.
    nfs_mount_timeout_seconds: int = 30
    # Verify the API's TLS certificate. Off by default because a platform deployment normally signs its
    # own, which is also what `pipe-common` does.
    verify_ssl: bool = False
    # Run one round of the loop and exit. For local runs and for a cron-style deployment.
    run_once: bool = False


@dataclass(frozen=True, slots=True)
class StorageDownloadArchiveConfig:
    """
    Mirror of `com.epam.pipeline.entity.datastorage.DownloadArchiveConfig`.

    `None` in either threshold means "no limit". That is the platform's meaning, not an accident of
    parsing: an administrator removing the value is removing the limit.
    """

    # Named after the Java fields, in snake case, rather than shortened. The mirror claim above is the
    # whole safety of this class, and a shortened name is how it was broken once already: the
    # preference parsed, the field stayed `None`, and the service quietly claimed nothing at all.
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
        Parses the preference.

        The preference arrives as a JSON *string* inside the API's answer - `payload.value` - because
        the platform stores every preference as text. So it is parsed twice: once by the HTTP client to
        get the envelope, once here to get the object. `isinstance` lets a caller pass either, which
        keeps tests from having to serialise a dictionary just to have it parsed back.

        Note the two ways a field is read, and that the difference is intentional:

          * `raw.get('maxFilesCount', DEFAULT)` - the default applies only when the key is *absent*. A
            key present as `null` yields `None`, which means unlimited;
          * `raw.get('ttlDays') or DEFAULT` - `None` falls back to the default, because "no limit" is
            not a meaning these fields have, and a missing TTL must not mean "keep archives forever".
        """
        if raw is None:
            raw = {}
        if isinstance(raw, str):
            # An empty or whitespace-only preference value is not valid JSON and would raise. Treat it
            # as an unset preference, which is what an administrator who cleared the field meant.
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

        Without a destination storage there is nowhere to put an archive, and a worker would do all the
        reading and packing only to fail at the last step. The orchestrator checks this before claiming
        anything and skips the round instead, leaving tasks pending until an administrator fills the
        preference in - which they can do while the service runs.
        """
        return bool(self.destination_storage_id)

    def warnings(self) -> list[str]:
        """
        Settings that are valid but will misbehave, as a list of sentences to log.

        Returned rather than logged so that the caller decides how often to repeat them; the
        orchestrator logs them once per change, not once per round.
        """
        found = []
        if not self.destination_storage_id:
            found.append(f'Preference {PREFERENCE_KEY} has no destinationStorageId, no task can be '
                         f'executed')
        if self.stuck_tasks_threshold_minutes <= self.worker_max_running_minutes:
            # The cleaner fails a task whose status has not changed within the stuck threshold. If that
            # threshold is not strictly larger than the worker's own limit, plus the worker's poll
            # interval and the time it needs to clean up and report, then the cleaner fails tasks whose
            # workers are alive and about to stop by themselves. That worker then deletes the archive
            # it has just uploaded, and the user sees a task that failed for no visible reason.
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
