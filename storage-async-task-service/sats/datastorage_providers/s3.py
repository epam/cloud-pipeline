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
S3: reading the files of a storage, and writing into one.

Every client is built in `sats/cloud/aws.py` and passed in here.
"""

from __future__ import annotations

import io
import math
from collections.abc import Iterator
from pathlib import Path

from botocore.exceptions import ClientError

from .base import ARCHIVED_STORAGE_CLASSES, DOWNLOAD_BLOCK_BYTES, EntryKind, FileDestination, \
    FileSource, Heartbeat, SourceFile
from ..util.errors import CredentialsUnavailable, DestinationConflict
from ..util.log import get_logger
from ..model import Storage

LOG = get_logger(__name__)

# Keys per listing call. 1000 is the S3 maximum.
LIST_PAGE_SIZE = 1000

# What S3 answers a conditional write with when the key is already there. Both names appear: the
# status code is 412, and botocore reports the error code as `PreconditionFailed`.
PRECONDITION_FAILED_STATUS = 412
PRECONDITION_FAILED_CODE = 'PreconditionFailed'

# Calls of this service's own plumbing rather than of the object a task asked about. botocore assumes
# the storage's role inside the signing path, so one of these can fail in the middle of an S3 call.
CREDENTIAL_OPERATIONS = frozenset({'AssumeRole', 'AssumeRoleWithWebIdentity', 'GetCallerIdentity'})

# Restore header of an archived object. "true" means the restore is still running and the object
# cannot be read yet.
RESTORE_IN_PROGRESS = 'ongoing-request="true"'

ARCHIVE_EXTENSION = '.zip'

# S3 limits: at most 10000 parts per multipart upload, at least 5 MiB per part except the last.
MAX_PARTS = 10000
MIN_PART_SIZE_BYTES = 5 * 1024 * 1024


def assert_not_a_credentials_failure(error: ClientError) -> None:
    """
    Re-raises a credentials failure as one, before its 403 is read as "you may not read that file".

    botocore assumes the storage's role inside the signing path, so a rejected `AssumeRole` comes out
    of whichever S3 call triggered it - carrying status 403, which is exactly what an object the
    caller may not read answers. The two are told apart by the operation that actually failed.

    Without this an expired node role tells every user "access denied" on their own files, and the
    real cause - that this service has no usable credentials - appears in nobody's message.
    """
    if error.operation_name in CREDENTIAL_OPERATIONS:
        raise CredentialsUnavailable(
            f'This service could not obtain credentials for the storage: {error}')


def absolute_key(storage_prefix: str, relative_key: str) -> str:
    """The key inside the bucket: the storage's own folder, then the storage-relative key."""
    return f'{storage_prefix}/{relative_key}' if storage_prefix else relative_key


def archive_key(prefix: str, task_id: str) -> str:
    """The object key of a task's archive. Mirrors `DownloadArchiveTaskProcessor.archivePath`."""
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
            assert_not_a_credentials_failure(error)
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
        """
        A HEAD that answers `None` instead of raising.

        A credentials failure is the exception: swallowed here it would come back as "stored in
        GLACIER and not restored", which names the wrong cause entirely.
        """
        try:
            return self._client.head_object(Bucket=self._bucket, Key=key)
        except ClientError as error:
            assert_not_a_credentials_failure(error)
            LOG.warning('Cannot read metadata of %s: %s', key, error)
            return None

    def stat(self, path: str) -> SourceFile | None:
        """
        One object by its storage-relative path, or `None` when there is no such object.

        A prefix with objects under it answers `None` as well: the caller asked for a file, and this
        is the method that tells it there is none - `list_files` would expand the folder instead.
        """
        found, unreadable = self._head_file(path)
        if found is not None:
            return found
        if unreadable:
            # It is there and we may not read it. Named, so the task's message says which.
            return SourceFile(path=path, size=0, locator=self._absolute(path),
                              skip_reason=unreadable)
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

    def open_stream(self, source_file: SourceFile) -> RangedObjectReader:
        """A file-like object over the object, each read being one ranged `GetObject`."""
        return RangedObjectReader(self._client, self._bucket, source_file.locator, source_file.size)

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


