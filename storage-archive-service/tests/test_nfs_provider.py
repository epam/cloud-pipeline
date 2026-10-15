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

The mounting itself is not exercised - it needs `mount`, a server and root - so `_ensure_mounted` is
replaced and the "mount point" is a directory pytest made. What is exercised is the walk over it, and
above all what it reports for a path that is not there: the same promise the S3 source keeps, since the
archive's note is the only place a user learns that something they ticked is missing.
"""

from pathlib import Path

from sas.model import FileShare, MountType, Storage, StorageType
from sas.datastorage_providers.nfs import NfsSource

SHARE_ROOT = '10.0.0.1:/export'


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
