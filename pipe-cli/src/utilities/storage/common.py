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

import os
import re

from abc import abstractmethod, ABCMeta
from collections import namedtuple

import click
import jwt

from src.config import Config
from src.model.data_storage_wrapper_type import WrapperType

TransferResult = namedtuple('TransferResult', ['source_key', 'destination_key', 'destination_version', 'tags'])

UploadResult = namedtuple('UploadResult', ['source_key', 'destination_key', 'destination_version', 'tags'])


class StorageOperations:
    PATH_SEPARATOR = '/'
    DEFAULT_PAGE_SIZE = 100
    MAX_TAGS_NUMBER = 10
    MAX_KEY_LENGTH = 128
    MAX_VALUE_LENGTH = 256
    TAG_SHORTEN_SUFFIX = '...'
    TAGS_VALIDATION_PATTERN = re.compile('[^a-zA-Z0-9\s_.\-@:+/\\\]+')
    CP_SOURCE_TAG = 'CP_SOURCE'
    CP_OWNER_TAG = 'CP_OWNER'
    STORAGE_PATH = '%s://%s/%s'
    __config__ = None

    @classmethod
    def get_proxy_config(cls, target_url=None):
        if cls.__config__ is None:
            cls.__config__ = Config.instance()
        if cls.__config__.proxy is None:
            return None
        else:
            return cls.__config__.resolve_proxy(target_url=target_url)

    @classmethod
    def init_wrapper(cls, wrapper, versioning=False):
        delimiter = StorageOperations.PATH_SEPARATOR
        prefix = StorageOperations.get_prefix(wrapper.path)
        check_file = True
        if prefix.endswith(delimiter):
            prefix = prefix[:-1]
            check_file = False
        listing_manager = wrapper.get_list_manager(show_versions=versioning)
        for item in listing_manager.list_items(prefix, show_all=True):
            if prefix.endswith(item.name.rstrip(delimiter)) and (check_file or item.type == 'Folder'):
                wrapper.exists_flag = True
                wrapper.is_file_flag = item.type == 'File'
                break
        return wrapper

    @classmethod
    def get_prefix(cls, path, delimiter=PATH_SEPARATOR):
        if path:
            prefix = path
            if prefix.startswith(delimiter):
                prefix = prefix[1:]
        else:
            prefix = delimiter
        return prefix

    @classmethod
    def get_item_name(cls, path, prefix, delimiter=PATH_SEPARATOR):
        possible_folder_name = prefix if prefix.endswith(delimiter) else \
            prefix + StorageOperations.PATH_SEPARATOR
        if prefix and path.startswith(prefix) and path != possible_folder_name and path != prefix:
            if not path == prefix:
                splitted = prefix.split(StorageOperations.PATH_SEPARATOR)
                return splitted[len(splitted) - 1] + path[len(prefix):]
            else:
                return path[len(prefix):]
        elif not path.endswith(StorageOperations.PATH_SEPARATOR) and path == prefix:
            return os.path.basename(path)
        elif path == possible_folder_name:
            return os.path.basename(path.rstrip(StorageOperations.PATH_SEPARATOR)) + StorageOperations.PATH_SEPARATOR
        else:
            return path

    @classmethod
    def normalize_path(cls, destination_wrapper, relative_path, delimiter=PATH_SEPARATOR):
        if destination_wrapper.path.endswith(delimiter) or not destination_wrapper.is_file():
            if os.path.sep != delimiter:
                relative_path = relative_path.replace(os.path.sep, delimiter)
            skip_separator = destination_wrapper.path.endswith(delimiter)
            if destination_wrapper.path:
                if skip_separator:
                    destination_key = destination_wrapper.path + relative_path
                else:
                    destination_key = destination_wrapper.path + delimiter + relative_path
            else:
                destination_key = relative_path
        else:
            destination_key = destination_wrapper.path
        result = cls.remove_double_slashes(destination_key)
        if result.startswith(delimiter):
            return result[1:]
        else:
            return result

    @classmethod
    def remove_double_slashes(cls, path, delimiter=PATH_SEPARATOR):
        return re.sub(delimiter + '+', delimiter, path)

    @classmethod
    def show_progress(cls, quiet, size, lock=None):
        return not quiet and size is not None and size != 0 and lock is None

    @classmethod
    def get_local_file_size(cls, path):
        try:
            return os.path.getsize(path)
        except OSError:
            return None

    @classmethod
    def without_prefix(cls, string, prefix):
        if string.startswith(prefix):
            return string[len(prefix):]

    @classmethod
    def without_suffix(cls, string, suffix):
        if string.endswith(suffix):
            return string[:-len(suffix)]

    @classmethod
    def is_relative_path(cls, full_path, prefix, delimiter=PATH_SEPARATOR):
        relative_path = StorageOperations.without_prefix(full_path, prefix)
        return not relative_path or relative_path.startswith(delimiter)

    @classmethod
    def parse_tags(cls, tags):
        if not tags:
            return {}
        if len(tags) > cls.MAX_TAGS_NUMBER:
            raise ValueError(
                "Maximum allowed number of tags is {}. Provided {} tags.".format(cls.MAX_TAGS_NUMBER, len(tags)))
        tags_dict = {}
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
            if not value or value.isspace() or bool(StorageOperations.TAGS_VALIDATION_PATTERN.search(value)):
                click.echo("The tag value you have provided is invalid: %s. The tag %s will be skipped." % (value, key))
                continue
            if len(value) > cls.MAX_VALUE_LENGTH:
                value = value[:cls.MAX_VALUE_LENGTH - len(cls.TAG_SHORTEN_SUFFIX)] + cls.TAG_SHORTEN_SUFFIX
            tags_dict[key] = value
        return tags_dict

    @classmethod
    def get_user(cls):
        config = Config.instance()
        user_info = jwt.decode(config.get_token(), verify=False)
        if 'sub' in user_info:
            return user_info['sub']
        raise RuntimeError('Cannot find user info.')

    @classmethod
    def generate_tags(cls, raw_tags, source):
        tags = StorageOperations.parse_tags(raw_tags)
        tags[StorageOperations.CP_SOURCE_TAG] = source
        tags[StorageOperations.CP_OWNER_TAG] = StorageOperations.get_user()
        return tags

    @classmethod
    def source_tags(cls, tags, source_path, storage_wrapper):
        bucket = storage_wrapper.bucket
        default_tags = {}
        if StorageOperations.CP_SOURCE_TAG not in tags:
            scheme = WrapperType.cloud_scheme(bucket.type)
            default_tags[StorageOperations.CP_SOURCE_TAG] = StorageOperations.STORAGE_PATH \
                                                            % (scheme, bucket.name, source_path)
        if StorageOperations.CP_OWNER_TAG not in tags:
            default_tags[StorageOperations.CP_OWNER_TAG] = StorageOperations.get_user()
        return default_tags

    @classmethod
    def get_items(cls, listing_manager, relative_path, delimiter=PATH_SEPARATOR):
        prefix = StorageOperations.get_prefix(relative_path).rstrip(delimiter)
        for item in listing_manager.list_items(prefix, recursive=True, show_all=True):
            if not StorageOperations.is_relative_path(item.name, prefix):
                continue
            if item.name == relative_path:
                item_relative_path = os.path.basename(item.name)
            else:
                item_relative_path = StorageOperations.get_item_name(item.name, prefix + delimiter)
            yield ('File', item.name, item_relative_path, item.size)

    @classmethod
    def file_is_empty(cls, size):
        return not size or size == 0


