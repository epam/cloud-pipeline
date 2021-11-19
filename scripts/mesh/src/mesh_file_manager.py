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

import os
import uuid
try:
    from urllib.request import urlopen  # Python 3
except ImportError:
    from urllib2 import urlopen  # Python 2


class MeshStructureFileManager(object):

    def __init__(self, tmp_folder, type):
        self.tmp_folder = tmp_folder
        if not tmp_folder:
            self.tmp_folder = self.get_tmp_folder_name()
        if not os.path.exists(self.tmp_folder):
            os.makedirs(self.tmp_folder)
        random_prefix = str(uuid.uuid4()).replace("-", "")
        self.file_name = random_prefix + type + ".xml"
        self.file_path = os.path.join(self.tmp_folder, self.file_name)

    def download(self, path_url):
        file_stream = urlopen(path_url)
        with open(self.file_path, 'wb') as f:
            while True:
                chunk = file_stream.read(16 * 1024)
                if not chunk:
                    break
                f.write(chunk)
        file_stream.close()
        return self.file_path

    def delete(self):
        if os.path.exists(self.file_path):
            os.remove(self.file_path)

    @staticmethod
    def get_tmp_folder_name():
        home = os.path.expanduser("~")
        return os.path.join(home, '.tmp')
