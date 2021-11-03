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

import click
import prettytable
from future.utils import iteritems

from src.api.metadata import Metadata


class MetadataOperations(object):

    @classmethod
    def set_metadata(cls, entity_class, entity_id, data):
        """

        :param entity_class: entity class
        :param entity_id: id or name of entity
        :param data: list of pairs in KEY=VALUE representation
        :return:
        """
        try:
            id = Metadata.find(entity_id, entity_class).entity_id
            updated_metadata = Metadata.update(id, entity_class, cls.convert_input_pairs_to_json(data))
            if not updated_metadata.entity_id:
                click.echo("No metadata available for {} {}.".format(entity_class, entity_id))
            else:
                click.echo("Metadata for {} {} updated.".format(entity_class, entity_id))
        except RuntimeError as runtime_error:
            if "No enum constant" and "AclClass" in str(runtime_error):
                click.echo("Error: Class '{}' does not exist.".format(entity_class), err=True)
            else:
                raise

    @classmethod
    def get_metadata(cls, entity_class, entity_id):
        try:
            id = Metadata.find(entity_id, entity_class).entity_id
            metadata = Metadata.load(id, entity_class)
            if not metadata.data:
                click.echo("No metadata available for {} {}.".format(entity_class, entity_id))
            else:
                click.echo(cls.create_table(metadata.data))
        except RuntimeError as runtime_error:
            if "No enum constant" and "AclClass" in str(runtime_error):
                click.echo("Error: Class '{}' does not exist.".format(entity_class), err=True)
            else:
                raise

    @classmethod
    def delete_metadata(cls, entity_class, entity_id, keys):
        """

        :param entity_class: entity class
        :param entity_id: id or name of entity
        :param keys: list of keys to delete
        :return:
        """
        try:
            id = Metadata.find(entity_id, entity_class).entity_id
            if not keys:
                deleted_metadata = Metadata.delete(id, entity_class)
                if not deleted_metadata.entity_id:
                    click.echo("No metadata available for {} {}.".format(entity_class, entity_id))
                else:
                    click.echo("Metadata for {} {} deleted.".format(entity_class, entity_id))
            else:
                deleted_metadata = Metadata.delete_keys(id, entity_class, cls.convert_list_of_keys_to_json(keys))
                if not deleted_metadata.entity_id:
                    click.echo("No metadata available for {} {}.".format(entity_class, entity_id))
                else:
                    click.echo("Deleted keys from metadata for {} {}: {}"
                               .format(entity_class, entity_id, ', '.join(keys)))
        except RuntimeError as runtime_error:
            if "No enum constant" and "AclClass" in str(runtime_error):
                click.echo("Error: Class '{}' does not exist.".format(entity_class), err=True)
            else:
                raise

    @classmethod
    def convert_input_pairs_to_json(cls, data):
        result = dict()
        for data_items_for_update in data:
            if "=" not in data_items_for_update:
                raise ValueError("Tags must be specified as KEY=VALUE pair.")
            pair = data_items_for_update.split("=", 1)
            value_type = "string"
            metadata_value = {
                "value": pair[1],
                "type": value_type
            }
            result.update({pair[0]: metadata_value})
        return result

    @classmethod
    def convert_list_of_keys_to_json(cls, keys):
        result = dict()
        for key in keys:
            result.update({key: {}})
        return result

    @classmethod
    def create_table(cls, metadata):
        table = prettytable.PrettyTable()
        table.field_names = ["Tag name", "Value", "Type"]
        table.align = "l"
        table.header = True
        for (key, entry) in iteritems(metadata):
            entry_value = None
            entry_type = None
            if 'value' in entry:
                entry_value = entry['value']
            if 'type' in entry:
                entry_type = entry['type']
            table.add_row([key, entry_value, entry_type])
        return table
