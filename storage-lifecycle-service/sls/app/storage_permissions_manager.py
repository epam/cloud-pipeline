# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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
from six import iteritems

from sls.app.cp_api_interface import CloudPipelineDataSource

DATA_STORAGE = 'DATA_STORAGE'


class StoragePermissionsManager:

    def __init__(self, api: CloudPipelineDataSource, storage_id: int):
        self._api = api
        self._storage_id = storage_id

    def get_users(self):
        users_with_permissions = {}  # {user_name: permission_mask}
        rw_users_from_roles = []  # [user_name]
        for storage_permission in self._get_storage_permissions():
            sid = storage_permission.get('sid')
            mask = storage_permission.get('mask')
            if not sid or not mask:
                continue
            name = sid.get('name')
            if not name:
                continue
            principal = sid.get('principal')
            if principal:
                users_with_permissions.update({name: mask})
                continue
            if not self._has_read_or_write_permission(int(mask)):
                continue
            role_name = str(name).upper()
            for role_user in self._get_role_users(role_name):
                user = role_user.get('userName')
                if user:
                    rw_users_from_roles.append(user)
        users = [user_name for user_name in rw_users_from_roles if users_with_permissions.get(user_name) is None]
        users.extend([user_name for user_name, mask in iteritems(users_with_permissions)
                      if self._has_read_or_write_permission(int(mask))])
        return set(users)

    def _get_storage_permissions(self):
        entity = self._api.load_entity_permissions(self._storage_id, DATA_STORAGE)
        if not entity:
            return []
        return entity.get('permissions', []) or []

    def _get_role_users(self, role_name):
        response = self._api.load_role_by_name(role_name)
        if not response:
            return []
        return response.get('users', []) or []

    def _has_read_or_write_permission(self, mask):
        return self._bit_enabled(1, mask) or self._bit_enabled(1 << 2, mask)

    @staticmethod
    def _bit_enabled(bit, mask):
        return mask & bit == bit
