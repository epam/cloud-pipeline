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

from permission_model import PermissionModel


class PipelineModel(object):
    def __init__(self):
        self.identifier = None
        self.name = None
        self.repository = None
        self.permissions = []

    @classmethod
    def load(cls, json):
        instance = PipelineModel()
        instance.identifier = json['id']
        instance.name = json['name']
        if 'description' in json:
            instance.description = json['description']
        if 'repository' in json:
            instance.repository = json['repository']
        if 'owner' in json and json['owner'].lower() != 'unauthorized':
            owner = PermissionModel()
            owner.name = json['owner']
            owner.principal = True
            owner.access_level = 40
            instance.permissions.append(owner)
        if 'permissions' in json:
            for permission_json in json['permissions']:
                permission = PermissionModel.load(permission_json)
                permission_exists = len([x for x in instance.permissions if x.principal == permission.principal and x.name.lower() == permission.name.lower()]) > 0
                if not permission_exists and permission.access_level > 0:
                    instance.permissions.append(permission)
        return instance
