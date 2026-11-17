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
Opening an archive onto local disk, and the checks that makes necessary.

This is the security-critical half of extracting an archive, and the reason is the arithmetic of the
final path:

    /mnt/sats-shares/<share>  +  destinationPath  +  the entry's path inside the archive
             mount root           checked by the API        checked by nothing but this module

The API never opens the archive, so it never sees the entry names - and there are up to
`maxFilesCount` of them against the one path it did check. Everything here exists because of that:

  * **every name is refused rather than repaired.** A path that leaves the destination, a backslash,
    a drive letter, an invisible or a control character fails the whole task on the first such entry.
    Repairing one would mean deciding what the person who built the archive meant, and being wrong
    about it silently;
  * **every path is checked again after `resolve()`**, with `util.paths.is_inside`. That is the
    guard, not a formality: `team/../../../../etc/cron.d/evil` resolves outside the extraction
    directory, this service runs as root, and its node holds a role of every storage on the platform;
  * **nothing is handed to `tarfile.extract` or `ZipFile.extract`.** The bytes are read from the
    member and written to a path this module computed, so no library decides where a file lands -
    which is also why `filter='data'` is not needed here, and why no setuid bit, owner or ACL from
    the archive ever reaches the disk: the files are created fresh, with this process's own defaults;
  * **the bytes are counted while they are written.** Every size an archive declares was written by
    whoever built it, so the declared total of `archive_probe` is a hint. This counter is the limit
    that cannot be lied to, and for `tar.bz2`, which declares nothing, it is the only one there is.

