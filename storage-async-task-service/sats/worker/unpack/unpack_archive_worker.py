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
One extraction, from the claimed task to the status it ends in.

The order of operations is designed as making less job before it could meet some limit.

    1. check, before downloading   a file, within the size limit, of a format we open, declaring no
                                   more than the limits allow - all read from its tail
    2. extract locally             into `<task_id>/extracted/`, counting bytes and checking names
    3. the destination             the folder the caller named, created where it is missing, then
                                   one collision pass over the archive's own keys only
    4. upload                      key = destinationPath + the path inside the archive
    5. report, then delete         SUCCESS first, the source archive after it

A 400 GB archive is refused without fetching it. A bad name or a breached limit is found while the
destination is untouched. A collision found at entry 500 would otherwise leave 499 files written, so
the pass comes before the first upload. The source is deleted last, because until the upload has
finished the archive is all the user has.

A failure leaves the files it had already uploaded, and the destination folder. There is no
rollback. So a failed task reports how many files it wrote.
"""

from __future__ import annotations

import shutil
import threading
import time
from pathlib import Path, PurePosixPath

from ...cp_api_client import CloudPipelineApi
from .archive_extractor import ExtractionLimits, extract
from ...util.archive_probe import ArchiveFormat, ArchiveProbe, detect_format, probe_archive
from ...config import ServiceConfig, StorageUnpackArchiveTaskConfig
from ...datastorage_providers import EntryKind, FileDestination, FileSource, open_destination, \
    open_source
from ...model import ArchiveTask, AsyncTaskType, Storage, TaskStatus, UnpackParams
from ...util.errors import ApiError, ApiRejected, CredentialsUnavailable, DestinationConflict, \
    DestinationWriteFailed, InvalidTask, StopReason, StopRequested, ThresholdExceeded, \
    UnsafeArchiveEntry, UnsupportedArchive, UnsupportedSource
from ...util.log import get_logger
from ...util.sizes import human_bytes
from ..base import AsyncTaskWorker
from ..task_stop_watcher import TaskStopWatcher

LOG = get_logger(__name__)

# Both inside the task's own directory, so one `rmtree` is the whole cleanup.
LOCAL_ARCHIVE_NAME = 'archive'
EXTRACTED_DIRECTORY = 'extracted'

# Error messages go into a database column and into the GUI, so the text is cut. The full one is in
# this service's log.
MAX_ERROR_MESSAGE = 1000


class UnpackArchiveWorker(AsyncTaskWorker):
    """
    Extracts the archive of one task into the destination the caller named.

    Everything it touches comes from the task: the scratch directory is named after the task id, and
    both storages are loaded by the ids the task carries.
    """

    TASK_TYPE = AsyncTaskType.UNPACK_ARCHIVE

    def __init__(self, api: CloudPipelineApi, service_config: ServiceConfig,
                 unpack_config: StorageUnpackArchiveTaskConfig, task: ArchiveTask):
        self._api = api
        self._config = service_config
        self._unpack_config = unpack_config
        self._task = task
        self._params = task.unpack
        self._task_dir = service_config.work_dir / task.id
        self._watcher = TaskStopWatcher(api, task.id,
                                        unpack_config.worker_max_running_minutes * 60,
                                        service_config.status_poll_seconds)
        # Counted as the upload goes, so a failure can say how much it left behind.
        self._files_written = 0
        self._bytes_written = 0

    def run(self) -> None:
        """
        Runs the task to one of its end states. Never raises.

        Every path ends in a status report or an explained decision not to report one. The `finally`
        removes the scratch directory on all of them.
        """
        threading.current_thread().name = f'unpack-{self._task.id[:8]}'
        started = time.monotonic()
        LOG.info('Took task %s: extracting %s of storage %s into %s of storage %s', self._task.id,
                 self._params.source_path if self._params else None,
                 self._params.source_storage_id if self._params else None,
                 self._params.destination_path if self._params else None,
                 self._params.destination_storage_id if self._params else None)
        try:
            self._execute()
        except StopRequested as stop:
            self._finish_stopped(stop.reason)
        except ThresholdExceeded as exceeded:
            # The user asked for too much, so a warning rather than an error.
            LOG.warning('Task %s rejected: %s', self._task.id, exceeded)
            self._finish_failed(str(exceeded))
        except (UnsupportedArchive, UnsafeArchiveEntry, DestinationConflict,
                DestinationWriteFailed) as refused:
            LOG.warning('Task %s refused: %s', self._task.id, refused)
            self._finish_failed(str(refused))
        except (UnsupportedSource, InvalidTask, CredentialsUnavailable) as unsupported:
            LOG.warning('Task %s cannot be executed: %s', self._task.id, unsupported)
            self._finish_failed(str(unsupported))
        except ApiRejected as rejected:
            # The task is no longer ours, so a failure report would be refused too.
            LOG.warning('Task %s was refused by the API (%s), dropping the work', self._task.id,
                        rejected)
        except Exception as error:
            LOG.exception('Task %s failed unexpectedly', self._task.id)
            self._finish_failed(f'Internal error of the storage task service: {error}')
        finally:
            self._remove_task_dir()
            LOG.info('Task %s released after %.1f s', self._task.id, time.monotonic() - started)

    # ------------------------------------------------------------------ the happy path

    def _execute(self) -> None:
        """The task as it goes when nothing is wrong. Every failure is handled by the caller."""
        params = self._assert_executable()
        self._task_dir.mkdir(parents=True, exist_ok=True)
        source_storage = self._load_storage(params.source_storage_id, 'the archive was requested from')
        destination_storage = self._load_storage(params.destination_storage_id,
                                                 'the archive was to be extracted into')
        with open_source(self._api, source_storage, self._config) as source:
            archive = self._find_archive(source, params)
            probe = self._probe(source, archive, params)
            self._assert_fits_locally(archive.size, probe)
            local_archive = self._download(source, archive)
        extraction = extract(local_archive, self._task_dir / EXTRACTED_DIRECTORY,
                             PurePosixPath(params.source_path).name, self._local_format(local_archive),
                             ExtractionLimits(self._unpack_config.max_files_count,
                                              self._unpack_config.max_size_bytes),
                             self._watcher.check)
        with open_destination(self._api, destination_storage, self._config) as destination:
            destination.ensure_destination_folder(params.destination_path)
            self._assert_no_collisions(destination, extraction, params, source_storage)
            self._upload(destination, extraction, params)
        self._finish_succeeded(params)
        self._delete_source(params, source_storage)

    def _assert_executable(self) -> UnpackParams:
        """
        Checks the four things an extraction cannot be guessed without.

        The API refuses a request without them, so reaching this means a task written straight into
        the queue. It fails here with the reason named instead of further down as an `AttributeError`.
        """
        params = self._params
        if params is None:
            raise InvalidTask(f'Task {self._task.id} carries no extraction parameters, so there is '
                              f'nothing to extract')
        if not params.source_storage_id or not params.source_path:
            raise InvalidTask(f'Task {self._task.id} names no source storage or no archive path')
        if not params.destination_storage_id:
            raise InvalidTask(f'Task {self._task.id} names no destination storage')
        return params

    def _load_storage(self, storage_id: int, what: str) -> Storage:
        """
        Loads one of the task's storages.

        The task carries ids only. The type, the bucket, the region and the mount are the API's to
        answer: copying them into the task would let them go stale.
        """
        try:
            return self._api.load_storage(storage_id)
        except ApiRejected as rejected:
            raise InvalidTask(f'The storage {what} is not available any more: {rejected}') \
                from rejected

    def _find_archive(self, source: FileSource, params: UnpackParams):
        """
        Finds the archive and checks its own size, before a byte of it is fetched.

        The cheapest refusal there is: the size comes from the storage listing.
        """
        archive = source.stat(params.source_path)
        if archive is None:
            raise InvalidTask(
                f'{params.source_path} is not a file in the source storage. Name the archive itself, '
                f'not the folder it is in.')
        if archive.skip_reason:
            raise InvalidTask(f'The archive {params.source_path} cannot be read: '
                              f'{archive.skip_reason}')
        limit = self._unpack_config.max_archive_size_bytes
        if limit is not None and archive.size > limit:
            raise ThresholdExceeded(
                f'The archive is {human_bytes(archive.size)}, and {human_bytes(limit)} is the largest '
                f'one this platform extracts. Split it, or ask an administrator to raise the limit.')
        LOG.info('Task %s: the archive is %s bytes', self._task.id, archive.size)
        return archive

    def _probe(self, source: FileSource, archive, params: UnpackParams) -> ArchiveProbe:
        """
        Reads what the archive declares about itself, and refuses it on that where it can.

        A format that declares nothing comes back as `None`, and its limits are enforced while
        extracting instead. That is the only guard `tar.bz2` ever gets.
        """
        with source.open_stream(archive) as stream:
            probe = probe_archive(stream, archive.size)
        LOG.info('Task %s: the archive is %s, declaring %s entry(ies) and %s byte(s)',
                 self._task.id, probe.format, probe.entries, probe.total_bytes)
        if not probe.is_supported:
            raise UnsupportedArchive(
                f'{params.source_path} is not an archive of a format this service can extract. '
                f'Supported are ZIP, TAR, TAR.GZ, TGZ, TAR.BZ2, TAR.XZ, GZ, BZ2 and XZ, and the '
                f'format is read from the content rather than from the name.')
        files_limit = self._unpack_config.max_files_count
        if files_limit is not None and probe.entries is not None and probe.entries > files_limit:
            raise ThresholdExceeded(
                f'The archive declares {probe.entries} entries, and {files_limit} is the most one '
                f'archive may be extracted into. Split it, or ask an administrator to raise the '
                f'limit.')
        size_limit = self._unpack_config.max_size_bytes
        if size_limit is not None and probe.total_bytes is not None and probe.total_bytes > size_limit:
            raise ThresholdExceeded(
                f'The archive declares {human_bytes(probe.total_bytes)} of content, and '
                f'{human_bytes(size_limit)} is the most one archive may be extracted into. Split it, '
                f'or ask an administrator to raise the limit.')
        return probe

    def _assert_fits_locally(self, archive_size: int, probe: ArchiveProbe) -> None:
        """
        Checks the scratch directory against the archive plus its content, before downloading.

        The content is counted at what the archive declares, and at `maxSizeBytes` where the format
        declares nothing - the byte counter stops the extraction at that size anyway. A declared
        total that lies ends as `ENOSPC`, which fails the task with a message saying so.
        """
        content = probe.total_bytes
        if content is None:
            content = self._unpack_config.max_size_bytes or 0
        needed = archive_size + content
        free = shutil.disk_usage(self._config.work_dir).free
        if free < needed:
            raise ThresholdExceeded(
                f'The storage task service has {human_bytes(free)} of local space free and needs about '
                f'{human_bytes(needed)} to extract this archive. Try again later, or ask an '
                f'administrator to give the service more space.')

    def _download(self, source: FileSource, archive) -> Path:
        """Fetches the archive into the task's own directory."""
        local = self._task_dir / LOCAL_ARCHIVE_NAME
        LOG.info('Task %s: downloading the archive', self._task.id)
        source.download(archive, local, self._watcher.check)
        return local

    def _local_format(self, local_archive: Path) -> ArchiveFormat:
        """
        Detects the format again, on the downloaded file.

        The remote read is the one that can come back short, and a ranged request that answered
        fewer bytes than asked would make a good archive look unknown. This is also the copy that is
        about to be opened.
        """
        with local_archive.open('rb') as stream:
            return detect_format(stream, local_archive.stat().st_size)

    # ------------------------------------------------------------------ the destination

    def _assert_no_collisions(self, destination: FileDestination, extraction, params: UnpackParams,
                              source_storage: Storage) -> None:
        """
        One pass over the destination, over the archive's own keys and nothing else.

        Three refusals, all before the first upload, so the destination is left as it was:

          * a key that is taken, with `overwrite=false`. One collision refuses the whole task;
          * a file where the archive holds a folder, or the reverse. Refused even with
            `overwrite=true`: a share cannot hold both, and on S3 the GUI would show a file and a
            folder of one name while `pipe mount` and a download to disk fail;
          * the archive's own path, refused even with `overwrite=true`. Otherwise a later failure
            leaves the user without the archive, and `deleteSourceOnSuccess` deletes the extracted
            file instead of the archive.

        One or two requests per entry on S3, one `lstat` per entry on a share. Listing the archive's
        top-level prefixes instead would be cheaper for a large archive, and needs a cache with a cap
        and a fallback.
        """
        archive_key = self._archive_key_in_destination(params, source_storage)
        for found in extraction.files:
            self._watcher.check()
            key = self._destination_key(params, found.key)
            if key == archive_key:
                raise DestinationConflict(
                    f'The archive holds {found.key}, which would be written over the archive itself '
                    f'at {params.source_path}. Extract it into another folder.')
            kind = destination.kind(key)
            if kind is EntryKind.FOLDER:
                raise DestinationConflict(
                    f'The destination already holds a folder at {key}, and the archive holds a file '
                    f'of that name. Rename one of them, or extract into another folder.')
            if kind is EntryKind.FILE and not params.overwrite:
                # The state of the destination is not named here: `_written_note` appends it to
                # every failure, and only it knows the count.
                raise DestinationConflict(
                    f'{key} already exists in the destination. Ask for the extraction again with '
                    f'"overwrite": true to replace it, or remove it first.')
        for folder in extraction.empty_folders:
            self._watcher.check()
            key = self._destination_key(params, folder)
            if destination.kind(key) is EntryKind.FILE:
                raise DestinationConflict(
                    f'The destination already holds a file at {key}, and the archive holds a folder '
                    f'of that name. Rename one of them, or extract into another folder.')

    def _upload(self, destination: FileDestination, extraction, params: UnpackParams) -> None:
        """
        Writes the extracted files into the destination, counting them as it goes.

        A counter and not a journal: nothing is ever deleted from the destination, so a failure needs
        to report how many files it wrote, not which.

        With `overwrite=false` the write itself refuses a taken key, so a file created after the
        collision pass is refused rather than replaced.
        """
        for found in extraction.files:
            self._watcher.check()
            key = self._destination_key(params, found.key)
            try:
                destination.upload(found.path, key, self._watcher.check,
                                   overwrite=params.overwrite)
            except (StopRequested, DestinationConflict):
                raise
            except Exception as error:
                # Not an internal error: the storage refused the write, or went away.
                raise DestinationWriteFailed(
                    f'{key} could not be written to the destination: {error}. Try the extraction '
                    f'again once the storage is reachable.') from error
            self._files_written += 1
            self._bytes_written += found.size
        for folder in extraction.empty_folders:
            destination.create_folder(self._destination_key(params, folder))
        LOG.info('Task %s wrote %s file(s), %s bytes', self._task.id, self._files_written,
                 self._bytes_written)

    @staticmethod
    def _destination_key(params: UnpackParams, entry_key: str) -> str:
        """
        The key one entry lands under: the destination folder, then the entry's own path.

        A concatenation and nothing more. `destination_path` arrives from the API as a folder prefix,
        ending in a slash or empty for the root, so there is no decision to make here.
        """
        return f'{params.destination_path}{entry_key}'

    @staticmethod
    def _archive_key_in_destination(params: UnpackParams, source_storage: Storage) -> str | None:
        """
        The archive's own path as a key of the destination, or `None` when the storages differ.

        Both paths are storage-relative, so they are comparable only within one storage. Extracting
        into the archive's own folder is otherwise safe: the archive is deleted after the report.
        """
        if source_storage.id != params.destination_storage_id:
            return None
        return params.source_path

    # ------------------------------------------------------------------ end states

    def _finish_succeeded(self, params: UnpackParams) -> None:
        """
        Reports success.

        Whether the report arrives does not change the content: nothing rolls the destination back,
        so a lost `SUCCESS` costs a wrong status and nothing else. That is why it is reported before
        the source is deleted.
        """
        result = {
            # Jackson selects `UnpackArchiveResult` by this, and refuses the report without it.
            'type': AsyncTaskType.UNPACK_ARCHIVE,
            'destinationStorageId': params.destination_storage_id,
            'destinationPath': params.destination_path,
            'filesWritten': self._files_written,
            'bytesWritten': self._bytes_written,
        }
        try:
            self._api.report_status(self._task.id, TaskStatus.SUCCESS, result=result)
            LOG.info('Task %s succeeded: %s file(s), %s bytes', self._task.id, self._files_written,
                     self._bytes_written)
        except (ApiError, ApiRejected) as error:
            LOG.error('Task %s extracted %s file(s) but its success could not be reported (%s); the '
                      'cleaner will fail it, and its content stays in the destination',
                      self._task.id, self._files_written, error)

    def _delete_source(self, params: UnpackParams, source_storage: Storage) -> None:
        """
        Removes the archive, if the task asked for it, after the success was reported.

        Last of everything: until the content is in the destination the archive is all the user has.
        A failure to delete is a warning, not a failed task - the extraction did succeed.

        Deleting is a write on the source storage, so it goes through a destination opened on the
        source. On a share that is what gives it a read-write mount.
        """
        if not params.delete_source_on_success:
            return
        try:
            with open_destination(self._api, source_storage, self._config) as source_writer:
                source_writer.delete(params.source_path)
            LOG.info('Task %s deleted its source archive %s', self._task.id, params.source_path)
        except Exception as error:
            LOG.warning('Task %s extracted the archive but could not delete %s (%s). It stays in '
                        'the storage', self._task.id, params.source_path, error)

    def _finish_stopped(self, reason: StopReason) -> None:
        """
        Ends a task that was going fine until it was asked to stop, or ran out of time.

        `ABANDONED` reports nothing: the task is already terminal, so every transition is refused.
        The other two go through `STOPPING` first, so a user watching the task sees why it stopped
        and the cleaner's stuck window starts again.

        Nothing is removed from the destination. A `CANCELLED` task cannot say how many files it
        left, because the API accepts an error message only with `FAILED`; the number is in the log.
        """
        if reason == StopReason.ABANDONED:
            LOG.warning('Task %s is already finished on the API side, dropping the work; the %s '
                        'file(s) it wrote stay in the destination', self._task.id,
                        self._files_written)
            return
        LOG.info('Task %s is stopping (%s) with %s file(s) written', self._task.id, reason,
                 self._files_written)
        self._try_report(TaskStatus.STOPPING)
        if reason == StopReason.CANCELLED:
            self._try_report(TaskStatus.CANCELLED)
        else:
            self._try_report(
                TaskStatus.FAILED,
                error_message=f'The archive was not extracted within '
                              f'{self._unpack_config.worker_max_running_minutes} minutes and was '
                              f'stopped. {self._written_note()} Extract a smaller archive, or ask an '
                              f'administrator to raise the time limit.')

    def _finish_failed(self, message: str) -> None:
        """
        Ends a task that cannot succeed.

        The message carries the number of files already written, because that decides what the user
        does next: with `overwrite=false` a new run refuses exactly those files.
        """
        full = f'{message} {self._written_note()}'.strip()
        self._try_report(TaskStatus.FAILED, error_message=full[:MAX_ERROR_MESSAGE])

    def _written_note(self) -> str:
        """One sentence about what is already in the destination."""
        if not self._files_written:
            return 'Nothing was written to the destination.'
        return (f'{self._files_written} file(s) had already been written to the destination and stay '
                f'there; a new extraction with "overwrite": false will refuse them.')

    def _try_report(self, status: TaskStatus, error_message: str | None = None) -> None:
        """
        Reports a status and swallows whatever comes back.

        Called only from paths that are already ending the task. One left in a non-terminal status by
        a failure here is the cleaner's to finish.
        """
        try:
            self._api.report_status(self._task.id, status, error_message=error_message)
        except (ApiError, ApiRejected) as error:
            LOG.warning('Task %s: cannot report %s (%s)', self._task.id, status, error)

    # ------------------------------------------------------------------ cleanup

    def _remove_task_dir(self) -> None:
        """
        Removes the scratch directory with the archive and everything that came out of it.

        `ignore_errors=True` because this is the last thing a worker does. Directories left by
        workers that never got here are the cleaner's.
        """
        if not self._task_dir.exists():
            return
        shutil.rmtree(self._task_dir, ignore_errors=True)
        if self._task_dir.exists():
            LOG.warning('Task %s: scratch directory %s could not be removed', self._task.id,
                        self._task_dir)
