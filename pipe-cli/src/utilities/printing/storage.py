import click

import datetime
from prettytable import prettytable
from src.model.data_storage_wrapper_type import WrapperType

STORAGE_DETAILS_SEPARATOR = ', '


def print_storage_listing(fields, items, bucket_model, show_details, show_extended, show_versions):
    if show_details:
        items_table = prettytable.PrettyTable()
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
            items_table.add_row(row)
            if show_versions and item.type == 'File':
                if item.deleted:
                    # Additional synthetic delete version
                    row = ['-File', '', item.deleted.strftime('%Y-%m-%d %H:%M:%S'), size, name, '- (latest)']
                    items_table.add_row(row)
                for version in item.versions:
                    version_type = "-File" if version.delete_marker else "+File"
                    version_label = "{} (latest)".format(version.version) if version.latest else version.version
                    labels = STORAGE_DETAILS_SEPARATOR.join(map(lambda i: i.value, version.labels))
                    size = '' if version.size is None else version.size
                    row = [version_type, labels, version.changed.strftime('%Y-%m-%d %H:%M:%S'), size, name,
                           version_label]
                    items_table.add_row(row)

        click.echo(items_table)
        click.echo()
    else:
        for item in items:
            click.echo('{}\t\t'.format(item.path), nl=False)
        click.echo()
