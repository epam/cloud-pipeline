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

class GitShareGroup(object):
    def __init__(self):
        self.group_id = None
        self.group_name = None
        self.group_access_level = 10

    @classmethod
    def load(cls, json):
        instance = GitShareGroup()
        if 'group_id' in json:
            instance.group_id = json['group_id']
        if 'group_name' in json:
            instance.group_name = json['group_name'].encode('utf-8')
        if 'group_access_level' in json:
            instance.group_access_level = int(json['group_access_level'])
        return instance
