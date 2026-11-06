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
What every file source has to provide.

The worker knows only this interface, so a new storage type means a module next to `s3.py` and a
branch in `open_source`, and nothing else.
"""

from __future__ import annotations

import abc
from collections.abc import Callable, Iterator
from dataclasses import dataclass
from pathlib import Path

# Storage classes whose objects cannot be read until someone restores them. Such a file is skipped,
# not failed: one archived file should not lose a selection of a thousand.
ARCHIVED_STORAGE_CLASSES = frozenset({'GLACIER', 'DEEP_ARCHIVE'})

# How much of a file is copied between two heartbeats, so also how fast a cancellation is noticed
# inside one file. Small on purpose.
DOWNLOAD_BLOCK_BYTES = 1024 * 1024

# What a source calls between chunks to give the worker a chance to stop. May raise `StopRequested`.
Heartbeat = Callable[[], None]


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
    def download(self, source_file: SourceFile, destination: Path, heartbeat: Heartbeat) -> None:
        """
        Copies one file to a local path, calling `heartbeat()` between blocks.

        The heartbeat is required: a single file may be tens of gigabytes. It may raise
        `StopRequested`, and the source must let that through.
        """

    def close(self) -> None:
        """Releases whatever the source holds. The default does nothing, which suits a bucket."""

    def __enter__(self) -> FileSource:
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        # Returning `None` lets the exception continue; a true value would swallow a cancellation.
        self.close()
