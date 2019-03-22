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

from user_model import UserModel


class StorageModel(object):
    def __init__(self):
        self.identifier = None
        self.name = None
        self.mask = None
        self.type = None
        self.path = None
        self.mount_source = None
        self.users = []

    @classmethod
    def load(cls, json):
        instance = StorageModel()
        entity_json = json['entity'] if 'entity' in json else None
        permissions_json = json['permissions'] if 'permissions' in json else None
        if not entity_json:
            return None
        instance.identifier = entity_json['id']
        instance.name = entity_json['name']
        instance.mask = entity_json['mask']
        instance.type = entity_json['type']
        instance.path = entity_json['pathMask'] if 'pathMask' in entity_json else None
        if 'owner' in entity_json and entity_json['owner'].lower() != 'unauthorized':
            user = UserModel()
            user.username = entity_json['owner']
            instance.users.append(user)
        if permissions_json is not None:
            for permission_json in permissions_json:
                if 'sid' in permission_json and 'mask' in permission_json:
                    mask = int(permission_json['mask'])
                    if mask & 1 == 1 and mask & 4 == 4:
                        sid_json = permission_json['sid']
                        if 'principal' in sid_json and sid_json['principal'] and 'name' in sid_json:
                            user = UserModel()
                            user.username = sid_json['name']
                            instance.users.append(user)
        return instance

    def is_nfs(self):
        return self.type.lower() == 'nfs'
