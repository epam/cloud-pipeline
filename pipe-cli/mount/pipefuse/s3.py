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

    def __init__(self, path, offset, size, bucket, s3):
        self._path = path
        self._original_size = size
        self._bucket = bucket
        self._s3 = s3
        self._upload_id = None
        self._ETags = []
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

    def upload_part(self, buf):
        with io.BytesIO(buf) as body:
            response = self._s3.upload_part(
                Bucket=self._bucket,
                Key=self._path,
                Body=body,
                UploadId=self._upload_id,
                PartNumber=len(self._ETags) + self._part_number_shift
            )
        self._ETags.append(response['ETag'])
        self._current_offset += len(buf)

    def complete(self):
        if self._offset:
            self._copy_prefix(self._offset)
        if self._current_offset < self._original_size:
            self._copy_suffix(self._current_offset)
        self._s3.complete_multipart_upload(
            Bucket=self._bucket,
            Key=self._path,
            MultipartUpload={
                'Parts': [
                    {
                        'ETag': ETag,
                        'PartNumber': index + self._part_number_shift
                    } for index, ETag in enumerate(self._ETags)
                ]
            },
            UploadId=self._upload_id
        )

    def _copy_prefix(self, end):
        self._copy(0, end, 1)

    def _copy_suffix(self, start):
        self._copy(start, self._original_size, len(self._ETags) + self._part_number_shift)

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
        self._ETags.insert(part_number - 1, response['CopyPartResult']['ETag'])
        self._part_number_shift -= 1

    def abort(self):
        logging.error('Aborting multipart upload for %s' % self._path)
        self._s3.abort_multipart_upload(Bucket=self._bucket, Key=self._path, UploadId=self._upload_id)


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
        mpu_key = fh, path
        source_path = path.lstrip(self._delimiter)
        mpu = self._mpus.get(mpu_key, None)
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
                    mpu = _MultipartUpload(source_path, offset, file_size, self.bucket, self._s3)
                    self._mpus[mpu_key] = mpu
                    mpu.initiate()
                    mpu.upload_part(uploading_buf)
            else:
                mpu.upload_part(buf)
        except _ANY_ERROR:
            if mpu:
                mpu.abort()
                del self._mpus[mpu_key]
            raise

    def _upload_single_range(self, fh, buf, path, offset):
        with io.BytesIO() as original_buf:
            self.download_range(fh, original_buf, path)
            modified_bytes = bytearray(original_buf.getvalue())
        modified_bytes[offset: offset + len(buf)] = buf
        with io.BytesIO(modified_bytes) as body:
            self._s3.put_object(Bucket=self.bucket, Key=path, Body=body)

    def flush(self, fh, path):
        mpu_key = fh, path
        mpu = self._mpus.get(mpu_key, None)
        if mpu:
            try:
                mpu.complete()
            except _ANY_ERROR:
                mpu.abort()
                raise
            finally:
                del self._mpus[mpu_key]
