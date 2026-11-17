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
Tests of how a selection on a mounted share is expanded.

Mounting needs `mount`, a server and root, so `_ensure_mounted` is replaced and the mount point is a
pytest directory. What a missing path reports is the point: it has to reach the archive's note.
"""

import os
from pathlib import Path

import pytest

from sats.util.errors import DestinationConflict, InvalidTask, UnsupportedSource
from sats.model import FileShare, MountType, Storage, StorageType
from sats.datastorage_providers.base import EntryKind
from sats.datastorage_providers.nfs import NfsDestination, NfsSource, _ShareMount, \
    _mount_options, _mount_source, _storage_folder

SHARE_ROOT = '10.0.0.1:/export'
# What the platform stores for an EFS share: the host alone, with no path on it.
EFS_SHARE_ROOT = 'fs-2a5ab373.efs.eu-central-1.amazonaws.com'


def _share() -> FileShare:
    return FileShare(id=1, mount_root=SHARE_ROOT, mount_type=MountType.NFS, region_id=1)


def _storage() -> Storage:
    # An NFS storage path is `host:/export/folder`; the folder after the share root is the storage's
    # own, and the walk is rooted there.
    return Storage(id=5, name='team-share', type=StorageType.NFS, path=f'{SHARE_ROOT}/team',
                   file_share_mount_id=1, region_id=1)


def _source(tmp_path: Path, monkeypatch) -> NfsSource:
    monkeypatch.setattr(NfsSource, '_ensure_mounted', lambda _self: None)
    return NfsSource(_storage(), _share(), tmp_path)


def _root(tmp_path: Path, monkeypatch) -> tuple[NfsSource, Path]:
    """A source and the directory its storage is rooted at, created and empty."""
    source = _source(tmp_path, monkeypatch)
    root = source._root
    root.mkdir(parents=True, exist_ok=True)
    return source, root


def _listed(source: NfsSource, *paths: str) -> dict[str, str | None]:
    return {found.path: found.skip_reason for found in source.list_files(paths)}


def test_reports_a_path_that_is_not_on_the_share(tmp_path, monkeypatch):
    # It used to be logged and dropped. A warning in the service's log is not something the person who
    # downloaded the archive can see, so the file vanished without explanation.
    source, root = _root(tmp_path, monkeypatch)
    (root / 'notes.txt').write_bytes(b'hello')

    listed = _listed(source, 'notes.txt', 'gone.txt')

    assert listed['notes.txt'] is None
    assert listed['gone.txt'] == f'not found at {root / "gone.txt"}'


def test_expands_a_folder_into_its_files(tmp_path, monkeypatch):
    source, root = _root(tmp_path, monkeypatch)
    (root / 'data' / 'nested').mkdir(parents=True)
    (root / 'data' / 'a.txt').write_bytes(b'a')
    (root / 'data' / 'nested' / 'b.txt').write_bytes(b'bb')
    (root / 'outside.txt').write_bytes(b'x')

    listed = _listed(source, 'data/')

    # Paths relative to the storage root, with forward slashes - the tree the user saw in the GUI.
    assert listed == {'data/a.txt': None, 'data/nested/b.txt': None}


def _share_with(root: str) -> FileShare:
    return FileShare(id=1, mount_root=root, mount_type=MountType.NFS, region_id=1)


def _storage_at(path: str) -> Storage:
    return Storage(id=5, name='team-share', type=StorageType.NFS, path=path,
                   file_share_mount_id=1, region_id=1)


@pytest.mark.parametrize('root,expected', [
    # An EFS share is the host alone. Without the delimiter `mount.nfs` answers "remote share not in
    # 'host:dir' format" and every task on such a storage fails.
    (EFS_SHARE_ROOT, f'{EFS_SHARE_ROOT}:/'),
    # A classic share already carries it.
    ('10.0.0.1:/export', '10.0.0.1:/export'),
    ('10.0.0.1:/export/', '10.0.0.1:/export'),
    # A root that is exactly the server root must not be trimmed to `host:`.
    ('10.0.0.1:/', '10.0.0.1:/'),
])
def test_builds_a_mount_source_mount_accepts(root, expected):
    assert _mount_source(_share_with(root)) == expected


@pytest.mark.parametrize('share_root,storage_path,expected', [
    # EFS: the storage carries `host:/folder` while the share is the host alone.
    (EFS_SHARE_ROOT, f'{EFS_SHARE_ROOT}:/nfs-super', 'nfs-super'),
    (EFS_SHARE_ROOT, f'{EFS_SHARE_ROOT}:/nfs-super/inner', 'nfs-super/inner'),
    # A storage that is the whole share.
    (EFS_SHARE_ROOT, EFS_SHARE_ROOT, ''),
    ('10.0.0.1:/export', '10.0.0.1:/export', ''),
    # A classic share with a folder per storage.
    ('10.0.0.1:/export', '10.0.0.1:/export/team', 'team'),
    ('10.0.0.1:/export/', '10.0.0.1:/export/team/inner', 'team/inner'),
])
def test_finds_the_storage_folder_inside_its_share(share_root, storage_path, expected):
    assert _storage_folder(_storage_at(storage_path), _share_with(share_root)) == expected


@pytest.mark.parametrize('share_root,storage_path', [
    # Another server altogether.
    ('10.0.0.1:/export', '10.0.0.2:/export/team'),
    # The right server, the wrong export.
    ('10.0.0.1:/export', '10.0.0.1:/other/team'),
    # A folder that merely starts with the share's name.
    ('10.0.0.1:/export', '10.0.0.1:/export-2/team'),
])
def test_refuses_a_storage_that_is_not_inside_its_share(share_root, storage_path):
    with pytest.raises(UnsupportedSource):
        _storage_folder(_storage_at(storage_path), _share_with(share_root))


# ---------------------------------------------------------------- the read-write side

def _destination(tmp_path: Path, monkeypatch) -> NfsDestination:
    monkeypatch.setattr(NfsDestination, '_ensure_mounted', lambda _self: None)
    destination = NfsDestination(_storage(), _share(), tmp_path)
    destination._root.mkdir(parents=True, exist_ok=True)
    return destination


def test_a_destination_mount_is_read_write_and_a_source_mount_is_not():
    # The one safety property an extraction relaxes, and only for a task that writes. The mode goes
    # last, so whatever the platform configured for job containers cannot decide it.
    share = FileShare(id=1, mount_root=SHARE_ROOT, mount_type=MountType.NFS,
                      mount_options='rw,noresvport', region_id=1)

    assert _mount_options(share, writable=True) == 'noresvport,rw'
    assert _mount_options(share) == 'noresvport,ro'


def test_the_two_modes_of_one_share_are_two_mount_points(tmp_path, monkeypatch):
    # Extracting inside a single storage needs the share read-only and read-write at the same time,
    # and one mount point cannot carry two modes - the second `mount` would be asked to put `rw` on a
    # directory that is already `ro`.
    monkeypatch.setattr(_ShareMount, 'ensure_mounted', lambda _self: None)
    read_only = _ShareMount(_storage(), _share(), tmp_path, 30, writable=False)
    writable = _ShareMount(_storage(), _share(), tmp_path, 30, writable=True)

    assert read_only.mount_point != writable.mount_point


def test_a_share_named_like_the_writable_marker_does_not_collide_with_another(tmp_path, monkeypatch):
    # The mode is a directory *level* and not a suffix on the share's name, because a suffix cannot
    # be made safe: `-` and `_` are legal in the normalised name. With `-rw` appended, the read-only
    # mount of `/export/team-rw` and the read-write mount of `/export/team` were one directory - and
    # `_is_mounted` compares the mount point alone, so the second caller found the share "already
    # mounted" and read or wrote through the first one's mount, across a storage boundary.
    monkeypatch.setattr(_ShareMount, 'ensure_mounted', lambda _self: None)
    looks_like_a_marker = _share_with('10.0.0.1:/export/team-rw')
    ordinary = _share_with('10.0.0.1:/export/team')

    read_only = _ShareMount(_storage_at('10.0.0.1:/export/team-rw'), looks_like_a_marker, tmp_path,
                            30, writable=False)
    writable = _ShareMount(_storage_at('10.0.0.1:/export/team'), ordinary, tmp_path, 30,
                           writable=True)

    assert read_only.mount_point != writable.mount_point


def test_a_share_the_administrator_marked_read_only_is_never_mounted_writable(tmp_path):
    # The API's WRITE check is about the storage's ACL and knows nothing about the share, so this is
    # the only place that can honour `mountOptions: ro` - and overriding it would make a share-level
    # decision inert.
    read_only_share = FileShare(id=1, mount_root=SHARE_ROOT, mount_type=MountType.NFS,
                                mount_options='ro,noresvport', region_id=1)

    with pytest.raises(UnsupportedSource) as refusal:
        _ShareMount(_storage(), read_only_share, tmp_path, 30, writable=True)

    assert 'read-only' in str(refusal.value)
    # And reading it is unaffected.
    assert _ShareMount(_storage(), read_only_share, tmp_path, 30, writable=False) is not None


def test_writes_a_file_and_creates_the_folders_of_its_key(tmp_path, monkeypatch):
    # The folders are the archive's own structure, not a level this service invents - and a write
    # without them fails.
    destination = _destination(tmp_path, monkeypatch)
    local = tmp_path / 'source.txt'
    local.write_bytes(b'hello')

    written = destination.upload(local, 'out/data/values.csv', lambda: None)

    assert (destination._root / 'out/data/values.csv').read_bytes() == b'hello'
    assert written == 5


def test_refuses_an_existing_file_in_the_same_call_that_would_create_it(tmp_path, monkeypatch):
    # `'xb'` creates and refuses in one system call, so a file that appeared since the collision pass
    # is refused rather than replaced.
    destination = _destination(tmp_path, monkeypatch)
    (destination._root / 'out').mkdir()
    (destination._root / 'out/notes.txt').write_bytes(b'older')
    local = tmp_path / 'source.txt'
    local.write_bytes(b'newer')

    with pytest.raises(DestinationConflict):
        destination.upload(local, 'out/notes.txt', lambda: None)

    assert (destination._root / 'out/notes.txt').read_bytes() == b'older'
    destination.upload(local, 'out/notes.txt', lambda: None, overwrite=True)
    assert (destination._root / 'out/notes.txt').read_bytes() == b'newer'


def test_tells_a_file_from_a_folder_from_nothing(tmp_path, monkeypatch):
    destination = _destination(tmp_path, monkeypatch)
    (destination._root / 'folder').mkdir()
    (destination._root / 'notes.txt').write_bytes(b'x')

    assert destination.kind('folder') is EntryKind.FOLDER
    assert destination.kind('notes.txt') is EntryKind.FILE
    assert destination.kind('gone.txt') is EntryKind.MISSING


def test_an_empty_folder_is_created_on_a_share(tmp_path, monkeypatch):
    # What an object storage cannot represent and a share can.
    destination = _destination(tmp_path, monkeypatch)

    destination.create_folder('out/empty')

    assert (destination._root / 'out/empty').is_dir()


def test_a_missing_destination_folder_is_refused_rather_than_created(tmp_path, monkeypatch):
    # The caller named a folder, and creating one would turn a typo in the path into a folder nobody
    # asked for, in a storage where nothing says the name was meant.
    destination = _destination(tmp_path, monkeypatch)

    with pytest.raises(InvalidTask):
        destination.assert_writable('out/')

    (destination._root / 'out').mkdir()
    destination.assert_writable('out/')


@pytest.mark.parametrize('key', ['../escaped.txt', 'out/../../escaped.txt'])
def test_a_key_that_leaves_the_storage_is_refused(tmp_path, monkeypatch, key):
    # The guard the whole extraction rests on. This container runs as root and its node holds a role
    # of every storage on the platform, so a path out of the share is code execution.
    destination = _destination(tmp_path, monkeypatch)
    local = tmp_path / 'source.txt'
    local.write_bytes(b'x')

    with pytest.raises(UnsupportedSource):
        destination.upload(local, key, lambda: None)


def test_a_symlink_pointing_outside_the_storage_does_not_redirect_a_write(tmp_path, monkeypatch):
    # Text checks cannot catch this one: the key is perfectly ordinary, and the share already held a
    # symlink. `is_inside` resolves before it compares, which is why it does.
    destination = _destination(tmp_path, monkeypatch)
    outside = tmp_path / 'outside'
    outside.mkdir()
    os.symlink(outside, destination._root / 'out')
    local = tmp_path / 'source.txt'
    local.write_bytes(b'x')

    with pytest.raises(UnsupportedSource):
        destination.upload(local, 'out/notes.txt', lambda: None)

    assert list(outside.iterdir()) == []


def test_deleting_what_is_not_there_is_not_an_error(tmp_path, monkeypatch):
    destination = _destination(tmp_path, monkeypatch)
    (destination._root / 'notes.txt').write_bytes(b'x')

    destination.delete('notes.txt')
    destination.delete('notes.txt')

    assert not (destination._root / 'notes.txt').exists()


def test_two_shares_whose_names_normalise_alike_get_different_mount_points(tmp_path, monkeypatch):
    # `/` becomes `_` in the readable half of the name, and `_` is itself legal there - so
    # `10.0.0.1:/export/data` and `10.0.0.1:/export_data` both read as `10.0.0.1_export_data`. With
    # one mount point between them, `_is_mounted` - which compares the mount point alone - would let
    # the second task read or write through the first share's export, across a storage boundary.
    monkeypatch.setattr(_ShareMount, 'ensure_mounted', lambda _self: None)
    nested = '10.0.0.1:/export/data'
    flat = '10.0.0.1:/export_data'

    first = _ShareMount(_storage_at(nested), _share_with(nested), tmp_path, 30, writable=True)
    second = _ShareMount(_storage_at(flat), _share_with(flat), tmp_path, 30, writable=True)

    assert first.mount_point != second.mount_point
    # And the readable half is still there, so an operator can tell which share a mount belongs to.
    assert '10.0.0.1_export_data' in first.mount_point.name
