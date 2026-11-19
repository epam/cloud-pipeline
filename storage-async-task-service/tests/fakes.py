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
Stand-ins for everything outside the process: the API, a bucket, a file source, a destination.

Hand-written rather than mocks: a fake has to be changed when the thing it stands for changes.
"""

from __future__ import annotations

import dataclasses
import io
import zipfile
from pathlib import Path

from botocore.exceptions import ClientError

from sats.config import AsyncTasksConfig, StorageDownloadArchiveTaskConfig, \
    StorageUnpackArchiveTaskConfig
from sats.util.errors import ApiRejected, DestinationConflict
from sats.model import ArchiveTask, AsyncTaskType, CloudRegion, FileShare, Storage, StorageType, \
    TaskStatus
from sats.datastorage_providers.base import EntryKind, FileDestination, FileSource, SourceFile


def task_configs(download_destination: int | None = 9,
                 unpack: StorageUnpackArchiveTaskConfig | None = None,
                 stuck_tasks_threshold_minutes: int = 360) -> AsyncTasksConfig:
    """
    The preference as a worker or the cleaner reads it, with both sections filled in.

    A helper rather than a literal in every test, because the shape has two levels and a test that
    cares about one limit should not have to spell out the other seven.
    """
    return AsyncTasksConfig(
        download=StorageDownloadArchiveTaskConfig(
            destination_storage_id=download_destination, destination_prefix='archives/',
            max_size_bytes=None, max_files_count=None, ttl_days=7, max_user_tasks=3,
            worker_max_running_minutes=240,
            stuck_tasks_threshold_minutes=stuck_tasks_threshold_minutes),
        unpack=unpack or unpack_config(),
        max_user_tasks=3, stuck_tasks_threshold_minutes=stuck_tasks_threshold_minutes)


def unpack_config(max_archive_size_bytes: int | None = None, max_size_bytes: int | None = None,
                  max_files_count: int | None = None, worker_max_running_minutes: int = 240,
                  configured: bool = True) -> StorageUnpackArchiveTaskConfig:
    """The `storageUnpackArchive` section, every limit off unless a test sets it."""
    return StorageUnpackArchiveTaskConfig(
        max_archive_size_bytes=max_archive_size_bytes, max_size_bytes=max_size_bytes,
        max_files_count=max_files_count, worker_max_running_minutes=worker_max_running_minutes,
        configured=configured)


class FakeApi:
    """The API, with the answers a test sets up and a record of every status reported."""

    def __init__(self, task: ArchiveTask, storage: Storage | None = None,
                 preference: str | None = None):
        self.task = task
        # `region_id` is set because a cloud storage of the platform always has one, and a client
        # cannot be built for a storage that has none - see `aws._load_region`.
        self.storage = storage or Storage(id=1, name='bucket-storage', type=StorageType.S3,
                                          path='some-bucket', region_id=1)
        # A storage per id, for a test with a source and a destination. `storage` answers for any id
        # this does not name, which is what most tests want.
        self.storages: dict[int, Storage] = {}
        self.preference = preference
        self.reports: list[tuple[TaskStatus, dict | None, str | None]] = []
        # Every list of types `claim_next` was called with, in order.
        self.claimed_types: list[tuple] = []
        # What `filter_tasks` answers, per status asked about: one cleanup runs three filters, each
        # asking about different statuses.
        self.filtered: dict[TaskStatus, list[ArchiveTask]] = {}
        # Set to make `report_status` refuse, as the real API does for a transition that is no longer
        # allowed.
        self.reject_reports = False
        # Set to make `load_task` answer that the task does not exist.
        self.task_missing = False
        # Set to make `load_storage` refuse, as the real API does for a storage that was deleted after
        # the task was created.
        self.storage_missing = False
        # The region every `load_region` answers with, unless `regions` names one for that id. Most
        # tests have one region and set this; a test about two regions sets the map instead.
        self.region = CloudRegion(id=1, code='eu-central-1')
        self.regions: dict[int, CloudRegion] = {}
        # Every region id asked about, in order. The tests of `cloud/aws.py` assert on it to show that a
        # client is built from the storage's own region.
        self.region_requests: list[int] = []
        # Set to make `load_region` refuse, as the real API does for a region that was deleted.
        self.region_missing = False

    def get_preference(self, key: str) -> str | None:
        return self.preference

    def claim_next(self, task_types) -> ArchiveTask | None:
        # A list of types, as the real method takes. Recorded so a test can assert that the service
        # asked for every type it runs in one call.
        self.claimed_types.append(tuple(task_types))
        return None

    def load_task(self, task_id: str) -> ArchiveTask:
        if self.task_missing:
            raise ApiRejected(f'Task {task_id} does not exist')
        return self.task

    def filter_tasks(self, task_type: AsyncTaskType | None = None,
                     statuses: list[TaskStatus] | None = None, **_kwargs) -> list[ArchiveTask]:
        # `**_kwargs` ignores the rest: the real method has six arguments and no test cares about the
        # timestamps. The type and the statuses are not ignored - the cleaner's cleanups are told
        # apart by the statuses, and it now asks once per task type, so a fake that ignored the type
        # would hand every cleanup every task.
        found = []
        for status in statuses or []:
            found += [task for task in self.filtered.get(status, [])
                      if task_type is None or task.type == task_type]
        return found

    def report_status(self, task_id: str, status: TaskStatus, result: dict | None = None,
                      error_message: str | None = None) -> ArchiveTask:
        if self.reject_reports:
            raise ApiRejected(f'Task {task_id} cannot move to {status}')
        self.reports.append((status, result, error_message))
        # The real API answers with the task as it now is, and the worker ignores the answer. Keeping
        # the status up to date anyway means a `load_task` after a report behaves like the real one.
        # `replace` and not a fresh object, so the type and the params survive - a worker polls its
        # own task through `load_task`, and one that came back as another type would be dispatched
        # wrongly.
        self.task = dataclasses.replace(self.task, status=status)
        return self.task

    def load_storage(self, storage_id: int) -> Storage:
        if self.storage_missing:
            raise ApiRejected(f'Data storage {storage_id} was not found')
        return self.storages.get(storage_id, self.storage)

    def load_file_share(self, share_id: int) -> FileShare:
        raise AssertionError('No test needs a file share yet')

    def load_region(self, region_id: int) -> CloudRegion:
        self.region_requests.append(region_id)
        if self.region_missing:
            raise ApiRejected(f'Cloud region {region_id} was not found')
        return self.regions.get(region_id, self.region)

    @property
    def statuses(self) -> list[TaskStatus]:
        """Just the statuses, which is what most assertions are about."""
        return [status for status, _, _ in self.reports]


class FakeBucket:
    """The archive bucket: records uploads and deletes, and can be told to fail."""

    def __init__(self):
        self.uploaded: list[str] | None = None
        # The key the archive went to, which the prefix decides.
        self.uploaded_key: str | None = None
        self.size: int | None = None
        self.deleted: list[str] = []
        self.fail_upload = False

    def upload(self, path: Path, key: str, heartbeat) -> int:
        if self.fail_upload:
            raise RuntimeError('upload failed')
        heartbeat()
        self.uploaded_key = key
        with zipfile.ZipFile(path) as archive:
            self.uploaded = archive.namelist()
        self.size = path.stat().st_size
        return self.size

    def delete(self, key: str) -> None:
        self.deleted.append(key)


class FakeS3Client:
    """The boto3 S3 client, with only the calls this service makes."""

    def __init__(self, objects: dict[str, int] | None = None, denied: set[str] | None = None,
                 storage_classes: dict[str, str] | None = None,
                 bodies: dict[str, bytes] | None = None):
        self.objects = objects or {}
        self.denied = denied or set()
        self.storage_classes = storage_classes or {}
        # The content of the objects a test reads. Apart from `objects`, which is only their sizes,
        # because most tests care about the listing and not about any bytes.
        self.bodies = dict(bodies or {})
        for key, content in self.bodies.items():
            self.objects.setdefault(key, len(content))
        # Every key a HEAD was made for, which is how a test shows that a listing did not make one per
        # object.
        self.heads: list[str] = []
        # Every `Range` header asked for, in order: the point of reading an archive where it lies is
        # that it costs a handful of requests, so a test asserts on their number.
        self.ranges: list[str] = []
        # `(key, whether the write was conditional)` per put, and the keys deleted.
        self.puts: list[tuple[str, bool]] = []
        self.deleted: list[str] = []

    def head_object(self, Bucket: str, Key: str) -> dict:
        self.heads.append(Key)
        if Key in self.denied:
            raise ClientError({'ResponseMetadata': {'HTTPStatusCode': 403}}, 'HeadObject')
        if Key not in self.objects:
            raise ClientError({'ResponseMetadata': {'HTTPStatusCode': 404}}, 'HeadObject')
        head = {'ContentLength': self.objects[Key]}
        if Key in self.storage_classes:
            head['StorageClass'] = self.storage_classes[Key]
        return head

    def get_paginator(self, operation_name: str) -> _FakePaginator:
        assert operation_name == 'list_objects_v2'
        return _FakePaginator(self)

    def get_object(self, Bucket: str, Key: str, Range: str | None = None) -> dict:
        content = self.bodies[Key]
        if Range is not None:
            self.ranges.append(Range)
            first, _, last = Range.removeprefix('bytes=').partition('-')
            content = content[int(first):int(last) + 1]
        return {'Body': io.BytesIO(content)}

    def list_objects_v2(self, Bucket: str, Prefix: str, MaxKeys: int | None = None) -> dict:
        found = [key for key in sorted(self.objects) if key.startswith(Prefix)]
        if MaxKeys is not None:
            found = found[:MaxKeys]
        if not found:
            # A listing that matches nothing omits `Contents` altogether.
            return {'KeyCount': 0}
        return {'Contents': [{'Key': key, 'Size': self.objects[key]} for key in found]}

    def put_object(self, Bucket: str, Key: str, Body, IfNoneMatch: str | None = None) -> dict:
        if IfNoneMatch is not None and Key in self.objects:
            # What S3 answers a conditional write with when the key is taken.
            raise ClientError({'ResponseMetadata': {'HTTPStatusCode': 412},
                               'Error': {'Code': 'PreconditionFailed'}}, 'PutObject')
        content = Body.read()
        self.objects[Key] = len(content)
        self.bodies[Key] = content
        self.puts.append((Key, IfNoneMatch is not None))
        return {}

    def delete_object(self, Bucket: str, Key: str) -> dict:
        self.deleted.append(Key)
        self.objects.pop(Key, None)
        self.bodies.pop(Key, None)
        return {}


class _FakePaginator:
    """The paginator `get_paginator` hands back. One page is enough for every test here."""

    def __init__(self, client: FakeS3Client):
        self._client = client

    def paginate(self, Bucket: str, Prefix: str, PaginationConfig: dict | None = None):
        contents = [{'Key': key, 'Size': size, 'StorageClass': self._client.storage_classes.get(key)}
                    for key, size in sorted(self._client.objects.items())
                    if key.startswith(Prefix)]
        if not contents:
            # A listing that matches nothing omits `Contents` altogether rather than sending an empty
            # list, and code that assumes the key is there breaks only against the real service.
            yield {}
            return
        yield {'Contents': contents}


class FakeSource(FileSource):
    """A file source over a list of `SourceFile`, writing their content on download."""

    def __init__(self, files: dict[str, bytes], unreadable: dict[str, str] | None = None,
                 failing: set[str] | None = None):
        self._files = files
        self._unreadable = unreadable or {}
        self._failing = failing or set()
        self.closed = False
        self.heartbeats = 0

    def list_files(self, paths: tuple[str, ...]):
        for name, content in self._files.items():
            yield SourceFile(path=name, size=len(content), locator=name)
        for name, reason in self._unreadable.items():
            yield SourceFile(path=name, size=0, locator=name, skip_reason=reason)

    def stat(self, path: str) -> SourceFile | None:
        if path in self._unreadable:
            return SourceFile(path=path, size=0, locator=path,
                              skip_reason=self._unreadable[path])
        if path not in self._files:
            return None
        return SourceFile(path=path, size=len(self._files[path]), locator=path)

    def download(self, source_file: SourceFile, destination: Path, heartbeat) -> None:
        heartbeat()
        self.heartbeats += 1
        if source_file.path in self._failing:
            raise OSError('permission denied')
        destination.write_bytes(self._files[source_file.path])

    def open_stream(self, source_file: SourceFile):
        # `BytesIO` is seekable and readable, which is all `util.archive_probe` asks of a stream.
        return io.BytesIO(self._files[source_file.path])

    def close(self) -> None:
        self.closed = True


class FakeDestination(FileDestination):
    """
    A destination in memory: what it already holds, what was written into it, what it refuses.

    `appearing` is the one case a fake has to be able to produce and a real storage cannot be made
    to: a key created between the collision pass and the upload. The first upload of such a key is
    refused, as a conditional write would refuse it.
    """

    def __init__(self, existing: dict[str, bytes] | None = None,
                 folders: set[str] | None = None, appearing: set[str] | None = None,
                 file_at_prefix: bool = False):
        self.existing = dict(existing or {})
        self.folders = set(folders or ())
        self.appearing = set(appearing or ())
        self.file_at_prefix = file_at_prefix
        self.written: list[str] = []
        self.created_folders: list[str] = []
        self.deleted: list[str] = []
        self.checked: list[str] = []
        self.closed = False
        self.prepared_prefix: str | None = None
        self.fail_upload_of: str | None = None

    def ensure_destination_folder(self, prefix: str) -> None:
        self.prepared_prefix = prefix
        if self.file_at_prefix:
            raise DestinationConflict(
                f'The folder {prefix or "/"} cannot be created in storage fake, a file is in the way')

    def kind(self, key: str) -> EntryKind:
        self.checked.append(key)
        if key in self.folders:
            return EntryKind.FOLDER
        if key in self.existing:
            return EntryKind.FILE
        return EntryKind.MISSING

    def upload(self, local_path: Path, key: str, heartbeat, overwrite: bool = False) -> int:
        heartbeat()
        if key == self.fail_upload_of:
            raise OSError('the storage refused the write')
        if not overwrite and (key in self.existing or key in self.appearing):
            raise DestinationConflict(f'{key} already exists')
        self.existing[key] = local_path.read_bytes()
        self.written.append(key)
        return local_path.stat().st_size

    def create_folder(self, key: str) -> None:
        self.created_folders.append(key)
        self.folders.add(key)

    def delete(self, key: str) -> None:
        self.deleted.append(key)
        self.existing.pop(key, None)

    def close(self) -> None:
        self.closed = True
