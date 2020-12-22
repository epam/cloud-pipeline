# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

from .base import API
from ..model.cluster_node_model import ClusterNodeModel
from ..model.cluster_instance_type_model import ClusterInstanceTypeModel


class Cluster(API):
    def __init__(self):
        super(Cluster, self).__init__()

    @classmethod
    def list(cls):
        api = cls.instance()
        result = []
        response_data = api.call('cluster/node/loadAll', None)
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        if 'payload' in response_data:
            for cluster_node_json in response_data['payload']:
                result.append(ClusterNodeModel.load(cluster_node_json))
        return result

    @classmethod
    def get(cls, name):
        api = cls.instance()
        response_data = api.call('cluster/node/{}/load'.format(name), None)
        return ClusterNodeModel.load(response_data['payload'])

    @classmethod
    def terminate_node(cls, name):
        api = cls.instance()
        response_data = api.call('cluster/node/{}'.format(name), None, http_method='delete')
        return ClusterNodeModel.load(response_data['payload'])

    @classmethod
    def list_instance_types(cls):
        api = cls.instance()
        response_data = api.call('cluster/instance/loadAll', None)
        result = []
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        if 'payload' in response_data:
            for instance_type_json in response_data['payload']:
                result.append(ClusterInstanceTypeModel.load(instance_type_json))
        return result

    @classmethod
    def download_usage_report(cls, instance_id, date_from, date_to, interval, file_path, report_type):
        api = cls.instance()
        url_path = 'cluster/node/%s/usage/report?interval=%s&from=%s&to=%s&type=%s' \
                   % (instance_id, interval, date_from, date_to, report_type.upper())
        api.download(url_path, file_path)