class AbstractTransferManager:
    __metaclass__ = ABCMeta

    @abstractmethod
    def get_destination_key(self, destination_wrapper, relative_path):
        pass

    @abstractmethod
    def get_source_key(self, source_wrapper, source_path):
        pass

    @abstractmethod
    def get_destination_size(self, destination_wrapper, destination_key):
        pass

    @abstractmethod
    def transfer(self, source_wrapper, destination_wrapper, path=None, relative_path=None, clean=False,
                 quiet=False, size=None, tags=(), lock=None):
        """
        Transfers data from the source storage to the destination storage.

        :param source_wrapper: Source data storage resource wrapper.
        :type source_wrapper: DataStorageWrapper.
        :param destination_wrapper: Destination data storage resource wrapper.
        :type destination_wrapper: DataStorageWrapper.
        :param path: Transfer data full path.
        :param relative_path: Transfer data relative path.
        :param clean: Remove source files after the transferring.
        :param quiet: True if quite mode specified.
        :param size: Size of the transfer source object.
        :param tags: Additional tags that will be included to the transferring object.
        Tags CP_SOURCE and CP_OWNER will be included by default.
        :param lock: The lock object if multithreaded transfer is requested
        :type lock: multiprocessing.Lock
        """
        pass

    @staticmethod
    def skip_existing(source_key, source_size, destination_key, destination_size, quiet):
        if destination_size is not None and destination_size == source_size:
            if not quiet:
                click.echo('Skipping file %s since it exists in the destination %s' % (source_key, destination_key))
            return True
        return False

    @staticmethod
    def create_local_folder(destination_key, lock):
        folder = os.path.dirname(destination_key)
        if lock:
            lock.acquire()
        try:
            if folder and not os.path.exists(folder):
                os.makedirs(folder)
        finally:
            if lock:
                lock.release()


