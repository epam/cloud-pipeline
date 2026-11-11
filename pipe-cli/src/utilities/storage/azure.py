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

import copy
import io
import os
from threading import Lock

import time
from datetime import timedelta, datetime

from src.model.datastorage_usage_model import StorageUsage
from src.utilities.audit import DataAccessEvent, DataAccessType
from src.utilities.encoding_utilities import to_string
from src.utilities.storage.storage_usage import StorageUsageAccumulator

from urllib.request import urlopen

from azure.core.credentials import AzureSasCredential, AzureNamedKeyCredential
from azure.core.pipeline.transport import RequestsTransport
from azure.storage.blob import (
    BlobServiceClient, BlobPrefix,
    generate_account_sas, AccountSasPermissions, ResourceTypes,
)

from src.api.data_storage import DataStorage
from src.model.data_storage_item_model import DataStorageItemModel, DataStorageItemLabelModel
from src.model.data_storage_tmp_credentials_model import TemporaryCredentialsModel
from src.utilities.patterns import PatternMatcher
from src.utilities.storage.common import StorageOperations, AbstractTransferManager, AbstractListingManager, \
    AbstractDeleteManager
from src.utilities.progress_bar import ProgressPercentage
from src.config import Config


class AzureProgressPercentage(ProgressPercentage):

    def __init__(self, filename, size):
        super(AzureProgressPercentage, self).__init__(filename, size)
        self._total_bytes = 0

    def __call__(self, bytes_amount):
        newest_bytes = bytes_amount - self._total_bytes
        self._total_bytes = bytes_amount
        super(AzureProgressPercentage, self).__call__(newest_bytes)

    @staticmethod
    def callback(source_key, size, quiet, lock=None):
        if not StorageOperations.show_progress(quiet, size, lock):
            return None
        progress = AzureProgressPercentage(source_key, size)
        return lambda current, _: progress(current)


class AzureManager:

    def __init__(self, blob_service, events=None):
        self.service = blob_service
        self.events = events

    def get_max_connections(self, io_threads):
        return max(io_threads, 1) if io_threads is not None else 2


class AzureListingManager(AzureManager, AbstractListingManager):
    DEFAULT_PAGE_SIZE = StorageOperations.DEFAULT_PAGE_SIZE

    def __init__(self, blob_service, bucket):
        super(AzureListingManager, self).__init__(blob_service)
        self.bucket = bucket
        self.delimiter = StorageOperations.PATH_SEPARATOR

    def list_items(self, relative_path=None, recursive=False, page_size=StorageOperations.DEFAULT_PAGE_SIZE,
                   show_all=False, show_archive=False):
        prefix = StorageOperations.get_prefix(relative_path)
        container_client = self.service.container_client(self.bucket.path)
        if recursive:
            blobs_iter = container_client.list_blobs(
                name_starts_with=prefix if relative_path else None
            )
        else:
            blobs_iter = container_client.walk_blobs(
                name_starts_with=prefix if relative_path else None,
                delimiter=StorageOperations.PATH_SEPARATOR
            )
        absolute_items = []
        for blob in blobs_iter:
            absolute_items.append(self._to_storage_item(blob))
            if not show_all and page_size and len(absolute_items) >= page_size:
                break
        return absolute_items if recursive else [self._to_local_item(item, prefix) for item in absolute_items]

    def list_paging_items(self, relative_path=None, recursive=False, page_size=StorageOperations.DEFAULT_PAGE_SIZE,
                          start_token=None, show_archive=False):
        return self.list_items(relative_path, recursive, page_size, show_all=False, show_archive=show_archive), None

    def get_summary(self, relative_path=None):
        prefix = StorageOperations.get_prefix(relative_path)
        container_client = self.service.container_client(self.bucket.path)
        blobs_iter = container_client.list_blobs(
            name_starts_with=prefix if relative_path else None
        )
        storage_usage = StorageUsage()
        for blob in blobs_iter:
            storage_usage.add_item(AbstractListingManager.STANDARD_TIER, blob.size)
        return [self.delimiter.join([self.bucket.path, relative_path]), storage_usage]

    def get_summary_with_depth(self, max_depth, relative_path=None):
        prefix = StorageOperations.get_prefix(relative_path)
        container_client = self.service.container_client(self.bucket.path)
        blobs_iter = container_client.list_blobs(
            name_starts_with=prefix if relative_path else None
        )
        accumulator = StorageUsageAccumulator(self.bucket.path, relative_path, self.delimiter, max_depth)
        for blob in blobs_iter:
            accumulator.add_path(blob.name, AbstractListingManager.STANDARD_TIER, blob.size)
        return accumulator.get_tree()

    def get_listing_with_depth(self, max_depth, relative_path=None):
        raise NotImplementedError("List items with depth is not implemented yet")

    def _to_storage_item(self, blob):
        item = DataStorageItemModel()
        item.name = blob.name
        item.path = item.name
        if isinstance(blob, BlobPrefix):
            item.type = 'Folder'
        else:
            item.type = 'File'
            item.changed = self._to_local_timezone(blob.last_modified)
            item.size = blob.size
            tier = str(blob.blob_tier).upper() if blob.blob_tier else ''
            item.labels = [DataStorageItemLabelModel('StorageClass', tier)]
        return item

    def _to_local_timezone(self, utc_datetime):
        return utc_datetime.astimezone(Config.instance().timezone())

    def _to_local_item(self, absolute_item, prefix):
        relative_item = copy.deepcopy(absolute_item)
        relative_item.name = StorageOperations.get_item_name(relative_item.name, prefix)
        relative_item.path = relative_item.name
        return relative_item

    def get_file_tags(self, relative_path):
        blob_client = self.service.blob_client(self.bucket.path, relative_path)
        return dict(blob_client.get_blob_properties().metadata or {})


