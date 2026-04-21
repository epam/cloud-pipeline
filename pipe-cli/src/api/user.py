# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import re

from ..model.object_permission_model import ObjectPermissionModel

_SAFE_TOKEN_NAME_PATTERN = re.compile(r'^[A-Za-z0-9_-]+$')

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
    def _normalize_token_name(cls, token_name):
        if token_name is None:
            return None
        token_name = token_name.strip()
        if not token_name:
            return None
        if not _SAFE_TOKEN_NAME_PATTERN.match(token_name):
            raise ValueError(
                'Token name may contain only letters, digits, underscore (_) and hyphen (-).'
            )
        return token_name

    @classmethod
    def generate_plain_user_token(cls, user_name=None, duration=None):
        """
        Plain JWT (GET /user/token). Optional user_name issues for that user (admin).
        """
        api = cls.instance()
        query_params = []
        if user_name is not None:
            query_params.append('name={}'.format(user_name))
        if duration:
            query_params.append('expiration={}'.format(str(duration)))
        query = '/user/token'
        if query_params:
            query += '?' + '&'.join(query_params)
        response_data = api.call(query, None)
        if 'payload' in response_data and 'token' in response_data['payload']:
            return response_data['payload']['token']
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        raise RuntimeError('Failed to generate plain user token.')

    @classmethod
    def generate_named_user_token(cls, user_id=None, duration=None, token_name=None):
        """
        Registered named JWT (GET /user/token/named). Optional user_id targets that user (admin).
        """
        token_name = cls._normalize_token_name(token_name)
        api = cls.instance()
        query_params = []
        if user_id is not None:
            query_params.append('userId={}'.format(int(user_id)))
        if duration:
            query_params.append('expiration={}'.format(str(duration)))
        if token_name:
            query_params.append('tokenName={}'.format(token_name))
        query = '/user/token/named'
        if query_params:
            query += '?' + '&'.join(query_params)
        response_data = api.call(query, None)
        if 'payload' in response_data and 'token' in response_data['payload']:
            return response_data['payload']['token']
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        raise RuntimeError('Failed to generate named user token.')

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

    @classmethod
    def list_named_tokens(cls, user_id=None):
        api = cls.instance()
        if user_id is not None:
            query = '/user/token/named/list?userId={}'.format(int(user_id))
        else:
            query = '/user/token/named/list'
        response_data = api.call(query, None)
        if 'payload' in response_data:
            return response_data['payload'] or []
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        raise RuntimeError('Failed to list named tokens.')

    @classmethod
    def revoke_named_token(cls, jti=None, user_id=None):
        """
        Revoke a JWT by jti (named or plain).

        :param jti: token id (required)
        :param user_id: if set, revoke for this user (admin-style); omit for current user
        """
        if not jti:
            raise ValueError('jti is required')
        try:
            from urllib.parse import quote
        except ImportError:
            from urllib import quote
        api = cls.instance()
        query_parts = ['jti=' + quote(str(jti), safe='')]
        if user_id is not None:
            query_parts.append('userId=' + str(int(user_id)))
        path = '/user/token/revoke?' + '&'.join(query_parts)
        response_data = api.call(path, None, http_method='DELETE')
        if 'payload' in response_data:
            return response_data['payload']
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        raise RuntimeError('Failed to revoke token.')
