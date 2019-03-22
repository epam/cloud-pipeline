# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

try:
    from urllib.request import urlopen  # Python 3
except ImportError:
    from urllib2 import urlopen  # Python 2

import click
import collections
import jwt
import os
import re
from boto3 import Session
from botocore.config import Config as AwsConfig
from botocore.credentials import RefreshableCredentials
from botocore.exceptions import ClientError
from botocore.session import get_session
from s3transfer.manager import TransferManager

from src.api.data_storage import DataStorage
from src.model.data_storage_item_model import DataStorageItemModel, DataStorageItemLabelModel
from src.utilities.patterns import PatternMatcher
from .progress_bar import ProgressPercentage
from ..config import Config


class StorageItemManager(object):

    MAX_TAGS_NUMBER = 10
    MAX_KEY_LENGTH = 128
    MAX_VALUE_LENGTH = 256
    TAG_SHORTEN_SUFFIX = '...'

    def __init__(self, session, bucket=None):
        self.session = session
        self.s3 = session.resource('s3', config=S3BucketOperations.get_proxy_config())
        if bucket:
            self.bucket = self.s3.Bucket(bucket)

    @classmethod
    def show_progress(cls, quiet, size):
        return not quiet and size is not None

    @classmethod
    def _get_user(cls):
        config = Config.instance()
        user_info = jwt.decode(config.access_key, verify=False)
        if 'sub' in user_info:
            return user_info['sub']
        raise RuntimeError('Cannot find user info.')

    @classmethod
    def _convert_tags_to_url_string(cls, tags):
        pattern = re.compile('[^a-zA-Z0-9\s_.\-@:+/\\\]+')
        if not tags:
            return tags
        if len(tags) > cls.MAX_TAGS_NUMBER:
            raise ValueError("Maximum allowed number of tags is {}. Provided {} tags.".format(cls.MAX_TAGS_NUMBER, len(tags)))
        formatted_tags = []
        for tag in tags:
            if "=" not in tag:
                raise ValueError("Tags must be specified as KEY=VALUE pair.")
            parts = tag.split("=", 1)
            key = parts[0]
            if len(key) > cls.MAX_KEY_LENGTH:
                click.echo("Maximum key value is {}. Provided key {}.".format(cls.MAX_KEY_LENGTH, key))
                continue
            value = parts[1]
            value = value.replace('\\', '/')
            if not value or value.isspace() or bool(pattern.search(value)):
                click.echo("The tag value you have provided is invalid: %s. The tag %s will be skipped." % (value, key))
                continue
            if len(value) > cls.MAX_VALUE_LENGTH:
                value = value[:cls.MAX_VALUE_LENGTH - len(cls.TAG_SHORTEN_SUFFIX)] + cls.TAG_SHORTEN_SUFFIX
            formatted_tags.append(key + "=" + value)
        return "&".join(formatted_tags)

    def get_s3_file_size(self, bucket, key):
        try:
            client = self.session.client('s3', config=S3BucketOperations.get_proxy_config())
            item = client.head_object(Bucket=bucket, Key=key)
            if 'DeleteMarker' in item:
                return None
            if 'ContentLength' in item:
                return int(item['ContentLength'])
            return None
        except ClientError:
            return None

    @staticmethod
    def get_local_file_size(path):
        try:
            return os.path.getsize(path)
        except OSError:
            return None


class DownloadManager(StorageItemManager):

    def __init__(self, session, bucket):
        super(DownloadManager, self).__init__(session, bucket=bucket)

    def transfer(self, source_wrapper, destination_wrapper, path=None,
                 relative_path=None, clean=False, quiet=False, size=None, tags=None, skip_existing=False):
        if path:
            source_key = path
        else:
            source_key = source_wrapper.path
        if destination_wrapper.path.endswith(os.path.sep):
            destination_key = os.path.join(destination_wrapper.path, relative_path)
        else:
            destination_key = destination_wrapper.path
        if skip_existing:
            remote_size = self.get_s3_file_size(source_wrapper.bucket.path, source_key)
            local_size = self.get_local_file_size(destination_key)
            if local_size is not None and remote_size == local_size:
                if not quiet:
                    click.echo('Skipping file %s since it exists in the destination %s' % (source_key, destination_key))
                return
        folder = os.path.dirname(destination_key)
        if folder and not os.path.exists(folder):
            os.makedirs(folder)
        if StorageItemManager.show_progress(quiet, size):
            self.bucket.download_file(source_key, destination_key, Callback=ProgressPercentage(relative_path, size))
        else:
            self.bucket.download_file(source_key, destination_key)
        if clean:
            source_wrapper.delete_item(source_key)


