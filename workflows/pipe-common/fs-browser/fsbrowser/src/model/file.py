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

from fsbrowser.src.model.fs_item import FsItem

FILE_TYPE = "File"


class File(FsItem):

    def __init__(self, name, path):
        self.name = name
        self.path = path
        self.size = self._get_size()

    def is_file(self):
        return True

    def to_json(self):
        return {
            "name": self.name,
            "path": self.path,
            "type": FILE_TYPE,
            "size": self.size
        }

    def _get_size(self):
        return os.path.getsize(self.path)
