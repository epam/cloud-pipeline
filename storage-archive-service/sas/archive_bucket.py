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
Where finished archives are written, and removed from.

The destination is the platform data storage named by `destinationStorageId` in
`storage.download.archive.config`, and this class works on the bucket behind it. A storage rather than a
bare bucket, so that the API can sign the download link through the machinery it already has for every
other storage - and so that this service reaches it the same way it reaches the storage being archived:
through the cloud region recorded against it and the role that region names. `sas/aws.py` holds that
part; here the client simply arrives built.

The object key is `archives/<task id>.zip`, derived from the task and not chosen here. That is what
lets anything - the janitor, an operator, the API signing a link - find or delete the archive of a task
knowing only the task. `DataStorageDownloadArchiveManager.archiveKey` is the Java side of the same
rule, and the two have to agree.

The upload is written out part by part rather than handed to `boto3`'s `upload_file`. Two reasons, and
the second is the important one:

  * `upload_file` runs its own thread pool and offers no way into its loop, so a cancelled task would
    keep uploading until the archive ended;
  * a multipart upload that is neither completed nor aborted leaves its parts in the bucket, invisible
    to a listing and chargeable until a lifecycle rule removes them. Aborting needs the upload id, and
    `upload_file` never exposes it.
"""

from __future__ import annotations

import math
from pathlib import Path

from .log import get_logger
from .sources.base import Heartbeat

LOG = get_logger(__name__)

ARCHIVE_EXTENSION = '.zip'

# S3 accepts at most 10000 parts in one multipart upload, and at least 5 MiB per part except for the
# last. Both are limits of the service, not choices.
MAX_PARTS = 10000
MIN_PART_SIZE_BYTES = 5 * 1024 * 1024


def absolute_key(storage_prefix: str, relative_key: str) -> str:
    """
    The key inside the bucket: the storage's own folder, then the storage-relative key.

    The destination is a data storage of the platform, and a storage may be a folder inside a bucket
    rather than a whole one. The API signs storage-relative paths and lets the provider prepend that
    folder; this service talks to S3 directly, so it has to prepend it itself.
    """
    return f'{storage_prefix}/{relative_key}' if storage_prefix else relative_key


def archive_key(prefix: str, task_id: str) -> str:
    """
    The object key of a task's archive. Mirrors `DataStorageDownloadArchiveManager.archiveKey`.

    The prefix is passed in rather than read here, because it comes from the preference and may change
    while the service runs. Normalising it is `config.normalise_prefix`'s job, done once when the
    preference is parsed.
    """
    return f'{prefix}{task_id}{ARCHIVE_EXTENSION}'


class ArchiveBucket:
    """
    Uploads and deletes archives.

    One instance per worker, and one client with it. boto3 *clients* are documented as thread-safe,
    while sessions and resources are not; building a client per worker sidesteps the question entirely
    for the price of one construction per task. The assumed session behind it is per worker too, which
    is what lets each task write through the role of its own destination storage.

    The client is a required argument, as it is for `S3Source` and for the same reason: fetching
    credentials is an API call, and a constructor that makes one cannot be read or tested. Its callers
    are `worker._resolve_destination` and the janitor's bucket factory.
    """

    def __init__(self, bucket: str, part_size_bytes: int, client):
        self._bucket = bucket
        self._part_size = max(part_size_bytes, MIN_PART_SIZE_BYTES)
        self._client = client

    def upload(self, path: Path, key: str, heartbeat: Heartbeat) -> int:
        """
        Uploads a local file and returns its size in bytes.

        The size is returned rather than looked up again by the caller because it is what goes into the
        task's result, and reading it here means reading it from the file we actually sent.

        A file that fits in one part is sent with a single `put_object`: a multipart upload of one part
        would work, but it is three calls instead of one and leaves an upload to abort if the middle
        one fails.
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
        """
        Removes an archive.

        Called on three paths - a cancelled task, a failed report, an expired archive - and all three
        may run against a key that is not there. S3 answers a delete of a missing object with success,
        so no existence check is needed and none is made.
        """
        LOG.info('Deleting s3://%s/%s', self._bucket, key)
        self._client.delete_object(Bucket=self._bucket, Key=key)

    def _upload_multipart(self, path: Path, key: str, size: int, heartbeat: Heartbeat) -> None:
        """
        Sends the file as a sequence of parts, aborting the upload if anything goes wrong.

        The part size is raised when the configured one would need more than 10000 parts, which is the
        alternative to failing an archive that is merely large: with the default 8 MiB the limit falls
        at 80 GiB, and the thresholds allow 100 GiB by default. `math.ceil` on the division gives the
        smallest size that fits.

        `except BaseException` is deliberately wider than `except Exception`. It also catches
        `KeyboardInterrupt` and `SystemExit`, which do not derive from `Exception` - so a Ctrl-C or a
        container being asked to stop mid-upload still aborts the upload instead of leaving its parts
        behind. The clause re-raises immediately, which is the only form in which that width is
        defensible: it changes nothing about the control flow and only adds the cleanup.
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
        """
        Reads the file block by block and sends each as a part.

        Part numbers start at 1 - zero is not a valid part number - and the completion call needs every
        part's number together with the ETag S3 answered with, in order. `enumerate(..., start=1)`
        would be the usual way to number a loop, but the loop here is driven by reads rather than by a
        sequence, so the counter is kept by hand.
        """
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

        Failures are logged and swallowed: this runs while another exception is on its way up, and
        raising a second one here would replace the real reason the task failed with "could not abort an
        upload". The parts that are left behind cost a little storage until the bucket's lifecycle rule
        clears incomplete uploads, which is the lesser of the two problems.
        """
        try:
            self._client.abort_multipart_upload(Bucket=self._bucket, Key=key, UploadId=upload_id)
            LOG.info('Aborted multipart upload %s for %s', upload_id, key)
        except Exception as error:
            LOG.warning('Could not abort multipart upload %s for %s: %s. Its parts stay in the '
                        'bucket until a lifecycle rule removes incomplete uploads', upload_id, key,
                        error)
