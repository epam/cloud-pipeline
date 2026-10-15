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
What every file source has to provide, and nothing more.

The worker knows only this interface. Adding a storage type means adding a module next to `s3.py` and
a branch in `open_source`; nothing in `worker/archive_preparation_worker.py` has to change, which is
the point of the
abstraction - the packing, the thresholds, the cancellation handling and the cleanup are identical
whatever the files were read from.
"""

from __future__ import annotations

import abc
from collections.abc import Callable, Iterator
from dataclasses import dataclass
from pathlib import Path

# Storage classes whose objects are not readable until someone restores them. An object in one of these
# is skipped rather than failed: a selection of a thousand files should not be lost because one of them
# was archived last month.
ARCHIVED_STORAGE_CLASSES = frozenset({'GLACIER', 'DEEP_ARCHIVE'})

# How much of a file is copied between two heartbeats. It is the granularity at which a worker notices
# that its task was cancelled, so it is small: 1 MiB takes well under a second on any link worth using,
# while a part-sized block of 8 MiB on a slow link would keep a cancelled worker busy for a minute.
DOWNLOAD_BLOCK_BYTES = 1024 * 1024

# A type alias. `Heartbeat` is a function taking nothing and returning nothing, which a source calls
# between chunks of a download to give the worker a chance to abandon the work - see
# `errors.StopRequested`. Naming the type keeps the signatures readable, and makes it obvious that a
# source is never expected to do anything with the result.
Heartbeat = Callable[[], None]


@dataclass(frozen=True, slots=True)
class SourceFile:
    """One file of the selection, as the listing found it."""

    # Where the file goes inside the archive. Relative to the storage root, with forward slashes, no
    # leading slash - so a user unpacking the archive gets the same tree they saw in the GUI.
    path: str
    # Size in bytes, as reported by the listing. Counted against `maxSizeBytes` before anything is
    # downloaded, which is why it must come from the listing and not from the download.
    size: int
    # How this file's own source finds it again: an object key for a bucket, an absolute local path for
    # a mounted share. Opaque to the worker.
    locator: str
    # Set when the file cannot be read at all. Such a file is listed, reported and named in the
    # archive's README, but never downloaded. `None` means a normal file.
    skip_reason: str | None = None


class FileSource(abc.ABC):
    """
    A place files are read from.

    `abc.ABC` plus `@abc.abstractmethod` is Python's abstract class: instantiating a subclass that left
    a method unimplemented raises at construction time. It is a convention with teeth, but a late one -
    nothing checks it when the file is imported, only when an object is made.

    The two context-manager methods make a source usable in a `with` block:

        with open_source(...) as source:
            ...

    `with` is Python's try-with-resources. `__enter__` runs on the way in and its result is bound to
    the name after `as`; `__exit__` runs on the way out, whether the block finished, returned or
    raised. That guarantee is what the worker relies on to release a source while unwinding from a
    cancellation.
    """

    @abc.abstractmethod
    def list_files(self, paths: tuple[str, ...]) -> Iterator[SourceFile]:
        """
        Walks the selection and yields its files, one at a time, folders expanded.

        An `Iterator` and not a list, and this is deliberate. A selection may hold hundreds of
        thousands of files, and the caller has to be able to stop early - a threshold is exceeded, the
        task is cancelled - without having paid for the whole listing first. A function containing
        `yield` is a generator: calling it runs none of the body, and each loop step runs it up to the
        next `yield`. Laziness is the default here, not an optimisation.

        A path ending in a slash is a folder and expands recursively; anything else is a single file.
        Paths are relative to the storage root, as the task carries them.

        A path that does not exist is the caller's problem to notice; a source yields what it finds.
        """

    @abc.abstractmethod
    def download(self, source_file: SourceFile, destination: Path, heartbeat: Heartbeat) -> None:
        """
        Copies one file to a local path, calling `heartbeat()` between blocks.

        The heartbeat is not optional and not advisory: a single file may be tens of gigabytes, and
        without a call inside the copy loop a cancelled task would keep downloading until the file
        ended. The call may raise `StopRequested`, and the source must let it through - the worker
        deletes the partial file on its way out.
        """

    def close(self) -> None:
        """
        Releases whatever the source holds. The default does nothing, which suits a bucket.

        Not abstract on purpose: a source with nothing to release should not have to say so.
        """

    def __enter__(self) -> FileSource:
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        """
        Called on the way out of a `with` block. The three arguments describe the exception that is
        unwinding, or are all `None` for a normal exit.

        Returning `None` - as this does implicitly - lets the exception continue. Returning a true
        value would swallow it, which is almost never what anyone wants and would hide a cancellation
        from the worker here.
        """
        self.close()
