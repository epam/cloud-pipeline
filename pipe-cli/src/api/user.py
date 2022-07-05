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
from src.api.entity import Entity
from .base import API
import json
from ..model.object_permission_model import ObjectPermissionModel


class User(API):
    def __init__(self):
        super(User, self).__init__()

    @classmethod
    def get_permissions(cls, identifier, acl_class):
        entity = Entity.load_by_id_or_name(identifier, acl_class)
        return cls.permissions(entity['id'], entity['aclClass']), entity['owner']

    @classmethod
    def permissions(cls, id, acl_class):
        api = cls.instance()
        response_data = api.call('permissions?id={}&aclClass={}'.format(id, acl_class.upper()), None)
        if 'payload' in response_data and 'permissions' in response_data['payload']:
            permissions = []
            for permission_json in response_data['payload']['permissions']:
                permission_object = ObjectPermissionModel.load(permission_json)
                permission_object.parse_mask(True)
                permissions.append(permission_object)
            return permissions
        else:
            return []

    @classmethod
    def grant_permission(cls, identifier, acl_class, user_name, principal, mask):
        api = cls.instance()
        payload = {}
        if acl_class is not None:
            payload['aclClass'] = acl_class.upper()
        if identifier is not None:
            payload['id'] = identifier
        if mask is not None:
            payload['mask'] = mask
        if principal is not None:
            payload['principal'] = principal
        if user_name is not None:
            payload['userName'] = user_name
        data = json.dumps(payload)
        api.call('grant', data)

    @classmethod
    def change_owner(cls, user_name, class_name, object_id):
        api = cls.instance()
        response_data = api.call('/grant/owner?userName={}&aclClass={}&id={}'.format(
            user_name, str(class_name).upper(), object_id), None, http_method='POST')
        if 'payload' in response_data and 'entity' in response_data['payload']:
            return response_data['payload']['entity']
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to change owner.")

    @classmethod
    def generate_user_token(cls, user_name, duration):
        api = cls.instance()
        query = '/user/token?name=%s' % user_name
        if duration:
            query = '&expiration='.join([query, str(duration)])
        response_data = api.call(query, None)
        if 'payload' in response_data and 'token' in response_data['payload']:
            return response_data['payload']['token']
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to generate user token.")

    @classmethod
    def import_users(cls, file_path, create_user, create_group, create_metadata):
        api = cls.instance()
        query = '/users/import?createUser=%s&createGroup=%s' % (create_user, create_group)
        if create_metadata:
            query = '%s&createMetadata=%s' % (query, ",".join(create_metadata))
        response_data = api.upload(query, file_path)
        if 'payload' in response_data:
            return response_data['payload']
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            return []

    @classmethod
    def whoami(cls):
        api = cls.instance()
        return api.retryable_call('GET', '/whoami') or {}

    @classmethod
    def load_launch_limits(cls, load_all=False):
        api = cls.instance()
        return api.retryable_call('GET', '/user/launchLimits?loadAll={}'.format(load_all)) or {}
