import io
import logging
import sys
import time
from datetime import datetime

import pytz
from google.auth import _helpers
from google.auth.transport.requests import AuthorizedSession
from google.cloud.storage import Bucket, Blob, Client
from google.cloud.exceptions import GoogleCloudError
from google.oauth2.credentials import Credentials

import fuseutils
from fsclient import File
from fuseutils import MB, TB
from mpu import MultipartUpload, ChunkedMultipartUpload, SplittingMultipartCopyUpload, \
    CompositeMultipartUpload, AppendOptimizedCompositeMultipartCopyUpload, OutOfBoundsSplittingMultipartCopyUpload, \
    OutOfBoundsFillingMultipartCopyUpload
from storage import StorageLowLevelFileSystemClient


class TruncatedExponentialBackoffException(RuntimeError):

    def __init__(self, *args):
        super(TruncatedExponentialBackoffException, self).__init__(*args)


class GCPMultipartUpload(MultipartUpload):

    def __init__(self, path, bucket, gcp):
        """
        GCP composite object multipart upload.

        :param path: Destination bucket relative path.
        :param bucket: Destination bucket name.
        :param gcp: CGP client.
        """
        self._bucket = bucket
        self._path = path
        self._gcp = gcp
        self._bucket_object = None
        self._blob_object = None
        self._parts = {}
        self._keep_parts = {}

    @property
    def path(self):
        return self._path

    def initiate(self):
        logging.info('Initializing multipart upload for %s' % self._path)
        self._bucket_object = self._gcp.bucket(self._bucket)
        self._blob_object = self._bucket_object.blob(self._path)

    def upload_part(self, buf, offset=None, part_number=None, part_path=None, keep=False):
        logging.info('Uploading multipart upload part #%d range %d-%d as %s for %s'
                     % (part_number, offset, offset + len(buf), part_path, self._path))
        part_blob = self._bucket_object.blob(part_path)
        with io.BytesIO(buf) as body:
            part_blob.upload_from_file(body)
        self._parts[part_number] = part_blob

    def upload_copy_part(self, start, end, offset=None, part_number=None, part_path=None, keep=False):
        logging.info('Attaching multipart upload part #%d copy of %s for %s'
                     % (part_number, part_path, self._path))
        part_blob = self._bucket_object.blob(part_path)
        if keep:
            self._keep_parts[part_number] = part_blob
        else:
            self._parts[part_number] = part_blob

    def complete(self):
        logging.info('Completing multipart upload for %s' % self._path)
        all_parts = {}
        all_parts.update(self._parts)
        all_parts.update(self._keep_parts)
        self._blob_object.compose([all_parts[part_number] for part_number in sorted(all_parts.keys())])
        for part_number in sorted(self._parts.keys()):
            self._parts[part_number].delete()

    def abort(self):
        logging.error('Aborting multipart upload for %s' % self._path)
        for part_number in sorted(self._parts.keys()):
            blob = self._parts[part_number]
            if blob.exists():
                blob.delete()


class _RefreshingCredentials(Credentials):

    def __init__(self, refresh):
        self._refresh = refresh
        self.temporary_credentials = self._refresh()
        super(_RefreshingCredentials, self).__init__(self.temporary_credentials.session_token)

    def refresh(self, request):
        logging.info('Refreshing temporary credentials for data storage.')
        self.temporary_credentials = self._refresh()

    def apply(self, headers, token=None):
        headers['authorization'] = 'Bearer {}'.format(_helpers.from_bytes(self.temporary_credentials.session_token))


