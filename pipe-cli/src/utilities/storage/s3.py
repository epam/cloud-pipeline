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

from botocore.endpoint import BotocoreHTTPSession, MAX_POOL_CONNECTIONS

from src.utilities.storage.s3_proxy_utils import AwsProxyConnectWithHeadersHTTPSAdapter

from src.utilities.storage.storage_usage import StorageUsageAccumulator

try:
    from urllib.request import urlopen  # Python 3
except ImportError:
    from urllib2 import urlopen  # Python 2

import click
import collections
import os
from boto3 import Session
from botocore.config import Config as AwsConfig
from botocore.credentials import RefreshableCredentials
from botocore.exceptions import ClientError
from botocore.session import get_session
from s3transfer.manager import TransferManager
from s3transfer import upload, tasks, copies

from src.api.data_storage import DataStorage
from src.model.data_storage_item_model import DataStorageItemModel, DataStorageItemLabelModel
from src.utilities.patterns import PatternMatcher
from src.utilities.progress_bar import ProgressPercentage
from src.utilities.storage.common import StorageOperations, AbstractListingManager, AbstractDeleteManager, \
    AbstractRestoreManager, AbstractTransferManager, TransferResult, UploadResult
from src.config import Config


class UploadedObjectsContainer:

    def __init__(self):
        self._objects = {}

    def add(self, bucket, key, data):
        self._objects[bucket + '/' + key] = data

    def pop(self, bucket, key):
        data = self._objects[bucket + '/' + key]
        del self._objects[bucket + '/' + key]
        return data


uploaded_objects_container = UploadedObjectsContainer()


def _put_object_task_main(self, client, fileobj, bucket, key, extra_args):
    with fileobj as body:
        output = client.put_object(Bucket=bucket, Key=key, Body=body, **extra_args)
        uploaded_objects_container.add(bucket, key, output.get('VersionId'))


def _complete_multipart_upload_task_main(self, client, bucket, key, upload_id, parts, extra_args):
    output = client.complete_multipart_upload(
        Bucket=bucket, Key=key, UploadId=upload_id,
        MultipartUpload={'Parts': parts},
        **extra_args)
    uploaded_objects_container.add(bucket, key, output.get('VersionId'))


def _copy_object_task_main(self, client, copy_source, bucket, key, extra_args, callbacks, size):
    output = client.copy_object(
        CopySource=copy_source, Bucket=bucket, Key=key, **extra_args)
    for callback in callbacks:
        callback(bytes_transferred=size)
    uploaded_objects_container.add(bucket, key, output.get('VersionId'))


# By default boto library doesn't aggregate uploaded object versions
# which have to be downloaded via an extra request for each individual object.
# This monkey patching allows to aggregate uploaded object versions
# and use them later on without any extra requests being performed.
upload.PutObjectTask._main = _put_object_task_main
tasks.CompleteMultipartUploadTask._main = _complete_multipart_upload_task_main
copies.CopyObjectTask._main = _copy_object_task_main


class StorageItemManager(object):

    def __init__(self, session, bucket=None, region_name=None, cross_region=False):
        self.session = session
        self.region_name = region_name
        self.s3 = session.resource('s3', config=S3BucketOperations.get_proxy_config(cross_region=cross_region),
                                   region_name=self.region_name)
        self.s3.meta.client._endpoint.http_session = BotocoreHTTPSession(
            max_pool_connections=MAX_POOL_CONNECTIONS, http_adapter_cls=AwsProxyConnectWithHeadersHTTPSAdapter)
        if bucket:
            self.bucket = self.s3.Bucket(bucket)

    @classmethod
    def show_progress(cls, quiet, size, lock=None):
        return StorageOperations.show_progress(quiet, size, lock)

    @classmethod
    def _get_user(cls):
        return StorageOperations.get_user()

    @classmethod
    def _convert_tags_to_url_string(cls, tags):
        if not tags:
            return tags
        parsed_tags = StorageOperations.parse_tags(tags)
        formatted_tags = ['%s=%s' % (key, value) for key, value in parsed_tags.items()]
        return '&'.join(formatted_tags)

    def _get_client(self):
        client = self.session.client('s3', config=S3BucketOperations.get_proxy_config(), region_name=self.region_name)
        client._endpoint.http_session.adapters['https://'] = BotocoreHTTPSession(
            max_pool_connections=MAX_POOL_CONNECTIONS, http_adapter_cls=AwsProxyConnectWithHeadersHTTPSAdapter)
        return client

    def get_s3_file_size(self, bucket, key):
        try:
            client = self._get_client()
            item = client.head_object(Bucket=bucket, Key=key)
            if 'DeleteMarker' in item:
                return None
            if 'ContentLength' in item:
                return int(item['ContentLength'])
            return None
        except ClientError:
            return None

    def get_s3_file_version(self, bucket, key):
        try:
            client = self._get_client()
            item = client.head_object(Bucket=bucket, Key=key)
            if 'DeleteMarker' in item:
                return None
            return item.get('VersionId')
        except ClientError:
            return None

    def get_uploaded_s3_file_version(self, bucket, key):
        return uploaded_objects_container.pop(bucket, key)

    @staticmethod
    def get_local_file_size(path):
        return StorageOperations.get_local_file_size(path)


