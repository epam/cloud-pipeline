import io
import logging
import sys
import time
from datetime import datetime

import pytz
from google.auth import _helpers
from google.auth.transport.requests import AuthorizedSession
from google.cloud.storage import Client
from google.oauth2.credentials import Credentials

from fsclient import FileSystemClient, File
from fuseutils import MB
import fuseutils
from mpu import MultipartUpload, ChunkedMultipartUpload, SplittingMultipartCopyUpload, \
    DownloadingMultipartCopyUpload, CompositeMultipartUpload

_ANY_ERROR = Exception


class GCPMultipartUpload(MultipartUpload):

    def __init__(self, bucket, path, gcp):
        """
        GCP composite object multipart upload.

        :param bucket: Destination bucket name.
        :param path: Destination bucket relative path.
        :param gcp: CGP client.
        """
        self._bucket = bucket
        self._path = path
        self._gcp = gcp
        self._bucket_object = None
        self._blob_object = None
        self._parts = {}

    @property
    def path(self):
        return self._path

    def initiate(self):
        logging.info('Initializing multipart upload for %s' % self._path)
        self._bucket_object = self._gcp.bucket(self._bucket)
        self._blob_object = self._bucket_object.blob(self._path)

    def upload_part(self, buf, offset=None, part_number=None):
        logging.info('Uploading multipart upload part range %d-%d for %s' % (offset, offset + len(buf), self._path))
        start = offset or 0
        end = start + len(buf)
        part_path = '%s_%d.tmp' % (self._path[:-4] if self._path.endswith('.tmp') else self._path, part_number)
        part_blob = self._bucket_object.blob(part_path)
        part_blob.upload_from_string(str(buf))
        self._parts[part_number] = part_blob

    def upload_copy_part(self, start, end, offset=None, part_number=None):
        logging.info('Uploading multipart upload part %d for %s' % (part_number, self._path))
        p = self._path[:-4] if self._path.endswith('.tmp') else self._path
        part_path = '%s_%s_%d.tmp' % (p, str(abs(hash(p))), part_number)
        part_blob = self._bucket_object.blob(part_path)
        self._parts[part_number] = part_blob

    def complete(self):
        logging.info('Completing multipart upload for %s' % self._path)
        self._blob_object.compose([self._parts[part_number] for part_number in sorted(self._parts.keys())])
        for part_number in sorted(self._parts.keys()):
            self._parts[part_number].delete()
            del self._parts[part_number]

    def abort(self):
        logging.error('Aborting multipart upload for %s' % self._path)
        for part_number in sorted(self._parts.keys()):
            self._parts[part_number].delete()


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


class _RefreshingClient(Client):
    MAX_REFRESH_ATTEMPTS = sys.maxint

    def __init__(self, refresh):
        credentials = _RefreshingCredentials(refresh)
        session = AuthorizedSession(credentials, max_refresh_attempts=self.MAX_REFRESH_ATTEMPTS)
        super(_RefreshingClient, self).__init__(project=credentials.temporary_credentials.secret_key, _http=session)


