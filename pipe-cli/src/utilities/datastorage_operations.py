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

import datetime
import os
import platform
import sys

import click
import prettytable
from future.utils import iteritems

from src.api.data_storage import DataStorage
from src.api.folder import Folder
from src.model.data_storage_wrapper import DataStorageWrapper, S3BucketWrapper
from src.model.data_storage_wrapper_type import WrapperType
from src.utilities.du import DataUsageHelper
from src.utilities.du_format_type import DuFormatType
from src.utilities.patterns import PatternMatcher
from src.utilities.storage.mount import Mount
from src.utilities.storage.umount import Umount

try:
    from urllib.parse import urlparse  # Python 3
except ImportError:
    from urlparse import urlparse  # Python 2

ALL_ERRORS = Exception


class DataStorageOperations(object):
    @classmethod
    def cp(cls, source, destination, recursive, force, exclude, include, quiet, tags, file_list, symlinks, clean=False,
           skip_existing=False):
        try:
            source_wrapper = DataStorageWrapper.get_wrapper(source, symlinks)
            destination_wrapper = DataStorageWrapper.get_wrapper(destination)
            files_to_copy = []

            if source_wrapper is None:
                click.echo('Could not resolve path {}'.format(source), err=True)
                sys.exit(1)
            if not source_wrapper.exists():
                click.echo("Source {} doesn't exist".format(source), err=True)
                sys.exit(1)
            if destination_wrapper is None:
                click.echo('Could not resolve path {}'.format(destination), err=True)
                sys.exit(1)
            if not recursive and not source_wrapper.is_file():
                click.echo('Flag --recursive (-r) is required to copy folders.', err=True)
                sys.exit(1)
            if file_list:
                if source_wrapper.is_file():
                    click.echo('Option --file-list (-l) allowed for folders copy only.', err=True)
                    sys.exit(1)
                if not os.path.exists(file_list):
                    click.echo('Specified --file-list file does not exist.', err=True)
                    sys.exit(1)
                files_to_copy = cls.__get_file_to_copy(file_list, source_wrapper.path)
            relative = os.path.basename(source) if source_wrapper.is_file() else None
            if not force and not destination_wrapper.is_empty(relative=relative):
                click.echo('Flag --force (-f) is required to overwrite files in the destination data.', err=True)
                sys.exit(1)

            # append slashes to path to correctly determine file/folder type
            if not source_wrapper.is_file():
                if not source_wrapper.is_local() and not source_wrapper.path.endswith('/'):
                    source_wrapper.path = source_wrapper.path + '/'
                if destination_wrapper.is_local() and not destination_wrapper.path.endswith(os.path.sep):
                    destination_wrapper.path = destination_wrapper.path + os.path.sep
                if not destination_wrapper.is_local() and not destination_wrapper.path.endswith('/'):
                    destination_wrapper.path = destination_wrapper.path + '/'

            # copying a file to a remote destination, we need to set folder/file flag correctly
            if source_wrapper.is_file() and not destination_wrapper.is_local() and not destination.endswith('/'):
                destination_wrapper.is_file_flag = True

            command = 'mv' if clean else 'cp'
            permission_to_check = os.R_OK if command == 'cp' else os.W_OK
            manager = DataStorageWrapper.get_operation_manager(source_wrapper, destination_wrapper, command)
            items = files_to_copy if file_list else source_wrapper.get_items()
            for item in items:
                full_path = item[1]
                relative_path = item[2]
                size = item[3]
                # check that we have corresponding permission for the file before take action
                if source_wrapper.is_local() and not os.access(full_path, permission_to_check):
                    continue
                if not include and not exclude:
                    if source_wrapper.is_file() and not source_wrapper.path == full_path:
                        continue
                    if not source_wrapper.is_file():
                        possible_folder_name = source_wrapper.path_with_trailing_separator()
                        if not full_path.startswith(possible_folder_name):
                            continue
                if not PatternMatcher.match_any(relative_path, include):
                    if not quiet:
                        click.echo("Skipping file {} since it doesn't match any of include patterns [{}]."
                                   .format(full_path, ",".join(include)))
                    continue
                if PatternMatcher.match_any(relative_path, exclude, default=False):
                    if not quiet:
                        click.echo("Skipping file {} since it matches exclude patterns [{}]."
                                   .format(full_path, ",".join(exclude)))
                    continue
                manager.transfer(source_wrapper, destination_wrapper, path=full_path,
                                 relative_path=relative_path, clean=clean, quiet=quiet, size=size,
                                 tags=tags, skip_existing=skip_existing)
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)

    @classmethod
    def storage_remove_item(cls, path, yes, version, hard_delete, recursive, exclude, include):
        """ Removes file or folder
        """
        try:
            if version and hard_delete:
                click.echo('"version" argument should\'t be combined with "hard-delete" option', err=True)
                sys.exit(1)
            source_wrapper = DataStorageWrapper.get_cloud_wrapper(path, versioning=version is not None or hard_delete)
            if source_wrapper is None or not source_wrapper.exists():
                click.echo('Storage path "{}" was not found'.format(path), err=True)
                sys.exit(1)
            if len(source_wrapper.path) == 0:
                click.echo('Cannot remove root folder \'{}\''.format(path), err=True)
                sys.exit(1)
            if not source_wrapper.is_file() and not recursive:
                click.echo('Flag --recursive (-r) is required to remove folders.', err=True)
                sys.exit(1)
            if (version or hard_delete) and not source_wrapper.bucket.policy.versioning_enabled:
                click.echo('Error: versioning is not enabled for storage.', err=True)
                sys.exit(1)
            if not yes:
                click.confirm('Are you sure you want to remove everything at path \'{}\'?'.format(path),
                              abort=True)
            click.echo('Removing {} ...'.format(path), nl=False)

            manager = source_wrapper.get_delete_manager(versioning=version or hard_delete)
            manager.delete_items(source_wrapper.path, version=version, hard_delete=hard_delete,
                                 exclude=exclude, include=include, recursive=recursive and not source_wrapper.is_file())
        except ALL_ERRORS as error:
            if not type(error) is click.Abort:
                click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)
        click.echo(' done.')

    @classmethod
    def save_data_storage(cls, name, description, sts_duration, lts_duration, versioning,
                          backup_duration, type, parent_folder, on_cloud, path, region_id):
        directory = None
        if parent_folder:
            directory = Folder.load(parent_folder)
            if directory is None:
                click.echo("Error: Directory with name '{}' not found! "
                           "Check if it exists and you have permission to read it".format(parent_folder), err=True)
                sys.exit(1)
        if region_id == 'default':
            region_id = None
        else:
            try:
                region_id = int(region_id)
            except ValueError:
                click.echo("Error: Given region id '{}' is not a number.".format(region_id))
                sys.exit(1)
        try:
            DataStorage.save(name, path, description, sts_duration, lts_duration, versioning, backup_duration, type,
                             directory.id if directory else None, on_cloud, region_id)
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)

    @classmethod
    def delete(cls, name, on_cloud, yes):
        if not yes:
            if on_cloud:
                click.confirm(
                    'Are you sure you want to delete datastorage {} and also delete it from a cloud?'.format(name),
                    abort=True)
            else:
                click.confirm(
                    'Are you sure you want to delete datastorage {}?'.format(name),
                    abort=True)

        try:
            DataStorage.delete(name, on_cloud)
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)

    @classmethod
    def policy(cls, storage_name, sts_duration, lts_duration, backup_duration, versioning):
        try:
            DataStorage.policy(storage_name, sts_duration, lts_duration, backup_duration, versioning)
        except ALL_ERRORS as error:
            click.echo(str(error), err=True)
            sys.exit(1)

    @classmethod
    def mvtodir(cls, name, directory):
        folder_id = None
        try:
            if directory is not "/":
                if os.path.split(directory)[0]:  # case with path
                    folder = Folder.load(directory)
                else:
                    folder = Folder.load_by_name(directory)
                if folder is None:
                    click.echo("Directory with name {} does not exist!".format(directory), err=True)
                    sys.exit(1)
                folder_id = folder.id
            DataStorage.mvtodir(name, folder_id)
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)

    @classmethod
    def restore(cls, path, version, recursive, exclude, include):
        try:
            source_wrapper = DataStorageWrapper.get_cloud_wrapper(path, True)
            if source_wrapper is None:
                click.echo('Storage path "{}" was not found'.format(path), err=True)
                sys.exit(1)
            if (recursive or exclude or include) and not isinstance(source_wrapper, S3BucketWrapper):
                click.echo('Folder restore allowed for S3 provider only', err=True)
                sys.exit(1)
            if not source_wrapper.bucket.policy.versioning_enabled:
                click.echo('Versioning is not enabled for storage "{}"'.format(source_wrapper.bucket.name), err=True)
                sys.exit(1)
            if version and recursive:
                click.echo('"version" argument should\'t be combined with "recursive" option', err=True)
                sys.exit(1)
            manager = source_wrapper.get_restore_manager()
            manager.restore_version(version, exclude, include, recursive=recursive)
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)

    @classmethod
    def storage_list(cls, path, show_details, show_versions, recursive, page, show_all):
        """Lists storage contents
        """
        if path:
            root_bucket = None
            original_path = ''
            try:
                root_bucket, original_path = DataStorage.load_from_uri(path)
            except ALL_ERRORS as error:
                click.echo('Error: %s' % str(error), err=True)
                sys.exit(1)
            if show_versions and not root_bucket.policy.versioning_enabled:
                click.echo('Error: versioning is not enabled for storage.', err=True)
                sys.exit(1)
            if root_bucket is None:
                click.echo('Storage path "{}" was not found'.format(path), err=True)
                sys.exit(1)
            else:
                relative_path = original_path if original_path != '/' else ''
                cls.__print_data_storage_contents(root_bucket, relative_path, show_details, recursive,
                                                  page_size=page, show_versions=show_versions, show_all=show_all)
        else:
            # If no argument is specified - list brief details of all buckets
            cls.__print_data_storage_contents(None, None, show_details, recursive, show_all=show_all)

    @classmethod
    def storage_mk_dir(cls, folders):
        """ Creates a directory
        """
        buckets = []
        try:
            buckets = list(DataStorage.list())
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)
        for original_path in folders:
            folder = original_path
            info = DataStorageWrapper.get_data_storage_item_path_info(folder, buckets)
            error = info[0]
            current_bucket_identifier = info[1]
            relative_path = info[2]
            if error is not None:
                click.echo(error, err=True)
                continue
            if len(relative_path) == 0:
                click.echo('Cannot create folder \'{}\': already exists'.format(original_path), err=True)
                continue
            click.echo('Creating folder {}...'.format(original_path), nl=False)
            result = None
            try:
                result = DataStorage.create_folder(current_bucket_identifier, relative_path)
            except ALL_ERRORS as error:
                click.echo('Error: %s' % str(error), err=True)
                sys.exit(1)
            if result is not None and result.error is None:
                click.echo('done.')
            elif result is not None and result.error is not None:
                click.echo('failed.')
                click.echo(result.error, err=True)

    @classmethod
    def set_object_tags(cls, path, tags, version):
        try:
            root_bucket, relative_path = DataStorage.load_from_uri(path)
            updated_tags = DataStorage.set_object_tags(root_bucket.identifier, relative_path,
                                                       cls.convert_input_pairs_to_json(tags), version)
            if not updated_tags:
                raise RuntimeError("Failed to set tags for path '{}'.".format(path))
        except BaseException as e:
            click.echo(str(e.message), err=True)
            sys.exit(1)

    @classmethod
    def get_object_tags(cls, path, version):
        try:
            root_bucket, relative_path = DataStorage.load_from_uri(path)
            tags = DataStorage.get_object_tags(root_bucket.identifier, relative_path, version)
            if not tags:
                click.echo("No tags available for path '{}'.".format(path))
            else:
                click.echo(cls.create_table(tags))
        except BaseException as e:
            click.echo(str(e.message), err=True)
            sys.exit(1)

    @classmethod
    def delete_object_tags(cls, path, tags, version):
        if not tags:
            click.echo("Error: Missing argument \"tags\"", err=True)
            sys.exit(1)
        try:
            root_bucket, relative_path = DataStorage.load_from_uri(path)
            DataStorage.delete_object_tags(root_bucket.identifier, relative_path, tags, version)
        except BaseException as e:
            click.echo(str(e.message), err=True)
            sys.exit(1)

    @classmethod
    def du(cls, storage_name, relative_path='', format='M', depth=None):
        if depth and not storage_name:
            click.echo("Error: bucket path must be provided with --depth option", err=True)
            sys.exit(1)
        du_helper = DataUsageHelper(format)
        items_table = prettytable.PrettyTable()
        fields = ["Storage", "Files count", "Size (%s)" % DuFormatType.pretty_type(format)]
        items_table.field_names = fields
        items_table.align = "l"
        items_table.border = False
        items_table.padding_width = 2
        items_table.align['Size'] = 'r'
        try:
            if storage_name:
                if relative_path == "/":
                    relative_path = ''
                storage = DataStorage.get(storage_name)
                if storage is None:
                    raise RuntimeError('Storage "{}" was not found'.format(storage_name))
                if storage.type.lower() == 'nfs':
                    if depth:
                        raise RuntimeError('--depth option is not supported for NFS storages')
                    items_table.add_row(du_helper.get_nfs_storage_summary(storage_name, relative_path))
                else:
                    for item in du_helper.get_cloud_storage_summary(storage, relative_path, depth):
                        items_table.add_row(item)
            else:
                # If no argument is specified - list all buckets
                items = du_helper.get_total_summary()
                if items is None:
                    click.echo("No datastorages available.")
                    sys.exit(0)
                for item in items:
                    items_table.add_row(item)
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)
        click.echo(items_table)
        click.echo()

    @classmethod
    def convert_input_pairs_to_json(cls, tags):
        result = dict()
        for tags_for_update in tags:
            if "=" not in tags_for_update:
                raise ValueError("Tags must be specified as KEY=VALUE pair.")
            pair = tags_for_update.split("=", 1)
            result.update({pair[0]: pair[1]})
        return result

    @classmethod
    def create_table(cls, tags):
        table = prettytable.PrettyTable()
        table.field_names = ["Tag name", "Value"]
        table.align = "l"
        table.header = True
        for (key, value) in iteritems(tags):
            table.add_row([key, value])
        return table

    @classmethod
    def __print_data_storage_contents(cls, bucket_model, relative_path,
                                      show_details, recursive, page_size=None, show_versions=False, show_all=False):
        items = []
        header = None
        if bucket_model is not None:
            try:
                wrapper = DataStorageWrapper.get_cloud_wrapper_for_bucket(bucket_model, relative_path)
                manager = wrapper.get_list_manager(show_versions=show_versions)
                items = manager.list_items(relative_path, recursive=recursive, page_size=page_size, show_all=show_all)
            except ALL_ERRORS as error:
                click.echo('Error: %s' % str(error), err=True)
                sys.exit(1)
        else:
            # If no argument is specified - list brief details of all buckets
            try:
                items = list(DataStorage.list())
                if not items:
                    click.echo("No datastorages available.")
                    sys.exit(0)
            except ALL_ERRORS as error:
                click.echo('Error: %s' % str(error), err=True)
                sys.exit(1)

        if recursive and header is not None:
            click.echo(header)

        if show_details:
            items_table = prettytable.PrettyTable()
            fields = ["Type", "Labels", "Modified", "Size", "Name"]
            if show_versions:
                fields.append("Version")
            items_table.field_names = fields
            items_table.align = "l"
            items_table.border = False
            items_table.padding_width = 2
            items_table.align['Size'] = 'r'
            for item in items:
                name = item.name
                changed = ''
                size = ''
                labels = ''
                if item.type is not None and item.type in WrapperType.cloud_types():
                    name = item.path
                item_updated = item.deleted or item.changed
                if item_updated is not None:
                    if bucket_model is None:
                        # need to wrap into datetime since bucket listing returns str
                        item_datetime = datetime.datetime.strptime(item_updated, '%Y-%m-%d %H:%M:%S')
                    else:
                        item_datetime = item_updated
                    changed = item_datetime.strftime('%Y-%m-%d %H:%M:%S')
                if item.size is not None and not item.deleted:
                    size = item.size
                if item.labels is not None and len(item.labels) > 0 and not item.deleted:
                    labels = ', '.join(map(lambda i: i.value, item.labels))
                item_type = "-File" if item.delete_marker or item.deleted else item.type
                row = [item_type, labels, changed, size, name]
                if show_versions:
                    row.append('')
                items_table.add_row(row)
                if show_versions and item.type == 'File':
                    if item.deleted:
                        # Additional synthetic delete version
                        row = ['-File', '', item.deleted.strftime('%Y-%m-%d %H:%M:%S'), size, name, '- (latest)']
                        items_table.add_row(row)
                    for version in item.versions:
                        version_type = "-File" if version.delete_marker else "+File"
                        version_label = "{} (latest)".format(version.version) if version.latest else version.version
                        labels = ', '.join(map(lambda i: i.value, version.labels))
                        size = '' if version.size is None else version.size
                        row = [version_type, labels, version.changed.strftime('%Y-%m-%d %H:%M:%S'), size, name, version_label]
                        items_table.add_row(row)

            click.echo(items_table)
            click.echo()
        else:
            for item in items:
                click.echo('{}\t\t'.format(item.path), nl=False)
            click.echo()

    @classmethod
    def __get_file_to_copy(cls, file_path, source_path):
        with open(file_path, 'rb') as source:
            for line in source:
                line = line.strip()
                splitted = line.split('\t')
                path = splitted[0]
                size = long(float(splitted[1]))
                yield ('File', os.path.join(source_path, path), path, size)

    @classmethod
    def mount_storage(cls, mountpoint, file=False, bucket=None, log_file=None, options=None, quiet=False,
                      threading=False):
        try:
            if not file and not bucket:
                click.echo('Either file system mode should be enabled (-f/--file) '
                           'or bucket name should be specified (-b/--bucket BUCKET).', err=True)
                sys.exit(1)
            cls.check_platform("mount")
            Mount().mount_storages(mountpoint, file, bucket, options, quiet=quiet, log_file=log_file,
                                   threading=threading)
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)

    @classmethod
    def umount_storage(cls, mountpoint, quiet=False):
        try:
            cls.check_platform("umount")
            if not os.path.isdir(mountpoint):
                click.echo('Mountpoint "%s" is not a folder.' % mountpoint, err=True)
                sys.exit(1)
            if not os.path.ismount(mountpoint):
                click.echo('Directory "%s" is not a mountpoint.' % mountpoint, err=True)
                sys.exit(1)
            Umount().umount_storages(mountpoint, quiet=quiet)
        except ALL_ERRORS as error:
            click.echo('Error: %s' % str(error), err=True)
            sys.exit(1)

    @classmethod
    def check_platform(self, command):
        if platform.system() == 'Windows':
            click.echo('%s command is not supported for Windows OS.' % command, err=True)
            sys.exit(1)
