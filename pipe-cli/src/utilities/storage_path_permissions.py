# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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
from src.api.user import User


def verify_storage_path_permissions_allowed(storage):
    if not storage.path_permissions_enabled:
        return
    current_user = User.whoami()
    if is_user_admin_or_owner(current_user, storage.owner):
        return
    click.echo('Storages with enabled path permissions currently are not available for plain users.',
               err=True)
    sys.exit(1)


def is_user_admin_or_owner(user, storage_owner):
    if user.get('admin', False):
        return True
    if [r for r in user.get('roles', []) if r.get('name', '').upper() == 'ROLE_ADMIN']:
        return True
    if [g for g in user.get('groups', []) if g.upper() == 'ROLE_ADMIN']:
        return True
    if user.get('userName').upper() == str(storage_owner).upper():
        return True
    return False
