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
Tests of the extraction, which is where this feature's safety is decided.

Three groups, and the middle one is the reason this module exists at all:

  * **every format opens**, and the name of the file decides nothing;
  * **a crafted entry name is refused**, and nothing is written. The API never opens the archive, so
    these names are checked by this code and by nothing else - and the container runs as root on a
    node holding a role of every storage on the platform;
  * **the limits are counted while writing**, because every size an archive declares was written by
    whoever built it.

The archives are real, built here, and a few are assembled by hand - a tar entry naming `..` is not
something `tarfile` will write for you by accident, which is the point.
"""

import bz2
import gzip
import io
import lzma
import os
import stat
import tarfile
import zipfile
from pathlib import Path

import pytest

from sats.archive_extractor import ExtractionLimits, extract, safe_entry_path
from sats.archive_probe import detect_format
from sats.util.errors import StopReason, StopRequested, ThresholdExceeded, \
    UnsafeArchiveEntry, UnsupportedArchive

CONTENT = {'notes.txt': b'hello', 'data/values.csv': b'1,2,3\n'}
TOTAL = sum(len(value) for value in CONTENT.values())

# Every test extracts with a heartbeat that does nothing; the one test about stopping replaces it.
NO_HEARTBEAT = lambda: None  # noqa: E731 - a lambda reads better than four lines of def here


def _extract(path: Path, target: Path, limits: ExtractionLimits | None = None, heartbeat=None):
    """Extracts with the format read from the content, as the worker does."""
    with path.open('rb') as stream:
        archive_format = detect_format(stream, path.stat().st_size)
    return extract(path, target, path.name, archive_format, limits or ExtractionLimits(),
                   heartbeat or NO_HEARTBEAT)


def _zip(tmp_path: Path, content=None, name='archive.zip', folders=()) -> Path:
    path = tmp_path / name
    with zipfile.ZipFile(path, 'w') as archive:
        for folder in folders:
            archive.writestr(f'{folder}/', b'')
        for entry, value in (content or CONTENT).items():
            archive.writestr(entry, value)
    return path


def _tar(tmp_path: Path, content=None, mode='w', name='archive.tar') -> Path:
    path = tmp_path / name
    with tarfile.open(path, mode) as archive:
        for entry, value in (content or CONTENT).items():
            info = tarfile.TarInfo(entry)
            info.size = len(value)
            archive.addfile(info, io.BytesIO(value))
    return path


def _contents(target: Path) -> dict[str, bytes]:
    """Everything under the extraction directory, by its path relative to it."""
    return {str(found.relative_to(target)): found.read_bytes()
            for found in sorted(target.rglob('*')) if found.is_file()}


# ---------------------------------------------------------------- every format opens

@pytest.mark.parametrize('mode,name', [
    ('w', 'archive.tar'),
    ('w:gz', 'archive.tar.gz'),
    ('w:gz', 'archive.tgz'),
    ('w:bz2', 'archive.tar.bz2'),
    ('w:xz', 'archive.tar.xz'),
])
def test_every_tar_flavour_extracts(tmp_path, mode, name):
    extraction = _extract(_tar(tmp_path, mode=mode, name=name), tmp_path / 'out')

    assert _contents(tmp_path / 'out') == CONTENT
    assert extraction.total_bytes == TOTAL
    assert {found.key for found in extraction.files} == set(CONTENT)


def test_a_zip_extracts(tmp_path):
    extraction = _extract(_zip(tmp_path), tmp_path / 'out')

    assert _contents(tmp_path / 'out') == CONTENT
    assert extraction.total_bytes == TOTAL


@pytest.mark.parametrize('opener,name,expected', [
    (gzip.open, 'data.csv.gz', 'data.csv'),
    (bz2.open, 'data.csv.bz2', 'data.csv'),
    (lzma.open, 'data.csv.xz', 'data.csv'),
    # A bare compressor with nothing left after its extension: `backup.gz` gives `backup`.
    (gzip.open, 'backup.gz', 'backup'),
    # The extension is matched whatever its case.
    (gzip.open, 'DATA.CSV.GZ', 'DATA.CSV'),
])
def test_a_bare_compressed_file_is_named_after_the_archive(tmp_path, opener, name, expected):
    # Not after the original name in the gzip header, deliberately: that field is written by whoever
    # built the archive, so trusting it would put an attacker-chosen name into the one path the API
    # had validated.
    path = tmp_path / name
    with opener(path, 'wb') as stream:
        stream.write(b'payload')

    extraction = _extract(path, tmp_path / 'out')

    assert _contents(tmp_path / 'out') == {expected: b'payload'}
    assert [found.key for found in extraction.files] == [expected]


def test_the_format_is_read_from_the_content(tmp_path):
    # A `.zip` that is really a tar extracts. The one thing the name is used for is the output name
    # of a bare compressor.
    _extract(_tar(tmp_path, name='archive.zip'), tmp_path / 'out')

    assert _contents(tmp_path / 'out') == CONTENT


def test_a_file_that_is_no_archive_fails_with_a_reason(tmp_path):
    path = tmp_path / 'notes.txt'
    path.write_bytes(b'not an archive\n')

    with pytest.raises(UnsupportedArchive) as refusal:
        _extract(path, tmp_path / 'out')

    assert 'ZIP' in str(refusal.value)


def test_a_damaged_archive_fails_once_for_the_archive(tmp_path):
    # Truncated halfway. One message about the archive, not one per entry.
    path = _tar(tmp_path, mode='w:gz', name='archive.tar.gz')
    whole = path.read_bytes()
    path.write_bytes(whole[:len(whole) // 2])

    with pytest.raises(UnsupportedArchive) as refusal:
        _extract(path, tmp_path / 'out')

    assert 'damaged' in str(refusal.value)


def test_an_encrypted_zip_fails_before_anything_is_written(tmp_path):
    # Answered from the entry flags rather than by trying to read an entry, so the message names the
    # archive and the cause instead of the first file in it.
    path = tmp_path / 'secret.zip'
    with zipfile.ZipFile(path, 'w') as archive:
        archive.writestr('notes.txt', b'hello')
    # Bit 0 of the general purpose flags, set by hand in both places it is written: `zipfile` cannot
    # produce an encrypted archive, and `infolist` reads the central directory rather than the local
    # header - so patching only the local one would prove nothing.
    raw = bytearray(path.read_bytes())
    raw[6] |= 0x1
    raw[raw.index(b'PK\x01\x02') + 8] |= 0x1
    path.write_bytes(raw)

    with pytest.raises(UnsupportedArchive) as refusal:
        _extract(path, tmp_path / 'out')

    assert 'encrypted' in str(refusal.value)
    assert _contents(tmp_path / 'out') == {}


# ---------------------------------------------------------------- one level, never recursive

def test_an_archive_inside_the_archive_comes_out_as_a_file(tmp_path):
    # The rule, not a limitation: the limits count one archive, and nesting is the easiest denial of
    # service to write. A caller who wants the inner one extracted asks for it in a second task.
    inner = _zip(tmp_path, {'inner-notes.txt': b'inside'}, name='inner.zip')
    inner_bytes = inner.read_bytes()
    outer = _zip(tmp_path, {'inner.zip': inner_bytes, 'notes.txt': b'hello'}, name='outer.zip')

    _extract(outer, tmp_path / 'out')

    extracted = _contents(tmp_path / 'out')
    assert extracted == {'inner.zip': inner_bytes, 'notes.txt': b'hello'}
    # Byte for byte, and its own content nowhere - nothing looked inside it.
    assert 'inner-notes.txt' not in extracted


# ---------------------------------------------------------------- crafted entry names

@pytest.mark.parametrize('name', [
    # The classic escape. `/mnt/sats-shares/team/../../../../etc/cron.d/evil` is `/etc/cron.d/evil`.
    '../evil.txt',
    'data/../../evil.txt',
    # An absolute path: joining one onto the destination discards the destination.
    '/etc/cron.d/evil',
    # A backslash is one legal file name on Linux and one legal S3 key, and a path on the Windows
    # machine that syncs the storage with cloud-data or DTS.
    '..\\..\\Users\\Public\\Startup\\x.lnk',
    'data\\evil.txt',
    # Windows path joining discards the base for a drive letter: `D:/sync` + `C:evil.txt`.
    'C:evil.txt',
    'C:/Windows/evil.txt',
    # Windows drops trailing dots and spaces, so these are `..` there.
    '.. /evil.txt',
    '.../evil.txt',
    # A `\n` in a key splits one line of `pipe storage ls` into two, and of every other line-based
    # tool. A `\0` belongs here too and cannot be tested through an archive - see below.
    'notes\nevil.txt',
    'notes\tevil.txt',
    # U+202E reverses what is drawn after it: `invoice<U+202E>fdp.exe` reads as `invoiceexe.pdf`.
    'invoice\u202efdp.exe',
    'data//evil.txt',
])
@pytest.mark.parametrize('kind', ['zip', 'tar'])
def test_a_crafted_entry_name_is_refused_and_nothing_is_written(tmp_path, kind, name):
    if kind == 'tar' and '\0' in name:
        pytest.skip('a tar stores its entry names as NUL-terminated strings, so one cannot carry a '
                    'NUL - the name comes back truncated at it. The zip case covers the rule.')
    archive = (_zip(tmp_path, {name: b'x'}, name='crafted.zip') if kind == 'zip'
               else _tar(tmp_path, {name: b'x'}, name='crafted.tar'))
    target = tmp_path / 'out'

    with pytest.raises(UnsafeArchiveEntry) as refusal:
        _extract(archive, target)

    # The message names the entry, because that is what the user has to repack.
    assert 'will not write' in str(refusal.value)
    assert _contents(target) == {}


def test_a_null_in_an_entry_name_is_refused():
    # Asserted on the rule rather than through an archive, because neither `zipfile` nor `tarfile`
    # can carry such a name: both read it back truncated at the NUL. The check stays in the code as
    # defence in depth - the day a name reaches it from somewhere other than those two readers, a
    # `\0` would end the name early for every C consumer of the storage.
    with pytest.raises(UnsafeArchiveEntry) as refusal:
        safe_entry_path('notes\0evil.txt')

    assert 'null' in str(refusal.value)


@pytest.mark.parametrize('name', [
    # Dots inside a name are ordinary. Refusing these would refuse `data.tar.gz` and every dotfile.
    'data.tar.gz',
    '.hidden',
    '..hidden',
    'a/.b/c..d.txt',
    'folder with spaces/file name.txt',
    # Not Latin, and not a problem: only the control and invisible categories are refused.
    'данные/отчёт.txt',
])
def test_an_ordinary_entry_name_is_accepted(name):
    assert safe_entry_path(name) == name


def test_a_trailing_slash_is_the_folder_marker_and_not_part_of_the_name():
    assert safe_entry_path('data/') == 'data'


@pytest.mark.parametrize('name,expected', [
    ('./a.txt', 'a.txt'),
    ('./sub/b.txt', 'sub/b.txt'),
    ('data/./evil.txt', 'data/evil.txt'),
    ('./', ''),
])
def test_a_current_directory_component_is_dropped_rather_than_refused(name, expected):
    # The one normalisation this module does, and the reason is `tar -cf out.tar .` - the commonest
    # way an archive is made - which writes every entry as `./x`. A `.` traverses nowhere, so
    # dropping it is safe; the API refuses the same thing in a `destinationPath` for a different
    # reason, which `safe_entry_path`'s docstring spells out.
    assert safe_entry_path(name) == expected


def test_an_archive_made_with_tar_dot_extracts(tmp_path):
    # The regression this exists for: every entry of such an archive is `./something`, and refusing
    # `.` refused most tars in existence - including the root entry `./`, which names the destination
    # folder itself and is simply nothing to create.
    path = tmp_path / 'plain.tar'
    with tarfile.open(path, 'w') as archive:
        for name in ('./', './sub/'):
            info = tarfile.TarInfo(name)
            info.type = tarfile.DIRTYPE
            archive.addfile(info)
        for name, value in (('./a.txt', b'a'), ('./sub/b.txt', b'bb')):
            info = tarfile.TarInfo(name)
            info.size = len(value)
            archive.addfile(info, io.BytesIO(value))

    extraction = _extract(path, tmp_path / 'out')

    assert _contents(tmp_path / 'out') == {'a.txt': b'a', 'sub/b.txt': b'bb'}
    # And the keys carry no `./`, so the S3 key is `out/a.txt` and not the literal `out/./a.txt`.
    assert sorted(found.key for found in extraction.files) == ['a.txt', 'sub/b.txt']
    assert extraction.empty_folders == ()


def test_an_entry_that_resolves_outside_through_a_symlink_is_refused(tmp_path):
    # The second guard, and the one the name checks cannot replace: the name is harmless text, and
    # what the filesystem makes of it is not. Here an earlier entry of the same archive made `data`
    # a symlink out of the extraction directory.
    target = tmp_path / 'out'
    target.mkdir()
    outside = tmp_path / 'outside'
    outside.mkdir()
    os.symlink(outside, target / 'data')

    with pytest.raises(UnsafeArchiveEntry):
        _extract(_zip(tmp_path, {'data/evil.txt': b'x'}, name='crafted.zip'), target)

    assert list(outside.iterdir()) == []


# ---------------------------------------------------------------- what cannot be stored

def test_links_and_devices_are_skipped_and_regular_files_are_not(tmp_path):
    # A data storage holds files. A symlink is also a way out of the destination, and reading a FIFO
    # would block the upload for ever.
    path = tmp_path / 'mixed.tar'
    with tarfile.open(path, 'w') as archive:
        regular = tarfile.TarInfo('notes.txt')
        regular.size = 5
        archive.addfile(regular, io.BytesIO(b'hello'))
        link = tarfile.TarInfo('link.txt')
        link.type = tarfile.SYMTYPE
        link.linkname = 'notes.txt'
        archive.addfile(link)
        fifo = tarfile.TarInfo('pipe')
        fifo.type = tarfile.FIFOTYPE
        archive.addfile(fifo)

    extraction = _extract(path, tmp_path / 'out')

    assert _contents(tmp_path / 'out') == {'notes.txt': b'hello'}
    assert set(extraction.skipped) == {'link.txt', 'pipe'}


def test_only_the_folders_no_file_lands_in_are_reported_as_empty(tmp_path):
    # A folder with files in it needs nothing in the destination: writing its files creates it on
    # both backends. Only an empty one has to be asked for, and only a share can hold it.
    path = _zip(tmp_path, {'data/values.csv': b'1'}, folders=('data', 'empty', 'empty/deeper'))

    extraction = _extract(path, tmp_path / 'out')

    assert 'data' not in extraction.empty_folders
    assert set(extraction.empty_folders) == {'empty', 'empty/deeper'}


# ---------------------------------------------------------------- the limits

def test_too_many_entries_stops_the_extraction(tmp_path):
    path = _zip(tmp_path, {f'file-{index}.txt': b'x' for index in range(5)})

    with pytest.raises(ThresholdExceeded) as refusal:
        _extract(path, tmp_path / 'out', ExtractionLimits(max_files_count=3))

    assert 'entries' in str(refusal.value)


def test_folder_entries_count_against_the_entry_limit(tmp_path):
    # An archive of nothing but empty folders is a few kilobytes, and counting only the files let it
    # through every limit - and then cost a request per folder in the collision pass and a directory
    # each on a share.
    path = _zip(tmp_path, {}, folders=[f'folder-{index}' for index in range(5)])

    with pytest.raises(ThresholdExceeded):
        _extract(path, tmp_path / 'out', ExtractionLimits(max_files_count=3))


def test_a_file_and_a_folder_at_one_path_inside_one_archive_fails_with_a_reason(tmp_path):
    # Legal to put in a zip, impossible to write anywhere. Reported as the archive's problem rather
    # than as an internal error of this service.
    path = _zip(tmp_path, {'a': b'a file', 'a/b.txt': b'inside it'})

    with pytest.raises(UnsupportedArchive) as refusal:
        _extract(path, tmp_path / 'out')

    # The entry is named, not the clash: the file in the way may be any ancestor of this path, and
    # naming the wrong one would send the user looking for a pair that does not exist.
    assert 'needs a folder' in str(refusal.value)


def test_a_symlink_in_a_zip_is_skipped_as_one_in_a_tar_is(tmp_path):
    # A zip has no entry type: the unix mode is in the top half of `external_attr`. Written out as a
    # file the entry would be a file holding a path.
    path = tmp_path / 'linked.zip'
    with zipfile.ZipFile(path, 'w') as archive:
        archive.writestr('notes.txt', b'hello')
        link = zipfile.ZipInfo('link.txt')
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        archive.writestr(link, b'notes.txt')

    extraction = _extract(path, tmp_path / 'out')

    assert _contents(tmp_path / 'out') == {'notes.txt': b'hello'}
    assert extraction.skipped == ('link.txt',)


def test_more_bytes_than_allowed_stops_the_extraction_mid_way(tmp_path):
    # The limit that cannot be lied to. Every size in an archive header is written by whoever built
    # the archive, and for a tar.bz2 there is no declared total at all - so this counter is the only
    # thing that stops a bomb.
    path = _tar(tmp_path, {'big.bin': b'x' * 10000}, mode='w:bz2', name='bomb.tar.bz2')

    with pytest.raises(ThresholdExceeded) as refusal:
        _extract(path, tmp_path / 'out', ExtractionLimits(max_size_bytes=1000))

    assert 'content' in str(refusal.value)


def test_a_limit_of_none_means_no_limit(tmp_path):
    extraction = _extract(_zip(tmp_path), tmp_path / 'out',
                          ExtractionLimits(max_files_count=None, max_size_bytes=None))

    assert extraction.total_bytes == TOTAL


def test_the_heartbeat_is_called_between_entries(tmp_path):
    # What makes a cancellation take effect inside one poll interval instead of at the end of the
    # archive. It raises here, as the real one does when the task was cancelled.
    calls = []

    def heartbeat():
        calls.append(1)
        if len(calls) > 1:
            raise RuntimeError('stop')

    with pytest.raises(RuntimeError):
        _extract(_zip(tmp_path, {f'file-{index}.txt': b'x' for index in range(5)}),
                 tmp_path / 'out', heartbeat=heartbeat)

    # Far fewer than five: it stopped at the second entry rather than extracting everything first.
    assert len(calls) == 2


def test_a_limit_passed_inside_a_bare_compressed_file_is_not_reported_as_damage(tmp_path):
    # The decompressors report a damaged stream as half a dozen unrelated types, so the arm that
    # catches them is broad - and without an exception for our own, a breached limit would be
    # reported as "the archive is damaged" and the user would go looking for the wrong problem.
    path = tmp_path / 'data.csv.gz'
    with gzip.open(path, 'wb') as stream:
        stream.write(b'x' * 10000)

    with pytest.raises(ThresholdExceeded):
        _extract(path, tmp_path / 'out', ExtractionLimits(max_size_bytes=100))


def test_a_cancellation_inside_a_bare_compressed_file_is_not_reported_as_damage(tmp_path):
    # The same arm, and the consequence that matters more: a cancelled task reported as a damaged
    # archive would end as FAILED instead of CANCELLED.
    path = tmp_path / 'data.csv.gz'
    with gzip.open(path, 'wb') as stream:
        stream.write(b'x' * 10000)

    def heartbeat():
        raise StopRequested(StopReason.CANCELLED)

    with pytest.raises(StopRequested):
        _extract(path, tmp_path / 'out', heartbeat=heartbeat)


@pytest.mark.parametrize('name', ['./C:evil.txt', './D:payload.lnk', './C:/Windows/evil'])
def test_a_drive_letter_hidden_behind_a_current_directory_component_is_refused(name):
    # The regression that dropping `.` introduced: the check used to look at the start of the raw
    # name, and `./C:evil.txt` starts with a dot. Once the dot is dropped the drive letter is in
    # first place, and Windows path joining resets on it - so `C:\sync` + `C:evil.txt` is
    # `C:evil.txt`, outside the sync root of whoever mirrors the storage with cloud-data or DTS.
    with pytest.raises(UnsafeArchiveEntry) as refusal:
        safe_entry_path(name)

    assert 'drive letter' in str(refusal.value)


def test_a_drive_letter_below_the_first_component_is_an_ordinary_name():
    # Only the first component matters: Windows resets the join on a drive letter at the start of
    # what is joined on, and refusing this would refuse a legal name.
    assert safe_entry_path('a/C:evil.txt') == 'a/C:evil.txt'


def test_an_archive_naming_one_thing_a_file_and_a_folder_is_refused(tmp_path):
    # A zip can carry `a` and `a/` at once, and nothing can hold both. Folders are never created
    # locally, so the extraction itself does not trip over it - without this check the task would
    # write the file into the destination and then fail on the folder, leaving what it wrote. On an
    # object storage, which has no folders, the same archive would pass silently.
    path = tmp_path / 'clash.zip'
    with zipfile.ZipFile(path, 'w') as archive:
        archive.writestr('a', b'a file')
        archive.writestr('a/', b'')

    with pytest.raises(UnsupportedArchive) as refusal:
        _extract(path, tmp_path / 'out')

    assert 'both a file and a folder' in str(refusal.value)


def test_the_order_of_the_clashing_entries_does_not_matter(tmp_path):
    path = tmp_path / 'clash.zip'
    with zipfile.ZipFile(path, 'w') as archive:
        archive.writestr('b/', b'')
        archive.writestr('b', b'a file')

    with pytest.raises(UnsupportedArchive):
        _extract(path, tmp_path / 'out')


def test_a_folder_that_holds_the_archives_files_is_not_a_clash(tmp_path):
    # The ordinary case: `data/` and `data/values.csv`. A folder entry with files under it is not a
    # clash, and refusing it would refuse most archives.
    path = _zip(tmp_path, {'data/values.csv': b'1'}, folders=('data',))

    extraction = _extract(path, tmp_path / 'out')

    assert [found.key for found in extraction.files] == ['data/values.csv']
