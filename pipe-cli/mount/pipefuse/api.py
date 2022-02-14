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
import traceback

import requests
import time


class ServerError(RuntimeError):
    pass


class HTTPError(ServerError):
    pass


class APIError(ServerError):
    pass


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

    def is_synthetic_read_allowed(self):
        return True

    def is_synthetic_write_allowed(self):
        return True

    def _is_allowed(self, mask):
        return self.mask & mask == mask


class User:

    def __init__(self, id, name, roles, groups, is_admin):
        self.id = id
        self.name = name
        self.roles = roles or []
        self.groups = groups or []
        self.is_admin = is_admin

    @classmethod
    def load(cls, json):
        return User(id=json.get('id'),
                    name=json.get('userName'),
                    roles=[role.get('name') for role in json.get('roles', [])],
                    groups=json.get('groups', []),
                    is_admin=json.get('admin', False))


class CloudPipelineClient:

    def __init__(self, api, token, attempts=3, timeout=5, connection_timeout=10):
        self._api = api.strip('/')
        self._token = token
        self._headers = {'Content-Type': 'application/json',
                         'Authorization': 'Bearer {}'.format(self._token)}
        self._attempts = attempts
        self._timeout = timeout
        self._connection_timeout = connection_timeout

    def get_user(self):
        logging.info('Loading current user...')
        try:
            response_payload = self._request('GET', 'whoami')
            return User.load(response_payload)
        except ServerError:
            raise RuntimeError('Failed to load current user:\n%s' % traceback.format_exc())

    def get_storage(self, name):
        logging.info('Loading data storage %s...' % name)
        try:
            response_payload = self._request('GET', 'datastorage/findByPath?id={}'.format(name))
            bucket = DataStorage.load(response_payload, self.get_region_info())
            # When regular bucket is mounted inside a sensitive run, the only way
            # check whether actual write will be allowed is to request write credentials
            # from API server and parse response
            if bucket.is_write_allowed():
                bucket.ro = not self._check_write_allowed(bucket)
            return bucket
        except ServerError:
            raise RuntimeError('Failed to load data storage %s:\n%s' % (name, traceback.format_exc()))

    def _check_write_allowed(self, bucket):
        try:
            self.get_temporary_credentials(bucket)
            return True
        except RuntimeError as e:
            if 'Write operations are forbidden' in str(e.message):
                return False
            else:
                raise e

    def get_region_info(self):
        logging.info('Load regions info...')
        try:
            response_payload = self._request('GET', 'cloud/region/info')
            return response_payload or []
        except ServerError:
            raise RuntimeError('Failed to load regions info:\n%s' % traceback.format_exc())

    def get_storage_permissions(self, bucket):
        logging.info('Loading storage object permissions...')
        data = {
            'id': bucket.id,
            'type': 'DATA_STORAGE'
        }
        try:
            response_payload = self._request('POST', 'storage/permission/batch/loadAll', data=data)
            return response_payload or []
        except ServerError:
            raise RuntimeError('Failed to load storage object permissions:\n%s' % traceback.format_exc())

    def move_storage_object_permissions(self, bucket, requests):
        logging.info('Moving storage object permissions...')
        data = {
            'id': bucket.id,
            'type': 'DATA_STORAGE',
            'requests': requests
        }
        try:
            self._request('PUT', 'storage/permission/batch/move', data=data)
        except ServerError:
            raise RuntimeError('Failed to move storage object permissions:\n%s' % traceback.format_exc())

    def delete_all_storage_object_permissions(self, bucket, requests):
        logging.info('Deleting all storage object permissions...')
        data = {
            'id': bucket.id,
            'type': 'DATA_STORAGE',
            'requests': requests
        }
        try:
            self._request('DELETE', 'storage/permission/batch/deleteAll', data=data)
        except ServerError:
            raise RuntimeError('Failed to delete all storage object permissions:\n%s' % traceback.format_exc())

    def get_temporary_credentials(self, bucket):
        logging.info('Loading temporary credentials for data storage #%s' % bucket.id)
        operation = {
            'id': bucket.id,
            'read': bucket.is_synthetic_read_allowed(),
            'write': bucket.is_synthetic_write_allowed()
        }
        try:
            response_payload = self._request('POST', 'datastorage/tempCredentials/', data=[operation])
            return TemporaryCredentials.load(response_payload)
        except ServerError:
            raise RuntimeError('Failed to load temporary credentials for data storage #%s:\n%s'
                               % (bucket.id, traceback.format_exc()))

    def _request(self, http_method, endpoint, data=None):
        url = '{}/{}'.format(self._api, endpoint)
        count = 0
        exceptions = []
        while count < self._attempts:
            count += 1
            try:
                response = requests.request(method=http_method, url=url, data=json.dumps(data),
                                            headers=self._headers, verify=False,
                                            timeout=self._connection_timeout)
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
            time.sleep(self._timeout)
        raise exceptions[-1]
