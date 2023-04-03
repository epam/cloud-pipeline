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

import json
import os

from .pipeline_run_parameter_model import PipelineRunParameterModel
from ..utilities import date_utilities
import logging


class RunSid(object):

    def __init__(self, name, is_principal, access_type):
        self.name = name
        self.is_principal = is_principal
        self.access_type = access_type


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
        self.run_sids = []
        self.sensitive = None
        self.platform = None

    @property
    def is_initialized(self):
        task_to_check = os.getenv('CP_SSH_INIT_TASK_NAME', 'InitializeEnvironment')
        task_status = task_to_check == 'NONE' or \
                        next(( True for t in self.tasks \
                            if t.name == task_to_check and t.status == 'SUCCESS' ), False)
        
        return self.status == 'RUNNING' and \
                    self.pod_ip is not None and \
                        task_status

    @classmethod
    def load(cls, result):
        instance = cls()
        instance.identifier = result['id']
        if 'pipelineId' in result:
            instance.pipeline_id = result['pipelineId']
        if 'pipelineName' in result:
            instance.pipeline = result['pipelineName']
        elif 'dockerImage' in result:
            parts = result['dockerImage'].split('/')
            instance.pipeline = parts[len(parts) - 1]
        else:
            instance.pipeline = 'CMD'
        if 'version' in result:
            instance.version = result['version']
        instance.status = result['status']
        if 'startDate' in result:
            instance.scheduled_date = date_utilities.server_date_representation(result['startDate'])
        if 'endDate' in result:
            instance.end_date = date_utilities.server_date_representation(result['endDate'])
        if 'owner' in result:
            instance.owner = result['owner']
        if 'serviceUrl' in result:
            instance.endpoints = cls.parse_service_urls(result['serviceUrl'].items())
        if 'pipelineRunParameters' in result:
            for parameter in result['pipelineRunParameters']:
                if 'value' in parameter and 'name' in parameter:
                    instance.parameters.append(PipelineRunParameterModel(parameter['name'], parameter['value'], None, False))
                    if parameter['name'] == 'parent-id' and parameter['value'] != 0:
                        instance.parent_id = parameter['value']
                elif 'name' in parameter:
                    instance.parameters.append(PipelineRunParameterModel(parameter['name'], None, None, False))
        if 'instance' in result:
            instance.instance = result['instance'].items()
        if 'podIP' in result:
            instance.pod_ip = result['podIP']
        if 'sshPassword' in result:
            instance.ssh_pass = result['sshPassword']
        node_ip_exists = False
        for (key, value) in instance.instance:
            if key == 'nodeIP':
                node_ip_exists = True
        if instance.status is not None and instance.status.upper() == 'RUNNING' and \
                (instance.instance is None or not node_ip_exists):
            instance.status = 'SCHEDULED'

        if 'runSids' in result and result['runSids'] is not None and len(result['runSids']) > 0:
            for item in result['runSids']:
                instance.run_sids.append(RunSid(item.get('name', None), item.get('isPrincipal', None),
                                                item.get('accessType', 'ENDPOINT')))

        if 'sensitive' in result:
            instance.sensitive = result['sensitive']

        instance.platform = result.get('platform')

        return instance

    @staticmethod
    def parse_service_urls(items):
        endpoints = []
        for region, service_urls_string in items:
            try:
                if not service_urls_string:
                    continue
                service_urls = {record["name"]: record['url'] for record in json.loads(service_urls_string) if 'url' in record}
                for name, service_url in service_urls.items():
                    endpoints.append("%s : %s : %s" % (name, region, service_url))
            except Exception:
                logging.exception('Service URL cannot be parsed.')
        return endpoints


class PriceType:
    ON_DEMAND = 'on-demand'
    SPOT = 'spot'
