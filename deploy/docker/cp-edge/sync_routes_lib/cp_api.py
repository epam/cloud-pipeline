# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import os
import json
import time
import urllib3
import requests
from .config import NUMBER_OF_RETRIES, SECS_TO_WAIT_BEFORE_RETRY
from .logger import do_log

urllib3.disable_warnings()

class CloudPipelineAPI:
    def __init__(self, api_url, api_token):
        self.api_url = api_url
        self.api_token = api_token
        self.headers = {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer {}'.format(self.api_token)
        }

    def call_api(self, method_url, data=None):
        full_url = self.api_url.rstrip('/') + '/' + method_url.lstrip('/')
        result = None
        for n in range(NUMBER_OF_RETRIES):
            try:
                do_log('Calling API {}'.format(full_url))
                if data:
                    response = requests.post(full_url, verify=False, data=data, headers=self.headers)
                else:
                    response = requests.get(full_url, verify=False, headers=self.headers)
                
                try:
                    response_data = json.loads(response.text)
                except ValueError:
                    do_log('Calling API ... NOT OK (JSON decode error)\nResponse: {}'.format(response.text))
                    # Retrying might help if it was a transient proxy error returning HTML
                else:
                    if response_data.get('status') == 'OK':
                        do_log('Calling API ... OK')
                        result = response_data
                        break
                    else:
                        err_msg = response_data.get('message', 'No error message available')
                        do_log('Calling API ... NOT OK ({})'.format(full_url))
                        print(err_msg)
                        do_log('As the API technically succeeded, it will not be retried')
                        break
            # todo: Use only specific exception types
            except Exception as api_exception:
                do_log('Calling API ... NOT OK ({})\n{}'.format(full_url, str(api_exception)))

            if n < NUMBER_OF_RETRIES - 1:
                do_log('Sleep for {} sec and perform API call again ({}/{})'.format(SECS_TO_WAIT_BEFORE_RETRY, n + 2, NUMBER_OF_RETRIES))
                time.sleep(SECS_TO_WAIT_BEFORE_RETRY)
            else:
                do_log('All attempts failed. API call failed')
        return result
