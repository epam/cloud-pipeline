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

from time import sleep

import pykube
import utils
import re

RUN_ID_LABEL = 'runid'
CLOUD_REGION_LABEL = 'cloud_region'
LOW_PRIORITY_INSTANCE_ID_TEMPLATE = '(az-[a-z0-9]{16})[0-9A-Z]{6}'


class KubeProvider(object):

    def __init__(self):
        try:
            self.api = pykube.HTTPClient(pykube.KubeConfig.from_service_account())
        except Exception:
            self.api = pykube.HTTPClient(pykube.KubeConfig.from_file("~/.kube/config"))
        self.api.session.verify = False

    def get_nodename(self, nodename):
        node = pykube.Node.objects(self.api).filter(field_selector={'metadata.name': nodename})
        if len(node.response['items']) > 0:
            return nodename
        else:
            return ''

    def find_node(self, nodename, nodename_full):
        ret_namenode = self.get_nodename(nodename)
        if not ret_namenode:
            return self.get_nodename(nodename_full)
        else:
            return ret_namenode

    def delete_kube_node(self, nodename, run_id):
        if nodename is None:
            nodes = pykube.Node.objects(self.api).filter(selector={RUN_ID_LABEL: run_id})
            if len(nodes.response['items']) > 0:
                node = nodes.response['items'][0]
                nodename = node['metadata']['name']
        if nodename is not None:
            obj = {
                "apiVersion": "v1",
                "kind": "Node",
                "metadata": {
                    "name": nodename,
                    "labels": {
                        "runid": run_id
                    }
                }
            }
            pykube.Node(self.api, obj).delete()

    def get_node(self, nodename):
        nodes = pykube.Node.objects(self.api).filter(field_selector={'metadata.name': nodename})
        if len(nodes.response['items']) == 0:
            return None
        return nodes.response['items'][0]

    def delete_kubernetes_node_by_name(self, node_name):
        if node_name is not None and self.get_node(node_name) is not None:
            obj = {
                "apiVersion": "v1",
                "kind": "Node",
                "metadata": {
                    "name": node_name
                }
            }
            pykube.Node(self.api, obj).delete()

    def label_node(self, nodename, run_id, cluster_name, cluster_role, cloud_region):
        utils.pipe_log('Assigning instance {} to RunID: {}'.format(nodename, run_id))
        obj = {
            "apiVersion": "v1",
            "kind": "Node",
            "metadata": {
                "name": nodename,
                "labels": {
                    "runid": run_id,
                    "cloud_region": cloud_region
                }
            }
        }

        if cluster_name:
            obj["metadata"]["labels"]["cp-cluster-name"] = cluster_name
        if cluster_role:
            obj["metadata"]["labels"]["cp-cluster-role"] = cluster_role

        pykube.Node(self.api, obj).update()
        utils.pipe_log('Instance {} is assigned to RunID: {}\n-'.format(nodename, run_id))

    def verify_regnode(self, ins_id, nodename, nodename_full, num_rep, time_rep):

        utils.pipe_log('Waiting for instance {} registration in cluster with name {}'.format(ins_id, nodename))

        ret_namenode = ''
        rep = 0
        while rep <= num_rep:
            ret_namenode = self.find_node(nodename, nodename_full)
            if ret_namenode:
                break
            rep = utils.increment_or_fail(num_rep, rep,
                                    'Exceeded retry count ({}) for instance (ID: {}, NodeName: {}) cluster registration'.format(
                                        num_rep, ins_id, nodename))
            sleep(time_rep)

        if ret_namenode:  # useless?
            utils.pipe_log('- Node registered in cluster as {}'.format(ret_namenode))
            rep = 0
            while rep <= num_rep:
                node = pykube.Node.objects(self.api).filter(field_selector={'metadata.name': ret_namenode})
                status = node.response['items'][0]['status']['conditions'][3]['status']
                if status == u'True':
                    utils.pipe_log('- Node ({}) status is READY'.format(ret_namenode))
                    break
                rep = utils.increment_or_fail(num_rep, rep,
                                        'Exceeded retry count ({}) for instance (ID: {}, NodeName: {}) kube node READY check'.format(
                                            num_rep, ins_id, ret_namenode))
                sleep(time_rep)

            rep = 0
            utils.pipe_log('- Waiting for system agents initialization...')
            while rep <= num_rep:
                pods = pykube.objects.Pod.objects(self.api).filter(namespace="kube-system",
                                                              field_selector={"spec.nodeName": ret_namenode})
                count_pods = len(pods.response['items'])
                ready_pods = len([p for p in pods if p.ready])
                if count_pods == ready_pods:
                    break
                utils.pipe_log('- {} of {} agents initialized. Still waiting...'.format(ready_pods, count_pods))
                rep = utils.increment_or_fail(num_rep, rep,
                                        'Exceeded retry count ({}) for instance (ID: {}, NodeName: {}) kube system pods check'.format(
                                            num_rep, ins_id, ret_namenode))
                sleep(time_rep)
            utils.pipe_log('Instance {} successfully registred in cluster with name {}\n-'.format(ins_id, nodename))
        return ret_namenode

    def verify_node_exists(self, ins_id, nodename_full, nodename):

        exist_node = False
        ret_namenode = ""
        node = pykube.Node.objects(self.api).filter(field_selector={'metadata.name': nodename})
        if len(node.response['items']) > 0:
            exist_node = True
            ret_namenode = nodename
        node_full = pykube.Node.objects(self.api).filter(field_selector={'metadata.name': nodename_full})
        if len(node_full.response['items']) > 0:
            exist_node = True
            ret_namenode = nodename_full
        if not exist_node:
            raise RuntimeError("Failed to find Node {}".format(ins_id))
        return ret_namenode

    def get_cloud_region(self, run_id):
        nodes = pykube.Node.objects(self.api).filter(selector={RUN_ID_LABEL: run_id})
        if len(nodes.response['items']) == 0:
            raise RuntimeError('Cannot find node matching RUN ID %s' % run_id)
        node = nodes.response['items'][0]
        labels = node['metadata']['labels']
        if CLOUD_REGION_LABEL not in labels:
            raise RuntimeError('Node %s is not labeled with Azure Region' % node['metadata']['name'])
        return labels[CLOUD_REGION_LABEL]

    def get_cloud_region_by_node_name(self, nodename):
        nodes = pykube.Node.objects(self.api).filter(field_selector={'metadata.name': nodename})
        if len(nodes.response['items']) == 0:
            return None
        node = nodes.response['items'][0]
        labels = node['metadata']['labels']
        if CLOUD_REGION_LABEL not in labels:
            raise RuntimeError('Node %s is not labeled with Cloud Region' % node['metadata']['name'])
        return labels[CLOUD_REGION_LABEL]

    def get_run_id_by_node_name(self, nodename):
        nodes = pykube.Node.objects(self.api).filter(field_selector={'metadata.name': nodename})
        if len(nodes.response['items']) == 0:
            return None
        node = nodes.response['items'][0]
        labels = node['metadata']['labels']
        if RUN_ID_LABEL not in labels:
            raise RuntimeError('Node %s is not labeled with Cloud Region' % node['metadata']['name'])
        return labels[RUN_ID_LABEL]

    def change_label(self, nodename, new_id, cloud_region):
        obj = {
            "apiVersion": "v1",
            "kind": "Node",
            "metadata": {
                "name": nodename,
                "labels": {
                    RUN_ID_LABEL: new_id
                }
            }
        }
        node = pykube.Node(self.api, obj)
        node.labels[RUN_ID_LABEL] = new_id
        node.labels[CLOUD_REGION_LABEL] = cloud_region
        node.update()

    # There is a strange behavior, when you create scale set with one node,
    # several node will be created at first and then only one of it will stay as running
    # Some times other nodes can rich startup script before it will be terminated, so nodes will join a kube cluster
    # In order to get rid of this 'phantom' nodes
    # this method will delete nodes with name like computerNamePrefix + 000000
    def delete_phantom_low_priority_kubernetes_node(self, ins_id):
        low_priority_search = re.search(LOW_PRIORITY_INSTANCE_ID_TEMPLATE, ins_id)
        if low_priority_search:
            scale_set_name = low_priority_search.group(1)

            # according to naming of azure scale set nodes: computerNamePrefix + hex postfix (like 000000)
            # delete node that opposite to ins_id
            nodes_to_delete = [scale_set_name + '%0*x' % (6, x) for x in range(0, 15)]
            for node_to_delete in nodes_to_delete:

                if node_to_delete == ins_id:
                    continue

                nodes = pykube.Node.objects(self.api).filter(field_selector={'metadata.name': node_to_delete})
                for node in nodes.response['items']:
                    obj = {
                        "apiVersion": "v1",
                        "kind": "Node",
                        "metadata": {
                            "name": node["metadata"]["name"]
                        }
                    }
                    pykube.Node(self.api, obj).delete()
