#  Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

from src.api.base import API

class StoragePathPermissions(API):

    def __init__(self):
        super(StoragePathPermissions, self).__init__()

    @classmethod
    def get_user_permissions(cls, storage_id):
        api = cls.instance()
        response_data = api.call('/datastorage/%d/paths/permissions' % storage_id, None)
        return response_data.get('payload', [])
