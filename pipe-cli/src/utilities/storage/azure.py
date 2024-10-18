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

from __future__ import absolute_import

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

try:
    from urllib.request import urlopen  # Python 3
except ImportError:
    from urllib2 import urlopen  # Python 2

from azure.storage.blob import BlockBlobService, ContainerPermissions, Blob
from azure.storage.common._auth import _StorageSASAuthentication

from src.api.data_storage import DataStorage
from src.model.data_storage_item_model import DataStorageItemModel, DataStorageItemLabelModel
from src.model.data_storage_tmp_credentials_model import TemporaryCredentialsModel
from src.utilities.patterns import PatternMatcher
from src.utilities.storage.common import StorageOperations, AbstractTransferManager, AbstractListingManager, \
    AbstractDeleteManager, UploadResult, TransferResult
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
        blobs_generator = self.service.list_blobs(self.bucket.path,
                                                  prefix=prefix if relative_path else None,
                                                  num_results=page_size if not show_all else None,
                                                  delimiter=StorageOperations.PATH_SEPARATOR if not recursive else None)
        absolute_items = [self._to_storage_item(blob) for blob in blobs_generator]
        return absolute_items if recursive else [self._to_local_item(item, prefix) for item in absolute_items]

    def list_paging_items(self, relative_path=None, recursive=False, page_size=StorageOperations.DEFAULT_PAGE_SIZE,
                          start_token=None, show_archive=False):
        return self.list_items(relative_path, recursive, page_size, show_all=False, show_archive=show_archive), None

    def get_summary(self, relative_path=None):
        prefix = StorageOperations.get_prefix(relative_path)
        blobs_generator = self.service.list_blobs(self.bucket.path,
                                                  prefix=prefix if relative_path else None)
        storage_usage = StorageUsage()
        for blob in blobs_generator:
            if type(blob) == Blob:
                storage_usage.add_item(AbstractListingManager.STANDARD_TIER, blob.properties.content_length)
        return [self.delimiter.join([self.bucket.path, relative_path]), storage_usage]

    def get_summary_with_depth(self, max_depth, relative_path=None):
        prefix = StorageOperations.get_prefix(relative_path)
        blobs_generator = self.service.list_blobs(self.bucket.path,
                                                  prefix=prefix if relative_path else None)
        accumulator = StorageUsageAccumulator(self.bucket.path, relative_path, self.delimiter, max_depth)
        for blob in blobs_generator:
            if type(blob) == Blob:
                size = blob.properties.content_length
                name = blob.name
                accumulator.add_path(name, AbstractListingManager.STANDARD_TIER, size)
        return accumulator.get_tree()

    def get_listing_with_depth(self, max_depth, relative_path=None):
        raise NotImplementedError("List items with depth is not implemented yet")

    def _to_storage_item(self, blob):
        item = DataStorageItemModel()
        item.name = blob.name
        item.path = item.name
        if type(blob) == Blob:
            item.type = 'File'
            item.changed = self._to_local_timezone(blob.properties.last_modified)
            item.size = blob.properties.content_length
            item.labels = [DataStorageItemLabelModel('StorageClass', blob.properties.blob_tier.upper())]
        else:
            item.type = 'Folder'
        return item

    def _to_local_timezone(self, utc_datetime):
        return utc_datetime.astimezone(Config.instance().timezone())

    def _to_local_item(self, absolute_item, prefix):
        relative_item = copy.deepcopy(absolute_item)
        relative_item.name = StorageOperations.get_item_name(relative_item.name, prefix)
        relative_item.path = relative_item.name
        return relative_item

    def get_file_tags(self, relative_path):
        return dict(self.service.get_blob_metadata(self.bucket.path, relative_path))


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
            deleted = self.__delete_blob(prefix, exclude, include)
            if deleted:
                self._delete_all_object_tags([prefix])
        else:
            blob_names_for_deletion = []
            for item in self.listing_manager.list_items(prefix, recursive=True, show_all=True):
                if item.name == prefix and check_file:
                    blob_names_for_deletion = [item.name]
                    break
                if self.__file_under_folder(item.name, prefix):
                    blob_names_for_deletion.append(item.name)
            deleted_blob_names = []
            for blob_name in blob_names_for_deletion:
                deleted = self.__delete_blob(blob_name, exclude, include, prefix=prefix)
                if deleted:
                    deleted_blob_names.append(blob_name)
            self._delete_all_object_tags(deleted_blob_names)

    def _delete_all_object_tags(self, blob_names_for_deletion, chunk_size=100):
        for blob_names_for_deletion_chunk in [blob_names_for_deletion[i:i + chunk_size]
                                              for i in range(0, len(blob_names_for_deletion), chunk_size)]:
            DataStorage.batch_delete_all_object_tags(self.bucket.identifier,
                                                     [{'path': blob_name}
                                                      for blob_name in blob_names_for_deletion_chunk])

    def __file_under_folder(self, file_path, folder_path):
        return StorageOperations.without_prefix(file_path, folder_path).startswith(self.delimiter)

    def __delete_blob(self, blob_name, exclude, include, prefix=None):
        file_name = blob_name
        if prefix:
            relative_file_name = StorageOperations.get_item_name(blob_name, prefix=prefix + self.delimiter)
            file_name = StorageOperations.get_prefix(relative_file_name)
        if not PatternMatcher.match_any(file_name, include):
            return False
        if PatternMatcher.match_any(file_name, exclude, default=False):
            return False
        self.events.put(DataAccessEvent(blob_name, DataAccessType.DELETE, storage=self.bucket))
        self.service.delete_blob(self.bucket.path, blob_name)
        return True


