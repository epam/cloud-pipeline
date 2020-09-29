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
import socket
from abc import abstractmethod, ABCMeta
from datetime import datetime, timedelta

from requests import RequestException
from requests.adapters import HTTPAdapter
from s3transfer import TransferConfig, MultipartUploader, OSUtils, MultipartDownloader
from urllib3.connection import VerifiedHTTPSConnection

try:
    import http.client as http_client  # Python 3
    from http import HTTPStatus  # Python 3
    OK = HTTPStatus.OK
except ImportError:
    import httplib as http_client  # Python 2
    from httplib import OK  # Python 2

from src.utilities.storage.storage_usage import StorageUsageAccumulator

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
from google.cloud.storage.blob import _get_encryption_headers, _raise_from_invalid_response
from google.oauth2.credentials import Credentials
from google import resumable_media
from google.resumable_media import DataCorruption
from google.resumable_media.requests import Download

from src.api.data_storage import DataStorage
from src.config import Config
from src.model.data_storage_item_model import DataStorageItemModel, DataStorageItemLabelModel
from src.model.data_storage_tmp_credentials_model import TemporaryCredentialsModel
from src.utilities.patterns import PatternMatcher
from src.utilities.progress_bar import ProgressPercentage
from src.utilities.storage.common import AbstractRestoreManager, AbstractListingManager, StorageOperations, \
    AbstractDeleteManager, AbstractTransferManager


KB = 1024
MB = KB * KB
CP_CLI_DOWNLOAD_BUFFERING_SIZE = 'CP_CLI_DOWNLOAD_BUFFERING_SIZE'
CP_CLI_RESUMABLE_DOWNLOAD_ATTEMPTS = 'CP_CLI_RESUMABLE_DOWNLOAD_ATTEMPTS'
CP_CLI_GCP_MULTIPART_THRESHOLD = 'CP_CLI_GCP_MULTIPART_THRESHOLD'
CP_CLI_GCP_MULTIPART_CHUNKSIZE = 'CP_CLI_GCP_MULTIPART_CHUNKSIZE'
CP_CLI_GCP_MAX_CONCURRENCY = 'CP_CLI_GCP_MAX_CONCURRENCY'


class GsProgressPercentage(ProgressPercentage):

    def __init__(self, filename, size):
        super(GsProgressPercentage, self).__init__(filename, size)
        self._total_bytes = 0

    def __call__(self, bytes_amount):
        newest_bytes = bytes_amount - self._total_bytes
        self._total_bytes = bytes_amount
        super(GsProgressPercentage, self).__call__(newest_bytes)

    @staticmethod
    def callback(source_key, size, quiet, lock=None):
        if not StorageOperations.show_progress(quiet, size, lock):
            return None
        progress = GsProgressPercentage(source_key, size)
        return lambda current: progress(current)


class S3TransferUploadClient:
    __metaclass__ = ABCMeta

    @abstractmethod
    def create_multipart_upload(self, Bucket, Key, *args, **kwargs):
        pass

    @abstractmethod
    def abort_multipart_upload(self, Bucket, Key, *args, **kwargs):
        pass

    @abstractmethod
    def complete_multipart_upload(self, Bucket, Key, UploadId, MultipartUpload, *args, **kwargs):
        pass

    @abstractmethod
    def upload_part(self, Bucket, Key, UploadId, PartNumber, Body, *args, **kwargs):
        pass


class S3TransferDownloadClient:
    __metaclass__ = ABCMeta

    @abstractmethod
    def get_object(self, Bucket, Key, Range, *args, **kwargs):
        pass


