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
from itertools import islice

import click

from src.api.entity import Entity
from src.api.pipeline_run import PipelineRun
from src.api.user import User
from src.utilities.printing.user import create_user_instances_print_service


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

    def get_instance_limits(self, verbose=False, output_format=None):
        print_service = create_user_instances_print_service(output_format)
        username = self.user['userName']
        active_runs_count = PipelineRun.count_user_runs(target_statuses=['RUNNING', 'RESUMING'], owner=username)
        active_limits = User.load_launch_limits(verbose)
        if len(active_limits) == 0:
            print_service.print_no_limits(username, active_runs_count)
            return
        sorted_limits = dict(sorted(active_limits.items(), key=lambda x: x[1]))
        if not verbose:
            source, value = next(iter(sorted_limits.items()))
            print_service.print_single_limits(username, active_runs_count, source, value)
            return
        print_service.print_limits(username, active_runs_count, sorted_limits)

    def is_admin(self):
        return 'ROLE_ADMIN' in self.get_all_user_roles()

    def is_owner(self, owner):
        return owner and owner == self.whoami().get('userName')

    def get_all_user_roles(self):
        user_groups = self.user.get('groups', [])
        user_roles = [role.get('name') for role in self.user.get('roles', [])]
        return set(user_groups + user_roles)

    def whoami(self):
        return self.user

    def has_storage_archive_permissions(self, storage_identifier, owner=None):
        if self.is_admin():
            return True
        if not owner:
            if storage_identifier:
                owner = self.get_owner(storage_identifier, 'DATA_STORAGE')
        if not owner:
            return False
        if self.is_owner(owner):
            return True
        user_roles = self.get_all_user_roles()
        if 'ROLE_STORAGE_ARCHIVE_MANAGER' in user_roles or 'ROLE_STORAGE_ARCHIVE_READER' in user_roles:
            return True
        return False

    @staticmethod
    def get_owner(identifier, acl_class):
        try:
            entity = Entity.load_by_id_or_name(identifier, acl_class)
            return entity.get('owner')
        except RuntimeError as e:
            if 'Access is denied' in str(e):
                return None
            raise e