class DownloadManager(StorageItemManager, AbstractTransferManager):

    def __init__(self, session, bucket, region_name=None):
        super(DownloadManager, self).__init__(session, bucket=bucket, region_name=region_name)

    def get_destination_key(self, destination_wrapper, relative_path):
        if destination_wrapper.path.endswith(os.path.sep):
            return os.path.join(destination_wrapper.path, relative_path)
        else:
            return destination_wrapper.path

    def get_source_key(self, source_wrapper, path):
        return path or source_wrapper.path

    def get_destination_size(self, destination_wrapper, destination_key):
        return self.get_local_file_size(destination_key)

    def transfer(self, source_wrapper, destination_wrapper, path=None,
                 relative_path=None, clean=False, quiet=False, size=None, tags=None, lock=None):
        source_key = self.get_source_key(source_wrapper, path)
        destination_key = self.get_destination_key(destination_wrapper, relative_path)

        self.create_local_folder(destination_key, lock)
        if StorageItemManager.show_progress(quiet, size, lock):
            self.bucket.download_file(source_key, destination_key, Callback=ProgressPercentage(relative_path, size))
        else:
            self.bucket.download_file(source_key, destination_key)
        if clean:
            source_wrapper.delete_item(source_key)


class UploadManager(StorageItemManager, AbstractTransferManager):

    def __init__(self, session, bucket, region_name=None):
        super(UploadManager, self).__init__(session, bucket=bucket, region_name=region_name)

    def get_destination_key(self, destination_wrapper, relative_path):
        return S3BucketOperations.normalize_s3_path(destination_wrapper, relative_path)

    def get_destination_size(self, destination_wrapper, destination_key):
        return self.get_s3_file_size(destination_wrapper.bucket.path, destination_key)

    def get_source_key(self, source_wrapper, source_path):
        if source_path:
            return os.path.join(source_wrapper.path, source_path)
        else:
            return source_wrapper.path

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None,
                 clean=False, quiet=False, size=None, tags=(), lock=None):
        source_key = self.get_source_key(source_wrapper, path)
        destination_key = self.get_destination_key(destination_wrapper, relative_path)

        tags += ("CP_SOURCE={}".format(source_key),)
        tags += ("CP_OWNER={}".format(self._get_user()),)
        extra_args = {
            'Tagging': self._convert_tags_to_url_string(tags),
            'ACL': 'bucket-owner-full-control'
        }
        TransferManager.ALLOWED_UPLOAD_ARGS.append('Tagging')
        if StorageItemManager.show_progress(quiet, size, lock):
            self.bucket.upload_file(source_key, destination_key, Callback=ProgressPercentage(relative_path, size),
                                    ExtraArgs=extra_args)
        else:
            self.bucket.upload_file(source_key, destination_key, ExtraArgs=extra_args)
        if clean:
            source_wrapper.delete_item(source_key)
        tags = StorageOperations.parse_tags(tags)
        version = self.get_uploaded_s3_file_version(destination_wrapper.bucket.path, destination_key)
        return UploadResult(source_key=source_key, destination_key=destination_key, destination_version=version,
                            tags=tags)


class TransferFromHttpOrFtpToS3Manager(StorageItemManager, AbstractTransferManager):

    def __init__(self, session, bucket, region_name=None):
        super(TransferFromHttpOrFtpToS3Manager, self).__init__(session, bucket=bucket, region_name=region_name)

    def get_destination_key(self, destination_wrapper, relative_path):
        if destination_wrapper.path.endswith(os.path.sep):
            return os.path.join(destination_wrapper.path, relative_path)
        else:
            return destination_wrapper.path

    def get_destination_size(self, destination_wrapper, destination_key):
        return self.get_s3_file_size(destination_wrapper.bucket.path, destination_key)

    def get_source_key(self, source_wrapper, source_path):
        return source_path or source_wrapper.path

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None,
                 clean=False, quiet=False, size=None, tags=(), lock=None):
        if clean:
            raise AttributeError("Cannot perform 'mv' operation due to deletion remote files "
                                 "is not supported for ftp/http sources.")
        source_key = self.get_source_key(source_wrapper, path)
        destination_key = self.get_destination_key(destination_wrapper, relative_path)

        tags += ("CP_SOURCE={}".format(source_key),)
        tags += ("CP_OWNER={}".format(self._get_user()),)
        extra_args = {
            'Tagging': self._convert_tags_to_url_string(tags),
            'ACL': 'bucket-owner-full-control'
        }
        TransferManager.ALLOWED_UPLOAD_ARGS.append('Tagging')
        file_stream = urlopen(source_key)
        if StorageItemManager.show_progress(quiet, size, lock):
            self.bucket.upload_fileobj(file_stream, destination_key, Callback=ProgressPercentage(relative_path, size),
                                       ExtraArgs=extra_args)
        else:
            self.bucket.upload_fileobj(file_stream, destination_key, ExtraArgs=extra_args)
        tags = StorageOperations.parse_tags(tags)
        version = self.get_uploaded_s3_file_version(destination_wrapper.bucket.path, destination_key)
        return UploadResult(source_key=source_key, destination_key=destination_key, destination_version=version,
                            tags=tags)