def retryable(func):
    """
    Decorator function which implements truncated exponential backoff for request rate issues.
    https://cloud.google.com/storage/docs/request-rate
    https://cloud.google.com/storage/docs/exponential-backoff

    Should be removed once the corresponding issue is resolved.
    https://github.com/googleapis/python-storage/issues/108
    """
    def wrapper(*args, **kwargs):
        iteration = 0
        max_iteration = 5
        while iteration < max_iteration:
            try:
                return func(*args, **kwargs)
            except GoogleCloudError as e:
                if e.code and (e.code == 429 or 500 <= e.code < 600):
                    timeout = 2 ** iteration
                    iteration += 1
                    logging.exception('Retryable error occurred during %s operation. '
                                      'Attempting to use truncated exponential backoff with timeout of %s seconds.'
                                      % (func.__name__, timeout))
                    time.sleep(timeout)
                else:
                    logging.exception('Non retryable error occurred during %s operation. '
                                      'Retrying won\'t be performed.' % func.__name__)
                    raise
        else:
            raise TruncatedExponentialBackoffException('All %s attempts to retry %s operation have failed.'
                                                       % (func.__name__, iteration))

    return wrapper


class _RetryableBlob(Blob):
    """
    Google cloud storage blob that supports retrying.

    Should be removed once the corresponding issue is resolved.
    https://github.com/googleapis/python-storage/issues/108
    """

    @retryable
    def exists(self, *args, **kwargs):
        return super(_RetryableBlob, self).exists(*args, **kwargs)

    @retryable
    def delete(self, *args, **kwargs):
        return super(_RetryableBlob, self).delete(*args, **kwargs)

    @retryable
    def upload_from_file(self, *args, **kwargs):
        super(_RetryableBlob, self).upload_from_file(*args, **kwargs)

    @retryable
    def compose(self, *args, **kwargs):
        super(_RetryableBlob, self).compose(*args, **kwargs)

    @retryable
    def download_to_file(self, *args, **kwargs):
        super(_RetryableBlob, self).download_to_file(*args, **kwargs)


class _RetryableBucket(Bucket):
    """
    Google cloud storage bucket that supports retrying.

    Should be removed once the corresponding issue is resolved.
    https://github.com/googleapis/python-storage/issues/108
    """

    @retryable
    def list_blobs(self, *args, **kwargs):
        return super(_RetryableBucket, self).list_blobs(*args, **kwargs)

    @retryable
    def copy_blob(self, *args, **kwargs):
        return super(_RetryableBucket, self).copy_blob(*args, **kwargs)

    def blob(self, blob_name, chunk_size=None, encryption_key=None, kms_key_name=None, generation=None):
        return _RetryableBlob(
            name=blob_name,
            bucket=self,
            chunk_size=chunk_size,
            encryption_key=encryption_key,
            kms_key_name=kms_key_name,
            generation=generation,
        )


class _RefreshingClient(Client):
    MAX_REFRESH_ATTEMPTS = sys.maxint

    def __init__(self, refresh):
        credentials = _RefreshingCredentials(refresh)
        session = AuthorizedSession(credentials, max_refresh_attempts=self.MAX_REFRESH_ATTEMPTS)
        super(_RefreshingClient, self).__init__(project=credentials.temporary_credentials.secret_key, _http=session)

    def bucket(self, bucket_name, user_project=None):
        return _RetryableBucket(client=self, name=bucket_name, user_project=user_project)


