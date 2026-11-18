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
What every file source and every file destination has to provide.

The workers know only these two interfaces, so a new storage type means a module next to `s3.py` and
a branch in `open_source` and `open_destination`.

Reading and writing are separate interfaces because they are not equally safe. A source is opened
for every task, a destination only for a task that writes. On a share that difference is the mount
option: a source mounts read-only.
"""

from __future__ import annotations

import abc
from collections.abc import Callable, Iterator
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from typing import BinaryIO

# Storage classes whose objects cannot be read until someone restores them. Such a file is skipped,
# not failed: one archived file should not lose a selection of a thousand.
ARCHIVED_STORAGE_CLASSES = frozenset({'GLACIER', 'DEEP_ARCHIVE'})

# How much of a file is copied between two heartbeats, so also how fast a cancellation is noticed
# inside one file. Small on purpose.
DOWNLOAD_BLOCK_BYTES = 1024 * 1024

# What a source calls between chunks to give the worker a chance to stop. May raise `StopRequested`.
Heartbeat = Callable[[], None]


class EntryKind(StrEnum):
    """
    What a destination already holds at a path.

    Three answers, not two: a file and a folder of one name is a conflict of its own. S3 allows it,
    a share does not, and tools that mount or download the storage then fail the way the share does.
    """

    MISSING = 'missing'
    FILE = 'file'
    FOLDER = 'folder'


@dataclass(frozen=True, slots=True)
class SourceFile:
    """One file of the selection, as the listing found it."""

    # Where the file goes inside the archive: relative to the storage root, no leading slash.
    path: str
    # Size from the listing, counted against `maxSizeBytes` before anything is downloaded.
    size: int
    # How this file's own source finds it again: an object key, or a local path. Opaque to the worker.
    locator: str
    # Set when the file cannot be read. Such a file is listed and named in the archive's note, but
    # never downloaded.
    skip_reason: str | None = None


class FileSource(abc.ABC):
    """
    A place files are read from.

    `__enter__` and `__exit__` make a source usable in a `with` block, which is how the worker
    releases it while unwinding from a cancellation.
    """

    @abc.abstractmethod
    def list_files(self, paths: tuple[str, ...]) -> Iterator[SourceFile]:
        """
        Walks the selection and yields its files one at a time, folders expanded.

        An iterator, not a list: a selection may hold hundreds of thousands of files and the caller
        has to be able to stop early. A path ending in a slash is a folder, anything else is a file.
        """

    @abc.abstractmethod
    def stat(self, path: str) -> SourceFile | None:
        """
        The one file at this path, or `None` when the path holds no file.

        Apart from `list_files` because a caller given one path - the archive of an unpack task -
        must be able to tell "not a file" from "a folder of that name".

        A file that exists and cannot be read comes back with a `skip_reason`, as in `list_files`.
        """

    @abc.abstractmethod
    def download(self, source_file: SourceFile, destination: Path, heartbeat: Heartbeat) -> None:
        """
        Copies one file to a local path, calling `heartbeat()` between blocks.

        The heartbeat is required: a single file may be tens of gigabytes. It may raise
        `StopRequested`, and the source must let that through.
        """

    @abc.abstractmethod
    def open_stream(self, source_file: SourceFile) -> BinaryIO:
        """
        A readable, seekable file over the file, for reading parts of it where it lies.

        What `archive_probe` is handed: an archive keeps its index in its tail, so a few hundred
        bytes answer what it holds without fetching it. The caller closes the object.
        """

    def close(self) -> None:
        """Releases whatever the source holds. The default does nothing, which suits a bucket."""

    def __enter__(self) -> FileSource:
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        # Returning `None` lets the exception continue; a true value would swallow a cancellation.
        self.close()


class FileDestination(abc.ABC):
    """
    A place files are written to, addressed by storage-relative keys.

    No permission check happens here. This service has no user identity and runs with a role of the
    storage's cloud region, so a check it invented would be a check against itself. The API
    authorized the destination folder, and nothing per entry. What a destination owes is the folder
    the caller named, and the refusals a caller cannot make for it: a collision, and a path that is
    not where it claims.
    """

    @abc.abstractmethod
    def ensure_destination_folder(self, prefix: str) -> None:
        """
        Makes sure the folder an extraction writes into is there, creating it where it is missing.

        Called once, before the first upload. A share creates it; an object storage has no folder to
        create.
        """

    @abc.abstractmethod
    def kind(self, key: str) -> EntryKind:
        """What the destination already holds at this key. The collision pass is built on it."""

    @abc.abstractmethod
    def upload(self, local_path: Path, key: str, heartbeat: Heartbeat,
               overwrite: bool = False) -> int:
        """
        Writes one local file under the key and answers its size in bytes.

        With `overwrite` false the write itself must refuse a taken key, rather than checking first:
        between a check and a write somebody else may create the file.
        """

    @abc.abstractmethod
    def create_folder(self, key: str) -> None:
        """
        Makes sure an empty folder of the archive exists in the destination.

        A folder with files in it needs nothing: writing the files creates it on both backends. On an
        object storage there is no folder to create at all, so this is the destination's decision.
        """

    @abc.abstractmethod
    def delete(self, key: str) -> None:
        """Removes one key. Deleting what is not there is not an error."""

    def close(self) -> None:
        """Releases whatever the destination holds. The default does nothing, which suits a bucket."""

    def __enter__(self) -> FileDestination:
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.close()