class RangedObjectReader(io.RawIOBase):
    """
    A seekable, read-only file over an S3 object: one ranged `GetObject` per read.

    What lets `zipfile` open an archive that stays in the bucket. A zip's index is at the end of the
    file, so a few seeks answer what the archive holds without fetching it: measured on a test
    archive, `zipfile` read 437 bytes of 1322 in three requests, and the cost does not grow with the
    archive - a 500 GB zip answers in the same three.

    `io.RawIOBase` rather than a file object written by hand, so `read`, `readall` and the context
    manager come from the standard library and only the three primitives below are ours.

    **Not for reading a whole file.** One read is one request, so a download through this would cost
    a request per block; `S3Source.download` is the method for that.
    """

    def __init__(self, client, bucket: str, key: str, size: int):
        super().__init__()
        self._client = client
        self._bucket = bucket
        self._key = key
        self._size = size
        self._position = 0
        # How many requests this object has cost, so a test can assert that probing an archive is
        # cheap rather than merely correct.
        self.requests = 0

    def readable(self) -> bool:
        return True

    def seekable(self) -> bool:
        return True

    def tell(self) -> int:
        return self._position

    def seek(self, offset: int, whence: int = io.SEEK_SET) -> int:
        match whence:
            case io.SEEK_SET:
                target = offset
            case io.SEEK_CUR:
                target = self._position + offset
            case io.SEEK_END:
                target = self._size + offset
            case _:
                raise ValueError(f'Unsupported whence {whence} for an S3 object reader')
        # Clamped at zero and not at the end: seeking past the end of a file is legal, and a read
        # there answers nothing, which is what `zipfile` expects.
        self._position = max(0, target)
        return self._position

    def readinto(self, buffer) -> int:
        """
        Fills the buffer from the current position with one ranged request.

        The loop drains the response rather than trusting one `read`: a short read is legal for a
        stream, and callers like `zipfile` ask for an exact number of bytes.
        """
        wanted = len(buffer)
        if wanted == 0 or self._position >= self._size:
            return 0
        last = min(self._position + wanted, self._size) - 1
        self.requests += 1
        response = self._client.get_object(Bucket=self._bucket, Key=self._key,
                                           Range=f'bytes={self._position}-{last}')
        filled = 0
        with response['Body'] as body:
            while filled < wanted:
                chunk = body.read(wanted - filled)
                if not chunk:
                    break
                buffer[filled:filled + len(chunk)] = chunk
                filled += len(chunk)
        self._position += filled
        return filled


