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
Reading an NFS storage, and writing into one.

The share is mounted and its files are then read or written as local files. Four rules:

  * **the share is mounted, not the storage** - several storages may live in one export;
  * **the mount is left in place** - mounting is the slow part, and the container is its lifetime;
  * **every call that touches the mount has a timeout** - a dead NFS server blocks in the kernel for
    ever, and a worker thread stuck there cannot be killed. So `mount` and the liveness check run as
    subprocesses;
  * **reading and writing get separate mounts** - a source read-only, a destination read-write. The
    mode is a directory level of the mount point, because one share may be needed both ways at once.
    A share the administrator marked `ro` is never mounted writable: the task fails instead.

Only `MountType.NFS` is handled. SMB and Lustre need the arguments the platform derives in
`NFSHelper`, and guessing them could mount the wrong path.
"""

from __future__ import annotations

import hashlib
import os
import re
import subprocess
import threading
from collections.abc import Iterator
from pathlib import Path
from typing import BinaryIO

from .base import DOWNLOAD_BLOCK_BYTES, EntryKind, FileDestination, FileSource, Heartbeat, SourceFile
from ..util.errors import DestinationConflict, DestinationWriteFailed, UnsupportedSource
from ..util.log import get_logger
from ..util.paths import is_inside
from ..model import FileShare, MountType, Storage

LOG = get_logger(__name__)

# Mounting is process-wide, so one lock for the module: two workers on the same share would otherwise
# both find it unmounted and both run `mount`.
_MOUNT_LOCK = threading.Lock()

# Characters allowed in the directory name derived from a share.
_UNSAFE_IN_PATH = re.compile(r'[^A-Za-z0-9._-]+')

# Dropped from what the platform configured, so the mode this service asked for is the mode it gets.
# `ro` is the one option honoured rather than overridden - see `_ShareMount`.
_MODE_OPTIONS = frozenset({'rw', 'ro'})
_READ_ONLY_OPTION = 'ro'

# What tells a read-write mount point from a read-only one. Both modes of one share may be needed at
# once - extracting inside a single storage does that - and `mount` cannot put two modes on one
# directory.
#
# A directory level, not a suffix on the share's name. A suffix cannot be made safe: `-` and `_` are
# legal in the normalised name, so the read-only mount of `.../team-rw` and the read-write mount of
# `.../team` would land on one directory. A level cannot collide, because the name holds no slash.
_READ_ONLY_DIRECTORY = 'ro'
_WRITABLE_DIRECTORY = 'rw'

# How much of the share's digest goes into its directory name - see `_share_directory_name`. Long
# enough that two shares of one deployment cannot collide, short enough to leave the name readable.
SHARE_DIGEST_LENGTH = 12

# What separates the host from the path in the platform's NFS notation. `mount` needs both halves,
# while the platform stores the root of an EFS share as the host alone.
_HOST_DELIMITER = ':/'


class _ShareMount:
    """
    One share mounted in one mode.

    Shared by the source and the destination, so the lock, the timeouts and the liveness check exist
    once. The mode is part of the mount point, so the two never collide.
    """

    def __init__(self, storage: Storage, share: FileShare, mount_root: Path, timeout_seconds: int,
                 writable: bool):
        if share.mount_type != MountType.NFS:
            raise UnsupportedSource(
                f'Storage {storage.name} is on a {share.mount_type} share, and this service mounts '
                f'only NFS. Mounting it needs the arguments the platform derives in NFSHelper.')
        if writable and _READ_ONLY_OPTION in _configured_options(share):
            # The API's WRITE check is about the storage's ACL and knows nothing about the share, so
            # this is the only place that can honour `ro` on the export.
            raise UnsupportedSource(
                f'The file share {share.mount_root} is configured read-only (mount options '
                f'"{share.mount_options}"), so nothing can be written into storage {storage.name}. '
                f'Ask an administrator to allow writing on the share, or extract into another '
                f'storage.')
        self.writable = writable
        self.mount_point = (mount_root
                            / (_WRITABLE_DIRECTORY if writable else _READ_ONLY_DIRECTORY)
                            / _share_directory_name(share.mount_root))
        self._storage = storage
        self._share = share
        self._timeout = timeout_seconds

    def ensure_mounted(self) -> None:
        """
        Makes sure the share is mounted at our mount point and answering.

        The lock covers the whole check-and-mount, or two workers race between the two steps.
        """
        with _MOUNT_LOCK:
            if self._is_mounted() and self._is_alive():
                LOG.debug('Share %s is already mounted at %s', self._share.mount_root,
                          self.mount_point)
                return
            self._mount()

    def _is_mounted(self) -> bool:
        """
        Looks our mount point up in `/proc/mounts`, which is the only answer that cannot be stale.

        The path is resolved before comparing, because `/proc/mounts` always holds an absolute one.
        """
        try:
            lines = Path('/proc/mounts').read_text().splitlines()
        except OSError as error:
            # Not Linux. Saying so beats a `mount` call whose failure is less clear.
            raise UnsupportedSource(f'Cannot read /proc/mounts ({error}); mounting NFS shares needs '
                                    f'Linux') from error
        target = str(self.mount_point.resolve())
        # Field 1 is the mount point. The length guard skips a line truncated by a concurrent read.
        return any(len(fields) > 1 and fields[1] == target
                   for fields in (line.split() for line in lines))

    def _is_alive(self) -> bool:
        """
        Checks that a mount point already in the list still answers.

        A share whose server has gone stays in `/proc/mounts` while every read under it hangs. `stat`
        as a subprocess can be killed; `os.stat` in our own thread could not.
        """
        completed = self._run(['stat', '-t', str(self.mount_point)], 'check')
        if completed is None:
            LOG.warning('Mount point %s did not answer within %s s, remounting', self.mount_point,
                        self._timeout)
            return False
        return completed.returncode == 0

    def _mount(self) -> None:
        """
        Mounts the share in our mode, creating the mount point first.

        `sudo` only when not already root: a container runs as root and may not have sudo.
        """
        self.mount_point.mkdir(parents=True, exist_ok=True)
        source = _mount_source(self._share)
        command = [] if os.geteuid() == 0 else ['sudo']
        command += ['mount', '-t', self._share.protocol]
        options = _mount_options(self._share, self.writable)
        if options:
            command += ['-o', options]
        command += [source, str(self.mount_point)]
        LOG.info('Mounting %s at %s, %s', source, self.mount_point,
                 'read-write' if self.writable else 'read-only')
        completed = self._run(command, 'mount')
        if completed is None:
            raise UnsupportedSource(f'Mounting {source} did not finish within {self._timeout} s')
        if completed.returncode != 0:
            # `mount` writes its reason to stderr, and that is what tells "no route to host" from
            # "access denied by server".
            raise UnsupportedSource(f'Mounting {source} failed: '
                                    f'{completed.stderr.strip() or completed.returncode}')

    def _run(self, command: list[str], what: str) -> subprocess.CompletedProcess | None:
        """
        Runs a command with a hard time limit. Returns `None` when it ran out of time.

        A list and no `shell=True`, so a storage path holding a space or a semicolon cannot become a
        second command. Part of these arguments comes from the platform's database.
        """
        try:
            return subprocess.run(command, capture_output=True, text=True, timeout=self._timeout,
                                  check=False)
        except subprocess.TimeoutExpired:
            LOG.warning('Command %s (%s) timed out after %s s', command[0], what, self._timeout)
            return None
        except OSError as error:
            raise UnsupportedSource(f'Cannot run {command[0]}: {error}') from error


class NfsSource(FileSource):
    """
    Lists and copies the files of an NFS storage, through a read-only mount of its share.

    The mount is prepared in the constructor, so an unreachable share fails the task before anything
    has been packed.
    """

    def __init__(self, storage: Storage, share: FileShare, mount_root: Path,
                 timeout_seconds: int = 30):
        self._storage = storage
        self._share = share
        self._mount = _ShareMount(storage, share, mount_root, timeout_seconds, writable=False)
        self._mount_point = self._mount.mount_point
        self._root = self._mount_point / _storage_folder(storage, share)
        self._ensure_mounted()

    # ------------------------------------------------------------------ mounting

    def _ensure_mounted(self) -> None:
        """Mounts the share read-only and checks that the storage's own folder is in it."""
        self._mount.ensure_mounted()
        if not self._root.exists():
            # The mount worked but the storage's folder is not in it, so the storage path and the
            # share root disagree. Without this the task reports success over an empty archive.
            raise UnsupportedSource(
                f'Storage {self._storage.name} points at {self._storage.path}, which resolves to '
                f'{self._root} under the mounted share {self._share.mount_root}, and that path does '
                f'not exist')

    # ------------------------------------------------------------------ listing

    def list_files(self, paths: tuple[str, ...]) -> Iterator[SourceFile]:
        """
        Expands every path of the selection.

        A path that is not there comes back with a `skip_reason`, so it reaches the archive's note
        instead of only the log.
        """
        for path in paths:
            target = self._root / path.strip('/')
            if not is_inside(target, self._root):
                # A path with `..` would read files outside the storage. The GUI cannot produce one,
                # a task created through the REST API directly can.
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

        Symlinks are skipped, so a cycle cannot turn the walk into an infinite one.
        """
        for entry in folder.rglob('*'):
            if entry.is_symlink():
                LOG.debug('Skipping symlink %s', entry)
                continue
            if entry.is_file():
                yield self._to_source_file(entry)

    def _to_source_file(self, entry: Path) -> SourceFile:
        """One local file as a `SourceFile`. `as_posix` because ZIP entry names use forward slashes."""
        return SourceFile(path=entry.relative_to(self._root).as_posix(),
                          size=entry.stat().st_size,
                          locator=str(entry))

    def stat(self, path: str) -> SourceFile | None:
        """The one file at a storage-relative path, or `None`. A folder answers `None` too."""
        target = self._root / path.strip('/')
        if not is_inside(target, self._root):
            raise UnsupportedSource(f'Path {path} leads outside storage {self._storage.name}')
        if not target.is_file():
            return None
        return self._to_source_file(target)

    # ------------------------------------------------------------------ download

    def download(self, source_file: SourceFile, destination: Path, heartbeat: Heartbeat) -> None:
        """
        Copies a file off the share.

        The loop is written out because `shutil.copyfileobj` has no place for the heartbeat. The copy
        goes to local disk, so a file that fails halfway is not already in the archive.
        """
        with open(source_file.locator, 'rb') as source, destination.open('wb') as out:
            while chunk := source.read(DOWNLOAD_BLOCK_BYTES):
                out.write(chunk)
                heartbeat()

    def open_stream(self, source_file: SourceFile) -> BinaryIO:
        """The file itself: a mount is a filesystem, so seeking needs no adapter."""
        return open(source_file.locator, 'rb')

    def close(self) -> None:
        """Nothing to release: the mount outlives the task on purpose. Do not add an `umount` here."""


class NfsDestination(FileDestination):
    """
    Writes files into an NFS storage, through a read-write mount of its share.

    The only place in this service that mounts a share writable: a separate mount point, taken only
    for a task that writes.

    Path permissions require S3, so for an NFS destination the only authorization that ever happened
    is storage-level WRITE, checked by the API when the task was created.
    """

    def __init__(self, storage: Storage, share: FileShare, mount_root: Path,
                 timeout_seconds: int = 30):
        self._storage = storage
        self._share = share
        self._mount = _ShareMount(storage, share, mount_root, timeout_seconds, writable=True)
        self._root = self._mount.mount_point / _storage_folder(storage, share)
        self._ensure_mounted()

    def _ensure_mounted(self) -> None:
        """Mounts the share read-write and checks that the storage's own folder is in it."""
        self._mount.ensure_mounted()
        if not self._root.is_dir():
            raise UnsupportedSource(
                f'Storage {self._storage.name} points at {self._storage.path}, which resolves to '
                f'{self._root} under the mounted share {self._share.mount_root}, and that path does '
                f'not exist')

    def ensure_destination_folder(self, prefix: str) -> None:
        """
        Creates the folder the extraction will write into, with its parents.

        The caller named this folder, so a missing one is created rather than refused: the same
        request needs no folder at all on an object storage, and the GUI always names a new folder
        beside the archive. A file at that path is a conflict - a share cannot hold both.

        Created with this container's umask and owned by root, where the API sets the mode of a
        folder it creates from `data.storage.nfs.default.umask` - group-writable by default. So a
        user may be unable to write into it, exactly as they may be unable to overwrite the files
        this service wrote.
        """
        target = self._resolve(prefix)
        try:
            target.mkdir(parents=True, exist_ok=True)
        except (FileExistsError, NotADirectoryError) as taken:
            raise DestinationConflict(
                f'The folder {prefix or "/"} cannot be created in storage {self._storage.name}, a '
                f'file is in the way. Extract into another folder, or remove that file.') from taken
        except OSError as error:
            # Not an internal error: the share refused the write. `root_squash` on the export is the
            # common one, since this service is root and root is then the share's anonymous user.
            raise DestinationWriteFailed(
                f'The folder {prefix or "/"} could not be created in storage {self._storage.name}: '
                f'{error}. Try the extraction again once the storage accepts writes.') from error

    def kind(self, key: str) -> EntryKind:
        """What is at the key. A symlink counts as what it is, not as what it points at."""
        target = self._resolve(key)
        if target.is_dir():
            return EntryKind.FOLDER
        if target.exists() or target.is_symlink():
            # A broken symlink is `exists() == False` and still occupies the name.
            return EntryKind.FILE
        return EntryKind.MISSING

    def upload(self, local_path: Path, key: str, heartbeat: Heartbeat,
               overwrite: bool = False) -> int:
        """
        Copies a local file onto the share, creating the folders of the key on the way.

        `'xb'` refuses an existing file in the same system call that creates it, so a file that
        appeared since the collision pass is refused rather than replaced.

        The folders are the archive's own structure, not a level this service invents.
        """
        target = self._resolve(key)
        mode = 'wb' if overwrite else 'xb'
        try:
            target.parent.mkdir(parents=True, exist_ok=True)
            with open(target, mode) as out, local_path.open('rb') as source:
                while chunk := source.read(DOWNLOAD_BLOCK_BYTES):
                    out.write(chunk)
                    heartbeat()
        except FileExistsError as taken:
            # From the `'xb'` open, or from a folder of the key being an existing file.
            raise DestinationConflict(f'{target} already exists') from taken
        except NotADirectoryError as taken:
            raise DestinationConflict(f'{target} cannot be created, a file is in the way') from taken
        return local_path.stat().st_size

    def create_folder(self, key: str) -> None:
        """
        Creates an empty folder of the archive, which a share can hold and S3 cannot.

        `exist_ok` because the collision pass already refused a file at this path.
        """
        target = self._resolve(key)
        try:
            target.mkdir(parents=True, exist_ok=True)
        except (FileExistsError, NotADirectoryError) as taken:
            raise DestinationConflict(f'{target} cannot be created, a file is in the way') from taken

    def delete(self, key: str) -> None:
        """Removes one file. A file that is not there is not an error."""
        self._resolve(key).unlink(missing_ok=True)

    def close(self) -> None:
        """Nothing to release: the mount outlives the task, exactly as a source's does."""

    def _resolve(self, key: str) -> Path:
        """
        The local path of a storage-relative key, refusing anything that leaves the storage.

        The guard the whole extraction rests on. Entry names come out of the archive, and
        `../../../../etc/cron.d/x` joined onto the mount root resolves outside it; this container
        runs as root and holds the roles of every storage. `is_inside` resolves symlinks first, so
        neither `..` in the name nor a symlink on the share redirects a write.
        """
        trimmed = key.strip('/')
        target = self._root / trimmed if trimmed else self._root
        if not is_inside(target, self._root):
            raise UnsupportedSource(f'Path {key} leads outside storage {self._storage.name}')
        return target


