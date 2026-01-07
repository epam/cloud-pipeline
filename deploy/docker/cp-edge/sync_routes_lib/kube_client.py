# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
try:
    from pykube.config import KubeConfig
    from pykube.http import HTTPClient
    from pykube.objects import Pod, Service
except ImportError:
    raise RuntimeError('pykube is not installed.')

from .config import (
    CP_KUBE_NAMESPACE, 
    EDGE_SVC_ROLE_LABEL, 
    EDGE_SVC_ROLE_LABEL_VALUE, 
    EDGE_SVC_REGION_LABEL,
    EDGE_SVC_HOST_LABEL,
    EDGE_SVC_PORT_LABEL,
    NUMBER_OF_RETRIES,
    SECS_TO_WAIT_BEFORE_RETRY
)
from .logger import do_log
import time

class KubeClient:
    def __init__(self, kube_config_path=None, api=None):
        if api:
            self.api = api
        elif kube_config_path and os.path.exists(kube_config_path):
            do_log('Using kubeconfig at {}'.format(kube_config_path))
            self.api = HTTPClient(KubeConfig.from_file(kube_config_path))
        else:
            do_log('Using in-cluster service account configuration')
            self.api = HTTPClient(KubeConfig.from_service_account())
        
        if not api:
            self.api.session.verify = False

    def get_pods(self, selector):
        return Pod.objects(self.api, namespace=CP_KUBE_NAMESPACE).filter(
            selector=selector
        ).filter(field_selector={"status.phase": "Running"})

    def get_edge_service_details(self, edge_region_name, edge_region_id):
        edge_kube_service_object = None
        edge_service_external_ip = None
        edge_service_port = None

        for n in range(NUMBER_OF_RETRIES):
            edge_kube_service = Service.objects(self.api, namespace=CP_KUBE_NAMESPACE).filter(selector={
                EDGE_SVC_ROLE_LABEL: EDGE_SVC_ROLE_LABEL_VALUE, EDGE_SVC_REGION_LABEL: edge_region_name})
            
            if not edge_kube_service.response['items']:
                do_log('EDGE service is not found by labels: cloud-pipeline/role=EDGE and %s=%s'
                       % (EDGE_SVC_REGION_LABEL, edge_region_name))
                return None, None
            else:
                edge_kube_service_object = edge_kube_service.response['items'][0]
                metadata = edge_kube_service_object['metadata']

                if 'labels' in metadata and EDGE_SVC_HOST_LABEL in metadata['labels']:
                    do_log('Getting EDGE service host from service label')
                    edge_service_external_ip = metadata['labels'][EDGE_SVC_HOST_LABEL]

                if 'labels' in metadata and EDGE_SVC_PORT_LABEL in metadata['labels']:
                    do_log('Getting EDGE service host port from service label')
                    edge_service_port = metadata['labels'][EDGE_SVC_PORT_LABEL]

                if edge_service_external_ip and edge_service_port:
                    break
                else:
                    do_log('Sleep for {} sec and perform kube API call again ({}/{})'.format(SECS_TO_WAIT_BEFORE_RETRY, n + 1, NUMBER_OF_RETRIES))
                    time.sleep(SECS_TO_WAIT_BEFORE_RETRY)

        if not edge_service_external_ip:
             do_log('Getting EDGE service host from externalIP')
             if edge_kube_service_object.get('spec', {}).get('externalIPs'):
                edge_service_external_ip = edge_kube_service_object['spec']['externalIPs'][0]
        
        if not edge_service_port:
            do_log('Getting EDGE service host port from nodePort')
            if edge_kube_service_object['ports']:
                edge_service_port = edge_kube_service_object['ports'][0]['nodePort']

        do_log('EDGE: {}:{} ({} #{})'.format(edge_service_external_ip, edge_service_port,
                                             edge_region_name, edge_region_id or 'undefined'))
        return edge_service_external_ip, edge_service_port
