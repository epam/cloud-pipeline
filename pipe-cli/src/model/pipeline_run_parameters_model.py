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

from .pipeline_run_parameter_model import PipelineRunParameterModel


class PipelineRunParametersModel(object):
    def __init__(self):
        self.main_class = None
        self.main_file = None
        self.instance_disk = None
        self.instance_size = None
        self.parameters = []
        self.version = None

    @classmethod
    def load(cls, json, version):
        instance = cls()
        instance.version = version
        instance.instance_disk = json['instance_disk']
        instance.instance_size = json['instance_size']
        if 'main_file' in json:
            instance.main_file = json['main_file']

        if 'main_class' in json:
            instance.main_class = json['main_class']
        if 'parameters' in json:
            for key, value in json['parameters'].items():
                parameter_value = value
                parameter_type = None
                parameter_required = False
                if 'value' in value or 'type' in value or 'required' in value:
                    parameter_value = None
                    if 'value' in value:
                        parameter_value = value['value']
                    if 'type' in value:
                        parameter_type = value['type']
                    if 'required' in value:
                        parameter_required = value['required']
                instance.parameters.append(PipelineRunParameterModel(key,
                                                                     parameter_value,
                                                                     parameter_type,
                                                                     parameter_required))
        return instance
