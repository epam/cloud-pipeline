# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import json

import click

import datetime
from abc import abstractmethod, ABCMeta

from future.utils import iteritems
from prettytable import prettytable

from src.model.data_storage_wrapper_type import WrapperType

STORAGE_DETAILS_SEPARATOR = ', '

def init_items_table(fields):
    items_table = prettytable.PrettyTable()
    items_table.field_names = fields
    items_table.align = "l"
    items_table.border = False
    items_table.padding_width = 2
    items_table.align['Size'] = 'r'
    return items_table

def print_storage_items(bucket_model, items, show_details, print_service, show_extended, show_versions=False):
    if show_details:
        for item in items:
            name = item.name
            changed = ''
            size = ''
            labels = ''
            if item.type is not None and item.type in WrapperType.cloud_types():
                name = item.path
            item_updated = item.deleted or item.changed
            if item_updated is not None:
                if bucket_model is None and isinstance(item_updated, str):
                    # need to wrap into datetime since bucket listing returns str
                    item_datetime = datetime.datetime.strptime(item_updated, '%Y-%m-%d %H:%M:%S')
                else:
                    item_datetime = item_updated
                changed = item_datetime.strftime('%Y-%m-%d %H:%M:%S')
            if item.size is not None and not item.deleted:
                size = item.size
            if item.labels is not None and len(item.labels) > 0 and not item.deleted:
                labels = STORAGE_DETAILS_SEPARATOR.join(map(lambda i: i.value, item.labels))
            item_type = "-File" if item.delete_marker or item.deleted else item.type
            row = [item_type, labels, changed, size, name]
            if show_versions:
                row.append('')
            if show_extended:
                mount_status = item.mount_status
                mount_limits = STORAGE_DETAILS_SEPARATOR.join(item.tools_to_mount)
                item_metadata = STORAGE_DETAILS_SEPARATOR.join(['='.join(entry) for entry in item.metadata.items()])
                row.extend([mount_status, mount_limits, item_metadata])
            print_service.add_item(row, show_versions, show_extended)
            if show_versions and item.type == 'File':
                if item.deleted:
                    # Additional synthetic delete version
                    row = ['-File', '', item.deleted.strftime('%Y-%m-%d %H:%M:%S'), size, name, '- (latest)']
                    print_service.add_version(row)
                for version in item.versions:
                    version_type = "-File" if version.delete_marker else "+File"
                    version_label = "{} (latest)".format(version.version) if version.latest else version.version
                    labels = STORAGE_DETAILS_SEPARATOR.join(map(lambda i: i.value, version.labels))
                    size = '' if version.size is None else version.size
                    row = [version_type, labels, version.changed.strftime('%Y-%m-%d %H:%M:%S'), size, name,
                           version_label]
                    print_service.add_version(row)
            print_service.buffer_item()
        print_service.flush_part()
    else:
        for item in items:
            print_service.print_item(item)