class TransferBetweenBucketsManager(StorageItemManager, AbstractTransferManager):

    def __init__(self, session, bucket, region_name=None, cross_region=False):
        self.cross_region = cross_region
        super(TransferBetweenBucketsManager, self).__init__(session, bucket=bucket, region_name=region_name,
                                                            cross_region=cross_region)

    def get_destination_key(self, destination_wrapper, relative_path):
        return S3BucketOperations.normalize_s3_path(destination_wrapper, relative_path)

    def get_destination_size(self, destination_wrapper, destination_key):
        return self.get_s3_file_size(destination_wrapper.bucket.path, destination_key)

    def get_source_key(self, source_wrapper, source_path):
        return source_path

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False,
                 quiet=False, size=None, tags=(), lock=None):
        # checked is bucket and file
        source_bucket = source_wrapper.bucket.path
        source_region = source_wrapper.bucket.region
        destination_key = self.get_destination_key(destination_wrapper, relative_path)
        copy_source = {
            'Bucket': source_bucket,
            'Key': path
        }
        source_client = self.build_source_client(source_region)

        extra_args = {
            'ACL': 'bucket-owner-full-control'
        }
        if StorageItemManager.show_progress(quiet, size, lock):
            self.bucket.copy(copy_source, destination_key, Callback=ProgressPercentage(relative_path, size),
                             ExtraArgs=extra_args, SourceClient=source_client)
        else:
            self.bucket.copy(copy_source, destination_key, ExtraArgs=extra_args, SourceClient=source_client)
        if clean:
            source_wrapper.delete_item(path)
        version = self.get_uploaded_s3_file_version(destination_wrapper.bucket.path, destination_key)
        return TransferResult(source_key=path, destination_key=destination_key, destination_version=version,
                              tags=StorageOperations.parse_tags(tags))

    def build_source_client(self, source_region):
        source_s3 = self.session.resource('s3',
                                          config=S3BucketOperations.get_proxy_config(
                                              cross_region=self.cross_region),
                                          region_name=source_region)
        source_s3.meta.client._endpoint.http_session = BotocoreHTTPSession(
            max_pool_connections=MAX_POOL_CONNECTIONS, http_adapter_cls=AwsProxyConnectWithHeadersHTTPSAdapter)
        return source_s3.meta.client

    @classmethod
    def has_required_tag(cls, tags, tag_name):
        for tag in tags:
            if tag.startswith(tag_name):
                return True
        return False

    @classmethod
    def convert_object_tags(cls, object_tags):
        tags = ()
        for tag in object_tags:
            if 'Key' in tag and 'Value' in tag:
                tags += ('{}={}'.format(tag['Key'], tag['Value']),)
        return tags


class RestoreManager(StorageItemManager, AbstractRestoreManager):
    VERSION_NOT_EXISTS_ERROR = 'Version "%s" doesn\'t exist.'

    def __init__(self, bucket, session, region_name=None):
        super(RestoreManager, self).__init__(session, region_name=region_name)
        self.bucket = bucket
        self.listing_manager = bucket.get_list_manager(True)

    def restore_version(self, version, exclude=[], include=[], recursive=False):
        client = self._get_client()
        bucket = self.bucket.bucket.path

        file_items = self._list_file_items()
        if not recursive or self._has_deleted_file(file_items):
            if not version:
                version = self._find_last_version(self.bucket.path, file_items)
            self.restore_file_version(version, bucket, client, file_items)
            return
        self.restore_folder(bucket, client, exclude, include, recursive)

    def restore_file_version(self, version, bucket, client, file_items):
        relative_path = self.bucket.path
        self._validate_version(bucket, client, version, file_items)
        try:
            copied_object = client.copy_object(Bucket=bucket, Key=relative_path,
                                               CopySource=dict(Bucket=bucket, Key=relative_path, VersionId=version))
            delete_us = dict(Objects=[])
            delete_us['Objects'].append(dict(Key=relative_path, VersionId=version))
            client.delete_objects(Bucket=bucket, Delete=delete_us)
            DataStorage.batch_copy_object_tags(self.bucket.bucket.identifier, [{
                'source': {
                    'path': relative_path,
                    'version': version
                },
                'destination': {
                    'path': relative_path
                }
            }, {
                'source': {
                    'path': relative_path,
                    'version': version
                },
                'destination': {
                    'path': relative_path,
                    'version': copied_object['VersionId']
                }
            }])
            DataStorage.batch_delete_object_tags(self.bucket.bucket.identifier, [{
                'path': relative_path,
                'version': version
            }])
        except ClientError as e:
            error_message = str(e)
            if 'delete marker' in error_message:
                text = "Cannot restore a delete marker"
            elif 'Invalid version' in error_message:
                text = self.VERSION_NOT_EXISTS_ERROR % version
            else:
                text = error_message
            raise RuntimeError(text)

    def load_item(self, bucket, client):
        try:
            item = client.head_object(Bucket=bucket, Key=self.bucket.path)
        except ClientError as e:
            error_message = str(e)
            if 'Not Found' in error_message:
                return self.load_delete_marker(bucket, self.bucket.path, client)
            raise RuntimeError('Requested file "{}" doesn\'t exist. {}.'.format(self.bucket.path, error_message))
        if item is None:
            raise RuntimeError('Path "{}" doesn\'t exist'.format(self.bucket.path))
        return item

    def _list_file_items(self):
        relative_path = self.bucket.path
        all_items = self.listing_manager.list_items(relative_path, show_all=True)
        item_name = relative_path.split(S3BucketOperations.S3_PATH_SEPARATOR)[-1]
        return [item for item in all_items if item.type == 'File' and item.name == item_name]

    def load_delete_marker(self, bucket, path, client, quite=False):
        operation_parameters = {
            'Bucket': bucket,
            'Prefix': path
        }
        paginator = client.get_paginator('list_object_versions')
        pages = paginator.paginate(**operation_parameters)
        for page in pages:
            if 'Versions' not in page and 'DeleteMarkers' not in page:
                raise RuntimeError('Requested file "{}" doesn\'t exist.'.format(path))
            if 'DeleteMarkers' not in page:
                continue
            for item in page['DeleteMarkers']:
                if 'IsLatest' in item and item['IsLatest'] and path == item['Key']:
                    return item
        if not quite:
            raise RuntimeError('Latest file version is not deleted. Please specify "--version" parameter.')

    @staticmethod
    def _find_last_version(relative_path, file_items):
        if not file_items or not len(file_items):
            raise RuntimeError('Requested file "%s" doesn\'t exist.' % relative_path)
        item = file_items[0]
        if not item:
            raise RuntimeError('Failed to receive deleted marker')
        if not item.delete_marker:
            raise RuntimeError('Latest file version is not deleted. Please specify "--version" parameter.')
        versions = [item_version for item_version in item.versions if not item_version.delete_marker]
        if not versions or not len(versions):
            raise RuntimeError('Latest file version is not deleted. Please specify "--version" parameter.')
        version = versions[0].version
        if not version:
            raise RuntimeError('Failed to find last version')
        return version

    @staticmethod
    def _has_deleted_file(file_items):
        return file_items and len(file_items) and file_items[0] and file_items[0].delete_marker

    def restore_folder(self, bucket, client, exclude, include, recursive):
        delimiter = S3BucketOperations.S3_PATH_SEPARATOR
        path = self.bucket.path
        prefix = StorageOperations.get_prefix(path)
        if not prefix.endswith(delimiter):
            prefix += delimiter

        operation_parameters = {
            'Bucket': bucket
        }
        if path:
            operation_parameters['Prefix'] = prefix
        if not recursive:
            operation_parameters['Delimiter'] = delimiter
        paginator = client.get_paginator('list_object_versions')
        pages = paginator.paginate(**operation_parameters)
        restore_us = dict(Objects=[])
        for page in pages:
            S3BucketOperations.process_listing(page, 'DeleteMarkers', restore_us, delimiter, exclude, include, prefix,
                                               versions=True)
            # flush once aws limit reached
            restore_us = S3BucketOperations.send_delete_objects_request(client, bucket, restore_us)
        # flush rest
        if len(restore_us['Objects']):
            client.delete_objects(Bucket=bucket, Delete=restore_us)

    def _validate_version(self, bucket, client, version, file_items):
        current_item = self.load_item(bucket, client)
        if current_item['VersionId'] == version:
            raise RuntimeError('Version "{}" is already the latest version'.format(version))
        if not file_items:
            raise RuntimeError(self.VERSION_NOT_EXISTS_ERROR % version)
        item = file_items[0]
        if not any(item.version == version for item in item.versions):
            raise RuntimeError(self.VERSION_NOT_EXISTS_ERROR % version)


