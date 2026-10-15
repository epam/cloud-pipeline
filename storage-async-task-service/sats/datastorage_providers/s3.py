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
Reading the selection out of an S3 storage.

Who the client is and which region it talks to is `sats/cloud/aws.py`'s subject, not this module's:
both follow from the storage's cloud region, and the same rules apply to the bucket the finished
archive is uploaded to. Everything here takes a client as given and is about listing and reading
objects. The region a download link is signed for is the destination storage's own, settled on the API
side, and has nothing to do with these calls.
"""

from __future__ import annotations

from collections.abc import Iterator
from pathlib import Path

from botocore.exceptions import ClientError

from .base import ARCHIVED_STORAGE_CLASSES, DOWNLOAD_BLOCK_BYTES, FileSource, Heartbeat, SourceFile
from ..log import get_logger
from ..model import Storage

# Two dots mean "one package up": `..log` is `sats/log.py`, read from `sats/datastorage_providers/s3.py`.

LOG = get_logger(__name__)

# How many keys one listing call returns. 1000 is the maximum S3 allows and also its default; naming it
# makes the paging below readable rather than magic.
LIST_PAGE_SIZE = 1000

# A restored copy of an archived object carries this header. `ongoing-request="true"` means the restore
# is still running and the object is not readable yet; `ongoing-request="false"` means a temporary copy
# exists and `get_object` will work.
RESTORE_IN_PROGRESS = 'ongoing-request="true"'


class S3Source(FileSource):
    """
    Lists and downloads the objects of an S3 storage.

    A platform S3 storage may be a whole bucket or a prefix inside one - `bucket/team/project`. The two
    are not distinguished anywhere below: `Storage.prefix` is prepended on the way into S3 and stripped
    on the way back out, so a path in a task and a path in the archive are both relative to the storage
    root, which is what the user saw in the GUI.

    The client is a required argument and there is no default for it. It could as easily be built here
    from the storage, and deliberately is not: credentials are fetched from the API, and a constructor
    that quietly makes an authenticated call is one that a test cannot run and a reader cannot see.
    `open_source` builds it, and a test passes a stub.
    """

    def __init__(self, storage: Storage, client):
        self._bucket = storage.root
        self._prefix = storage.prefix
        self._client = client

    # ------------------------------------------------------------------ listing

    def list_files(self, paths: tuple[str, ...]) -> Iterator[SourceFile]:
        """
        Expands every path of the selection in turn.

        `yield from` delegates to another generator: the files of each path are yielded one by one as
        the caller consumes them, and nothing is collected in between.

        Duplicates are not filtered. A selection that names both a folder and a file inside it would
        put that file in the archive twice, under the same name - but the API builds the selection from
        what the user ticked in the GUI, which cannot produce that overlap, and de-duplicating would
        mean holding every key of the listing in memory, which is exactly what the laziness above
        exists to avoid.
        """
        for path in paths:
            yield from self._list_path(path)

    def _list_path(self, path: str) -> Iterator[SourceFile]:
        """
        A single entry of the selection: a folder if it ends in a slash, otherwise a file.

        **A path that yields nothing is reported rather than dropped.** The user ticked it, so its
        absence from the archive is something they have to be told about: it comes back as a
        `SourceFile` with a `skip_reason`, which puts it in the archive's note beside the files that
        could not be read. Before this, a selection naming a file that is not in the bucket produced an
        archive quietly missing it.
        """
        if path.endswith('/'):
            yield from self._expand_folder(path, path)
            return
        single, unreadable = self._head_file(path)
        if single is not None:
            yield single
            return
        # Not an object. Most likely a folder written without its trailing slash - the API normalises
        # this, but a task created through the REST API directly may not have. Listing it as a folder
        # costs one call and settles which of the two it is.
        LOG.debug('%s is not an object, listing it as a folder', path)
        yield from self._expand_folder(path + '/', path, unreadable)

    def _expand_folder(self, prefix: str, path: str,
                       unreadable: str | None = None) -> Iterator[SourceFile]:
        """
        Lists a prefix, and says so when it holds nothing.

        A missing prefix and an empty one are the same thing in S3 - there are no directories, only
        keys - so one wording covers both, and it names the place that was looked at because that is
        what the person reading the note needs in order to go and check.

        `unreadable` is passed in when a HEAD of the same path was refused rather than answered: that
        is a better reason than "nothing found", and it is the honest one.
        """
        found = False
        for source_file in self._list_folder(prefix):
            found = True
            yield source_file
        if not found:
            # The place named is `path` and not `prefix`: the two differ for a path that was tried as
            # an object first, and a message about `notes.txt/` would name a location that was never
            # asked for.
            location = f's3://{self._bucket}/{self._absolute(path)}'
            LOG.warning('Nothing was found at %s', location)
            yield SourceFile(path=path.rstrip('/'), size=0, locator=self._absolute(path),
                             skip_reason=unreadable or f'nothing found at {location}')

    def _list_folder(self, path: str) -> Iterator[SourceFile]:
        """
        Yields every object under a prefix, recursively, paging through the listing.

        `get_paginator` wraps the continuation-token loop S3 requires for a listing of more than a
        thousand keys. Each page is a dictionary; `page.get('Contents', [])` matters because an empty
        result omits the key altogether rather than sending an empty list.
        """
        prefix = self._absolute(path)
        paginator = self._client.get_paginator('list_objects_v2')
        for page in paginator.paginate(Bucket=self._bucket, Prefix=prefix,
                                       PaginationConfig={'PageSize': LIST_PAGE_SIZE}):
            for item in page.get('Contents', []):
                key = item['Key']
                if key.endswith('/'):
                    # A folder marker: a zero-byte object a console creates so that an empty folder
                    # shows up. It is not a file, and adding it to the archive would create an entry
                    # with an empty name.
                    continue
                yield self._to_source_file(key, item['Size'], item.get('StorageClass'))

    def _head_file(self, path: str) -> tuple[SourceFile | None, str | None]:
        """
        Reads one object's metadata, as `(the file, why it could not be read)`.

        Both halves are `None` when there is simply no such object, which is an answer rather than a
        failure: the caller then tries the path as a folder. A tuple rather than one value because the
        two refusals mean different things to the person who asked for the archive, and saying "not
        found" for a file that is there but forbidden would send them looking in the wrong place.

        `except ClientError` and then a look at the error code: botocore reports every S3 refusal as
        the same exception type and puts the detail in `error.response`. `404` is "no such key". `403`
        is "no permission for this key" - with the role this service assumes, listing is granted
        alongside reading, so a 403 here really is a denial rather than S3 hiding a missing key from a
        client that may not list. Anything else is a real failure and is left to propagate.
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
        Turns one listing entry into a `SourceFile`, deciding whether it can be read at all.

        An object in Glacier or Deep Archive cannot be downloaded directly. It may still have a
        temporary restored copy, though, and a user who asked for a restore and then for an archive
        expects that copy to be used - so a listing entry in an archived class is checked once more with
        a HEAD, which is the only call that reports the restore state. The extra call happens per
        archived object and not per object, which keeps a normal selection at one call per page.
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
        """A HEAD that answers `None` instead of raising. Used where a failure only costs us detail."""
        try:
            return self._client.head_object(Bucket=self._bucket, Key=key)
        except ClientError as error:
            LOG.warning('Cannot read metadata of %s: %s', key, error)
            return None

    # ------------------------------------------------------------------ download

    def download(self, source_file: SourceFile, destination: Path, heartbeat: Heartbeat) -> None:
        """
        Streams one object into a local file.

        Written against `get_object` rather than `download_file`, which would be shorter, because
        `download_file` runs its own thread pool and offers no way into its copy loop - so a cancelled
        task would go on downloading until the file ended. Here the loop is ours and a heartbeat sits
        inside it.

        `while chunk := body.read(n):` is an assignment expression, the "walrus". It reads the next
        block, binds it to `chunk`, and uses its truth value as the loop condition - an empty block
        means end of stream and is falsy. Without it the same loop needs a read before the loop and
        another at its end.

        `with` on both the response body and the output file closes them on the way out, including when
        the heartbeat raises - which leaves a partial file behind, deleted by the worker.
        """
        response = self._client.get_object(Bucket=self._bucket, Key=source_file.locator)
        with response['Body'] as body, destination.open('wb') as out:
            while chunk := body.read(DOWNLOAD_BLOCK_BYTES):
                out.write(chunk)
                heartbeat()

    # ------------------------------------------------------------------ paths

    def _absolute(self, path: str) -> str:
        """Storage-relative path to object key: prepend the storage's own prefix, if it has one."""
        cleaned = path.lstrip('/')
        if not self._prefix:
            return cleaned
        return f'{self._prefix}/{cleaned}'

    def _relative(self, key: str) -> str:
        """
        Object key back to storage-relative path, which is also the name inside the archive.

        `removeprefix` (Python 3.9 and later) returns the string unchanged when the prefix is not there,
        so a key that somehow fell outside the storage prefix keeps its full name rather than losing a
        few characters to a `[len(p):]` slice.
        """
        if not self._prefix:
            return key
        return key.removeprefix(f'{self._prefix}/')
