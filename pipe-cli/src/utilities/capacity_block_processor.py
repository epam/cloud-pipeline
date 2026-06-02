# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import json
import sys
import click

from src.api.preferenceapi import PreferenceAPI


class CapacityBlockProcessor:
    CONFIG_PREFERENCE = 'launch.reservation.parameters'

    def __init__(self, instance_type, print_service):
        self._config = None
        if not instance_type:
            # instance type is None, we can not to check further
            return
        self._instance_type = instance_type
        config = self._find_capacity_block_config()
        self._config = config.get(self._instance_type)
        self._print_service = print_service

    def verify(self, parameters):
        if not self._config:
            # instance type is not capacity block
            return
        self._validate_parameter('cpu_requests_enabled',
                                 parameters, 'CP_CAP_REQUESTS_CPU')
        self._validate_parameter('gpu_requests_enabled',
                                 parameters, 'CP_CAP_REQUESTS_GPU')
        self._validate_parameter('ram_requests_enabled',
                                 parameters, 'CP_CAP_REQUESTS_RAM')

    def apply_config(self, parameters):
        if not self._config:
            # instance type is not capacity block
            return parameters, None
        if parameters is None:
            parameters = {}
        for param_name, param_value in self._config.get('parameters', {}).items():
            if param_name not in parameters:
                parameters.update({param_name: param_value})
        kube_policy = self._config.get('kube_assign_policy')
        return parameters, kube_policy

    def _find_capacity_block_config(self):
        preference = PreferenceAPI.get_preference(self.CONFIG_PREFERENCE)
        if not preference:
            return {}
        preference_value = preference.value
        if not preference_value:
            return {}
        try:
            return json.loads(preference_value)
        except json.JSONDecodeError:
            self._print_service.error('Cannot parse preference %s, not a valid json.' % self.CONFIG_PREFERENCE,
                                      err=True, buf=True)
            return {}

    def _validate_parameter(self, config_marker_name, parameters, parameter_name):
        if not self._config.get(config_marker_name):
            return
        if parameters.get(parameter_name):
            return
        self._print_service.error(
            'Parameter %s shall be specified for instance type %s.' % (parameter_name, self._instance_type), err=True)
        sys.exit(1)
