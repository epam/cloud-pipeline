# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import logging
from abc import abstractmethod, ABCMeta

import intervals

from fuseutils import MB, GB


class UnmanageableMultipartUploadException(RuntimeError):

    def __init__(self, *args):
        super(UnmanageableMultipartUploadException, self).__init__(*args)


class MultipartUpload:
    __metaclass__ = ABCMeta

    @property
    @abstractmethod
    def path(self):
        pass

    @abstractmethod
    def initiate(self):
        pass

    @abstractmethod
    def upload_part(self, buf, offset=None, part_number=None):
        pass

    @abstractmethod
    def upload_copy_part(self, start, end, offset=None, part_number=None):
        pass

    @abstractmethod
    def complete(self):
        pass

    @abstractmethod
    def abort(self):
        pass


class MultipartUploadDecorator(MultipartUpload):

    def __init__(self, mpu):
        self._mpu = mpu

    @property
    def path(self):
        return self._mpu.path

    def initiate(self):
        self._mpu.initiate()

    def upload_part(self, buf, offset=None, part_number=None):
        self._mpu.upload_part(buf, offset, part_number)

    def upload_copy_part(self, start, end, offset=None, part_number=None):
        self._mpu.upload_copy_part(start, end, offset, part_number)

    def complete(self):
        self._mpu.complete()

    def abort(self):
        self._mpu.abort()


class _PartialChunk:
    __metaclass__ = ABCMeta

    @property
    @abstractmethod
    def offset(self):
        pass

    @abstractmethod
    def append(self, offset, buf):
        pass

    @abstractmethod
    def is_full(self):
        pass

    @abstractmethod
    def missing_intervals(self):
        pass

    @abstractmethod
    def collect(self):
        pass


class _IncompletePartialChunk(_PartialChunk):

    def __init__(self, offset, size):
        self._offset = offset
        self._size = size
        self._buf = bytearray(size)
        self._bounds_interval = intervals.closed(0, self._size)
        self._interval = intervals.empty()

    @property
    def offset(self):
        return self._offset

    def append(self, offset, buf):
        end = offset + len(buf)
        self._buf[offset:end] = buf[:]
        self._interval |= intervals.closed(offset, end)

    def is_full(self):
        return self._missing_interval().is_empty()

    def missing_intervals(self):
        for interval in self._missing_interval():
            yield interval.lower, interval.upper

    def _missing_interval(self):
        return self._interval.complement().intersection(self._bounds_interval)

    def collect(self):
        return self._buf[:self._interval.upper]


class _CompletePartialChunk(_PartialChunk):

    def __init__(self, offset):
        self._offset = offset

    @property
    def offset(self):
        return self._offset

    def append(self, offset, buf):
        pass

    def is_full(self):
        return True

    def missing_intervals(self):
        return []

    def collect(self):
        return bytearray(0)