class UploadManager(StorageItemManager):

    def __init__(self, session, bucket):
        super(UploadManager, self).__init__(session, bucket=bucket)

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None,
                 clean=False, quiet=False, size=None, tags=(), skip_existing=False):
        if path:
            source_key = os.path.join(source_wrapper.path, path)
        else:
            source_key = source_wrapper.path
        destination_key = S3BucketOperations.normalize_s3_path(destination_wrapper, relative_path)
        if skip_existing:
            local_size = self.get_local_file_size(source_key)
            remote_size = self.get_s3_file_size(destination_wrapper.bucket.path, destination_key)
            if remote_size is not None and local_size == remote_size:
                if not quiet:
                    click.echo('Skipping file %s since it exists in the destination %s' % (source_key, destination_key))
                return
        tags += ("CP_SOURCE={}".format(source_key),)
        tags += ("CP_OWNER={}".format(self._get_user()),)
        extra_args = {
            'Tagging': self._convert_tags_to_url_string(tags)
        }
        TransferManager.ALLOWED_UPLOAD_ARGS.append('Tagging')
        if StorageItemManager.show_progress(quiet, size):
            self.bucket.upload_file(source_key, destination_key, Callback=ProgressPercentage(relative_path, size),
                                    ExtraArgs=extra_args)
        else:
            self.bucket.upload_file(source_key, destination_key, ExtraArgs=extra_args)
        if clean:
            source_wrapper.delete_item(source_key)


class TransferFromHttpOrFtpToS3Manager(StorageItemManager):

    def __init__(self, session, bucket):
        super(TransferFromHttpOrFtpToS3Manager, self).__init__(session, bucket=bucket)

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None,
                 clean=False, quiet=False, size=None, tags=(), skip_existing=False):
        """
        Transfers data from remote resource (only ftp(s) or http(s) protocols supported) to the s3 bucket.
        :param source_wrapper: wrapper for ftp or http resource
        :type source_wrapper: FtpSourceWrapper or HttpSourceWrapper
        :param destination_wrapper: wrapper for s3 resource
        :type destination_wrapper: S3BucketWrapper
        :param path: full path to remote file
        :param relative_path: relative path
        :param clean: remove source files (unsupported for this kind of transfer)
        :param quiet: True if quite mode specified
        :param size: the size of the source file
        :param tags: additional tags that will be included to the s3 object (tags CP_SOURCE and CP_OWNER will be
                     included by default)
        :param skip_existing: indicates --skip_existing option
        """
        if clean:
            raise AttributeError("Cannot perform 'mv' operation due to deletion remote files "
                                 "is not supported for ftp/http sources.")
        if path:
            source_key = path
        else:
            source_key = source_wrapper.path
        if destination_wrapper.path.endswith(os.path.sep):
            destination_key = os.path.join(destination_wrapper.path, relative_path)
        else:
            destination_key = destination_wrapper.path
        if skip_existing:
            remote_size = size
            s3_size = self.get_s3_file_size(destination_wrapper.bucket.path, destination_key)
            if s3_size is not None and remote_size == s3_size:
                if not quiet:
                    click.echo('Skipping file %s since it exists in the destination %s' % (source_key, destination_key))
                return
        tags += ("CP_SOURCE={}".format(source_key),)
        tags += ("CP_OWNER={}".format(self._get_user()),)
        extra_args = {
            'Tagging': self._convert_tags_to_url_string(tags)
        }
        TransferManager.ALLOWED_UPLOAD_ARGS.append('Tagging')
        file_stream = urlopen(source_key)
        if StorageItemManager.show_progress(quiet, size):
            self.bucket.upload_fileobj(file_stream, destination_key, Callback=ProgressPercentage(relative_path, size),
                                       ExtraArgs=extra_args)
        else:
            self.bucket.upload_fileobj(file_stream, destination_key, ExtraArgs=extra_args)


