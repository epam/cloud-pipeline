# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import io
import logging
import os
from abc import ABCMeta, abstractmethod

import time
from collections import namedtuple
from datetime import datetime

import pytz
from boto3 import Session
from botocore.config import Config
from botocore.credentials import RefreshableCredentials
from botocore.session import get_session
from sortedcontainers import SortedList

import fuseutils
from fsclient import File, FileSystemClient
from fuseutils import MB, GB

_ANY_ERROR = Exception


def _http_range(start, end):
    return 'bytes=%s-%s' % (start, end - 1)


class _MultipartUpload:
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


_UploadPart = namedtuple('UploadPart', ['number', 'ETag'])
_UploadSection = namedtuple('UploadSection', ['offset', 'length', 'number'])


class _PlainMultipartUpload(_MultipartUpload):

    def __init__(self, path, offset, bucket, s3):
        """
        Plain multipart upload.

        :param path: Destination bucket relative path.
        :param offset: First upload part offset.
        :param bucket: Destination bucket name.
        :param s3: Boto S3 client.
        """
        self._path = path
        self._bucket = bucket
        self._s3 = s3
        self._upload_id = None
        self._parts = []
        self._offset = offset

    @property
    def path(self):
        return self._path

    def initiate(self):
        response = self._s3.create_multipart_upload(
            Bucket=self._bucket,
            Key=self._path
        )
        self._upload_id = response['UploadId']

    def upload_part(self, buf, offset=None, part_number=None):
        with io.BytesIO(buf) as body:
            response = self._s3.upload_part(
                Bucket=self._bucket,
                Key=self._path,
                Body=body,
                UploadId=self._upload_id,
                PartNumber=part_number
            )
        self._parts.append(_UploadPart(part_number, response['ETag']))

    def upload_copy_part(self, start, end, offset=None, part_number=None):
        response = self._s3.upload_part_copy(
            Bucket=self._bucket,
            Key=self._path,
            CopySource={
                'Bucket': self._bucket,
                'Key': self._path,
            },
            CopySourceRange=_http_range(start, end),
            UploadId=self._upload_id,
            PartNumber=part_number
        )
        self._parts.append(_UploadPart(part_number, response['CopyPartResult']['ETag']))

    def complete(self):
        self._s3.complete_multipart_upload(
            Bucket=self._bucket,
            Key=self._path,
            MultipartUpload={
                'Parts': [
                    {
                        'ETag': part.ETag,
                        'PartNumber': part.number
                    } for part in sorted(self._parts, key=lambda p: p.number)
                ]
            },
            UploadId=self._upload_id
        )

    def abort(self):
        logging.error('Aborting multipart upload for %s' % self._path)
        self._s3.abort_multipart_upload(Bucket=self._bucket, Key=self._path, UploadId=self._upload_id)


class _NumeratingMultipartUpload(_MultipartUpload):

    MIN_PART_NUMBER = 1
    INIT_PART_NUMBER = 1000
    MAX_PART_NUMBER = 10000

    def __init__(self, mpu, margin=10):
        """
        Numerating multipart upload.

        It numerates upload parts with numbers in range from MIN_PART_NUMBER to MAX_PART_NUMBER with a given margin.
        Margin between uploading parts is required to support later upload part inserts.

        First upload part gets INIT_PART_NUMBER part number.

        :param mpu: Wrapping multipart upload.
        :param margin: Upload part numbers initial margin.
        """
        self._mpu = mpu
        self._margin = margin
        self._sections = SortedList(key=lambda f: f.offset)

    @property
    def path(self):
        return self._mpu.path

    def initiate(self):
        self._mpu.initiate()

    def upload_part(self, buf, offset=None, part_number=None):
        part_number = self._resolve_part_number(offset)
        self._mpu.upload_part(buf, offset, part_number)
        self._sections.add(_UploadSection(offset, len(buf), part_number))

    def _resolve_part_number(self, offset):
        if not self._sections:
            return self.INIT_PART_NUMBER
        else:
            prev_offset = 0
            for index, section in enumerate(self._sections):
                if prev_offset <= offset < section.offset:
                    prev_number = self._sections[index - 1].number if index else 0
                    next_number = section.number
                    part_number = self._between(prev_number, next_number)
                    if prev_number < part_number < next_number:
                        return part_number
                    else:
                        # TODO 25.09.2019: There is no available part numbers.
                        raise RuntimeError('There is no available part numbers for %s. '
                                           'Operation is not supported yet.' % self.path)
                prev_offset = section.offset
            last_section = self._sections[len(self._sections) - 1]
            return last_section.number + self._margin

    def _between(self, first_number, second_number):
        return first_number + (second_number - first_number) / 2

    def upload_copy_part(self, start, end, offset=None, part_number=None):
        part_number = self._resolve_part_number(offset)
        self._mpu.upload_copy_part(start, end, offset, part_number)
        self._sections.add(_UploadSection(start, end-start, part_number))

    def complete(self):
        self._mpu.complete()

    def abort(self):
        self._mpu.abort()


