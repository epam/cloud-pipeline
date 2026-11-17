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
Tests of what an archive can be made to say about itself before it is fetched.

Real archives of every supported format, built in a temporary directory and read through a plain
file object - which is what the NFS provider hands over, and what the S3 one imitates with ranged
reads.

Two things are asserted throughout, and they matter for different reasons. **What a format
declares** is what makes a cheap refusal possible at all. **What it does not** has to come back as
`None` rather than as a zero or a pass, because the caller turns `None` into "the limits are
enforced while extracting" - and reading an unknown total as a pass is how a zip bomb would walk
through the check.
"""

import bz2
import gzip
import io
import lzma
import struct
import tarfile
import zipfile
from pathlib import Path

import pytest

from sats.archive_probe import ArchiveFormat, detect_format, probe_archive

# Compressible on purpose: a payload that shrinks makes the declared total and the compressed size
# clearly different numbers, so a check that mixed them up would fail here.
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


# ---------------------------------------------------------------- what each format declares

def test_a_zip_declares_its_entry_count_and_its_total(tmp_path):
    # The strongest case there is: the central directory is at the end of the file, so both numbers
    # are exact and cost a handful of bytes.
    probe = _probe(_zip(tmp_path))

    assert probe.format is ArchiveFormat.ZIP
    assert probe.entries == len(CONTENT)
    assert probe.total_bytes == TOTAL


def test_a_zip_counts_its_folder_entries_too(tmp_path):
    # Every entry, because that is what the limit means and what the extraction counts. Counting only
    # the files left a hole: an archive of ninety thousand empty folders is a few megabytes, passed
    # both checks, and then cost a request per folder in the collision pass.
    folders = ('data', 'empty')

    probe = _probe(_zip(tmp_path, folders=folders))

    assert probe.entries == len(CONTENT) + len(folders)
    # A folder declares no bytes, so the total is unaffected.
    assert probe.total_bytes == TOTAL


def test_a_tar_gz_declares_its_total_and_not_its_count(tmp_path):
    # The last four bytes are the size of the tar inside, which is the content plus a 512-byte header
    # per entry and its padding: an upper bound on the content, which is the safe direction.
    probe = _probe(_tar(tmp_path, 'w:gz', 'archive.tar.gz'))

    assert probe.format is ArchiveFormat.GZIP
    assert probe.entries is None
    assert probe.total_bytes >= TOTAL


def test_a_tar_xz_declares_its_total_and_not_its_count(tmp_path):
    # From the index the stream footer points at, where the sizes are exact rather than modular.
    probe = _probe(_tar(tmp_path, 'w:xz', 'archive.tar.xz'))

    assert probe.format is ArchiveFormat.XZ
    assert probe.entries is None
    assert probe.total_bytes >= TOTAL


def test_a_tar_bz2_declares_nothing(tmp_path):
    # The one format that reveals neither number, anywhere. Its only limit is the byte counter during
    # the extraction, and this test is what pins that it is reported honestly as unknown.
    probe = _probe(_tar(tmp_path, 'w:bz2', 'archive.tar.bz2'))

    assert probe.format is ArchiveFormat.BZIP2
    assert probe.entries is None
    assert probe.total_bytes is None


def test_a_plain_tar_is_bounded_by_its_own_size(tmp_path):
    # An uncompressed tar cannot hold more bytes than it is long, so its own size is an exact upper
    # bound - free, and enough for the size limit. The entry count is left to the extraction, because
    # reading it would cost one ranged request per entry.
    path = _tar(tmp_path)

    probe = _probe(path)

    assert probe.format is ArchiveFormat.TAR
    assert probe.total_bytes == path.stat().st_size
    assert probe.total_bytes >= TOTAL
    assert probe.entries is None


@pytest.mark.parametrize('opener,name,declares_total', [
    (gzip.open, 'data.csv.gz', True),
    (lzma.open, 'data.csv.xz', True),
    # bzip2 again: a bare one is as silent as a tar.bz2.
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


# ---------------------------------------------------------------- the format comes from the content

def test_the_format_is_read_from_the_content_and_not_from_the_name(tmp_path):
    # This service is the only thing between a mislabelled file and a storage, so the name decides
    # nothing: a `.zip` that is really a tar extracts as a tar.
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
    # Every tail read asks for more bytes than there are. Short reads have to answer, not raise.
    path = tmp_path / 'empty.zip'
    path.write_bytes(b'')

    assert _probe(path).format is ArchiveFormat.UNKNOWN


def test_a_tar_that_holds_a_zip_near_its_end_is_still_a_tar(tmp_path):
    # Why the order of the checks matters: `zipfile.is_zipfile` searches the *end* of the file for a
    # central directory, so asking it first would call this a zip and then fail to open it.
    inner = _zip(tmp_path)
    path = tmp_path / 'outer.tar'
    with tarfile.open(path, 'w') as archive:
        archive.add(inner, arcname='inner.zip')

    assert _format(path) is ArchiveFormat.TAR


def test_a_zip_with_something_in_front_of_it_is_still_a_zip(tmp_path):
    # A self-extracting archive: the magic is not at offset 0, and the central directory is.
    original = _zip(tmp_path).read_bytes()
    path = tmp_path / 'installer.exe'
    path.write_bytes(b'MZ' + b'\0' * 64 + original)

    assert _format(path) is ArchiveFormat.ZIP


# ---------------------------------------------------------------- an honest unknown

def test_a_gzip_total_smaller_than_the_compressed_size_is_read_as_unknown(tmp_path):
    # ISIZE is the uncompressed size modulo 2**32, so a 6 GiB archive declares 2 GiB. The symptom is
    # a declared total below the compressed size, and reading it as a pass is how such an archive
    # would walk through the size limit. Reproduced by overwriting the trailer, because the honest
    # way to get here needs a 4 GiB file.
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
    # Truncated after its magic: the format is still recognisable and the index is not. The local
    # extraction will report what is wrong with it, and refusing here would mean refusing an archive
    # this service might have opened.
    path = tmp_path / 'broken.zip'
    path.write_bytes(b'PK\x03\x04' + b'\0' * 100)

    probe = _probe(path)

    assert probe.format is ArchiveFormat.ZIP
    assert probe.entries is None
    assert probe.total_bytes is None
