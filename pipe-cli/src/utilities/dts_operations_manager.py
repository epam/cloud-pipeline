# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import click
import json
import sys
from prettytable import prettytable

from src.api.dts import DTS
from src.model.dts_model import DtsEncoder


class DtsOperationsManager:

    _DTS_TABLE_HEADERS = ['ID', 'Name', 'Schedulable']
    _PREF_DELIMITER = "="

    def __init__(self):
        pass

    def create(self, url, name, schedulable, prefixes, preferences, json_out):
        registry = DTS.create(url, name, schedulable, prefixes, self._convert_preferences_list_to_dict(preferences))
        self._print_registry(registry, json_out)

    def list(self, registry_id, json_out):
        if registry_id:
            registry = DTS.load(registry_id)
            self._print_registry(registry, json_out)
        else:
            registries = DTS.load_all()
            if json_out:
                self._print_dts_json(registries)
            else:
                self._print_registries_prettytable(registries)

    def upsert_preferences(self, registry_id, preferences_list, json_out):
        if not preferences_list:
            click.echo('Preferences should not be empty!', err=True)
            sys.exit(1)
        updated_registry = DTS.update_preferences(registry_id, self._convert_preferences_list_to_dict(preferences_list))
        self._print_registry(updated_registry, json_out)

    def delete_preferences(self, registry_id, preferences_keys, json_out):
        if not preferences_keys:
            click.echo('Preferences keys to be removed should not be empty!', err=True)
            sys.exit(1)
        updated_registry = DTS.delete_preferences(registry_id, preferences_keys)
        self._print_registry(updated_registry, json_out)

    def _convert_preferences_list_to_dict(self, preferences_list):
        preferences_dict = {}
        for preference_entry in preferences_list:
            preference_value_and_key = preference_entry.split(self._PREF_DELIMITER, 1)
            if len(preference_value_and_key) != 2:
                click.echo('Error [%s]: preference declaration should contain a delimiter!' % preference_entry, err=True)
                sys.exit(1)
            else:
                preferences_dict[preference_value_and_key[0]] = preference_value_and_key[1]
        return preferences_dict

    def _print_registry(self, registry, json_out):
        if json_out:
            self._print_dts_json(registry)
        else:
            self._print_single_registry_pretty(registry)

    def _print_dts_json(self, object):
        click.echo(json.dumps(object, cls=DtsEncoder))

    def _print_single_registry_pretty(self, registry):
        registry_info_table = prettytable.PrettyTable()
        registry_info_table.field_names = ['key', 'value']
        registry_info_table.align = 'l'
        registry_info_table.set_style(12)
        registry_info_table.header = False
        registry_info_table.add_row(['ID:', registry.id])
        registry_info_table.add_row(['Name:', registry.name])
        registry_info_table.add_row(['URL:', registry.url])
        registry_info_table.add_row(['Created:', registry.created_date])
        registry_info_table.add_row(['Schedulable:', registry.schedulable])
        click.echo(registry_info_table)
        self._print_list_as_table('Prefixes', registry.prefixes)
        self._print_list_as_table('Preferences', self.get_flat_preferences(registry))

    def get_flat_preferences(self, registry):
        flat_preferences = []
        for preference, value in registry.preferences.items():
            flat_preferences.append(preference + ': ' + value)
        return flat_preferences

    def _print_list_as_table(self, header_name, elements):
        click.echo()
        if elements:
            self._echo_title('{}:'.format(header_name))
            for prefix in elements:
                click.echo(prefix)
        else:
            click.echo('No {} specified.'.format(header_name.lower()))

    def _echo_title(self, title, line=True):
        click.echo(title)
        if line:
            for i in title:
                click.echo('-', nl=False)
            click.echo('')

    def _print_registries_prettytable(self, registries):
        table = self._init_table()
        for registry in registries:
            table.add_row(self._convert_registry_to_prettytable_row(registry))
        click.echo(table)

    def _init_table(self):
        table = prettytable.PrettyTable()
        table.field_names = self._DTS_TABLE_HEADERS
        table.align = "l"
        table.header = True
        return table

    def _convert_registry_to_prettytable_row(self, registry):
        return [registry.id, registry.name, registry.schedulable]
