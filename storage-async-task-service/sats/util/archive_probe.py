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
What an archive says about itself, read from its tail without fetching it.

    ZIP            entry count and total uncompressed size
    TAR.GZ, GZ     total
    TAR.XZ, XZ     total
    TAR            total, which is the archive's own size
    TAR.BZ2, BZ2   nothing

`None` means the format does not say. Read it as "enforce the limit while extracting", never as
"the limit is met": a number here is only what the archive claims.
"""

from __future__ import annotations

import struct
import tarfile
import zipfile
from dataclasses import dataclass
from enum import StrEnum
from typing import BinaryIO

from .log import get_logger

LOG = get_logger(__name__)

GZIP_MAGIC = b'\x1f\x8b'
BZIP2_MAGIC = b'BZh'
XZ_MAGIC = b'\xfd7zXZ\x00'
ZIP_MAGIC = b'PK\x03\x04'

MAGIC_BYTES = 8

# gzip: the last four bytes are ISIZE, the uncompressed size modulo 2**32.
GZIP_ISIZE_BYTES = 4
GZIP_ISIZE_MODULUS = 2 ** 32

# xz: the last 12 bytes are the stream footer - CRC32, Backward Size, Stream Flags, magic `YZ`.
# Backward Size is stored as (real size / 4) - 1.
XZ_FOOTER_BYTES = 12
XZ_FOOTER_MAGIC = b'YZ'
XZ_INDEX_INDICATOR = 0x00

# An xz varint is little-endian base 128, the high bit marking a continuation. Nine bytes is the most
# a 63-bit value needs; longer means a damaged file.
VARINT_MAX_BYTES = 9


class ArchiveFormat(StrEnum):
    """
    The outer layer of an archive.

    `GZIP`, `BZIP2` and `XZ` mean one compressed stream, so a `tar.gz` and a bare `data.csv.gz` are
    the same format here.
    """

    ZIP = 'ZIP'
    TAR = 'TAR'
    GZIP = 'GZIP'
    BZIP2 = 'BZIP2'
    XZ = 'XZ'
    UNKNOWN = 'UNKNOWN'


@dataclass(frozen=True, slots=True)
class ArchiveProbe:
    """What could be learned without fetching the archive. `None` means the format is silent."""

    format: ArchiveFormat
    entries: int | None = None
    total_bytes: int | None = None

    @property
    def is_supported(self) -> bool:
        return self.format is not ArchiveFormat.UNKNOWN


def detect_format(stream: BinaryIO, size: int) -> ArchiveFormat:
    """
    The format, from the content and never from the name.

    `zipfile` is asked last: it searches the end of the file, so it would call a tar holding a zip
    near its end a zip.
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
        return ArchiveFormat.ZIP
    return ArchiveFormat.UNKNOWN


def probe_archive(stream: BinaryIO, size: int) -> ArchiveProbe:
    """Detects the format and reads whatever it declares. A tail that cannot be read is unknown."""
    archive_format = detect_format(stream, size)
    match archive_format:
        case ArchiveFormat.ZIP:
            return _probe_zip(stream, archive_format)
        case ArchiveFormat.GZIP:
            return ArchiveProbe(archive_format, total_bytes=_gzip_total(stream, size))
        case ArchiveFormat.XZ:
            return ArchiveProbe(archive_format, total_bytes=_xz_total(stream, size))
        case ArchiveFormat.TAR:
            # A tar cannot hold more bytes than it is long. The entry count is left unknown: it
            # would cost one ranged request per entry, and is counted while extracting instead.
            return ArchiveProbe(archive_format, total_bytes=size)
        case ArchiveFormat.BZIP2:
            return ArchiveProbe(archive_format)
        case _:
            return ArchiveProbe(ArchiveFormat.UNKNOWN)


def _probe_zip(stream: BinaryIO, archive_format: ArchiveFormat) -> ArchiveProbe:
    """
    Entry count and total uncompressed size from the central directory. Folders count too.
    """
    try:
        with zipfile.ZipFile(stream) as archive:
            entries = archive.infolist()
    except (zipfile.BadZipFile, OSError, ValueError) as error:
        # Not a refusal: the local extraction reports what is wrong with the archive.
        LOG.warning('Cannot read the index of the archive (%s), its content stays unknown', error)
        return ArchiveProbe(archive_format)
    return ArchiveProbe(archive_format, entries=len(entries),
                        total_bytes=sum(entry.file_size for entry in entries))


def _gzip_total(stream: BinaryIO, size: int) -> int | None:
    """
    The uncompressed size a gzip stream declares, or `None` where it cannot be trusted.

    ISIZE is modulo 2**32, so above 4 GiB it wraps. A declared total below the compressed size is
    the symptom, and it answers unknown.
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
    The uncompressed size an xz stream declares, summed out of the index in its footer.
    """
    footer = _read_at(stream, size - XZ_FOOTER_BYTES, XZ_FOOTER_BYTES)
    if len(footer) != XZ_FOOTER_BYTES:
        return None
    _, backward_size, _, magic = struct.unpack('<IIH2s', footer)
    if magic != XZ_FOOTER_MAGIC:
        LOG.debug('The xz footer carries no %r magic, reading the content as unknown',
                  XZ_FOOTER_MAGIC)
        return None
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

    Layout: the indicator byte, the record count, then two varints per record - the unpadded size
    and the uncompressed size.
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
    """`length` bytes from `offset`, or fewer. Never raises on a short file."""
    if offset < 0 or length <= 0:
        return b''
    stream.seek(offset)
    return stream.read(length)


def _is_tar(stream: BinaryIO) -> bool:
    """Whether the stream is an uncompressed tar."""
    stream.seek(0)
    try:
        return tarfile.is_tarfile(stream)
    except (tarfile.TarError, OSError, ValueError):
        return False


def _is_zip(stream: BinaryIO) -> bool:
    """Whether the stream carries a zip central directory."""
    stream.seek(0)
    try:
        return zipfile.is_zipfile(stream)
    except (OSError, ValueError):
        return False