class TransferFromHttpOrFtpToLocal(object):

    CHUNK_SIZE = 16 * 1024

    def __init__(self):
        pass

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False,
                 quiet=False, size=None, tags=(), skip_existing=False):
        """
        Transfers data from remote resource (only ftp(s) or http(s) protocols supported) to local file system.
        :param source_wrapper: wrapper for ftp or http resource
        :type source_wrapper: FtpSourceWrapper or HttpSourceWrapper
        :param destination_wrapper: wrapper for local file
        :type destination_wrapper: LocalFileSystemWrapper
        :param path: full path to remote file
        :param relative_path: relative path
        :param clean: remove source files (unsupported for this kind of transfer)
        :param quiet: True if quite mode specified
        :param size: the size of the source file
        :param tags: not needed for this kind of transfer
        :param skip_existing: indicates --skip_existing option
        """
        if clean:
            raise AttributeError("Cannot perform 'mv' operation due to deletion remote files "
                                 "is not supported for ftp/http sources.")
        if path:
            source_key = path
        else:
            source_key = source_wrapper.path
        if destination_wrapper.path.endswith(os.path.sep):
            destination_key = os.path.join(destination_wrapper.path, relative_path)
        else:
            destination_key = destination_wrapper.path
        if skip_existing:
            remote_size = size
            local_size = StorageItemManager.get_local_file_size(destination_key)
            if local_size is not None and remote_size == local_size:
                if not quiet:
                    click.echo('Skipping file %s since it exists in the destination %s' % (source_key, destination_key))
                return
        dir_path = os.path.dirname(destination_key)
        if not os.path.exists(dir_path):
            os.makedirs(dir_path)
        file_stream = urlopen(source_key)
        if StorageItemManager.show_progress(quiet, size):
            progress_bar = ProgressPercentage(relative_path, size)
        with open(destination_key, 'wb') as f:
            while True:
                chunk = file_stream.read(self.CHUNK_SIZE)
                if not chunk:
                    break
                f.write(chunk)
                if StorageItemManager.show_progress(quiet, size):
                    progress_bar.__call__(len(chunk))
        file_stream.close()


class TransferBetweenBucketsManager(StorageItemManager):

    def __init__(self, session, bucket):
        super(TransferBetweenBucketsManager, self).__init__(session, bucket=bucket)

    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False,
                 quiet=False, size=None, tags=(), skip_existing=False):
        # checked is bucket and file
        source_bucket = source_wrapper.bucket.path
        destination_key = S3BucketOperations.normalize_s3_path(destination_wrapper, relative_path)
        copy_source = {
            'Bucket': source_bucket,
            'Key': path
        }
        if skip_existing:
            from_size = self.get_s3_file_size(source_bucket, path)
            to_size = self.get_s3_file_size(destination_wrapper.bucket.path, destination_key)
            if to_size is not None and to_size == from_size:
                if not quiet:
                    click.echo('Skipping file %s since it exists in the destination %s' % (path, destination_key))
                return
        object_tags = ObjectTaggingManager.get_object_tagging(ObjectTaggingManager(self.session, source_bucket), path)
        if not tags and object_tags:
            tags = self.convert_object_tags(object_tags)
        if not self.has_required_tag(tags, 'CP_SOURCE'):
            tags += ('CP_SOURCE=s3://{}/{}'.format(source_bucket, path),)
        if not self.has_required_tag(tags, 'CP_OWNER'):
            tags += ('CP_OWNER={}'.format(self._get_user()),)
        TransferManager.ALLOWED_COPY_ARGS.append('TaggingDirective')
        TransferManager.ALLOWED_COPY_ARGS.append('Tagging')
        extra_args = {
            'TaggingDirective': 'REPLACE',
            'Tagging': self._convert_tags_to_url_string(tags)
        }
        if StorageItemManager.show_progress(quiet, size):
            self.bucket.copy(copy_source, destination_key, Callback=ProgressPercentage(relative_path, size),
                             ExtraArgs=extra_args)
        else:
            self.bucket.copy(copy_source, destination_key, ExtraArgs=extra_args)
        if clean:
            source_wrapper.delete_item(path)

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


