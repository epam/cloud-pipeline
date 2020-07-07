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
import sys

import click
import future
import requests
import prettytable
from future.utils import iteritems

from src.api.entity import Entity
from src.api.user import User
from src.config import ConfigNotFoundError


class ACLOperations(object):

    @classmethod
    def get_classes(cls):
        return ['pipeline', 'folder', 'data_storage', 'configuration', 'docker_registry', 'tool', 'tool_group']

    @classmethod
    def set_acl(cls, identifier, object_type, sid, group, allow, deny, inherit):
        """ Set object permissions
        """

        try:
            entity = Entity.load_by_id_or_name(identifier, object_type)
            identifier = entity['id']
            all_permissions = User.permissions(identifier, entity['aclClass'])
            user_permissions = future.utils.lfilter(
                lambda permission: permission.name.lower() == sid.lower() and permission.principal != group,
                all_permissions)
            user_mask = 0
            if len(user_permissions) == 1:
                user_mask = user_permissions[0].mask

            if allow is None and deny is None and inherit is None:
                raise RuntimeError('You must specify at least one permission')

            permissions_masks = {
                'r': {
                    'allow': 1,
                    'deny': 1 << 1,
                    'inherit': 0,
                    'group': 1 | 1 << 1
                },
                'w': {
                    'allow': 1 << 2,
                    'deny': 1 << 3,
                    'inherit': 0,
                    'group': 1 << 2 | 1 << 3
                },
                'x': {
                    'allow': 1 << 4,
                    'deny': 1 << 5,
                    'inherit': 0,
                    'group': 1 << 4 | 1 << 5
                }
            }

            def check_permission(permission):
                exists_in_allow = allow is not None and permission.lower() in allow.lower()
                exists_in_deny = deny is not None and permission.lower() in deny.lower()
                exists_in_inherit = inherit is not None and permission.lower() in inherit.lower()
                if exists_in_allow + exists_in_deny + exists_in_inherit > 1:
                    raise RuntimeError('You cannot set permission (\'{}\') in multiple groups'.format(permission))

            check_permission('r')
            check_permission('w')
            check_permission('x')

            def modify_permissions_group(mask, permissions_group_mask, permission_mask):
                permissions_clear_mask = (1 | 1 << 1 | 1 << 2 | 1 << 3 | 1 << 4 | 1 << 5) ^ permissions_group_mask
                return (mask & permissions_clear_mask) | permission_mask

            def modify_permissions(mask, permissions_group_name, permissions):
                if permissions is not None:
                    for permission in permissions:
                        if permission.lower() not in permissions_masks:
                            raise RuntimeError('Unknown permission \'{}\''.format(permission))
                        else:
                            permissions_group_mask = permissions_masks[permission.lower()]['group']
                            permission_mask = permissions_masks[permission.lower()][permissions_group_name]
                            mask = modify_permissions_group(mask, permissions_group_mask, permission_mask)
                return mask

            user_mask = modify_permissions(user_mask, 'allow', allow)
            user_mask = modify_permissions(user_mask, 'deny', deny)
            user_mask = modify_permissions(user_mask, 'inherit', inherit)
            User.grant_permission(identifier, object_type, sid, not group, user_mask)
            click.echo('Permissions set')

        except ConfigNotFoundError as config_not_found_error:
            click.echo(str(config_not_found_error), err=True)
            sys.exit(1)
        except requests.exceptions.RequestException as http_error:
            click.echo('Http error: {}'.format(str(http_error)), err=True)
            sys.exit(1)
        except RuntimeError as runtime_error:
            click.echo('Error: {}'.format(str(runtime_error)), err=True)
            sys.exit(1)
        except ValueError as value_error:
            click.echo('Error: {}'.format(str(value_error)), err=True)
            sys.exit(1)

    @classmethod
    def view_acl(cls, identifier, object_type):
        """ View object permissions
        """

        try:
            permissions_list, owner = User.get_permissions(identifier, object_type)
            click.echo("Owner: %s" % owner)
            if len(permissions_list) > 0:
                permissions_table = prettytable.PrettyTable()
                permissions_table.field_names = ["SID", "Principal", "Allow", "Deny"]
                permissions_table.align = "r"
                for permission in permissions_list:
                    permissions_table.add_row([permission.name,
                                               permission.principal,
                                               permission.get_allowed_permissions_description(),
                                               permission.get_denied_permissions_description()])
                click.echo(permissions_table)
                click.echo()
            else:
                click.echo('No user permissions are configured')

        except ConfigNotFoundError as config_not_found_error:
            click.echo(str(config_not_found_error), err=True)
            sys.exit(1)
        except requests.exceptions.RequestException as http_error:
            click.echo('Http error: {}'.format(str(http_error)), err=True)
            sys.exit(1)
        except RuntimeError as runtime_error:
            click.echo('Error: {}'.format(str(runtime_error)), err=True)
            sys.exit(1)
        except ValueError as value_error:
            click.echo('Error: {}'.format(str(value_error)), err=True)
            sys.exit(1)

    @classmethod
    def print_sid_objects(cls, sid_name, principal, acl_class=None):
        try:
            available_entities = Entity.load_available_entities(cls.normalize_sid_name(sid_name, principal),
                                                                principal, acl_class)
            if len(available_entities) == 0:
                click.echo("No accessible objects available for '%s'" % sid_name)
                sys.exit(0)
            entities_table = prettytable.PrettyTable()
            entities_table.field_names = ["Type", "Name"]
            entities_table.align = "r"
            for entity_type, entities in iteritems(available_entities):
                for entity in entities:
                    entity_name = cls.build_name_by_type(entity, entity_type)
                    if not entity_name:
                        continue
                    entities_table.add_row([entity_type, entity_name])
            click.echo(entities_table)
            click.echo()
        except ConfigNotFoundError as config_not_found_error:
            click.echo(str(config_not_found_error), err=True)
            sys.exit(1)
        except requests.exceptions.RequestException as http_error:
            click.echo('Http error: {}'.format(str(http_error)), err=True)
            sys.exit(1)
        except RuntimeError as runtime_error:
            click.echo('Error: {}'.format(str(runtime_error)), err=True)
            sys.exit(1)
        except ValueError as value_error:
            click.echo('Error: {}'.format(str(value_error)), err=True)
            sys.exit(1)

    @staticmethod
    def build_name_by_type(entity, entity_type):
        if str(entity_type).lower() == 'tool':
            registry = entity['registry'] if 'registry' in entity else None
            if 'image' in entity:
                return "/".join([registry, entity['image']]) if registry else entity['image']
            return None
        if str(entity_type).lower() == 'docker_registry':
            return entity['path'] if 'path' in entity else None
        return entity['name'] if 'name' in entity else None

    @staticmethod
    def normalize_sid_name(sid_name, principal):
        role_prefix = "ROLE_"
        sid_name = str(sid_name).upper()
        if principal or sid_name.startswith(role_prefix):
            return sid_name
        return role_prefix + sid_name
