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

from .version_model import VersionModel
from ..utilities import date_utilities


class PipelineModel(object):
    def __init__(self):
        self.identifier = None
        self.name = None
        self.description = None
        self.repository = None
        self.created_date = None
        self.current_version = None
        self.current_version_name = None
        self.versions = []
        self.set_current_version(None)
        self.storage_rules = []
        self.mask = 0

    @classmethod
    def load(cls, json):
        instance = PipelineModel()
        instance.identifier = json['id']
        instance.name = json['name']
        if 'description' in json:
            instance.description = json['description']
        if 'repository' in json:
            instance.repository = json['repository']
        if 'createdDate' in json:
            instance.created_date = date_utilities.server_date_representation(json['createdDate'])
        if 'currentVersion' in json:
            instance.set_current_version(VersionModel.load(json['currentVersion']))
        if 'mask' in json:
            instance.mask = json['mask']
        return instance

    def set_current_version(self, version):
        self.current_version = version
        self.current_version_name = None
        if self.current_version is not None:
            self.current_version_name = self.current_version.name

    def set_versions(self, versions):
        self.versions = versions
