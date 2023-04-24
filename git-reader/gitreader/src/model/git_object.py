# Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

class GitObject:

    def __init__(self, git_id, name, git_type, path, mode, size):
        self.git_id = git_id
        self.name = name
        self.git_type = git_type
        self.path = path
        self.mode = mode
        if size != "-":
            self.size = int(size)
        else:
            self.size = 0

    def to_json(self):
        return {
            "id": self.git_id,
            "name": self.name,
            "type": self.git_type,
            "path": self.path,
            "mode": self.mode,
            "size": self.size
        }