class ChunkedMultipartUpload(MultipartUploadDecorator):

    def __init__(self, mpu, original_size, download_func, chunk_size, min_chunk, max_chunk):
        """
        Chunked multipart upload.

        Cuts all the incoming uploads into chunks of the given size.

        Has a limit on the maximum file size that can be written using chunked multipart upload.
        It can be calculated multiplying chunk size by max chunk number. F.e. for chunk size of 10MB and 10000 chunks
        it will be 100GB, for chunk size of 100MB and 10000 chunks it will be 1TB.

        Fills gaps between uploaded parts with the original file's content.

        :param mpu: Wrapping multipart upload.
        :param original_size: Destination file original size.
        :param download_func: Function that retrieves content from the original file by its offset and length.
        :param chunk_size: Size of a single upload part.
        :param min_chunk: Minimum allowed chunk number.
        :param max_chunk: Maximum allowed chunk number.
        """
        super(ChunkedMultipartUpload, self).__init__(mpu)
        self._mpu = mpu
        self._original_size = original_size
        self._download_func = download_func
        self._chunk_size = chunk_size
        self._partial_chunks = {}
        self._min_chunk = min_chunk
        self._max_chunk = max_chunk

    def upload_part(self, buf, offset=None, part_number=None):
        chunk = self._resolve_chunk(offset)
        chunk_offset = self._chunk_offset(chunk)
        chunk_shift = offset - chunk_offset
        buf_shift = 0
        while buf_shift < len(buf):
            if chunk_shift or buf_shift + self._chunk_size - chunk_shift > len(buf):
                partial_chunk = self._partial_chunks.get(chunk, None)
                if not partial_chunk:
                    partial_chunk = _IncompletePartialChunk(chunk_offset, self._chunk_size)
                    self._partial_chunks[chunk] = partial_chunk
                if partial_chunk.is_full():
                    raise UnmanageableMultipartUploadException(
                        'Multipart upload chunk %s cannot be reuploaded for %s.' % (chunk, self.path))
                partial_chunk.append(chunk_shift, buf[buf_shift:buf_shift + self._chunk_size - chunk_shift])
                if partial_chunk.is_full():
                    self._mpu.upload_part(partial_chunk.collect(), partial_chunk.offset, chunk)
                    self._partial_chunks[chunk] = _CompletePartialChunk(partial_chunk.offset)
            else:
                chunk_buf = bytearray(self._chunk_size)
                chunk_buf[chunk_shift:self._chunk_size] = buf[buf_shift:buf_shift + self._chunk_size]
                self._mpu.upload_part(chunk_buf, chunk_offset, chunk)
                self._partial_chunks[chunk] = _CompletePartialChunk(chunk_offset)
            buf_shift += self._chunk_size - chunk_shift
            chunk += 1
            chunk_offset += self._chunk_size
            chunk_shift = 0

    def _resolve_chunk(self, offset):
        first_chunk = self._min_chunk
        last_chunk = self._max_chunk
        while last_chunk - first_chunk > 1:
            mid_chunk = first_chunk + (last_chunk - first_chunk) / 2
            mid_chunk_offset = self._chunk_offset(mid_chunk)
            if offset > mid_chunk_offset:
                first_chunk = mid_chunk
            elif offset < mid_chunk_offset:
                last_chunk = mid_chunk
            else:
                return mid_chunk
        return last_chunk if offset >= self._chunk_offset(last_chunk) else first_chunk

    def _chunk_offset(self, chunk):
        return (chunk - 1) * self._chunk_size

    def complete(self):
        current_missing_chunk = 1
        for chunk in sorted(self._partial_chunks.keys()):
            if chunk > current_missing_chunk:
                missing_start = self._chunk_offset(current_missing_chunk)
                missing_end = self._chunk_offset(chunk)
                self._mpu.upload_copy_part(missing_start, missing_end, missing_start, current_missing_chunk)
            current_missing_chunk = chunk + 1
        last_written_chunk = max(self._partial_chunks.keys())
        last_missing_chunk = last_written_chunk + 1
        last_chunk_end = self._chunk_offset(last_missing_chunk)
        if last_chunk_end < self._original_size:
            self._mpu.upload_copy_part(last_chunk_end, self._original_size, last_chunk_end, last_missing_chunk)
        for chunk_number, partial_chunk in self._partial_chunks.items():
            if partial_chunk.is_full():
                continue
            for missing_start, missing_end in partial_chunk.missing_intervals():
                actual_start = missing_start + partial_chunk.offset
                actual_end = missing_end + partial_chunk.offset
                if self._original_size <= actual_start:
                    if chunk_number < last_written_chunk:
                        partial_chunk.append(missing_start,
                                             bytearray(actual_end - actual_start))
                elif actual_start < self._original_size < actual_end:
                    partial_chunk.append(missing_start,
                                         self._download_func(actual_start, self._original_size - actual_start))
                    if chunk_number < last_written_chunk:
                        partial_chunk.append(missing_start + self._original_size - actual_start,
                                             bytearray(actual_end - self._original_size))
                else:
                    partial_chunk.append(missing_start, self._download_func(actual_start, actual_end - actual_start))
            chunk = partial_chunk.collect()
            self._mpu.upload_part(chunk, partial_chunk.offset, chunk_number)
        self._mpu.complete()


