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
import json

from src.api.base import API


class Entity(API):

    def __init__(self):
        super(Entity, self).__init__()

    @classmethod
    def load_by_id_or_name(cls, identifier, entity_class):
        api = cls.instance()
        response_data = api.call('entities?identifier={}&aclClass={}'.format(identifier, str(entity_class).upper()),
                                 None)
        if 'payload' in response_data and 'id' in response_data['payload'] and 'aclClass' in response_data['payload']:
            return response_data['payload']
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to load entity by entity id or entity name.")

    @classmethod
    def load_available_entities(cls, sid_name, principal, acl_class=None):
        api = cls.instance()
        body = {
            "name": sid_name,
            "principal": principal
        }
        query = 'entities'
        if acl_class:
            query += '?aclClass=' + str(acl_class).upper()
        response_data = api.call(query, json.dumps(body))
        if 'payload' in response_data:
            return response_data['payload']
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            return dict()
