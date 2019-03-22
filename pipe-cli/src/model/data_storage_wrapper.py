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

import os
from ftplib import FTP, error_temp

from future.standard_library import install_aliases
install_aliases()

from urllib.parse import urlparse

import click
import requests
import sys
from ..api.data_storage import DataStorage
from ..config import ConfigNotFoundError
from ..utilities.bucket_operations_manager import S3BucketOperations
import shutil
from bs4 import BeautifulSoup, SoupStrainer
import posixpath

FILE = 'File'
FOLDER = 'Folder'


class WrapperType(object):
    LOCAL = 'LOCAL'
    S3 = 'S3'
    FTP = 'FTP'
    HTTP = 'HTTP'


class DataStorageWrapper(object):
    def __init__(self, path):
        self.path = path
        self.items = []

    @classmethod
    def get_wrapper(cls, uri):
        parsed = urlparse(uri)
        if not parsed.scheme or not parsed.netloc:
            return LocalFileSystemWrapper(uri)
        if parsed.scheme.lower() == 'ftp' or parsed.scheme.lower() == 'ftps':
            return HttpSourceWrapper(uri) if os.getenv("ftp_proxy") \
                else FtpSourceWrapper(parsed.scheme, parsed.netloc, parsed.path, uri)
        if parsed.scheme.lower() == 'http' or parsed.scheme.lower() == 'https':
            return HttpSourceWrapper(uri)
        else:
            return cls.get_cloud_wrapper(uri)

    @classmethod
    def get_cloud_wrapper(cls, uri, versioning=False):
        root_bucket, original_path = DataStorage.load_from_uri(uri)
        relative_path = original_path if original_path != '/' else ''
        wrapper = S3BucketWrapper(root_bucket, relative_path)
        return S3BucketOperations.init_wrapper(wrapper, versioning=versioning)

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
        if url.scheme.lower() != 'cp' and url.scheme.lower() != 's3':
            return "'%s' scheme is not supported" % url.scheme, None, None, None
        parts = url.path.split('/')
        current_bucket = None
        for bucket_model in buckets:
            if bucket_model.path is not None and bucket_model.type is not None and bucket_model.type.lower() == 's3' and \
                            bucket_model.path.lower() == url.netloc:
                current_bucket = bucket_model
                break
        if current_bucket is None:
            return 'Storage \'{}\' was not found'.format(url.netloc), None, None, None
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

    def is_file(self):
        return False

    def exists(self):
        return False

    def is_empty(self):
        return False

    def is_local(self):
        return self.get_type() == WrapperType.LOCAL

    def fetch_items(self):
        self.items = self.get_items()

    def get_items(self):
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

    def is_s3(self):
        return self.get_type() == WrapperType.S3

    def is_http(self):
        return self.get_type() == WrapperType.HTTP

    def is_ftp(self):
        return self.get_type() == WrapperType.FTP


class S3BucketWrapper(DataStorageWrapper):
    def __init__(self, bucket, path):
        super(S3BucketWrapper, self).__init__(path)
        self.bucket = bucket
        self.is_file_flag = False
        self.exists_flag = False
        self.is_empty_flag = True
        self.session = None
        # case when the root bucket folder is passed
        if len(path) == 0 and self.bucket.identifier:
            self.exists_flag = True
            self.is_file_flag = False
            self.is_empty_flag = False

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

    def is_file(self):
        return self.is_file_flag

    def exists(self):
        return self.exists_flag

    def get_items(self):
        return S3BucketOperations.get_items(self, session=self.session)

    def get_file_download_uri(self, relative_path):
        download_url_model = None
        try:
            download_url_model = DataStorage.generate_download_url(self.bucket.identifier, relative_path)
        except ConfigNotFoundError as config_not_found_error:
            click.echo(str(config_not_found_error), err=True)
        except requests.exceptions.RequestException as http_error:
            click.echo('Http error: {}'.format(str(http_error)), err=True)
        except RuntimeError as runtime_error:
            click.echo('Error: {}'.format(str(runtime_error)), err=True)
        except ValueError as value_error:
            click.echo('Error: {}'.format(str(value_error)), err=True)
        if download_url_model is not None:
            return download_url_model.url
        return None

    def delete_item(self, relative_path):
        S3BucketOperations.delete_item(self, relative_path, session=self.session)


class LocalFileSystemWrapper(DataStorageWrapper):

    def __init__(self, path):
        super(LocalFileSystemWrapper, self).__init__(path)
        if self.path == ".":
            self.path = "./"
        if self.path.startswith("~"):
            self.path = os.path.join(os.path.expanduser('~'), self.path.strip("~/"))

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

    def get_items(self):

        def leaf_path(source_path):
            head, tail = os.path.split(source_path)
            return tail or os.path.basename(head)

        self.path = os.path.abspath(self.path)

        if os.path.isfile(self.path):
            return [(FILE, self.path, leaf_path(self.path), os.path.getsize(self.path))]
        else:
            result = list()

            def list_items(path, parent, root=False):
                for item in os.listdir(path):
                    absolute_path = os.path.join(path, item)
                    relative_path = item
                    if not root and parent is not None:
                        relative_path = os.path.join(parent, item)
                    if os.path.isfile(absolute_path):
                        result.append((FILE, absolute_path, relative_path, os.path.getsize(absolute_path)))
                    elif os.path.isdir(absolute_path):
                        list_items(absolute_path, relative_path)
            list_items(self.path, leaf_path(self.path), root=True)
            return result

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
        path = os.path.join(self.path, relative_path)
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

    def get_items(self):
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

    def get_items(self):
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
