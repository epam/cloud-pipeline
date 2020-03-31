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
import subprocess

_ENV_VAR_PLACEHOLDER = '${%s}'
_ENV_VAR_PATTERN = r'\${(\w*|_)}'
_ENV_VAR_NAME_PATTERN_GROUP = 1


def replace_all_system_variables_in_path(path):
    if not path:
        return ''

    try:
        # Try to evaluate any expression in the path. E.g. for the complex: s3://bucket/$(date)/...
        return subprocess.check_output('echo {}'.format(path), shell=True).strip()
    except:
        # If it subprocess fails - try a simplier option with environment variables only
        # Note, that any unset variables won't be substituted with empty value, e.g.:
        # 1. unset a
        #    > os.path.expandvars('$a')
        #    > '$a'
        # 2. export a=aaa
        #    > os.path.expandvars('$a')
        #    > 'aaa'
        return os.path.expandvars(path)


def get_path_without_first_delimiter(path):
    return path[1:] if path.startswith('/') else path


def get_path_with_trailing_delimiter(path):
    return path if path.endswith('/') else path + '/'


def get_path_without_trailing_delimiter(path):
    return path if not path.endswith('/') else path[:-1]
