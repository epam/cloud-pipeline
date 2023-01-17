# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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


def determinate_prefix_from_glob(_glob_str):
    _prefix = None
    if "*" in _glob_str:
        _prefix = os.path.split(_glob_str.split("*", 1)[0])[0]
    else:
        _prefix = _glob_str

    return _prefix


def convert_glob_to_regexp(glob_str):
    resulted_regexp = "^\\/"
    for glob_part in glob_str.split("/"):
        if not glob_part:
            continue
        if "*" not in glob_part:
            resulted_regexp += glob_part + "\\/"
        elif glob_part == "**":
            resulted_regexp += "(?:[^\\/]+\\/)*[^\\/]*"
        else:
            resulted_regexp += glob_part.replace("*", "[^\\/]*") + "\\/"
    if resulted_regexp.endswith("\\/"):
        resulted_regexp = resulted_regexp[:len(resulted_regexp) - 2]
    resulted_regexp += "$"
    resulted_regexp = resulted_regexp.replace(".", "\\.")
    return resulted_regexp


def generate_all_possible_dir_paths(paths):
    def generate_hierarchy(_path):
        _result = set()
        interim_result = ""
        for path_part in _path.split("/"):
            interim_result = interim_result + "/" + path_part if path_part else interim_result
            _result.add(interim_result)
        return _result

    result = set()
    for path in paths:
        result = result.union(generate_hierarchy(path))
    return result


def join_paths(prefix, suffix, delimiter="/"):
    formatted_prefix = prefix[:-len(delimiter)] if prefix.endswith(delimiter) else prefix
    formatted_suffix = suffix[len(delimiter)::] if suffix.startswith(delimiter) else suffix
    return delimiter.join([formatted_prefix, formatted_suffix])
