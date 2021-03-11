# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import logging
import urllib3
import requests
import time
import json

logger = logging.getLogger('fsbrowser')
logger.setLevel(logging.INFO)
formatter = logging.Formatter('[%(levelname)s] %(asctime)s %(message)s')
handler = logging.StreamHandler()
handler.setFormatter(formatter)
logger.addHandler(handler)

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


class ServerError(RuntimeError):
    pass


class HTTPError(ServerError):
    pass


class APIError(ServerError):
    pass


class CloudPipelineAPI:
    _RESPONSE_CODE_OK = 200
    _RESPONSE_STATUS_OK = 'OK'

    def __init__(self, api, api_token, attempts=3, timeout=5, connection_timeout=10, page_size=100):
        self._api = api.rstrip('/')
        self._api_token = api_token
        self._attempts = attempts
        self._timeout = timeout
        self._connection_timeout = connection_timeout
        self._page_size = page_size
        self._headers = {
            'Accept': 'application/json',
            'Authorization': 'Bearer ' + self._api_token,
            'Content-Type': 'application/json',
        }

    def find_storage(self, storage_id_or_name):
        result = self._get('/datastorage/find?id=%s' % storage_id_or_name)
        return result or {}

    def get_storage_download_url(self, storage_id, paths):
        data = {
            'paths': paths
        }
        url = '/datastorage/%s/generateUrl' % str(storage_id)
        result = self._post(url, data=data)
        return result or []

    def get_storage_upload_url(self, storage_id, paths):
        url = '/datastorage/%s/generateUploadUrl' % str(storage_id)
        result = self._post(url, data=paths)
        return result or []

    def log_event(self, run_id, data):
        url = '/run/%s/log' % str(run_id)
        result = self._post(url, data)
        return result or {}

    def _get(self, url):
        return self._execute_request(url, 'get')

    def _post(self, url, data=None):
        return self._execute_request(url, 'post', data=data)

    def _execute_request(self, method_url, http_method, data=None):
        url = self._api + method_url
        logger.debug('Calling %s...', url)
        count = 0
        exceptions = []
        while count < self._attempts:
            count += 1
            try:
                response = requests.request(method=http_method, url=url, data=json.dumps(data) if data else None,
                                            headers=self._headers, verify=False, timeout=self._connection_timeout)
                if response.status_code != self._RESPONSE_CODE_OK:
                    raise HTTPError('API responded with http status %s.' % str(response.status_code))
                response_json = response.json()
                status = response_json.get('status')
                message = response_json.get('message')
                if not status:
                    raise APIError('API responded without any status.')
                if status != self._RESPONSE_STATUS_OK:
                    if message:
                        raise APIError('API responded with status %s and error message: %s.' % (status, message))
                    else:
                        raise APIError('API responded with status %s.' % status)
                return response_json.get('payload')
            except Exception as e:
                exceptions.append(e)
                logger.warning('An error has occurred during request %s/%s to API: %s', count, self._attempts, str(e))
            time.sleep(self._timeout)
        logger.warning('Exceeded maximum retry count %s for API request.', self._attempts)
        raise exceptions[-1]


class CloudPipelineApiProvider(object):

    def __init__(self):
        self.api = CloudPipelineAPI(os.environ.get('API'), os.environ.get('API_TOKEN'))

    def load_storage_id_by_name(self, name):
        storage = self.api.find_storage(name)
        storage_id = storage.get('id', '')
        if not storage or not storage_id:
            raise RuntimeError('Failed to find storage by name' % name)
        return storage_id

    def get_download_url(self, storage_id, path):
        result = self.api.get_storage_download_url(storage_id, [path])
        if len(result) == 1 and 'url' in result[0]:
            return result[0]
        raise RuntimeError("Failed to generate download url")

    def get_upload_url(self, storage_id, path):
        result = self.api.get_storage_upload_url(storage_id, [path])
        if len(result) == 1 and 'url' in result[0]:
            return result[0]
        raise RuntimeError("Failed to generate upload url")

    def log_event(self, run_id, data):
        self.api.log_event(run_id, data)
