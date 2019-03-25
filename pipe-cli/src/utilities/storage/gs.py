import copy
import os
from datetime import datetime, timedelta

import click
from google.auth import _helpers
from google.auth.transport.requests import AuthorizedSession
from google.cloud.storage import Client
from google.oauth2.credentials import Credentials

from src.api.data_storage import DataStorage
from src.config import Config
from src.model.data_storage_item_model import DataStorageItemModel, DataStorageItemLabelModel
from src.model.data_storage_tmp_credentials_model import TemporaryCredentialsModel
from src.utilities.progress_bar import ProgressPercentage
from src.utilities.storage.common import AbstractRestoreManager, AbstractListingManager, StorageOperations, \
    AbstractDeleteManager, AbstractTransferManager, UrlIO


class GsProgressPercentage(ProgressPercentage):

    def __init__(self, filename, size):
        super(GsProgressPercentage, self).__init__(filename, size)
        self._total_bytes = 0

    def __call__(self, bytes_amount):
        newest_bytes = bytes_amount - self._total_bytes
        self._total_bytes = bytes_amount
        super(GsProgressPercentage, self).__call__(newest_bytes)

    @staticmethod
    def callback(source_key, size, quiet):
        if not StorageOperations.show_progress(quiet, size):
            return None
        progress = GsProgressPercentage(source_key, size)
        return lambda current: progress(current)


class GsManager:

    def __init__(self, client):
        self.client = client


class GsListingManager(GsManager, AbstractListingManager):

    def __init__(self, client, bucket):
        super(GsListingManager, self).__init__(client)
        self.bucket = bucket

    def list_items(self, relative_path=None, recursive=False, page_size=StorageOperations.DEFAULT_PAGE_SIZE,
                   show_all=False):
        prefix = StorageOperations.get_prefix(relative_path)
        bucket = self.client.get_bucket(self.bucket.path)
        blobs_iterator = bucket.list_blobs(prefix=prefix if relative_path else None,
                                           max_results=page_size if not show_all else None,
                                           delimiter=StorageOperations.PATH_SEPARATOR if not recursive else None)
        # TODO 25.03.19: Handle prefixes as folders.
        absolute_items = [self._to_storage_item(blob) for blob in blobs_iterator]
        return absolute_items if recursive else [self._to_local_item(item, prefix) for item in absolute_items]

    def _to_storage_item(self, blob):
        item = DataStorageItemModel()
        item.name = blob.name
        item.path = item.name
        item.type = 'File'
        item.changed = self._to_local_timezone(blob.updated)
        item.size = blob.size
        item.labels = [DataStorageItemLabelModel('StorageClass', blob.storage_class)]
        return item

    def _to_local_timezone(self, utc_datetime):
        return utc_datetime.astimezone(Config.instance().timezone())

    def _to_local_item(self, absolute_item, prefix):
        relative_item = copy.deepcopy(absolute_item)
        relative_item.name = StorageOperations.get_item_name(relative_item.name, prefix)
        relative_item.path = relative_item.name
        return relative_item


class GsDeleteManager(GsManager, AbstractDeleteManager):

    def __init__(self, client, bucket):
        super(GsDeleteManager, self).__init__(client)
        self.bucket = bucket
        self.delimiter = StorageOperations.PATH_SEPARATOR
        self.listing_manager = GsListingManager(self.client, self.bucket)

    def delete_items(self, relative_path, recursive=False, exclude=[], include=[], version=None, hard_delete=False):
        # TODO 25.03.19: Handle exclude and include filters.
        # TODO 25.03.19: Handle version and hard delete.
        prefix = StorageOperations.get_prefix(relative_path)
        check_file = True
        if prefix.endswith(self.delimiter):
            prefix = prefix[:-1]
            check_file = False
        bucket = self.client.get_bucket(self.bucket.path)
        if not recursive:
            blob = bucket.blob(prefix)
            blob.delete()
        else:
            blob_names_for_deletion = []
            for item in self.listing_manager.list_items(prefix, recursive=True, show_all=True):
                if item.name == prefix and check_file:
                    blob_names_for_deletion = [item.name]
                    break
                if self.__file_under_folder(item.name, prefix):
                    blob_names_for_deletion.append(item.name)
            for blob_name in blob_names_for_deletion:
                blob = bucket.blob(blob_name)
                blob.delete()

    def __file_under_folder(self, file_path, folder_path):
        return StorageOperations.without_prefix(file_path, folder_path).startswith(self.delimiter)


