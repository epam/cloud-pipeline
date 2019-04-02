import copy
import os
from datetime import datetime, timedelta

try:
    from urllib.parse import urlparse  # Python 3
    from urllib.request import urlopen  # Python 3
except ImportError:
    from urlparse import urlparse  # Python 2
    from urllib2 import urlopen  # Python 2

import click
from google.auth import _helpers
from google.auth.transport.requests import AuthorizedSession
from google.cloud.storage import Client, Blob
from google.oauth2.credentials import Credentials

from src.api.data_storage import DataStorage
from src.config import Config
from src.model.data_storage_item_model import DataStorageItemModel, DataStorageItemLabelModel
from src.model.data_storage_tmp_credentials_model import TemporaryCredentialsModel
from src.utilities.patterns import PatternMatcher
from src.utilities.progress_bar import ProgressPercentage
from src.utilities.storage.common import AbstractRestoreManager, AbstractListingManager, StorageOperations, \
    AbstractDeleteManager, AbstractTransferManager


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

    def __init__(self, client, bucket, show_versions=False):
        super(GsListingManager, self).__init__(client)
        self.bucket = bucket
        self.show_versions = show_versions

    def list_items(self, relative_path=None, recursive=False, page_size=StorageOperations.DEFAULT_PAGE_SIZE,
                   show_all=False):
        prefix = StorageOperations.get_prefix(relative_path)
        bucket = self.client.get_bucket(self.bucket.path)
        blobs_iterator = bucket.list_blobs(prefix=prefix if relative_path else None,
                                           max_results=page_size if not show_all and not self.show_versions else None,
                                           delimiter=StorageOperations.PATH_SEPARATOR if not recursive else None,
                                           versions=self.show_versions)
        absolute_files = [self._to_storage_file(blob) for blob in blobs_iterator]
        absolute_folders = [self._to_storage_folder(name) for name in blobs_iterator.prefixes]
        absolute_versions = absolute_files if not self.show_versions \
            else self._group_files_to_versions(absolute_files, absolute_folders, page_size, show_all)
        absolute_items = absolute_folders + absolute_versions
        requested_items = absolute_items if recursive else [self._to_local_item(item, prefix)
                                                            for item in absolute_items]
        return requested_items if show_all or not page_size else requested_items[:page_size]

    def _to_storage_file(self, blob):
        item = DataStorageItemModel()
        item.name = blob.name
        item.path = item.name
        item.type = 'File'
        item.changed = self._to_local_timezone(blob.updated)
        item.size = blob.size
        item.labels = [DataStorageItemLabelModel('StorageClass', blob.storage_class)]
        item.version = blob.generation
        item.deleted = self._to_local_timezone(blob.time_deleted) if blob.time_deleted else None
        return item

    def _to_local_timezone(self, utc_datetime):
        return utc_datetime.astimezone(Config.instance().timezone())

    def _to_storage_folder(self, name):
        item = DataStorageItemModel()
        item.name = name
        item.path = item.name
        item.type = 'Folder'
        return item

    def _group_files_to_versions(self, absolute_files, absolute_folders, page_size, show_all):
        page_size = page_size - len(absolute_folders) if page_size and not show_all else None
        names = set(file.name for file in absolute_files)
        absolute_versions = []
        number_of_versions = 0
        for name in names:
            files = [file for file in absolute_files if file.name == name]
            files.reverse()
            latest_file = files[0]
            latest_file.latest = not latest_file.deleted
            latest_file.versions = files
            number_of_versions += len(latest_file.versions)
            if latest_file.deleted:
                # Because additional synthetic delete version will be shown to user it should be counted in the number
                # of file versions.
                number_of_versions += 1
            if page_size and number_of_versions > page_size:
                number_of_extra_versions = number_of_versions - page_size
                latest_file.versions = latest_file.versions[:-number_of_extra_versions]
                if latest_file.versions or latest_file.deleted:
                    absolute_versions.append(latest_file)
                break
            absolute_versions.append(latest_file)
        return absolute_versions

    def _to_local_item(self, absolute_item, prefix):
        relative_item = copy.deepcopy(absolute_item)
        relative_item.name = StorageOperations.get_item_name(relative_item.name, prefix)
        relative_item.path = relative_item.name
        return relative_item

    def get_file_tags(self, relative_path):
        bucket = self.client.get_bucket(self.bucket.path)
        blob = bucket.blob(relative_path)
        blob.reload()
        return blob.metadata or {}