class GsCompositeUploadClient(S3TransferUploadClient):

    def __init__(self, bucket, path, metadata, client, progress_callback):
        self._bucket = bucket
        self._path = path
        self._metadata = metadata
        self._gcp = client
        self._bucket_object = self._gcp.bucket(self._bucket)
        self._blob_object = self._bucket_object.blob(self._path)
        self._parts = {}
        self._max_composite_parts = 32
        self._progress_callback = progress_callback

    def create_multipart_upload(self, Bucket, Key, *args, **kwargs):
        return {'UploadId': self._path}

    def upload_part(self, Bucket, Key, UploadId, PartNumber, Body, *args, **kwargs):
        part_path = self._part_path(PartNumber)
        part_blob = self._bucket_object.blob(part_path)
        part_blob.upload_from_file(Body)
        if self._progress_callback:
            self._progress_callback(part_blob.size)
        self._parts[PartNumber] = part_blob
        return {'ETag': part_path}

    def complete_multipart_upload(self, Bucket, Key, UploadId, MultipartUpload, *args, **kwargs):
        remaining_parts = list(self._parts[part_number] for part_number in sorted(self._parts.keys()))
        composed_part = None
        last_part_number = 0
        while remaining_parts:
            if composed_part:
                merging_parts = remaining_parts[:self._max_composite_parts - 1]
                last_part_number += len(merging_parts)
                composed_part = self._merge([composed_part] + merging_parts, last_part_number)
                remaining_parts = remaining_parts[self._max_composite_parts - 1:]
            else:
                merging_parts = remaining_parts[:self._max_composite_parts]
                last_part_number += len(merging_parts)
                composed_part = self._merge(merging_parts, last_part_number)
                remaining_parts = remaining_parts[self._max_composite_parts:]
        source_blob = self._bucket_object.blob(composed_part.name)
        self._bucket_object.copy_blob(source_blob, self._bucket_object, self._path)
        source_blob.delete()

    def _merge(self, parts, last_part_number):
        merged_part_path = self._merged_part_path(last_part_number)
        merged_blob = self._bucket_object.blob(merged_part_path)
        merged_blob.metadata = self._metadata
        merged_blob.compose(parts)
        for part_blob in parts:
            part_blob.delete()
        return merged_blob

    def _part_path(self, part_number):
        return '%s_%s.tmp/%d' % (self._path, self._hashed_path(), part_number)

    def _merged_part_path(self, last_part_number):
        return '%s_%s.tmp/1:%d' % (self._path, self._hashed_path(), last_part_number)

    def _hashed_path(self):
        return str(abs(hash(self._path)))

    def abort_multipart_upload(self, Bucket, Key, *args, **kwargs):
        for part_number in sorted(self._parts.keys()):
            blob = self._parts[part_number]
            if blob.exists():
                blob.delete()


class GsRangeDownloadClient(S3TransferDownloadClient):

    def __init__(self, bucket, path, client, blob_object):
        self._bucket = bucket
        self._path = path
        self._gcp = client
        self._blob_object = blob_object if blob_object else self._blob_object

    def get_object(self, Bucket, Key, Range, *args, **kwargs):
        start, end = Range.split('=')[1].split('-')
        start = int(start)
        end = int(end) if end else None
        return {'Body': self._blob_object.get_content_stream(start=start, end=end)}


class _OutputStreamMixin(object):

    def __init__(self, stream):
        self._stream = stream
        self._bytes_transferred = 0

    @property
    def bytes_transferred(self):
        return self._stream.bytes_transferred if hasattr(self._stream, 'bytes_transferred') else self._bytes_transferred


class _ProgressOutputStream(_OutputStreamMixin):

    def __init__(self, stream, progress_callback, progress_chunk_size):
        """
        Output stream wrapper that updates writing progress.

        Progress callbacks are time-consuming actions. To improve overall performance callbacks have to be called
        only a limited number of times. The amount of transferred data to cause a new progress callback is specified
        in progress_chunk_size parameter.

        :param stream: Wrapping stream.
        :param progress_callback: Progress callback.
        :param progress_chunk_size: Required amount of transferred data from the previous progress callback
         to cause a new callback.
        """
        super(_ProgressOutputStream, self).__init__(stream)
        self._progress_callback = progress_callback
        self._progress_chunk_size = progress_chunk_size
        self._progress_chunk = 0

    def write(self, data):
        self._stream.write(data)
        self._bytes_transferred += len(data)
        current_chunk = self._bytes_transferred / self._progress_chunk_size
        if current_chunk > self._progress_chunk:
            self._progress_chunk = current_chunk
            if self._progress_callback:
                self._progress_callback(self._bytes_transferred)


def _do_nothing(*args, **kwargs):
    pass


class _IterReader:

    def __init__(self, iter):
        self._iter = iter

    def read(self, *args, **kwargs):
        return self._iter.next()