def _configured_options(share: FileShare) -> list[str]:
    """Whatever the platform recorded on the share, as a list with the blanks dropped."""
    return [option.strip() for option in (share.mount_options or '').split(',') if option.strip()]


def _mount_options(share: FileShare, writable: bool = False) -> str:
    """
    The `-o` argument: what the platform configured, minus its mode, plus the mode we asked for.

    The mode goes last, because options apply left to right. A share configured `ro` never reaches
    here with `writable`: `_ShareMount` refuses that instead of overriding it.
    """
    kept = [option for option in _configured_options(share) if option not in _MODE_OPTIONS]
    return ','.join(kept + ['rw' if writable else 'ro'])


def _share_directory_name(mount_root: str) -> str:
    """
    One directory name per share: `10.0.0.1:/export/data` gives `10.0.0.1_export_data.<digest>`.

    The digest is what makes the name unique. The readable half replaces every character a directory
    name may not hold with `_`, and `_` is legal there - so `10.0.0.1:/export/data` and
    `10.0.0.1:/export_data` both read as `10.0.0.1_export_data`. Two shares would then share a mount
    point, and `_is_mounted` compares the mount point alone. The digest comes from the untouched
    mount root, so two roots that differ anywhere differ here.

    The mode is the directory above, for the same reason - see `_WRITABLE_DIRECTORY`.

    Deliberately not `NFSHelper.normalizeMountPath`: that builds the path a job container sees, which
    is part of the platform's contract. This directory is private to this container.
    """
    readable = _UNSAFE_IN_PATH.sub('_', mount_root).strip('_')
    digest = hashlib.sha256(mount_root.encode()).hexdigest()[:SHARE_DIGEST_LENGTH]
    return f'{readable}.{digest}'


