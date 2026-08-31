# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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


class UnauthorizedAPI:

    def __init__(self, api, proxies=None):
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        self.__api__ = api
        self.__proxies__ = proxies

    def get(self, method):
        url = '{}/{}'.format(self.__api__.strip('/'), method)
        response = requests.get(url, verify=False, proxies=self.__proxies__)
        return self._build_response_data(response)

    @classmethod
    def _build_response_data(cls, response, error_message=None):
        response_data = json.loads(response.text)
        message_text = error_message if error_message else 'Failed to fetch data from server'
        if 'status' not in response_data:
            raise RuntimeError('{}. Server responded with status: {}.'
                               .format(message_text, str(response_data.status_code)))
        if response_data['status'] != 'OK':
            raise RuntimeError('{}. Server responded with message: {}'.format(message_text,
                                                                              response_data['message']))
        else:
            return response_data


class AccessAPI:

    def __init__(self, api):
        self.api = api

    def get_code(self, code_challenge):
        response_data = self.api.get('access/code?code_challenge=%s' % code_challenge)
        return response_data.get('payload', {})

    def get_token(self, code_verifier, code):
        response_data = self.api.get('access/token?code_verifier=%s&code=%s' % (code_verifier, code))
        return response_data.get('payload', {})
