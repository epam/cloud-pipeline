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

from base_api import API

API_GET_ALL_USERS = 'users'
API_CREATE_USER = 'user'
API_BLOCK_USER = 'user/{user_id}/block'
API_GET_CURRENT_USER_NAME = 'whoami'

API_SET_GROUP_BLOCK_STATUS = 'group/{group_name}/block'
API_GET_ALL_BLOCK_STATUSES = 'groups/block'

API_GET_ALL_ROLES = 'role/loadAll'
API_CREATE_ROLE = 'role/create'
API_ASSIGN_ROLE_TO_USER = 'role/{role_id}/assign'


class UserSyncAPI(API):
    def __init__(self, api_path, access_key):
        super(UserSyncAPI, self).__init__(api_path, access_key)

    def load_all_roles(self):
        response = self.call(API_GET_ALL_ROLES, http_method='GET')
        return self.parse_response(response=response, default_value=[])

    def create_role(self, role_name, is_user_default):
        response = self.call(API_CREATE_ROLE, params={'roleName': role_name, 'userDefault': is_user_default},
                             http_method='POST')
        return self.parse_response(response=response)

    def load_all_users(self):
        response = self.call(API_GET_ALL_USERS, http_method='GET')
        return self.parse_response(response=response, default_value=[])

    def get_current_user_name(self):
        response = self.call(API_GET_CURRENT_USER_NAME, http_method='GET')
        payload = self.parse_response(response=response, default_value=[])
        if payload and 'userName' in payload:
            return payload['userName']
        else:
            return None

    def create_user(self, name, roles_ids):
        response = self.call(API_CREATE_USER, data=API.to_json({'userName': name, 'roleIds': roles_ids}),
                             http_method='POST')
        return self.parse_response(response=response, default_value=[])

    def get_available_groups_blocking(self):
        response = self.call(API_GET_ALL_BLOCK_STATUSES, http_method='GET')
        blocking_statuses = self.parse_response(response=response, default_value=[])
        return {status['groupName']: status['blocked'] for status in blocking_statuses}

    def set_user_blocking(self, user_id, status):
        self.call(API_BLOCK_USER.format(user_id), params={'blockStatus': status}, http_method='GET')

    def assign_users_to_roles(self, role_id, user_ids):
        self.call(API_ASSIGN_ROLE_TO_USER.format(role_id=role_id), params={'userIds': user_ids})

    def update_group_blocking_status(self, group_name, status=True):
        response = self.call(API_SET_GROUP_BLOCK_STATUS.format(group_name=group_name),
                             params={'blockStatus': str(status).lower()})
        return self.parse_response(response=response)
