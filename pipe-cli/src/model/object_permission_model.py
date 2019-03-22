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

from ..utilities import permissions_manager


class ObjectPermissionModel(object):
    def __init__(self):
        self.mask = 0
        self.name = None
        self.principal = False
        self.read_allowed = False
        self.read_denied = False
        self.write_allowed = False
        self.write_denied = False
        self.execute_allowed = False
        self.execute_denied = False
        self.extended_mask = False

    @classmethod
    def load(cls, json):
        instance = ObjectPermissionModel()
        if 'mask' in json:
            instance.mask = json['mask']
        if 'sid' in json:
            if 'name' in json['sid']:
                instance.name = json['sid']['name']
            if 'principal' in json['sid']:
                instance.principal = json['sid']['principal']
        return instance

    def parse_mask(self, extended=False):
        self.extended_mask = extended
        self.read_allowed = permissions_manager.read_allowed(self.mask, extended)
        self.write_allowed = permissions_manager.write_allowed(self.mask, extended)
        self.execute_allowed = permissions_manager.execute_allowed(self.mask, extended)
        if extended:
            self.read_denied = permissions_manager.read_denied(self.mask, extended)
            self.write_denied = permissions_manager.write_denied(self.mask, extended)
            self.execute_denied = permissions_manager.execute_denied(self.mask, extended)

    def get_permissions_description(self):
        descriptions = []
        if self.read_allowed:
            descriptions.append('Read')
        if self.write_allowed:
            descriptions.append('Write')
        if self.execute_allowed:
            descriptions.append('Execute')
        return ', '.join(descriptions)

    def get_allowed_permissions_description(self):
        descriptions = []
        if self.read_allowed:
            descriptions.append('Read')
        if self.write_allowed:
            descriptions.append('Write')
        if self.execute_allowed:
            descriptions.append('Execute')
        return ', '.join(descriptions)

    def get_denied_permissions_description(self):
        descriptions = []
        if self.read_denied:
            descriptions.append('Read')
        if self.write_denied:
            descriptions.append('Write')
        if self.execute_denied:
            descriptions.append('Execute')
        return ', '.join(descriptions)