class AzureDeleteManager(AzureManager, AbstractDeleteManager):

    def __init__(self, blob_service, events, bucket):
        super(AzureDeleteManager, self).__init__(blob_service, events)
        self.bucket = bucket
        self.delimiter = StorageOperations.PATH_SEPARATOR
        self.listing_manager = AzureListingManager(self.service, self.bucket)

    def delete_items(self, relative_path, recursive=False, exclude=[], include=[], version=None, hard_delete=False,
                     page_size=None):
        if version or hard_delete:
            raise RuntimeError('Versioning is not supported by AZURE cloud provider')
        prefix = StorageOperations.get_prefix(relative_path)
        check_file = True
        if prefix.endswith(self.delimiter):
            prefix = prefix[:-1]
            check_file = False
        if not recursive:
            self.__delete_blob(prefix, exclude, include)
        else:
            blob_names_for_deletion = []
            for item in self.listing_manager.list_items(prefix, recursive=True, show_all=True):
                if item.name == prefix and check_file:
                    blob_names_for_deletion = [item.name]
                    break
                if self.__file_under_folder(item.name, prefix):
                    blob_names_for_deletion.append(item.name)
            for blob_name in blob_names_for_deletion:
                self.__delete_blob(blob_name, exclude, include, prefix=prefix)

    def __file_under_folder(self, file_path, folder_path):
        return StorageOperations.without_prefix(file_path, folder_path).startswith(self.delimiter)

    def __delete_blob(self, blob_name, exclude, include, prefix=None):
        file_name = blob_name
        if prefix:
            relative_file_name = StorageOperations.get_item_name(blob_name, prefix=prefix + self.delimiter)
            file_name = StorageOperations.get_prefix(relative_file_name)
        if not PatternMatcher.match_any(file_name, include):
            return
        if PatternMatcher.match_any(file_name, exclude, default=False):
            return
        self.events.put(DataAccessEvent(blob_name, DataAccessType.DELETE, storage=self.bucket))
        self.service.blob_client(self.bucket.path, blob_name).delete_blob()


