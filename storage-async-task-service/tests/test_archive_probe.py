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

"""Tests of what an archive declares about itself before it is fetched."""

import bz2
import gzip
import io
import lzma
import struct
import tarfile
import zipfile
from pathlib import Path

import pytest

from sats.util.archive_probe import ArchiveFormat, detect_format, probe_archive

CONTENT = {'notes.txt': b'a' * 1000, 'data/values.csv': b'1,2,3\n' * 500}
TOTAL = sum(len(value) for value in CONTENT.values())


def _zip(tmp_path: Path, content=None, folders=(), compression=zipfile.ZIP_DEFLATED) -> Path:
    path = tmp_path / 'archive.zip'
    with zipfile.ZipFile(path, 'w', compression) as archive:
        for folder in folders:
            archive.writestr(f'{folder}/', b'')
        for name, value in (content or CONTENT).items():
            archive.writestr(name, value)
    return path


def _tar(tmp_path: Path, mode: str = 'w', name: str = 'archive.tar') -> Path:
    path = tmp_path / name
    with tarfile.open(path, f'{mode}') as archive:
        for entry, value in CONTENT.items():
            info = tarfile.TarInfo(entry)
            info.size = len(value)
            archive.addfile(info, io.BytesIO(value))
    return path


def _single(tmp_path: Path, opener, name: str) -> Path:
    path = tmp_path / name
    with opener(path, 'wb') as stream:
        stream.write(b'x' * TOTAL)
    return path


def _probe(path: Path):
    with path.open('rb') as stream:
        return probe_archive(stream, path.stat().st_size)


def _format(path: Path) -> ArchiveFormat:
    with path.open('rb') as stream:
        return detect_format(stream, path.stat().st_size)


def test_a_zip_declares_its_entry_count_and_its_total(tmp_path):
    probe = _probe(_zip(tmp_path))

    assert probe.format is ArchiveFormat.ZIP
    assert probe.entries == len(CONTENT)
    assert probe.total_bytes == TOTAL


def test_a_zip_counts_its_folder_entries_too(tmp_path):
    folders = ('data', 'empty')

    probe = _probe(_zip(tmp_path, folders=folders))

    assert probe.entries == len(CONTENT) + len(folders)
    assert probe.total_bytes == TOTAL


def test_a_tar_gz_declares_its_total_and_not_its_count(tmp_path):
    probe = _probe(_tar(tmp_path, 'w:gz', 'archive.tar.gz'))

    assert probe.format is ArchiveFormat.GZIP
    assert probe.entries is None
    assert probe.total_bytes >= TOTAL


def test_a_tar_xz_declares_its_total_and_not_its_count(tmp_path):
    probe = _probe(_tar(tmp_path, 'w:xz', 'archive.tar.xz'))

    assert probe.format is ArchiveFormat.XZ
    assert probe.entries is None
    assert probe.total_bytes >= TOTAL


def test_a_tar_bz2_declares_nothing(tmp_path):
    probe = _probe(_tar(tmp_path, 'w:bz2', 'archive.tar.bz2'))

    assert probe.format is ArchiveFormat.BZIP2
    assert probe.entries is None
    assert probe.total_bytes is None


def test_a_plain_tar_is_bounded_by_its_own_size(tmp_path):
    path = _tar(tmp_path)

    probe = _probe(path)

    assert probe.format is ArchiveFormat.TAR
    assert probe.total_bytes == path.stat().st_size
    assert probe.total_bytes >= TOTAL
    assert probe.entries is None


@pytest.mark.parametrize('opener,name,declares_total', [
    (gzip.open, 'data.csv.gz', True),
    (lzma.open, 'data.csv.xz', True),
    (bz2.open, 'data.csv.bz2', False),
])
def test_a_bare_compressed_file_declares_what_its_format_allows(tmp_path, opener, name,
                                                                declares_total):
    probe = _probe(_single(tmp_path, opener, name))

    assert probe.is_supported
    if declares_total:
        assert probe.total_bytes == TOTAL
    else:
        assert probe.total_bytes is None


def test_the_format_is_read_from_the_content_and_not_from_the_name(tmp_path):
    mislabelled = _tar(tmp_path, 'w', 'archive.zip')

    assert _format(mislabelled) is ArchiveFormat.TAR


def test_a_tgz_is_not_a_special_case(tmp_path):
    assert _format(_tar(tmp_path, 'w:gz', 'archive.tgz')) is ArchiveFormat.GZIP


def test_a_file_that_is_no_archive_at_all_is_unsupported(tmp_path):
    path = tmp_path / 'notes.txt'
    path.write_bytes(b'just text, and not even a little bit of an archive\n')

    probe = _probe(path)

    assert probe.format is ArchiveFormat.UNKNOWN
    assert not probe.is_supported


def test_an_empty_file_is_unsupported_rather_than_a_crash(tmp_path):
    path = tmp_path / 'empty.zip'
    path.write_bytes(b'')

    assert _probe(path).format is ArchiveFormat.UNKNOWN


def test_a_tar_that_holds_a_zip_near_its_end_is_still_a_tar(tmp_path):
    inner = _zip(tmp_path)
    path = tmp_path / 'outer.tar'
    with tarfile.open(path, 'w') as archive:
        archive.add(inner, arcname='inner.zip')

    assert _format(path) is ArchiveFormat.TAR


def test_a_zip_with_something_in_front_of_it_is_still_a_zip(tmp_path):
    original = _zip(tmp_path).read_bytes()
    path = tmp_path / 'installer.exe'
    path.write_bytes(b'MZ' + b'\0' * 64 + original)

    assert _format(path) is ArchiveFormat.ZIP


def test_a_gzip_total_smaller_than_the_compressed_size_is_read_as_unknown(tmp_path):
    path = _single(tmp_path, gzip.open, 'data.csv.gz')
    with path.open('r+b') as stream:
        stream.seek(-4, io.SEEK_END)
        stream.write(struct.pack('<I', 1))

    probe = _probe(path)

    assert probe.format is ArchiveFormat.GZIP
    assert probe.total_bytes is None


def test_an_xz_footer_without_its_magic_is_read_as_unknown(tmp_path):
    path = _single(tmp_path, lzma.open, 'data.csv.xz')
    with path.open('r+b') as stream:
        stream.seek(-2, io.SEEK_END)
        stream.write(b'??')

    assert _probe(path).total_bytes is None


def test_a_zip_whose_index_cannot_be_read_is_reported_as_unknown_rather_than_refused(tmp_path):
    path = tmp_path / 'broken.zip'
    path.write_bytes(b'PK\x03\x04' + b'\0' * 100)

    probe = _probe(path)

    assert probe.format is ArchiveFormat.ZIP
    assert probe.entries is None
    assert probe.total_bytes is None
