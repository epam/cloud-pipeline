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
import time
from pipeline import Logger
from paths import LocalToS3, S3ToLocal, LocalToAzure, AzureToLocal, Path


class _TransferStatus:
    CREATED = 'CREATED'
    RUNNING = 'RUNNING'
    SUCCESS = 'SUCCESS'
    FAILURE = 'FAILURE'
    STOPPED = 'STOPPED'


class _TransferPathType:
    LOCAL = 'LOCAL'
    GOOGLE_CLOUD_STORAGE = 'GOOGLE_CLOUD_STORAGE'
    AWS_STORAGE = 'S3'
    AZURE_STORAGE = 'AZ'


class DataTransferServiceClient:

    def __init__(self, dts_url, api_token, pipeline_url, pipeline_token, pooling_delay):
        self.dts_url = dts_url if dts_url.endswith('/') else dts_url + '/'
        self.api_token = api_token
        self.credentials = {'api': pipeline_url, 'apiToken': pipeline_token}
        self.pooling_delay = pooling_delay

    def transfer_data(self, data_paths, log_task):
        if len(data_paths) > 0:
            Logger.info('Transferring %d path(s)' % len(data_paths), task_name=log_task)
            transfers = map(self.__schedule_transfer_task, data_paths)
            for transfer in transfers:
                if transfer is None:
                    raise RuntimeError('Upload via DTS failed')
            remaining_ids = map(lambda transfer: transfer['id'], transfers)
            while remaining_ids:
                current_ids = list(remaining_ids)
                for id in current_ids:
                    transfer_task = self.__get_transfer_task(id)
                    source_path = transfer_task['source']['path']
                    destination_path = transfer_task['destination']['path']
                    if transfer_task['status'] == _TransferStatus.SUCCESS:
                        remaining_ids.remove(id)
                        Logger.info('Data transfer from source %s to destination %s has finished'
                                    % (source_path, destination_path), task_name=log_task)
                    elif transfer_task['status'] == _TransferStatus.FAILURE:
                        remaining_ids.remove(id)
                        reason = transfer_task['reason'] if 'reason' in transfer_task else 'No reason available'
                        Logger.fail("Data transfer from source %s to destination %s went bad due to the reason: '%s'"
                                    % (source_path, destination_path, reason), task_name=log_task)
                        raise RuntimeError('Data transfer went bad for source %s' % source_path)
                    else:
                        time.sleep(self.pooling_delay)
                if not len(remaining_ids) == len(current_ids) and remaining_ids:
                    Logger.info('%d data transfers are still being processed' % len(remaining_ids), task_name=log_task)
            Logger.info('All data transfers have finished successfully', task_name=log_task)
        else:
            Logger.warn('No files for data transfer were found', task_name=log_task)

    def __schedule_transfer_task(self, path):
        if isinstance(path, LocalToS3):
            data = self.__build_transfer_data(path,
                                              from_storage=_TransferPathType.LOCAL,
                                              to_storage=_TransferPathType.AWS_STORAGE)
        elif isinstance(path, S3ToLocal):
            data = self.__build_transfer_data(path,
                                              from_storage=_TransferPathType.AWS_STORAGE,
                                              to_storage=_TransferPathType.LOCAL)
        elif isinstance(path, LocalToAzure):
            data = self.__build_transfer_data(path,
                                              from_storage=_TransferPathType.LOCAL,
                                              to_storage=_TransferPathType.AZURE_STORAGE)
        elif isinstance(path, AzureToLocal):
            data = self.__build_transfer_data(path,
                                              from_storage=_TransferPathType.AZURE_STORAGE,
                                              to_storage=_TransferPathType.LOCAL)
        else:
            raise RuntimeError('Incompatible path type. Actual: %s. Required: one of %s.' %
                               (type(path), (Path.all())))
        if path.rules:
            data['included'] = path.rules
        return self.__post('transfer', data=data)

    def __build_transfer_data(self, path, from_storage, to_storage):
        return {
            'source': self.__build_storage_item(path.source_path, from_storage),
            'destination': self.__build_storage_item(path.destination_path, to_storage)
        }

    def __build_storage_item(self, path, type):
        return {
            'path': path,
            'type': type,
            'credentials': self.credentials
        }

    def __get_transfer_task(self, id):
        return self.__get('transfer/%d' % id)

    def __post(self, api_method, data=None):
        return self.__http_call('POST', api_method, data)

    def __get(self, api_method, data=None):
        return self.__http_call('GET', api_method, data)

    def __http_call(self, http_method, api_method, data=None):
        try:
            if data:
                response = requests.request(http_method, str(self.dts_url + api_method), headers=self.__headers(),
                                            data=json.dumps(data))
            else:
                response = requests.request(http_method, str(self.dts_url + api_method), headers=self.__headers())
            if response.status_code != 200:
                raise RuntimeError('Dts call ends with %d response code due to: %s' % (response.status_code,
                                                                                       response.reason))

            body = response.json()
            if 'status' in body and body['status'] == 'ERROR':
                raise RuntimeError('Dts call ends with an error: %s' % body['message'])
            elif 'payload' in body:
                return body['payload']
            else:
                return None
        except BaseException as e:
            raise RuntimeError('An error during call to data transfer service: %s' % e.message)

    def __headers(self):
        return {
            'Authorization': 'Bearer %s' % self.api_token,
            'Content-Type': 'application/json'
        }
