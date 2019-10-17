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
from src.api.app_info import ApplicationInfo
from src.version import __version__

VERSION_DELIMITER = "."


def need_to_update_version():
    api_info = ApplicationInfo().info()
    if 'version' not in api_info or not api_info['version']:
        raise RuntimeError("Failed to load Cloud Pipeline API version")
    api_version = parse_version(api_info['version'])
    cli_version = parse_version(__version__)

    if None in api_version:
        raise RuntimeError("Cloud Pipeline API version has invalid format. "
                           "Expected format is <major>.<minor>.<patch>.<build>.<...>")

    for api, cli in zip(api_version, cli_version):
        if not cli:
            return True
        if int(api) > int(cli):
            return True
        if int(api) < int(cli):
            return False
    return False


def parse_version(version):
    parts = version.split(VERSION_DELIMITER)
    major = parse_version_part(parts, 0)
    minor = parse_version_part(parts, 1)
    patch = parse_version_part(parts, 2)
    build = parse_version_part(parts, 3)
    return [major, minor, patch, build]


def parse_version_part(parts, index):
    if len(parts) > index:
        return parts[index]
    return None
