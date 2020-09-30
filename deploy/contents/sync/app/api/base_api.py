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
import requests
import time
import urllib3
from requests import ConnectionError

API_METADATA_LOAD = 'metadata/load'
API_METADATA_UPDATE = 'metadata/update'
API_CALL_RETRIES_COUNT = 10
API_CALL_RETRIES_TIMEOUT_SEC = 60.0


class API(object):
    def __init__(self, api_host_url, access_key):
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        self.api = api_host_url + '/pipeline/restapi'
        self.__headers__ = {'Content-Type': 'application/json',
                            'Authorization': 'Bearer {}'.format(access_key)}

    def get_url_for_method(self, method):
        return '{}/{}'.format(self.api.strip('/'), method)

    def get_headers(self):
        return self.__headers__

    def call(self, method, data=None, params=None, http_method=None, error_message=None, files=None):
        retries = API_CALL_RETRIES_COUNT
        while retries > 0:
            try:
                return self.call_plain(method, data, params, http_method, error_message, files)
            except ConnectionError:
                retries -= 1
                print('{} is unreachable, waiting for a timeout and trying again'.format(self.api))
                time.sleep(API_CALL_RETRIES_TIMEOUT_SEC)
        raise RuntimeError('{} is unreachable and retries count exceeded!'.format(self.api))

    def call_plain(self, method, data, params, http_method, error_message, files):
        url = '{}/{}'.format(self.api.strip('/'), method)
        if not http_method:
            if data:
                response = requests.post(url, data, headers=self.__headers__, verify=False)
            else:
                response = requests.get(url, headers=self.__headers__, verify=False)
        else:
            if http_method.lower() == 'get':
                response = requests.get(url, headers=self.__headers__, params=params, verify=False)
            elif http_method.lower() == 'put':
                response = requests.put(url, data, headers=self.__headers__, params=params, verify=False, files=files)
            elif http_method.lower() == 'post':
                headers = {}
                if files:
                    headers.update(self.__headers__)
                    headers.pop('Content-Type')
                else:
                    headers = self.__headers__
                response = requests.post(url, data, headers=headers, params=params, verify=False, files=files)
            elif http_method.lower() == 'delete':
                if data:
                    response = requests.delete(url, data=data, headers=self.__headers__, verify=False)
                else:
                    response = requests.delete(url, headers=self.__headers__, params=params, verify=False)
            else:
                if data:
                    response = requests.post(url, data, headers=self.__headers__, verify=False)
                else:
                    response = requests.get(url, headers=self.__headers__, verify=False)
        content_type = response.headers.get('Content-Type')
        if content_type.startswith('application/json'):
            response_data = json.loads(response.text)
            message_text = error_message if error_message else 'Failed to fetch data from server'
            if 'status' not in response_data:
                raise RuntimeError('{}. Server responded with status: {}.'
                                   .format(message_text, str(response_data.status_code)))
            if response_data['status'] != 'OK':
                raise RuntimeError('{}. Server responded with message: {}'.format(message_text, response_data['message']))
            else:
                return response_data
        else:
            return response.content

    def load_entities_metadata(self, entities_ids, entity_class):
        data = []
        for entity_id in entities_ids:
            data.append({'entityId': entity_id, 'entityClass': entity_class})
        response = self.call(API_METADATA_LOAD, data=json.dumps(data), http_method='POST')
        return self.parse_response(response, default_value=[])

    def upload_metadata(self, metadata_entity):
        self.call(API_METADATA_UPDATE, data=json.dumps(metadata_entity), http_method='POST')

    @staticmethod
    def to_json(obj):
        return json.dumps(obj)

    @staticmethod
    def parse_response(response, default_value=None):
        value = default_value
        if 'payload' in response and response['payload']:
            value = response['payload']
        return value
