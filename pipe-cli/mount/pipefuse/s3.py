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
from datetime import datetime

import pytz
from boto3 import Session
from botocore.config import Config
from botocore.credentials import RefreshableCredentials
from botocore.session import get_session
import fuseutils
from fsclient import File, FileSystemClient
from fuseutils import MB

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
    def upload_part(self, buf, offset=None):
        pass

    @abstractmethod
    def complete(self):
        pass

    @abstractmethod
    def abort(self):
        pass


class _UploadPart:

    def __init__(self, number, ETag):
        self._number = number
        self._ETag = ETag

    @property
    def ETag(self):
        return self._ETag

    @property
    def number(self):
        return self._number


class _SequentialMultipartUpload(_MultipartUpload):

    def __init__(self, path, offset, size, bucket, s3):
        """
        Sequential multipart upload.

        Minimal upload part size is 5 MB. See https://docs.aws.amazon.com/en_us/AmazonS3/latest/dev/qfacts.html.

        :param path: Destination bucket relative path.
        :param offset: First upload part offset.
        :param size: Destination file original size.
        :param bucket: Destination bucket name.
        :param s3: Boto S3 client.
        """
        self._path = path
        self._original_size = size
        self._bucket = bucket
        self._s3 = s3
        self._upload_id = None
        self._parts = []
        self._part_number_shift = 2
        self._offset = offset
        self._current_offset = self._offset

    @property
    def path(self):
        return self._path

    def initiate(self):
        response = self._s3.create_multipart_upload(
            Bucket=self._bucket,
            Key=self._path
        )
        self._upload_id = response['UploadId']

    def upload_part(self, buf, offset=None):
        with io.BytesIO(buf) as body:
            part_number = self._next_part_number()
            response = self._s3.upload_part(
                Bucket=self._bucket,
                Key=self._path,
                Body=body,
                UploadId=self._upload_id,
                PartNumber=part_number
            )
        self._parts.append(_UploadPart(part_number, response['ETag']))
        self._current_offset += len(buf)

    def _next_part_number(self):
        return len(self._parts) + self._part_number_shift

    def complete(self):
        if self._offset:
            self._copy_prefix()
        if self._current_offset < self._original_size:
            self._copy_suffix()
        self._s3.complete_multipart_upload(
            Bucket=self._bucket,
            Key=self._path,
            MultipartUpload={
                'Parts': [
                    {
                        'ETag': part.ETag,
                        'PartNumber': part.number
                    } for part in self._parts
                ]
            },
            UploadId=self._upload_id
        )

    def _copy_prefix(self):
        self._copy(0, self._offset, 1)

    def _copy_suffix(self):
        self._copy(self._offset, self._original_size, self._next_part_number())

    def _copy(self, start, end, part_number):
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
        self._parts.insert(part_number - 1, _UploadPart(part_number, response['CopyPartResult']['ETag']))

    def abort(self):
        logging.error('Aborting multipart upload for %s' % self._path)
        self._s3.abort_multipart_upload(Bucket=self._bucket, Key=self._path, UploadId=self._upload_id)


class _BufferedSequentialMultipartUpload(_MultipartUpload):

    def __init__(self, mpu, minimal_buffer_size):
        """
        Buffered sequential multipart upload.

        Merges several sequential upload parts to reach a certain size.

        :param mpu: Wrapping multipart upload object.
        :param minimal_buffer_size: Minimal buffer size to be used as an upload part.
        """
        self._mpu = mpu
        self._minimal_buffer_size = minimal_buffer_size
        self._bufs = []
        self._bufs_offset = 0
        self._bufs_current_offset = 0

    @property
    def path(self):
        return self._mpu.path

    def initiate(self):
        self._mpu.initiate()

    def upload_part(self, buf, offset=None):
        if self._bufs:
            self._bufs.append(buf)
            self._bufs_current_offset += len(buf)
            if self._bufs_current_offset - self._bufs_offset > self._minimal_buffer_size:
                self._mpu.upload_part(self._collect_current_buffer(), self._bufs_offset)
                self._clear_current_buffer()
        else:
            if len(buf) > self._minimal_buffer_size:
                self._mpu.upload_part(buf, offset)
            else:
                self._bufs.append(buf)
                self._bufs_offset = offset
                self._bufs_current_offset = offset + len(buf)

    def _collect_current_buffer(self):
        collected_buf = bytearray(self._bufs_current_offset - self._bufs_offset)
        start = 0
        for buf in self._bufs:
            end = start + len(buf)
            collected_buf[start:end] = buf[:]
        return collected_buf

    def _clear_current_buffer(self):
        self._bufs = []
        self._bufs_offset = 0
        self._bufs_current_offset = 0

    def complete(self):
        if self._bufs:
            self._mpu.upload_part(self._collect_current_buffer(), self._bufs_offset)
            self._clear_current_buffer()
        self._mpu.complete()

    def abort(self):
        self._mpu.abort()


class S3Client(FileSystemClient):
    DOWNLOAD_CHUNK_SIZE_BYTES = 1 * MB
    MULTIPART_PART_MIN_SIZE_BYTES = 5 * MB

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
                    uploading_buf = buf
                    if offset and offset <= 5 * MB:
                        with io.BytesIO() as prefix_buf:
                            self.download_range(fh, prefix_buf, source_path)
                            uploading_buf = bytearray(prefix_buf.getvalue()) + buf
                    mpu = self._new_mpu(file_size, offset, source_path)
                    self._mpus[path] = mpu
                    mpu.initiate()
                    mpu.upload_part(uploading_buf, offset)
            else:
                mpu.upload_part(buf, offset)
        except _ANY_ERROR:
            if mpu:
                mpu.abort()
                del self._mpus[path]
            raise

    def _new_mpu(self, file_size, offset, source_path):
        mpu = _SequentialMultipartUpload(source_path, offset, file_size, self.bucket, self._s3)
        return _BufferedSequentialMultipartUpload(mpu, self.MULTIPART_PART_MIN_SIZE_BYTES)

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
