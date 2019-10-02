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

    def __init__(self, log_dir):
        self.api = PipelineAPI(os.environ.get('API'), log_dir)

    def load_storage_id_by_name(self, name):
        storage = self.api.find_datastorage(name)
        if not storage or not storage.id:
            raise RuntimeError()
        return storage.id

    def get_download_url(self, storage_id, path):
        result = self.api.get_storage_download_url(storage_id, [path])
        if len(result) == 1 and 'url' in result[0]:
            return result[0]
        raise RuntimeError("Failed to generate download url")

    def get_upload_url(self, storage_id, path):
        result = self.api.get_storage_upload_url(storage_id, [path])
        if len(result) == 1 and 'url' in result[0]:
            return result[0]
        raise RuntimeError("Failed to generate upload url")
