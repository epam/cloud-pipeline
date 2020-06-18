import logging
import time
from datetime import datetime

import pytz
from google.auth import _helpers
from google.auth.transport.requests import AuthorizedSession
from google.cloud.storage import Client
from google.oauth2.credentials import Credentials

from fsclient import FileSystemClient, File
from fuseutils import MB, GB
from pipefuse import fuseutils


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
    # todo: Attempt limits should be removed
    MAX_REFRESH_ATTEMPTS = 100

    def __init__(self, refresh):
        credentials = _RefreshingCredentials(refresh)
        session = AuthorizedSession(credentials, max_refresh_attempts=self.MAX_REFRESH_ATTEMPTS)
        super(_RefreshingClient, self).__init__(project=credentials.temporary_credentials.secret_key, _http=session)


class GCPClient(FileSystemClient):
    _MIN_CHUNK = 1
    _MAX_CHUNK = 10000
    _MIN_PART_SIZE = 5 * MB
    _MAX_PART_SIZE = 5 * GB
    _SINGLE_UPLOAD_SIZE = 5 * MB

    def __init__(self, bucket, pipe):
        """
        GCP API client for single bucket operations.

        :param bucket: Name of the GCP bucket.
        :param pipe: Cloud Pipeline API client.
        """
        super(GCPClient, self).__init__()
        self._delimiter = '/'
        self._is_read_only = False
        path_chunks = bucket.rstrip(self._delimiter).split(self._delimiter)
        self.bucket = path_chunks[0]
        self.root_path = self._delimiter.join(path_chunks[1:]) if len(path_chunks) > 1 else ''
        self._gcp = self._generate_gcp(pipe)

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
        # todo: Extract the method to common utils
        from s3 import S3Client
        return File(name=blob.name if recursive else S3Client.get_item_name(blob.name, prefix=prefix),
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
        blob.upload_from_string(str(buf))

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

    def download_range(self, fh, buf, path, offset, length):
        pass

    def upload_range(self, fh, buf, path, offset):
        pass