class GsDeleteManager(GsManager, AbstractDeleteManager):

    def __init__(self, client, bucket):
        super(GsDeleteManager, self).__init__(client)
        self.bucket = bucket
        self.delimiter = StorageOperations.PATH_SEPARATOR
        self.listing_manager = GsListingManager(self.client, self.bucket)

    def delete_items(self, relative_path, recursive=False, exclude=[], include=[], version=None, hard_delete=False):
        if recursive and version:
            raise RuntimeError('Recursive folder deletion with specified version is not available '
                               'for GCP cloud provider.')
        prefix = StorageOperations.get_prefix(relative_path)
        check_file = True
        if prefix.endswith(self.delimiter):
            prefix = prefix[:-1]
            check_file = False
        bucket = self.client.get_bucket(self.bucket.path)
        if not recursive and not hard_delete:
            self._delete_blob(self._blob(bucket, prefix, version), exclude, include)
        else:
            blobs_for_deletion = []
            self.listing_manager.show_versions = version is not None or hard_delete
            for item in self.listing_manager.list_items(prefix, recursive=True, show_all=True):
                if item.name == prefix and check_file:
                    if version:
                        matching_item_versions = [item_version for item_version in item.versions
                                                  if item_version.version == version]
                        if matching_item_versions:
                            blobs_for_deletion = [self._blob(bucket, item.name, matching_item_versions[0].version)]
                    else:
                        blobs_for_deletion.extend(self._item_blobs_for_deletion(bucket, item, hard_delete))
                    break
                if self._file_under_folder(item.name, prefix):
                    blobs_for_deletion.extend(self._item_blobs_for_deletion(bucket, item, hard_delete))
            for blob in blobs_for_deletion:
                self._delete_blob(blob, exclude, include, prefix)

    def _item_blobs_for_deletion(self, bucket, item, hard_delete):
        if hard_delete:
            return [self._blob(bucket, item.name, item_version.version) for item_version in item.versions]
        else:
            return [bucket.blob(item.name)]

    def _blob(self, bucket, blob_name, generation):
        """
        Returns blob instance with the specified name and generation.

        The current method is a workaround for the absence of support for the operation in the official SDK.
        The support for such an operation was requested implemented in #7444 pull request that is
        already merged. Therefore, as long as google-cloud-storage==1.15.0 is released the usage of the current
        method should be replaced with the usage of a corresponding SDK method.
        """
        blob = bucket.blob(blob_name)
        if generation:
            blob._patch_property('generation', int(generation))
        return blob

    def _delete_blob(self, blob, exclude, include, prefix=None):
        if self._is_matching_delete_filters(blob.name, exclude, include, prefix):
            self.client._delete_blob_generation(blob)

    def _is_matching_delete_filters(self, blob_name, exclude, include, prefix=None):
        if prefix:
            relative_file_name = StorageOperations.get_item_name(blob_name, prefix=prefix + self.delimiter)
            file_name = StorageOperations.get_prefix(relative_file_name)
        else:
            file_name = blob_name
        return PatternMatcher.match_any(file_name, include) \
               and not PatternMatcher.match_any(file_name, exclude, default=False)

    def _file_under_folder(self, file_path, folder_path):
        return StorageOperations.without_prefix(file_path, folder_path).startswith(self.delimiter)


