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


class EnvironmentParametersParser:
    def __init__(self, skip_params):
        self.skip_params = skip_params
        self.list_delimiter = ','
        self.param_type_suffix = '_PARAM_TYPE'
        self.pattern_prefix = 'p_'
        self.exclude_suffix = '_exclude'
        self.original_suffix = '_ORIGINAL'
        self.preprocessed_types = {'common', 'input'}

    def collect_params_from_env(self):
        all_params = {}
        file_patterns = {}
        exclude_patterns = {}
        param_types = {}
        for name, param in os.environ.iteritems():
            if name in self.skip_params:
                continue
            if name + self.param_type_suffix in os.environ:
                if name.startswith(self.pattern_prefix):
                    if name.endswith(self.exclude_suffix):
                        exclude_patterns[
                            name[len(self.pattern_prefix):len(self.exclude_suffix) - 1]] = self.parse_param(param)
                    else:
                        file_patterns[name[len(self.pattern_prefix):]] = self.parse_param(param)
                else:
                    param_type = os.environ[name + self.param_type_suffix]
                    param_types[name] = param_type
                    if param_type in self.preprocessed_types:
                        all_params[name] = os.environ[name + self.original_suffix]
                    else:
                        all_params[name] = param
        return all_params, file_patterns, exclude_patterns, param_types

    def parse_param(self, param):
        return param.split(self.list_delimiter)

    @classmethod
    def get_env_value(cls, env_name, param_name=None, default_value=None):
        if param_name is not None and param_name in os.environ:
            return os.environ[param_name]
        elif env_name in os.environ:
            return os.environ[env_name]
        elif default_value is None:
            raise RuntimeError('Required parameter {} is not set'.format(env_name))
        else:
            return default_value

    @classmethod
    def has_flag(cls, env_name):
        if env_name not in os.environ:
            return False
        if not os.environ[env_name]:
            return False
        if os.environ[env_name].lower() == 'true':
            return True
        else:
            return False