class TransferBetweenAzureBucketsManager(AzureManager, AbstractTransferManager):

    _COPY_SUCCESS_STATUS = 'success'
    _COPY_PENDING_STATUS = 'pending'
    _COPY_ABORTED_STATUS = 'aborted'
    _COPY_FAILED_STATUS = 'failed'
    _COPY_TERMINAL_STATUSES = [_COPY_SUCCESS_STATUS, _COPY_ABORTED_STATUS, _COPY_FAILED_STATUS]
    _SYNC_COPY_SIZE_LIMIT = (256 - 1) * 1024 * 1024  # 255 Mb
    _POLLS_TIMEOUT = 10  # 10 seconds
    _POLLS_LIMIT = 60 * 60 * 3  # 3 hours
    _POLLS_ATTEMPTS = _POLLS_LIMIT / _POLLS_TIMEOUT

    def get_destination_key(self, destination_wrapper, relative_path):
        return StorageOperations.normalize_path(destination_wrapper, relative_path)

    def get_destination_size(self, destination_wrapper, destination_key):
        return destination_wrapper.get_list_manager().get_file_size(destination_key)

    def get_destination_object_head(self, destination_wrapper, destination_key):
        return destination_wrapper.get_list_manager().get_file_object_head(destination_key)

    def get_source_key(self, source_wrapper, source_path):
        return source_path

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), io_threads=None, lock=None, checksum_algorithm='md5', checksum_skip=False):
        full_path = path
        destination_path = self.get_destination_key(destination_wrapper, relative_path)

        source_service = AzureBucketOperations.get_blob_service(source_wrapper.bucket, read=True, write=clean)
        source_credentials = source_service.credentials
        source_blob_url = self.service.make_blob_url(source_wrapper.bucket.path, full_path,
                                                     sas_token=source_credentials.session_token.lstrip('?'))
        destination_bucket = destination_wrapper.bucket.path
        sync_copy = size < TransferBetweenAzureBucketsManager._SYNC_COPY_SIZE_LIMIT
        if not size or size == 0:
            sync_copy = None
        progress_callback = AzureProgressPercentage.callback(full_path, size, quiet, lock)
        if progress_callback:
            progress_callback(0, size)
        self.events.put_all([DataAccessEvent(full_path, DataAccessType.READ, storage=source_wrapper.bucket),
                             DataAccessEvent(destination_path, DataAccessType.WRITE, storage=destination_wrapper.bucket)])
        self.service.copy_blob(destination_bucket, destination_path, source_blob_url,
                               requires_sync=sync_copy)
        if not sync_copy:
            self._wait_for_copying(destination_bucket, destination_path, full_path)
        if progress_callback:
            progress_callback(size, size)
        if clean:
            self.events.put(DataAccessEvent(full_path, DataAccessType.DELETE, storage=source_wrapper.bucket))
            source_service.delete_blob(source_wrapper.bucket.path, full_path)
        return TransferResult(source_key=full_path, destination_key=destination_path, destination_version=None,
                              tags=StorageOperations.parse_tags(tags))

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
        blob = self.service.get_blob_properties(destination_bucket, destination_path)
        return blob.properties.copy.status