def _mount_source(share: FileShare) -> str:
    """
    The source `mount` is given: `host:/path`.

    Mirrors `NFSHelper.formatNfsPath`. The platform stores the root of an EFS share as the host
    alone - `fs-1234.efs.eu-central-1.amazonaws.com` - and `mount.nfs` refuses that with "remote
    share not in 'host:dir' format", so the delimiter is added when it is missing.
    """
    root = share.mount_root.strip()
    if _HOST_DELIMITER not in root:
        return f'{root}{_HOST_DELIMITER}'
    trimmed = root.rstrip('/')
    # A root of exactly `host:/` is already a source; trimming it would leave `host:`.
    return trimmed if _HOST_DELIMITER in trimmed else root


def _storage_folder(storage: Storage, share: FileShare) -> str:
    """
    The storage's own folder inside its share: its path without the share's root.

    The host and the path are compared apart, because the two values are written differently per
    provider: a classic NFS share is `host:/export` and its storages `host:/export/folder`, while an
    EFS share is the host alone and its storages are `host:/folder`. Taking the root off as a plain
    string prefix left `:/folder` for the second case.

    A storage that is the whole share yields an empty string, which joins onto a `Path` unchanged.
    """
    share_host, share_path = _split_root(share.mount_root)
    storage_host, storage_path = _split_root(storage.path)
    if storage_host != share_host:
        # Guessing would mean archiving the wrong folder, or the wrong server.
        raise UnsupportedSource(f'Storage path {storage.path} does not start with the root of its '
                                f'share {share.mount_root}')
    if not share_path:
        return storage_path
    if storage_path != share_path and not storage_path.startswith(f'{share_path}/'):
        raise UnsupportedSource(f'Storage path {storage.path} is not inside the root of its share '
                                f'{share.mount_root}')
    return storage_path[len(share_path):].strip('/')


def _split_root(value: str) -> tuple[str, str]:
    """The platform's NFS notation as `(host, path inside the share)`. Either half may be empty."""
    host, _, path = value.partition(':')
    return host.strip('/'), path.strip('/')
