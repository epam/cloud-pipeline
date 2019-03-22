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


class VersionModel(object):
    def __init__(self):
        self.name = None
        self.created_date = None
        self.draft = None
        self.commit_id = None
        self.run_parameters = None

    @classmethod
    def load(cls, json):
        instance = VersionModel()
        instance.name = json['name']
        instance.created_date = date_utilities.server_date_representation(json['createdDate'])
        instance.draft = json['draft']
        instance.commit_id = json['commitId']
        return instance

    def set_run_parameters(self, parameters):
        self.run_parameters = parameters
