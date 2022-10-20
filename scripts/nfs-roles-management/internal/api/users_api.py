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

from internal.api.base import API
from internal.model.user_model import UserModel


class Users(API):

    def __init__(self, config=None):
        super(Users, self).__init__(config)

    def list(self):
        response_data = self.call('users', None)
        users = response_data.get('payload', [])
        for user in users:
            yield UserModel.load(user)