class DeleteManager(StorageItemManager, AbstractDeleteManager):
    def __init__(self, bucket, session, region_name=None):
        super(DeleteManager, self).__init__(session, region_name=region_name)
        self.bucket = bucket

    def delete_items(self, relative_path, recursive=False, exclude=[], include=[], version=None, hard_delete=False):
        client = self._get_client()
        delimiter = S3BucketOperations.S3_PATH_SEPARATOR
        bucket = self.bucket.bucket.path
        prefix = StorageOperations.get_prefix(relative_path)

        if not recursive and not hard_delete:
            delete_us = dict(Objects=[])
            if version is not None:
                delete_us['Objects'].append(dict(Key=prefix, VersionId=version))
            else:
                delete_us['Objects'].append(dict(Key=prefix))
            client.delete_objects(Bucket=bucket, Delete=delete_us)
            if self.bucket.bucket.policy.versioning_enabled:
                if version:
                    latest_version = self.get_s3_file_version(bucket, prefix)
                    if latest_version:
                        DataStorage.batch_copy_object_tags(self.bucket.bucket.identifier, [{
                            'source': {
                                'path': relative_path,
                                'version': latest_version
                            },
                            'destination': {
                                'path': relative_path
                            }
                        }])
                        self._delete_object_tags(delete_us)
                    else:
                        delete_us['Objects'].append(dict(Key=prefix))
                        self._delete_object_tags(delete_us)
            else:
                self._delete_object_tags(delete_us)
        else:
            operation_parameters = {
                'Bucket': bucket,
                'Prefix': prefix
            }
            if hard_delete:
                paginator = client.get_paginator('list_object_versions')
            else:
                paginator = client.get_paginator('list_objects_v2')
            pages = paginator.paginate(**operation_parameters)
            delete_us = dict(Objects=[])
            for page in pages:
                S3BucketOperations.process_listing(page, 'Contents', delete_us, delimiter, exclude, include, prefix)
                S3BucketOperations.process_listing(page, 'Versions', delete_us, delimiter, exclude, include, prefix,
                                                   versions=True)
                S3BucketOperations.process_listing(page, 'DeleteMarkers', delete_us, delimiter, exclude, include,
                                                   prefix, versions=True)
                # flush once aws limit reached
                if not self.bucket.bucket.policy.versioning_enabled or hard_delete:
                    self._delete_all_object_tags(delete_us)
                delete_us = S3BucketOperations.send_delete_objects_request(client, bucket, delete_us)
            # flush rest
            if len(delete_us['Objects']):
                if not self.bucket.bucket.policy.versioning_enabled or hard_delete:
                    self._delete_all_object_tags(delete_us)
                client.delete_objects(Bucket=bucket, Delete=delete_us)

    def _delete_all_object_tags(self, delete_us, chunk_size=100):
        item_names = list(set(item['Key'] for item in delete_us['Objects']))
        for item_names_chunk in [item_names[i:i + chunk_size]
                                 for i in range(0, len(item_names), chunk_size)]:
            DataStorage.batch_delete_all_object_tags(self.bucket.bucket.identifier,
                                                     [{'path': item_name}
                                                      for item_name in item_names_chunk])

    def _delete_object_tags(self, delete_us, chunk_size=100):
        for items_chunk in [delete_us['Objects'][i:i + chunk_size]
                            for i in range(0, len(delete_us['Objects']), chunk_size)]:
            DataStorage.batch_delete_object_tags(self.bucket.bucket.identifier,
                                                 [{'path': item['Key'], 'version': item.get('VersionId')}
                                                  for item in items_chunk])


