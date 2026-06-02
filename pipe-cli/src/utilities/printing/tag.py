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
from abc import ABCMeta, abstractmethod

import click
import prettytable
from future.utils import iteritems


class TagPrintService(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def print_tags(self, entity_class, entity_id, data):
        pass

    @abstractmethod
    def error(self, message, err=True):
        pass


class PrettyTableTagPrintService(TagPrintService):

    def print_tags(self, entity_class, entity_id, data):
        if not data:
            click.echo("No metadata available for {} {}.".format(entity_class, entity_id))
            return
        table = prettytable.PrettyTable()
        table.field_names = ["Tag name", "Value", "Type"]
        table.align = "l"
        table.header = True
        for key, entry in iteritems(data):
            table.add_row([key, entry.get('value'), entry.get('type')])
        click.echo(table)

    def error(self, message, err=True):
        click.echo(u'Error: {}'.format(message), err=err)


class JsonTagPrintService(TagPrintService):

    @staticmethod
    def _to_json(obj):
        return json.dumps(obj, indent=2, default=str, ensure_ascii=False)

    def print_tags(self, entity_class, entity_id, data):
        if not data:
            click.echo(self._to_json([]))
            return
        tags = [{'tagName': key, 'value': entry.get('value'), 'type': entry.get('type')}
                for key, entry in iteritems(data)]
        click.echo(self._to_json(tags))

    def error(self, message, err=True):
        click.echo(json.dumps({'error': message}), err=err)


def create_tag_print_service(output_format):
    if output_format == 'json':
        return JsonTagPrintService()
    return PrettyTableTagPrintService()