class AzureDownloadManager(AzureManager, AbstractTransferManager):

    def get_destination_key(self, destination_wrapper, relative_path):
        if destination_wrapper.path.endswith(os.path.sep):
            return os.path.join(destination_wrapper.path, relative_path)
        else:
            return destination_wrapper.path

    def get_destination_size(self, destination_wrapper, destination_key):
        return StorageOperations.get_local_file_size(destination_key)

    def get_destination_object_head(self, destination_wrapper, destination_key):
        return StorageOperations.get_local_file_size(destination_key), \
            StorageOperations.get_local_file_modification_datetime(destination_key)

    def get_source_key(self, source_wrapper, source_path):
        return source_path or source_wrapper.path

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=None, io_threads=None, lock=None, checksum_algorithm='md5', checksum_skip=False):
        source_key = self.get_source_key(source_wrapper, path)
        destination_key = self.get_destination_key(destination_wrapper, relative_path)

        self.create_local_folder(destination_key, lock)
        progress_callback = AzureProgressPercentage.callback(source_key, size, quiet, lock)
        self.events.put(DataAccessEvent(source_key, DataAccessType.READ, storage=source_wrapper.bucket))
        self.service.get_blob_to_path(source_wrapper.bucket.path, source_key, to_string(destination_key),
                                      progress_callback=progress_callback)
        if clean:
            self.events.put(DataAccessEvent(source_key, DataAccessType.DELETE, storage=source_wrapper.bucket))
            self.service.delete_blob(source_wrapper.bucket.path, source_key)


class AzureUploadManager(AzureManager, AbstractTransferManager):

    def get_destination_key(self, destination_wrapper, relative_path):
        return StorageOperations.normalize_path(destination_wrapper, relative_path)

    def get_destination_size(self, destination_wrapper, destination_key):
        return destination_wrapper.get_list_manager().get_file_size(destination_key)

    def get_destination_object_head(self, destination_wrapper, destination_key):
        return destination_wrapper.get_list_manager().get_file_object_head(destination_key)

    def get_source_key(self, source_wrapper, source_path):
        if source_path:
            return os.path.join(source_wrapper.path, source_path)
        else:
            return source_wrapper.path

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), io_threads=None, lock=None, checksum_algorithm='md5', checksum_skip=False):
        source_key = self.get_source_key(source_wrapper, path)
        destination_key = self.get_destination_key(destination_wrapper, relative_path)

        destination_tags = StorageOperations.generate_tags(tags, source_key)
        progress_callback = AzureProgressPercentage.callback(relative_path, size, quiet, lock)
        max_connections = self.get_max_connections(io_threads)
        self.events.put(DataAccessEvent(destination_key, DataAccessType.WRITE, storage=destination_wrapper.bucket))
        self.service.create_blob_from_path(destination_wrapper.bucket.path, destination_key, to_string(source_key),
                                           metadata=destination_tags,
                                           progress_callback=progress_callback,
                                           max_connections=max_connections)
        if clean:
            source_wrapper.delete_item(source_key)
        return UploadResult(source_key=source_key, destination_key=destination_key, destination_version=None,
                            tags=destination_tags)


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

    def get_destination_object_head(self, destination_wrapper, destination_key):
        return destination_wrapper.get_list_manager().get_file_object_head(destination_key)

    def get_source_key(self, source_wrapper, source_path):
        return source_path or source_wrapper.path

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), io_threads=None, lock=None, checksum_algorithm='md5', checksum_skip=False):
        if clean:
            raise AttributeError('Cannot perform \'mv\' operation due to deletion remote files '
                                 'is not supported for ftp/http sources.')

        source_key = self.get_source_key(source_wrapper, path)
        destination_key = self.get_destination_key(destination_wrapper, relative_path)

        destination_tags = StorageOperations.generate_tags(tags, source_key)
        progress_callback = AzureProgressPercentage.callback(relative_path, size, quiet, lock)
        max_connections = self.get_max_connections(io_threads)
        self.events.put(DataAccessEvent(destination_key, DataAccessType.WRITE, storage=destination_wrapper.bucket))
        self.service.create_blob_from_stream(destination_wrapper.bucket.path, destination_key, _SourceUrlIO(source_key),
                                             metadata=destination_tags,
                                             progress_callback=progress_callback,
                                             max_connections=max_connections)
        return UploadResult(source_key=source_key, destination_key=destination_key, destination_version=None,
                            tags=destination_tags)


