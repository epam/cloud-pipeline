# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

class ShareMountModel(object):
    def __init__(self):
        self.identifier = None
        self.region_id = None
        self.mount_root = None
        self.mount_type = None
        self.mount_options = None

    @classmethod
    def load(cls, json):
        instance = ShareMountModel()
        if not json:
            return None
        instance.identifier = json['id']
        instance.region_id = json['regionId']
        instance.mount_root = json['mountRoot']
        instance.mount_type = json['mountType']
        instance.mount_options = json['mountOptions'] if 'mountOptions' in json else None
        return instance