class _StreamingDownloadMixin(Blob):
    """
    Blob download mixin that allows to generate blob content streams.
    """

    def get_content_stream(self, start=None, end=None):
        download_url = self._get_download_url()
        headers = _get_encryption_headers(self._encryption_key)
        headers["accept-encoding"] = "gzip"

        transport = self._get_transport(None)
        try:
            download = Download(download_url, stream=42, headers=headers, start=start, end=end)
            download._write_to_stream = _do_nothing
            response = download.consume(transport)
            body_iter = response.iter_content(chunk_size=resumable_media.requests.download._SINGLE_GET_CHUNK_SIZE,
                                              decode_unicode=False)
            return _IterReader(body_iter)
        except resumable_media.InvalidResponse as exc:
            _raise_from_invalid_response(exc)


class _ResumableDownloadProgressMixin(Blob):
    """
    Blob download mixin that resumes a download in case of any low-level error and
    provides support for downloading progress callbacks.
    """

    def _do_download(self, transport, file_obj, download_url, headers, start=None, end=None):
        stream = _ProgressOutputStream(file_obj, progress_callback=self._progress_callback,
                                       progress_chunk_size=self._progress_chunk_size)
        remaining_attempts = self._attempts
        while stream.bytes_transferred < self._size and remaining_attempts:
            try:
                download = Download(
                    download_url, stream=stream, headers=headers,
                    start=stream.bytes_transferred, end=end
                )
                download.consume(transport)
            except RequestException as e:
                remaining_attempts -= 1
                if not remaining_attempts:
                    raise RuntimeError('Resumable download has failed after %s sequential resumes. '
                                       'You can alter the number of allowed resumes using %s environment variable. '
                                       'Original error: %s.'
                                       % (self._attempts, CP_CLI_RESUMABLE_DOWNLOAD_ATTEMPTS, str(e)))
        self.reload()


class _UploadProgressMixin(Blob):
    """
    Blob upload mixin that provides support for uploading progress callbacks.
    """

    def _do_resumable_upload(self, client, stream, content_type, size, num_retries, predefined_acl):
        upload, transport = self._initiate_resumable_upload(
            client,
            stream,
            content_type,
            size,
            num_retries,
            predefined_acl=predefined_acl,
            chunk_size=self.chunk_size
        )

        response = None
        while not upload.finished:
            if self._progress_callback:
                self._progress_callback(upload._bytes_uploaded)
            response = upload.transmit_next_chunk(transport)

        return response


class _CustomBlob(_StreamingDownloadMixin, _ResumableDownloadProgressMixin, _UploadProgressMixin, Blob):
    PROGRESS_CHUNK_SIZE = 5 * 1024 * 1024  # 5 MB
    DEFAULT_RESUME_ATTEMPTS = 100

    def __init__(self, size, progress_callback, attempts=DEFAULT_RESUME_ATTEMPTS, *args, **kwargs):
        """
        Custom blob that supports uploading / downloading progress callbacks, implements resumable download strategy
        and allows blob content streaming using several blob mixins.

        :param size: Total size of the uploading / downloading blob.
        :param attempts: Maximum number of download sequential resumes before failing. Defaults to
        DEFAULT_RESUME_ATTEMPTS and can be overridden with CP_CLI_RESUMABLE_DOWNLOAD_ATTEMPTS environment variable.
        """
        self._size = size
        self._progress_callback = progress_callback
        self._attempts = int(os.environ.get(CP_CLI_RESUMABLE_DOWNLOAD_ATTEMPTS) or attempts)
        self._progress_chunk_size = _CustomBlob.PROGRESS_CHUNK_SIZE
        super(_CustomBlob, self).__init__(*args, **kwargs)


class GsManager:

    def __init__(self, client):
        self.client = client

    def custom_blob(self, bucket, blob_name, progress_callback, size):
        return _CustomBlob(
            size=size,
            progress_callback=progress_callback,
            name=blob_name,
            bucket=bucket
        )


