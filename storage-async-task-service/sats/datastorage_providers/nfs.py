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

The share is mounted read-only and the selection is then read as local files. Three rules:

  * **the share is mounted, not the storage** - several storages may live in one export;
  * **the mount is left in place** - mounting is the slow part, and the container is its lifetime;
  * **every call that touches the mount has a timeout** - a dead NFS server blocks in the kernel for
    ever, and a worker thread stuck there cannot be killed. So `mount` and the liveness check run as
    subprocesses.

Only `MountType.NFS` is handled. SMB and Lustre need the arguments the platform derives in
`NFSHelper`, and guessing them could mount the wrong path.
"""

from __future__ import annotations

import os
import re
import subprocess
import threading
from collections.abc import Iterator
from pathlib import Path

from .base import DOWNLOAD_BLOCK_BYTES, FileSource, Heartbeat, SourceFile
from ..util.errors import UnsupportedSource
from ..util.log import get_logger
from ..model import FileShare, MountType, Storage

LOG = get_logger(__name__)

# Mounting is process-wide, so one lock for the module: two workers on the same share would otherwise
# both find it unmounted and both run `mount`.
_MOUNT_LOCK = threading.Lock()

# Characters allowed in the directory name derived from a share.
_UNSAFE_IN_PATH = re.compile(r'[^A-Za-z0-9._-]+')

# Options dropped from whatever the platform configured, since this service only reads. `ro` is then
# added unconditionally.
_WRITE_OPTIONS = frozenset({'rw'})

# What separates the host from the path in the platform's NFS notation. `mount` needs both halves,
# while the platform stores the root of an EFS share as the host alone.
_HOST_DELIMITER = ':/'


class NfsSource(FileSource):
    """
    Lists and copies the files of an NFS storage, through a read-only mount of its share.

    The mount is prepared in the constructor, so an unreachable share fails the task before anything
    has been packed.
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
        Makes sure the share is mounted at our mount point and answering.

        The lock covers the whole check-and-mount, or two workers race between the two steps.
        """
        with _MOUNT_LOCK:
            if self._is_mounted() and self._is_alive():
                LOG.debug('Share %s is already mounted at %s', self._share.mount_root,
                          self._mount_point)
                return
            self._mount()
        if not self._root.exists():
            # The mount worked but the storage's folder is not in it, so the storage path and the
            # share root disagree. Without this the task reports success over an empty archive.
            raise UnsupportedSource(
                f'Storage {self._storage.name} points at {self._storage.path}, which resolves to '
                f'{self._root} under the mounted share {self._share.mount_root}, and that path does '
                f'not exist')

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
        target = str(self._mount_point.resolve())
        # Field 1 is the mount point. The length guard skips a line truncated by a concurrent read.
        return any(len(fields) > 1 and fields[1] == target
                   for fields in (line.split() for line in lines))

    def _is_alive(self) -> bool:
        """
        Checks that a mount point already in the list still answers.

        A share whose server has gone stays in `/proc/mounts` while every read under it hangs. `stat`
        as a subprocess can be killed; `os.stat` in our own thread could not.
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

        `sudo` only when not already root: a container runs as root and may not have sudo at all.
        """
        self._mount_point.mkdir(parents=True, exist_ok=True)
        source = _mount_source(self._share)
        command = [] if os.geteuid() == 0 else ['sudo']
        command += ['mount', '-t', self._share.protocol]
        options = _mount_options(self._share)
        if options:
            command += ['-o', options]
        command += [source, str(self._mount_point)]
        LOG.info('Mounting %s at %s', source, self._mount_point)
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

    # ------------------------------------------------------------------ listing

    def list_files(self, paths: tuple[str, ...]) -> Iterator[SourceFile]:
        """
        Expands every path of the selection.

        A path that is not there comes back with a `skip_reason`, so it reaches the archive's note
        instead of only the log.
        """
        for path in paths:
            target = self._root / path.strip('/')
            if not _is_inside(target, self._root):
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

    def close(self) -> None:
        """Nothing to release: the mount outlives the task on purpose. Do not add an `umount` here."""


def _mount_options(share: FileShare) -> str:
    """
    The `-o` argument: what the platform configured, minus anything writable, plus `ro`.

    `ro` goes last, because options are applied left to right.
    """
    configured = [option.strip() for option in (share.mount_options or '').split(',')]
    kept = [option for option in configured if option and option not in _WRITE_OPTIONS]
    if 'ro' in kept:
        kept.remove('ro')
    return ','.join(kept + ['ro'])


def _share_directory_name(mount_root: str) -> str:
    """
    Turns `10.0.0.1:/export/data` into one directory name, `10.0.0.1_export_data`.

    Deliberately not `NFSHelper.normalizeMountPath`: that one builds the path a job container sees,
    which is part of the platform's contract. This directory is private to this container.
    """
    return _UNSAFE_IN_PATH.sub('_', mount_root).strip('_')


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


def _is_inside(candidate: Path, root: Path) -> bool:
    """
    Whether a path stays within a root once `..` and symlinks are resolved.

    `is_relative_to` compares text, so both sides are resolved first. `strict=False` so a path that
    does not exist gets an answer rather than an exception.
    """
    return candidate.resolve(strict=False).is_relative_to(root.resolve(strict=False))
