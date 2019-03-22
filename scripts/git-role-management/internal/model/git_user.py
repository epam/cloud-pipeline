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

class GitUser(object):
    def __init__(self):
        self.id = None
        self.username = None
        self.name = None
        self.email = None
        self.access_level = 10

    @classmethod
    def load(cls, json):
        instance = GitUser()
        if 'id' in json:
            instance.id = int(json['id'])
        if 'username' in json:
            instance.username = json['username'].encode('utf-8')
        if 'name' in json:
            instance.name = json['name'].encode('utf-8')
        if 'email' in json:
            instance.email = json['email'].encode('utf-8')
        if 'access_level' in json:
            instance.access_level = int(json['access_level'])
        return instance
