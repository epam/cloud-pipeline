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

from pathlib import Path

import pytest

from sats.util.errors import UnsupportedSource
from sats.model import FileShare, MountType, Storage, StorageType
from sats.datastorage_providers.nfs import NfsSource, _mount_source, _storage_folder

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