class SplittingMultipartCopyUpload(MultipartUploadDecorator):

    def __init__(self, mpu, min_part_size=5 * MB, max_part_size=5 * GB):
        """
        Splitting multipart copy upload.

        Splits copy upload parts into several ones to fit maximum upload part size limit.
        Also takes into the account minimum upload part size.

        :param mpu: Wrapping multipart upload.
        :param min_part_size: Minimum upload part size.
        :param max_part_size: Maximum upload part size.
        """
        super(SplittingMultipartCopyUpload, self).__init__(mpu)
        self._mpu = mpu
        self._min_part_size = min_part_size
        self._max_part_size = max_part_size

    def upload_copy_part(self, start, end, offset=None, part_number=None):
        copy_part_length = end - start
        if copy_part_length > self._max_part_size:
            logging.debug('Splitting copy upload part %d into pieces for %s' % (part_number, self.path))
            remaining_length = copy_part_length
            current_offset = offset
            actual_part_number = part_number
            while remaining_length > 0:
                part_size = self._resolve_part_size(remaining_length)
                self._mpu.upload_copy_part(current_offset, current_offset + part_size, current_offset,
                                           actual_part_number)
                remaining_length -= part_size
                current_offset += part_size
                actual_part_number += 1
        else:
            self._mpu.upload_copy_part(start, end, offset, part_number)

    def _resolve_part_size(self, remaining_length):
        if self._min_part_size <= remaining_length <= self._max_part_size:
            return remaining_length
        else:
            return min(self._max_part_size, remaining_length - self._min_part_size)


class OutOfBoundsSplittingMultipartCopyUpload(SplittingMultipartCopyUpload):

    def __init__(self, mpu, original_size, min_part_size, max_part_size):
        """
        Out of bounds splitting multipart copy upload.

        Splits out of bounds copy upload parts into several ones to fit memory-safe part size limit.
        Also takes into the account minimum upload part size.

        :param mpu: Wrapping multipart upload.
        :param original_size: Destination file original size.
        :param min_part_size: Minimum upload part size.
        :param max_part_size: Maximum upload part size.
        """
        super(OutOfBoundsSplittingMultipartCopyUpload, self).__init__(mpu, min_part_size, max_part_size)
        self._original_size = original_size

    def upload_copy_part(self, start, end, offset=None, part_number=None):
        if self._original_size < end:
            super(OutOfBoundsSplittingMultipartCopyUpload, self).upload_copy_part(start, end, offset, part_number)
        else:
            self._mpu.upload_copy_part(start, end, offset, part_number)


class OutOfBoundsFillingMultipartCopyUpload(MultipartUploadDecorator):

    def __init__(self, mpu, original_size, download_func):
        """
        Out of bounds filling multipart copy upload.

        Fills copy upload part regions which are located beyond the original file size with null bytes.

        :param mpu: Wrapping multipart upload.
        :param original_size: Destination file original size.
        :param download_func: Function that retrieves content from the original file by its offset and length.
        """
        super(OutOfBoundsFillingMultipartCopyUpload, self).__init__(mpu)
        self._mpu = mpu
        self._original_size = original_size
        self._download_func = download_func

    def upload_copy_part(self, start, end, offset=None, part_number=None):
        if self._original_size <= start:
            logging.debug('Filling out of bounds copy upload part %s whole %d-%d for %s' % (part_number, start, end, self.path))
            modified_buf = bytearray(end - start)
            self._mpu.upload_part(modified_buf, offset, part_number)
        elif start < self._original_size < end:
            logging.debug('Filling out of bounds copy upload part %s region %d-%d with nulls for %s' % (part_number, start, end, self.path))
            original_buf = self._download_func(start, end - start)
            modified_buf = bytearray(end - start)
            modified_buf[0:len(original_buf)] = original_buf
            self._mpu.upload_part(modified_buf, offset, part_number)
        else:
            self._mpu.upload_copy_part(start, end, offset, part_number)


class TruncatingMultipartCopyUpload(MultipartUploadDecorator):

    def __init__(self, mpu, length, min_part_number=1):
        """
        Truncating multipart copy upload.

        Truncates the file length to the given size which has to be smaller then the original one.

        :param mpu: Wrapping multipart upload.
        :param length: Target size of the truncating file.
        :param min_part_number: Minimal allowed part number. Same as minimum allowed chunk number.
        """
        super(TruncatingMultipartCopyUpload, self).__init__(mpu)
        self._mpu = mpu
        self._length = length
        self._min_part_number = min_part_number

    def complete(self):
        self._mpu.upload_copy_part(start=0, end=self._length, offset=0, part_number=self._min_part_number)
        self._mpu.complete()
