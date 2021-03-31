# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

class VersionedStorage:

    def __init__(self, id, name, path, revision, detached):
        self.id = id
        self.name = name
        self.path = path
        self.revision = revision
        self.detached = detached

    def to_json(self):
        return {
            "id": self.id,
            "name": self.name,
            "path": self.path,
            "revision": self.revision,
            "detached": self.detached
        }
