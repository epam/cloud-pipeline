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

from src.api.base import API
from src.model.metadata_model import MetadataModel


class Metadata(API):

    def __init__(self):
        super(Metadata, self).__init__()

    @classmethod
    def find(cls, identifier, entity_class):
        api = cls.instance()
        response_data = api.call('metadata/find?entityName={}&entityClass={}'.format(identifier,
                                                                                      str(entity_class).upper()), None)
        if 'payload' in response_data:
            return MetadataModel.load(response_data['payload'])
        if 'status' in response_data and response_data['status'] == "OK":
            return MetadataModel()
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to find entity id by entity name.")

    @classmethod
    def load(cls, entity_id, entity_class):
        api = cls.instance()
        data = json.dumps([{
            'entityId': entity_id,
            'entityClass': str(entity_class).upper()
        }])
        response_data = api.call('metadata/load', data=data, http_method='POST')
        if 'payload' in response_data:
            for response_data_item in response_data['payload']:
                return MetadataModel.load(response_data_item)
        if 'status' in response_data and response_data['status'] == "OK":
            return MetadataModel()
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to load metadata.")

    @classmethod
    def update(cls, entity_id, entity_class, metadata):
        api = cls.instance()
        data = json.dumps({
            "entity": {
                "entityId": entity_id,
                "entityClass": str(entity_class).upper()
            },
            "data": metadata
        })
        response_data = api.call('metadata/updateKeys', data=data, http_method='POST')
        if 'payload' in response_data:
            return MetadataModel.load(response_data['payload'])
        if 'status' in response_data and response_data['status'] == "OK":
            return MetadataModel()
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to update metadata.")

    @classmethod
    def delete(cls, entity_id, entity_class):
        api = cls.instance()
        data = json.dumps({
            'entityId': entity_id,
            'entityClass': str(entity_class).upper()
        })
        response_data = api.call('metadata/delete', data=data, http_method='DELETE')
        if 'payload' in response_data:
            return MetadataModel.load(response_data['payload'])
        if 'status' in response_data and response_data['status'] == "OK":
            return MetadataModel()
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to delete metadata.")

    @classmethod
    def delete_keys(cls, entity_id, entity_class, metadata):
        api = cls.instance()
        data = json.dumps({
            "entity": {
                "entityId": entity_id,
                "entityClass": str(entity_class).upper()
            },
            "data": metadata
        })
        response_data = api.call('metadata/deleteKeys', data=data, http_method='DELETE')
        if 'payload' in response_data:
            return MetadataModel.load(response_data['payload'])
        if 'status' in response_data and response_data['status'] == "OK":
            return MetadataModel()
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to delete metadata keys.")
