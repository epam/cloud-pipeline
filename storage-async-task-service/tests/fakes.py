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
Stand-ins for everything outside the process: the API, the archive bucket, the file source.

Hand-written rather than mocks: a fake has to be changed when the thing it stands for changes.
"""

from __future__ import annotations

import zipfile
from pathlib import Path

from botocore.exceptions import ClientError

from sats.util.errors import ApiRejected
from sats.model import ArchiveTask, CloudRegion, FileShare, Storage, StorageType, TaskStatus
from sats.datastorage_providers.base import FileSource, SourceFile


class FakeApi:
    """The API, with the answers a test sets up and a record of every status reported."""

    def __init__(self, task: ArchiveTask, storage: Storage | None = None,
                 preference: str | None = None):
        self.task = task
        # `region_id` is set because a cloud storage of the platform always has one, and a client
        # cannot be built for a storage that has none - see `aws._load_region`.
        self.storage = storage or Storage(id=1, name='bucket-storage', type=StorageType.S3,
                                          path='some-bucket', region_id=1)
        self.preference = preference
        self.reports: list[tuple[TaskStatus, dict | None, str | None]] = []
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

    def claim_next(self, task_type: str) -> ArchiveTask | None:
        return None

    def load_task(self, task_id: str) -> ArchiveTask:
        if self.task_missing:
            raise ApiRejected(f'Task {task_id} does not exist')
        return self.task

    def filter_tasks(self, statuses: list[TaskStatus] | None = None, **_kwargs) -> list[ArchiveTask]:
        # `**_kwargs` ignores the rest: the real method has six arguments and no test cares. The
        # statuses are the exception - the cleaner's cleanups are told apart by them.
        found = []
        for status in statuses or []:
            found += self.filtered.get(status, [])
        return found

    def report_status(self, task_id: str, status: TaskStatus, result: dict | None = None,
                      error_message: str | None = None) -> ArchiveTask:
        if self.reject_reports:
            raise ApiRejected(f'Task {task_id} cannot move to {status}')
        self.reports.append((status, result, error_message))
        # The real API answers with the task as it now is, and the worker ignores the answer. Keeping
        # the status up to date anyway means a `load_task` after a report behaves like the real one.
        self.task = ArchiveTask(id=self.task.id, status=status, owner=self.task.owner,
                                storage_id=self.task.storage_id, paths=self.task.paths,
                                archive_name=self.task.archive_name)
        return self.task

    def load_storage(self, storage_id: int) -> Storage:
        if self.storage_missing:
            raise ApiRejected(f'Data storage {storage_id} was not found')
        return self.storage

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
                 storage_classes: dict[str, str] | None = None):
        self.objects = objects or {}
        self.denied = denied or set()
        self.storage_classes = storage_classes or {}
        # Every key a HEAD was made for, which is how a test shows that a listing did not make one per
        # object.
        self.heads: list[str] = []

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

    def get_object(self, Bucket: str, Key: str) -> dict:
        raise AssertionError('No test downloads through the fake client yet')


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

    def download(self, source_file: SourceFile, destination: Path, heartbeat) -> None:
        heartbeat()
        self.heartbeats += 1
        if source_file.path in self._failing:
            raise OSError('permission denied')
        destination.write_bytes(self._files[source_file.path])

    def close(self) -> None:
        self.closed = True
