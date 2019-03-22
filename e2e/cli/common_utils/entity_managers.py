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

from src.api.base import API
from pipe_cli import create_data_storage
import json


class EntityManager(API):

    def __init__(self):
        super(EntityManager, self).__init__()

    @classmethod
    def create(cls, name):
        pass

    @classmethod
    def delete(cls, id):
        pass

    @classmethod
    def get_manager(cls, entity_class):
        if str(entity_class).upper() == "PIPELINE":
            return PipelineManager()
        if str(entity_class).upper() == "DATA_STORAGE":
            return DataStorageManager()
        if str(entity_class).upper() == "FOLDER":
            return FolderManager()


class PipelineManager(EntityManager):

    @classmethod
    def create(cls, pipeline_name):
        if not pipeline_name:
            return None
        api = cls.instance()
        run_request_postfix = "pipeline/register"
        request = {
            "name": pipeline_name,
            "description": ""
        }
        response = api.call(method=run_request_postfix, data=json.dumps(request), http_method="post")
        if 'payload' in response and 'id' in response['payload']:
            return response['payload']['id']
        else:
            raise RuntimeError("Response is not valid while creating pipeline {}".format(pipeline_name))

    @classmethod
    def delete(cls, pipeline_id):
        if not pipeline_id:
            return
        api = cls.instance()
        delete_request_postfix = "pipeline/{}/delete".format(pipeline_id)
        api.call(method=delete_request_postfix, data=None, http_method="delete")


class FolderManager(EntityManager):

    @classmethod
    def create(cls, folder_name, parent_id=None):
        if not folder_name:
            return None
        api = cls.instance()
        run_request_postfix = "folder/register"
        request = {
            "name": folder_name,
            "parentId": parent_id
        }
        response = api.call(method=run_request_postfix, data=json.dumps(request), http_method="post")
        if 'payload' in response and 'id' in response['payload']:
            return response['payload']['id']
        else:
            raise RuntimeError("Response is not valid while creating folder {}".format(folder_name))

    @classmethod
    def delete(cls, folder_id):
        if not folder_id:
            return
        api = cls.instance()
        endpoint = "folder/{}/delete".format(folder_id)
        api.call(method=endpoint, data=None, http_method="delete")

    @classmethod
    def get_id_by_name(cls, name):
        if name is None:
            return None
        api = cls.instance()
        response_data = api.call('folder/find?id={}'.format(name), None)
        if 'payload' in response_data and 'id' in response_data['payload']:
            return response_data['payload']['id']
        raise RuntimeError("Response is not valid while getting folder id by name {}".format(name))


class DataStorageManager(EntityManager):

    @classmethod
    def create(cls, data_storage_name):
        create_data_storage(data_storage_name)
        return cls.get_id_by_name(data_storage_name)

    @classmethod
    def delete(cls, data_storage_id):
        if not data_storage_id:
            return
        api = cls.instance()
        api.call('datastorage/{}/delete?cloud={}'.format(data_storage_id, True), data=None, http_method='DELETE')

    @classmethod
    def get_id_by_name(cls, name):
        if name is None:
            return None
        api = cls.instance()
        response_data = api.call('datastorage/find?id={}'.format(name), None)
        if 'payload' in response_data and 'id' in response_data['payload']:
            return response_data['payload']['id']
        raise RuntimeError("Response is not valid while getting data storage id by name {}".format(name))

    @classmethod
    def get_parent_id_by_name(cls, name):
        if name is None:
            return None
        api = cls.instance()
        response_data = api.call('datastorage/find?id={}'.format(name), None)
        if 'payload' in response_data and 'parentFolderId' in response_data['payload']:
            return response_data['payload']['parentFolderId']
        raise RuntimeError("Response is not valid while getting data storage id by name {}".format(name))


class UtilsManager(API):

    def __init__(self):
        super(UtilsManager, self).__init__()

    @classmethod
    def get_preference(cls, preference):
        api = cls.instance()
        response_data = api.call('preferences/'+preference, None)
        if 'payload' in response_data and 'value' in response_data['payload']:
            return response_data['payload']['value']
        raise RuntimeError('Response is not valid while getting cloud pipeline {} preference'.format(preference))