class TransferBetweenAzureBucketsManager(AzureManager, AbstractTransferManager):

    _COPY_SUCCESS_STATUS = 'success'
    _COPY_PENDING_STATUS = 'pending'
    _COPY_ABORTED_STATUS = 'aborted'
    _COPY_FAILED_STATUS = 'failed'
    _COPY_TERMINAL_STATUSES = [_COPY_SUCCESS_STATUS, _COPY_ABORTED_STATUS, _COPY_FAILED_STATUS]
    _SYNC_COPY_SIZE_LIMIT = (256 - 1) * 1024 * 1024  # 255 Mb
    _POLLS_TIMEOUT = 10  # 10 seconds
    _POLLS_LIMIT = 60 * 60 * 3  # 3 hours
    _POLLS_ATTEMPTS = _POLLS_LIMIT // _POLLS_TIMEOUT

    def get_destination_key(self, destination_wrapper, relative_path):
        return StorageOperations.normalize_path(destination_wrapper, relative_path)

    def get_destination_size(self, destination_wrapper, destination_key):
        return destination_wrapper.get_list_manager().get_file_size(destination_key)

    def get_source_key(self, source_wrapper, source_path):
        return source_path

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False,
                 quiet=False, size=None, tags=(), io_threads=None, lock=None):
        full_path = path
        destination_path = self.get_destination_key(destination_wrapper, relative_path)

        source_service = AzureBucketOperations.get_blob_service(source_wrapper.bucket, read=True, write=clean)
        source_sas = source_service.credentials.session_token.lstrip('?')
        source_account = source_service.credentials.secret_key
        source_blob_url = 'https://{}.blob.core.windows.net/{}/{}?{}'.format(
            source_account, source_wrapper.bucket.path, full_path, source_sas
        )
        destination_tags = self._destination_tags(source_wrapper, full_path, tags)
        destination_bucket = destination_wrapper.bucket.path
        progress_callback = AzureProgressPercentage.callback(full_path, size, quiet, lock)
        if progress_callback:
            progress_callback(0, size)
        self.events.put_all([DataAccessEvent(full_path, DataAccessType.READ, storage=source_wrapper.bucket),
                             DataAccessEvent(destination_path, DataAccessType.WRITE, storage=destination_wrapper.bucket)])
        dest_blob_client = self.service.blob_client(destination_bucket, destination_path)
        copy_props = dest_blob_client.start_copy_from_url(source_blob_url, metadata=destination_tags)
        if copy_props.get('copy_status') != self._COPY_SUCCESS_STATUS:
            self._wait_for_copying(destination_bucket, destination_path, full_path)
        if progress_callback:
            progress_callback(size, size)
        if clean:
            self.events.put(DataAccessEvent(full_path, DataAccessType.DELETE, storage=source_wrapper.bucket))
            source_service.blob_client(source_wrapper.bucket.path, full_path).delete_blob()

    def _wait_for_copying(self, destination_bucket, destination_path, full_path):
        for _ in range(0, TransferBetweenAzureBucketsManager._POLLS_ATTEMPTS):
            time.sleep(TransferBetweenAzureBucketsManager._POLLS_TIMEOUT)
            copying_status = self._get_copying_status(destination_bucket, destination_path)
            if copying_status in TransferBetweenAzureBucketsManager._COPY_TERMINAL_STATUSES:
                if copying_status == TransferBetweenAzureBucketsManager._COPY_SUCCESS_STATUS:
                    return
                else:
                    raise RuntimeError('Blob copying from %s to %s has failed.' % (full_path, destination_path))
        raise RuntimeError('Blob copying from %s to %s has failed.' % (full_path, destination_path))

    def _get_copying_status(self, destination_bucket, destination_path):
        blob_client = self.service.blob_client(destination_bucket, destination_path)
        props = blob_client.get_blob_properties()
        return props.copy.status if props.copy and props.copy.status else None

    def _destination_tags(self, source_wrapper, full_path, raw_tags):
        tags = StorageOperations.parse_tags(raw_tags) if raw_tags \
            else source_wrapper.get_list_manager().get_file_tags(full_path)
        tags.update(StorageOperations.source_tags(tags, full_path, source_wrapper))
        return tags


