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
Opens an archive onto local disk and checks every name it holds.

Nobody else checks the entry names: the API never opens the archive. A bad name is refused, not
repaired. Every path is checked again after `resolve()`. Nothing is passed to `tarfile.extract` or
`ZipFile.extract`. The bytes are counted while they are written.
"""

from __future__ import annotations

import errno
import stat
import tarfile
import unicodedata
import zipfile
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath

from ...util.archive_probe import ArchiveFormat
from ...datastorage_providers.base import DOWNLOAD_BLOCK_BYTES, Heartbeat
from ...util.errors import StopRequested, ThresholdExceeded, UnsafeArchiveEntry, UnsupportedArchive
from ...util.log import get_logger
from ...util.paths import is_inside
from ...util.sizes import human_bytes

LOG = get_logger(__name__)

# Control and invisible characters. A `\n` breaks line-based tools, and `U+202E` reverses the name.
FORBIDDEN_CATEGORIES = frozenset({'Cc', 'Cf'})

# Windows drops trailing dots and spaces, so `".. "` is `".."` there.
DOTS_AND_SPACES = frozenset('. ')

CURRENT_DIRECTORY = '.'

SINGLE_STREAM_SUFFIXES = {ArchiveFormat.GZIP: '.gz', ArchiveFormat.BZIP2: '.bz2',
                          ArchiveFormat.XZ: '.xz'}

TAR_TRANSPARENT_MODE = 'r:*'

# Raised by this module itself, not by a compression library. `_single_stream` lets these through
# before its broad `except`, which would otherwise report a cancelled task as a damaged archive.
OWN_EXCEPTIONS = (StopRequested, ThresholdExceeded, UnsafeArchiveEntry, UnsupportedArchive)


@dataclass(frozen=True, slots=True)
class ExtractionLimits:
    """The entry count and the uncompressed total one archive may hold. `None` means no limit."""

    max_files_count: int | None = None
    max_size_bytes: int | None = None


@dataclass(frozen=True, slots=True)
class ExtractedFile:
    """One file that came out of the archive, and where it lies locally."""

    # Its path inside the archive, and the tail of its key in the destination.
    key: str
    path: Path
    size: int


@dataclass(frozen=True, slots=True)
class Extraction:
    """What came out of one archive."""

    files: tuple[ExtractedFile, ...] = ()
    # Folders no extracted file lands in.
    empty_folders: tuple[str, ...] = ()
    # Symlinks, hardlinks, devices and FIFOs.
    skipped: tuple[str, ...] = ()
    total_bytes: int = 0
    archive_format: ArchiveFormat = ArchiveFormat.UNKNOWN


@dataclass
class _Budget:
    """How much of the limits is left."""

    limits: ExtractionLimits
    entries: int = 0
    total_bytes: int = 0
    skipped: list[str] = field(default_factory=list)

    def take_entry(self, name: str) -> None:
        """Counts one entry, folders included, and refuses the archive when there are too many."""
        self.entries += 1
        limit = self.limits.max_files_count
        if limit is not None and self.entries > limit:
            raise ThresholdExceeded(
                f'The archive holds more than {limit} entries, which is the maximum one archive may '
                f'be extracted into. Split it, or ask an administrator to raise the limit.')

    def take_bytes(self, count: int) -> None:
        """Counts bytes as they are written and refuses the archive the moment the total is passed."""
        self.total_bytes += count
        limit = self.limits.max_size_bytes
        if limit is not None and self.total_bytes > limit:
            raise ThresholdExceeded(
                f'The archive holds more than {human_bytes(limit)} of content, which is the maximum one '
                f'archive may be extracted into. Split it, or ask an administrator to raise the '
                f'limit.')


def extract(archive: Path, target_dir: Path, archive_name: str, archive_format: ArchiveFormat,
            limits: ExtractionLimits, heartbeat: Heartbeat) -> Extraction:
    """
    Extracts the archive into `target_dir` and says what came out.

    `archive_format` comes from the content. `archive_name` is used only by the bare compressors.
    The heartbeat runs between entries.
    """
    target_dir.mkdir(parents=True, exist_ok=True)
    budget = _Budget(limits)
    LOG.info('Extracting a %s archive into %s', archive_format, target_dir)
    try:
        match archive_format:
            case ArchiveFormat.ZIP:
                extraction = _extract_zip(archive, target_dir, budget, heartbeat)
            case ArchiveFormat.TAR:
                extraction = _extract_tar(archive, target_dir, budget, heartbeat)
            case ArchiveFormat.GZIP | ArchiveFormat.BZIP2 | ArchiveFormat.XZ:
                # A compressor says nothing about what it wraps.
                if tarfile.is_tarfile(archive):
                    extraction = _extract_tar(archive, target_dir, budget, heartbeat)
                else:
                    extraction = _single_stream(archive, target_dir, archive_name, archive_format,
                                                budget, heartbeat)
            case _:
                raise UnsupportedArchive(
                    f'{archive_name} is not an archive of a format this service can extract. '
                    f'Supported are ZIP, TAR, TAR.GZ, TGZ, TAR.BZ2, TAR.XZ, GZ, BZ2 and XZ, and the '
                    f'format is read from the content rather than from the name.')
    except (tarfile.TarError, zipfile.BadZipFile, EOFError) as damaged:
        raise UnsupportedArchive(
            f'{archive_name} could not be read to the end: {damaged}. The archive is damaged or was '
            f'uploaded incompletely.') from damaged
    _assert_no_file_and_folder_clash(extraction)
    LOG.info('Extracted %s file(s), %s bytes, %s entry(ies) skipped', len(extraction.files),
             extraction.total_bytes, len(extraction.skipped))
    for skipped in extraction.skipped:
        LOG.info('Skipped %s: a data storage holds files, and this entry is not one', skipped)
    return extraction


def _assert_no_file_and_folder_clash(extraction: Extraction) -> None:
    """Refuses an archive that names one thing both a file and a folder."""
    folders = set(extraction.empty_folders)
    clashing = sorted(folders.intersection(found.key for found in extraction.files))
    if clashing:
        raise UnsupportedArchive(
            f'The archive names {clashing[0]} both a file and a folder, which cannot both exist. '
            f'Repack it without that clash.')


def safe_entry_path(name: str) -> str:
    """
    The entry's path inside the archive, or a refusal saying what is wrong with it.

    Refused: an absolute path, a `..` component, a backslash, a drive letter, a component of dots
    and spaces only, and the characters of `FORBIDDEN_CATEGORIES`.

    A `.` component is dropped instead, because `tar -cf out.tar .` writes every entry as `./x`. A
    name that is nothing but `.` answers with the empty string.
    """
    cleaned = name.replace('\0', '')
    if cleaned != name:
        raise UnsafeArchiveEntry(_refusal(name, 'a null character'))
    stripped = name.rstrip('/')
    if not stripped:
        raise UnsafeArchiveEntry(_refusal(name, 'no name at all'))
    if '\\' in stripped:
        raise UnsafeArchiveEntry(_refusal(name, 'a backslash, which is a path separator on Windows'))
    if stripped.startswith('/'):
        raise UnsafeArchiveEntry(_refusal(name, 'an absolute path'))
    if _has_drive_letter(stripped):
        raise UnsafeArchiveEntry(_refusal(name, 'a Windows drive letter'))
    for character in stripped:
        if unicodedata.category(character) in FORBIDDEN_CATEGORIES:
            raise UnsafeArchiveEntry(_refusal(
                name, f'the character U+{ord(character):04X}, which is invisible or a control '
                      f'character'))
    kept = []
    for component in stripped.split('/'):
        if component == CURRENT_DIRECTORY:
            continue
        if not component:
            raise UnsafeArchiveEntry(_refusal(name, 'an empty path component'))
        if set(component) <= DOTS_AND_SPACES:
            raise UnsafeArchiveEntry(_refusal(
                name, f'the path component {component!r}, which is dots and spaces only'))
        kept.append(component)
    key = '/'.join(kept)
    if _has_drive_letter(key):
        # Dropping a leading `./` can move a drive letter into first place.
        raise UnsafeArchiveEntry(_refusal(name, 'a Windows drive letter'))
    return key


def _file_key(name: str) -> str:
    """`safe_entry_path` for an entry that carries bytes, where having no name is a refusal."""
    key = safe_entry_path(name)
    if not key:
        raise UnsafeArchiveEntry(_refusal(name, 'no name of its own'))
    return key


def _refusal(name: str, what: str) -> str:
    """The message of a refused entry. It says nothing about the destination."""
    return (f'The archive holds an entry this service will not write: {name!r} carries {what}. '
            f'Repack the archive with ordinary relative paths.')


def _is_symlink(entry: zipfile.ZipInfo) -> bool:
    """Whether a zip entry is a symlink. The unix mode is the top half of `external_attr`."""
    return stat.S_ISLNK(entry.external_attr >> 16)


def _has_drive_letter(name: str) -> bool:
    """Whether the name starts with `C:`, which Windows joins by discarding the base."""
    return len(name) >= 2 and name[1] == ':' and name[0].isalpha()


def _extract_zip(archive: Path, target_dir: Path, budget: _Budget,
                 heartbeat: Heartbeat) -> Extraction:
    """Extracts a zip entry by entry. `zipfile` checks no names, so every rule is applied here."""
    files: list[ExtractedFile] = []
    folders: list[str] = []
    with zipfile.ZipFile(archive) as opened:
        entries = opened.infolist()
        _assert_not_encrypted(entries, archive.name)
        for entry in entries:
            heartbeat()
            key = safe_entry_path(entry.filename)
            budget.take_entry(key or entry.filename)
            if entry.is_dir():
                if key:
                    folders.append(key)
                continue
            if _is_symlink(entry):
                budget.skipped.append(key)
                continue
            key = _file_key(entry.filename)
            target = _target_of(target_dir, key)
            with opened.open(entry) as source:
                size = _write(source, target, budget, heartbeat)
            files.append(ExtractedFile(key=key, path=target, size=size))
    return Extraction(files=tuple(files), empty_folders=_empty_folders(folders, files),
                      skipped=tuple(budget.skipped), total_bytes=budget.total_bytes,
                      archive_format=ArchiveFormat.ZIP)


def _extract_tar(archive: Path, target_dir: Path, budget: _Budget,
                 heartbeat: Heartbeat) -> Extraction:
    """
    Extracts a tar, whatever it is compressed with. Only regular files are written.

    The members are read one at a time, because a tar has no index.
    """
    files: list[ExtractedFile] = []
    folders: list[str] = []
    with tarfile.open(archive, mode=TAR_TRANSPARENT_MODE) as opened:
        for member in opened:
            heartbeat()
            key = safe_entry_path(member.name)
            budget.take_entry(key or member.name)
            if member.isdir():
                if key:
                    # An empty key is the archive's own root.
                    folders.append(key)
                continue
            if not member.isfile():
                budget.skipped.append(key)
                continue
            key = _file_key(member.name)
            target = _target_of(target_dir, key)
            source = opened.extractfile(member)
            if source is None:
                budget.skipped.append(key)
                continue
            with source:
                size = _write(source, target, budget, heartbeat)
            files.append(ExtractedFile(key=key, path=target, size=size))
    return Extraction(files=tuple(files), empty_folders=_empty_folders(folders, files),
                      skipped=tuple(budget.skipped), total_bytes=budget.total_bytes,
                      archive_format=ArchiveFormat.TAR)


def _single_stream(archive: Path, target_dir: Path, archive_name: str,
                   archive_format: ArchiveFormat, budget: _Budget,
                   heartbeat: Heartbeat) -> Extraction:
    """
    Extracts a bare `GZ`, `BZ2` or `XZ`: one stream in, one file out.

    The output is named after the archive with its final extension removed, not after the name in
    the gzip header.
    """
    opener = _single_stream_opener(archive_format)
    key = safe_entry_path(_single_stream_name(archive_name, archive_format))
    budget.take_entry(key)
    target = _target_of(target_dir, key)
    try:
        with opener(archive, 'rb') as source:
            size = _write(source, target, budget, heartbeat)
    except OWN_EXCEPTIONS:
        raise
    except Exception as error:
        # Broad on purpose: the three compression libraries report a damaged stream as half a dozen
        # unrelated types.
        raise UnsupportedArchive(
            f'{archive_name} could not be decompressed: {error}. The archive is damaged or was '
            f'uploaded incompletely.') from error
    return Extraction(files=(ExtractedFile(key=key, path=target, size=size),),
                      skipped=tuple(budget.skipped), total_bytes=budget.total_bytes,
                      archive_format=archive_format)


def _single_stream_opener(archive_format: ArchiveFormat):
    """The `open` of the module that reads this compressor."""
    match archive_format:
        case ArchiveFormat.GZIP:
            import gzip
            return gzip.open
        case ArchiveFormat.BZIP2:
            import bz2
            return bz2.open
        case ArchiveFormat.XZ:
            import lzma
            return lzma.open
        case _:
            raise UnsupportedArchive(f'{archive_format} is not a single compressed stream')


def _single_stream_name(archive_name: str, archive_format: ArchiveFormat) -> str:
    """The archive's file name without its compression extension, matched case-insensitively."""
    name = PurePosixPath(archive_name).name
    suffix = SINGLE_STREAM_SUFFIXES[archive_format]
    if name.lower().endswith(suffix):
        return name[:-len(suffix)] or name
    return name


