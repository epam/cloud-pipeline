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

import os

from pipeline import PipelineAPI


class CloudPipelineApiProvider(object):

    def __init__(self):
        self.api = PipelineAPI(os.environ.get('API'), "logs")

    def search(self, query, type):
        return self.api.search(query, [type])

    def create_pipeline(self, name, description):
        data = {
            "name": name,
            "description": description,
        }
        return self.api.create_pipeline(data)

    def delete_pipeline(self, id):
        self.api.delete_pipeline(id)

    def create_folder(self, name, parent=None):
        return self.api.create_folder(name, parent)

    def delete_folder(self, id):
        self.api.delete_folder(id)

    def create_s3_data_storage(self, name, description, parent_folder_id=None, region_id=2, storage_policy=None):
        if not storage_policy:
            storage_policy = {
                "versioningEnabled": True
            }
        data = {
            "name": name,
            "path": name,
            "description": description,
            "type": 'S3',
            "shared": False,
            "parentFolderId": parent_folder_id,
            "regionId": region_id,
            "storagePolicy": storage_policy
        }
        return self.api.datastorage_create(data)

    def delete_data_storage(self, id):
        self.api.delete_datastorage(id)

    def create_issue(self, name, text, entity_id, entity_class):
        return self.api.create_issue(name, text, entity_id, entity_class)

    def delete_issue(self, id):
        self.api.delete_folder(id)

    def create_comment(self, issue_id, text):
        return self.api.create_comment(issue_id, text)
