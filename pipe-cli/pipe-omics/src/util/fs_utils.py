# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import os.path


def parse_local_path(path):
    parent_dir = os.path.dirname(path)
    basename = os.path.basename(path)
    if os.path.exists(path):
        if os.path.isdir(path):
            return path, None
        else:
            raise ValueError("File with path {} already exists!".format(path))
    elif os.path.isdir(parent_dir):
        return parent_dir, basename
    raise ValueError("Path {} doesn't exists!".format(parent_dir))
