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


class DataStorageItemModel(object):
    def __init__(self):
        self.name = None
        self.path = None
        self.changed = None
        self.type = None
        self.size = None
        self.labels = []
        self.version = None
        self.versions = []
        self.latest = False
        self.delete_marker = False

    @classmethod
    def load(cls, json):
        instance = DataStorageItemModel()
        instance.initialize(json)
        return instance

    def initialize(self, json):
        self.name = json['name']
        if 'path' in json:
            self.path = json['path']
        if 'type' in json:
            self.type = json['type']
        if 'size' in json:
            self.size = json['size']
        if 'changed' in json:
            self.changed = date_utilities.server_date_representation(json['changed'])
        if 'labels' in json:
            self.labels = []
            for key in json['labels'].keys():
                self.labels.append(DataStorageItemLabelModel(key, json['labels'][key]))


class DataStorageItemLabelModel(object):
    def __init__(self, name, value):
        self.name = name
        self.value = value
