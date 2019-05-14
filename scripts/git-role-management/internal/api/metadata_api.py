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

import json

from .base import API


class Metadata(API):
    def __init__(self):
        super(Metadata, self).__init__()

    @classmethod
    def load(cls, entity_id, entity_class):
        api = cls.instance()
        payload = [
            {
                'entityId': entity_id,
                'entityClass': entity_class
            }
        ]
        response_data = api.call('metadata/load', json.dumps(payload))
        metadata = {}
        if 'payload' not in response_data or not response_data['payload']:
            return metadata
        entity = response_data['payload'][0]
        if not entity or 'data' not in entity:
            return metadata
        for key, conf_value in entity['data'].items():
            if 'value' in conf_value:
                metadata[key] = conf_value['value']
        return metadata

    @classmethod
    def update_keys(cls, entity_id, entity_class, metadata):
        api = cls.instance()
        payload = {
            'data': {key: {'value': value} for key, value in metadata.items()},
            'entity': {
                'entityId': entity_id,
                'entityClass': entity_class
            }
        }
        api.call('metadata/updateKeys', json.dumps(payload))

    class Class:
        PIPELINE = 'PIPELINE'
        FOLDER = 'FOLDER'
        DATA_STORAGE = 'DATA_STORAGE'
        DOCKER_REGISTRY = 'DOCKER_REGISTRY'
        TOOL = 'TOOL'
        TOOL_GROUP = 'TOOL_GROUP'
        CONFIGURATION = 'CONFIGURATION'
        METADATA_ENTITY = 'METADATA_ENTITY'
        ATTACHMENT = 'ATTACHMENT'
        CLOUD_REGION = 'CLOUD_REGION'
        PIPELINE_USER = 'PIPELINE_USER'
        ROLE = 'ROLE'
