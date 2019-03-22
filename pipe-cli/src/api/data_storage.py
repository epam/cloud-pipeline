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

from dateutil.parser import parse
from future.standard_library import install_aliases
install_aliases()

from urllib.parse import urlparse, urlencode
from urllib.request import urlopen, Request
from urllib.error import HTTPError

import json
from src.model.data_storage_tmp_credentials_model import TemporaryCredentialsModel
from .base import API
from ..model.data_storage_model import DataStorageModel
from ..model.data_storage_item_model import DataStorageItemModel
from ..model.data_storage_item_modification_result import DataStorageItemModificationResultModel
from ..model.data_storage_item_download_url import DataStorageItemDownloadUrl
from ..model.data_storage_wrapper_type import WrapperType


class DataStorage(API):
    def __init__(self):
        super(DataStorage, self).__init__()

    @classmethod
    def load_from_uri(cls, path):
        url = urlparse(path)
        requested_scheme = url.scheme
        if requested_scheme not in WrapperType.cloud_schemes():
            raise RuntimeError('Supported schemes for datastorage are: {}. '
                               'Actual scheme is "{}".'
                               .format('"' + '", "'.join(WrapperType.cloud_schemes()) + '"', requested_scheme))
        storage = DataStorage.get(url.netloc)
        expected_scheme = WrapperType.cloud_scheme(storage.type)
        if not WrapperType.is_dynamic_cloud_scheme(requested_scheme) and requested_scheme != expected_scheme:
            raise RuntimeError('Requested datastorage scheme differs with its type. '
                               'Expected scheme is "{}". '
                               'Actual scheme is "{}".'.format(expected_scheme, requested_scheme))

        return storage, url.path[1:]

    @classmethod
    def list(cls):
        api = cls.instance()
        response_data = api.call('datastorage/loadAll', None)
        if 'payload' not in response_data:
            return
        for data_storages_json in response_data['payload']:
            data_storage = DataStorageModel.load(data_storages_json)
            yield data_storage

    @classmethod
    def get_by_name(cls, name):
        if name is None:
            return None
        storages = DataStorage.list()
        for storage in storages:
            if storage.path is not None and storage.path.lower() == name.lower():
                return storage
        raise ValueError("Datastorage with name {} does not exist!".format(name))

    @classmethod
    def get(cls, name):
        if name is None:
            return None
        api = cls.instance()
        response_data = api.call('datastorage/find?id={}'.format(name), None)
        if 'payload' in response_data:
            return DataStorageModel.load(response_data['payload'])
        return None

    @classmethod
    def get_by_id(cls, identifier):
        api = cls.instance()
        response_data = api.call('datastorage/{}/load'.format(identifier), None)
        if 'status' in response_data and response_data['status'] == 'OK' and 'payload' in response_data:
            return DataStorageModel.load(response_data['payload'])
        return None

    @classmethod
    def list_items(cls, bucket_identifier, path):
        api = cls.instance()
        url = 'datastorage/{}/list'.format(bucket_identifier)
        if path is not None and len(path) > 0:
            url = url + '?path={}'.format(path)
        response_data = api.call(url, None)
        if 'payload' in response_data:
            for item_json in response_data['payload']:
                yield DataStorageItemModel.load(item_json)

    @classmethod
    def create_folder(cls, bucket_identifier, relative_path):
        api = cls.instance()
        data = json.dumps([{'action': 'Create', 'type': 'Folder', 'path': relative_path}])
        response_data = api.call('datastorage/{}/list'.format(bucket_identifier), data=data, http_method='POST')
        return DataStorageItemModificationResultModel.load(response_data)

    @classmethod
    def remove_item(cls, bucket_identifier, item_type, relative_path):
        api = cls.instance()
        data = json.dumps([{'type': item_type, 'path': relative_path}])
        response_data = api.call('datastorage/{}/list'.format(bucket_identifier), data=data, http_method='DELETE')
        return DataStorageItemModificationResultModel.load(response_data)

    @classmethod
    def move_item(cls, bucket_identifier, item_type, relative_path_from, relative_path_to):
        api = cls.instance()
        data = json.dumps([{
            'action': 'Move',
            'type': item_type,
            'path': relative_path_to,
            'oldPath': relative_path_from
        }])
        response_data = api.call('datastorage/{}/list'.format(bucket_identifier), data=data, http_method='POST')
        return DataStorageItemModificationResultModel.load(response_data)

    @classmethod
    def generate_download_url(cls, bucket_identifier, path):
        api = cls.instance()
        response_data = api.call('datastorage/{}/generateUrl?path={}'.format(bucket_identifier, path), None)
        if 'payload' in response_data:
            return DataStorageItemDownloadUrl.load(response_data['payload'])
        return None

    @classmethod
    def generate_download_urls(cls, bucket_identifier, paths):
        api = cls.instance()
        data = json.dumps({'paths': paths})
        response_data = api.call('datastorage/{}/generateUrl'.format(bucket_identifier), data=data, http_method='POST')
        if 'payload' in response_data:
            for item_json in response_data['payload']:
                yield DataStorageItemDownloadUrl.load(item_json)

    @classmethod
    def save(cls, name, path, description, sts_duration, lts_duration, versioning, backup_duration, type,
             parent_folder_id, on_cloud, region_id=None):
        api = cls.instance()
        body = json.dumps({
            'name': name if name else None,
            'path': path if path else None,
            'description': description if description else None,
            'type': type if type else None,
            'parentFolderId': parent_folder_id if parent_folder_id else None,
            'storagePolicy': __create_policy__(sts_duration, lts_duration, versioning, backup_duration),
            'regionId': region_id
        })
        response_data = api.call('datastorage/save?cloud={}'.format(on_cloud), data=body, http_method='POST',
                                 error_message='Failed to create new datastorage')
        if 'payload' in response_data:
            return DataStorageModel.load(response_data['payload'])
        elif 'message' in response_data:
            return RuntimeError(response_data['message'])
        else:
            return RuntimeError('Failed to create new datastorage')

    @classmethod
    def delete(cls, name, on_cloud):
        api = cls.instance()
        storage_id = cls.get_by_name(name).identifier
        response_data = api.call('datastorage/{}/delete?cloud={}'.format(storage_id, on_cloud),
                                 data=None, http_method='DELETE', error_message='Failed to delete a datastorage "{}"'
                                 .format(str(name)))
        return DataStorageModel.load(response_data['payload'])

    @classmethod
    def mvtodir(cls, name, folder_id):
        datastorage = cls.get_by_name(name)
        policy = datastorage.policy

        api = cls.instance()
        data = json.dumps({'id': datastorage.identifier,
                           'name': datastorage.name,
                           'path': datastorage.path,
                           'description': datastorage.description,
                           'storagePolicy': {
                               'backupDuration': policy.backup_duration,
                               'shortTermStorageDuration': policy.sts_duration,
                               'longTermStorageDuration': policy.lts_duration,
                               'versioningEnabled': policy.versioning_enabled
                           },
                           'type': datastorage.type,
                           'parentFolderId': folder_id
                           })
        return api.call('datastorage/update', data=data, http_method='POST')

    @classmethod
    def get_temporary_credentials(cls, source_bucket, destination_bucket, command, versioning=False):
        data = cls.create_operation_info(source_bucket, destination_bucket, command, versioning=versioning)
        return DataStorage._get_temporary_credentials(data)

    @classmethod
    def get_single_temporary_credentials(cls, bucket, read=False, write=False):
        credentials = DataStorage._get_temporary_credentials([{'id': bucket, 'read': read, 'write': write}])
        credentials.expiration = parse(credentials.expiration).replace(tzinfo=None)
        return credentials

    @classmethod
    def _get_temporary_credentials(cls, data):
        api = cls.instance()
        response_data = api.call('datastorage/tempCredentials/', data=json.dumps(data))
        if 'payload' in response_data:
            return TemporaryCredentialsModel.load(response_data['payload'])
        elif 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError('Failed to load credentials from server.')

    @staticmethod
    def create_operation_info(source_bucket, destination_bucket, command, versioning=False):
        operations = []
        if source_bucket and command == "cp":
            operation = {'id': source_bucket, 'read': True, 'write': False}
            if versioning:
                operation['readVersion'] = True
            operations.append(operation)
        if destination_bucket:
            operation = {'id': destination_bucket, 'read': True, 'write': True}
            if versioning:
                operation['readVersion'] = True
                operation['writeVersion'] = True
            operations.append(operation)
        if source_bucket and command == "mv":
            operation = {'id': source_bucket, 'read': True, 'write': True}
            if versioning:
                operation['readVersion'] = True
                operation['writeVersion'] = True
            operations.append(operation)
        return operations

    @classmethod
    def policy(cls, storage_name, sts_duration, lts_duration, backup_duration, versioning):
        datastorage = cls.get(storage_name)
        api = cls.instance()
        data = json.dumps({'id': datastorage.identifier,
                           'storagePolicy': {
                               'backupDuration': backup_duration if backup_duration else None,
                               'longTermStorageDuration': lts_duration if lts_duration else None,
                               'shortTermStorageDuration': sts_duration if sts_duration else None,
                               'versioningEnabled': versioning if versioning else None
                           }})
        response_data = api.call('datastorage/policy', data=data, http_method='POST',
                                 error_message="Failed to update %s storage policy." % storage_name)
        if 'payload' in response_data:
            return DataStorageModel.load(response_data['payload'])
        elif 'message' in response_data:
            return RuntimeError(response_data['message'])
        else:
            return RuntimeError("Failed to update %s storage policy." % storage_name)

    @classmethod
    def set_object_tags(cls, identifier, path, tags, version):
        api = cls.instance()
        data = json.dumps(tags)
        endpoint = 'datastorage/{}/tags?path={}'.format(identifier, path)
        if version:
            endpoint = '&version='.join([endpoint, version])
        response_data = api.call(endpoint, data=data, http_method='POST')
        if 'payload' in response_data:
            return response_data['payload']
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to update tags for object {}.".format(path))

    @classmethod
    def get_object_tags(cls, identifier, path, version):
        api = cls.instance()
        endpoint = 'datastorage/{}/tags?path={}'.format(identifier, path)
        if version:
            endpoint = '&version='.join([endpoint, version])
        response_data = api.call(endpoint, None)
        if 'payload' in response_data:
            return response_data['payload']
        if response_data['status'] == 'OK':
            return []
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to update tags for object {}.".format(path))

    @classmethod
    def delete_object_tags(cls, identifier, path, tags, version):
        api = cls.instance()
        data = json.dumps(tags)
        endpoint = 'datastorage/{}/tags?path={}'.format(identifier, path)
        if version:
            endpoint = '&version='.join([endpoint, version])
        response_data = api.call(endpoint, data=data, http_method='DELETE')
        if 'payload' in response_data:
            return response_data['payload']
        if response_data['status'] == 'OK':
            return []
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to update tags for object {}.".format(path))


def __create_policy__(sts_duration, lts_duration, versioning, backup_duration):
    if versioning or sts_duration or lts_duration or backup_duration:
        return {
            'versioningEnabled': versioning,
            'shortTermStorageDuration': sts_duration if sts_duration else None,
            'longTermStorageDuration': lts_duration if lts_duration else None,
            'backupDuration': backup_duration if backup_duration else None
        }
    else:
        return None
