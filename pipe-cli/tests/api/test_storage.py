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

import datetime as dt
import pytz
import requests_mock
from src.api.data_storage import DataStorage
from tests.test_utils.assertions_utils import *
from tests.test_utils.build_models import *
from tests.test_utils.mocked_requests import *

server_date = dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
raw_date = dt.datetime.now(pytz.utc).strftime("%Y-%m-%d %H:%M:%S.%f")

API = "https://pipeline-cloud/pipeline/restapi/"
API_TOKEN = "token_placeholder"


def setup_module(module):
    os.environ["API"] = API
    os.environ["API_TOKEN"] = API_TOKEN


class TestStorage(object):
    storage_id = "1"
    name = "test-storage"
    path = "test-storage"
    description = None
    type = "S3"
    parent_folder_id = None
    backup_duration = 6
    lts_duration = 4
    sts_duration = 2
    versioning = True
    on_cloud = True

    @requests_mock.mock()
    def test_save_storage(self, mock):
        mock.post(mocked_url("datastorage/save"),
                  text=mock_datastorage(self.storage_id, self.name, self.path, self.type,
                                        mock_storage_policy(self.backup_duration, self.lts_duration,
                                                            self.sts_duration, self.versioning)))

        actual = DataStorage.save(self.name, self.path, self.description, self.sts_duration, self.lts_duration, self.versioning,
                                  self.backup_duration, self.type, self.parent_folder_id, self.on_cloud)

        expected = build_storage_model(identifier=self.storage_id, name=self.name, path=self.path,
                                       storage_type=self.type,
                                       policy=build_storage_policy(self.backup_duration,
                                                                   self.lts_duration,
                                                                   self.sts_duration,
                                                                   self.versioning))

        assert_storages(actual, expected)

    @requests_mock.mock()
    def test_policy(self, mock):
        mock.get(mocked_url("datastorage/find"),
                 text=mock_datastorage(self.storage_id, self.name, self.path, self.type,
                                                 mock_storage_policy(self.backup_duration, self.lts_duration,
                                                                     self.sts_duration, self.versioning)))
        mock.post(mocked_url("datastorage/policy"),
                  text=mock_datastorage(self.storage_id, self.name, self.path, self.type,
                                        mock_storage_policy(self.backup_duration, self.lts_duration,
                                                            self.sts_duration, self.versioning)))

        actual = DataStorage.policy(self.name, self.sts_duration, self.lts_duration, self.backup_duration,
                                    self.versioning)

        expected = build_storage_model(identifier=self.storage_id, name=self.name, path=self.path,
                                       storage_type=self.type,
                                       policy=build_storage_policy(self.backup_duration,
                                                                   self.lts_duration,
                                                                   self.sts_duration,
                                                                   self.versioning))

        assert_storages(actual, expected)
