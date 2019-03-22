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

from ..utilities import date_utilities


class TaskModel(object):
    def __init__(self):
        self.created = None
        self.started = None
        self.finished = None
        self.instance = None
        self.name = None
        self.parameters = None
        self.status = None

    @classmethod
    def load(cls, json):
        instance = cls()
        if 'created' in json:
            instance.created = date_utilities.server_date_representation(json['created'])
        if 'started' in json:
            instance.started = date_utilities.server_date_representation(json['started'])
        if 'finished' in json:
            instance.finished = date_utilities.server_date_representation(json['finished'])
        if 'instance' in json:
            instance.instance = json['instance']
        if 'name' in json:
            instance.name = json['name']
        if 'parameters' in json:
            instance.parameters = json['parameters']
        if 'status' in json:
            instance.status = json['status']
        return instance
