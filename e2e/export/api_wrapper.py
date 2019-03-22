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

import requests
import urllib3


class API(object):
    def __init__(self, api_path, access_key):
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        self.api = api_path
        self.__headers__ = {'Content-Type': 'application/json',
                            'Authorization': 'Bearer {}'.format(access_key)}

    def get_url_for_method(self, method):
        return '{}/{}'.format(self.api.strip('/'), method)

    def get_headers(self):
        return self.__headers__

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


class PipelineRunAPI(API):
    def __init__(self, api_path, access_key):
        super(PipelineRunAPI, self).__init__(api_path, access_key)

    def filter_runs(self, start_date, page, page_size):
        payload = json.dumps({
            'page': page,
            'pageSize': page_size,
            'startDateFrom': start_date,
            'statuses': ['SUCCESS', 'FAILURE', 'STOPPED']
        })
        response_data = self.call('/run/filter', payload, http_method='POST')
        return response_data['payload']


class UsersAPI(API):
    def __init__(self, api_path, access_key):
        super(UsersAPI, self).__init__(api_path, access_key)

    def get_users(self):
        response_data = self.call('/users', None, http_method='GET')
        return response_data['payload']
