# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

class PipelineRunParameterModel(object):
    def __init__(self, name, value, parameter_type, required, roles=set()):
        self.name = name
        self.value = value
        self.parameter_type = parameter_type
        if parameter_type is None:
            self.parameter_type = 'string'
        self.required = required
        self.roles = set() if roles is None else set(roles)

    @staticmethod
    def load_from_default_system_parameter(parameter_json):
        parameter_name = None
        parameter_value = None
        parameter_type = None
        parameter_roles = []
        if 'name' in parameter_json:
            parameter_name = parameter_json['name']
        if 'defaultValue' in parameter_json:
            parameter_value = parameter_json['defaultValue']
        if 'type' in parameter_json:
            parameter_type = parameter_json['type']
        if 'roles' in parameter_json:
            parameter_roles = parameter_json['roles']
        return PipelineRunParameterModel(parameter_name, parameter_value, parameter_type, None, roles=parameter_roles)
