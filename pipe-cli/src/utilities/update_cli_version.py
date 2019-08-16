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
import stat
import sys
import requests

from src.config import Config


CP_RESTAPI_SUFFIX = 'restapi/'
CP_CLI_DOWNLOAD_SUFFIX = 'pipe'


class UpdateCLIVersionManager(object):

    def __init__(self, path=None):
        if path:
            self.path = path
        else:
            config = Config.instance()
            self.path = str(config.api)
            if not self.path:
                raise RuntimeError("Failed to find Cloud Pipeline CLI download url")
        self.path = self.path.replace(CP_RESTAPI_SUFFIX, CP_CLI_DOWNLOAD_SUFFIX)

    def update(self):
        path_to_script = os.path.realpath(sys.argv[0])
        requests.urllib3.disable_warnings()
        self.download_executable(path_to_script)
        self.set_x_permission(path_to_script)

    def download_executable(self, path_to_script):
        is_d = self.is_downloadable()
        if is_d:
            os.remove(path_to_script)
            request = requests.get(self.path, verify=False)
            open(path_to_script, 'wb').write(request.content)
        else:
            raise RuntimeError("Provided url '%s' not downloadable or invalid." % self.path)

    def is_downloadable(self):
        header = requests.head(self.path, verify=False, allow_redirects=True).headers
        content_type = header.get('content-type')
        return content_type is None or 'html' not in content_type.lower()

    @staticmethod
    def set_x_permission(path_to_script):
        st = os.stat(path_to_script)
        os.chmod(path_to_script, st.st_mode | stat.S_IEXEC)