**One level, never recursive.** The archive is opened once and whatever comes out is a file, an
entry named `inner.zip` included. That is a rule and not a limitation to be lifted: the limits count
one archive, and deliberate nesting is the easiest denial of service to write. Nothing here carries
a depth argument or looks at what it has just written.
"""

from __future__ import annotations

import errno
import stat
import tarfile
import unicodedata
import zipfile
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath

from .archive_probe import ArchiveFormat
from .datastorage_providers.base import DOWNLOAD_BLOCK_BYTES, Heartbeat
from .util.errors import StopRequested, ThresholdExceeded, UnsafeArchiveEntry, UnsupportedArchive
from .util.log import get_logger
from .util.paths import is_inside
from .util.sizes import human_bytes

LOG = get_logger(__name__)

# Characters no entry name may hold, by Unicode category rather than by a list:
#
#   Cc  the control characters. A `\n` in a key splits one line of `pipe storage ls` - and of every
#       other line-based tool - into two, and a `\0` ends the name early for anything written in C.
#   Cf  the invisible formatting characters. No path escape at all: `invoice<U+202E>fdp.exe` is drawn
#       as `invoiceexe.pdf`, and the person clicks it expecting a document.
FORBIDDEN_CATEGORIES = frozenset({'Cc', 'Cf'})

# A component of nothing but dots and spaces. Windows drops trailing dots and spaces, so `".. "` is
# `".."` there and `"..."` is nothing - a name that walks up a level on the machine of whoever syncs
# the storage with `cloud-data` or DTS.
DOTS_AND_SPACES = frozenset('. ')

# The one component that is dropped rather than refused - see `safe_entry_path`.
CURRENT_DIRECTORY = '.'

# The archive's own compression, which the output of a bare compressor drops from its name.
SINGLE_STREAM_SUFFIXES = {ArchiveFormat.GZIP: '.gz', ArchiveFormat.BZIP2: '.bz2',
                          ArchiveFormat.XZ: '.xz'}

# The mode `tarfile` is opened with: the compression is read from the content, whatever the name says.
TAR_TRANSPARENT_MODE = 'r:*'

# What this module raises at itself and at its caller, as opposed to what a compression library
# raises at it. Named, because a broad `except` around a decompressor has to let these through: a
# limit that was passed, a name that was refused and a task that is being abandoned are all answers
# in their own right, and reporting one of them as a damaged archive would be a lie.
OWN_EXCEPTIONS = (StopRequested, ThresholdExceeded, UnsafeArchiveEntry, UnsupportedArchive)


@dataclass(frozen=True, slots=True)
class ExtractionLimits:
    """
    The two limits counted during the extraction. `None` in either means the administrator removed it.

    They are the `tasks.storageUnpackArchive` section's `maxFilesCount` and `maxSizeBytes`, and they
    mean the entry count and the uncompressed total of one archive.
    """

    max_files_count: int | None = None
    max_size_bytes: int | None = None


@dataclass(frozen=True, slots=True)
class ExtractedFile:
    """One file that came out of the archive, and where it is to go."""

    # Its path inside the archive: posix separators, no leading slash, already checked. Also the tail
    # of its key in the destination, since the archive's structure is preserved as it is.
    key: str
    # Where it lies locally until it is uploaded.
    path: Path
    size: int


@dataclass(frozen=True, slots=True)
class Extraction:
    """What came out of one archive."""

    files: tuple[ExtractedFile, ...] = ()
    # Folders the archive holds that no extracted file lands in. Only these need creating in the
    # destination, and only a share can hold one - see `FileDestination.create_folder`.
    empty_folders: tuple[str, ...] = ()
    # Entries that cannot be represented in a data storage: symlinks, hardlinks, devices, FIFOs.
    # Logged, and deliberately not reported in the task's result - an archive may hold thousands of
    # symlinks, so a list would need a cap and a bare count would not tell a user what to do.
    skipped: tuple[str, ...] = ()
    total_bytes: int = 0
    archive_format: ArchiveFormat = ArchiveFormat.UNKNOWN


@dataclass
class _Budget:
    """
    How much of the limits is left, carried through the extraction.

    Mutable and passed around rather than returned, because both limits have to be checked the
    moment they are passed - in the middle of writing one file, if that is where it happens.
    """

    limits: ExtractionLimits
    entries: int = 0
    total_bytes: int = 0
    skipped: list[str] = field(default_factory=list)

    def take_entry(self, name: str) -> None:
        """
        Counts one more entry, refusing the archive when there are too many.

        **Every entry, not only the files**: a folder is an entry too, and the preference calls this
        limit the entry count of one archive. Counting files alone left a hole worth closing - an
        archive of ninety thousand empty folders is a few megabytes, passes every limit, and then
        costs a request per folder in the collision pass and ninety thousand directories on a share.
        """
        self.entries += 1
        limit = self.limits.max_files_count
        if limit is not None and self.entries > limit:
            raise ThresholdExceeded(
                f'The archive holds more than {limit} entries, which is the maximum one archive may '
                f'be extracted into. Split it, or ask an administrator to raise the limit.')

    def take_bytes(self, count: int) -> None:
        """Counts bytes as they are written, refusing the archive the moment the total is passed."""
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

    `archive_format` comes from `archive_probe.detect_format`, so the name of the file decides
    nothing: a `.zip` that is really a tar extracts, and `TGZ` is not a special case. `archive_name`
    is the archive's own file name, needed only by the bare compressors, whose output is named after
    it.

    The heartbeat is called between entries, so a cancellation takes effect within one poll interval
    unless a single entry is enormous.
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
                # A compressor says nothing about what it wraps, so the two cases are told apart
                # here: `is_tarfile` decompresses the first header block and checks it.
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
        # A truncated or corrupt archive. One message for the archive, not one per entry.
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
    """
    Refuses an archive that names the same thing a file and a folder.

    A zip can carry `a` and `a/` at once, and nothing can hold both. The local extraction does not
    notice: folders are only recorded, never created on disk, so there is nothing for `_target_of`
    to trip over. Without this the task would reach the destination, write the file, and then fail
    on the folder - leaving the files it had written - while an object storage, which has no folders
    to clash with, would let the same archive through silently. One answer for both backends, and it
    comes before anything is written.
    """
    folders = set(extraction.empty_folders)
    clashing = sorted(folders.intersection(found.key for found in extraction.files))
    if clashing:
        raise UnsupportedArchive(
            f'The archive names {clashing[0]} both a file and a folder, which cannot both exist. '
            f'Repack it without that clash.')


def safe_entry_path(name: str) -> str:
    """
    The entry's path inside the archive, or a refusal naming what is wrong with it.

    Refused and never repaired - see the module docstring. The rules, and whom each protects:

      * **an absolute path**: joining one onto the destination discards the destination, in Python
        as in a shell, so the request would quietly become a different one;
      * **a `..` component**: the classic escape, and the one `is_inside` also catches. Both, because
        this one can name the entry in the message while `is_inside` can only say the path left;
      * **a backslash anywhere**: `..\\..\\Users\\Public\\Startup\\x.lnk` is one legal file name on
        Linux and one legal S3 key. On the Windows machine that syncs the storage with `cloud-data`
        or DTS it is a path, and it leaves the sync root;
      * **a drive letter**: Windows path joining discards the base, so `D:/sync` + `C:evil.txt` is
        `C:evil.txt`;
      * **a component of nothing but dots and spaces**, `.` excepted: `".."` under another spelling
        on Windows;
      * **control and invisible characters**: `FORBIDDEN_CATEGORIES`.

    A dot inside a name is fine - `data.tar.gz`, `.hidden` and `..hidden` are ordinary names.

    **A `.` component is the one thing dropped rather than refused**, and it is the one case where
    this function normalises instead of judging. `tar -cf out.tar .` - the commonest way an archive
    is made - writes every entry as `./x`, so refusing `.` would refuse most tars in existence for a
    path that traverses nowhere. Dropping it is safe here and is *not* safe in
    `AsyncTaskParamsValidator` on the API side, which refuses the same thing: there the string whose
    permissions were checked has to be the string that was recorded, and here nobody checked an entry
    name at all. The key is built from what this function returns, so `./a.txt` lands at `a.txt`
    rather than at the literal S3 key `./a.txt`.
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
    # An empty result means the name was nothing but `.` - the root entry `tar -cf out.tar .` writes,
    # which names the destination folder itself. Only a folder entry can be named that, and there is
    # nothing to create for it; `_file_key` refuses it for a file.
    key = '/'.join(kept)
    if _has_drive_letter(key):
        # Checked again, on the assembled key. Dropping a leading `./` moves whatever follows into
        # first place, so `./D:payload.lnk` passes the check above - where the string begins with a
        # dot - and would come out as the key `D:payload.lnk`. Only the first component matters:
        # Windows path joining resets on a drive letter at the start of what is joined on, and
        # `a/C:evil.txt` is an ordinary name there.
        raise UnsafeArchiveEntry(_refusal(name, 'a Windows drive letter'))
    return key


