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

from internal.model.mask import Mask
from internal.model.tool_model import ToolModel
from internal.model.user_model import UserModel


class StorageModel(object):

    def __init__(self):
        self.identifier = None
        self.name = None
        self.mask = None
        self.type = None
        self.path = None
        self.share_mount_id = None
        self.mount_source = None
        self.users = {}
        self.tools_to_mount = []

    @classmethod
    def load(cls, json):
        instance = StorageModel()
        entity_json = json.get('entity', {})
        permissions_json = json.get('permissions', [])
        if not entity_json:
            return None
        instance.identifier = entity_json['id']
        instance.name = entity_json['name']
        instance.mask = entity_json['mask']
        instance.type = entity_json['type']
        instance.share_mount_id = entity_json.get('fileShareMountId')
        instance.path = entity_json.get('pathMask')
        for tool_json in entity_json.get('toolsToMount', []):
            tool = ToolModel.load(tool_json)
            if tool:
                instance.tools_to_mount.append(tool)
        entity_owner = entity_json.get('owner', '')
        if entity_owner.strip().lower() != 'unauthorized':
            user = UserModel()
            user.username = entity_owner
            instance.users[user] = Mask.ALL
        for permission_json in permissions_json:
            sid_json = permission_json.get('sid', {})
            sid_mask = permission_json.get('mask')
            sid_name = sid_json.get('name')
            sid_principal = sid_json.get('principal')
            if not sid_name or not sid_principal or not sid_mask:
                continue
            user = UserModel()
            user.username = sid_name
            instance.users[user] = Mask.from_full(sid_mask)
        return instance

    def is_nfs(self):
        return self.type.lower() == 'nfs'