class AbstractListingManager:
    __metaclass__ = ABCMeta

    @abstractmethod
    def list_items(self, relative_path=None, recursive=False, page_size=StorageOperations.DEFAULT_PAGE_SIZE,
                   show_all=False):
        """
        Lists files and folders by a relative path in the current storage.

        :param relative_path: Storage relative path to be listed.
        :param recursive: Specifies if the listing has to be recursive.
        :param page_size: Max number of items to return. The argument is ignored if show_all argument is specified.
        :param show_all: Specifies if all items have to be listed.
        """
        pass

    @abstractmethod
    def get_summary_with_depth(self, max_depth, relative_path=None):
        """
        Returns tree with storage usage statistic under the given relative path and according to given depth.

        :param max_depth: returns N or fewer levels below
        :param relative_path: Storage relative path to be processed
        :return: tree with storage usage statistic
        """
        pass

    @abstractmethod
    def get_summary(self, relative_path=None):
        """
        Calculates storage usage statistic according to relative path

        :param relative_path: Storage relative path to be processed
        :return: <Storage path>, <total objects by path>, <total objects size>
        """
        pass

    def get_items(self, relative_path):
        """
        Returns all files under the given relative path in forms of tuples with the following structure:
        ('File', full_path, relative_path, size)

        :param relative_path: Path to a folder or a file.
        :return: Generator of file tuples.
        """
        prefix = StorageOperations.get_prefix(relative_path).rstrip(StorageOperations.PATH_SEPARATOR)
        for item in self.list_items(prefix, recursive=True, show_all=True):
            if not StorageOperations.is_relative_path(item.name, prefix):
                continue
            if item.name == relative_path:
                item_relative_path = os.path.basename(item.name)
            else:
                item_relative_path = StorageOperations.get_item_name(item.name, prefix + StorageOperations.PATH_SEPARATOR)
            yield ('File', item.name, item_relative_path, item.size)

    def folder_exists(self, relative_path, delimiter=StorageOperations.PATH_SEPARATOR):
        prefix = StorageOperations.get_prefix(relative_path).rstrip(delimiter) + delimiter
        for item in self.list_items(prefix, show_all=True):
            if prefix.endswith(item.name):
                return True
        return False

    @abstractmethod
    def get_file_tags(self, relative_path):
        pass

    def get_file_size(self, relative_path):
        items = self.list_items(relative_path, show_all=True, recursive=True)
        for item in items:
            if item.name == relative_path:
                return item.size
        return None


class AbstractDeleteManager:
    __metaclass__ = ABCMeta

    @abstractmethod
    def delete_items(self, relative_path, recursive=False, exclude=[], include=[], version=None, hard_delete=False):
        """
        Deletes all items under the given path.

        :param relative_path: Storage relative path to be deleted.
        :param recursive: Specifies if the deletion has to be recursive. The argument is required for folders deletion.
        :param exclude: Exclude item pattern.
        :param include: Include item pattern.
        :param version: Version to be deleted.
        :param hard_delete: Specifies if all item versions have to be deleted.
        """
        pass


class AbstractRestoreManager:
    __metaclass__ = ABCMeta

    @abstractmethod
    def restore_version(self, version, exclude, include, recursive):
        """
        Restores item version.

        :param version: Version to be restored.
        """
        pass