class GsRestoreManager(GsManager, AbstractRestoreManager):

    def __init__(self, client, wrapper):
        super(GsRestoreManager, self).__init__(client)
        self.wrapper = wrapper
        self.listing_manager = GsListingManager(self.client, self.wrapper.bucket, show_versions=True)

    def restore_version(self, version):
        bucket = self.client.get_bucket(self.wrapper.bucket.path)
        if version:
            blob = bucket.blob(self.wrapper.path)
            all_items = self.listing_manager.list_items(blob.name, show_all=True)
            file_items = [item for item in all_items if item.name == blob.name]
            if not file_items:
                raise RuntimeError('Version "%s" doesn\'t exist.' % version)
            item = file_items[0]
            try:
                version = int(version)
            except ValueError:
                raise RuntimeError('Version "%s" doesn\'t exist.' % version)
            if not any(item.version == version for item in item.versions):
                raise RuntimeError('Version "%s" doesn\'t exist.' % version)
            if not item.deleted and item.version == version:
                raise RuntimeError('Version "%s" is already the latest version.' % version)
            bucket.copy_blob(blob, bucket, blob.name, source_generation=int(version))
        else:
            all_items = self.listing_manager.list_items(self.wrapper.path, show_all=True, recursive=True)
            file_items = [item for item in all_items if item.name == self.wrapper.path]
            if file_items:
                item = file_items[0]
                if not item.deleted:
                    raise RuntimeError('Latest file version is not deleted. Please specify "--version" parameter.')
                self._restore_latest_archived_version(bucket, item)
            else:
                for item in all_items:
                    if item.deleted:
                        self._restore_latest_archived_version(bucket, item)

    def _restore_latest_archived_version(self, bucket, item):
        blob = bucket.blob(item.name)
        latest_version = item.version
        bucket.copy_blob(blob, bucket, blob.name, source_generation=int(latest_version))


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
        source_bucket = self.client.get_bucket(source_wrapper.bucket.path)
        source_blob = source_bucket.blob(full_path)
        destination_bucket = self.client.get_bucket(destination_wrapper.bucket.path)
        progress_callback = GsProgressPercentage.callback(full_path, size, quiet)
        source_bucket.copy_blob(source_blob, destination_bucket, destination_path)
        destination_blob = destination_bucket.blob(destination_path)
        destination_blob.metadata = self._destination_tags(source_wrapper, full_path, tags)
        destination_blob.patch()
        progress_callback(size)
        if clean:
            source_blob.delete()

    def _destination_tags(self, source_wrapper, full_path, raw_tags):
        tags = StorageOperations.parse_tags(raw_tags) if raw_tags \
            else source_wrapper.get_list_manager().get_file_tags(full_path)
        tags.update(StorageOperations.source_tags(tags, full_path, source_wrapper))
        return tags


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
        progress_callback = GsProgressPercentage.callback(relative_path, size, quiet)
        bucket = self.client.get_bucket(destination_wrapper.bucket.path)
        blob = bucket.blob(destination_key)
        blob.metadata = StorageOperations.generate_tags(tags, source_key)
        blob.upload_from_filename(source_key)
        progress_callback(size)
        if clean:
            source_wrapper.delete_item(source_key)


class _SourceUrlIO:

    def __init__(self, response):
        self.response = response
        self.read_bytes_number = 0

    def tell(self):
        return self.read_bytes_number

    def read(self, *args, **kwargs):
        new_bytes = self.response.read(*args, **kwargs)
        self.read_bytes_number += len(new_bytes)
        return new_bytes


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
        progress_callback = GsProgressPercentage.callback(relative_path, size, quiet)
        bucket = self.client.get_bucket(destination_wrapper.bucket.path)
        blob = bucket.blob(destination_key)
        blob.metadata = StorageOperations.generate_tags(tags, source_key)
        blob.upload_from_file(_SourceUrlIO(urlopen(source_key)))
        progress_callback(blob.size)


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


class _ProxySession(AuthorizedSession):

    def request(self, method, url, data=None, headers=None, **kwargs):
        parsed_url = urlparse(url)
        request_url = '%s://%s' % (parsed_url.scheme, parsed_url.netloc)
        self.proxies = StorageOperations.get_proxy_config(request_url)
        return super(_ProxySession, self).request(method, url, data, headers, **kwargs)


class _DeleteBlobGenerationMixin:

    def _delete_blob_generation(self, blob):
        """
        Deletes a specific blob generation.

        If the given blob has generation then it will be deleted, otherwise the latest blob generation will.

        The current method is a workaround for the absence of support for the operation in the official SDK.
        The support for such an operation was requested in #5781 issue and implemented in #7444 pull request that is
        already merged. Therefore, as long as google-cloud-storage==1.15.0 is released the usage of the current
        method should be replaced with the usage of a corresponding SDK method.

        See also:
        https://github.com/googleapis/google-cloud-python/issues/5781
        https://github.com/googleapis/google-cloud-python/pull/7444
        """
        storage_name, blob_name, generation = blob.bucket.name, blob.name, blob.generation
        query_params = {'userProject': self.project}
        if generation:
            query_params['generation'] = generation
        bucket_path = "/b/" + storage_name
        blob_path = Blob.path_helper(bucket_path, blob_name)
        self._connection.api_request(
            method="DELETE",
            path=blob_path,
            query_params=query_params,
            _target_object=None,
        )


class _RefreshingClient(Client, _DeleteBlobGenerationMixin):
    MAX_REFRESH_ATTEMPTS = 100

    def __init__(self, bucket, read, write, refresh_credentials):
        credentials = _RefreshingCredentials(refresh=lambda: refresh_credentials(bucket, read, write))
        session = _ProxySession(credentials, max_refresh_attempts=self.MAX_REFRESH_ATTEMPTS)
        super(_RefreshingClient, self).__init__(project=credentials.temporary_credentials.secret_key, _http=session)


class GsBucketOperations:

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
    def get_client(cls, *args, **kwargs):
        return _RefreshingClient(*args, refresh_credentials=GsTemporaryCredentials.from_environment,
                                 **kwargs)
