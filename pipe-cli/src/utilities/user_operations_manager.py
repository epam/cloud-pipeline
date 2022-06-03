# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import os
import sys

import click

from prettytable import prettytable
from src.api.user import User


class UserOperationsManager:

    def __init__(self):
        self.user = User.whoami()

    @classmethod
    def import_users(cls, file_path, create_user, create_group, metadata_list):
        full_path = os.path.abspath(file_path)
        if not os.path.exists(full_path):
            click.echo("Specified file path '%s' doesn't exist" % full_path, err=True)
            sys.exit(1)
        if not os.path.isfile(full_path):
            click.echo("Specified path '%s' exists but not a file" % full_path, err=True)
            sys.exit(1)
        events = User().import_users(full_path, create_user, create_group, metadata_list)
        for event in events:
            click.echo("[%s] %s" % (event.get('status', ''), event.get('message', '')))

    def get_instance_limits(self, verbose=False):
        active_limits = User.load_launch_limits()
        if len(active_limits) == 0:
            click.echo('No restrictions on runs launching configured')
            return
        if not verbose:
            source, limit = min(active_limits.items(), key=lambda x: x[1])
            click.echo('The following restriction applied on runs launching: [{}: {}]'.format(source, limit))
        else:
            self.print_limits_table(active_limits)

    def print_limits_table(self, limits_dict):
        click.echo('The following restrictions applied on runs launching:\n')
        limit_details_table = prettytable.PrettyTable()
        limit_details_table.field_names = ['Source', 'Value']
        limit_details_table.sortby = 'Value'
        limit_details_table.align = 'l'
        for source, value in limits_dict.items():
            limit_details_table.add_row([source, value])
        click.echo(limit_details_table)

    def is_admin(self):
        return 'ROLE_ADMIN' in self.get_all_user_roles()

    def get_all_user_roles(self):
        user_groups = self.user.get('groups', [])
        user_roles = [role.get('name') for role in self.user.get('roles', [])]
        return set(user_groups + user_roles)

    def whoami(self):
        return self.user
