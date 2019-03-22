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

from base import API
import json
from entity import Entity


class EntitiesAPI(API):

    def __init__(self, api, access_token):
        super(EntitiesAPI, self).__init__(api, access_token)

    def filter(self, identifier, entity_class, page, page_size):
        payload = json.dumps({
            "folderId": identifier,
            "metadataClass": entity_class,
            "page": page,
            "pageSize": page_size}
        )
        response_data = self.call('metadataEntity/filter', payload)
        if 'payload' in response_data:
            payload = response_data['payload']
            total = 0
            result = []
            if 'totalCount' in payload:
                total = payload['totalCount']
            if 'elements' in payload:
                elements = payload['elements']
                for element in elements:
                    element_obj = Entity.load(element)
                    if element_obj is not None:
                        result.append(element_obj)
            return total, result
        return 0, []

    def load_all(self, identifier, entity_class):
        total = 0
        page = 0
        page_size = 20
        while page == 0 or page * page_size < total:
            page += 1
            total, results = self.filter(identifier, entity_class, page, page_size)
            for result in results:
                yield result

    def update_key(self, metadata_id, entity_id, column, column_type, value):
        data = dict()
        data[column] = {
            "type": column_type,
            "value": value
        }
        payload = json.dumps({
            "entityId": entity_id,
            "parentId": metadata_id,
            "data": data
        })
        self.call('metadataEntity/updateKey', payload)
