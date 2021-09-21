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
import os
import requests


def get_required_env_var(name):
    val = os.getenv(name)
    if val is None:
        raise RuntimeError('Required environment variable "%s" is not set.' % name)
    return val


class API(object):

    def __init__(self):
        self.api = get_required_env_var('API')
        self.api_token = get_required_env_var('API_TOKEN')
        self.__headers__ = {
            'Accept': 'application/json',
            'Authorization': 'Bearer ' + self.api_token,
            'Content-Type': 'application/json',
        }

    def call(self, method, data, http_method=None, error_message=None):
        url = '{}/{}'.format(self.api.strip('/'), method)
        if not http_method:
            if data:
                response = requests.post(url, data, headers=self.__headers__, verify=False)
            else:
                response = requests.get(url, headers=self.__headers__, verify=False)
        else:
            if http_method.lower() == 'get':
                response = requests.get(url, headers=self.__headers__, verify=False)
            elif http_method.lower() == 'post':
                response = requests.post(url, data, headers=self.__headers__, verify=False)
            elif http_method.lower() == 'delete':
                if data:
                    response = requests.delete(url, data=data, headers=self.__headers__, verify=False)
                else:
                    response = requests.delete(url, headers=self.__headers__, verify=False)
            elif http_method.lower() == 'put':
                response = requests.put(url, data, headers=self.__headers__, verify=False)
            else:
                if data:
                    response = requests.post(url, data, headers=self.__headers__, verify=False)
                else:
                    response = requests.get(url, headers=self.__headers__, verify=False)
        response_data = json.loads(response.text)
        message_text = error_message if error_message else 'Failed to fetch data from server'
        if 'status' not in response_data:
            raise RuntimeError('{}. Server responded with status: {}.'
                               .format(message_text, str(response_data.status_code)))
        if response_data['status'] != 'OK':
            raise RuntimeError('{}. Server responded with message: {}'.format(message_text, response_data['message']))
        else:
            return response_data

    def create_ontology(self, ontology):
        response = self.call('ontologies', json.dumps(ontology))
        if 'payload' in response and 'id' in response['payload']:
            return response['payload']
        if 'message' in response:
            raise RuntimeError(response['message'])
        else:
            raise RuntimeError("Failed to create ontology.")

    def get_by_external_id(self, external_id, parent_id):
        query = 'ontologies/external?externalId=%s' % str(external_id)
        if parent_id:
            query += "&parentId=%s" % str(parent_id)
        response = self.call(query, None)
        if 'payload' in response:
            return response['payload']
        if 'message' in response:
            raise RuntimeError("Failed to load ontology by external id '%s' and parent '%s'. Reason: %s"
                               % (external_id, parent_id, response['message']))
        else:
            return None

    def update(self, ontology, id):
        response = self.call('ontologies/%s' % str(id), json.dumps(ontology), http_method="put")
        if 'payload' in response and 'id' in response['payload']:
            return response['payload']
        if 'message' in response:
            raise RuntimeError(response['message'])
        else:
            raise RuntimeError("Failed to update ontology.")

    def get_roots(self, ontology_type):
        response = self.call('ontologies/tree?type=%s' % str(ontology_type).upper(), None)
        if 'payload' in response:
            return response['payload']
        if 'message' in response:
            raise RuntimeError(response['message'])
        else:
            raise RuntimeError("Failed to update ontology.")