class ListingManager(StorageItemManager, AbstractListingManager):
    DEFAULT_PAGE_SIZE = StorageOperations.DEFAULT_PAGE_SIZE

    def __init__(self, bucket, session, show_versions=False, region_name=None):
        super(ListingManager, self).__init__(session, region_name=region_name)
        self.bucket = bucket
        self.show_versions = show_versions

    def list_items(self, relative_path=None, recursive=False, page_size=StorageOperations.DEFAULT_PAGE_SIZE,
                   show_all=False):
        delimiter = S3BucketOperations.S3_PATH_SEPARATOR
        client = self._get_client()
        operation_parameters = {
            'Bucket': self.bucket.bucket.path
        }
        if not show_all:
            operation_parameters['PaginationConfig'] = {
                    'MaxItems': page_size,
                    'PageSize': page_size
                 }
        else:
            page_size = None
        if not recursive:
            operation_parameters['Delimiter'] = delimiter
        prefix = S3BucketOperations.get_prefix(delimiter, relative_path)
        if relative_path:
            operation_parameters['Prefix'] = prefix

        if self.show_versions:
            return self.list_versions(client, prefix, operation_parameters, recursive, page_size)
        else:
            return self.list_objects(client, prefix, operation_parameters, recursive, page_size)

    def get_summary_with_depth(self, max_depth, relative_path=None):
        bucket_name = self.bucket.bucket.path
        delimiter = S3BucketOperations.S3_PATH_SEPARATOR
        client = self._get_client()
        operation_parameters = {
            'Bucket': bucket_name
        }
        prefix = S3BucketOperations.get_prefix(delimiter, relative_path)
        if relative_path:
            operation_parameters['Prefix'] = prefix
            max_depth += len(prefix.split(delimiter))

        paginator = client.get_paginator('list_objects_v2')
        page_iterator = paginator.paginate(**operation_parameters)

        accumulator = StorageUsageAccumulator(bucket_name, relative_path, delimiter, max_depth)

        for page in page_iterator:
            if 'Contents' in page:
                for file in page['Contents']:
                    name = self.get_file_name(file, prefix, True)
                    size = file['Size']
                    accumulator.add_path(name, size)
            if not page['IsTruncated']:
                break
        return accumulator.get_tree()

    def get_summary(self, relative_path=None):
        delimiter = S3BucketOperations.S3_PATH_SEPARATOR
        client = self._get_client()
        operation_parameters = {
            'Bucket': self.bucket.bucket.path
        }
        prefix = S3BucketOperations.get_prefix(delimiter, relative_path)
        if relative_path:
            operation_parameters['Prefix'] = prefix

        paginator = client.get_paginator('list_objects_v2')
        page_iterator = paginator.paginate(**operation_parameters)

        total_size = 0
        total_objects = 0

        for page in page_iterator:
            if 'Contents' in page:
                for file in page['Contents']:
                    if self.prefix_match(file, relative_path):
                        total_size += file['Size']
                        total_objects += 1
            if not page['IsTruncated']:
                break
        return delimiter.join([self.bucket.bucket.path, relative_path]), total_objects, total_size

    @classmethod
    def prefix_match(cls, page_file, relative_path=None):
        if not relative_path:
            return True
        if 'Key' not in page_file or not page_file['Key']:
            return False
        key = page_file['Key']
        if key == relative_path:
            return True
        if relative_path.endswith(S3BucketOperations.S3_PATH_SEPARATOR):
            return True
        if key.startswith("%s%s" % (relative_path, S3BucketOperations.S3_PATH_SEPARATOR)):
            return True
        return False

    def list_versions(self, client, prefix,  operation_parameters, recursive, page_size):
        paginator = client.get_paginator('list_object_versions')
        page_iterator = paginator.paginate(**operation_parameters)
        items = []
        item_keys = collections.OrderedDict()
        items_count = 0
        for page in page_iterator:
            if 'CommonPrefixes' in page:
                for folder in page['CommonPrefixes']:
                    name = S3BucketOperations.get_item_name(folder['Prefix'], prefix=prefix)
                    items.append(self.get_folder_object(name))
                    items_count += 1
            if 'Versions' in page:
                for version in page['Versions']:
                    name = self.get_file_name(version, prefix, recursive)
                    item = self.get_file_object(version, name, version=True)
                    self.process_version(item, item_keys, name)
            if 'DeleteMarkers' in page:
                for delete_marker in page['DeleteMarkers']:
                    name = self.get_file_name(delete_marker, prefix, recursive)
                    item = self.get_file_object(delete_marker, name, version=True, storage_class=False)
                    item.delete_marker = True
                    self.process_version(item, item_keys, name)
            items_count += len(item_keys)
            if self.need_to_stop_paging(page, page_size, items_count):
                break
        items.extend(item_keys.values())
        for item in items:
            item.versions.sort(key=lambda x: x.changed, reverse=True)
        return items

    def process_version(self, item, item_keys, name):
        if name not in item_keys:
            item.versions = [item]
            item_keys[name] = item
        else:
            previous = item_keys[name]
            versions = previous.versions
            versions.append(item)
            if item.latest:
                item.versions = versions
                item_keys[name] = item

    def list_objects(self, client, prefix, operation_parameters, recursive, page_size):
        paginator = client.get_paginator('list_objects_v2')
        page_iterator = paginator.paginate(**operation_parameters)
        items = []
        items_count = 0
        for page in page_iterator:
            if 'CommonPrefixes' in page:
                for folder in page['CommonPrefixes']:
                    name = S3BucketOperations.get_item_name(folder['Prefix'], prefix=prefix)
                    items.append(self.get_folder_object(name))
                    items_count += 1
            if 'Contents' in page:
                for file in page['Contents']:
                    name = self.get_file_name(file, prefix, recursive)
                    item = self.get_file_object(file, name)
                    items.append(item)
                    items_count += 1
            if self.need_to_stop_paging(page, page_size, items_count):
                break
        return items

    def get_file_object(self, file, name, version=False, storage_class=True):
        item = DataStorageItemModel()
        item.type = 'File'
        item.name = name
        if 'Size' in file:
            item.size = file['Size']
        item.path = name
        item.changed = file['LastModified'].astimezone(Config.instance().timezone())
        if storage_class:
            item.labels = [DataStorageItemLabelModel('StorageClass', file['StorageClass'])]
        if version:
            item.version = file['VersionId']
            item.latest = file['IsLatest']
        return item

    def get_file_name(self, file, prefix, recursive):
        if recursive:
            name = file['Key']
        else:
            name = S3BucketOperations.get_item_name(file['Key'], prefix=prefix)
        return name

    def get_folder_object(self, name):
        item = DataStorageItemModel()
        item.type = 'Folder'
        item.name = name
        item.path = name
        return item

    def get_items(self, relative_path):
        return S3BucketOperations.get_items(self.bucket, session=self.session)

    def get_file_tags(self, relative_path):
        return ObjectTaggingManager.get_object_tagging(ObjectTaggingManager(
            self.session, self.bucket, self.region_name), relative_path)

    @staticmethod
    def need_to_stop_paging(page, page_size, items_count):
        if 'IsTruncated' in page and not page['IsTruncated']:
            return True
        if page_size and items_count >= page_size:
            return True
        return False