def _assert_not_encrypted(entries: list[zipfile.ZipInfo], archive_name: str) -> None:
    """Refuses a password-protected zip once, reading bit 0 of the general purpose flags."""
    if any(entry.flag_bits & 0x1 for entry in entries):
        raise UnsupportedArchive(
            f'{archive_name} is encrypted, and this service has no password for it. Extract it where '
            f'the password is known and upload the content instead.')


def _target_of(target_dir: Path, key: str) -> Path:
    """
    Where an entry is written locally, refusing anything that leaves the extraction directory.

    The last word on the path: the name checks read the text, this reads what the filesystem makes
    of it.
    """
    target = target_dir / key
    if not is_inside(target, target_dir):
        raise UnsafeArchiveEntry(_refusal(key, 'a path that leads outside the extraction directory'))
    try:
        target.parent.mkdir(parents=True, exist_ok=True)
    except (FileExistsError, NotADirectoryError) as taken:
        raise UnsupportedArchive(
            f'The archive holds a file where {key} needs a folder, so both cannot exist. Repack it '
            f'without that clash.') from taken
    if target.is_dir():
        raise UnsupportedArchive(
            f'The archive names {key} both a file and a folder, which cannot both exist. Repack it '
            f'without that clash.')
    return target


def _write(source, target: Path, budget: _Budget, heartbeat: Heartbeat) -> int:
    """
    Copies one entry onto disk, block by block, counting its bytes.

    A full disk is reported as a limit rather than as an `OSError`.
    """
    written = 0
    try:
        with target.open('wb') as out:
            while chunk := _read_block(source):
                budget.take_bytes(len(chunk))
                out.write(chunk)
                written += len(chunk)
                heartbeat()
    except OSError as error:
        if error.errno == errno.ENOSPC:
            raise ThresholdExceeded(
                f'The extraction ran out of local disk space after {human_bytes(budget.total_bytes)}. '
                f'The archive declared less than it holds. Ask an administrator to give the service '
                f'more space, or split the archive.') from error
        raise
    return written


def _read_block(source) -> bytes:
    """
    One block out of an entry.

    `zipfile` reports a member it cannot decompress as a bare `RuntimeError`. The translation covers
    the read alone, so nothing the heartbeat raises is reported as a damaged archive.
    """
    try:
        return source.read(DOWNLOAD_BLOCK_BYTES)
    except RuntimeError as error:
        raise UnsupportedArchive(f'An entry of the archive could not be decompressed: {error}') \
            from error


def _empty_folders(folders: list[str], files: list[ExtractedFile]) -> tuple[str, ...]:
    """The folders of the archive that no extracted file lands in."""
    occupied = {str(PurePosixPath(found.key).parent) for found in files}
    # Every ancestor counts too: `a/b/c.txt` fills `a` as well as `a/b`.
    for parent in tuple(occupied):
        current = PurePosixPath(parent)
        while str(current) not in ('.', '/'):
            occupied.add(str(current))
            current = current.parent
    return tuple(folder for folder in folders if folder not in occupied)