class StoragePrintService(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def init_header(self, show_versions, show_extended):
        pass

    @abstractmethod
    def disable_header(self):
        """
        Do not print header during printing not the first part.
        Relevant for pretty-table case only.
        """
        pass

    @abstractmethod
    def flush(self):
        pass

    @abstractmethod
    def flush_part(self):
        pass

    @abstractmethod
    def error(self, message, err=False, buf=False):
        pass

    @abstractmethod
    def add_item(self, item, show_versions, show_extended):
        pass

    @abstractmethod
    def print_item(self, item):
        pass

    @abstractmethod
    def empty_items(self):
        pass

    @abstractmethod
    def add_version(self, item):
        self.__table.add_row(item)

    @abstractmethod
    def buffer_item(self):
        pass


class PrettyTableStoragePrintService(StoragePrintService):

    def __init__(self):
        self.__table = None

    def init_header(self, show_versions, show_extended):
        fields = ["Type", "Labels", "Modified", "Size", "Name"]
        if show_versions:
            fields.append("Version")
        if show_extended:
            fields.extend(["Mount status", "Mount limits", "Metadata"])
        self.__table = init_items_table(fields)

    def disable_header(self):
        if not self.__table:
            return
        self.__table.header = False

    def flush_part(self):
        if not self.__table:
            return
        click.echo(self.__table)
        self.__table.clear_rows()

    def flush(self):
        click.echo()

    def error(self, message, err=False, buf=False):
        click.echo(message, err=err)

    def add_item(self, item, show_versions, show_extended):
        if not self.__table:
            return
        self.__table.add_row(item)

    def add_version(self, item):
        if not self.__table:
            return
        self.__table.add_row(item)

    def buffer_item(self):
        pass

    def print_item(self, item):
        click.echo('{}\t\t'.format(item.path), nl=False)

    def empty_items(self):
        click.echo("No datastorages available.")


class JsonStoragePrintService(StoragePrintService):

    def __init__(self):
        self.__buffer = []
        self.__item = {}

    @staticmethod
    def _to_json(obj):
        return json.dumps(obj, default=str, indent=2, ensure_ascii=False)

    def disable_header(self):
        # no-op
        pass

    def init_header(self, show_versions, show_extended):
        # no-op
        pass

    def flush_part(self):
        # partial flash not supported for JSON format
        pass

    def flush(self):
        if self.__buffer:
            click.echo(self._to_json(self.__buffer))

    def error(self, message, err=False, buf=False):
        click.echo(json.dumps({'error': message}), err=err)

    def add_item(self, item, show_versions, show_extended):
        # item:
        # 0 - item_type
        # 1 - labels
        # 2 - changed
        # 3 - size
        # 4 - name
        item_type = item[0]
        labels = item[1]
        changed = item[2]
        size = item[3]
        name = item[4]
        _item = {
            'type': item_type,
            'name': name
        }
        if labels:
            _item['labels'] = labels
        if changed:
            _item['changed'] = changed
        if size:
            _item['size'] = size
        # Optional data:
        # - version (skipping now)
        # extended (index depends on --show-version enabled):
        # - mount_status
        # - mount_limits
        # - item_metadata
        # extended info available for latest version only
        if show_extended:
            _item['mountStatus'] = item[6 if show_versions else 5]
            _item['mountLimits'] = item[7 if show_versions else 6]
            _item['metadata'] = item[8 if show_versions else 7]
        self.__item = _item

    def add_version(self, item):
        if 'versions' not in self.__item:
            self.__item['versions'] = []
        _item = {
            'type': item[0],
            'labels': item[1],
            'changed': item[2],
            'size': item[3],
            'name': item[4],
            'version': item[5]
        }
        self.__item['versions'].append(_item)

    def buffer_item(self):
        self.__buffer.append(self.__item)
        self.__item = {}

    def print_item(self, item):
        self.__buffer.append({'name': item.path})

    def empty_items(self):
        click.echo(self._to_json([]))


class StorageObjectTagPrintService(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def print_tags(self, path, tags):
        pass


class PrettyTableStorageObjectTagPrintService(StorageObjectTagPrintService):

    def print_tags(self, path, tags):
        if not tags:
            click.echo("No tags available for path '{}'.".format(path))
            return
        table = prettytable.PrettyTable()
        table.field_names = ["Tag name", "Value"]
        table.align = "l"
        table.header = True
        for key, value in iteritems(tags):
            table.add_row([key, value])
        click.echo(table)


class JsonStorageObjectTagPrintService(StorageObjectTagPrintService):

    @staticmethod
    def _to_json(obj):
        return json.dumps(obj, indent=2, default=str, ensure_ascii=False)

    def print_tags(self, path, tags):
        if not tags:
            click.echo(self._to_json([]))
            return
        result = [{'tagName': key, 'value': value} for key, value in iteritems(tags)]
        click.echo(self._to_json(result))


def create_storage_object_tag_print_service(output_format):
    if output_format == 'json':
        return JsonStorageObjectTagPrintService()
    return PrettyTableStorageObjectTagPrintService()


def create_storage_print_service(output):
    if output == 'json':
        return JsonStoragePrintService()
    return PrettyTableStoragePrintService()
