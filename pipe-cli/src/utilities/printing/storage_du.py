# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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
from abc import abstractmethod, ABCMeta

import click
from prettytable import prettytable

from src.utilities.datastorage_du_operation import DuOutput
from src.utilities.printing.utils import to_json


class StorageDuPrintService(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def add_item(self, du_command, item):
        """Add a single item incrementally (used in eager/streaming mode)."""
        pass

    @abstractmethod
    def print_items(self, du_command, items):
        """Print all collected items at once (used in non-eager mode)."""
        pass

    @abstractmethod
    def flush(self):
        """Finalize output (print buffered JSON or trailing newline)."""
        pass

    @abstractmethod
    def error(self, message):
        """Print an error message in the appropriate format."""
        pass

    @abstractmethod
    def warning(self, message, err=False):
        """Print a non-fatal warning; implementations may suppress it (e.g. JSON mode)."""
        pass


class PrettyTableStorageDuPrintService(StorageDuPrintService):

    def __init__(self):
        self._header = None
        self._table = None

    def add_item(self, du_command, item):
        self._header, self._table = self._print_item(du_command, item, self._header, self._table)

    def print_items(self, du_command, items):
        click.echo(self._format_table(du_command, items))

    def flush(self):
        click.echo()

    def error(self, message):
        click.echo(message, err=True)

    def warning(self, message, err=False):
        click.echo(message, err=err)

    @staticmethod
    def _configure_table(header):
        table = prettytable.PrettyTable()
        table.field_names = header
        table.align = "r"
        table.align['Storage'] = 'l'
        table.border = False
        table.padding_width = 2
        return table

    @staticmethod
    def _build_header(du_command, du_leafs):
        header = [
            "Storage", "Files count", "Size (%s)" % DuOutput.pretty_size(du_command.size_format)
        ]
        if not du_command.show_archive:
            return header
        if du_command.output_mode in DuOutput.brief_mode():
            header.append("Archive size (%s)" % DuOutput.pretty_size(du_command.size_format))
        else:
            possible_additional_columns = set()
            for _item in du_leafs:
                possible_additional_columns.update(
                    filter(lambda t: t != DuOutput.STANDARD_TIER, _item[1].get_tiers())
                )
            for _column in possible_additional_columns:
                if _column not in header:
                    header.append(_column + " (%s)" % DuOutput.pretty_size(du_command.size_format))
        return header

    @staticmethod
    def _build_row(du_command, header, item):
        item_usage = item[1]
        row = [item[0], item_usage.get_total_count()]
        usage_by_tiers = item_usage.get_usage()
        row.append(
            DuOutput.pretty_size_value(
                DuOutput.get_tier_size(usage_by_tiers.get(DuOutput.STANDARD_TIER), du_command.generation),
                du_command.size_format
            )
        )

        if not du_command.show_archive:
            return row

        if du_command.output_mode in DuOutput.brief_mode():
            archive_size = 0
            for archive_tier in filter(lambda t: t != DuOutput.STANDARD_TIER, item_usage.get_tiers()):
                archive_size += DuOutput.get_tier_size(usage_by_tiers.get(archive_tier), du_command.generation)
            row.append(DuOutput.pretty_size_value(archive_size, du_command.size_format))
        else:
            for header_column in header[3:]:
                row.append(
                    DuOutput.pretty_size_value(
                        DuOutput.get_tier_size(
                            next((t for t in usage_by_tiers.values() if header_column.startswith(t.tier)), None),
                            du_command.generation
                        ),
                        du_command.size_format
                    )
                )
        return row

    def _format_table(self, du_command, du_leafs):
        header = self._build_header(du_command, du_leafs)
        table = self._configure_table(header)
        for item in du_leafs:
            table.add_row(self._build_row(du_command, header, item))
        return table

    def _print_item(self, du_command, item, header, table):
        if not table:
            header = self._build_header(du_command, [])
            table = self._configure_table(header)
            table.add_row(self._build_row(du_command, header, item))
            click.echo(table)
            table.clear_rows()
            table.header = False
        else:
            table.add_row(self._build_row(du_command, header, item))
            click.echo(table)
            table.clear_rows()
        return header, table


class JsonStorageDuPrintService(StorageDuPrintService):
    """

    JSON output schema:

        [
          {
            "storage":     "<path>",
            "filesCount":  <integer>,
            "size":        <number>,
            "sizeUnit":    "Kb" | "Mb" | "Gb",
            "archiveSize": <number>
          }
        ]

    "archiveSize" is present in brief output mode (-o brief, default).
    In full output mode (-o full) per-tier keys replace it, e.g.:

        {
          "storage": "<path>",
          "filesCount": <integer>,
          "size": <number>,
          "sizeUnit": "<unit>",
          "GLACIER": <number>,
          "DEEP_ARCHIVE": <number>
        }

    Errors are reported as:

        { "error": "<message>" }

    """

    def __init__(self):
        self._buffer = []

    def add_item(self, du_command, item):
        self._buffer.append(self._build_entry(du_command, item))

    def print_items(self, du_command, items):
        for item in items:
            self._buffer.append(self._build_entry(du_command, item))

    def flush(self):
        click.echo(to_json(self._buffer))

    def error(self, message):
        click.echo(json.dumps({'error': message}), err=True)

    def warning(self, message, err=False):
        pass

    @staticmethod
    def _build_entry(du_command, item):
        item_usage = item[1]
        usage_by_tiers = item_usage.get_usage()

        entry = {
            'storage': item[0],
            'filesCount': item_usage.get_total_count(),
            'size': float(DuOutput.pretty_size_value(
                DuOutput.get_tier_size(usage_by_tiers.get(DuOutput.STANDARD_TIER), du_command.generation),
                du_command.size_format
            )),
            'sizeUnit': DuOutput.pretty_size(du_command.size_format),
        }

        if du_command.show_archive:
            if du_command.output_mode in DuOutput.brief_mode():
                archive_size = sum(
                    DuOutput.get_tier_size(usage_item, du_command.generation)
                    for tier, usage_item in usage_by_tiers.items()
                    if tier != DuOutput.STANDARD_TIER
                )
                entry['archiveSize'] = float(DuOutput.pretty_size_value(archive_size, du_command.size_format))
            else:
                for tier, usage_item in usage_by_tiers.items():
                    if tier != DuOutput.STANDARD_TIER:
                        entry[tier] = float(DuOutput.pretty_size_value(
                            DuOutput.get_tier_size(usage_item, du_command.generation),
                            du_command.size_format
                        ))

        return entry


def create_storage_du_print_service(output_format):
    if output_format == 'json':
        return JsonStorageDuPrintService()
    return PrettyTableStorageDuPrintService()
