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

class MetadataModel(object):

    def __init__(self):
        self.entity_id = None
        self.entity_class = None
        self.data = {}

    @classmethod
    def load(cls, json):
        instance = MetadataModel()
        if not json:
            return instance
        if 'entity' in json:
            entity = json['entity']
            instance.entity_id = entity['entityId']
            instance.entity_class = entity['entityClass']
        if 'data' in json:
            instance.data = json['data']
        return instance