def _file_key(name: str) -> str:
    """`safe_entry_path` for an entry that carries bytes, where having no name is a refusal."""
    key = safe_entry_path(name)
    if not key:
        raise UnsafeArchiveEntry(_refusal(name, 'no name of its own'))
    return key


def _refusal(name: str, what: str) -> str:
    """
    One sentence for the user, naming the entry and what about it was refused.

    It says nothing about the state of the destination: the worker appends that to every failure it
    reports, and it is the only thing that knows how many files were written.
    """
    return (f'The archive holds an entry this service will not write: {name!r} carries {what}. '
            f'Repack the archive with ordinary relative paths.')


def _is_symlink(entry: zipfile.ZipInfo) -> bool:
    """
    Whether a zip entry is a symlink rather than a file.

    A zip has no entry type: the unix mode sits in the top half of `external_attr`, and an entry
    written by `zip -y` or by `shutil.make_archive` on a tree with links carries `S_IFLNK` there with
    the target path as its content. Without this check such an entry would be written out as an
    ordinary file holding a path, which is neither what the archive meant nor what
    `Extraction.skipped` promises.
    """
    return stat.S_ISLNK(entry.external_attr >> 16)


def _has_drive_letter(name: str) -> bool:
    """`C:`, `C:/` or `C:evil.txt` - a Windows path that is not relative to anything we chose."""
    return len(name) >= 2 and name[1] == ':' and name[0].isalpha()


