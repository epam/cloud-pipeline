# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

from .base import API
from ..model.data_storage_lifecycle_model import DataStorageLifecycleModel


class DataStorageLifecycle(API):

    def __init__(self):
        super(DataStorageLifecycle, self).__init__()

    @classmethod
    def load_hierarchy(cls, storage_id, path, is_file=False):
        api = cls.instance()
        request_url = '/datastorage/%s/lifecycle/restore/effectiveHierarchy?path=%s&pathType=%s' \
                      % (str(storage_id), path, 'FILE' if is_file else 'FOLDER&recursive=true')
        response_data = api.call(request_url, None)
        if 'payload' not in response_data:
            return None
        items = []
        for lifecycles_json in response_data['payload']:
            lifecycle = DataStorageLifecycleModel.load(lifecycles_json)
            items.append(lifecycle)
        return items
