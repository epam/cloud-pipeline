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

class PermissionModel(object):
    def __init__(self):
        self.name = None
        self.principal = False
        self.mask = 0
        self.access_level = 0

    @classmethod
    def load(cls, json):
        instance = PermissionModel()
        if 'mask' in json:
            instance.mask = int(json['mask'])
            read_allowed = instance.mask & 1 == 1
            write_allowed = instance.mask & 4 == 4
            if write_allowed and read_allowed:
                instance.access_level = 40
            elif read_allowed:
                instance.access_level = 20
        if 'sid' in json:
            if 'name' in json['sid']:
                instance.name = json['sid']['name']
            if 'principal' in json['sid']:
                instance.principal = json['sid']['principal']
        return instance
