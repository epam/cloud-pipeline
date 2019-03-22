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

from urllib import quote_plus
from git_share_group import GitShareGroup


class GitProject(object):
    def __init__(self):
        self.id = None
        self.name = None
        self.path_with_namespace = None
        self.shared_with_groups = []

    @classmethod
    def load(cls, json):
        instance = GitProject()
        if 'id' in json:
            instance.id = int(json['id'])
        if 'name' in json:
            instance.name = json['name'].encode('utf-8')
        if 'path_with_namespace' in json:
            instance.path_with_namespace = quote_plus(json['path_with_namespace'].encode('utf-8'))
        if 'shared_with_groups' in json:
            for share_group_json in json['shared_with_groups']:
                instance.shared_with_groups.append(GitShareGroup.load(share_group_json))
        return instance
