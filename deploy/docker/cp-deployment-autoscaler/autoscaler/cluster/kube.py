# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import time
from typing import Iterator, Optional

import logging
import pykube

from autoscaler.cluster.provider import NodeProvider, PodProvider, DeploymentProvider
from autoscaler.config import AutoscalingConfiguration
from autoscaler.exception import NodeEvictionTimeoutError
from autoscaler.model import Node, Condition, Pod, Deployment, Persistence


class KubeProvider(NodeProvider, PodProvider, DeploymentProvider):

    def __init__(self, kube: pykube.HTTPClient, configuration: AutoscalingConfiguration):
        self._kube = kube
        self._configuration = configuration
        self._conditions_mapping = {
            'MemoryPressure': Condition.MEMORY_PRESSURE,
            'DiskPressure': Condition.DISK_PRESSURE,
            'PIDPressure': Condition.PID_PRESSURE,
            'Ready': Condition.READY
        }

    def get_node(self, name: str) -> Optional[Node]:
        try:
            kube_node = pykube.Node.objects(self._kube).get_by_name(name)
            return self._get_node(kube_node)
        except pykube.exceptions.ObjectDoesNotExist:
            return None

    def get_nodes_by_pods(self, pods: [Pod]) -> Iterator[Node]:
        logging.info('Searching for target nodes...')
        nodes = [node for node in pykube.Node.objects(self._kube)
                 if any(self._configuration.target.labels.get(key) == value
                        for key, value in node.labels.items())]
        node_names = [node.name for node in nodes]
        logging.debug('Found %s target nodes: %s.', len(nodes), nodes)

        logging.info('Resolving reserved target nodes...')
        logging.debug('Resolving reserved by label target nodes...')
        reserved_by_label_node_names = list(set(pod.node_name for pod in pods if pod.reserved))
        logging.debug('Resolved %s reserved by label target nodes: %s.',
                      len(reserved_by_label_node_names), reserved_by_label_node_names)
        logging.debug('Resolving reserved by non target pods target nodes...')
        pod_names = [pod.name for pod in pods]
        reserved_by_non_target_pods_node_names = list(set(pod.obj.get('spec', {}).get('nodeName', '')
                                                          for pod in pykube.Pod.objects(self._kube)
                                                          if pod.obj.get('spec', {}).get('nodeName', '') in node_names
                                                          and pod.name not in pod_names
                                                          and not self._is_owned_by_daemon_set(pod)))
        logging.debug('Resolved %s reserved by non target pods target nodes: %s.',
                      len(reserved_by_non_target_pods_node_names), reserved_by_non_target_pods_node_names)
        reserved_node_names = list(set(reserved_by_label_node_names + reserved_by_non_target_pods_node_names))
        logging.debug('Resolved %s reserved target nodes.', len(reserved_node_names))

        used_node_names = list(set(pod.node_name for pod in pods))
        for node in nodes:
            yield self._get_node(node, used_node_names, reserved_node_names)

    def _is_owned_by_daemon_set(self, child):
        for child_owner in child.metadata.get('ownerReferences', []):
            if child_owner.get('kind', '') == 'DaemonSet':
                return True
        return False

    def _get_node(self, kube_node, used_node_names=None, reserved_node_names=None):
        used_node_names = used_node_names or []
        reserved_node_names = reserved_node_names or []
        return Node(
            name=kube_node.name,
            persistence=Persistence.TRANSIENT if self._is_transient(kube_node) else Persistence.PERSISTENT,
            reserved=kube_node.name in reserved_node_names,
            used=kube_node.name in used_node_names)

    def _is_transient(self, kube_node):
        return any(kube_node.labels.get(key) == value for key, value in self._configuration.target.transient_labels.items())

    def get_node_conditions(self, node: Node) -> Iterator[Condition]:
        yield from self._get_conditions(pykube.Node, node.name)

    def get_all_nodes_count(self) -> int:
        return len(pykube.Node.objects(self._kube))

    def drain_node(self, node: Node):
        logging.info('Draining node %s...', node.name)
        self._cordon_node(node)
        self._evict_node(node)
        logging.info('Node %s has been drained.', node.name)

    def _cordon_node(self, node: Node):
        logging.info('Cordoning node %s...', node.name)
        kube_node = pykube.Node.objects(self._kube).get_by_name(node.name)
        kube_node.cordon()
        logging.info('Node %s has been cordoned.', node.name)

    def _evict_node(self, node: Node):
        logging.info('Evicting node %s...', node.name)
        kube_pods = []
        for kube_pod in pykube.Pod.objects(self._kube):
            if self._is_owned_by_daemon_set(kube_pod):
                continue
            if node.name and kube_pod.obj.get('spec', {}).get('nodeName', '') == node.name:
                logging.info('Deleting pod %s...', kube_pod.name)
                kube_pod.delete()
                kube_pods.append(kube_pod)
        timeout = self._configuration.timeout.scale_down_node_timeout
        while timeout > 0:
            time.sleep(self._configuration.timeout.scale_down_node_delay)
            timeout -= self._configuration.timeout.scale_down_node_delay
            for kube_pod in list(kube_pods):
                if not kube_pod.exists():
                    logging.info('Pod %s is deleted on node %s.', kube_pod.name, node.name)
                    kube_pods.remove(kube_pod)
            if not kube_pods:
                break
        if timeout <= 0:
            logging.warning('Node %s has not been evicted after %s seconds.',
                            node.name, self._configuration.timeout.scale_down_node_timeout)
            raise NodeEvictionTimeoutError(node.name)
        logging.info('Node %s has been evicted.', node.name)

    def delete_node(self, node: Node):
        logging.info('Deleting node %s...', node.name)
        kube_node = pykube.Node.objects(self._kube).get_by_name(node.name)
        kube_node.delete()
        logging.info('Node %s has been deleted.', node.name)

    def get_pods_by_nodes(self, nodes: [Node]) -> Iterator[Pod]:
        for node in nodes:
            for kube_pod in pykube.Pod.objects(self._kube, namespace='kube-system'):
                if node.name and kube_pod.obj.get('spec', {}).get('nodeName', '') != node.name:
                    continue
                yield self._get_pod(kube_pod)

    def get_pods_by_deployments(self, deployments: [Deployment]) -> Iterator[Pod]:
        reserved_by_label_pods = [deployment.labels.get(reserved_label)
                                  for reserved_label in self._configuration.target.reserved_labels
                                  for deployment in deployments]
        reserved_by_label_pods = [leader_pod for leader_pod in reserved_by_label_pods if leader_pod]
        replica_sets = [replica_set for replica_set in pykube.ReplicaSet.objects(self._kube)
                        if self._is_owned_by_deployments(replica_set, *deployments)]
        pods = [pod for pod in pykube.Pod.objects(self._kube) if self._is_owned_by_replica_sets(pod, *replica_sets)]
        for pod in pods:
            yield self._get_pod(pod, reserved_by_label_pods)

    def _is_owned_by_deployments(self, child, *deployments):
        for child_owner in child.metadata.get('ownerReferences', []):
            for deployment in deployments:
                if child_owner.get('kind', '') == 'Deployment' and child_owner.get('name', '') == deployment.name:
                    return True
        return False

    def _is_owned_by_replica_sets(self, child, *replica_sets):
        for child_owner in child.metadata.get('ownerReferences', []):
            for replica_set in replica_sets:
                if child_owner.get('kind', '') == 'ReplicaSet' and child_owner.get('name', '') == replica_set.name:
                    return True
        return False

    def _get_pod(self, kube_pod, reserved_pod_names=None):
        reserved_pod_names = reserved_pod_names or []
        return Pod(
            name=kube_pod.name,
            namespace=kube_pod.namespace,
            node_name=kube_pod.obj.get('spec', {}).get('nodeName', ''),
            reserved=kube_pod.name in reserved_pod_names)

    def get_pod_conditions(self, pod: Pod) -> Iterator[Condition]:
        yield from self._get_conditions(pykube.Pod, pod.name, pod.namespace)

    def _get_conditions(self, entity_type, entity_name, entity_namespace=None):
        kube_entity = entity_type.objects(self._kube, namespace=entity_namespace).get_by_name(entity_name)
        for condition_type in self._conditions_mapping.keys():
            for condition in kube_entity.obj.get('status').get('conditions', []):
                if self._is_condition_active(condition, condition_type):
                    yield self._conditions_mapping[condition_type]

    def _is_condition_active(self, condition, condition_type):
        return condition.get('type') == condition_type and condition.get('status') == 'True'

    def get_deployments(self, names: [str]) -> Iterator[Deployment]:
        for name in names:
            kube_deployment = pykube.Deployment.objects(self._kube).get_by_name(name)
            yield Deployment(
                name=kube_deployment.name,
                replicas=kube_deployment.replicas,
                labels=dict(kube_deployment.labels))

    def scale_deployment(self, deployment: Deployment, replicas: int):
        kube_deployment = pykube.Deployment.objects(self._kube).get_by_name(deployment.name)
        kube_deployment.replicas = replicas
        kube_deployment.update()