class AzureDownloadManager(AzureManager, AbstractTransferManager):

    def get_destination_key(self, destination_wrapper, relative_path):
        if destination_wrapper.path.endswith(os.path.sep):
            return os.path.join(destination_wrapper.path, relative_path)
        else:
            return destination_wrapper.path

    def get_destination_size(self, destination_wrapper, destination_key):
        return StorageOperations.get_local_file_size(destination_key)

    def get_source_key(self, source_wrapper, source_path):
        return source_path or source_wrapper.path

    def transfer(self, source_wrapper, destination_wrapper, path=None,
                 relative_path=None, clean=False, quiet=False, size=None, tags=None, io_threads=None, lock=None):
        source_key = self.get_source_key(source_wrapper, path)
        destination_key = self.get_destination_key(destination_wrapper, relative_path)

        self.create_local_folder(destination_key, lock)
        progress_callback = AzureProgressPercentage.callback(source_key, size, quiet, lock)
        self.events.put(DataAccessEvent(source_key, DataAccessType.READ, storage=source_wrapper.bucket))
        blob_client = self.service.blob_client(source_wrapper.bucket.path, source_key)
        downloader = blob_client.download_blob()
        total_read = 0
        with open(to_string(destination_key), 'wb') as f:
            for chunk in downloader.chunks():
                f.write(chunk)
                total_read += len(chunk)
                if progress_callback:
                    progress_callback(total_read, size)
        if clean:
            self.events.put(DataAccessEvent(source_key, DataAccessType.DELETE, storage=source_wrapper.bucket))
            blob_client.delete_blob()


class AzureUploadManager(AzureManager, AbstractTransferManager):

    def get_destination_key(self, destination_wrapper, relative_path):
        return StorageOperations.normalize_path(destination_wrapper, relative_path)

    def get_destination_size(self, destination_wrapper, destination_key):
        return destination_wrapper.get_list_manager().get_file_size(destination_key)

    def get_source_key(self, source_wrapper, source_path):
        if source_path:
            return os.path.join(source_wrapper.path, source_path)
        else:
            return source_wrapper.path

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), io_threads=None, lock=None):
        source_key = self.get_source_key(source_wrapper, path)
        destination_key = self.get_destination_key(destination_wrapper, relative_path)

        destination_tags = StorageOperations.generate_tags(tags, source_key)
        max_connections = self.get_max_connections(io_threads)
        self.events.put(DataAccessEvent(destination_key, DataAccessType.WRITE, storage=destination_wrapper.bucket))
        blob_client = self.service.blob_client(destination_wrapper.bucket.path, destination_key)
        with open(to_string(source_key), 'rb') as f:
            blob_client.upload_blob(f, overwrite=True, metadata=destination_tags,
                                    max_concurrency=max_connections)
        if clean:
            source_wrapper.delete_item(source_key)


class _SourceUrlIO(io.BytesIO):

    def __init__(self, url):
        super(_SourceUrlIO, self).__init__()
        self.io = urlopen(url)

    def read(self, n=10):
        return self.io.read(n)


class TransferFromHttpOrFtpToAzureManager(AzureManager, AbstractTransferManager):

    def get_destination_key(self, destination_wrapper, relative_path):
        if destination_wrapper.path.endswith(os.path.sep):
            return os.path.join(destination_wrapper.path, relative_path)
        else:
            return destination_wrapper.path

    def get_destination_size(self, destination_wrapper, destination_key):
        return destination_wrapper.get_list_manager().get_file_size(destination_key)

    def get_source_key(self, source_wrapper, source_path):
        return source_path or source_wrapper.path

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), io_threads=None, lock=None):
        if clean:
            raise AttributeError('Cannot perform \'mv\' operation due to deletion remote files '
                                 'is not supported for ftp/http sources.')

        source_key = self.get_source_key(source_wrapper, path)
        destination_key = self.get_destination_key(destination_wrapper, relative_path)

        destination_tags = StorageOperations.generate_tags(tags, source_key)
        max_connections = self.get_max_connections(io_threads)
        self.events.put(DataAccessEvent(destination_key, DataAccessType.WRITE, storage=destination_wrapper.bucket))
        blob_client = self.service.blob_client(destination_wrapper.bucket.path, destination_key)
        blob_client.upload_blob(_SourceUrlIO(source_key), overwrite=True, metadata=destination_tags,
                                max_concurrency=max_connections)


