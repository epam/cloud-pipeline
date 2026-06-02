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

"""
ACL printing utilities for different output formats.
Supports both pretty-table and JSON output formats.
"""

import json
from abc import ABCMeta, abstractmethod

import click
from prettytable import prettytable


def _build_name_by_type(entity, entity_type):
    """Build display name based on entity type."""
    if str(entity_type).lower() == 'tool':
        registry = entity.get('registry')
        if 'image' in entity:
            return "/".join([registry, entity['image']]) if registry else entity['image']
        return None
    if str(entity_type).lower() == 'docker_registry':
        return entity.get('path')
    return entity.get('name')


class AclPrintService(object):
    """Abstract base class for ACL printing services."""
    
    __metaclass__ = ABCMeta

    @abstractmethod
    def print_acl(self, permissions_list, owner):
        """
        Print ACL permissions for an object.
        
        Args:
            permissions_list: List of permission objects
            owner: Owner of the object
        """
        pass

    @abstractmethod
    def print_sid_objects(self, sid_name, available_entities):
        """
        Print objects accessible to a user or group.
        
        Args:
            sid_name: Name of the user or group
            available_entities: Dictionary of entity types to entities
        """
        pass

    @abstractmethod
    def error(self, message, err=True):
        """
        Print an error message.

        Args:
            message: Error message text
            err: Write to stderr when True
        """
        pass


class PrettyTableAclPrintService(AclPrintService):
    """ACL print service using pretty-table format."""

    def print_acl(self, permissions_list, owner):
        """Print ACL permissions in table format."""
        click.echo("Owner: %s" % owner)
        
        if len(permissions_list) > 0:
            permissions_table = prettytable.PrettyTable()
            permissions_table.field_names = ["SID", "Principal", "Allow", "Deny"]
            permissions_table.align = "r"
            
            for permission in permissions_list:
                permissions_table.add_row([
                    permission.name,
                    permission.principal,
                    permission.get_allowed_permissions_description(),
                    permission.get_denied_permissions_description()
                ])
            
            click.echo(permissions_table)
            click.echo()
        else:
            click.echo('No user permissions are configured')

    def print_sid_objects(self, sid_name, available_entities):
        """Print accessible objects in table format."""
        if len(available_entities) == 0:
            click.echo("No accessible objects available for '%s'" % sid_name)
            return
        
        entities_table = prettytable.PrettyTable()
        entities_table.field_names = ["Type", "Name", "Owner"]
        entities_table.align = "r"
        
        for entity_type, entities in available_entities.items():
            for entity in entities:
                entity_name = _build_name_by_type(entity, entity_type)
                if not entity_name:
                    continue
                owner = entity.get('owner', '')
                entities_table.add_row([entity_type, entity_name, owner])
        
        click.echo(entities_table)
        click.echo()

    def error(self, message, err=True):
        click.echo(u'Error: {}'.format(message), err=err)


class JsonAclPrintService(AclPrintService):
    """ACL print service using JSON format with camelCase fields."""

    @staticmethod
    def _to_json(obj):
        """Convert object to JSON string with proper formatting."""
        return json.dumps(obj, indent=2, default=str, ensure_ascii=False)

    def print_acl(self, permissions_list, owner):
        """Print ACL permissions in JSON format."""
        acl_dict = {
            'owner': owner,
            'permissions': []
        }
        
        for permission in permissions_list:
            perm_dict = {
                'sid': permission.name,
                'principal': permission.principal,
                'allow': permission.get_allowed_permissions_list(),
                'deny': permission.get_denied_permissions_list()
            }
            acl_dict['permissions'].append(perm_dict)
        
        click.echo(self._to_json(acl_dict))

    def print_sid_objects(self, sid_name, available_entities):
        """Print accessible objects in JSON format."""
        objects_list = []
        
        for entity_type, entities in available_entities.items():
            for entity in entities:
                entity_name = _build_name_by_type(entity, entity_type)
                if not entity_name:
                    continue
                
                obj_dict = {
                    'type': entity_type,
                    'name': entity_name,
                    'owner': entity.get('owner', '')
                }
                objects_list.append(obj_dict)
        
        click.echo(self._to_json(objects_list))

    def error(self, message, err=True):
        click.echo(json.dumps({'error': message}), err=err)


def create_acl_print_service(output_format):
    """
    Factory function to create appropriate ACL print service.
    
    Args:
        output_format: Output format ('json' or None for table format)
        
    Returns:
        AclPrintService: Appropriate print service instance
    """
    if output_format == 'json':
        return JsonAclPrintService()
    return PrettyTableAclPrintService()
