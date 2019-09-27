import logging
from abc import abstractmethod, ABCMeta

from mount.pipefuse import fuseutils
from mount.pipefuse.fuseutils import MB, GB


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


class _PartialChunk:

    def __init__(self, offset, size):
        self._offset = offset
        self._size = size
        self._buf = bytearray(size)
        self._filled_regions = []

    @property
    def offset(self):
        return self._offset

    def append(self, offset, buf):
        end = offset + len(buf)
        self._buf[offset:end] = buf[:]
        # TODO 27.09.2019: Merge overlapping and adjacent regions
        self._filled_regions.append((offset, end))

    def missing_regions(self):
        last_index = 0
        for region_start, region_end in self._sorted_filled_regions():
            if last_index < region_start:
                yield (last_index, region_start)
            last_index = max(last_index, region_end)
        if last_index < self._size:
            yield (last_index, self._size)

    def _sorted_filled_regions(self):
        return sorted(self._filled_regions, key=lambda region: region[0])

    def collect(self):
        regions = self._sorted_filled_regions()
        last_region_end = regions[len(regions)-1][1]
        return self._buf[:last_region_end]


class ChunkedMultipartUpload(MultipartUpload):

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
        self._mpu = mpu
        self._original_size = original_size
        self._download_func = download_func
        self._chunk_size = chunk_size
        self._chunk_numbers = set()
        self._partial_chunks = {}
        self._chunk_number_range = fuseutils.lazy_range(min_chunk, max_chunk)

    @property
    def path(self):
        return self._mpu.path

    def initiate(self):
        self._mpu.initiate()

    def upload_part(self, buf, offset=None, part_number=None):
        chunk_number, chunk_offset = self._resolve_chunk_number(offset)
        chunk_shift = offset - chunk_offset
        buf_shift = 0
        while buf_shift < len(buf):
            if chunk_shift or buf_shift + self._chunk_size - chunk_shift > len(buf):
                partial_chunk = self._partial_chunks.get(chunk_number, None)
                if not partial_chunk:
                    partial_chunk = _PartialChunk(chunk_offset, self._chunk_size)
                    self._partial_chunks[chunk_number] = partial_chunk
                partial_chunk.append(chunk_shift, buf[buf_shift:buf_shift+self._chunk_size - chunk_shift])
                # TODO 27.09.2019: Flush partial chunk if it is filled
            else:
                chunk = bytearray(self._chunk_size)
                chunk[chunk_shift:self._chunk_size] = buf[buf_shift:buf_shift + self._chunk_size]
                self._mpu.upload_part(chunk, chunk_offset, chunk_number)
            buf_shift += self._chunk_size - chunk_shift
            self._chunk_numbers.add(chunk_number)
            chunk_number += 1
            chunk_offset += self._chunk_size
            chunk_shift = 0

    def _resolve_chunk_number(self, offset):
        # TODO 26.09.2019: Use binary-like search for that
        for chunk in self.CHUNK_NUMBER_RANGE:
            chunk_offset = (chunk-1) * self._chunk_size
            if chunk_offset <= offset < chunk_offset + self._chunk_size:
                return chunk, chunk_offset

    def upload_copy_part(self, start, end, offset=None, part_number=None):
        self._mpu.upload_copy_part(start, end, offset, part_number)

    def complete(self):
        last_chunk = 0
        for chunk in sorted(self._chunk_numbers):
            if chunk - last_chunk > 1:
                missing_start = last_chunk * self._chunk_size
                missing_end = (chunk - 1) * self._chunk_size
                self._mpu.upload_copy_part(missing_start, missing_end, missing_start, last_chunk + 1)
            last_chunk = chunk
        last_chunk_end = last_chunk * self._chunk_size
        if last_chunk_end < self._original_size:
            self._mpu.upload_copy_part(last_chunk_end, self._original_size, last_chunk_end, last_chunk + 1)
        for chunk_number, partial_chunk in self._partial_chunks.items():
            for missing_start, missing_end in partial_chunk.missing_regions():
                actual_start = missing_start + partial_chunk.offset
                actual_end = min(missing_end + partial_chunk.offset, self._original_size)
                if actual_end > actual_start:
                    partial_chunk.append(missing_start, self._download_func(actual_start, actual_end - actual_start))
            chunk = partial_chunk.collect()
            self._mpu.upload_part(chunk, partial_chunk.offset, chunk_number)
        self._mpu.complete()

    def abort(self):
        self._mpu.abort()


class SplittingMultipartCopyUpload(MultipartUpload):

    def __init__(self, mpu, min_part_size=5 * MB, max_part_size=5 * GB):
        """
        Splitting multipart copy upload.

        Splits copy upload parts into several ones to fit maximum upload part size limit.
        Also takes into the account minimum upload part size.

        :param mpu: Wrapping multipart upload.
        :param min_part_size: Minimum upload part size.
        :param max_part_size: Maximum upload part size.
        """
        self._mpu = mpu
        self._min_part_size = min_part_size
        self._max_part_size = max_part_size

    @property
    def path(self):
        return self._mpu.path

    def initiate(self):
        self._mpu.initiate()

    def upload_part(self, buf, offset=None, part_number=None):
        self._mpu.upload_part(buf, offset, part_number)

    def upload_copy_part(self, start, end, offset=None, part_number=None):
        copy_part_length = end - start
        if copy_part_length > self._max_part_size:
            logging.debug('Splitting upload part into pieces for %s' % self.path)
            remaining_length = copy_part_length
            current_offset = 0
            actual_part_number = part_number
            while remaining_length > 0:
                part_size = self._resolve_part_size(remaining_length)
                self._mpu.upload_copy_part(current_offset, current_offset + part_size, offset, actual_part_number)
                remaining_length -= part_size
                actual_part_number += 1
        else:
            self._mpu.upload_copy_part(start, end, offset, part_number)

    def _resolve_part_size(self, remaining_length):
        if self._min_part_size <= remaining_length <= self._max_part_size:
            return remaining_length
        else:
            return min(self._max_part_size, remaining_length - self._min_part_size)

    def complete(self):
        self._mpu.complete()

    def abort(self):
        self._mpu.abort()
