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
Reading the selection out of an NFS storage.

An NFS storage has no API to read objects through: the files are on a server that speaks NFS, so the
share has to be mounted before anything can be read. This module mounts it, read-only, and then treats
the selection as ordinary local files.

Three things follow from that, and all three are deliberate:

  * **the share is mounted, not the storage.** Several storages may live in one export - the platform
    models the export separately, as a `FileShareMount`, for that reason - so mounting per storage
    would mount the same server repeatedly. The export is mounted once, under its own directory, and a
    storage's own folder is a path inside it;
  * **the mount is left in place.** Unmounting after every task would mean re-mounting for the next
    one, and a mount is the slow part. The container is the lifetime of the mount;
  * **every call that touches the mount has a timeout.** A file server that stops answering makes an
    NFS call block in the kernel, indefinitely by default. A worker thread stuck there cannot be
    cancelled, cannot be killed and holds its slot until the process is restarted - so `mount` and the
    liveness check both run as subprocesses with a hard limit, which is the only way to put a bound on
    them.

This covers `MountType.NFS`. SMB and Lustre shares are refused: both need their own mount arguments and
credentials, the platform derives those in `NFSHelper`, and guessing at them here would produce a
mount that silently fails or, worse, one that succeeds against the wrong path.
"""

from __future__ import annotations

import os
import re
import subprocess
import threading
from collections.abc import Iterator
from pathlib import Path

from .base import DOWNLOAD_BLOCK_BYTES, FileSource, Heartbeat, SourceFile
from ..errors import UnsupportedSource
from ..log import get_logger
from ..model import FileShare, MountType, Storage

LOG = get_logger(__name__)

# Mounting is a process-wide operation, so one lock for the whole module rather than one per instance:
# two workers given tasks on the same share at the same moment would otherwise both find it unmounted
# and both run `mount`. The second would fail, or worse, succeed and shadow the first.
_MOUNT_LOCK = threading.Lock()

# Characters allowed in the directory name derived from a share. Everything else becomes an underscore.
_UNSAFE_IN_PATH = re.compile(r'[^A-Za-z0-9._-]+')

# Mount options we always drop from whatever the platform configured, because this service only ever
# reads. `ro` is then added unconditionally: a bug in the packing code cannot damage a user's data if
# the kernel will not let it write.
_WRITE_OPTIONS = frozenset({'rw'})


class NfsSource(FileSource):
    """
    Lists and copies the files of an NFS storage, through a read-only mount of its share.

    The mount is prepared in the constructor rather than lazily, so that a share that cannot be reached
    fails the task before any part of it has been packed.
    """

    def __init__(self, storage: Storage, share: FileShare, mount_root: Path,
                 timeout_seconds: int = 30):
        if share.mount_type != MountType.NFS:
            raise UnsupportedSource(
                f'Storage {storage.name} is on a {share.mount_type} share, and this service mounts '
                f'only NFS. Mounting it needs the arguments the platform derives in NFSHelper.')
        self._storage = storage
        self._share = share
        self._timeout = timeout_seconds
        self._mount_point = mount_root / _share_directory_name(share.mount_root)
        self._root = self._mount_point / _storage_folder(storage, share)
        self._ensure_mounted()

    # ------------------------------------------------------------------ mounting

    def _ensure_mounted(self) -> None:
        """
        Makes sure the share is mounted at our mount point and is answering.

        The lock is held for the whole check-and-mount, not just the mount: the question "is it
        mounted?" and the action "mount it" have to be one step, or two workers race between them.
        """
        with _MOUNT_LOCK:
            if self._is_mounted() and self._is_alive():
                LOG.debug('Share %s is already mounted at %s', self._share.mount_root,
                          self._mount_point)
                return
            self._mount()
        if not self._root.exists():
            # The mount worked but the storage's folder is not in it. Almost always a storage whose
            # path and whose share's root disagree - worth saying plainly, because the alternative is
            # an empty archive and a task that reports success.
            raise UnsupportedSource(
                f'Storage {self._storage.name} points at {self._storage.path}, which resolves to '
                f'{self._root} under the mounted share {self._share.mount_root}, and that path does '
                f'not exist')

    def _is_mounted(self) -> bool:
        """
        Looks our mount point up in the kernel's list of mounts.

        `/proc/mounts` is a virtual file the kernel generates on read, one line per mount, with the
        mount point in the second field. Reading it is how the platform's own `NFSStorageMounter`
        answers this question, and it is the only answer that cannot be stale.

        The point is compared as a resolved absolute path, because `/proc/mounts` always holds one and
        a configured mount root may be relative or hold a symlink.
        """
        try:
            lines = Path('/proc/mounts').read_text().splitlines()
        except OSError as error:
            # Not Linux - a developer's macOS, most likely, where `/proc` does not exist. Say so
            # rather than pretending the share is not mounted, which would lead to a `mount` call whose
            # failure is far less clear.
            raise UnsupportedSource(f'Cannot read /proc/mounts ({error}); mounting NFS shares needs '
                                    f'Linux') from error
        target = str(self._mount_point.resolve())
        # `line.split()` with no argument splits on any run of whitespace, which is what the format
        # needs; `[1]` is the mount point. The guard on the length skips a line truncated by a
        # concurrent read.
        return any(len(fields) > 1 and fields[1] == target
                   for fields in (line.split() for line in lines))

    def _is_alive(self) -> bool:
        """
        Checks that a mount point already in the list still answers.

        A share whose server has gone away stays in `/proc/mounts` and looks mounted, while every read
        under it hangs. `stat` is the cheapest call that touches the filesystem, and running it as a
        subprocess - rather than calling `os.stat` - is what makes the timeout possible: a hung
        subprocess can be killed, a hung syscall in our own thread cannot.
        """
        completed = self._run(['stat', '-t', str(self._mount_point)], 'check')
        if completed is None:
            LOG.warning('Mount point %s did not answer within %s s, remounting', self._mount_point,
                        self._timeout)
            return False
        return completed.returncode == 0

    def _mount(self) -> None:
        """
        Mounts the share read-only, creating the mount point first.

        `sudo` is prepended only when we are not root already: a container normally runs as root, where
        `sudo` may not even be installed, while a developer running the service by hand needs it.
        `os.geteuid()` is the effective user id, and 0 is root.
        """
        self._mount_point.mkdir(parents=True, exist_ok=True)
        command = [] if os.geteuid() == 0 else ['sudo']
        command += ['mount', '-t', self._share.protocol]
        options = _mount_options(self._share)
        if options:
            command += ['-o', options]
        command += [self._share.mount_root, str(self._mount_point)]
        LOG.info('Mounting %s at %s', self._share.mount_root, self._mount_point)
        completed = self._run(command, 'mount')
        if completed is None:
            raise UnsupportedSource(f'Mounting {self._share.mount_root} did not finish within '
                                    f'{self._timeout} s')
        if completed.returncode != 0:
            # `mount` writes its reason to stderr and says nothing useful on stdout, so stderr is what
            # goes into the message - it is the only thing that distinguishes "no route to host" from
            # "access denied by server".
            raise UnsupportedSource(f'Mounting {self._share.mount_root} failed: '
                                    f'{completed.stderr.strip() or completed.returncode}')

    def _run(self, command: list[str], what: str) -> subprocess.CompletedProcess | None:
        """
        Runs a command with a hard time limit. Returns `None` when it ran out of time.

        `subprocess.run` with a list argument - and without `shell=True` - passes the arguments to the
        kernel as they are. Nothing is interpreted by a shell, so a storage path holding a space, a
        quote or a semicolon cannot turn into a second command. This matters: part of what goes into
        these arguments comes from the platform's database.

        `capture_output=True` collects stdout and stderr instead of letting them into our own log, and
        `text=True` decodes them to `str` rather than leaving `bytes`.
        """
        try:
            return subprocess.run(command, capture_output=True, text=True, timeout=self._timeout,
                                  check=False)
        except subprocess.TimeoutExpired:
            LOG.warning('Command %s (%s) timed out after %s s', command[0], what, self._timeout)
            return None
        except OSError as error:
            raise UnsupportedSource(f'Cannot run {command[0]}: {error}') from error

    # ------------------------------------------------------------------ listing

    def list_files(self, paths: tuple[str, ...]) -> Iterator[SourceFile]:
        """
        Expands every path of the selection.

        **A path that is not there is reported rather than dropped.** The user ticked it, so its
        absence from the archive is something they have to be told about: it comes back as a
        `SourceFile` with a `skip_reason` naming the place that was looked at, which puts it in the
        archive's note beside the files that could not be read. A warning in the log is not enough -
        nobody reading the archive sees it.
        """
        for path in paths:
            # `/` is overloaded on `Path` to join path segments, so `self._root / 'a/b'` is the same
            # path as `self._root / 'a' / 'b'`. Cleaner than string concatenation, and it uses the
            # platform's separator.
            target = self._root / path.strip('/')
            if not _is_inside(target, self._root):
                # A path with `..` in it would otherwise read files outside the storage - a storage the
                # user has rights to is not a licence to read its neighbours. The API builds these
                # paths from a GUI selection, so this cannot happen through the UI, but a task created
                # through the REST API directly is not bound by that.
                raise UnsupportedSource(f'Path {path} leads outside storage {self._storage.name}')
            if target.is_dir():
                yield from self._list_folder(target)
            elif target.is_file():
                yield self._to_source_file(target)
            else:
                LOG.warning('Path %s of storage %s does not exist', path, self._storage.name)
                yield SourceFile(path=path.strip('/'), size=0, locator=str(target),
                                 skip_reason=f'not found at {target}')

    def _list_folder(self, folder: Path) -> Iterator[SourceFile]:
        """
        Walks a folder recursively.

        `rglob('*')` yields every entry at any depth. Directories are filtered out rather than
        followed, because `rglob` descends into them itself; a symlink to a directory is not followed
        at all, which keeps a cycle from turning the walk into an infinite one.
        """
        for entry in folder.rglob('*'):
            if entry.is_symlink():
                LOG.debug('Skipping symlink %s', entry)
                continue
            if entry.is_file():
                yield self._to_source_file(entry)

    def _to_source_file(self, entry: Path) -> SourceFile:
        """
        One local file as a `SourceFile`.

        `relative_to` gives the path inside the storage, and `as_posix()` forces forward slashes: ZIP
        entry names are defined to use them, and a backslash in an entry name is what makes an archive
        unpack into one file with slashes in its name on some tools.
        """
        return SourceFile(path=entry.relative_to(self._root).as_posix(),
                          size=entry.stat().st_size,
                          locator=str(entry))

    # ------------------------------------------------------------------ download

    def download(self, source_file: SourceFile, destination: Path, heartbeat: Heartbeat) -> None:
        """
        Copies a file off the share.

        `shutil.copyfileobj` would be shorter but has no way in for a heartbeat, so the loop is written
        out - the same shape as the S3 download, for the same reason.

        The copy is to local disk and not straight into the ZIP stream. It costs a write and a read of
        every byte, and it buys the ability to abandon a file that failed halfway without having already
        put part of it into the archive.
        """
        with open(source_file.locator, 'rb') as source, destination.open('wb') as out:
            while chunk := source.read(DOWNLOAD_BLOCK_BYTES):
                out.write(chunk)
                heartbeat()

    def close(self) -> None:
        """
        Nothing to release: the mount outlives the task on purpose - see the module docstring.

        The override exists only to say so. An empty inherited `close` on a source that holds a mount
        reads as an omission, and the next person to see it would add an `umount` here.
        """


def _mount_options(share: FileShare) -> str:
    """
    Builds the `-o` argument: whatever the platform configured for the share, minus anything that would
    make the mount writable, plus `ro`.

    Order is kept, duplicates are dropped, and `ro` goes last so that it wins over anything equivalent
    left in the configured list - mount options are applied left to right.
    """
    configured = [option.strip() for option in (share.mount_options or '').split(',')]
    kept = [option for option in configured if option and option not in _WRITE_OPTIONS]
    if 'ro' in kept:
        kept.remove('ro')
    return ','.join(kept + ['ro'])


def _share_directory_name(mount_root: str) -> str:
    """
    Turns `10.0.0.1:/export/data` into `10.0.0.1_export_data`, a single directory name.

    This does *not* reproduce `NFSHelper.normalizeMountPath`, and does not need to: that method builds
    the mount point a *job container* sees, which users and other services refer to, so its exact shape
    is part of the platform's contract. The directory here is private to this container - nothing
    outside the process ever names it - so the only requirements are that it is a valid single path
    segment and that the same share always maps to the same name.
    """
    return _UNSAFE_IN_PATH.sub('_', mount_root).strip('_')


def _storage_folder(storage: Storage, share: FileShare) -> str:
    """
    The storage's own folder inside its share: its path with the share's root taken off the front.

    For `10.0.0.1:/export` holding storage `10.0.0.1:/export/project-a`, that is `project-a`. A storage
    that *is* the whole share yields an empty string, and joining an empty segment onto a `Path`
    changes nothing - so the root case needs no branch.

    The platform solves the same problem with a regular expression over the storage path, in
    `NFSHelper.getNfsRootPath`, because the code there does not always have the share at hand. This
    service always does: the share is loaded by id from the storage, two lines earlier.
    """
    relative = storage.path.removeprefix(share.mount_root.rstrip('/')).strip('/')
    if relative == storage.path.strip('/') and relative:
        # The prefix did not match. Rather than guess, say so: the alternative is mounting the right
        # share and then archiving the wrong folder of it.
        raise UnsupportedSource(f'Storage path {storage.path} does not start with the root of its '
                                f'share {share.mount_root}')
    return relative


def _is_inside(candidate: Path, root: Path) -> bool:
    """
    Whether a path stays within a root once `..` and symlinks are resolved.

    `is_relative_to` compares the two as text, so it has to be given already-resolved paths - which is
    why both sides go through `resolve()`. `strict=False` lets a path that does not exist be resolved
    too: the answer for a missing file should be "outside" or "inside", not an exception.
    """
    return candidate.resolve(strict=False).is_relative_to(root.resolve(strict=False))