class _PartBuffer:

    def __init__(self, offset, part_number):
        self._bufs = []
        self._offset = offset
        self._current_offset = self._offset
        self._part_number = part_number

    @property
    def offset(self):
        return self._offset

    @property
    def current_offset(self):
        return self._current_offset

    @property
    def part_number(self):
        return self._part_number

    @property
    def size(self):
        return self.current_offset - self.offset

    def append(self, buf):
        self._bufs.append(buf)
        self._current_offset += len(buf)

    def suits(self, offset):
        return self.offset <= offset <= self.current_offset

    def collect(self):
        collected_buf_size = self.size
        collected_buf = bytearray(collected_buf_size)
        current_offset = 0
        for current_buf in self._bufs:
            current_buf_size = len(current_buf)
            collected_buf[current_offset:current_offset + current_buf_size] = current_buf
            current_offset += current_buf_size
        return collected_buf


class _MergingMultipartUpload(_MultipartUpload):

    def __init__(self, mpu, min_part_size=5 * MB):
        """
        Merging multipart upload.

        Merges sequential series of upload parts up to a minimum part size.

        :param mpu: Wrapping sequential multipart upload.
        :param min_part_size: Minimum upload part size.
        """
        self._mpu = mpu
        self._min_part_size = min_part_size
        self._pbufs = []

    @property
    def path(self):
        return self._mpu.path

    def initiate(self):
        self._mpu.initiate()

    def upload_part(self, buf, offset=None, part_number=None):
        pbuf = self._matching_sbuf(offset)
        if pbuf:
            pbuf.append(buf)
            if pbuf.size > self._min_part_size:
                self._mpu.upload_part(pbuf.collect(), pbuf.offset, pbuf.part_number)
                self._pbufs.remove(pbuf)
        elif len(buf) > self._min_part_size:
            self._mpu.upload_part(buf, offset, part_number)
        else:
            pbuf = _PartBuffer(offset, part_number)
            pbuf.append(buf)
            self._pbufs.append(pbuf)

    def _matching_sbuf(self, offset):
        for pbuf in self._pbufs:
            if pbuf.suits(offset):
                return pbuf

    def upload_copy_part(self, start, end, offset=None, part_number=None):
        return self._mpu.upload_copy_part(start, end, offset, part_number)

    def complete(self):
        for pbuf in self._pbufs:
            self._mpu.upload_part(pbuf.collect(), pbuf.offset, pbuf.part_number)
        self._pbufs = []
        self._mpu.complete()

    def abort(self):
        self._mpu.abort()


class _SplittingMultipartCopyUpload(_MultipartUpload):

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
            while remaining_length > 0:
                part_size = self._resolve_part_size(remaining_length)
                self._mpu.upload_copy_part(current_offset, current_offset + part_size, offset, part_number)
                remaining_length -= part_size
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


class _FillingGapsMultipartUpload(_MultipartUpload):

    def __init__(self, mpu, offset, original_size, min_part_size=5*MB):
        """
        Filling gaps multipart upload.

        Fills heading and trailing gaps with the original file data.

        :param mpu: Wrapping multipart upload.
        :param offset: First upload part offset.
        :param original_size: Destination file original size.
        """
        self._mpu = mpu
        self._offset = offset
        self._original_size = original_size
        self._min_part_size = min_part_size
        self._sections = SortedList(key=lambda f: f.offset)

    @property
    def path(self):
        return self._mpu.path

    def initiate(self):
        self._mpu.initiate()

    def upload_part(self, buf, offset=None, part_number=None):
        self._sections.add(_UploadSection(offset, len(buf), part_number))
        self._mpu.upload_part(buf, offset, part_number)

    def upload_copy_part(self, start, end, offset=None, part_number=None):
        self._sections.add(_UploadSection(offset, end - start, part_number))
        self._mpu.upload_copy_part(start, end, offset, part_number)

    def complete(self):
        last_fragment_end = 0
        for fragment in self._sections:
            self._upload_gap(last_fragment_end, fragment.offset)
            last_fragment_end = fragment.offset + fragment.length
        if last_fragment_end < self._original_size:
            self._upload_gap(last_fragment_end, self._original_size)
        self._mpu.complete()

    def _upload_gap(self, last_fragment_end, next_fragment_start):
        gap_length = next_fragment_start - last_fragment_end
        if gap_length:
            if gap_length >= self._min_part_size:
                self._mpu.upload_copy_part(last_fragment_end, next_fragment_start, last_fragment_end)
            else:
                # TODO 26.09.2019: Too small gap to be copied.
                raise RuntimeError('Too small gap %d to be copied. Operation is not supported yet.' % gap_length)

    def abort(self):
        self._mpu.abort()