class GCPClient(FileSystemClient):
    _MIN_CHUNK = 1
    _MAX_CHUNK = 10000
    _MAX_COMPOSITE_PARTS = 32
    _MIN_PART_SIZE = 5 * MB
    _MAX_PART_SIZE = 500 * MB
    _SINGLE_UPLOAD_SIZE = 5 * MB

    def __init__(self, bucket, pipe, chunk_size):
        """
        GCP API client for single bucket operations.

        :param bucket: Name of the GCP bucket.
        :param pipe: Cloud Pipeline API client.
        :param chunk_size: Multipart upload chunk size.
        """
        super(GCPClient, self).__init__()
        self._delimiter = '/'
        self._is_read_only = False
        path_chunks = bucket.rstrip(self._delimiter).split(self._delimiter)
        self.bucket = path_chunks[0]
        self.root_path = self._delimiter.join(path_chunks[1:]) if len(path_chunks) > 1 else ''
        self._gcp = self._generate_gcp(pipe)
        self._chunk_size = chunk_size
        self._mpus = {}

    def _generate_gcp(self, pipe):
        bucket_object = pipe.get_storage(self.bucket)
        self._is_read_only = not bucket_object.is_write_allowed()
        return _RefreshingClient(lambda: pipe.get_temporary_credentials(bucket_object))

    def is_available(self):
        # TODO 05.09.2019: Check GCP API for availability
        return True

    def is_read_only(self):
        return self._is_read_only

    def exists(self, path, expand_path=True):
        return len(self.ls(path, expand_path=expand_path)) > 0

    def ls(self, path, depth=1, expand_path=True):
        prefix = self.build_full_path(path) if expand_path else path
        recursive = depth < 0
        bucket_object = self._gcp.bucket(self.bucket)
        blobs_iterator = bucket_object.list_blobs(prefix=prefix,
                                                  delimiter=self._delimiter if not recursive else None)
        absolute_files = [self._get_file_object(blob, prefix, recursive) for blob in blobs_iterator]
        absolute_folders = [self._get_folder_object(name) for name in blobs_iterator.prefixes]
        absolute_items = absolute_folders + absolute_files
        return absolute_items

    def build_full_path(self, path):
        full_path = None
        if not self.root_path:
            full_path = path
        elif not path:
            full_path = self.root_path
        else:
            full_path = self.root_path + self._delimiter + path.lstrip(self._delimiter)
        if not full_path:
            return ''
        return full_path.lstrip(self._delimiter)

    def _get_file_object(self, blob, prefix, recursive):
        return File(name=blob.name if recursive else fuseutils.get_item_name(blob.name, prefix=prefix),
                    size=blob.size,
                    mtime=time.mktime(blob.updated.astimezone(pytz.utc).timetuple()),
                    ctime=None,
                    contenttype='',
                    is_dir=False)

    def _get_folder_object(self, name):
        return File(name=name,
                    size=0,
                    mtime=time.mktime(datetime.now(tz=pytz.utc).timetuple()),
                    ctime=None,
                    contenttype='',
                    is_dir=True)

    def upload(self, buf, path, expand_path=True):
        destination_path = self.build_full_path(path) if expand_path else path
        bucket_object = self._gcp.bucket(self.bucket)
        blob = bucket_object.blob(destination_path)
        blob.upload_from_string(str(buf or ''))

    def delete(self, path, expand_path=True):
        prefix = self.build_full_path(path) if expand_path else path
        bucket_object = self._gcp.bucket(self.bucket)
        blob = bucket_object.blob(prefix)
        blob.delete()

    def mv(self, old_path, path):
        source_path = self.build_full_path(old_path)
        destination_path = self.build_full_path(path)
        folder_source_path = fuseutils.append_delimiter(source_path)
        if self.exists(folder_source_path, expand_path=False):
            self._mvdir(folder_source_path, destination_path)
        else:
            self._mvfile(source_path, destination_path)

    def _mvdir(self, folder_source_path, folder_destination_path):
        for file in self.ls(fuseutils.append_delimiter(folder_source_path), depth=-1, expand_path=False):
            relative_path = fuseutils.without_prefix(file.name, folder_source_path)
            destination_path = fuseutils.join_path_with_delimiter(folder_destination_path, relative_path)
            self._mvfile(file.name, destination_path)

    def _mvfile(self, source_path, destination_path):
        source_bucket = self._gcp.bucket(self.bucket)
        source_blob = source_bucket.blob(source_path)
        source_bucket.copy_blob(source_blob, source_bucket, destination_path)
        source_blob.delete()

    def mkdir(self, path):
        synthetic_file_path = fuseutils.join_path_with_delimiter(path, '.DS_Store')
        self.upload([], synthetic_file_path)

    def rmdir(self, path):
        for file in self.ls(fuseutils.append_delimiter(path), depth=-1):
            self.delete(file.name, expand_path=False)

    def download_range(self, fh, buf, path, offset=0, length=0, expand_path=True):
        source_path = self.build_full_path(path) if expand_path else path
        source_bucket = self._gcp.bucket(self.bucket)
        source_blob = source_bucket.blob(source_path)
        body = source_blob.download_as_string(start=offset, end=offset + length)
        buf.write(body)

    def upload_range(self, fh, buf, path, offset=0):
        source_path = self.build_full_path(path)
        mpu = self._mpus.get(source_path, None)
        try:
            if not mpu:
                file_size = self.attrs(path).size
                buf_size = len(buf)
                if buf_size < self._SINGLE_UPLOAD_SIZE and file_size < self._SINGLE_UPLOAD_SIZE:
                    logging.info('Using single range upload approach')
                    self._upload_single_range(fh, buf, source_path, offset, file_size)
                else:
                    logging.info('Using multipart upload approach')
                    mpu = self._new_mpu(source_path, file_size)
                    self._mpus[source_path] = mpu
                    mpu.initiate()
                    mpu.upload_part(buf, offset)
            else:
                mpu.upload_part(buf, offset)
        except _ANY_ERROR:
            if mpu:
                mpu.abort()
                del self._mpus[source_path]
            raise

    def _new_mpu(self, source_path, file_size):
        mpu = CompositeMultipartUpload(self.bucket, path=source_path,
                                       new_mpu_func=lambda path: GCPMultipartUpload(self.bucket, path, self._gcp),
                                       max_composite_parts=self._MAX_COMPOSITE_PARTS)
        mpu = DownloadingMultipartCopyUpload(mpu, download_func=self._generate_region_download_function(source_path))
        mpu = SplittingMultipartCopyUpload(mpu, min_part_size=self._MIN_PART_SIZE, max_part_size=self._MAX_PART_SIZE)
        mpu = ChunkedMultipartUpload(mpu, original_size=file_size,
                                     download_func=self._generate_region_download_function(source_path),
                                     chunk_size=self._chunk_size, min_chunk=self._MIN_CHUNK, max_chunk=self._MAX_CHUNK)
        return mpu

    def _generate_region_download_function(self, path):
        def download_func(region_offset, region_length):
            with io.BytesIO() as buf:
                self.download_range(None, buf, path, region_offset, region_length, expand_path=False)
                return buf.getvalue()
        return download_func

    def _upload_single_range(self, fh, buf, path, offset, file_size):
        if file_size:
            with io.BytesIO() as original_buf:
                self.download_range(fh, original_buf, path, offset=0, length=file_size, expand_path=False)
                modified_bytes = bytearray(original_buf.getvalue())
        else:
            modified_bytes = bytearray()
        modified_bytes[offset: offset + len(buf)] = buf
        logging.info('Uploading range %d-%d for %s' % (offset, offset + len(buf), path))
        self.upload(modified_bytes, path, expand_path=False)

    def flush(self, fh, path):
        source_path = self.build_full_path(path)
        mpu = self._mpus.get(source_path, None)
        if mpu:
            try:
                mpu.complete()
            except _ANY_ERROR:
                mpu.abort()
                raise
            finally:
                del self._mpus[source_path]
