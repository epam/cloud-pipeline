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

from ..config import Config


class API(object):
    def __init__(self):
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        self.__config__ = Config.instance()
        self.__headers__ = {'Content-Type': 'application/json',
                            'Authorization': 'Bearer {}'.format(self.__config__.access_key)}
        if self.__config__.is_proxy_enabled():
            self.__proxies__ = self.__config__.resolve_proxy()
        else:
            self.__proxies__ = None

    def get_url_for_method(self, method):
        return '{}/{}'.format(self.__config__.api.strip('/'), method)

    def get_headers(self):
        return self.__headers__

    def call(self, method, data, http_method=None, error_message=None):
        url = '{}/{}'.format(self.__config__.api.strip('/'), method)
        if not http_method:
            if data:
                response = requests.post(url, data, headers=self.__headers__, verify=False, proxies=self.__proxies__)
            else:
                response = requests.get(url, headers=self.__headers__, verify=False, proxies=self.__proxies__)
        else:
            if http_method.lower() == 'get':
                response = requests.get(url, headers=self.__headers__, verify=False, proxies=self.__proxies__)
            elif http_method.lower() == 'post':
                response = requests.post(url, data, headers=self.__headers__, verify=False, proxies=self.__proxies__)
            elif http_method.lower() == 'delete':
                if data:
                    response = requests.delete(url, data=data, headers=self.__headers__, verify=False, proxies=self.__proxies__)
                else:
                    response = requests.delete(url, headers=self.__headers__, verify=False, proxies=self.__proxies__)
            else:
                if data:
                    response = requests.post(url, data, headers=self.__headers__, verify=False, proxies=self.__proxies__)
                else:
                    response = requests.get(url, headers=self.__headers__, verify=False, proxies=self.__proxies__)
        response_data = json.loads(response.text)
        message_text = error_message if error_message else 'Failed to fetch data from server'
        if 'status' not in response_data:
            raise RuntimeError('{}. Server responded with status: {}.'
                               .format(message_text, str(response_data.status_code)))
        if response_data['status'] != 'OK':
            raise RuntimeError('{}. Server responded with message: {}'.format(message_text, response_data['message']))
        else:
            return response_data

    @classmethod
    def instance(cls):
        return cls()