class GsListingManager(GsManager, AbstractListingManager):

    def __init__(self, client, bucket, show_versions=False):
        super(GsListingManager, self).__init__(client)
        self.bucket = bucket
        self.show_versions = show_versions

    def list_items(self, relative_path=None, recursive=False, page_size=StorageOperations.DEFAULT_PAGE_SIZE,
                   show_all=False):
        prefix = StorageOperations.get_prefix(relative_path)
        bucket = self.client.bucket(self.bucket.path)
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

    def get_summary(self, relative_path=None):
        prefix = StorageOperations.get_prefix(relative_path)
        bucket = self.client.bucket(self.bucket.path)
        blobs_iterator = bucket.list_blobs(prefix=prefix if relative_path else None)

        size = 0
        count = 0
        for blob in blobs_iterator:
            size += blob.size
            count += 1
        return [StorageOperations.PATH_SEPARATOR.join([self.bucket.path, relative_path]), count, size]

    def get_summary_with_depth(self, max_depth, relative_path=None):
        prefix = StorageOperations.get_prefix(relative_path)
        bucket = self.client.bucket(self.bucket.path)
        blobs_iterator = bucket.list_blobs(prefix=prefix if relative_path else None)

        accumulator = StorageUsageAccumulator(self.bucket.path, relative_path, StorageOperations.PATH_SEPARATOR,
                                              max_depth)
        for blob in blobs_iterator:
            size = blob.size
            name = blob.name
            accumulator.add_path(name, size)
        return accumulator.get_tree()

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
        bucket = self.client.bucket(self.bucket.path)
        blob = bucket.blob(relative_path)
        blob.reload()
        return blob.metadata or {}


class GsDeleteManager(GsManager, AbstractDeleteManager):

    def __init__(self, client, bucket):
        super(GsDeleteManager, self).__init__(client)
        self.bucket = bucket
        self.delimiter = StorageOperations.PATH_SEPARATOR

    def delete_items(self, relative_path, recursive=False, exclude=[], include=[], version=None, hard_delete=False):
        if recursive and version:
            raise RuntimeError('Recursive folder deletion with specified version is not available '
                               'for GCP cloud provider.')
        prefix = StorageOperations.get_prefix(relative_path)
        check_file = True
        if prefix.endswith(self.delimiter):
            prefix = prefix[:-1]
            check_file = False
        bucket = self.client.bucket(self.bucket.path)
        if not recursive and not hard_delete:
            self._delete_blob(bucket.blob(prefix, generation=version), exclude, include)
        else:
            blobs_for_deletion = []
            listing_manager = self._get_listing_manager(show_versions=version is not None or hard_delete)
            for item in listing_manager.list_items(prefix, recursive=True, show_all=True):
                if item.name == prefix and check_file:
                    if version:
                        matching_item_versions = [item_version for item_version in item.versions
                                                  if item_version.version == version]
                        if matching_item_versions:
                            blobs_for_deletion = [bucket.blob(item.name, generation=matching_item_versions[0].version)]
                    else:
                        blobs_for_deletion.extend(self._item_blobs_for_deletion(bucket, item, hard_delete))
                    break
                if self._file_under_folder(item.name, prefix):
                    blobs_for_deletion.extend(self._item_blobs_for_deletion(bucket, item, hard_delete))
            for blob in blobs_for_deletion:
                self._delete_blob(blob, exclude, include, prefix)

    def _item_blobs_for_deletion(self, bucket, item, hard_delete):
        if hard_delete:
            return [bucket.blob(item.name, generation=item_version.version) for item_version in item.versions]
        else:
            return [bucket.blob(item.name)]

    def _delete_blob(self, blob, exclude, include, prefix=None):
        if self._is_matching_delete_filters(blob.name, exclude, include, prefix):
            blob.delete()

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

    def _get_listing_manager(self, show_versions):
        client = self.client
        if show_versions:
            client = GsBucketOperations.get_client(self.bucket, read=True, write=True, versioning=True)
        return GsListingManager(client, self.bucket, show_versions=show_versions)


class GsRestoreManager(GsManager, AbstractRestoreManager):

    def __init__(self, client, wrapper):
        super(GsRestoreManager, self).__init__(client)
        self.wrapper = wrapper
        self.listing_manager = GsListingManager(self.client, self.wrapper.bucket, show_versions=True)

    def restore_version(self, version, exclude, include, recursive):
        bucket = self.client.bucket(self.wrapper.bucket.path)
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
                 size=None, tags=(), skip_existing=False, lock=None):
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
        source_client = GsBucketOperations.get_client(source_wrapper.bucket, read=True, write=clean)
        source_bucket = source_client.bucket(source_wrapper.bucket.path)
        source_blob = source_bucket.blob(full_path)
        destination_bucket = self.client.bucket(destination_wrapper.bucket.path)
        source_bucket.copy_blob(source_blob, destination_bucket, destination_path, client=self.client)
        destination_blob = destination_bucket.blob(destination_path)
        destination_blob.metadata = self._destination_tags(source_wrapper, full_path, tags)
        destination_blob.patch()
        # Transfer between buckets in GCP is almost an instant operation. Therefore the progress bar can be updated
        # only once.
        progress_callback = GsProgressPercentage.callback(full_path, size, quiet, lock)
        if progress_callback is not None:
            progress_callback(size)
        if clean:
            source_blob.delete()

    def _destination_tags(self, source_wrapper, full_path, raw_tags):
        tags = StorageOperations.parse_tags(raw_tags) if raw_tags \
            else source_wrapper.get_list_manager().get_file_tags(full_path)
        tags.update(StorageOperations.source_tags(tags, full_path, source_wrapper))
        return tags


