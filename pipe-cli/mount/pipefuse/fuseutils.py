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

DEFAULT_DELIMITER = '/'


def join_path_with_delimiter(parent, child, delimiter=DEFAULT_DELIMITER):
    return parent.rstrip(delimiter) + delimiter + child.lstrip(delimiter)


def append_delimiter(path, delimiter=DEFAULT_DELIMITER):
    return path if path.endswith(delimiter) else path + delimiter