class GoogleStorageLowLevelFileSystemClient(StorageLowLevelFileSystemClient):

    def __init__(self, bucket, pipe, chunk_size, storage_path):
        """
        Google storage low level file system client operations.

        :param bucket: Name of the GCP bucket.
        :param pipe: Cloud Pipeline API client.
        :param chunk_size: Multipart upload chunk size.
        """
        super(GoogleStorageLowLevelFileSystemClient, self).__init__()
        self._delimiter = '/'
        self._is_read_only = False
        self.bucket = bucket
        self._gcp = self._generate_gcp(pipe, storage_path)
        self._chunk_size = chunk_size
        self._max_size = 5 * TB
        self._min_chunk = 1
        self._max_chunk = self._max_size / self._chunk_size
        self._min_part_size = 5 * MB
        self._max_part_size = 500 * MB
        self._max_composite_parts = 32

    def _generate_gcp(self, pipe, storage_path):
        bucket_object = pipe.get_storage(storage_path)
        self._is_read_only = not bucket_object.is_write_allowed()
        return _RefreshingClient(lambda: pipe.get_temporary_credentials(bucket_object))

    def is_available(self):
        # TODO 05.09.2019: Check GCP API for availability
        return True

    def is_read_only(self):
        return self._is_read_only

    def ls(self, path, depth=1):
        prefix = path.lstrip(self._delimiter)
        recursive = depth < 0
        bucket_object = self._gcp.bucket(self.bucket)
        blobs_iterator = bucket_object.list_blobs(prefix=prefix, delimiter=self._delimiter if not recursive else None)
        absolute_files = [self._get_file_object(blob, prefix, recursive) for blob in blobs_iterator]
        absolute_folders = [self._get_folder_object(name, prefix, recursive) for name in blobs_iterator.prefixes]
        absolute_items = absolute_folders + absolute_files
        return absolute_items

    def _get_file_object(self, blob, prefix, recursive):
        return File(name=self._get_object_name(blob.name, prefix, recursive),
                    size=blob.size,
                    mtime=time.mktime(blob.updated.astimezone(pytz.utc).timetuple()),
                    ctime=None,
                    contenttype='',
                    is_dir=False)

    def _get_folder_object(self, name, prefix, recursive):
        return File(name=self._get_object_name(name, prefix, recursive),
                    size=0,
                    mtime=time.mktime(datetime.now(tz=pytz.utc).timetuple()),
                    ctime=None,
                    contenttype='',
                    is_dir=True)

    def _get_object_name(self, name, prefix, recursive):
        return name if recursive else fuseutils.get_item_name(name, prefix=prefix)

    def upload(self, buf, path):
        with io.BytesIO(bytearray(buf)) as body:
            bucket_object = self._gcp.bucket(self.bucket)
            blob = bucket_object.blob(path)
            blob.upload_from_file(body)

    def delete(self, path):
        bucket_object = self._gcp.bucket(self.bucket)
        blob = bucket_object.blob(path)
        blob.delete()

    def mv(self, old_path, path):
        source_bucket = self._gcp.bucket(self.bucket)
        source_blob = source_bucket.blob(old_path)
        source_bucket.copy_blob(source_blob, source_bucket, path)
        source_blob.delete()

    def download_range(self, fh, buf, path, offset=0, length=0):
        logging.info('Downloading range %d-%d for %s' % (offset, offset + length, path))
        source_bucket = self._gcp.bucket(self.bucket)
        source_blob = source_bucket.blob(path)
        source_blob.download_to_file(buf, start=offset, end=offset + length - 1)

    def new_mpu(self, path, file_size, download, mv):
        def new_composing_mpu(path):
            return GCPMultipartUpload(path, self.bucket, self._gcp)
        mpu = CompositeMultipartUpload(self.bucket, path=path, new_mpu=new_composing_mpu, mv=mv,
                                       max_composite_parts=self._max_composite_parts)
        mpu = AppendOptimizedCompositeMultipartCopyUpload(mpu, original_size=file_size, chunk_size=self._chunk_size,
                                                          download=download)
        mpu = OutOfBoundsFillingMultipartCopyUpload(mpu, original_size=file_size, download=download)
        mpu = SplittingMultipartCopyUpload(mpu, min_part_size=self._min_part_size, max_part_size=self._max_part_size)
        mpu = OutOfBoundsSplittingMultipartCopyUpload(mpu, original_size=file_size,
                                                      min_part_size=self._min_part_size, max_part_size=self._chunk_size)
        mpu = ChunkedMultipartUpload(mpu, original_size=file_size, download=download,
                                     chunk_size=self._chunk_size, min_chunk=self._min_chunk, max_chunk=self._max_chunk)
        return mpu