class AzureTemporaryCredentials:
    AZURE_STORAGE_ACCOUNT = 'AZURE_STORAGE_ACCOUNT'
    AZURE_STORAGE_KEY = 'AZURE_STORAGE_KEY'
    SAS_TOKEN = 'SAS_TOKEN'

    @classmethod
    def from_azure_sdk(cls, bucket, read, write):
        storage_account = os.environ[AzureTemporaryCredentials.AZURE_STORAGE_ACCOUNT]

        if AzureTemporaryCredentials.SAS_TOKEN not in os.environ:
            storage_account_key = os.environ[AzureTemporaryCredentials.AZURE_STORAGE_KEY]

            generation_date = datetime.utcnow()
            expiration_date = generation_date + timedelta(hours=1)

            print('SAS token generation date: %s' % generation_date)
            print('SAS token expiration date: %s' % expiration_date)

            sas_token = generate_account_sas(
                account_name=storage_account,
                account_key=storage_account_key,
                resource_types=ResourceTypes(service=True, container=True, object=True),
                permission=AccountSasPermissions(read=True, list=True),
                expiry=expiration_date,
                start=generation_date,
            )
        else:
            sas_token = os.environ[AzureTemporaryCredentials.SAS_TOKEN]
            expiration_date = datetime.utcnow() + timedelta(hours=1)

        credentials = TemporaryCredentialsModel()
        credentials.region = "eu-central-1"
        credentials.access_key_id = None
        credentials.secret_key = storage_account
        credentials.session_token = sas_token
        credentials.expiration = expiration_date
        return credentials

    @classmethod
    def from_cp_api(cls, bucket, read, write):
        return DataStorage.get_single_temporary_credentials(bucket=bucket.identifier, read=read, write=write)


class _RefreshingBlobServiceClient:
    """BlobServiceClient wrapper with automatic SAS token refresh and proxy support."""

    def __init__(self, bucket, read, write, refresh_timeout=15,
                 refresh_credentials=AzureTemporaryCredentials.from_cp_api):
        self._refresh_timeout = refresh_timeout
        self._refresh_credentials_fn = lambda: refresh_credentials(bucket, read, write)
        self.credentials = self._refresh_credentials_fn()
        self._lock = Lock()
        sas = self.credentials.session_token.lstrip('?')
        account_name = self.credentials.secret_key
        self._sas_credential = AzureSasCredential(sas)
        account_url = 'https://{}.blob.core.windows.net'.format(account_name)
        proxy_config = StorageOperations.get_proxy_config(account_url)
        transport = self._make_transport(proxy_config)
        kwargs = dict(transport=transport) if transport is not None else {}
        self._client = BlobServiceClient(account_url, credential=self._sas_credential, **kwargs)

    @staticmethod
    def _make_transport(proxy_config):
        if not proxy_config:
            return None
        if isinstance(proxy_config, dict):
            proxies = proxy_config
        else:
            proxies = {'http': proxy_config, 'https': proxy_config}
        return RequestsTransport(proxies=proxies)

    def _ensure_fresh(self):
        with self._lock:
            if self.credentials.expiration - datetime.utcnow() < timedelta(minutes=self._refresh_timeout):
                self.credentials = self._refresh_credentials_fn()
                self._sas_credential.update(self.credentials.session_token.lstrip('?'))

    def container_client(self, container):
        self._ensure_fresh()
        return self._client.get_container_client(container)

    def blob_client(self, container, blob):
        self._ensure_fresh()
        return self._client.get_blob_client(container, blob)


class AzureBucketOperations:

    @classmethod
    def get_transfer_between_buckets_manager(cls, source_wrapper, destination_wrapper, events, command):
        blob_service = cls.get_blob_service(destination_wrapper.bucket, read=True, write=True)
        return TransferBetweenAzureBucketsManager(blob_service, events)

    @classmethod
    def get_download_manager(cls, source_wrapper, destination_wrapper, events, command):
        blob_service = cls.get_blob_service(source_wrapper.bucket, read=True, write=command == 'mv')
        return AzureDownloadManager(blob_service, events)

    @classmethod
    def get_upload_manager(cls, source_wrapper, destination_wrapper, events, command):
        blob_service = cls.get_blob_service(destination_wrapper.bucket, read=True, write=True)
        return AzureUploadManager(blob_service, events)

    @classmethod
    def get_transfer_from_http_or_ftp_manager(cls, source_wrapper, destination_wrapper, events, command):
        blob_service = cls.get_blob_service(destination_wrapper.bucket, read=True, write=True)
        return TransferFromHttpOrFtpToAzureManager(blob_service, events)

    @classmethod
    def get_blob_service(cls, *args, **kwargs):
        return _RefreshingBlobServiceClient(*args, **kwargs)
