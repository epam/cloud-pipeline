# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

from .util.omics_utils import OmicsUrl


class OmicsStoreType:

    OMICS_REF = "AWS_OMICS_REF"
    OMICS_SEQ = "AWS_OMICS_SEQ"

    def __init__(self):
        pass


class OmicsStoreImportJob:

    def __init__(self):
        self.id = None
        self.store_id = None
        self.service_role_arn = None
        self.status = None
        self.creation_time = None

    @classmethod
    def load(cls, json):
        instance = cls()
        instance.id = json['id']
        instance.store_id = json['storeId']
        instance.session_token = json['serviceRoleArn'] if 'serviceRoleArn' in json else None
        instance.expiration = json['status'] if 'status' in json else None
        instance.region = json['creationTime'] if 'creationTime' in json else None
        return instance


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


class OmicsDataStorage:

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
        self.cloud_store_id = None

    @classmethod
    def load(cls, json):
        instance = OmicsDataStorage()
        instance.id = json['id']
        instance.path = json['path']
        instance.root = json['root']
        instance.mask = json['mask']
        instance.sensitive = json['sensitive']
        instance.type = json['type']
        instance.cloud_store_id, instance.region_name = OmicsUrl.find_store_id_and_region_code(instance.path)
        return instance

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

    def load_storage(self, name):
        storage_payload = self.get_storage(name)
        bucket = OmicsDataStorage.load(storage_payload)
        # When regular bucket is mounted inside a sensitive run, the only way
        # check whether actual write will be allowed is to request write credentials
        # from API server and parse response
        if bucket.is_write_allowed():
            bucket.ro = not self._is_write_allowed(bucket)
        return bucket

    def _is_write_allowed(self, bucket):
        try:
            self.get_temporary_credentials(bucket, write=True)
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

    def import_omics_file(self, storage, sources, name, omics_file_type, description, sample_id, subject_id,
                          generated_from=None, reference_arn=None):
        def _prepare_sources(sources):
            size = len(sources)
            if not sources or size == 0 or size > 2:
                raise ValueError("sources should be present and have no more then 2 items!")

            return {
                "source1": sources[0],
                "source2": sources[1] if 1 < size else None
            }

        data = {
            "sources": [
                {
                    "description": description,
                    "generatedFrom": generated_from,
                    "name": name,
                    "sourceFileType": omics_file_type,
                    "sampleId": sample_id,
                    "subjectId": subject_id,
                    "referenceArn": reference_arn,
                    "sourceFiles": _prepare_sources(sources)
                }
            ]
        }
        payload = self._retryable_call('POST', 'omicsstore/{}/import'.format(storage.id), data=data) or {}
        return OmicsStoreImportJob.load(payload)

    def get_temporary_credentials(self, storage, list=False, read=False, write=False):
        logging.info('Getting temporary credentials for data storage #%s...' % storage.id)
        data = [{
            'id': storage.id,
            'list': list,
            'read': read,
            'write': write
        }]
        payload = self._retryable_call('POST', 'datastorage/tempCredentials/', data=data) or {}
        return TemporaryCredentials.load(payload)

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