def _extract_zip(archive: Path, target_dir: Path, budget: _Budget,
                 heartbeat: Heartbeat) -> Extraction:
    """
    Extracts a zip entry by entry.

    `zipfile` filters nothing at all - no `..`, no absolute path, no name check of any kind - so
    every rule above is this function's to apply.

    Encryption is answered once, before anything is written: a password-protected archive would
    otherwise fail on its first entry and say nothing about the cause.
    """
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
                # A zip carries a symlink as an ordinary entry with the unix mode in its external
                # attributes, so `is_dir()` is false and the content is the target path. Written as a
                # file it would be a file holding a path, which is not what the archive meant.
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
    Extracts a tar, whatever it is compressed with.

    Only regular files are written. A symlink, a hardlink, a device or a FIFO cannot be represented
    in a data storage, and a FIFO has a second reason: reading one would block the upload for ever.
    They are skipped and logged, and on a share that is deliberate rather than a shortcoming - a
    link is otherwise a way out of the destination.

    The iteration is `for member in opened`, which reads the headers as it goes rather than
    building the whole member list first: a tar has no index, so the list would mean reading the
    archive twice.
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
                    # An empty key is the archive's own root - `tar -cf out.tar .` writes `./` - and
                    # that names the destination folder itself.
                    folders.append(key)
                continue
            if not member.isfile():
                budget.skipped.append(key)
                continue
            key = _file_key(member.name)
            target = _target_of(target_dir, key)
            source = opened.extractfile(member)
            if source is None:
                # `isfile()` said otherwise, so this is a tar whose header and body disagree.
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
    Extracts a bare `GZ`, `BZ2` or `XZ`: one compressed stream, one output file.

    No container, so there is no entry list and nothing to iterate - which is why this is a code
    path of its own rather than a case inside the others.

    **The output name is the archive's own, with its final extension removed**: `data.csv.gz` gives
    `data.csv`, and `backup.gz` gives `backup`. Not the original name from the gzip header,
    deliberately: that field is written by whoever built the archive, so using it would put an
    attacker-chosen name back into the one path the API had validated.
    """
    opener = _single_stream_opener(archive_format)
    key = safe_entry_path(_single_stream_name(archive_name, archive_format))
    budget.take_entry(key)
    target = _target_of(target_dir, key)
    try:
        with opener(archive, 'rb') as source:
            size = _write(source, target, budget, heartbeat)
    except OWN_EXCEPTIONS:
        # Ours, and each already the right answer with the right message: a limit was passed, a name
        # was refused, the disk filled up, or the task is being abandoned. The arm below is broad on
        # purpose - `gzip`, `bz2` and `lzma` report a damaged stream as half a dozen unrelated types,
        # `lzma.LZMAError` not even an `OSError` - so without this one a cancelled task would be
        # reported as a damaged archive.
        raise
    except Exception as error:
        raise UnsupportedArchive(
            f'{archive_name} could not be decompressed: {error}. The archive is damaged or was '
            f'uploaded incompletely.') from error
    return Extraction(files=(ExtractedFile(key=key, path=target, size=size),),
                      skipped=tuple(budget.skipped), total_bytes=budget.total_bytes,
                      archive_format=archive_format)


def _single_stream_opener(archive_format: ArchiveFormat):
    """The `open` of the module that reads this compressor, imported where it is needed."""
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
    """
    The archive's file name with its compression extension removed.

    The name is matched case-insensitively - `DATA.CSV.GZ` gives `DATA.CSV` - and a name that does
    not end in the expected extension is left alone rather than truncated, so `backup` stays
    `backup` instead of losing a character.
    """
    name = PurePosixPath(archive_name).name
    suffix = SINGLE_STREAM_SUFFIXES[archive_format]
    if name.lower().endswith(suffix):
        return name[:-len(suffix)] or name
    # `.tgz` is the one short form worth knowing, and a bare `.tgz` that is not a tar is odd enough
    # to leave named as it is rather than guessed at.
    return name


def _assert_not_encrypted(entries: list[zipfile.ZipInfo], archive_name: str) -> None:
    """Refuses a password-protected zip once, naming the archive rather than an entry."""
    # Bit 0 of the general purpose flags. `zipfile` would otherwise raise a bare `RuntimeError` on
    # the first entry read, which reads to the user as an internal error of this service.
    if any(entry.flag_bits & 0x1 for entry in entries):
        raise UnsupportedArchive(
            f'{archive_name} is encrypted, and this service has no password for it. Extract it where '
            f'the password is known and upload the content instead.')


def _target_of(target_dir: Path, key: str) -> Path:
    """
    Where an entry is written locally, refusing anything that would leave the extraction directory.

    The last word on the path, after the name checks - because they look at the text and this looks
    at what the filesystem makes of it: a symlink created by an earlier entry of the same archive
    would otherwise redirect a later one.
    """
    target = target_dir / key
    if not is_inside(target, target_dir):
        raise UnsafeArchiveEntry(_refusal(key, 'a path that leads outside the extraction directory'))
    try:
        target.parent.mkdir(parents=True, exist_ok=True)
    except (FileExistsError, NotADirectoryError) as taken:
        # An archive holding both `a` as a file and `a/b.txt`. Legal to put in a zip, impossible to
        # write anywhere, and left as an `OSError` it would be reported to the user as an internal
        # error of this service.
        #
        # The message names the entry rather than the clash: the file in the way may be any ancestor
        # of this path, and naming the wrong one would send the user looking for a pair that does
        # not exist.
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
    Copies one entry onto disk, counting its bytes as they go and stopping the moment a limit is met.

    Block by block and not `shutil.copyfileobj`, for the two reasons that always apply here: the
    heartbeat, so a cancellation does not wait for a 50 GB file, and the counter, which is the one
    limit a crafted archive cannot talk its way past.

    A full disk is translated rather than left as an `OSError`: it is the ordinary consequence of an
    archive whose declared total was a lie, and the person who asked for the extraction can act on
    the message.
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
    One block out of an entry, translating what the compression libraries raise.

    `zipfile` reports a member it cannot decompress - an unsupported method, or encryption it did not
    see in the flags - as a bare `RuntimeError`. The translation is around the read alone and not
    around the loop, so that nothing the heartbeat raises is ever reported as a damaged archive.
    """
    try:
        return source.read(DOWNLOAD_BLOCK_BYTES)
    except RuntimeError as error:
        raise UnsupportedArchive(f'An entry of the archive could not be decompressed: {error}') \
            from error


def _empty_folders(folders: list[str], files: list[ExtractedFile]) -> tuple[str, ...]:
    """
    The folders of the archive that no extracted file lands in.

    A folder with files in it needs nothing in the destination: writing its files creates it, on a
    share as on an object storage. Only an empty one has to be asked for, and only a share can hold
    it - so narrowing the list here keeps the destination from being called once per folder of a
    deep archive.
    """
    occupied = {str(PurePosixPath(found.key).parent) for found in files}
    # Every ancestor counts as occupied too: `a/b/c.txt` fills `a` as well as `a/b`.
    for parent in tuple(occupied):
        current = PurePosixPath(parent)
        while str(current) not in ('.', '/'):
            occupied.add(str(current))
            current = current.parent
    return tuple(folder for folder in folders if folder not in occupied)
