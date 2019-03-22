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

class Entity(object):

    def __init__(self):
        self.id = None
        self.data = {}
        self.external_id = None

    @classmethod
    def load(cls, json):
        instance = Entity()
        if not json:
            return instance
        if 'id' in json:
            instance.id = json['id']
        if 'externalId' in json:
            instance.external_id = json['externalId']
        if 'data' in json:
            instance.data = json['data']
        return instance