class RestoreManager(StorageItemManager):

    def __init__(self, bucket, session):
        super(RestoreManager, self).__init__(session)
        self.bucket = bucket

    def restore_version(self, version):
        client = self.session.client('s3', config=S3BucketOperations.get_proxy_config())
        bucket = self.bucket.bucket.path
        if version:
            current_item = self.load_item(bucket, client)
            if current_item['VersionId'] == version:
                click.echo('Version "{}" is already the latest version'.format(version), err=True)
                return
            try:
                client.copy_object(Bucket=bucket, Key=self.bucket.path,
                                   CopySource=dict(Bucket=bucket, Key=self.bucket.path, VersionId=version))
            except ClientError as e:
                if 'delete marker' in e.message:
                    text = "Cannot restore a delete marker"
                elif 'Invalid version' in e.message:
                    text = 'Version "{}" doesn\'t exist.'.format(version)
                else:
                    text = e.message
                raise RuntimeError(text)
        else:
            item = self.load_delete_marker(bucket, self.bucket.path, client)
            delete_us = dict(Objects=[])
            delete_us['Objects'].append(dict(Key=item['Key'], VersionId=item['VersionId']))
            client.delete_objects(Bucket=bucket, Delete=delete_us)

    def load_item(self, bucket, client):
        try:
            item = client.head_object(Bucket=bucket, Key=self.bucket.path)
        except ClientError as e:
            if 'Not Found' in e.message:
                return self.load_delete_marker(bucket, self.bucket.path, client)
            raise RuntimeError('Requested file "{}" doesn\'t exist. {}.'.format(self.bucket.path, e.message))
        if item is None:
            raise RuntimeError('Path "{}" doesn\'t exist'.format(self.bucket.path))
        return item

    def load_delete_marker(self, bucket, path, client):
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
                if 'IsLatest' in item and item['IsLatest']:
                    return item
        raise RuntimeError('Latest version in the buckets is not a delete marker. Please specify "--version" parameter.')


class DeleteManager(StorageItemManager):
    def __init__(self, bucket, session):
        super(DeleteManager, self).__init__(session)
        self.bucket = bucket

    def delete_items(self, relative_path, recursive=False, exclude=[], include=[], version=None, hard_delete=False):
        client = self.session.client('s3', config=S3BucketOperations.get_proxy_config())
        delimiter = S3BucketOperations.S3_PATH_SEPARATOR
        bucket = self.bucket.bucket.path
        if relative_path:
            prefix = relative_path
            if prefix.startswith(delimiter):
                prefix = prefix[1:]
        else:
            prefix = delimiter

        if not recursive and not hard_delete:
            delete_us = dict(Objects=[])
            if version is not None:
                delete_us['Objects'].append(dict(Key=prefix, VersionId=version))
            else:
                delete_us['Objects'].append(dict(Key=prefix))
            client.delete_objects(Bucket=bucket, Delete=delete_us)
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
                self.process_listing(page, 'Contents', delete_us, delimiter, exclude, include, prefix)
                self.process_listing(page, 'Versions', delete_us, delimiter, exclude, include, prefix, versions=True)
                self.process_listing(page, 'DeleteMarkers', delete_us, delimiter, exclude, include, prefix, versions=True)
                # flush once aws limit reached
                if len(delete_us['Objects']) >= 1000:
                    client.delete_objects(Bucket=bucket, Delete=delete_us)
                    delete_us = dict(Objects=[])
            # flush rest
            if len(delete_us['Objects']):
                client.delete_objects(Bucket=bucket, Delete=delete_us)

    def process_listing(self, page, name, delete_us, delimiter, exclude, include, prefix, versions=False):
        if name in page:
            if not versions:
                single_file_item = self.get_single_file_item(name, page, prefix)
                if single_file_item:
                    self.add_item_to_deletion(single_file_item, prefix, delimiter, include, exclude, versions, delete_us)
                    return
            for item in page[name]:
                if item is None:
                    break
                if self.expect_to_delete_file(prefix, item):
                    continue
                self.add_item_to_deletion(item, prefix, delimiter, include, exclude, versions, delete_us)

    def get_single_file_item(self, name, page, prefix):
        single_file_item = None
        for item in page[name]:
            if item is None:
                break
            if not prefix.endswith(S3BucketOperations.S3_PATH_SEPARATOR) and item['Key'] == prefix:
                single_file_item = item
                break
        return single_file_item

    def expect_to_delete_file(self, prefix, item):
        return not prefix.endswith(S3BucketOperations.S3_PATH_SEPARATOR) and not item['Key'] == prefix \
                        and not item['Key'].startswith(prefix + S3BucketOperations.S3_PATH_SEPARATOR)

    @classmethod
    def add_item_to_deletion(cls, item, prefix, delimiter, include, exclude, versions, delete_us):
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


