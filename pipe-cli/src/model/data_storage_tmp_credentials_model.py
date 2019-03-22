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

class TemporaryCredentialsModel(object):

    def __init__(self):
        self.access_key_id = None
        self.secret_key = None
        self.session_token = None
        self.expiration = None
        self.region = None

    @classmethod
    def load(cls, json):
        instance = cls()
        instance.access_key_id = json['keyID']
        instance.secret_key = json['accessKey']
        instance.session_token = json['token']
        instance.expiration = json['expiration']
        instance.region = json['region']
        return instance
