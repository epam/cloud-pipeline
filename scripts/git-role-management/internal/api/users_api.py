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

from .base import API
from ..model.user_model import UserModel


class Users(API):
    def __init__(self):
        super(Users, self).__init__()

    @classmethod
    def list(cls):
        api = cls.instance()
        response_data = api.call('users', None)
        users = []
        for user_json in response_data['payload']:
            users.append(UserModel.load(user_json))
        return users