class ListingManager(StorageItemManager):
    DEFAULT_PAGE_SIZE = 100

    def __init__(self, bucket, session, show_versions=False):
        super(ListingManager, self).__init__(session)
        self.bucket = bucket
        self.show_versions = show_versions

    def list_items(self, relative_path=None, recursive=False, page_size=None, show_all=False):
        delimiter = S3BucketOperations.S3_PATH_SEPARATOR
        client = self.session.client('s3', config=S3BucketOperations.get_proxy_config())
        operation_parameters = {
            'Bucket': self.bucket.bucket.path
        }
        if not page_size:
            page_size = self.DEFAULT_PAGE_SIZE
        if not show_all:
            operation_parameters['PaginationConfig'] = {
                    'MaxItems': page_size,
                    'PageSize': page_size
                 }
        if not recursive:
            operation_parameters['Delimiter'] = delimiter
        prefix = S3BucketOperations.get_prefix(delimiter, relative_path)
        if relative_path:
            operation_parameters['Prefix'] = prefix

        if self.show_versions:
            return self.list_versions(client, prefix, operation_parameters, recursive)
        else:
            return self.list_objects(client, prefix, operation_parameters, recursive)

    def list_versions(self, client, prefix,  operation_parameters, recursive):
        paginator = client.get_paginator('list_object_versions')
        page_iterator = paginator.paginate(**operation_parameters)
        items = []
        item_keys = collections.OrderedDict()
        for page in page_iterator:
            if 'CommonPrefixes' in page:
                for folder in page['CommonPrefixes']:
                    name = S3BucketOperations.get_item_name(folder['Prefix'], prefix=prefix)
                    items.append(self.get_folder_object(name))
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

    def list_objects(self, client, prefix, operation_parameters, recursive):
        paginator = client.get_paginator('list_objects_v2')
        page_iterator = paginator.paginate(**operation_parameters)
        items = []
        for page in page_iterator:
            if 'CommonPrefixes' in page:
                for folder in page['CommonPrefixes']:
                    name = S3BucketOperations.get_item_name(folder['Prefix'], prefix=prefix)
                    items.append(self.get_folder_object(name))
            if 'Contents' in page:
                for file in page['Contents']:
                    name = self.get_file_name(file, prefix, recursive)
                    item = self.get_file_object(file, name)
                    items.append(item)
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


class ObjectTaggingManager(StorageItemManager):

    def __init__(self, session, bucket):
        super(ObjectTaggingManager, self).__init__(session)
        self.bucket = bucket

    def get_object_tagging(self, source):
        client = self.session.client('s3', config=S3BucketOperations.get_proxy_config())
        response = client.get_object_tagging(
            Bucket=self.bucket,
            Key=source
        )
        return response['TagSet']