class AzureTemporaryCredentials:
    AZURE_STORAGE_ACCOUNT = 'AZURE_STORAGE_ACCOUNT'
    AZURE_STORAGE_KEY = 'AZURE_STORAGE_KEY'
    SAS_TOKEN = 'SAS_TOKEN'

    @classmethod
    def from_azure_sdk(cls, bucket, read, write):
        storage_account = os.environ[AzureTemporaryCredentials.AZURE_STORAGE_ACCOUNT]

        if AzureTemporaryCredentials.SAS_TOKEN not in os.environ:
            storage_account_key = os.environ[AzureTemporaryCredentials.AZURE_STORAGE_KEY]

            client = BlockBlobService(account_name=storage_account, account_key=storage_account_key)

            generation_date = datetime.utcnow()
            expiration_date = generation_date + timedelta(hours=1)

            print('SAS token generation date: %s' % generation_date)
            print('SAS token expiration date: %s' % expiration_date)

            permission = ContainerPermissions(True, False, False, True)
            sas_token = client.generate_account_shared_access_signature('sco',
                                                                        permission=permission,
                                                                        expiry=expiration_date,
                                                                        start=generation_date)
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


class RefreshingBlockBlobService(BlockBlobService):

    def __init__(self, bucket, read, write, refresh_timeout=15,
                 refresh_credentials=AzureTemporaryCredentials.from_cp_api):
        self.refresh_timeout = refresh_timeout
        self.refresh_credentials = lambda: refresh_credentials(bucket, read, write)
        self.credentials = self.refresh_credentials()
        self.refresh_credentials_lock = Lock()
        super(RefreshingBlockBlobService, self).__init__(account_name=self.credentials.secret_key,
                                                         sas_token=self.credentials.session_token)

    def _perform_request(self, request, parser=None, parser_args=None, operation_context=None, expected_errors=None):
        with self.refresh_credentials_lock:
            if self._expired(self.credentials):
                self.credentials = self.refresh_credentials()

                self.sas_token = self.credentials.session_token
                self.authentication = _StorageSASAuthentication(self.credentials.session_token)
        return super(RefreshingBlockBlobService, self)._perform_request(request, parser, parser_args, operation_context,
                                                                        expected_errors)

    def _expired(self, credentials):
        return credentials.expiration - datetime.utcnow() < timedelta(minutes=self.refresh_timeout)


class ProxyBlockBlobService(RefreshingBlockBlobService):

    def _apply_host(self, request, operation_context, retry_context):
        super(ProxyBlockBlobService, self)._apply_host(request, operation_context, retry_context)

        request_url = self.protocol + '://' + request.host
        self._httpclient.proxies = StorageOperations.get_proxy_config(request_url)


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
        return ProxyBlockBlobService(*args, **kwargs)
