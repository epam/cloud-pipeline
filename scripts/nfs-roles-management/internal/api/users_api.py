# Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

from internal.api.base import API

DAV_SCOPE_ATTR_KEY = 'dav_scope'
DAV_SCOPE_VALUES = [ 'owner' ]

class Users(API):

    def __init__(self, config=None):
        super(Users, self).__init__(config)

    def loadAll(self):
        response_data = self.call('users', None)
        all_users = {}
        if 'payload' in response_data:
            scoped_users_ids = self.loadScopedUsers()
            for user_entity in response_data['payload']:
                if 'userName' not in user_entity or 'id' not in user_entity:
                    continue
                user_name = user_entity['userName'].strip().lower()
                user_id = user_entity['id']
                if user_id in scoped_users_ids:
                    user_entity[DAV_SCOPE_ATTR_KEY] = scoped_users_ids[user_id]
                all_users[user_name] = user_entity
        return all_users

    def loadScopedUsers(self):
        scoped_users_ids = {}
        for scope in DAV_SCOPE_VALUES:
            response_data = self.call('metadata/search?entityClass=PIPELINE_USER&key={}&value={}'.format(DAV_SCOPE_ATTR_KEY, scope), None)
            if 'payload' in response_data:
                for entity in response_data['payload']:
                    if not 'entityId' in entity:
                        continue
                    user_id = int(entity['entityId'])
                    if user_id in scoped_users_ids:
                        scoped_users_ids[user_id].append(scope)
                    else:
                        scoped_users_ids[user_id] = [ scope ]
        return scoped_users_ids