class S3Bucket:
    """
    Uploads to and deletes from one bucket, by absolute key.

    One instance and one client per worker: boto3 clients are thread-safe, sessions are not. The
    client is required, for the same reason as in `S3Source`.

    It knows nothing about what it is moving - it was the archive bucket of the download tasks first
    and is now also what an extraction writes its files through, which is why the name is the bucket
    rather than the archive.
    """

    def __init__(self, bucket: str, part_size_bytes: int, client):
        self._bucket = bucket
        self._part_size = max(part_size_bytes, MIN_PART_SIZE_BYTES)
        self._client = client

    def upload(self, path: Path, key: str, heartbeat: Heartbeat,
               refuse_existing: bool = False) -> int:
        """
        Uploads a local file and returns its size in bytes, which goes into the task's result.

        A file that fits in one part is sent with a single `put_object`.

        `refuse_existing` turns the write into a conditional one - `IfNoneMatch: *`, which S3 answers
        with 412 when the key is taken. The condition is the write itself rather than a HEAD before
        it, so a key created in between is refused too, which is the whole promise of the flag.
        """
        size = path.stat().st_size
        LOG.info('Uploading %s bytes to s3://%s/%s', size, self._bucket, key)
        guard = {'IfNoneMatch': '*'} if refuse_existing else {}
        if size <= self._part_size:
            with path.open('rb') as body:
                self._refusing_a_taken_key(
                    key, lambda: self._client.put_object(Bucket=self._bucket, Key=key, Body=body,
                                                         **guard))
            return size
        self._upload_multipart(path, key, size, heartbeat, guard)
        return size

    def exists(self, key: str) -> bool:
        """
        Whether the key is an object. A refusal is raised rather than read as absence.

        The credentials check is the same one `S3Source` makes: this is the call the collision pass
        goes through, so without it an expired role reaches the user as an internal error of this
        service rather than as "no usable credentials".
        """
        try:
            self._client.head_object(Bucket=self._bucket, Key=key)
            return True
        except ClientError as error:
            assert_not_a_credentials_failure(error)
            if error.response.get('ResponseMetadata', {}).get('HTTPStatusCode') == 404:
                return False
            raise

    def has_children(self, key: str) -> bool:
        """
        Whether anything lies under the key as a folder would.

        One listing of at most one key: the question is existence, not content.
        """
        response = self._client.list_objects_v2(Bucket=self._bucket, Prefix=f'{key}/', MaxKeys=1)
        return bool(response.get('Contents'))

    def delete(self, key: str) -> None:
        """Removes an object. S3 answers a delete of a missing object with success."""
        LOG.info('Deleting s3://%s/%s', self._bucket, key)
        self._client.delete_object(Bucket=self._bucket, Key=key)

    def _upload_multipart(self, path: Path, key: str, size: int, heartbeat: Heartbeat,
                          guard: dict) -> None:
        """
        Sends the file part by part, aborting the upload if anything goes wrong.

        The part size is raised when the configured one would need more than `MAX_PARTS`.

        The conditional guard goes on the completion and not on the parts: parts are not objects, so
        nothing is visible in the bucket until the upload completes, and that is where the key is
        taken or not.

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
            self._refusing_a_taken_key(key, lambda: self._client.complete_multipart_upload(
                Bucket=self._bucket, Key=key, UploadId=upload_id,
                MultipartUpload={'Parts': parts}, **guard))
            LOG.debug('Completed multipart upload %s with %s parts', upload_id, len(parts))
        except BaseException:
            self._abort(key, upload_id)
            raise

    def _refusing_a_taken_key(self, key: str, write):
        """
        Runs a write and turns S3's 412 into the refusal the caller asked for.

        Without the translation the failure arrives as a `ClientError` and reads to the user as an
        internal error of this service rather than as "that file is already there".
        """
        try:
            return write()
        except ClientError as error:
            response = error.response
            status = response.get('ResponseMetadata', {}).get('HTTPStatusCode')
            code = response.get('Error', {}).get('Code')
            if status == PRECONDITION_FAILED_STATUS or code == PRECONDITION_FAILED_CODE:
                raise DestinationConflict(
                    f's3://{self._bucket}/{key} already exists') from error
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


class S3Destination(FileDestination):
    """
    Writes files into an S3 storage, addressed by storage-relative keys.

    The storage's own folder is added here and nowhere else, which is the same split as in
    `S3Source`: everything outside this class speaks in paths relative to the storage root, so a
    storage that is a prefix of a bucket behaves like one that is a whole bucket.
    """

    def __init__(self, storage: Storage, part_size_bytes: int, client):
        self._storage = storage
        self._prefix = storage.prefix
        self._bucket = S3Bucket(storage.root, part_size_bytes, client)

    def assert_writable(self, prefix: str) -> None:
        """
        Nothing to check, and that is not an omission.

        An object storage has no folders: a key with slashes in it is one key, so there is no folder
        to find missing and nothing to create. Whether this service may write at all is answered by
        the first upload, with the region's role - and whether the *caller* may was answered by the
        API when the task was created.
        """

    def kind(self, key: str) -> EntryKind:
        """
        What lies at the key: an object, something underneath it, or nothing.

        Two calls at most, and the second only when the first found no object - so the common case
        of an empty destination costs one HEAD per entry.
        """
        absolute = absolute_key(self._prefix, key)
        if self._bucket.exists(absolute):
            return EntryKind.FILE
        if self._bucket.has_children(absolute):
            return EntryKind.FOLDER
        return EntryKind.MISSING

    def upload(self, local_path: Path, key: str, heartbeat: Heartbeat,
               overwrite: bool = False) -> int:
        return self._bucket.upload(local_path, absolute_key(self._prefix, key), heartbeat,
                                   refuse_existing=not overwrite)

    def create_folder(self, key: str) -> None:
        """
        Nothing is created, and the empty folder is logged as skipped.

        An object storage has no folders, so an empty one cannot be represented: the alternative - a
        zero-length object whose key ends in a slash - is a convention some tools draw as a folder
        and others as a strange file, and this service does not invent storage content. Logged
        rather than reported, because an archive may hold thousands of them and a count would not
        tell a user what to do about it.
        """
        LOG.info('The archive holds the empty folder %s, which an object storage cannot represent: '
                 'skipping it', key)

    def delete(self, key: str) -> None:
        self._bucket.delete(absolute_key(self._prefix, key))
