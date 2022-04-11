# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import subprocess

import pathlib
import time

import json
import requests


class ServerError(RuntimeError):
    pass


class HTTPError(ServerError):
    pass


class APIError(ServerError):
    pass


class CloudPipelineAPI:

    def __init__(self, api_url, api_token, attempts=3, timeout=5, connection_timeout=10):
        self.api_url = api_url
        self.api_token = api_token
        self.header = {'content-type': 'application/json',
                       'Authorization': 'Bearer {}'.format(self.api_token)}
        self.attempts = attempts
        self.timeout = timeout
        self.connection_timeout = connection_timeout

    def get_preferences(self):
        return self._request('GET', 'preferences') or {}

    def _request(self, http_method, endpoint, data=None):
        url = '{}/{}'.format(self.api_url, endpoint)
        count = 0
        exceptions = []
        while count < self.attempts:
            count += 1
            try:
                response = requests.request(method=http_method, url=url, data=json.dumps(data),
                                            headers=self.header, verify=False,
                                            timeout=self.connection_timeout)
                if response.status_code != 200:
                    raise HTTPError('API responded with http status %s.' % str(response.status_code))
                response_data = response.json()
                status = response_data.get('status') or 'ERROR'
                message = response_data.get('message') or 'No message'
                if status != 'OK':
                    raise APIError('%s: %s' % (status, message))
                return response_data.get('payload')
            except APIError as e:
                raise e
            except Exception as e:
                exceptions.append(e)
            time.sleep(self.timeout)
        raise exceptions[-1]


def _mkdir(path):
    pathlib.Path(path).mkdir(parents=True, exist_ok=True)


home_dir = os.getenv('CP_DTS_TUNNEL_HOME', os.getcwd())
log_dir = os.path.join(home_dir, 'logs')
log_file = os.path.join(log_dir, 'runtime.log')
pipe_executable_path = os.path.join(home_dir, 'pipe')
api_host = os.getenv('CP_API_SRV_INTERNAL_HOST', 'cp-api-srv.default.svc.cluster.local')
api_port = os.getenv('CP_API_SRV_INTERNAL_PORT', '31080')
api_url = 'https://{}:{}/pipeline/restapi'.format(api_host, api_port)
api_token = os.getenv('CP_API_JWT_ADMIN')

_mkdir(log_dir)

api = CloudPipelineAPI(api_url=api_url, api_token=api_token)
preferences = api.get_preferences()

input_ports = '5000-5009'
output_ports = '5010-5019'
for preference in preferences:
    if preference.get('name') == 'dts.tunnel.input.ports':
        input_ports = preference.get('value', input_ports)
    if preference.get('name') == 'dts.tunnel.output.ports':
        output_ports = preference.get('value', output_ports)

subprocess.check_call([pipe_executable_path, 'tunnel', 'receive',
                       '-ip', input_ports,
                       '-tp', output_ports,
                       '-v', 'DEBUG',
                       '-f'])