class GsRestoreManager(GsManager, AbstractRestoreManager):

    def __init__(self, client, wrapper):
        super(GsRestoreManager, self).__init__(client)
        self.wrapper = wrapper

    def restore_version(self, version):
        # TODO 25.03.19: Add merged tags to the uploading blob.
        path = self.wrapper.bucket.path
        source_bucket = self.client.get_bucket(path)
        source_blob = source_bucket.blob(self.wrapper.path)
        source_bucket.copy_blob(source_blob, source_bucket, path, source_generation=int(version))


class TransferBetweenGsBucketsManager(GsManager, AbstractTransferManager):

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), skip_existing=False):
        full_path = path
        destination_path = StorageOperations.normalize_path(destination_wrapper, relative_path)
        if skip_existing:
            from_size = source_wrapper.get_list_manager().get_file_size(full_path)
            to_size = destination_wrapper.get_list_manager().get_file_size(destination_path)
            if to_size is not None and to_size == from_size:
                if not quiet:
                    click.echo('Skipping file %s since it exists in the destination %s'
                               % (full_path, destination_path))
                return
        # TODO 25.03.19: Add merged tags to the uploading blob.
        source_bucket = self.client.get_bucket(source_wrapper.bucket.path)
        source_blob = source_bucket.blob(full_path)
        destination_bucket = self.client.get_bucket(destination_wrapper.bucket.path)
        progress_callback = GsProgressPercentage.callback(full_path, size, quiet)
        source_bucket.copy_blob(source_blob, destination_bucket, destination_path)
        progress_callback(size, size)
        if clean:
            source_blob.delete()


class GsDownloadManager(GsManager, AbstractTransferManager):

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), skip_existing=False):
        if path:
            source_key = path
        else:
            source_key = source_wrapper.path
        if destination_wrapper.path.endswith(os.path.sep):
            destination_key = os.path.join(destination_wrapper.path, relative_path)
        else:
            destination_key = destination_wrapper.path
        if skip_existing:
            remote_size = source_wrapper.get_list_manager().get_file_size(source_key)
            local_size = StorageOperations.get_local_file_size(destination_key)
            if local_size is not None and remote_size == local_size:
                if not quiet:
                    click.echo('Skipping file %s since it exists in the destination %s' % (source_key, destination_key))
                return
        folder = os.path.dirname(destination_key)
        if folder and not os.path.exists(folder):
            os.makedirs(folder)
        bucket = self.client.get_bucket(source_wrapper.bucket.path)
        blob = bucket.blob(source_key)
        progress_callback = GsProgressPercentage.callback(source_key, size, quiet)
        blob.download_to_filename(destination_key)
        progress_callback(size)
        if clean:
            blob.delete()


class GsUploadManager(GsManager, AbstractTransferManager):

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), skip_existing=False):
        if path:
            source_key = os.path.join(source_wrapper.path, path)
        else:
            source_key = source_wrapper.path
        destination_key = StorageOperations.normalize_path(destination_wrapper, relative_path)
        if skip_existing:
            local_size = StorageOperations.get_local_file_size(source_key)
            remote_size = destination_wrapper.get_list_manager().get_file_size(destination_key)
            if remote_size is not None and local_size == remote_size:
                if not quiet:
                    click.echo('Skipping file %s since it exists in the destination %s' % (source_key, destination_key))
                return
        # TODO 25.03.19: Add tags to the uploading blob.
        progress_callback = GsProgressPercentage.callback(relative_path, size, quiet)
        bucket = self.client.get_bucket(destination_wrapper.bucket.path)
        blob = bucket.blob(destination_key)
        blob.upload_from_filename(source_key)
        progress_callback(size)
        if clean:
            source_wrapper.delete_item(source_key)