class ObjectTaggingManager(StorageItemManager):

    def __init__(self, session, bucket, region_name=None):
        super(ObjectTaggingManager, self).__init__(session, region_name=region_name)
        self.bucket = bucket

    def get_object_tagging(self, source):
        client = self._get_client()
        response = client.get_object_tagging(
            Bucket=self.bucket,
            Key=source
        )
        return response['TagSet']


class S3BucketOperations(object):

    S3_ENDPOINT_URL = 'https://s3.amazonaws.com'
    S3_PATH_SEPARATOR = StorageOperations.PATH_SEPARATOR
    S3_REQUEST_ELEMENTS_LIMIT = 1000
    __config__ = None

    @classmethod
    def get_proxy_config(cls, cross_region=False):
        if cls.__config__ is None:
            cls.__config__ = Config.instance()
        if cls.__config__.proxy is None:
            if cross_region:
                os.environ['no_proxy'] = ''
            return None
        else:
            return AwsConfig(proxies=cls.__config__.resolve_proxy(target_url=cls.S3_ENDPOINT_URL))

    @classmethod
    def _get_client(cls, session, region_name=None):
        client = session.client('s3', config=S3BucketOperations.get_proxy_config(), region_name=region_name)
        client._endpoint.http_session.adapters['https://'] = BotocoreHTTPSession(
            max_pool_connections=MAX_POOL_CONNECTIONS, http_adapter_cls=AwsProxyConnectWithHeadersHTTPSAdapter)
        return client

    @classmethod
    def init_wrapper(cls, storage_wrapper, session=None, versioning=False):
        if storage_wrapper.is_local():
            return storage_wrapper
        delimiter = S3BucketOperations.S3_PATH_SEPARATOR
        prefix = cls.get_prefix(delimiter, storage_wrapper.path)
        check_file = True
        if prefix.endswith(cls.S3_PATH_SEPARATOR):
            prefix = prefix[:-1]
            check_file = False
        if session is None:
            session = cls.assumed_session(storage_wrapper.bucket.identifier, None, 'cp', versioning=versioning)
            storage_wrapper.session = session
        client = cls._get_client(session, storage_wrapper.bucket.region)
        if versioning:
            paginator = client.get_paginator('list_object_versions')
        else:
            paginator = client.get_paginator('list_objects_v2')
        operation_parameters = {
            'Bucket': storage_wrapper.bucket.path,
            'Prefix': prefix,
            'Delimiter': delimiter,
            'PaginationConfig':
                {
                    'MaxItems': 5,
                    'PageSize': 5
                }
        }
        page_iterator = paginator.paginate(**operation_parameters)
        for page in page_iterator:
            if check_file:
                if cls.check_section(storage_wrapper, prefix, page, 'DeleteMarkers'):
                    break
                if cls.check_section(storage_wrapper, prefix, page, 'Versions'):
                    break
                if cls.check_section(storage_wrapper, prefix, page, 'Contents'):
                    break
            if 'CommonPrefixes' in page:
                for page_info in page['CommonPrefixes']:
                    if 'Prefix' in page_info and page_info['Prefix'] == prefix + cls.S3_PATH_SEPARATOR:
                        storage_wrapper.exists_flag = True
                        storage_wrapper.is_file_flag = False
                        storage_wrapper.is_empty_flag = False
                        break
        return storage_wrapper

    @classmethod
    def check_section(cls, storage_wrapper, prefix, page, section):
        if section in page:
            for page_info in page[section]:
                if 'Key' in page_info and page_info['Key'] == prefix:
                    storage_wrapper.exists_flag = True
                    storage_wrapper.is_file_flag = True
                    return True
        else:
            return False

    @classmethod
    def get_prefix(cls, delimiter, path):
        return StorageOperations.get_prefix(path, delimiter=delimiter)

    @classmethod
    def get_item_name(cls, param, prefix=None):
        return StorageOperations.get_item_name(param, prefix)

    @classmethod
    def get_items(cls, storage_wrapper, session=None):
        if session is None:
            session = cls.assumed_session(storage_wrapper.bucket.identifier, None, 'cp')

        delimiter = S3BucketOperations.S3_PATH_SEPARATOR
        client = cls._get_client(session, storage_wrapper.bucket.region)
        paginator = client.get_paginator('list_objects_v2')
        operation_parameters = {
            'Bucket': storage_wrapper.bucket.path,
        }

        prefix = cls.get_prefix(delimiter, storage_wrapper.path)
        operation_parameters['Prefix'] = prefix
        page_iterator = paginator.paginate(**operation_parameters)
        for page in page_iterator:
            if 'Contents' in page:
                for file in page['Contents']:
                    name = cls.get_item_name(file['Key'], prefix=prefix)
                    if name.endswith(delimiter):
                        continue
                    yield ('File', file['Key'], cls.get_prefix(delimiter, name), file['Size'])

    @classmethod
    def path_exists(cls, storage_wrapper, relative_path, session=None):
        delimiter = S3BucketOperations.S3_PATH_SEPARATOR
        prefix = cls.get_prefix(delimiter, storage_wrapper.path)
        if relative_path:
            skip_separator = prefix.endswith(S3BucketOperations.S3_PATH_SEPARATOR)
            if skip_separator:
                prefix = prefix + relative_path
            else:
                prefix = prefix + delimiter + relative_path
        if session is None:
            session = cls.assumed_session(storage_wrapper.bucket.identifier, None, 'cp')
        client = cls._get_client(session, storage_wrapper.bucket.region)
        paginator = client.get_paginator('list_objects_v2')
        operation_parameters = {
            'Bucket': storage_wrapper.bucket.path,
            'Prefix': prefix,
            'Delimiter': delimiter,
            'PaginationConfig':
                {
                    'MaxItems': 5,
                    'PageSize': 5
                }
        }

        page_iterator = paginator.paginate(**operation_parameters)
        if not prefix.endswith(cls.S3_PATH_SEPARATOR) and not storage_wrapper.path.endswith(cls.S3_PATH_SEPARATOR):
            prefix = storage_wrapper.path
        for page in page_iterator:
            if cls.check_prefix_existence_for_section(prefix, page, 'Contents', 'Key'):
                return True
            if cls.check_prefix_existence_for_section(prefix, page, 'CommonPrefixes', 'Prefix'):
                return True
        return False

    @classmethod
    def check_prefix_existence_for_section(cls, prefix, page, section, key):
        if section in page:
            for page_info in page[section]:
                if key in page_info and page_info[key] == prefix:
                    return True
        return False

    @classmethod
    def get_list_manager(cls, source_wrapper, show_versions=False):
        session = cls.assumed_session(source_wrapper.bucket.identifier, None, 'ls', versioning=show_versions)
        return ListingManager(source_wrapper, session, show_versions=show_versions,
                              region_name=source_wrapper.bucket.region)

    @classmethod
    def get_delete_manager(cls, source_wrapper, versioning=False):
        session = cls.assumed_session(source_wrapper.bucket.identifier, None, 'mv', versioning=versioning)
        return DeleteManager(source_wrapper, session, source_wrapper.bucket.region)

    @classmethod
    def get_restore_manager(cls, source_wrapper):
        session = cls.assumed_session(source_wrapper.bucket.identifier, None, 'mv', versioning=True)
        return RestoreManager(source_wrapper, session, source_wrapper.bucket.region)

    @classmethod
    def delete_item(cls, storage_wrapper, relative_path, session=None):
        if session is None:
            session = cls.assumed_session(storage_wrapper.bucket.identifier, None, 'mv')
        client = cls._get_client(session, storage_wrapper.bucket.region)
        delimiter = S3BucketOperations.S3_PATH_SEPARATOR
        bucket = storage_wrapper.bucket.path
        if relative_path:
            prefix = relative_path
            if prefix.startswith(delimiter):
                prefix = prefix[1:]
        else:
            prefix = delimiter

        delete_us = dict(Objects=[])
        delete_us['Objects'].append(dict(Key=prefix))
        client.delete_objects(Bucket=bucket, Delete=delete_us)

    @classmethod
    def normalize_s3_path(cls, destination_wrapper, relative_path):
        return StorageOperations.normalize_path(destination_wrapper, relative_path)

    @classmethod
    def assumed_session(cls, source_bucket_id, destination_bucket_id, command, versioning=False):
        def refresh():
            credentials = DataStorage.get_temporary_credentials(source_bucket_id, destination_bucket_id, command,
                                                                versioning=versioning)
            return dict(
                access_key=credentials.access_key_id,
                secret_key=credentials.secret_key,
                token=credentials.session_token,
                expiry_time=credentials.expiration)

        fresh_metadata = refresh()
        session_credentials = RefreshableCredentials.create_from_metadata(
            metadata=fresh_metadata,
            refresh_using=refresh,
            method='sts-assume-role')

        s = get_session()
        s._credentials = session_credentials
        return Session(botocore_session=s)

    @classmethod
    def get_transfer_between_buckets_manager(cls, source_wrapper, destination_wrapper, command):
        source_id = source_wrapper.bucket.identifier
        destination_id = destination_wrapper.bucket.identifier
        session = cls.assumed_session(source_id, destination_id, command)
        # replace session to be able to delete source for move
        source_wrapper.session = session
        destination_bucket = destination_wrapper.bucket.path
        cross_region = destination_wrapper.bucket.region != source_wrapper.bucket.region
        return TransferBetweenBucketsManager(session, destination_bucket, destination_wrapper.bucket.region,
                                             cross_region)

    @classmethod
    def get_download_manager(cls, source_wrapper, destination_wrapper, command):
        source_id = source_wrapper.bucket.identifier
        session = cls.assumed_session(source_id, None, command)
        # replace session to be able to delete source for move
        source_wrapper.session = session
        source_bucket = source_wrapper.bucket.path
        return DownloadManager(session, source_bucket, source_wrapper.bucket.region)

    @classmethod
    def get_upload_manager(cls, source_wrapper, destination_wrapper, command):
        destination_id = destination_wrapper.bucket.identifier
        session = cls.assumed_session(None, destination_id, command)
        destination_bucket = destination_wrapper.bucket.path
        return UploadManager(session, destination_bucket, destination_wrapper.bucket.region)

    @classmethod
    def get_transfer_from_http_or_ftp_manager(cls, source_wrapper, destination_wrapper, command):
        destination_id = destination_wrapper.bucket.identifier
        session = cls.assumed_session(None, destination_id, command)
        destination_bucket = destination_wrapper.bucket.path
        return TransferFromHttpOrFtpToS3Manager(session, destination_bucket, destination_wrapper.bucket.region)

    @classmethod
    def get_full_path(cls, path, param):
        delimiter = cls.S3_PATH_SEPARATOR
        return cls.remove_double_slashes(path + delimiter + param)

    @classmethod
    def remove_double_slashes(cls, path):
        return StorageOperations.remove_double_slashes(path)

    @staticmethod
    def process_listing(page, name, delete_us, delimiter, exclude, include, prefix, versions=False):
        found_file = False
        if name in page:
            if not versions:
                single_file_item = S3BucketOperations.get_single_file_item(name, page, prefix)
                if single_file_item:
                    S3BucketOperations.add_item_to_deletion(single_file_item, prefix, delimiter, include, exclude,
                                                            versions, delete_us)
                    return True
            for item in page[name]:
                if item is None:
                    break
                if item['Key'] == prefix:
                    found_file = True
                if S3BucketOperations.expect_to_delete_file(prefix, item):
                    continue
                S3BucketOperations.add_item_to_deletion(item, prefix, delimiter, include, exclude, versions, delete_us)
        return found_file

    @staticmethod
    def get_single_file_item(name, page, prefix):
        single_file_item = None
        for item in page[name]:
            if item is None:
                break
            if not prefix.endswith(S3BucketOperations.S3_PATH_SEPARATOR) and item['Key'] == prefix:
                single_file_item = item
                break
        return single_file_item

    @staticmethod
    def expect_to_delete_file(prefix, item):
        return not prefix.endswith(S3BucketOperations.S3_PATH_SEPARATOR) and not item['Key'] == prefix \
               and not item['Key'].startswith(prefix + S3BucketOperations.S3_PATH_SEPARATOR)

    @staticmethod
    def add_item_to_deletion(item, prefix, delimiter, include, exclude, versions, delete_us):
        name = S3BucketOperations.get_item_name(item['Key'], prefix=prefix)
        name = S3BucketOperations.get_prefix(delimiter, name)
        if not PatternMatcher.match_any(name, include):
            return
        if PatternMatcher.match_any(name, exclude, default=False):
            return
        if versions:
            delete_us['Objects'].append(dict(Key=item['Key'], VersionId=item['VersionId']))
        else:
            delete_us['Objects'].append(dict(Key=item['Key']))

    @staticmethod
    def send_delete_objects_request(client, bucket, delete_us, limit=S3_REQUEST_ELEMENTS_LIMIT):
        if len(delete_us['Objects']) >= limit:
            client.delete_objects(Bucket=bucket, Delete=dict(Objects=delete_us['Objects'][:limit]))
            return dict(Objects=delete_us['Objects'][limit:])
        return delete_us
