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

import logging
import os
from abc import abstractmethod, ABCMeta
from ftplib import FTP, error_temp

from future.standard_library import install_aliases

from ..utilities.encoding_utilities import to_unicode, to_string

install_aliases()

from urllib.parse import urlparse

import click
import requests
import sys
from ..api.data_storage import DataStorage
from ..config import ConfigNotFoundError
from ..utilities.storage.s3 import S3BucketOperations
from ..utilities.storage.local import LocalOperations
from ..utilities.storage.azure import AzureListingManager, AzureDeleteManager, AzureBucketOperations
from ..utilities.storage.gs import GsRestoreManager, GsListingManager, GsDeleteManager, GsBucketOperations
from ..utilities.storage.common import StorageOperations
from .data_storage_wrapper_type import WrapperType
import shutil
from bs4 import BeautifulSoup, SoupStrainer
import posixpath

try:
    from urllib.request import urlopen  # Python 3
except ImportError:
    from urllib2 import urlopen  # Python 2

FILE = 'File'
FOLDER = 'Folder'


class AllowedSymlinkValues(object):
    FOLLOW = 'follow'
    SKIP = 'skip'
    FILTER = 'filter'


class LocationWrapper(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def get_type(self):
        pass

    @abstractmethod
    def get_path(self):
        pass

    @abstractmethod
    def is_file(self):
        pass

    @abstractmethod
    def get_input_stream(self, path):
        pass

    @abstractmethod
    def get_output_stream(self, path):
        pass

    @abstractmethod
    def exists(self):
        pass

    @abstractmethod
    def is_empty(self, relative=None):
        pass

    @abstractmethod
    def get_items(self, quiet=False):
        pass

    @abstractmethod
    def delete_item(self, relative_path):
        pass


class DataStorageWrapper(object):

    _transfer_manager_suppliers = {
        (WrapperType.S3, WrapperType.S3): S3BucketOperations.get_transfer_between_buckets_manager,
        (WrapperType.S3, WrapperType.LOCAL): S3BucketOperations.get_download_manager,
        (WrapperType.LOCAL, WrapperType.S3): S3BucketOperations.get_upload_manager,
        (WrapperType.FTP, WrapperType.S3): S3BucketOperations.get_upload_stream_manager,
        (WrapperType.HTTP, WrapperType.S3): S3BucketOperations.get_upload_stream_manager,
        (WrapperType.STREAM, WrapperType.S3): S3BucketOperations.get_upload_stream_manager,
        (WrapperType.S3, WrapperType.STREAM): S3BucketOperations.get_download_stream_manager,

        (WrapperType.AZURE, WrapperType.AZURE): AzureBucketOperations.get_transfer_between_buckets_manager,
        (WrapperType.AZURE, WrapperType.LOCAL): AzureBucketOperations.get_download_manager,
        (WrapperType.LOCAL, WrapperType.AZURE): AzureBucketOperations.get_upload_manager,
        (WrapperType.FTP, WrapperType.AZURE): AzureBucketOperations.get_transfer_from_http_or_ftp_manager,
        (WrapperType.HTTP, WrapperType.AZURE): AzureBucketOperations.get_transfer_from_http_or_ftp_manager,

        (WrapperType.GS, WrapperType.GS): GsBucketOperations.get_transfer_between_buckets_manager,
        (WrapperType.GS, WrapperType.LOCAL): GsBucketOperations.get_download_manager,
        (WrapperType.LOCAL, WrapperType.GS): GsBucketOperations.get_upload_manager,
        (WrapperType.FTP, WrapperType.GS): GsBucketOperations.get_upload_stream_manager,
        (WrapperType.HTTP, WrapperType.GS): GsBucketOperations.get_upload_stream_manager,
        (WrapperType.STREAM, WrapperType.GS): GsBucketOperations.get_upload_stream_manager,
        (WrapperType.GS, WrapperType.STREAM): GsBucketOperations.get_download_stream_manager,

        (WrapperType.FTP, WrapperType.LOCAL): LocalOperations.get_transfer_from_http_or_ftp_manager,
        (WrapperType.HTTP, WrapperType.LOCAL): LocalOperations.get_transfer_from_http_or_ftp_manager
    }

    def __init__(self, path):
        self.path = path
        self.items = []
        self.path_separator = StorageOperations.PATH_SEPARATOR

    @classmethod
    def get_wrapper(cls, uri, symlinks=None):
        if uri == '-':
            return StandardStreamWrapper()
        parsed = urlparse(uri)
        if not parsed.scheme or not parsed.netloc:
            return LocalFileSystemWrapper(uri, symlinks)
        if parsed.scheme.lower() in ['ftp', 'ftps'] and os.getenv('ftp_proxy'):
            return HttpSourceWrapper(uri)
        if parsed.scheme.lower() in ['ftp', 'ftps']:
            return FtpSourceWrapper(parsed.scheme, parsed.netloc, parsed.path, uri)
        if parsed.scheme.lower() in ['http', 'https']:
            return HttpSourceWrapper(uri)
        return cls.get_cloud_wrapper(uri)

    @classmethod
    def get_cloud_wrapper(cls, uri, versioning=False):
        root_bucket, original_path, _ = DataStorage.load_from_uri(uri)
        relative_path = original_path if original_path != '/' else ''
        return cls.__get_storage_wrapper(root_bucket, relative_path, versioning=versioning)

    @classmethod
    def get_cloud_wrapper_for_bucket(cls, bucket_model, relative_path):
        return cls.__get_storage_wrapper(bucket_model, relative_path, init=False)

    @classmethod
    def __get_storage_wrapper(cls, bucket, relative_path, *args, **kwargs):
        _suppliers = {
            WrapperType.S3: S3BucketWrapper.build_wrapper,
            WrapperType.AZURE: AzureBucketWrapper.build_wrapper,
            WrapperType.GS: GsBucketWrapper.build_wrapper,
        }
        if bucket.type in _suppliers:
            supplier = _suppliers[bucket.type]
            return supplier(bucket, relative_path, *args, **kwargs)
        else:
            raise RuntimeError('There is no data storage wrapper for %s storage type.' % bucket.type)

    @classmethod
    def get_operation_manager(cls, source_wrapper, destination_wrapper, events, command):
        manager_types = source_wrapper.get_type(), destination_wrapper.get_type()
        if manager_types in DataStorageWrapper._transfer_manager_suppliers:
            supplier = DataStorageWrapper._transfer_manager_suppliers[manager_types]
            return supplier(source_wrapper, destination_wrapper, events, command)
        else:
            raise RuntimeError('Transferring files between the following storage types %s -> %s is not supported.'
                               % manager_types)

    @classmethod
    def get_data_storage_item_path_info(cls, path, buckets=None):
        error = None
        if buckets is None or len(buckets) == 0:
            buckets = []
            try:
                buckets = list(DataStorage.list())
            except ConfigNotFoundError as config_not_found_error:
                error = str(config_not_found_error)
            except requests.exceptions.RequestException as http_error:
                error = 'Http error: {}'.format(str(http_error))
            except RuntimeError as runtime_error:
                error = 'Error: {}'.format(str(runtime_error))
            except ValueError as value_error:
                error = 'Error: {}'.format(str(value_error))
        if error:
            return error, None, None, None
        url = urlparse(path)
        if url.scheme.lower() not in WrapperType.cloud_schemes():
            return "'%s' scheme is not supported" % url.scheme, None, None, None
        parts = url.path.split('/')
        current_bucket = None
        for bucket_model in buckets:
            if bucket_model.path is not None \
                    and bucket_model.type in WrapperType.cloud_types() \
                    and path.startswith(bucket_model.path.lower()):
                if current_bucket is None or len(bucket_model.path) > len(current_bucket.path):
                    current_bucket = bucket_model
                break
        if current_bucket is None:
            return 'Storage \'{}\' was not found'.format(path), None, None, None
        delimiter = '/'
        if current_bucket.delimiter is not None:
            delimiter = current_bucket.delimiter
        relative_path = url.path
        item_type = FILE
        if len(parts[len(parts) - 1].split('.')) == 1:
            item_type = FOLDER
        return None, current_bucket.identifier, relative_path, item_type, delimiter

    def get_type(self):
        return None

    def get_path(self):
        return self.path

    def is_file(self):
        return False

    def get_input_stream(self, path):
        return urlopen(path)

    def get_output_stream(self, path):
        raise RuntimeError('Output stream is not supported for {}'.format(self.get_type()))

    def exists(self):
        return False

    def is_empty(self, relative=None):
        return False

    def is_local(self):
        return self.get_type() in [WrapperType.LOCAL, WrapperType.STREAM]

    def fetch_items(self):
        self.items = self.get_items()

    def get_items(self, quiet=False):
        return []

    def get_folders_list(self):
        return map(lambda i: (i[1], i[2]), [item for item in self.items if item[0] == FOLDER])

    def get_files_list(self):
        return map(lambda i: (i[1], i[2]), [item for item in self.items if item[0] == FILE])

    def create_folder(self, relative_path):
        pass

    def download_file(self, source_uri, relative_path):
        pass

    def download_single_file(self, source_uri, relative_path):
        pass

    def get_file_download_uri(self, relative_path):
        return None

    def delete_item(self, relative_path):
        pass

    def path_with_trailing_separator(self):
        return self.path \
            if self.path.endswith(self.path_separator) \
            else self.path + self.path_separator


class CloudDataStorageWrapper(DataStorageWrapper):
    __metaclass__ = ABCMeta

    def __init__(self, bucket, path):
        super(CloudDataStorageWrapper, self).__init__(path)
        self.bucket = bucket
        self.is_file_flag = False
        self.exists_flag = False
        # case when the root bucket folder is passed
        if len(path) == 0 and self.bucket.identifier:
            self.exists_flag = True
            self.is_file_flag = False

    def is_file(self):
        return self.is_file_flag

    def exists(self):
        return self.exists_flag

    def get_items(self, quiet=False):
        return self.get_list_manager().get_items(self.path)

    def is_empty(self, relative=None):
        if not self.exists():
            return True
        if self.is_file():
            return False
        if relative:
            delimiter = StorageOperations.PATH_SEPARATOR
            path = self.path.rstrip(delimiter) + delimiter + relative
        else:
            path = self.path
        return not self.get_list_manager().folder_exists(path)

    @abstractmethod
    def get_type(self):
        pass

    @abstractmethod
    def get_restore_manager(self, events):
        pass

    @abstractmethod
    def get_list_manager(self, show_versions=False):
        pass

    @abstractmethod
    def get_delete_manager(self, events, versioning):
        pass


class S3BucketWrapper(CloudDataStorageWrapper):

    def __init__(self, bucket, path):
        super(S3BucketWrapper, self).__init__(bucket, path)
        # parse root bucket from path
        self.bucket.path = bucket.path.split(self.path_separator)[0]
        self.is_empty_flag = True
        self.session = None

    @classmethod
    def build_wrapper(cls, root_bucket, relative_path, init=True, *args, **kwargs):
        wrapper = S3BucketWrapper(root_bucket, relative_path)
        if init:
            S3BucketOperations.init_wrapper(wrapper, *args, **kwargs)
        return wrapper

    def get_type(self):
        return WrapperType.S3

    def is_empty(self, relative=None):
        if not self.exists():
            return True
        if self.is_file():
            return False
        if not self.is_empty_flag and relative:
            return not S3BucketOperations.path_exists(self, relative, session=self.session)
        return self.is_empty_flag

    def get_file_download_uri(self, relative_path):
        download_url_model = DataStorage.generate_download_url(self.bucket.identifier, relative_path)
        if download_url_model is not None:
            return download_url_model.url
        return None

    def delete_item(self, relative_path):
        S3BucketOperations.delete_item(self, relative_path, session=self.session)

    def get_restore_manager(self, events):
        return S3BucketOperations.get_restore_manager(self, events)

    def get_list_manager(self, show_versions=False):
        return S3BucketOperations.get_list_manager(self, show_versions=show_versions)

    def get_delete_manager(self, events, versioning):
        return S3BucketOperations.get_delete_manager(self, events, versioning)


class AzureBucketWrapper(CloudDataStorageWrapper):

    def __init__(self, bucket, path):
        super(AzureBucketWrapper, self).__init__(bucket, path)
        self.service = None

    @classmethod
    def build_wrapper(cls, root_bucket, relative_path, versioning=False, init=True):
        if versioning:
            raise RuntimeError('Versioning is not supported by AZURE cloud provider')
        wrapper = AzureBucketWrapper(root_bucket, relative_path)
        if init:
            StorageOperations.init_wrapper(wrapper, versioning=versioning)
        return wrapper

    def get_type(self):
        return WrapperType.AZURE

    def get_restore_manager(self, events):
        raise RuntimeError('Versioning is not supported by AZURE cloud provider')

    def get_list_manager(self, show_versions=False):
        if show_versions:
            raise RuntimeError('Versioning is not supported by AZURE cloud provider')
        return AzureListingManager(self._blob_service(read=True, write=False), self.bucket)

    def get_delete_manager(self, events, versioning):
        if versioning:
            raise RuntimeError('Versioning is not supported by AZURE cloud provider')
        return AzureDeleteManager(self._blob_service(read=True, write=True), events, self.bucket)

    def _blob_service(self, read, write):
        if write or not self.service:
            self.service = AzureBucketOperations.get_blob_service(self.bucket, read, write)
        return self.service


class GsBucketWrapper(CloudDataStorageWrapper):

    @classmethod
    def build_wrapper(cls, root_bucket, relative_path, init=True, *args, **kwargs):
        wrapper = GsBucketWrapper(root_bucket, relative_path)
        if init:
            StorageOperations.init_wrapper(wrapper, *args, **kwargs)
        return wrapper

    def get_type(self):
        return WrapperType.GS

    def get_restore_manager(self, events):
        return GsRestoreManager(self._storage_client(write=True, versioning=True), events, self)

    def get_list_manager(self, show_versions=False):
        return GsListingManager(self._storage_client(versioning=show_versions), self.bucket, show_versions)

    def get_delete_manager(self, events, versioning):
        return GsDeleteManager(self._storage_client(write=True, versioning=versioning), events, self.bucket)

    def _storage_client(self, read=True, write=False, versioning=False):
        return GsBucketOperations.get_client(self.bucket, read=read, write=write, versioning=versioning)


class StandardStreamWrapper(LocationWrapper, DataStorageWrapper):

    def __init__(self):
        super(StandardStreamWrapper, self).__init__('-')

    def get_type(self):
        return WrapperType.STREAM

    def get_path(self):
        return self.path

    def is_file(self):
        return True

    def get_input_stream(self, path):
        try:
            # python 3
            return sys.stdin.buffer
        except AttributeError:
            # python 2
            return sys.stdin

    def get_output_stream(self, path):
        try:
            # python 3
            return sys.stdout.buffer
        except AttributeError:
            # python 2
            return sys.stdout

    def exists(self):
        return True

    def is_empty(self, relative=None):
        return True

    def get_items(self, quiet=False):
        return [(FILE, self.path, self.path, 0)]

    def delete_item(self, relative_path):
        pass


class LocalFileSystemWrapper(DataStorageWrapper):

    def __init__(self, path, symlinks=None):
        super(LocalFileSystemWrapper, self).__init__(path)
        if self.path == ".":
            self.path = "./"
        if self.path.startswith("~"):
            self.path = os.path.join(os.path.expanduser('~'), self.path.strip("~/"))
        self.symlinks = symlinks
        self.path_separator = os.sep

    def exists(self):
        return os.path.exists(self.path)

    def is_file(self):
        return os.path.isfile(self.path)

    def get_type(self):
        return WrapperType.LOCAL

    def is_empty(self, relative=None):
        if not self.exists():
            return True
        if self.is_file():
            return False
        if relative:
            return not os.path.exists(os.path.join(self.path, relative))
        return not os.listdir(self.path)

    def get_items(self, quiet=False):
        logging.debug(u'Collecting paths...')

        def leaf_path(source_path):
            head, tail = os.path.split(source_path)
            return tail or os.path.basename(head)

        self.path = os.path.abspath(self.path)

        if os.path.isfile(self.path):
            if os.path.islink(self.path) and self.symlinks == AllowedSymlinkValues.SKIP:
                return
            yield (FILE, self.path, leaf_path(self.path), os.path.getsize(self.path))
        else:
            visited_symlinks = set()

            def list_items(path, parent, symlinks, visited_symlinks, root=False):
                logging.debug(u'Collecting paths under {}...'.format(path))
                path = to_unicode(path)
                parent = to_unicode(parent)
                for item in os.listdir(to_string(path)):
                    safe_item = to_unicode(item, replacing=True)
                    safe_absolute_path = os.path.join(path, safe_item)
                    logging.debug(u'Collecting path {}...'.format(safe_absolute_path))
                    try:
                        item = to_unicode(item)
                        absolute_path = os.path.join(path, item)
                    except UnicodeDecodeError:
                        err_msg = u'Skipping path with unmanageable unsafe characters {}...'.format(safe_absolute_path)
                        logging.warn(err_msg)
                        if not quiet:
                            click.echo(err_msg)
                        continue
                    symlink_target = None
                    if os.path.islink(to_string(absolute_path)) and symlinks != AllowedSymlinkValues.FOLLOW:
                        if symlinks == AllowedSymlinkValues.SKIP:
                            continue
                        if symlinks == AllowedSymlinkValues.FILTER:
                            symlink_target = os.readlink(to_string(absolute_path))
                            if symlink_target in visited_symlinks:
                                continue
                            else:
                                visited_symlinks.add(symlink_target)
                    relative_path = item
                    if not root and parent is not None:
                        relative_path = os.path.join(parent, item)
                    if os.path.isfile(to_string(absolute_path)):
                        logging.debug(u'Collected path {}.'.format(absolute_path))
                        yield (FILE, absolute_path, relative_path, os.path.getsize(to_string(absolute_path)))
                    elif os.path.isdir(to_string(absolute_path)):
                        for folder_item in list_items(absolute_path, relative_path, symlinks, visited_symlinks):
                            yield folder_item
                    if symlink_target and os.path.islink(to_string(path)) and symlink_target in visited_symlinks:
                        visited_symlinks.remove(symlink_target)
            for list_item in list_items(self.path, leaf_path(self.path), self.symlinks, visited_symlinks, root=True):
                yield list_item

    def create_folder(self, relative_path):
        absolute_path = os.path.join(self.path, relative_path)
        if os.path.isfile(absolute_path):
            return 'Error creating folder {}: a file with the same name already exists'.format(relative_path)
        if not os.path.isdir(absolute_path):
            os.makedirs(absolute_path)
        return None

    def download_single_file(self, source_uri, relative_path):
        if source_uri is None:
            click.echo('Download uri is empty for file {}'.format(relative_path), err=True)
            sys.exit(1)
        folder, file_name = os.path.split(self.path)
        file_name = file_name or relative_path
        full_path = os.path.join(folder, file_name)
        if not os.path.isdir(folder):
            os.makedirs(folder)
        if os.path.isdir(full_path):
            click.echo('Error copying file to \'{}\': a directory with the same name already exists'.format(full_path),
                       err=True)
            sys.exit(1)
        r = requests.get(source_uri, stream=True)
        content_length = None
        if 'content-length' in r.headers:
            content_length = int(r.headers['content-length'])
        if content_length is None:
            click.echo('{}...'.format(relative_path), nl=False)
            with open(full_path, 'wb') as f:
                for chunk in r.iter_content(chunk_size=1024):
                    if chunk:  # filter out keep-alive new chunks
                        f.write(chunk)
            click.echo('done.')
        else:
            estimated_bytes = content_length
            click.echo('{}:'.format(relative_path))
            with click.progressbar(length=content_length,
                                   show_eta=False,
                                   label=relative_path,
                                   bar_template='[%(bar)s]  %(info)s  %(label)s') as progress_bar:
                with open(full_path, 'wb') as f:
                    for chunk in r.iter_content(chunk_size=1024):
                        if chunk:  # filter out keep-alive new chunks
                            f.write(chunk)
                            if len(chunk) > 0:
                                estimated_bytes -= len(chunk)
                                progress_bar.update(len(chunk))
                    progress_bar.update(estimated_bytes)
        pass

    def download_file(self, source_uri, relative_path):
        if source_uri is None:
            click.echo('Download uri is empty for file {}'.format(relative_path), err=True)
            sys.exit(1)
        r = requests.get(source_uri, stream=True)
        content_length = None
        if 'content-length' in r.headers:
            content_length = int(r.headers['content-length'])
        if content_length is None:
            click.echo('{}...'.format(relative_path), nl=False)
            with open(os.path.join(self.path, relative_path), 'wb') as f:
                for chunk in r.iter_content(chunk_size=1024):
                    if chunk:  # filter out keep-alive new chunks
                        f.write(chunk)
            click.echo('done.')
        else:
            estimated_bytes = content_length
            with click.progressbar(length=content_length,
                                   show_eta=False,
                                   label=relative_path,
                                   bar_template='[%(bar)s]  %(info)s  %(label)s') as progress_bar:
                with open(os.path.join(self.path, relative_path), 'wb') as f:
                    for chunk in r.iter_content(chunk_size=1024):
                        if chunk:  # filter out keep-alive new chunks
                            f.write(chunk)
                            if len(chunk) > 0:
                                estimated_bytes -= len(chunk)
                                progress_bar.update(len(chunk))
                    progress_bar.update(estimated_bytes)

    def delete_item(self, relative_path):
        path = to_string(os.path.join(self.path, relative_path))
        if os.path.isfile(path) and os.path.exists(path):
            os.remove(path)
        else:
            shutil.rmtree(path, ignore_errors=True)


class FtpSourceWrapper(DataStorageWrapper):

    def __init__(self, scheme, netloc, relative_path, url):
        super(FtpSourceWrapper, self).__init__(url)
        self.is_file_flag = False
        self.exists_flag = False
        self.relative_path = relative_path
        self.host = netloc
        self.scheme = scheme
        self.ftp = FTP(netloc)
        self.ftp.login()

    def get_type(self):
        return WrapperType.FTP

    def exists(self):
        try:
            self.ftp.nlst(self.relative_path)
            self.exists_flag = True
            return True
        except error_temp:
            self.exists_flag = False
            return False

    def is_file(self):
        self.is_file_flag = len(self.ftp.nlst(self.relative_path)) == 1
        return self.is_file_flag

    def get_items(self, quiet=False):
        return self._get_files([], self.relative_path)

    def _get_files(self, files, path):
        remote_files = self.ftp.nlst(path)
        if len(remote_files) == 1:
            self.ftp.voidcmd('TYPE I')  # change ftp connection to binary mode to get file size
            files.append((FILE, "%s://%s%s" % (self.scheme, self.host, path),
                          self._get_relative_path(path).strip("/"), self.ftp.size(path)))
        else:
            for file_path in remote_files:
                self._get_files(files, file_path)
        return files

    def _get_relative_path(self, path):
        if self.relative_path == path:
            return os.path.basename(path)
        else:
            return path[len(self.relative_path):]


class HttpSourceWrapper(DataStorageWrapper):

    def __init__(self, url):
        super(HttpSourceWrapper, self).__init__(url)
        self.host = urlparse(url).netloc
        self.is_file_flag = False
        self.exists_flag = False
        self.ftp_proxy_session = None
        if os.getenv("ftp_proxy"):
            self.ftp_proxy_session = requests.Session()
            self.ftp_proxy_session.mount('ftp://', requests.adapters.HTTPAdapter())

    def get_type(self):
        return WrapperType.HTTP

    def exists(self):
        head = self._head(self.path)
        self.exists_flag = head.status_code == 200
        return self.exists_flag

    def is_file(self):
        self.is_file_flag = self._is_downloadable()
        return self.is_file_flag

    def get_items(self, quiet=False):
        return self._get_files(self.path, [], [])

    def _head(self, path):
        return self.ftp_proxy_session.head(path) if self.ftp_proxy_session \
            else requests.head(path, allow_redirects=True)

    def _get(self, path):
        return self.ftp_proxy_session.get(path) if self.ftp_proxy_session else requests.get(path)

    def _is_downloadable(self):
        """
        Does the url contain a downloadable resource
        """
        head = self._head(self.path)
        header = head.headers
        content_type = header.get('content-type')
        return content_type is None or 'html' not in content_type.lower()

    def _get_files(self, path, files, processed_paths):
        if path in processed_paths:
            return files
        processed_paths.append(path)
        if self._is_downloadable():
            head = self._head(path)
            content_length = head.headers.get('Content-Length')
            files.append((FILE, str(path), self._get_relative_path(path).strip("/"),
                          content_length if content_length is None else int(content_length)))
        else:
            response = self._get(path)
            soup = BeautifulSoup(response.content, "html.parser", parse_only=SoupStrainer('a'))
            page_paths = set([link['href'] for link in soup.findAll('a', href=True)])
            for page_path in page_paths:
                current_host = urlparse(page_path).netloc
                if current_host:
                    if page_path in processed_paths:
                        continue
                    if current_host != self.host:
                        processed_paths.append(page_path)
                        continue
                    head = self._head(page_path)
                    if head.status_code == 200:
                        self._get_files(page_path, files, processed_paths)
                else:
                    parsed = urlparse(path)
                    normalized_path = "%s://%s%s" % (parsed.scheme, parsed.netloc,
                                                     posixpath.normpath(os.path.join(parsed.path, page_path)))

                    if normalized_path in processed_paths:
                        continue
                    if not normalized_path.startswith(self.path):
                        processed_paths.append(normalized_path)
                        continue
                    head = self._head(normalized_path)
                    if head.status_code == 200:
                        self._get_files(normalized_path, files, processed_paths)
                    else:
                        processed_paths.append(normalized_path)
        return files

    def _get_relative_path(self, path):
        if self.path == path:
            return os.path.basename(path)
        else:
            return path[len(self.path):]