class TransferFromHttpOrFtpToGsManager(GsManager, AbstractTransferManager):

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), skip_existing=False):
        if clean:
            raise AttributeError('Cannot perform \'mv\' operation due to deletion remote files '
                                 'is not supported for ftp/http sources.')
        if path:
            source_key = path
        else:
            source_key = source_wrapper.path
        if destination_wrapper.path.endswith(os.path.sep):
            destination_key = os.path.join(destination_wrapper.path, relative_path)
        else:
            destination_key = destination_wrapper.path
        if skip_existing:
            source_size = size
            destination_size = destination_wrapper.get_list_manager().get_file_size(destination_key)
            if destination_size is not None and source_size == destination_size:
                if not quiet:
                    click.echo('Skipping file %s since it exists in the destination %s' % (source_key, destination_key))
                return
        # TODO 25.03.19: Add tags to the uploading blob.
        progress_callback = GsProgressPercentage.callback(relative_path, size, quiet)
        bucket = self.client.get_bucket(destination_wrapper.bucket.path)
        blob = bucket.blob(destination_key)
        blob.upload_from_file(UrlIO(source_key))
        progress_callback(size)


class GsTemporaryCredentials:
    GS_PROJECT = 'GS_PROJECT'
    GS_STS_PROJECT = 'GS_STS_TOKEN'

    @classmethod
    def from_environment(cls, bucket, read, write):
        credentials = TemporaryCredentialsModel()
        credentials.secret_key = os.getenv(GsTemporaryCredentials.GS_PROJECT)
        credentials.session_token = os.getenv(GsTemporaryCredentials.GS_STS_PROJECT)
        credentials.expiration = datetime.utcnow() + timedelta(hours=1)
        return credentials

    @classmethod
    def from_cp_api(cls, bucket, read, write):
        return DataStorage.get_single_temporary_credentials(bucket=bucket.identifier, read=read, write=write)


class _RefreshingCredentials(Credentials):

    def __init__(self, refresh):
        self._refresh = refresh
        self.temporary_credentials = self._refresh()
        super(_RefreshingCredentials, self).__init__(self.temporary_credentials.session_token)

    def refresh(self, request):
        self.temporary_credentials = self._refresh()

    def apply(self, headers, token=None):
        headers['authorization'] = 'Bearer {}'.format(_helpers.from_bytes(self.temporary_credentials.session_token))


class _RefreshingClient(Client):
    MAX_REFRESH_ATTEMPTS = 100

    def __init__(self, bucket, read, write, refresh_credentials):
        credentials = _RefreshingCredentials(refresh=lambda: refresh_credentials(bucket, read, write))
        session = AuthorizedSession(credentials, max_refresh_attempts=self.MAX_REFRESH_ATTEMPTS)
        super(_RefreshingClient, self).__init__(project=credentials.temporary_credentials.secret_key, _http=session)


class GsBucketOperations:

    @classmethod
    def init_wrapper(cls, wrapper, versioning=False):
        # TODO 25.03.19: Method is not implemented yet.
        raise RuntimeError('Method is not implemented yet.')

    @classmethod
    def get_transfer_between_buckets_manager(cls, source_wrapper, destination_wrapper, command):
        client = GsBucketOperations.get_client(destination_wrapper.bucket, read=True, write=True)
        return TransferBetweenGsBucketsManager(client)

    @classmethod
    def get_download_manager(cls, source_wrapper, destination_wrapper, command):
        client = GsBucketOperations.get_client(source_wrapper.bucket, read=True, write=command == 'mv')
        return GsDownloadManager(client)

    @classmethod
    def get_upload_manager(cls, source_wrapper, destination_wrapper, command):
        client = GsBucketOperations.get_client(destination_wrapper.bucket, read=True, write=True)
        return GsUploadManager(client)

    @classmethod
    def get_transfer_from_http_or_ftp_manager(cls, source_wrapper, destination_wrapper, command):
        client = GsBucketOperations.get_client(destination_wrapper.bucket, read=True, write=True)
        return TransferFromHttpOrFtpToGsManager(client)

    @classmethod
    def get_client(cls, bucket, read, write):
        return _RefreshingClient(bucket, read, write, refresh_credentials=GsTemporaryCredentials.from_environment)
