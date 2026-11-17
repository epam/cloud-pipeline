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
What an archive holds, read from the archive where it lies.

The cheapest refusal is the one made before any bytes move, and how much can be known without
fetching the archive differs sharply by format - the index of a zip is at its end, a gzip stream
records its uncompressed size in its last four bytes, an xz stream in a footer index, and bzip2
records nothing at all. So this module reads a few hundred bytes at the right offsets and answers
what it can:

    ZIP            entry count and total uncompressed size, from the central directory
    TAR.GZ, GZ     total, from the four-byte ISIZE
    TAR.XZ, XZ     total, from the stream footer's index
    TAR            total, which is the archive's own size - an uncompressed tar cannot hold more
    TAR.BZ2, BZ2   nothing

**An honest "unknown" is the contract.** `None` means "the format does not say", never "the limit is
met": the caller has to treat it as "enforced while extracting instead". Everything a format does
declare was written by whoever built the archive, so even a number here is a hint - the limit that
cannot be lied to is the one counted while writing, in `archive_extractor`.

The stream is whatever `FileSource.open_stream` hands over: a file on a share, or ranged reads
against an object. Nothing here downloads.
"""

from __future__ import annotations

import struct
import tarfile
import zipfile
from dataclasses import dataclass
from enum import StrEnum
from typing import BinaryIO

from .util.log import get_logger

LOG = get_logger(__name__)

# How the outer layer of each format announces itself. Checked at offset 0, which is where a format
# that was not tampered with puts it.
GZIP_MAGIC = b'\x1f\x8b'
BZIP2_MAGIC = b'BZh'
XZ_MAGIC = b'\xfd7zXZ\x00'
ZIP_MAGIC = b'PK\x03\x04'

# Enough for the longest magic above.
MAGIC_BYTES = 8

# gzip: the last four bytes are ISIZE, the uncompressed size modulo 2**32.
GZIP_ISIZE_BYTES = 4
GZIP_ISIZE_MODULUS = 2 ** 32

# xz: the stream footer is the last 12 bytes - CRC32, Backward Size, Stream Flags, and the magic
# `YZ`. Backward Size is stored as (real size / 4) - 1.
XZ_FOOTER_BYTES = 12
XZ_FOOTER_MAGIC = b'YZ'
XZ_INDEX_INDICATOR = 0x00

# A varint in xz is little-endian base 128, the high bit marking a continuation. Nine bytes is the
# most a 63-bit value needs, and a longer one is a damaged file rather than a large number.
VARINT_MAX_BYTES = 9


class ArchiveFormat(StrEnum):
    """
    The outer layer of an archive, which is all that can be told without opening it.

    `GZIP`, `BZIP2` and `XZ` say "one compressed stream" and nothing about what is inside it: a
    `tar.gz` and a bare `data.csv.gz` are the same format here and are told apart only once the
    stream can be decompressed - see `archive_extractor`. That is deliberate, because the two share
    every answer this module gives.
    """

    ZIP = 'ZIP'
    TAR = 'TAR'
    GZIP = 'GZIP'
    BZIP2 = 'BZIP2'
    XZ = 'XZ'
    UNKNOWN = 'UNKNOWN'


@dataclass(frozen=True, slots=True)
class ArchiveProbe:
    """
    What could be learned about an archive without fetching it.

    `None` in either number means the format does not declare it. A number is what the archive
    claims, not a fact - see the module docstring.
    """

    format: ArchiveFormat
    entries: int | None = None
    total_bytes: int | None = None

    @property
    def is_supported(self) -> bool:
        return self.format is not ArchiveFormat.UNKNOWN


def detect_format(stream: BinaryIO, size: int) -> ArchiveFormat:
    """
    The format, from the content and never from the name.

    This service is the only thing between a mislabelled or crafted file and a storage, so a `.zip`
    that is really a tar extracts and a `.zip` that is neither is refused. The order is what makes
    it cheap and what keeps a false positive out: the magic at offset 0 answers the ordinary cases
    in one read, `tarfile` is asked next because a tar has no magic at offset 0 at all, and
    `zipfile` is asked last - it searches the *end* of the file for the central directory, so asking
    it first would call a tar holding a zip near its end a zip.
    """
    stream.seek(0)
    magic = stream.read(MAGIC_BYTES)
    if magic.startswith(ZIP_MAGIC):
        return ArchiveFormat.ZIP
    if magic.startswith(GZIP_MAGIC):
        return ArchiveFormat.GZIP
    if magic.startswith(BZIP2_MAGIC):
        return ArchiveFormat.BZIP2
    if magic.startswith(XZ_MAGIC):
        return ArchiveFormat.XZ
    if _is_tar(stream):
        return ArchiveFormat.TAR
    if _is_zip(stream):
        # A zip with something in front of it - a self-extracting archive, most often.
        return ArchiveFormat.ZIP
    return ArchiveFormat.UNKNOWN


def probe_archive(stream: BinaryIO, size: int) -> ArchiveProbe:
    """
    Detects the format and reads whatever it declares about its content.

    One entry point, because the two halves are never wanted apart: the format decides which tail to
    read. A tail that cannot be read is reported as unknown rather than as a failure - the archive
    may still extract, and the limits still hold while it does.
    """
    archive_format = detect_format(stream, size)
    match archive_format:
        case ArchiveFormat.ZIP:
            return _probe_zip(stream, archive_format)
        case ArchiveFormat.GZIP:
            return ArchiveProbe(archive_format, total_bytes=_gzip_total(stream, size))
        case ArchiveFormat.XZ:
            return ArchiveProbe(archive_format, total_bytes=_xz_total(stream, size))
        case ArchiveFormat.TAR:
            # An uncompressed tar cannot hold more bytes than it is long, so its own size is an exact
            # upper bound on its content - the right direction for a limit check, and free.
            #
            # The entry count is deliberately left unknown. It is readable, one 512-byte header per
            # entry, and against an object storage that is one ranged request per entry: an archive
            # of a hundred thousand files would cost a hundred thousand requests to refuse. The count
            # is enforced while extracting instead.
            return ArchiveProbe(archive_format, total_bytes=size)
        case ArchiveFormat.BZIP2:
            # bzip2 records neither a count nor a size, anywhere. The byte counter during extraction
            # is the only limit this format ever gets.
            return ArchiveProbe(archive_format)
        case _:
            return ArchiveProbe(ArchiveFormat.UNKNOWN)


def _probe_zip(stream: BinaryIO, archive_format: ArchiveFormat) -> ArchiveProbe:
    """
    Entry count and total uncompressed size from the central directory, which is at the end.

    **Every entry is counted, folders included**, because that is what the limit means and what the
    extraction counts: an archive of ninety thousand empty folders is a few megabytes and would
    otherwise pass this check and the one during the extraction, and then cost a request per folder
    in the collision pass. A folder's own `file_size` is zero, so the total is unaffected.

    The sizes are the ones the archive declares for its own entries - a zip bomb declares them
    honestly, which is what makes this check worth making.
    """
    try:
        with zipfile.ZipFile(stream) as archive:
            entries = archive.infolist()
    except (zipfile.BadZipFile, OSError, ValueError) as error:
        # Not a refusal: the local extraction will fail with a message of its own, and refusing here
        # would mean refusing an archive this service might open.
        LOG.warning('Cannot read the index of the archive (%s), its content stays unknown', error)
        return ArchiveProbe(archive_format)
    return ArchiveProbe(archive_format, entries=len(entries),
                        total_bytes=sum(entry.file_size for entry in entries))


def _gzip_total(stream: BinaryIO, size: int) -> int | None:
    """
    The uncompressed size a gzip stream declares, or `None` where the value cannot be trusted.

    Two reasons for `None`, and both are the format's own limits rather than failures:

      * **ISIZE is modulo 2**32**, so anything above 4 GiB wraps. A declared total smaller than the
        compressed size is the symptom, and it is read as unknown rather than as a pass - otherwise
        a 6 GiB archive would declare 2 GiB and walk through the limit.
      * **a multi-member gzip file** has an ISIZE per member and these are its last member's, so the
        total would be short. Nothing in the trailer says how many members there are, which is why
        the plausibility check above is the whole defence.
    """
    tail = _read_at(stream, size - GZIP_ISIZE_BYTES, GZIP_ISIZE_BYTES)
    if len(tail) != GZIP_ISIZE_BYTES:
        return None
    declared = struct.unpack('<I', tail)[0]
    if declared < size:
        LOG.debug('The gzip trailer declares %s bytes for %s compressed ones, which cannot be the '
                  'whole content: reading it as unknown', declared, size)
        return None
    return declared


def _xz_total(stream: BinaryIO, size: int) -> int | None:
    """
    The uncompressed size an xz stream declares, summed out of the index its footer points at.

    The index records an (unpadded size, uncompressed size) pair per block, so the sum is exact -
    unlike gzip there is nothing modular about it. A multi-stream file reports only its last stream,
    which makes the answer short rather than wrong in the dangerous direction; the counter during
    extraction is what covers that.
    """
    footer = _read_at(stream, size - XZ_FOOTER_BYTES, XZ_FOOTER_BYTES)
    if len(footer) != XZ_FOOTER_BYTES:
        return None
    _, backward_size, _, magic = struct.unpack('<IIH2s', footer)
    if magic != XZ_FOOTER_MAGIC:
        LOG.debug('The xz footer carries no %r magic, reading the content as unknown',
                  XZ_FOOTER_MAGIC)
        return None
    # Stored as (real size / 4) - 1, so a stored 0 means four bytes.
    index_bytes = (backward_size + 1) * 4
    index = _read_at(stream, size - XZ_FOOTER_BYTES - index_bytes, index_bytes)
    if len(index) != index_bytes:
        return None
    try:
        return _sum_xz_index(index)
    except (IndexError, ValueError) as error:
        LOG.warning('Cannot read the xz index (%s), the content stays unknown', error)
        return None


def _sum_xz_index(index: bytes) -> int | None:
    """
    Sums the uncompressed sizes in an xz index.

    Layout: the indicator byte, the number of records, then two varints per record - the unpadded
    size, which is skipped, and the uncompressed size, which is added up.
    """
    if not index or index[0] != XZ_INDEX_INDICATOR:
        return None
    records, offset = _read_varint(index, 1)
    total = 0
    for _ in range(records):
        _, offset = _read_varint(index, offset)
        uncompressed, offset = _read_varint(index, offset)
        total += uncompressed
    return total


def _read_varint(data: bytes, offset: int) -> tuple[int, int]:
    """One xz multibyte integer, as `(value, the offset after it)`."""
    value = 0
    for index in range(VARINT_MAX_BYTES):
        byte = data[offset + index]
        value |= (byte & 0x7F) << (7 * index)
        if not byte & 0x80:
            return value, offset + index + 1
    raise ValueError(f'A multibyte integer at offset {offset} does not end within '
                     f'{VARINT_MAX_BYTES} bytes')


def _read_at(stream: BinaryIO, offset: int, length: int) -> bytes:
    """`length` bytes from `offset`, or fewer when the file is shorter. Never raises on a short file."""
    if offset < 0 or length <= 0:
        return b''
    stream.seek(offset)
    return stream.read(length)


def _is_tar(stream: BinaryIO) -> bool:
    """
    Whether the stream is an uncompressed tar.

    `tarfile` reads the first header block and checks its checksum, which is the only thing a tar
    offers - there is no magic at offset 0. A compressed tar never reaches here: its compressor was
    recognised first.
    """
    stream.seek(0)
    try:
        return tarfile.is_tarfile(stream)
    except (tarfile.TarError, OSError, ValueError):
        return False


def _is_zip(stream: BinaryIO) -> bool:
    """Whether the stream carries a zip central directory, wherever the file begins."""
    stream.seek(0)
    try:
        return zipfile.is_zipfile(stream)
    except (OSError, ValueError):
        return False
