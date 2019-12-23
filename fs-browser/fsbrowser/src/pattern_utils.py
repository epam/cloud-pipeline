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

import fnmatch
import os


class PatternMatcher(object):

    @classmethod
    def match_any(cls, path, patterns):
        path = cls._normalize_folder_path(path)
        for pattern in patterns:
            if path == pattern:
                return True
            if fnmatch.fnmatch(path, pattern):
                return True
        return False

    @staticmethod
    def _normalize_folder_path(path):
        if os.path.isfile(path):
            return path
        if str(path).endswith("/"):
            return path
        return path + "/"
