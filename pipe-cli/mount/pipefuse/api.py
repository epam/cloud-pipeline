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
import logging
import requests
import time


class CloudType:

    S3 = 'S3'
    GS = 'GS'

    def __init__(self):
        pass


class TemporaryCredentials:

    def __init__(self):
        self.access_key_id = None
        self.secret_key = None
        self.session_token = None
        self.expiration = None
        self.region = None

    @classmethod
    def load(cls, json):
        instance = cls()
        instance.access_key_id = json['keyID'] if 'keyID' in json else None
        instance.secret_key = json['accessKey']
        instance.session_token = json['token'] if 'token' in json else None
        instance.expiration = json['expiration'] if 'expiration' in json else None
        instance.region = json['region'] if 'region' in json else None
        return instance


class StorageLifecycle:

    def __init__(self):
        self.path = None
        self.status = None
        self.restored_till = None

    @classmethod
    def load(cls, json):
        instance = StorageLifecycle()
        if 'path' in json:
            instance.path = json['path']
        if 'status' in json:
            instance.status = json['status']
        if 'restoredTill' in json:
            instance.restored_till = json['restoredTill']
        return instance

    def is_restored(self):
        return self.status == 'SUCCEEDED'


class DataStorage:
    _READ_MASK = 1
    _WRITE_MASK = 1 << 1

    def __init__(self):
        self.id = None
        self.path = None
        self.root = None
        self.mask = None
        self.sensitive = False
        self.ro = False
        self.type = None
        self.region_name = None

    @classmethod
    def load(cls, json, region_info=[]):
        instance = DataStorage()
        instance.id = json['id']
        instance.path = json['path']
        instance.root = json['root']
        instance.mask = json['mask']
        instance.sensitive = json['sensitive']
        instance.type = json['type']
        instance.region_name = cls._find_region_code(json.get('regionId', 0), region_info)
        instance.endpoint = cls._find_endpoint(json.get('regionId', 0), region_info)
        return instance

    @staticmethod
    def _find_region_code(region_id, region_data):
        for region in region_data:
            if int(region.get('id', 0)) == int(region_id):
                return region.get('regionId', None)
        return None

    @staticmethod
    def _find_endpoint(region_id, region_data):
        for region in region_data:
            if int(region.get('id', 0)) == int(region_id):
                return region.get('endpoint', None)
        return None

    def is_read_allowed(self):
        return self._is_allowed(self._READ_MASK)

    def is_write_allowed(self):
        return not self.ro and self._is_allowed(self._WRITE_MASK)

    def _is_allowed(self, mask):
        return self.mask & mask == mask


class ServerError(RuntimeError):
    pass


class HTTPError(ServerError):
    pass


class APIError(ServerError):
    pass


class CloudPipelineClient:

    def __init__(self, api, token):
        self._api = api.strip('/')
        self._token = token
        self.__headers__ = {'Content-Type': 'application/json',
                            'Authorization': 'Bearer {}'.format(self._token)}
        self.__attempts__ = 3
        self.__timeout__ = 5
        self.__connection_timeout__ = 10

    def init_bucket_object(self, name):
        storage_payload = self.get_storage(name)
        regions_payload = self.get_regions()
        bucket = DataStorage.load(storage_payload, regions_payload)
        # When regular bucket is mounted inside a sensitive run, the only way
        # check whether actual write will be allowed is to request write credentials
        # from API server and parse response
        if bucket.is_write_allowed():
            bucket.ro = not self._is_write_allowed(bucket)
        return bucket

    def _is_write_allowed(self, bucket):
        try:
            self.get_temporary_credentials(bucket)
            return True
        except RuntimeError as e:
            if 'Write operations are forbidden' in str(e):
                return False
            else:
                raise e

    def get_storage(self, name):
        logging.info('Getting data storage %s...' % name)
        return self._retryable_call('GET', 'datastorage/findByPath?id={}'.format(name)) or {}

    def get_regions(self):
        logging.info('Getting regions...')
        return self._retryable_call('GET', 'cloud/region/info') or []

    def get_temporary_credentials(self, bucket):
        logging.info('Getting temporary credentials for data storage #%s...' % bucket.id)
        data = [{
            'id': bucket.id,
            'read': bucket.is_read_allowed(),
            'write': bucket.is_write_allowed()
        }]
        payload = self._retryable_call('POST', 'datastorage/tempCredentials/', data=data) or {}
        return TemporaryCredentials.load(payload)

    def get_storage_lifecycle(self, bucket, path, is_file=False):
        logging.info('Getting storage lifecycle for data storage #%s...' % bucket.id)
        request_url = 'datastorage/%s/lifecycle/restore/effectiveHierarchy?path=%s&pathType=%s' \
                      % (str(bucket.id), path, 'FILE' if is_file else 'FOLDER&recursive=false')
        payload = self._retryable_call('GET', request_url) or []
        return [StorageLifecycle.load(lifecycles_json) for lifecycles_json in payload]

    def create_system_logs(self, entries):
        self._retryable_call('POST', 'log', data=entries)

    def whoami(self):
        return self._retryable_call('GET', 'whoami') or {}

    def _retryable_call(self, http_method, endpoint, data=None):
        url = '{}/{}'.format(self._api, endpoint)
        count = 0
        exceptions = []
        while count < self.__attempts__:
            count += 1
            try:
                response = requests.request(method=http_method, url=url, data=json.dumps(data),
                                            headers=self.__headers__, verify=False,
                                            timeout=self.__connection_timeout__)
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
            time.sleep(self.__timeout__)
        raise exceptions[-1]
