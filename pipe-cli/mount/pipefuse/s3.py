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

import io
import logging

import time
from datetime import datetime

import pytz
from boto3 import Session
from botocore.config import Config
from botocore.credentials import RefreshableCredentials
from botocore.session import get_session

import fuseutils
from fsclient import File
from fuseutils import MB, GB
from mpu import MultipartUpload, SplittingMultipartCopyUpload, ChunkedMultipartUpload, \
    TruncatingMultipartCopyUpload, OutOfBoundsSplittingMultipartCopyUpload, \
    OutOfBoundsFillingMultipartCopyUpload
from storage import StorageLowLevelFileSystemClient

_ANY_ERROR = Exception


def _http_range(start, end):
    return 'bytes=%s-%s' % (start, end - 1)


class S3MultipartUpload(MultipartUpload):

    def __init__(self, path, bucket, s3):
        """
        Plain multipart upload.

        :param path: Destination bucket relative path.
        :param bucket: Destination bucket name.
        :param s3: Boto S3 client.
        """
        self._path = path
        self._bucket = bucket
        self._s3 = s3
        self._upload_id = None
        self._parts = {}

    @property
    def path(self):
        return self._path

    def initiate(self):
        logging.info('Initializing multipart upload for %s' % self._path)
        response = self._s3.create_multipart_upload(
            Bucket=self._bucket,
            Key=self._path,
            ACL='bucket-owner-full-control'
        )
        self._upload_id = response['UploadId']

    def upload_part(self, buf, offset=None, part_number=None, part_path=None, keep=False):
        logging.info('Uploading multipart upload part %d range %d-%d for %s'
                     % (part_number, offset, offset + len(buf), self._path))
        with io.BytesIO(buf) as body:
            response = self._s3.upload_part(
                Bucket=self._bucket,
                Key=self._path,
                Body=body,
                UploadId=self._upload_id,
                PartNumber=part_number
            )
        self._parts[part_number] = response['ETag']

    def upload_copy_part(self, start, end, offset=None, part_number=None, part_path=None, keep=False):
        logging.info('Uploading multipart upload part %d copy range %d-%d for %s'
                     % (part_number, start, end, self._path))
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
        self._parts[part_number] = response['CopyPartResult']['ETag']

    def complete(self):
        logging.info('Completing multipart upload for %s' % self._path)
        self._s3.complete_multipart_upload(
            Bucket=self._bucket,
            Key=self._path,
            MultipartUpload={
                'Parts': [
                    {
                        'ETag': self._parts[part_number],
                        'PartNumber': part_number
                    } for part_number in sorted(self._parts.keys())
                ]
            },
            UploadId=self._upload_id
        )

    def abort(self):
        logging.error('Aborting multipart upload for %s' % self._path)
        self._s3.abort_multipart_upload(Bucket=self._bucket, Key=self._path, UploadId=self._upload_id)


