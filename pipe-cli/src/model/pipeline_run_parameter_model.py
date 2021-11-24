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
        if roles is None:
            self.roles = set()
        else:
            self.roles = set(roles)

    @staticmethod
    def load_from_default_system_parameter(json):
        name = None
        value = None
        parameter_type = None
        required = 'false'
        parameter_roles = []
        if 'name' in json:
            name = json['name']
        if 'defaultValue' in json:
            value = json['defaultValue']
        if 'type' in json:
            parameter_type = json['type']
        if 'roles' in json:
            parameter_roles = json['roles']
        return PipelineRunParameterModel(name, value, parameter_type, required, roles=parameter_roles)
