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

from src.utilities import date_utilities


class DataStorageLifecycleModel:

    def __init__(self):
        self.id = None
        self.datastorage_id = None
        self.user_actor_id = None
        self.path = None
        self.type = None
        self.restore_versions = None
        self.restore_mode = None
        self.days = None
        self.started = None
        self.updated = None
        self.restored_till = None
        self.status = None

    @classmethod
    def load(cls, json):
        model = DataStorageLifecycleModel()
        model.id = json['id']
        if 'datastorageId' in json:
            model.datastorage_id = int(json['datastorageId'])
        if 'userActorId' in json:
            model.user_actor_id = int(json['userActorId'])
        if 'path' in json:
            model.path = json['path']
        if 'type' in json:
            model.type = json['type']
        if 'restoreVersions' in json:
            model.restore_versions = json['restoreVersions']
        if 'restoreMode' in json:
            model.restore_mode = json['restoreMode']
        if 'days' in json:
            model.days = int(json['days'])
        if 'started' in json:
            model.started = date_utilities.server_date_representation(json['started'])
        if 'updated' in json:
            model.updated = date_utilities.server_date_representation(json['updated'])
        if 'restoredTill' in json:
            model.restored_till = date_utilities.server_date_representation(json['restoredTill'])
        if 'status' in json:
            model.status = json['status']
        return model
