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
        instance.session_token = json['token']
        instance.expiration = json['expiration']
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
        self.mask = None
        self.sensitive = False
        self.ro = False
        self.type = None
        self.region_name = None

    @classmethod
    def load(cls, json, region_info=[]):
        instance = DataStorage()
        instance.id = json['id']
        instance.mask = json['mask']
        instance.sensitive = json['sensitive']
        instance.type = json['type']
        if region_info and 'regionId' in json:
            instance.region_name = cls._find_region_code(json['regionId'], region_info)
        return instance

    @staticmethod
    def _find_region_code(region_id, region_data):
        for region in region_data:
            if int(region.get('id', 0)) == int(region_id):
                return region.get('regionId', None)
        return None

    def is_read_allowed(self):
        return self._is_allowed(self._READ_MASK)

    def is_write_allowed(self):
        return not self.ro and self._is_allowed(self._WRITE_MASK)

    def _is_allowed(self, mask):
        return self.mask & mask == mask


class CloudPipelineClient:

    def __init__(self, api, token):
        self._api = api.strip('/')
        self._token = token
        self.__headers__ = {'Content-Type': 'application/json',
                            'Authorization': 'Bearer {}'.format(self._token)}

    def get_storage(self, name):
        logging.info('Getting data storage %s' % name)
        response_data = self._get('datastorage/findByPath?id={}'.format(name))
        if 'payload' in response_data:
            bucket = DataStorage.load(response_data['payload'], self.get_region_info())
            # When regular bucket is mounted inside a sensitive run, the only way
            # check whether actual write will be allowed is to request write credentials
            # from API server and parse response
            if bucket.is_write_allowed():
                bucket.ro = not self._check_write_allowed(bucket)
            return bucket
        return None

    def get_region_info(self):
        logging.info('Getting region info')
        response_data = self._get('cloud/region/info')
        if 'payload' in response_data:
            return response_data['payload']
        if response_data['status'] == 'OK':
            return []
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to load regions info")

    def get_temporary_credentials(self, bucket):
        logging.info('Getting temporary credentials for data storage #%s' % bucket.id)
        operation = {
            'id': bucket.id,
            'read': bucket.is_read_allowed(),
            'write': bucket.is_write_allowed()
        }
        credentials = self._get_temporary_credentials([operation])
        return credentials

    def get_storage_lifecycle(self, bucket, path, is_file=False):
        logging.info('Getting storage lifecycle for data storage #%s' % bucket.id)
        request_url = '/datastorage/%s/lifecycle/restore/effectiveHierarchy?path=%s&pathType=%s' \
                      % (str(bucket.id), path, 'FILE' if is_file else 'FOLDER&recursive=false')
        response_data = self._get(request_url)
        if 'payload' in response_data:
            items = []
            for lifecycles_json in response_data['payload']:
                lifecycle = StorageLifecycle.load(lifecycles_json)
                items.append(lifecycle)
            return items
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        return None

    def _check_write_allowed(self, bucket):
        try:
            self.get_temporary_credentials(bucket)
            return True
        except RuntimeError as e:
            if 'Write operations are forbidden' in str(e.message):
                return False
            else:
                raise e

    def _get_temporary_credentials(self, data):
        response_data = self._post('datastorage/tempCredentials/', data=json.dumps(data))
        if 'payload' in response_data:
            return TemporaryCredentials.load(response_data['payload'])
        elif 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError('Failed to load credentials from server.')

    def _get(self, method, *args, **kwargs):
        return self._call(method, http_method='get', *args, **kwargs)

    def _post(self, method, *args, **kwargs):
        return self._call(method, http_method='post', *args, **kwargs)

    def _call(self, method, http_method, data=None, error_message=None):
        url = '{}/{}'.format(self._api, method)
        if http_method == 'get':
            response = requests.get(url, headers=self.__headers__, verify=False)
        else:
            response = requests.post(url, data=data, headers=self.__headers__, verify=False)
        response_data = json.loads(response.text)
        message_text = error_message if error_message else 'Failed to fetch data from server'
        if 'status' not in response_data:
            raise RuntimeError('{}. Server responded with status: {}.'
                               .format(message_text, str(response_data.status_code)))
        if response_data['status'] != 'OK':
            raise RuntimeError('{}. Server responded with message: {}'.format(message_text, response_data['message']))
        else:
            return response_data
