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


from fsbrowser.model.fs_item import FsItem

FOLDER_TYPE = "Folder"


class Folder(FsItem):

    def __init__(self, name, path):
        self.name = name
        self.path = path

    def is_file(self):
        return False

    def to_json(self):
        return {
            "name": self.name,
            "path": self.path,
            "type": FOLDER_TYPE
        }
