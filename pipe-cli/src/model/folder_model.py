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

from ..model.pipeline_model import PipelineModel
from ..model.data_storage_model import DataStorageModel


class FolderModel(object):
    def __init__(self):
        self.id = None
        self.name = None
        self.parent_id = None
        self.created_date = None
        self.child_folders = []
        self.pipelines = []
        self.storages = []

    @classmethod
    def load(cls, json):
        instance = FolderModel()
        instance.initialize(json)
        if 'pipelines' in json:
            for pipeline in json['pipelines']:
                instance.pipelines.append(PipelineModel.load(pipeline))

        if 'storages' in json:
            for storage in json['storages']:
                instance.pipelines.append(DataStorageModel.load(storage))

        if 'childFolders' in json:
            for child in json['childFolders']:
                instance.child_folders.append(FolderModel.load(child))
        return instance

    def initialize(self, json):
        if 'id' in json:
            self.id = json['id']
        if 'name' in json:
            self.name = json['name']
        if 'created_date' in json:
            self.created_date = json['created_date']
        if 'parent_id' in json:
            self.parent_id = json['parent_id']
