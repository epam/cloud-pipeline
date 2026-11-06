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
One task, from the moment it was claimed to the status it ends in.

A worker runs in its own thread and owns one task: it walks the selection, checks the thresholds,
downloads the files one at a time, packs them into a ZIP, uploads it and reports the result. Then it
deletes everything it created locally, whichever way it ended.

Stopping is cooperative, because a Python thread cannot be stopped from outside: the worker calls
`TaskStopWatcher.check()` wherever abandoning the work is safe. Everything between two checks is
short, so the delay between a cancel and the task stopping does not depend on the size of the data.

`run` decides what each kind of failure means for the status:

    cancel noticed on a poll   STOPPING, clean up, CANCELLED
    own deadline passed        STOPPING, clean up, FAILED
    already finished elsewhere clean up only, report nothing
    threshold exceeded         FAILED, with the threshold named
    storage type unsupported   FAILED, with the type named
    credentials refused        FAILED, with the storage and the refusal named
    anything else              FAILED, as an internal error

The archive is deleted on every path except success, including when the API refuses the success
report: an archive no task points at is unreachable for the user.
"""

from __future__ import annotations

import itertools
import shutil
import threading
import time
import zipfile
from pathlib import Path

from ..api import CloudPipelineApi
from ..datastorage_providers.s3 import ArchiveBucket, absolute_key, archive_key
from ..cloud.aws import build_s3_client
from ..config import ServiceConfig, StorageDownloadArchiveConfig
from ..util.errors import ApiError, ApiRejected, CredentialsUnavailable, InvalidTask, StopReason, \
    StopRequested, ThresholdExceeded, UnsupportedSource
from ..util.log import get_logger
from ..model import ArchiveTask, AsyncTaskType, StorageType, TaskStatus
from ..datastorage_providers import FileSource, SourceFile, open_source
from .base import AsyncTaskWorker
from .task_stop_watcher import TaskStopWatcher

LOG = get_logger(__name__)

# The local name of the archive while it is built. What the browser offers is decided by the API.
LOCAL_ARCHIVE_NAME = 'archive.zip'
# Where every download lands before it goes into the ZIP. One file per worker is ever in flight, and
# a fixed name means a crash cannot leave a pile of them.
DOWNLOAD_TEMP_NAME = 'current.part'
# The note added to an archive that is missing something. A selection may hold a file of this name,
# so it is only the first choice - see `_note_name`.
SKIPPED_NOTE_NAME = 'README.txt'
SKIPPED_NOTE_FALLBACK = 'CLOUD-PIPELINE-SKIPPED-FILES.txt'

# Error messages go into a database column and into the GUI, so the text is cut. The full one is in
# this service's log.
MAX_ERROR_MESSAGE = 1000


class ArchivePreparationWorker(AsyncTaskWorker):
    """
    Builds the archive for one task.

    Everything it touches is derived from the task: the scratch directory and the object key are both
    named after the task id. Only the API client and the mounted shares are shared with others.
    """

    TASK_TYPE = AsyncTaskType.DOWNLOAD_ARCHIVE

    def __init__(self, api: CloudPipelineApi, service_config: ServiceConfig,
                 archive_config: StorageDownloadArchiveConfig, task: ArchiveTask,
                 bucket: ArchiveBucket | None = None):
        self._api = api
        self._config = service_config
        self._archive_config = archive_config
        self._task = task
        # Resolved in `_resolve_destination`, which needs an API call. A test injects it whole.
        self._bucket = bucket
        # Two keys: the relative one is what gets reported, because that is the vocabulary the API
        # signs in; the absolute one carries the storage's own folder and is what boto3 is given.
        self._relative_key = archive_key(archive_config.destination_prefix, task.id)
        self._key = self._relative_key
        self._task_dir = service_config.work_dir / task.id
        self._watcher = TaskStopWatcher(api, task.id,
                                        archive_config.worker_max_running_minutes * 60,
                                        service_config.status_poll_seconds)
        # Set once the archive is in the bucket, so a delete is only issued for an object that exists.
        self._uploaded = False

    def run(self) -> None:
        """
        Runs the task to one of its end states. Never raises.

        Every path ends in a status report or an explained decision not to report one. The `finally`
        removes the scratch directory, which has to happen on all six paths.
        """
        threading.current_thread().name = f'task-{self._task.id[:8]}'
        started = time.monotonic()
        LOG.info('Took task %s: storage %s, %s path(s), archive %s', self._task.id,
                 self._task.storage_id, len(self._task.paths), self._task.archive_name)
        try:
            self._execute()
        except StopRequested as stop:
            self._finish_stopped(stop.reason)
        except ThresholdExceeded as exceeded:
            # The user asked for too much, so a warning rather than an error, and the message goes to
            # them unchanged.
            LOG.warning('Task %s rejected: %s', self._task.id, exceeded)
            self._finish_failed(str(exceeded))
        except (UnsupportedSource, InvalidTask, CredentialsUnavailable) as unsupported:
            LOG.warning('Task %s cannot be executed: %s', self._task.id, unsupported)
            self._finish_failed(str(unsupported))
        except ApiRejected as rejected:
            # The task is no longer ours, so reporting a failure would be refused too.
            LOG.warning('Task %s was refused by the API (%s), dropping the work', self._task.id,
                        rejected)
            self._discard_archive()
        except Exception as error:
            # `LOG.exception` adds the traceback; the user gets the short form.
            LOG.exception('Task %s failed unexpectedly', self._task.id)
            self._finish_failed(f'Internal error of the archive service: {error}')
        finally:
            self._remove_task_dir()
            LOG.info('Task %s released after %.1f s', self._task.id, time.monotonic() - started)

    # ------------------------------------------------------------------ the happy path

    def _execute(self) -> None:
        """The task as it goes when nothing is wrong. Every failure is handled by the caller."""
        if not self._task.storage_id or not self._task.paths:
            raise InvalidTask(f'Task {self._task.id} names no storage or no paths, so there is '
                              f'nothing to archive')
        self._task_dir.mkdir(parents=True, exist_ok=True)
        self._resolve_destination()
        with self._open_source() as source:
            files, unreadable = self._scan(source)
            archive_path, failed = self._pack(source, files, unreadable)
            size = self._upload(archive_path)
        self._finish_succeeded(size, len(unreadable) + len(failed))

    def _resolve_destination(self) -> None:
        """
        Works out which bucket the archive goes to and under which key.

        Done before a byte is downloaded. A refused destination is a configuration problem rather
        than this task's fault, so it is reported as `InvalidTask`.
        """
        if self._bucket is not None:
            return
        try:
            storage = self._api.load_storage(self._archive_config.destination_storage_id)
        except ApiRejected as rejected:
            raise InvalidTask(f'The archive storage this service is configured with is not available: '
                              f'{rejected}') from rejected
        if storage.type is not StorageType.S3:
            # Without this the failure arrives from the client builder as "belongs to no cloud
            # region", which names the wrong problem.
            raise InvalidTask(f'The archive storage this service is configured with, {storage.name}, '
                              f'is of type {storage.type}, which this service cannot write an archive '
                              f'to')
        # The destination's own region and role, which may be neither the source's region nor its
        # role. A region that cannot be read ends the task as `CredentialsUnavailable`.
        self._bucket = ArchiveBucket(storage.root, self._config.part_size_bytes,
                                     build_s3_client(self._api, storage))
        self._key = absolute_key(storage.prefix, self._relative_key)

    def _open_source(self) -> FileSource:
        """
        Loads the storage behind the task and opens a source over it.

        A refusal here means the storage or its file share was deleted after the task was created,
        and becomes `InvalidTask` so the task fails with the reason named. Left to `run`'s
        `ApiRejected` arm it would report nothing and the task would hang until the cleaner.
        """
        try:
            storage = self._api.load_storage(self._task.storage_id)
            return open_source(self._api, storage, self._config)
        except ApiRejected as rejected:
            raise InvalidTask(f'The storage this archive was requested from is not available any '
                              f'more: {rejected}') from rejected

    def _scan(self, source: FileSource) -> tuple[list[SourceFile], list[SourceFile]]:
        """
        Walks the selection once, measuring it, and splits it into what can be read and what cannot.

        One pass for both thresholds, stopping at the first one breached. Measuring means this walk,
        which is why the thresholds are the executor's to enforce and not the API's.

        Only readable files are counted: a file in Glacier is never packed.

        The stop check is per file, not per batch. `check` is a comparison of two numbers unless the
        poll interval has elapsed, and a selection of four hundred Glacier objects needs a HEAD each.
        """
        files: list[SourceFile] = []
        unreadable: list[SourceFile] = []
        total = 0
        for found in source.list_files(self._task.paths):
            self._watcher.check()
            if found.skip_reason:
                unreadable.append(found)
                continue
            total += found.size
            files.append(found)
            self._assert_within_thresholds(len(files), total)
        self._assert_fits_locally(files, total)
        LOG.info('Task %s selection: %s file(s), %s bytes, %s unreadable', self._task.id,
                 len(files), total, len(unreadable))
        return files, unreadable

    def _assert_within_thresholds(self, count: int, total: int) -> None:
        """
        Checks the selection against the preference, naming whichever threshold was breached.

        `None` means the administrator removed that limit; `is not None` rather than a truth test, so
        a configured zero means zero.

        Both limits exist because each misses what the other catches: two hundred thousand small
        files are nothing by size and still two hundred thousand downloads, while one huge file is a
        single download no file count would stop.
        """
        limit_files = self._archive_config.max_files_count
        if limit_files is not None and count > limit_files:
            raise ThresholdExceeded(
                f'The selection holds more than {limit_files} files, which is the maximum one archive '
                f'may contain. Ask for fewer folders at a time.')
        limit_size = self._archive_config.max_size_bytes
        if limit_size is not None and total > limit_size:
            raise ThresholdExceeded(
                f'The selection is larger than {_human(limit_size)}, which is the maximum one archive '
                f'may contain. Ask for fewer or smaller files at a time.')

    def _assert_fits_locally(self, files: list[SourceFile], total: int) -> None:
        """
        Checks that the scratch directory can hold the archive before a byte is downloaded.

        The estimate is the selection plus its largest single file, which sits next to the archive on
        its way in.
        """
        needed = total + max((found.size for found in files), default=0)
        free = shutil.disk_usage(self._config.work_dir).free
        if free < needed:
            raise ThresholdExceeded(
                f'The archive service has {_human(free)} of local space free and needs about '
                f'{_human(needed)} for this selection. Ask for fewer files at a time, or ask an '
                f'administrator to give the service more space.')

    def _pack(self, source: FileSource, files: list[SourceFile],
              unreadable: list[SourceFile]) -> tuple[Path, list[tuple[str, str]]]:
        """
        Downloads the files one at a time and writes each into the ZIP.

        Through a local file rather than straight into the ZIP stream, so a download that fails
        halfway does not leave half a file in the archive. A file that fails is noted and skipped:
        a user whose nine hundred and ninety-ninth file has a permission problem wants the rest.

        `zipfile` writes ZIP64 by itself when it needs to, so a file above 4 GiB is not a special case.
        """
        archive_path = self._task_dir / LOCAL_ARCHIVE_NAME
        temp_path = self._task_dir / DOWNLOAD_TEMP_NAME
        failed: list[tuple[str, str]] = []
        with zipfile.ZipFile(archive_path, 'w', zipfile.ZIP_DEFLATED,
                             compresslevel=self._config.compress_level) as archive:
            for found in files:
                self._watcher.check()
                try:
                    source.download(found, temp_path, self._watcher.check)
                except StopRequested:
                    # Not a failure of this file - the whole task is being abandoned.
                    raise
                except Exception as error:
                    LOG.warning('Task %s: cannot read %s (%s), skipping it', self._task.id,
                                found.path, error)
                    failed.append((found.path, str(error)))
                    temp_path.unlink(missing_ok=True)
                    continue
                # Without `arcname` every entry would be called `current.part`.
                archive.write(temp_path, arcname=found.path)
                temp_path.unlink(missing_ok=True)
            if unreadable or failed:
                # The name is chosen against what is already in the archive: `zipfile` would happily
                # write a second entry under a name that is taken, and most tools keep the last one.
                archive.writestr(_note_name(archive.namelist()),
                                 _skipped_note(unreadable, failed))
        LOG.info('Task %s packed %s file(s), %s skipped', self._task.id,
                 len(files) - len(failed), len(unreadable) + len(failed))
        return archive_path, failed

    def _upload(self, archive_path: Path) -> int:
        """
        Puts the archive in the bucket and returns its size.

        `_uploaded` is set only after the upload returned: an interrupted upload leaves no object, so
        a delete issued for it would be pointless.
        """
        self._watcher.check()
        size = self._bucket.upload(archive_path, self._key, self._watcher.check)
        self._uploaded = True
        return size

    # ------------------------------------------------------------------ end states

    def _finish_succeeded(self, size: int, skipped: int) -> None:
        """
        Reports success, and deletes the archive if the report is refused.

        A refusal means the task is not RUNNING any more, so nothing will ever point at the archive -
        and the cleaner only expires archives of tasks that did succeed.
        """
        result = {
            # Jackson selects `DownloadArchiveResult` by this, and refuses the report without it.
            'type': AsyncTaskType.DOWNLOAD_ARCHIVE,
            # Where the archive went, reported rather than derived later: the destination is a
            # preference an administrator may change, and after a change a derived location points at
            # a key that was never written. Only the settings half is stored - the API derives the
            # `<taskId>.zip` tail.
            'storageId': self._archive_config.destination_storage_id,
            'path': self._relative_key,
            'archiveSizeBytes': size,
            'skippedFiles': skipped,
        }
        try:
            self._api.report_status(self._task.id, TaskStatus.SUCCESS, result=result)
            LOG.info('Task %s succeeded: %s bytes, %s skipped', self._task.id, size, skipped)
        except ApiRejected as rejected:
            LOG.warning('Task %s would not accept a success report (%s), deleting its archive',
                        self._task.id, rejected)
            self._discard_archive()
        except ApiError as error:
            # Not delivered at all. The archive stays: the cleaner will fail the task as stuck and
            # delete the object, and deleting it here would leave SUCCESS with nothing behind it if
            # the report did arrive.
            LOG.error('Task %s succeeded but its report could not be delivered (%s); the cleaner '
                      'will clean it up', self._task.id, error)

    def _finish_stopped(self, reason: StopReason) -> None:
        """
        Ends a task that was going fine until it was asked to stop, or ran out of time.

        `ABANDONED` reports nothing: the task is already terminal, so every transition is refused.

        The other two go through `STOPPING` first. Not required, and worth the extra call: a user
        watching the task sees why it is not finished, and the cleaner's stuck window starts again
        rather than running out mid-cleanup.
        """
        if reason == StopReason.ABANDONED:
            LOG.warning('Task %s is already finished on the API side, dropping its archive',
                        self._task.id)
            self._discard_archive()
            return
        LOG.info('Task %s is stopping (%s)', self._task.id, reason)
        self._try_report(TaskStatus.STOPPING)
        self._discard_archive()
        if reason == StopReason.CANCELLED:
            self._try_report(TaskStatus.CANCELLED)
        else:
            self._try_report(
                TaskStatus.FAILED,
                error_message=f'The archive was not finished within '
                              f'{self._archive_config.worker_max_running_minutes} minutes and was stopped. '
                              f'Ask for fewer files at a time.')

    def _finish_failed(self, message: str) -> None:
        """Ends a task that cannot succeed: no archive left behind, and the reason recorded."""
        self._discard_archive()
        self._try_report(TaskStatus.FAILED, error_message=message[:MAX_ERROR_MESSAGE])

    def _try_report(self, status: TaskStatus, error_message: str | None = None) -> None:
        """
        Reports a status and swallows whatever comes back.

        Called only from paths that are already ending the task. A task left in a non-terminal status
        by a failure here is the cleaner's to finish.
        """
        try:
            self._api.report_status(self._task.id, status, error_message=error_message)
        except (ApiError, ApiRejected) as error:
            LOG.warning('Task %s: cannot report %s (%s)', self._task.id, status, error)

    # ------------------------------------------------------------------ cleanup

    def _discard_archive(self) -> None:
        """Removes the uploaded archive, if there is one. Safe to call more than once."""
        if not self._uploaded:
            return
        try:
            self._bucket.delete(self._key)
            self._uploaded = False
        except Exception as error:
            # Not raised: the task is ending either way, and an operator needs to know about an
            # object that is now unreferenced.
            LOG.error('Task %s: cannot delete its archive %s (%s). It stays in the bucket',
                      self._task.id, self._key, error)

    def _remove_task_dir(self) -> None:
        """
        Removes the scratch directory with everything in it.

        `ignore_errors=True` because this is the last thing a worker does. Directories left by
        workers that never got here are the cleaner's.
        """
        if not self._task_dir.exists():
            return
        shutil.rmtree(self._task_dir, ignore_errors=True)
        if self._task_dir.exists():
            LOG.warning('Task %s: scratch directory %s could not be removed', self._task.id,
                        self._task_dir)


def _note_name(taken: list[str]) -> str:
    """
    Picks a name for the note that no file in the archive already uses.

    `README.txt` first, because that is where a person looks; then the fallback; then a number.
    """
    for candidate in (SKIPPED_NOTE_NAME, SKIPPED_NOTE_FALLBACK):
        if candidate not in taken:
            return candidate
    stem = SKIPPED_NOTE_FALLBACK.removesuffix('.txt')
    for suffix in itertools.count(2):
        candidate = f'{stem}-{suffix}.txt'
        if candidate not in taken:
            return candidate


def _skipped_note(unreadable: list[SourceFile], failed: list[tuple[str, str]]) -> str:
    """
    The text of the note added to an archive that is missing something.

    Inside the archive and not only in the task, because the archive outlives the task and the
    question is asked while looking at the unpacked folder.
    """
    lines = ['Some of the files you asked for are not in this archive.', '']
    for found in unreadable:
        lines.append(f'{found.path}: {found.skip_reason}')
    for path, reason in failed:
        lines.append(f'{path}: {reason}')
    lines.append('')
    lines.append('Everything else you asked for is here.')
    return '\n'.join(lines)


def _human(size: int) -> str:
    """Formats a byte count for a user: `1.5 GiB` rather than `1610612736`."""
    value = float(size)
    for unit in ('B', 'KiB', 'MiB', 'GiB'):
        if abs(value) < 1024:
            return f'{value:.1f} {unit}' if unit != 'B' else f'{int(value)} {unit}'
        value /= 1024
    return f'{value:.1f} TiB'
