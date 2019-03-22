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
from .pipeline_run_model import PipelineRunModel
from .pod_model import PodModel


class ClusterNodeModel(object):
    def __init__(self):
        self.identifier = None
        self.name = None
        self.created = None
        self.run = None
        self.addresses = []
        self.system_info = None
        self.labels = None
        self.allocatable = None
        self.capacity = None
        self.is_master = False
        self.pods = []

    @classmethod
    def load(cls, json):
        instance = cls()
        instance.identifier = json['uid']
        instance.name = json['name']
        instance.created = datetime.datetime.strptime(json['creationTimestamp'], '%Y-%m-%dT%H:%M:%SZ')
        if 'addresses' in json:
            for address in json['addresses']:
                instance.addresses.append('{} ({})'.format(address['address'], address['type']))
        if 'pipelineRun' in json:
            instance.run = PipelineRunModel.load(json['pipelineRun'])
        if 'systemInfo' in json:
            instance.system_info = json['systemInfo'].items()
        if 'labels' in json:
            instance.labels = json['labels'].items()
            for label in instance.labels:
                if label[0].lower() == 'node-role.kubernetes.io/master':
                    instance.is_master = True
                elif label[0].lower() == 'kubeadm.alpha.kubernetes.io/role':
                    instance.is_master = label[1].lower() == 'master'
                elif label[0].lower() == 'cloud-pipeline/role':
                    instance.is_master = label[1].lower() == 'edge'
        if 'allocatable' in json:
            instance.allocatable = json['allocatable']
        if 'capacity' in json:
            instance.capacity = json['capacity']
        if 'pods' in json:
            for pod_json in json['pods']:
                instance.pods.append(PodModel.load(pod_json))
        return instance
