# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

class ToolModel(object):
    def __init__(self):
        self.id = None
        self.image = None
        self.registry = None

    @classmethod
    def load(cls, json):
        if not json:
            return None
        instance = ToolModel()
        instance.id = json['id'] if 'id' in json else None
        instance.image = json['image'] if 'image' in json else None
        instance.registry = json['registry'] if 'registry' in json else None

        if not instance.id or not instance.image or not instance.registry:
            return None
        else:
            return instance
