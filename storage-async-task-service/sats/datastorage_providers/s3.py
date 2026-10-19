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
S3: reading the files of a storage, and writing the finished archive.

Both clients are built in `sats/cloud/aws.py` and passed in here.
"""

from __future__ import annotations

import math
from collections.abc import Iterator
from pathlib import Path

from botocore.exceptions import ClientError

from .base import ARCHIVED_STORAGE_CLASSES, DOWNLOAD_BLOCK_BYTES, FileSource, Heartbeat, SourceFile
from ..util.log import get_logger
from ..model import Storage

LOG = get_logger(__name__)

# Keys per listing call. 1000 is the S3 maximum.
LIST_PAGE_SIZE = 1000

# Restore header of an archived object. "true" means the restore is still running and the object
# cannot be read yet.
RESTORE_IN_PROGRESS = 'ongoing-request="true"'

ARCHIVE_EXTENSION = '.zip'

# S3 limits: at most 10000 parts per multipart upload, at least 5 MiB per part except the last.
MAX_PARTS = 10000
MIN_PART_SIZE_BYTES = 5 * 1024 * 1024


def absolute_key(storage_prefix: str, relative_key: str) -> str:
    """The key inside the bucket: the storage's own folder, then the storage-relative key."""
    return f'{storage_prefix}/{relative_key}' if storage_prefix else relative_key


def archive_key(prefix: str, task_id: str) -> str:
    """The object key of a task's archive. Mirrors `DataStorageDownloadArchiveManager.archiveKey`."""
    return f'{prefix}{task_id}{ARCHIVE_EXTENSION}'


class S3Source(FileSource):
    """
    Lists and downloads the objects of an S3 storage.

    A storage may be a whole bucket or a prefix inside one. The prefix is prepended on the way into S3
    and stripped on the way out, so every path here is relative to the storage root.

    The client is required: fetching credentials is an API call, so it is not made in a constructor.
    """

    def __init__(self, storage: Storage, client):
        self._bucket = storage.root
        self._prefix = storage.prefix
        self._client = client

    # ------------------------------------------------------------------ listing

    def list_files(self, paths: tuple[str, ...]) -> Iterator[SourceFile]:
        """Expands every path of the selection in turn. Duplicates are not filtered."""
        for path in paths:
            yield from self._list_path(path)

    def _list_path(self, path: str) -> Iterator[SourceFile]:
        """A folder if the path ends in a slash, otherwise a file."""
        if path.endswith('/'):
            yield from self._expand_folder(path, path)
            return
        single, unreadable = self._head_file(path)
        if single is not None:
            yield single
            return
        # Not an object, so most likely a folder written without its trailing slash.
        LOG.debug('%s is not an object, listing it as a folder', path)
        yield from self._expand_folder(path + '/', path, unreadable)

    def _expand_folder(self, prefix: str, path: str,
                       unreadable: str | None = None) -> Iterator[SourceFile]:
        """
        Lists a prefix. A prefix holding nothing yields one skipped file, so the user is told.

        `unreadable` carries the reason when a HEAD of the same path was refused.
        """
        found = False
        for source_file in self._list_folder(prefix):
            found = True
            yield source_file
        if not found:
            # `path`, not `prefix`: the two differ for a path that was tried as an object first.
            location = f's3://{self._bucket}/{self._absolute(path)}'
            LOG.warning('Nothing was found at %s', location)
            yield SourceFile(path=path.rstrip('/'), size=0, locator=self._absolute(path),
                             skip_reason=unreadable or f'nothing found at {location}')

    def _list_folder(self, path: str) -> Iterator[SourceFile]:
        """Yields every object under a prefix, recursively, paging through the listing."""
        prefix = self._absolute(path)
        paginator = self._client.get_paginator('list_objects_v2')
        for page in paginator.paginate(Bucket=self._bucket, Prefix=prefix,
                                       PaginationConfig={'PageSize': LIST_PAGE_SIZE}):
            # An empty result omits `Contents` rather than sending an empty list.
            for item in page.get('Contents', []):
                key = item['Key']
                if key.endswith('/'):
                    # A folder marker, not a file.
                    continue
                yield self._to_source_file(key, item['Size'], item.get('StorageClass'))

    def _head_file(self, path: str) -> tuple[SourceFile | None, str | None]:
        """
        Reads one object's metadata, as `(the file, why it could not be read)`.

        Both halves are `None` when there is no such object; the caller then tries the path as a
        folder. 403 is kept apart from 404 because the two mean different things to the user.
        """
        key = self._absolute(path)
        try:
            head = self._client.head_object(Bucket=self._bucket, Key=key)
        except ClientError as error:
            code = error.response.get('ResponseMetadata', {}).get('HTTPStatusCode')
            if code == 404:
                return None, None
            if code == 403:
                return None, f'cannot be read at s3://{self._bucket}/{key}: access denied'
            raise
        return self._to_source_file(key, head['ContentLength'], head.get('StorageClass'),
                                    restore=head.get('Restore')), None

    def _to_source_file(self, key: str, size: int, storage_class: str | None,
                        restore: str | None = None) -> SourceFile:
        """
        Turns one listing entry into a `SourceFile`, with a skip reason where it cannot be read.

        An object in an archived class needs one extra HEAD, the only call that reports whether a
        restored copy exists.
        """
        relative = self._relative(key)
        if storage_class not in ARCHIVED_STORAGE_CLASSES:
            return SourceFile(path=relative, size=size, locator=key)
        if restore is None:
            head = self._safe_head(key)
            restore = head.get('Restore') if head else None
        if restore is None:
            return SourceFile(path=relative, size=size, locator=key,
                              skip_reason=f'stored in {storage_class} and not restored')
        if RESTORE_IN_PROGRESS in restore:
            return SourceFile(path=relative, size=size, locator=key,
                              skip_reason=f'stored in {storage_class}, restore still in progress')
        LOG.debug('%s is in %s but has a restored copy', key, storage_class)
        return SourceFile(path=relative, size=size, locator=key)

    def _safe_head(self, key: str) -> dict | None:
        """A HEAD that answers `None` instead of raising."""
        try:
            return self._client.head_object(Bucket=self._bucket, Key=key)
        except ClientError as error:
            LOG.warning('Cannot read metadata of %s: %s', key, error)
            return None

    # ------------------------------------------------------------------ download

    def download(self, source_file: SourceFile, destination: Path, heartbeat: Heartbeat) -> None:
        """
        Streams one object into a local file.

        `get_object` and our own loop, not `download_file`: that one has no place for the heartbeat,
        so a cancelled task would keep downloading to the end of the file.
        """
        response = self._client.get_object(Bucket=self._bucket, Key=source_file.locator)
        with response['Body'] as body, destination.open('wb') as out:
            while chunk := body.read(DOWNLOAD_BLOCK_BYTES):
                out.write(chunk)
                heartbeat()

    # ------------------------------------------------------------------ paths

    def _absolute(self, path: str) -> str:
        """Storage-relative path to object key."""
        cleaned = path.lstrip('/')
        if not self._prefix:
            return cleaned
        return f'{self._prefix}/{cleaned}'

    def _relative(self, key: str) -> str:
        """Object key to storage-relative path, which is also the name inside the archive."""
        if not self._prefix:
            return key
        return key.removeprefix(f'{self._prefix}/')


