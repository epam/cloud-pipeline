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

from role_model import RoleModel
from ..config import Config

class UserModel(object):
    def __init__(self):
        self.identifier = None
        self.username = None
        self.git_username = None
        self.email = None
        self.friendly_name = None
        self.roles = []
        self.groups = []
        self.attributes = {}

    @classmethod
    def load(cls, json):
        config = Config.instance()
        instance = UserModel()
        instance.identifier = json['id']
        instance.username = json['userName']
        instance.git_username = UserModel.get_username_safe(json['userName'])
        if 'email' in json:
            instance.email = json['email']
        if 'roles' in json:
            for role in json['roles']:
                instance.roles.append(RoleModel.load(role))
        if 'groups' in json:
            instance.groups = list(json['groups'])
        if instance.email is None and 'attributes' in json and config.email_attribute_name in json['attributes']:
            instance.email = json['attributes'][config.email_attribute_name]
        if 'attributes' in json and config.name_attribute_name in json['attributes']:
            instance.friendly_name = json['attributes'][config.name_attribute_name]
        if instance.friendly_name is None and instance.email is not None:
            instance.friendly_name = UserModel.get_username_safe(instance.email)
        return instance

    @classmethod
    def get_username_safe(cls, name):
        if name:
            return name.encode('utf8').split('@')[0]
        else:
            return None