class GsDownloadManager(GsManager, AbstractTransferManager):
    DEFAULT_BUFFERING_SIZE = 1024 * 1024  # 1MB

    def __init__(self, client, buffering=DEFAULT_BUFFERING_SIZE):
        """
        Google cloud storage download manager that performs either resumable downloading or
        parallel downloading depending on file size.

        If file size is less than 200 MB then resumable downloading will be performed.
        Otherwise parallel downloading will be performed.

        Resumable downloading uses custom buffering size for destination files.
        See the corresponding issue for more information on why the buffering size should be altered:
         https://github.com/epam/cloud-pipeline/issues/435.

        Parallel downloading threshold size can be configured via CP_CLI_GCP_MULTIPART_THRESHOLD environment variable,
                             chunk size can be configured via CP_CLI_GCP_MULTIPART_CHUNKSIZE environment variable
                             and number of threads can be configured via CP_CLI_GCP_MAX_CONCURRENCY environment variable.

        :param client: Google cloud storage client.
        :param buffering: Buffering size for file system flushing. Defaults to DEFAULT_BUFFERING_SIZE and
        can be overridden with CP_CLI_DOWNLOAD_BUFFERING_SIZE environment variable.
        """
        GsManager.__init__(self, client)
        self._buffering = int(os.environ.get(CP_CLI_DOWNLOAD_BUFFERING_SIZE) or buffering)

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), skip_existing=False, lock=None):
        if path:
            source_key = path
        else:
            source_key = source_wrapper.path
        if destination_wrapper.path.endswith(os.path.sep):
            destination_key = os.path.join(destination_wrapper.path, relative_path)
        else:
            destination_key = destination_wrapper.path
        if source_key.endswith(StorageOperations.PATH_SEPARATOR):
            return
        if skip_existing:
            remote_size = source_wrapper.get_list_manager().get_file_size(source_key)
            local_size = StorageOperations.get_local_file_size(destination_key)
            if local_size is not None and remote_size == local_size:
                if not quiet:
                    click.echo('Skipping file %s since it exists in the destination %s' % (source_key, destination_key))
                return
        self.create_local_folder(destination_key, lock)
        self._replace_default_download_chunk_size(self._buffering)
        transfer_config = self._get_download_config()
        if size > transfer_config.multipart_threshold:
            if StorageOperations.show_progress(quiet, size, lock):
                progress_callback = ProgressPercentage(source_key, size)
            else:
                progress_callback = None
            bucket = self.client.bucket(source_wrapper.bucket.path)
            blob = self.custom_blob(bucket, source_key, None, size)
            download_client = GsRangeDownloadClient(source_wrapper.bucket.path, destination_key, self.client,
                                                    blob_object=blob)
            downloader = MultipartDownloader(client=download_client, config=transfer_config, osutil=OSUtils())
            downloader.download_file(bucket=source_wrapper.bucket.path, key=source_key, filename=destination_key,
                                     object_size=size, extra_args={},
                                     callback=progress_callback)
        else:
            progress_callback = GsProgressPercentage.callback(source_key, size, quiet, lock)
            bucket = self.client.bucket(source_wrapper.bucket.path)
            if StorageOperations.file_is_empty(size):
                blob = bucket.blob(source_key)
            else:
                blob = self.custom_blob(bucket, source_key, progress_callback, size)
            self._download_to_file(blob, destination_key)
            if progress_callback is not None:
                progress_callback(size)
        if clean:
            blob.delete()

    def _get_download_config(self):
        transfer_config = TransferConfig(multipart_threshold=200 * MB,
                                         multipart_chunksize=200 * MB)
        if os.getenv(CP_CLI_GCP_MULTIPART_THRESHOLD):
            transfer_config.multipart_threshold = int(os.getenv(CP_CLI_GCP_MULTIPART_THRESHOLD))
        if os.getenv(CP_CLI_GCP_MULTIPART_CHUNKSIZE):
            transfer_config.multipart_chunksize = int(os.getenv(CP_CLI_GCP_MULTIPART_CHUNKSIZE))
        if os.getenv(CP_CLI_GCP_MAX_CONCURRENCY):
            transfer_config.max_concurrency = int(os.getenv(CP_CLI_GCP_MAX_CONCURRENCY))
        return transfer_config

    def _download_to_file(self, blob, destination_key):
        try:
            with open(destination_key, "wb", buffering=self._buffering) as file_obj:
                blob.download_to_file(file_obj)
        except DataCorruption:
            os.remove(destination_key)
            raise

    def _replace_default_download_chunk_size(self, chunk_size):
        resumable_media.requests.download._SINGLE_GET_CHUNK_SIZE = chunk_size


