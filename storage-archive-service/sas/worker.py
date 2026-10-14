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

A worker runs in its own thread and owns one task. It walks the selection, checks it against the
thresholds, downloads the files one at a time, packs them into a ZIP, uploads the ZIP and reports the
result. Then it deletes everything it created locally, whichever way it ended.

The shape of this module is set by one constraint: **a Python thread cannot be stopped from outside.**
There is no `Thread.stop()`, no interrupt, nothing an orchestrator can do to a worker that must give up
a task. So a worker stops itself: it calls `StopWatcher.check()` at every point where abandoning the
work is safe, and that method raises when it is time to go. Everything between two checks is short - a
block of a file, a part of an upload - so the delay between a user pressing cancel and the task
actually stopping is bounded by the poll interval, not by the size of the data.

`run` is the single place that decides what each kind of failure means for the task's status:

    cancel noticed on a poll   STOPPING, clean up, CANCELLED
    own deadline passed        STOPPING, clean up, FAILED
    already finished elsewhere clean up only, report nothing
    threshold exceeded         FAILED, with the threshold named
    storage type unsupported   FAILED, with the type named
    credentials refused        FAILED, with the storage and the refusal named
    anything else              FAILED, as an internal error

The archive is deleted on every path except success, including when the API refuses the success report -
an archive no task points at is unreachable for the user and would sit in the bucket until someone
noticed it.
"""

from __future__ import annotations

import itertools
import shutil
import threading
import time
import zipfile
from pathlib import Path

from .api import CloudPipelineApi
from .archive_bucket import ArchiveBucket, absolute_key, archive_key
from .aws import build_s3_client
from .config import PlatformConfig, ServiceConfig
from .errors import ApiError, ApiRejected, CredentialsUnavailable, InvalidTask, StopReason, \
    StopRequested, ThresholdExceeded, UnsupportedSource
from .log import get_logger
from .model import ArchiveTask, StorageType, TASK_TYPE, TaskStatus
from .sources import FileSource, SourceFile, open_source

# A backslash continues a statement onto the next line. Parentheses are the usual way to wrap an
# expression, but an `import ... from` list cannot take them around the whole clause, so this is the
# form the style guide allows here.

LOG = get_logger(__name__)

# The local name of the archive while it is being built. The name the user's browser offers is decided
# by the API when it signs the download link, so this one never reaches anybody.
LOCAL_ARCHIVE_NAME = 'archive.zip'
# The file every download lands in before it goes into the ZIP. One name, reused: only one file is ever
# in flight per worker, and a fixed name means a crash cannot leave a growing pile of them.
DOWNLOAD_TEMP_NAME = 'current.part'
# The note added to an archive that is missing something, so the user does not have to compare what
# they asked for against what they got. A selection may hold a file of the same name, so the name is
# only the first choice - see `_note_name`.
SKIPPED_NOTE_NAME = 'README.txt'
# Used instead when the selection itself contains the name above. Deliberately not a name anybody would
# have in a data storage.
SKIPPED_NOTE_FALLBACK = 'CLOUD-PIPELINE-SKIPPED-FILES.txt'

# Error messages go into a database column and into the GUI. A traceback-sized message helps nobody
# there, so the text is cut - the full one is in this service's log.
MAX_ERROR_MESSAGE = 1000


class StopWatcher:
    """
    Answers one question - may this worker carry on? - and raises when the answer is no.

    Two independent reasons to stop, with deliberately different costs:

      * **the deadline** is local arithmetic, so it is checked on every call;
      * **a cancellation** has to be read from the API, so it is checked at most once per
        `status_poll_seconds`. With one poll per worker per interval, the load this puts on the API is
        the number of slots divided by the interval, and that is the whole reason the throttle exists.

    Time is measured with `time.monotonic()`, never with the clock. A monotonic clock only ever moves
    forward at a steady rate; the wall clock can jump - NTP correcting a drift, a container resuming,
    a daylight-saving change - and a deadline measured against it can jump an hour into the past or
    the future.
    """

    def __init__(self, api: CloudPipelineApi, task_id: str, deadline_seconds: float,
                 poll_seconds: float):
        self._api = api
        self._task_id = task_id
        self._deadline = time.monotonic() + deadline_seconds
        self._poll_seconds = poll_seconds
        # The first poll is due one interval from now: the task was handed to us as RUNNING a moment
        # ago, so asking again immediately would learn nothing.
        self._next_poll = time.monotonic() + poll_seconds

    def check(self) -> None:
        """
        Raises `StopRequested` if the task should be abandoned. Returns nothing otherwise.

        A method called for its exceptions rather than its value reads oddly at first, and it is the
        right shape here: the alternative is a boolean every caller has to remember to act on, in a
        loop that copies a file, where forgetting is both easy and invisible.
        """
        now = time.monotonic()
        if now >= self._deadline:
            raise StopRequested(StopReason.TIMEOUT)
        if now < self._next_poll:
            return
        self._next_poll = now + self._poll_seconds
        try:
            status = self._api.load_task(self._task_id).status
        except (ApiError, ApiRejected) as error:
            # The API being briefly unreachable is not a reason to throw away work in progress. The
            # next check tries again; if the API stays down, the deadline eventually stops us anyway.
            LOG.warning('Cannot read the status of task %s (%s), carrying on', self._task_id, error)
            return
        if status == TaskStatus.CANCEL_REQUESTED:
            raise StopRequested(StopReason.CANCELLED)
        if status.is_terminal:
            # Somebody else ended this task - the janitor failing it as stuck, in practice. Nothing we
            # report will be accepted, and the archive we are building belongs to nobody.
            raise StopRequested(StopReason.ABANDONED)


class ArchiveWorker:
    """
    Builds the archive for one task.

    Everything it touches is derived from the task: the scratch directory is named after the task id,
    and so is the object key. Nothing is shared with another worker except the API client and the
    mounted shares.
    """

    def __init__(self, api: CloudPipelineApi, service_config: ServiceConfig,
                 platform_config: PlatformConfig, task: ArchiveTask,
                 bucket: ArchiveBucket | None = None):
        self._api = api
        self._config = service_config
        self._platform = platform_config
        self._task = task
        # Resolved in `_resolve_destination`, because the bucket behind the destination storage is
        # behind an API call. Injected whole by a test, which then needs no storage at all.
        self._bucket = bucket
        # Two keys, and the difference matters. The relative one is storage-relative and is what gets
        # reported: that is the vocabulary the API signs in. The absolute one carries the storage's own
        # folder in front and is what boto3 is given. They are equal for a storage that is a whole
        # bucket, which is why a test with an injected bucket needs no storage.
        self._relative_key = archive_key(platform_config.destination_prefix, task.id)
        self._key = self._relative_key
        self._task_dir = service_config.work_dir / task.id
        self._watcher = StopWatcher(api, task.id,
                                    platform_config.worker_max_running_minutes * 60,
                                    service_config.status_poll_seconds)
        # Set once the archive is in the bucket. Everything that cleans up consults it, so that a
        # delete is only issued for an object that can exist.
        self._uploaded = False

    def run(self) -> None:
        """
        Runs the task to one of its end states. Never raises.

        It must never raise: it is the body of a thread-pool task, and an exception escaping here would
        be stored in a future nobody reads - the task would stay RUNNING until the janitor noticed, and
        the log would say nothing. So every path ends in a status report or an explained decision not
        to report one.

        `finally` runs whatever happened, including on a `return` from inside the `try`. It is where the
        scratch directory goes, because that has to happen on every path and there are six of them.
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
            # Expected, and the user's to act on - they asked for too much. Logged as a warning, not an
            # error, and the message goes to them unchanged.
            LOG.warning('Task %s rejected: %s', self._task.id, exceeded)
            self._finish_failed(str(exceeded))
        except (UnsupportedSource, InvalidTask, CredentialsUnavailable) as unsupported:
            LOG.warning('Task %s cannot be executed: %s', self._task.id, unsupported)
            self._finish_failed(str(unsupported))
        except ApiRejected as rejected:
            # The API refused something mid-flight, which in practice means the task is no longer ours.
            # Reporting a failure would be refused for the same reason, so clean up and say why.
            LOG.warning('Task %s was refused by the API (%s), dropping the work', self._task.id,
                        rejected)
            self._discard_archive()
        except Exception as error:
            # The catch-all. `LOG.exception` writes the message together with the traceback, which is
            # the one thing that makes an unexpected failure diagnosable; the user gets the short form.
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

        Done before a byte is downloaded: a destination that cannot be read is cheap to fail on now and
        expensive to fail on after the packing. A refusal means the archive storage was deleted or is
        not visible to this service, which is a configuration problem and not this task's fault - so it
        is reported as one, with `InvalidTask`, rather than as an internal error.
        """
        if self._bucket is not None:
            return
        try:
            storage = self._api.load_storage(self._platform.destination_storage_id)
        except ApiRejected as rejected:
            raise InvalidTask(f'The archive storage this service is configured with is not available: '
                              f'{rejected}') from rejected
        if storage.type is not StorageType.S3:
            # Checked here because the failure otherwise arrives from the client builder as "belongs to
            # no cloud region", which names the wrong problem. `open_source` makes the same check for
            # the storage being read; this is the destination's half of it.
            raise InvalidTask(f'The archive storage this service is configured with, {storage.name}, '
                              f'is of type {storage.type}, which this service cannot write an archive '
                              f'to')
        # A client for the destination storage, in the destination's own region and through the
        # destination's own role - which may be neither the source's region nor its role. A region that
        # cannot be read arrives as `CredentialsUnavailable` and ends the task with the reason named,
        # the same treatment the source storage gets in `_open_source`.
        self._bucket = ArchiveBucket(storage.root, self._config.part_size_bytes,
                                     build_s3_client(self._api, storage))
        self._key = absolute_key(storage.prefix, self._relative_key)

    def _open_source(self) -> FileSource:
        """
        Loads the storage behind the task and opens a source over it.

        The two calls are here together for one reason: both go to the API, and both can be *refused*
        rather than merely fail - the storage was deleted after the task was created, or the file share
        it lives on was. A refusal is turned into `InvalidTask`, so the task ends as `FAILED` with a
        message naming what is missing.

        Without this, `run`'s `except ApiRejected` arm would take it. That arm means "the API will not
        let us report anything about this task any more", and it reports nothing at all - so the task
        would stay `RUNNING` until the janitor failed it hours later with a message about a worker that
        vanished, which is both wrong and useless to the user.
        """
        try:
            storage = self._api.load_storage(self._task.storage_id)
            return open_source(self._api, storage, self._config)
        except ApiRejected as rejected:
            # `raise ... from rejected` keeps the original exception as the cause, so the log shows both
            # the refusal and the decision made about it.
            raise InvalidTask(f'The storage this archive was requested from is not available any '
                              f'more: {rejected}') from rejected

    def _scan(self, source: FileSource) -> tuple[list[SourceFile], list[SourceFile]]:
        """
        Walks the selection once, measuring it, and splits it into what can be read and what cannot.

        One pass for both thresholds, and the pass stops at the first one breached. That is why the
        thresholds are the executor's to enforce and not the API's: the API accepts the request without
        measuring it, because measuring means this walk, which takes minutes on a large selection and
        would hold an HTTP request open for all of it.

        Only readable files are counted. A file in Glacier is neither downloaded nor packed, so
        counting it against a limit on what gets packed would reject a selection for work nobody does.

        The stop check is per file, not per batch of files. It looks wasteful and is not: `check` is a
        comparison of two numbers unless `status_poll_seconds` has elapsed, and the cost of the
        alternative is a cancellation nobody notices. A selection of four hundred Glacier objects needs
        a `HEAD` each, takes minutes, and would otherwise be checked exactly once - so the promise that
        a cancellation is noticed within `status_poll_seconds` would hold only for large selections.

        `tuple[list[...], list[...]]` as a return type is a pair. Returning several values is ordinary
        in Python - `return a, b` builds a tuple and `x, y = f()` takes it apart - and it is preferred
        over a holder class for a result that is consumed immediately, by one caller.
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

        The message is written for the user who asked for the archive: it says what they asked for,
        what the limit is, and therefore what to do about it. `None` means the administrator removed
        that limit, and `is not None` rather than a truth test is what makes a configured zero mean
        zero.

        Both limits exist because each misses what the other catches. A selection of two hundred
        thousand one-kilobyte files is two hundred megabytes - nothing by size, nowhere near the
        default hundred gigabytes - but it is two hundred thousand separate downloads, which at a few
        files per second runs past any sane time limit. Conversely one ten-terabyte file is a single
        download that no file count would stop.
        """
        limit_files = self._platform.max_files_count
        if limit_files is not None and count > limit_files:
            raise ThresholdExceeded(
                f'The selection holds more than {limit_files} files, which is the maximum one archive '
                f'may contain. Ask for fewer folders at a time.')
        limit_size = self._platform.max_size_bytes
        if limit_size is not None and total > limit_size:
            raise ThresholdExceeded(
                f'The selection is larger than {_human(limit_size)}, which is the maximum one archive '
                f'may contain. Ask for fewer or smaller files at a time.')

    def _assert_fits_locally(self, files: list[SourceFile], total: int) -> None:
        """
        Checks that the scratch directory can hold the archive before a byte is downloaded.

        Without this the task fails somewhere in the middle of packing, with whatever message the
        operating system gives for a full disk, after having spent however long it took to get there.

        The estimate is the sum of the selection plus its largest single file: the archive may compress
        well or not at all, and one file sits next to it, downloaded, on its way in. `default=0` keeps
        `max` from raising on an empty selection, which is a thing a user can ask for.
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

        One at a time, and through a local file rather than straight into the ZIP stream, because a
        download that fails halfway must not leave half a file in the archive. The cost is a write and
        a read of every byte on local disk; the gain is that one unreadable file costs that file and
        not the whole task.

        A file that fails to download is noted and skipped, exactly like one that was archived in
        Glacier. A user who asked for a thousand files and whose nine hundred and ninety-ninth has a
        permission problem wants the other nine hundred and ninety-nine.

        `zipfile` writes a ZIP64 archive by itself when it needs to, so neither a single file above 4
        GiB nor an archive above it is a special case. Deflate at a low level is a deliberate choice -
        see `ServiceConfig.compress_level`.
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
                    # Not a failure of this file - the whole task is being abandoned. Let it through;
                    # the `with` closes the half-written archive and `run` deletes the directory.
                    raise
                except Exception as error:
                    LOG.warning('Task %s: cannot read %s (%s), skipping it', self._task.id,
                                found.path, error)
                    failed.append((found.path, str(error)))
                    temp_path.unlink(missing_ok=True)
                    continue
                # `arcname` is the name inside the archive. Without it the entry would carry the local
                # temporary path, and every file in the archive would be called `current.part`.
                archive.write(temp_path, arcname=found.path)
                temp_path.unlink(missing_ok=True)
            if unreadable or failed:
                # `writestr` adds an entry from a string, with no file behind it. The name is chosen
                # against what is already in the archive: `zipfile` would happily write a second entry
                # under a name that is already there, and most extraction tools keep the last one - so
                # a selection containing its own `README.txt` would have it replaced by our note.
                archive.writestr(_note_name(archive.namelist()),
                                 _skipped_note(unreadable, failed))
        LOG.info('Task %s packed %s file(s), %s skipped', self._task.id,
                 len(files) - len(failed), len(unreadable) + len(failed))
        return archive_path, failed

    def _upload(self, archive_path: Path) -> int:
        """
        Puts the archive in the bucket and returns its size.

        `_uploaded` is set only after the upload returned, and it is what every cleanup path consults.
        An upload interrupted halfway leaves no object - `ArchiveBucket` aborts its multipart upload on
        the way out - so a delete issued for it would be pointless, and the flag keeps us from issuing
        one.
        """
        self._watcher.check()
        size = self._bucket.upload(archive_path, self._key, self._watcher.check)
        self._uploaded = True
        return size

    # ------------------------------------------------------------------ end states

    def _finish_succeeded(self, size: int, skipped: int) -> None:
        """
        Reports success, and deletes the archive if the report is refused.

        The refusal is the interesting case. It means the task is not RUNNING any more - the janitor
        failed it as stuck while we were working, most likely - so nothing will ever point at the
        archive we have just uploaded. Leaving it would be a file nobody can reach and nobody will
        clean up, since the janitor only expires archives of tasks that *did* succeed.
        """
        result = {
            # The discriminator of the polymorphic result. Jackson selects `DownloadArchiveResult` by
            # it, and refuses the whole report if it is missing or names another type.
            'type': TASK_TYPE,
            # Where the archive actually went. Reported rather than left to be derived later, because
            # the destination is a preference an administrator may change: after a change, a derived
            # location points at a key that was never written, the signed link gives the user a 404,
            # and - since deleting an absent key succeeds - the expiry sweep reports a clean delete
            # while the real object stays where it was for ever.
            # A snapshot of the destination as it was when this archive was written, and only of the
            # parts that are settings: the key's own tail is `<taskId>.zip` and the API derives it, so
            # reporting the whole key would store a second copy of something already known and let it
            # disagree with the task it belongs to.
            'storageId': self._platform.destination_storage_id,
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
            # The report could not be delivered at all, after retries. The archive stays: the janitor
            # will fail the task as stuck and delete the object, which is the right outcome, and
            # deleting it here would only make a window where the task is SUCCESS with nothing behind
            # it if the report did in fact arrive.
            LOG.error('Task %s succeeded but its report could not be delivered (%s); the janitor '
                      'will clean it up', self._task.id, error)

    def _finish_stopped(self, reason: StopReason) -> None:
        """
        Ends a task that was going fine until it was asked to stop, or ran out of time.

        `ABANDONED` is the one case that reports nothing. The task is already in a terminal status, so
        every transition would be refused, and the only thing left to do is to not leave an archive
        behind.

        For the other two the task is moved to `STOPPING` first. It is not required - `RUNNING` may go
        straight to `FAILED` - and it is worth the extra call: cleaning up a large archive takes time,
        a user watching the task sees why it is not finished yet, and the janitor's stuck-task window
        starts again from the moment the status changed rather than running out mid-cleanup.
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
                              f'{self._platform.worker_max_running_minutes} minutes and was stopped. '
                              f'Ask for fewer files at a time.')

    def _finish_failed(self, message: str) -> None:
        """Ends a task that cannot succeed: no archive left behind, and the reason recorded."""
        self._discard_archive()
        self._try_report(TaskStatus.FAILED, error_message=message[:MAX_ERROR_MESSAGE])

    def _try_report(self, status: TaskStatus, error_message: str | None = None) -> None:
        """
        Reports a status and swallows whatever comes back.

        Called only from paths that are already ending the task, where there is nothing left to do
        about a failure and raising would replace the real reason with a reporting error. A task left
        in a non-terminal status by a failure here is the janitor's to finish, which is exactly what the
        janitor is for.
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
            # Logged as an error and not raised: the task is ending either way, and an operator needs
            # to know about an object that is now unreferenced.
            LOG.error('Task %s: cannot delete its archive %s (%s). It stays in the bucket',
                      self._task.id, self._key, error)

    def _remove_task_dir(self) -> None:
        """
        Removes the scratch directory with everything in it.

        `ignore_errors=True` because this is the last thing a worker does: a file that cannot be deleted
        is worth a log line, not an exception at the end of a task that otherwise went well. The
        janitor sweeps directories left behind by workers that never got here at all.
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

    `README.txt` first, because that is where a person looks. A selection that contains one of its own
    gets the fallback, and in the case nobody will ever see - a selection containing both - a number is
    appended until the name is free. `itertools.count()` counts from 2 upwards with no end, which is
    safe here only because the loop always leaves on the first free name.
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

    Written as a text file inside the archive rather than only reported in the task, because the
    archive outlives the task - it is downloaded, moved, shared - and the question "is this everything
    I asked for?" is asked while looking at the unpacked folder.

    `'\\n'.join(lines)` is how a multi-line string is built: a list of lines, joined once. Repeated
    `text += line` would work and would copy the whole string every time.
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
    """
    Formats a byte count for a message a user reads: `1.5 GiB` rather than `1610612736`.

    The loop divides by 1024 until the number is small, which is the whole of it. `:.1f` keeps one
    decimal; the unit list ends at TiB because nothing here is bigger, and the loop leaves the number
    in TiB if it ever is.
    """
    value = float(size)
    for unit in ('B', 'KiB', 'MiB', 'GiB'):
        if abs(value) < 1024:
            return f'{value:.1f} {unit}' if unit != 'B' else f'{int(value)} {unit}'
        value /= 1024
    return f'{value:.1f} TiB'