class S3BucketOperations(object):

    S3_ENDPOINT_URL = 'https://s3.amazonaws.com'
    S3_PATH_SEPARATOR = '/'
    __config__ = None

    @classmethod
    def get_proxy_config(cls):
        if cls.__config__ is None:
            cls.__config__ = Config.instance()
        if cls.__config__.proxy is None:
            return None
        else:
            return AwsConfig(proxies=cls.__config__.resolve_proxy(target_url=cls.S3_ENDPOINT_URL))

    @classmethod
    def get_operation_manager(cls, source_wrapper, destination_wrapper, command):
        if source_wrapper.is_s3() and destination_wrapper.is_s3():
            source_id = source_wrapper.bucket.identifier
            destination_id = destination_wrapper.bucket.identifier
            session = cls.assumed_session(source_id, destination_id, command)
            # replace session to be able to delete source for move
            source_wrapper.session = session
            destination_bucket = destination_wrapper.bucket.path
            return TransferBetweenBucketsManager(session, destination_bucket)
        elif source_wrapper.is_s3() and destination_wrapper.is_local():
            source_id = source_wrapper.bucket.identifier
            session = cls.assumed_session(source_id, None, command)
            # replace session to be able to delete source for move
            source_wrapper.session = session
            source_bucket = source_wrapper.bucket.path
            return DownloadManager(session, source_bucket)
        elif source_wrapper.is_local() and destination_wrapper.is_s3():
            destination_id = destination_wrapper.bucket.identifier
            session = cls.assumed_session(None, destination_id, command)
            destination_bucket = destination_wrapper.bucket.path
            return UploadManager(session, destination_bucket)
        elif (source_wrapper.is_ftp() or source_wrapper.is_http()) and destination_wrapper.is_s3():
            destination_id = destination_wrapper.bucket.identifier
            session = cls.assumed_session(None, destination_id, command)
            destination_bucket = destination_wrapper.bucket.path
            return TransferFromHttpOrFtpToS3Manager(session, destination_bucket)
        elif (source_wrapper.is_ftp() or source_wrapper.is_http()) and destination_wrapper.is_local():
            return TransferFromHttpOrFtpToLocal()
        else:
            raise RuntimeError("Transferring files between local paths is not supported.")

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
        client = session.client('s3', config=S3BucketOperations.get_proxy_config())
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
        if path:
            prefix = path
            if prefix.startswith(delimiter):
                prefix = prefix[1:]
        else:
            prefix = delimiter
        return prefix

    @classmethod
    def get_item_name(cls, param, prefix=None):
        possible_folder_name = prefix if prefix.endswith(S3BucketOperations.S3_PATH_SEPARATOR) else \
            prefix + S3BucketOperations.S3_PATH_SEPARATOR
        if prefix and param.startswith(prefix) and param != possible_folder_name and param != prefix:
            if not param == prefix:
                splitted = prefix.split(S3BucketOperations.S3_PATH_SEPARATOR)
                return splitted[len(splitted) - 1] + param[len(prefix):]
            else:
                return param[len(prefix):]
        elif not param.endswith(S3BucketOperations.S3_PATH_SEPARATOR) and param == prefix:
            return os.path.basename(param)
        else:
            return param

    @classmethod
    def get_items(cls, storage_wrapper, session=None):
        if session is None:
            session = cls.assumed_session(storage_wrapper.bucket.identifier, None, 'cp')

        delimiter = S3BucketOperations.S3_PATH_SEPARATOR
        client = session.client('s3', config=S3BucketOperations.get_proxy_config())
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
        client = session.client('s3', config=S3BucketOperations.get_proxy_config())
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
        session = cls.assumed_session(source_wrapper.bucket.identifier, None, 'cp', versioning=show_versions)
        return ListingManager(source_wrapper, session, show_versions=show_versions)

    @classmethod
    def get_delete_manager(cls, source_wrapper, versioning=False):
        session = cls.assumed_session(source_wrapper.bucket.identifier, None, 'mv', versioning=versioning)
        return DeleteManager(source_wrapper, session)

    @classmethod
    def get_restore_manager(cls, source_wrapper):
        session = cls.assumed_session(source_wrapper.bucket.identifier, None, 'mv', versioning=True)
        return RestoreManager(source_wrapper, session)

    @classmethod
    def delete_item(cls, storage_wrapper, relative_path, session=None):
        if session is None:
            session = cls.assumed_session(storage_wrapper.bucket.identifier, None, 'mv')
        client = session.client('s3', config=S3BucketOperations.get_proxy_config())
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
        if destination_wrapper.path.endswith(S3BucketOperations.S3_PATH_SEPARATOR) or not destination_wrapper.is_file():
            if os.path.sep != S3BucketOperations.S3_PATH_SEPARATOR:
                relative_path = relative_path.replace(os.path.sep, S3BucketOperations.S3_PATH_SEPARATOR)
            skip_separator = destination_wrapper.path.endswith(S3BucketOperations.S3_PATH_SEPARATOR)
            if destination_wrapper.path:
                if skip_separator:
                    destination_key = destination_wrapper.path + relative_path
                else:
                    destination_key = destination_wrapper.path + S3BucketOperations.S3_PATH_SEPARATOR + relative_path
            else:
                destination_key = relative_path
        else:
            destination_key = destination_wrapper.path
        result = cls.remove_double_slashes(destination_key)
        if result.startswith('/'):
            return result[1:]
        else:
            return result

    @classmethod
    def assumed_session(cls, source_bucket_id, destination_bucket_id, command, versioning=False):
        def refresh():
            credentials = DataStorage.get_temporary_credentials(source_bucket_id, destination_bucket_id, command,
                                                                versioning=versioning)
            return dict(
                access_key=credentials.access_key_id,
                secret_key=credentials.secret_key,
                token=credentials.session_token,
                expiry_time=credentials.expiration,
                region_name=credentials.region)

        fresh_metadata = refresh()
        session_credentials = RefreshableCredentials.create_from_metadata(
            metadata=fresh_metadata,
            refresh_using=refresh,
            method='sts-assume-role')

        s = get_session()
        s._credentials = session_credentials
        return Session(botocore_session=s, region_name=fresh_metadata['region_name'])

    @classmethod
    def get_full_path(cls, path, param):
        delimiter = cls.S3_PATH_SEPARATOR
        return cls.remove_double_slashes(path + delimiter + param)

    @classmethod
    def remove_double_slashes(cls, path):
        return re.sub('/+', '/', path)