class GsUploadManager(GsManager, AbstractTransferManager):
    """
    Google cloud storage upload manager that performs either simple upload or
    parallel composite upload depending on file size.

    If file size is less than 150 MB then simple uploading will be performed.
    Otherwise parallel composite uploading will be performed.

    Parallel composite uploading threshold size can be configured via CP_CLI_GCP_MULTIPART_THRESHOLD environment variable,
                                 chunk size can be configured via CP_CLI_GCP_MULTIPART_CHUNKSIZE environment variable
                                 and number of threads can be configured via CP_CLI_GCP_MAX_CONCURRENCY environment variable.
    """

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False, quiet=False,
                 size=None, tags=(), skip_existing=False, lock=None):
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
        transfer_config = self._get_upload_config(size)
        if size > transfer_config.multipart_threshold:
            if StorageOperations.show_progress(quiet, size, lock):
                progress_callback = ProgressPercentage(relative_path, size)
            else:
                progress_callback = None
            upload_client = GsCompositeUploadClient(destination_wrapper.bucket.path, destination_key,
                                                    StorageOperations.generate_tags(tags, source_key),
                                                    self.client, progress_callback)
            uploader = MultipartUploader(client=upload_client, config=transfer_config, osutil=OSUtils())
            uploader.upload_file(filename=source_key, bucket=destination_wrapper.bucket.path, key=destination_key,
                                 callback=None, extra_args={})
        else:
            progress_callback = GsProgressPercentage.callback(relative_path, size, quiet, lock)
            bucket = self.client.bucket(destination_wrapper.bucket.path)
            blob = self.custom_blob(bucket, destination_key, progress_callback, size)
            blob.metadata = StorageOperations.generate_tags(tags, source_key)
            blob.upload_from_filename(source_key)
            if progress_callback is not None:
                progress_callback(size)
        if clean:
            source_wrapper.delete_item(source_key)

    def _get_upload_config(self, size):
        multipart_threshold, multipart_chunksize = self._get_adjusted_parameters(size,
                                                                                 multipart_threshold=150 * MB,
                                                                                 max_parts=32)
        transfer_config = TransferConfig(multipart_threshold=multipart_threshold,
                                         multipart_chunksize=multipart_chunksize)
        if os.getenv(CP_CLI_GCP_MULTIPART_THRESHOLD):
            transfer_config.multipart_threshold = int(os.getenv(CP_CLI_GCP_MULTIPART_THRESHOLD))
        if os.getenv(CP_CLI_GCP_MULTIPART_CHUNKSIZE):
            transfer_config.multipart_chunksize = int(os.getenv(CP_CLI_GCP_MULTIPART_CHUNKSIZE))
        if os.getenv(CP_CLI_GCP_MAX_CONCURRENCY):
            transfer_config.max_concurrency = int(os.getenv(CP_CLI_GCP_MAX_CONCURRENCY))
        return transfer_config

    def _get_adjusted_parameters(self, size, multipart_threshold, max_parts):
        chunks_number = max(min(self._safely_divide(size, multipart_threshold), max_parts), 2)
        multipart_chunksize = self._safely_divide(size, chunks_number)
        return multipart_threshold, multipart_chunksize

    def _safely_divide(self, a, b):
        return a // b + 1 if a % b != 0 else a // b


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
                 size=None, tags=(), skip_existing=False, lock=None):
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
        progress_callback = GsProgressPercentage.callback(relative_path, size, quiet, lock)
        bucket = self.client.bucket(destination_wrapper.bucket.path)
        if StorageOperations.file_is_empty(size):
            blob = bucket.blob(destination_key)
        else:
            blob = self.custom_blob(bucket, destination_key, progress_callback, size)
        blob.metadata = StorageOperations.generate_tags(tags, source_key)
        blob.upload_from_file(_SourceUrlIO(urlopen(source_key)))
        if progress_callback is not None:
            progress_callback(blob.size)