class S3Client(FileSystemClient):
    DOWNLOAD_CHUNK_SIZE_BYTES = 1 * MB
    MULTIPART_PART_MIN_SIZE_BYTES = 5 * MB
    MULTIPART_PART_MAX_SIZE_BYTES = 5 * GB

    def __init__(self, bucket, pipe):
        """
        AWS S3 API client for single bucket operations.

        :param bucket: Name of the AWS S3 bucket.
        :param pipe: Cloud Pipeline API client.
        """
        super(S3Client, self).__init__()
        self._is_read_only = False
        self.bucket = bucket
        session = self._init_session(bucket, pipe)
        proxy_config = self._init_proxy_config()
        self._s3 = session.client('s3', config=proxy_config)
        self._delimiter = '/'
        self._mpus = {}
        self.root_path = '/'

    def _init_session(self, bucket, pipe):
        def refresh():
            bucket_object = pipe.get_storage(bucket)
            credentials = pipe.get_temporary_credentials(bucket_object)
            return dict(
                access_key=credentials.access_key_id,
                secret_key=credentials.secret_key,
                token=credentials.session_token,
                expiry_time=credentials.expiration,
                region_name=credentials.region,
                write_allowed=bucket_object.is_write_allowed())

        fresh_metadata = refresh()

        self._is_read_only = not fresh_metadata['write_allowed']

        session_credentials = RefreshableCredentials.create_from_metadata(
            metadata=fresh_metadata,
            refresh_using=refresh,
            method='sts-assume-role')

        s = get_session()
        s._credentials = session_credentials
        return Session(botocore_session=s, region_name=fresh_metadata['region_name'])

    def _init_proxy_config(self):
        return Config()

    def is_available(self):
        # TODO 05.09.2019: Check AWS API for availability
        return True

    def is_read_only(self):
        return self._is_read_only

    def exists(self, path):
        return len(self.ls(path)) > 0

    def ls(self, path, depth=1):
        paginator = self._s3.get_paginator('list_objects_v2')
        prefix = (path or '').lstrip(self._delimiter)
        recursive = depth < 0
        operation_parameters = {
            'Bucket': self.bucket
        }
        if prefix:
            operation_parameters['Prefix'] = prefix
        if not recursive:
            operation_parameters['Delimiter'] = self._delimiter
        page_iterator = paginator.paginate(**operation_parameters)
        items = []
        for page in page_iterator:
            if 'CommonPrefixes' in page:
                for folder in page['CommonPrefixes']:
                    name = S3Client.get_item_name(folder['Prefix'], prefix=prefix)
                    items.append(self.get_folder_object(name))
            if 'Contents' in page:
                for file in page['Contents']:
                    if not file['Key'].endswith(self._delimiter):
                        name = self.get_file_name(file, prefix, recursive)
                        item = self.get_file_object(file, name)
                        items.append(item)
            break
        return items if path.endswith(self._delimiter) else self._matching_paths(items, path)

    def _matching_paths(self, items, path):
        _, file_name = fuseutils.split_path(path)
        return [item for item in items if item.name.rstrip(self._delimiter) == file_name]

    @classmethod
    def get_item_name(cls, path, prefix, delimiter='/'):
        possible_folder_name = prefix if prefix.endswith(delimiter) else \
            prefix + delimiter
        if prefix and path.startswith(prefix) and path != possible_folder_name and path != prefix:
            if not path == prefix:
                splitted = prefix.split(delimiter)
                return splitted[len(splitted) - 1] + path[len(prefix):]
            else:
                return path[len(prefix):]
        elif not path.endswith(delimiter) and path == prefix:
            return os.path.basename(path)
        elif path == possible_folder_name:
            return os.path.basename(path.rstrip(delimiter)) + delimiter
        else:
            return path

    def get_folder_object(self, name):
        return File(name=name,
                    size=0,
                    mtime=time.mktime(datetime.now(tz=pytz.utc).timetuple()),
                    ctime=None,
                    contenttype='',
                    is_dir=True)

    def get_file_name(self, file, prefix, recursive):
        return file['Key'] if recursive else S3Client.get_item_name(file['Key'], prefix=prefix)

    def get_file_object(self, file, name):
        return File(name=name,
                    size=file.get('Size', ''),
                    mtime=time.mktime(file['LastModified'].astimezone(pytz.utc).timetuple()),
                    ctime=None,
                    contenttype='',
                    is_dir=False)

    def upload(self, buf, path):
        destination_path = path.lstrip(self._delimiter)
        with io.BytesIO(bytearray(buf)) as body:
            self._s3.put_object(Bucket=self.bucket, Key=destination_path, Body=body)

    def delete(self, path):
        source_path = path.lstrip(self._delimiter)
        self._s3.delete_object(Bucket=self.bucket, Key=source_path)

    def mv(self, old_path, path):
        source_path = old_path.lstrip(self._delimiter)
        destination_path = path.lstrip(self._delimiter)
        source = {
            'Bucket': self.bucket,
            'Key': source_path
        }
        self._s3.copy(source, self.bucket, destination_path)
        self._s3.delete_object(**source)

    def mkdir(self, path):
        folder_path = path.lstrip(self._delimiter)
        synthetic_file_path = fuseutils.join_path_with_delimiter(folder_path, '.DS_Store')
        self.upload([], synthetic_file_path)

    def rmdir(self, path):
        for file in self.ls(fuseutils.append_delimiter(path), depth=-1):
            self.delete(file.name)

    def download_range(self, fh, buf, path, offset=0, length=0):
        source_path = path.lstrip(self._delimiter)
        source = {
            'Bucket': self.bucket,
            'Key': source_path
        }
        if offset >= 0 and length >= 0:
            source['Range'] = _http_range(offset, offset + length)
        response = self._s3.get_object(**source)
        self._download(buf, response['Body'])

    def _download(self, buf, response):
        for chunk in iter(lambda: response.read(S3Client.DOWNLOAD_CHUNK_SIZE_BYTES), b''):
            buf.write(chunk)

    def upload_range(self, fh, buf, path, offset=0):
        source_path = path.lstrip(self._delimiter)
        mpu = self._mpus.get(path, None)
        try:
            if not mpu:
                file_size = self.attrs(path).size
                buf_size = len(buf)
                if buf_size < self.MULTIPART_PART_MIN_SIZE_BYTES and file_size < self.MULTIPART_PART_MIN_SIZE_BYTES:
                    self._upload_single_range(fh, buf, source_path, offset)
                else:
                    mpu = self._new_mpu(file_size, offset, source_path)
                    self._mpus[path] = mpu
                    mpu.initiate()
                    mpu.upload_part(buf, offset)
            else:
                mpu.upload_part(buf, offset)
        except _ANY_ERROR:
            if mpu:
                mpu.abort()
                del self._mpus[path]
            raise

    def _new_mpu(self, file_size, offset, source_path):
        mpu = _PlainMultipartUpload(source_path, offset, self.bucket, self._s3)
        mpu = _NumeratingMultipartUpload(mpu)
        mpu = _FillingGapsMultipartUpload(mpu, offset, file_size, self.MULTIPART_PART_MIN_SIZE_BYTES)
        mpu = _MergingMultipartUpload(mpu, self.MULTIPART_PART_MIN_SIZE_BYTES)
        mpu = _SplittingMultipartCopyUpload(mpu, self.MULTIPART_PART_MIN_SIZE_BYTES, self.MULTIPART_PART_MAX_SIZE_BYTES)
        return mpu

    def _upload_single_range(self, fh, buf, path, offset):
        with io.BytesIO() as original_buf:
            self.download_range(fh, original_buf, path)
            modified_bytes = bytearray(original_buf.getvalue())
        modified_bytes[offset: offset + len(buf)] = buf
        with io.BytesIO(modified_bytes) as body:
            self._s3.put_object(Bucket=self.bucket, Key=path, Body=body)

    def flush(self, fh, path):
        mpu = self._mpus.get(path, None)
        if mpu:
            try:
                mpu.complete()
            except _ANY_ERROR:
                mpu.abort()
                raise
            finally:
                del self._mpus[path]