class S3StorageLowLevelClient(StorageLowLevelFileSystemClient):

    def __init__(self, bucket, pipe, chunk_size, storage_path):
        """
        AWS S3 storage low level file system client operations.

        :param bucket: Name of the AWS S3 bucket.
        :param pipe: Cloud Pipeline API client.
        :param chunk_size: Multipart upload chunk size.
        """
        super(S3StorageLowLevelClient, self).__init__()
        self._delimiter = '/'
        self._is_read_only = False
        self.bucket = bucket
        self._s3 = self._generate_s3_client(storage_path, pipe)
        self._chunk_size = chunk_size
        self._min_chunk = 1
        self._max_chunk = 10000
        self._min_part_size = 5 * MB
        self._max_part_size = 5 * GB

    def _generate_s3_client(self, bucket, pipe):
        session = self._generate_aws_session(bucket, pipe)
        return session.client('s3', config=Config())

    def _generate_aws_session(self, bucket, pipe):
        def refresh():
            logging.info('Refreshing temporary credentials for data storage %s' % bucket)
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

    def is_available(self):
        # TODO 05.09.2019: Check AWS API for availability
        return True

    def is_read_only(self):
        return self._is_read_only

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
                    name = fuseutils.get_item_name(folder['Prefix'], prefix=prefix)
                    items.append(self.get_folder_object(name))
            if 'Contents' in page:
                for file in page['Contents']:
                    if not file['Key'].endswith(self._delimiter):
                        name = self.get_file_name(file, prefix, recursive)
                        item = self.get_file_object(file, name)
                        items.append(item)
            if 'IsTruncated' in page and not page['IsTruncated']:
                break
        return items if path.endswith(self._delimiter) else self._matching_paths(items, path)

    def _matching_paths(self, items, path):
        _, file_name = fuseutils.split_path(path)
        return [item for item in items if item.name.rstrip(self._delimiter) == file_name]

    def get_folder_object(self, name):
        return File(name=name,
                    size=0,
                    mtime=time.mktime(datetime.now(tz=pytz.utc).timetuple()),
                    ctime=None,
                    contenttype='',
                    is_dir=True)

    def get_file_name(self, file, prefix, recursive):
        return file['Key'] if recursive else fuseutils.get_item_name(file['Key'], prefix=prefix)

    def get_file_object(self, file, name):
        return File(name=name,
                    size=file.get('Size', ''),
                    mtime=time.mktime(file['LastModified'].astimezone(pytz.utc).timetuple()),
                    ctime=None,
                    contenttype='',
                    is_dir=False)

    def upload(self, buf, path):
        with io.BytesIO(bytearray(buf)) as body:
            self._s3.put_object(Bucket=self.bucket, Key=path, Body=body,
                                ACL='bucket-owner-full-control')

    def delete(self, path, expand_path=True):
        self._s3.delete_object(Bucket=self.bucket, Key=path)

    def mv(self, old_path, path):
        source = {
            'Bucket': self.bucket,
            'Key': old_path
        }
        self._s3.copy(source, self.bucket, path)
        self._s3.delete_object(**source)

    def download_range(self, fh, buf, path, offset=0, length=0):
        logging.info('Downloading range %d-%d for %s' % (offset, offset + length, path))
        source = {
            'Bucket': self.bucket,
            'Key': path
        }
        if offset >= 0 and length >= 0:
            source['Range'] = _http_range(offset, offset + length)
        response = self._s3.get_object(**source)
        self._download(buf, response['Body'])

    def _download(self, buf, response):
        for chunk in iter(lambda: response.read(1 * MB), b''):
            buf.write(chunk)

    def new_mpu(self, path, file_size, download, mv):
        mpu = S3MultipartUpload(path, self.bucket, self._s3)
        mpu = OutOfBoundsFillingMultipartCopyUpload(mpu, original_size=file_size, download=download)
        mpu = SplittingMultipartCopyUpload(mpu, min_part_size=self._min_part_size, max_part_size=self._max_part_size)
        mpu = OutOfBoundsSplittingMultipartCopyUpload(mpu, original_size=file_size,
                                                      min_part_size=self._min_part_size, max_part_size=self._chunk_size)
        mpu = ChunkedMultipartUpload(mpu, original_size=file_size, download=download,
                                     chunk_size=self._chunk_size, min_chunk=self._min_chunk, max_chunk=self._max_chunk)
        return mpu

    def truncate(self, fh, path, length):
        logging.info('Truncating %s to %d' % (path, length))
        file_size = self.attrs(path).size
        if not length:
            self.upload(bytearray(), path)
        elif file_size > length:
            mpu = self._new_truncating_mpu(path, length)
            try:
                mpu.initiate()
                mpu.complete()
            except _ANY_ERROR:
                mpu.abort()
                raise
        elif file_size < length:
            raise RuntimeError('S3 filesystem client doesn\'t support up truncate operation. '
                               'Use some of the existing filesystem client decorators.')

    def _new_truncating_mpu(self, source_path, length):
        mpu = S3MultipartUpload(source_path, bucket=self.bucket, s3=self._s3)
        mpu = SplittingMultipartCopyUpload(mpu, min_part_size=self._min_part_size, max_part_size=self._max_part_size)
        mpu = TruncatingMultipartCopyUpload(mpu, length=length, min_part_number=self._min_chunk)
        return mpu