class ArchiveBucket:
    """
    Uploads and deletes finished archives in the destination storage's bucket.

    One instance and one client per worker: boto3 clients are thread-safe, sessions are not. The
    client is required, for the same reason as in `S3Source`.
    """

    def __init__(self, bucket: str, part_size_bytes: int, client):
        self._bucket = bucket
        self._part_size = max(part_size_bytes, MIN_PART_SIZE_BYTES)
        self._client = client

    def upload(self, path: Path, key: str, heartbeat: Heartbeat) -> int:
        """
        Uploads a local file and returns its size in bytes, which goes into the task's result.

        A file that fits in one part is sent with a single `put_object`.
        """
        size = path.stat().st_size
        LOG.info('Uploading %s bytes to s3://%s/%s', size, self._bucket, key)
        if size <= self._part_size:
            with path.open('rb') as body:
                self._client.put_object(Bucket=self._bucket, Key=key, Body=body)
            return size
        self._upload_multipart(path, key, size, heartbeat)
        return size

    def delete(self, key: str) -> None:
        """Removes an archive. S3 answers a delete of a missing object with success."""
        LOG.info('Deleting s3://%s/%s', self._bucket, key)
        self._client.delete_object(Bucket=self._bucket, Key=key)

    def _upload_multipart(self, path: Path, key: str, size: int, heartbeat: Heartbeat) -> None:
        """
        Sends the file part by part, aborting the upload if anything goes wrong.

        The part size is raised when the configured one would need more than `MAX_PARTS`.

        `BaseException` so that a `KeyboardInterrupt` or `SystemExit` mid-upload also aborts: an
        upload that is neither completed nor aborted leaves chargeable parts in the bucket.
        """
        part_size = max(self._part_size, math.ceil(size / MAX_PARTS))
        if part_size != self._part_size:
            LOG.info('Raising the part size to %s bytes to keep %s bytes within %s parts', part_size,
                     size, MAX_PARTS)
        created = self._client.create_multipart_upload(Bucket=self._bucket, Key=key)
        upload_id = created['UploadId']
        LOG.debug('Started multipart upload %s for %s', upload_id, key)
        try:
            parts = self._send_parts(path, key, upload_id, part_size, heartbeat)
            self._client.complete_multipart_upload(
                Bucket=self._bucket, Key=key, UploadId=upload_id,
                MultipartUpload={'Parts': parts})
            LOG.debug('Completed multipart upload %s with %s parts', upload_id, len(parts))
        except BaseException:
            self._abort(key, upload_id)
            raise

    def _send_parts(self, path: Path, key: str, upload_id: str, part_size: int,
                    heartbeat: Heartbeat) -> list[dict]:
        """Reads the file block by block and sends each as a part. Part numbers start at 1."""
        parts: list[dict] = []
        number = 1
        with path.open('rb') as body:
            while chunk := body.read(part_size):
                heartbeat()
                response = self._client.upload_part(
                    Bucket=self._bucket, Key=key, UploadId=upload_id, PartNumber=number,
                    Body=chunk)
                parts.append({'ETag': response['ETag'], 'PartNumber': number})
                number += 1
        return parts

    def _abort(self, key: str, upload_id: str) -> None:
        """
        Abandons a multipart upload and removes its parts.

        Failures are logged and swallowed: this runs while another exception is on its way up.
        """
        try:
            self._client.abort_multipart_upload(Bucket=self._bucket, Key=key, UploadId=upload_id)
            LOG.info('Aborted multipart upload %s for %s', upload_id, key)
        except Exception as error:
            LOG.warning('Could not abort multipart upload %s for %s: %s. Its parts stay in the '
                        'bucket until a lifecycle rule removes incomplete uploads', upload_id, key,
                        error)