class GsTemporaryCredentials:
    GS_PROJECT = 'GS_PROJECT'
    GS_STS_PROJECT = 'GS_STS_TOKEN'

    @classmethod
    def from_environment(cls, bucket, read, write, versioning):
        credentials = TemporaryCredentialsModel()
        credentials.secret_key = os.getenv(GsTemporaryCredentials.GS_PROJECT)
        credentials.session_token = os.getenv(GsTemporaryCredentials.GS_STS_PROJECT)
        credentials.expiration = datetime.utcnow() + timedelta(hours=1)
        return credentials

    @classmethod
    def from_cp_api(cls, bucket, read, write, versioning):
        return DataStorage.get_single_temporary_credentials(bucket=bucket.identifier, read=read, write=write,
                                                            versioning=versioning)


class _RefreshingCredentials(Credentials):

    def __init__(self, refresh):
        self._refresh = refresh
        self.temporary_credentials = self._refresh()
        super(_RefreshingCredentials, self).__init__(self.temporary_credentials.session_token)

    def refresh(self, request):
        self.temporary_credentials = self._refresh()

    def apply(self, headers, token=None):
        headers['authorization'] = 'Bearer {}'.format(_helpers.from_bytes(self.temporary_credentials.session_token))


class VerifiedHTTPSConnectionWithHeaders(VerifiedHTTPSConnection):
    def _tunnel(self):
        """This is just a simple rework of the CONNECT method to combine
        the headers with the CONNECT request as it causes problems for
        some proxies
        """
        connect_str = "CONNECT %s:%d HTTP/1.0\r\n" % (self._tunnel_host,
            self._tunnel_port)
        header_bytes = connect_str.encode("ascii")

        for header, value in self._tunnel_headers.items():
            header_str = "%s: %s\r\n" % (header, value)
            header_bytes += header_str.encode("latin-1")

        self.send(header_bytes + b'\r\n')

        response = self.response_class(self.sock, method=self._method)
        (version, code, message) = response._read_status()

        if code != OK:
            self.close()
            raise socket.error("Tunnel connection failed: %d %s" % (code,
                                                                    message.strip()))
        while True:
            line = response.fp.readline(http_client._MAXLINE + 1)
            if len(line) > http_client._MAXLINE:
                raise http_client.LineTooLong("header line")
            if not line:
                # for sites which EOF without sending a trailer
                break
            if line in (b'\r\n', b'\n', b''):
                break


class ProxyConnectWithHeadersHTTPSAdapter(HTTPAdapter):
    """Overriding HTTP Adapter so that we can use our own Connection, since
        we need to get at _tunnel()
    """
    def proxy_manager_for(self, proxy, **proxy_kwargs):
        manager = super(ProxyConnectWithHeadersHTTPSAdapter, self).proxy_manager_for(proxy, **proxy_kwargs)
        # Need to override the ConnectionCls with our Subclassed one to get at _tunnel()
        manager.pool_classes_by_scheme['https'].ConnectionCls = VerifiedHTTPSConnectionWithHeaders
        return manager


class _ProxySession(AuthorizedSession):

    def request(self, method, url, data=None, headers=None, **kwargs):
        parsed_url = urlparse(url)
        request_url = '%s://%s' % (parsed_url.scheme, parsed_url.netloc)
        self.proxies = StorageOperations.get_proxy_config(request_url)
        return super(_ProxySession, self).request(method, url, data, headers, **kwargs)


class _RefreshingClient(Client):
    MAX_REFRESH_ATTEMPTS = 100

    def __init__(self, bucket, read, write, refresh_credentials, versioning=False):
        credentials = _RefreshingCredentials(refresh=lambda: refresh_credentials(bucket, read, write, versioning))
        session = _ProxySession(credentials, max_refresh_attempts=self.MAX_REFRESH_ATTEMPTS)
        adapter = ProxyConnectWithHeadersHTTPSAdapter(max_retries=3)
        session.mount("https://", adapter)
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
        return _RefreshingClient(*args, refresh_credentials=GsTemporaryCredentials.from_cp_api, **kwargs)
