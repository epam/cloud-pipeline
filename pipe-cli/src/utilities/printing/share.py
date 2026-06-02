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

import click
from abc import abstractmethod, ABCMeta
from prettytable import prettytable


class SharePrintService(object):
    """Formats and prints the sharing state of a pipeline run."""
    __metaclass__ = ABCMeta

    @abstractmethod
    def not_shared(self):
        """Print output for a run that has no sharing entries."""
        pass

    @abstractmethod
    def print_sids(self, run_sids):
        """Print the list of RunSid objects for a shared run."""
        pass

    @abstractmethod
    def error(self, message):
        """Print an error message to stderr."""
        pass

class PrettyTableSharePrintService(SharePrintService):

    def not_shared(self):
        click.echo("Not shared (use 'pipe share add' to configure)")

    def print_sids(self, run_sids):
        table = prettytable.PrettyTable()
        table.field_names = ["User/group", "SSH shared"]
        table.align = "l"
        table.header = True
        for sid in run_sids:
            table.add_row([sid.name, '+' if sid.access_type == 'SSH' else ''])
        click.echo(table)

    def error(self, message):
        click.echo(message, err=True)


class JsonSharePrintService(SharePrintService):
    """Prints sharing state as JSON to stdout.

    JSON output schema:
        [
          {
            "name":        string,   -- user or group name
            "isPrincipal": boolean,  -- true for users, false for groups
            "accessType":  string    -- "SSH" | "ENDPOINT"
          },
          ...
        ]

    If empty:
        []

    On error:
        {"error": "<message>"}
    """

    @staticmethod
    def _to_json(obj):
        return json.dumps(obj, default=str, indent=2, ensure_ascii=False)

    def not_shared(self):
        click.echo(self._to_json([]))

    def print_sids(self, run_sids):
        result = [{'name': sid.name, 'isPrincipal': sid.is_principal, 'accessType': sid.access_type}
                  for sid in run_sids]
        click.echo(self._to_json(result))

    def error(self, message):
        click.echo(json.dumps({'error': message}), err=True)


def create_share_print_service(output):
    if output == 'json':
        return JsonSharePrintService()
    return PrettyTableSharePrintService()
