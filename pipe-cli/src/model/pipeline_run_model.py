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

import datetime
from .pipeline_run_parameter_model import PipelineRunParameterModel
from ..utilities import date_utilities


class PipelineRunModel(object):
    def __init__(self):
        self.identifier = None
        self.parameters = []
        self.status = None
        self.version = None
        self.pipeline = None
        self.pipeline_id = None
        self.parent_id = None
        self.start_date = None
        self.end_date = None
        self.scheduled_date = None
        self.ssh_pass = None
        self.pod_ip = None
        self.tasks = []
        self.instance = {}
        self.owner = None
        self.endpoints = []

    @property
    def is_initialized(self):
        return self.status == 'RUNNING' and \
            self.pod_ip is not None and \
                next(( True for t in self.tasks \
                    if t.name == 'InitializeNode' and t.status == 'SUCCESS' ), False)

    @classmethod
    def load(cls, json):
        instance = cls()
        instance.identifier = json['id']
        if 'pipelineId' in json:
            instance.pipeline_id = json['pipelineId']
        if 'pipelineName' in json:
            instance.pipeline = json['pipelineName']
        elif 'dockerImage' in json:
            parts = json['dockerImage'].split('/')
            instance.pipeline = parts[len(parts) - 1]
        else:
            instance.pipeline = 'CMD'
        if 'version' in json:
            instance.version = json['version']
        instance.status = json['status']
        if 'startDate' in json:
            instance.scheduled_date = date_utilities.server_date_representation(json['startDate'])
        if 'endDate' in json:
            instance.end_date = date_utilities.server_date_representation(json['endDate'])
        if 'owner' in json:
            instance.owner = json['owner']
        if 'serviceUrl' in json:
            instance.endpoints = json['serviceUrl'].split(';')
        if 'pipelineRunParameters' in json:
            for parameter in json['pipelineRunParameters']:
                if 'value' in parameter and 'name' in parameter:
                    instance.parameters.append(PipelineRunParameterModel(parameter['name'], parameter['value'], None, False))
                    if parameter['name'] == 'parent-id' and parameter['value'] != 0:
                        instance.parent_id = parameter['value']
                elif 'name' in parameter:
                    instance.parameters.append(PipelineRunParameterModel(parameter['name'], None, None, False))
        if 'instance' in json:
            instance.instance = json['instance'].items()
        if 'podIP' in json:
            instance.pod_ip = json['podIP']
        if 'sshPassword' in json:
            instance.ssh_pass = json['sshPassword']
        node_ip_exists = False
        for (key, value) in instance.instance:
            if key == 'nodeIP':
                node_ip_exists = True
        if instance.status is not None and instance.status.upper() == 'RUNNING' and \
                (instance.instance is None or not node_ip_exists):
            instance.status = 'SCHEDULED'

        return instance


class PriceType:
    ON_DEMAND = 'on-demand'
    SPOT = 'spot'
