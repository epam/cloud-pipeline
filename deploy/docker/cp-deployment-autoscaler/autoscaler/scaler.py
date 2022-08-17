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

import logging

from autoscaler.cluster.provider import DeploymentProvider, NodeProvider, PodProvider
from autoscaler.config import AutoscalingConfiguration
from autoscaler.exception import NodeScaleUpTimeoutError, ForbiddenNodeScaleDownError
from autoscaler.instance.provider import InstanceProvider
from autoscaler.model import Condition, Instance, Persistence
from autoscaler.timer import AutoscalingTimer


class DeploymentScaler:

    def __init__(self, configuration, timer, deployment_provider):
        self._configuration: AutoscalingConfiguration = configuration
        self._timer: AutoscalingTimer = timer
        self._deployment_provider: DeploymentProvider = deployment_provider

    @AutoscalingTimer.wraps
    def scale_up(self, deployments):
        logging.info('Scaling deployment ↑...')
        for deployment in deployments:
            logging.info('Scaling deployment ↑ from %s to %s replicas...',
                         deployment.replicas, deployment.replicas + 1)
            self._deployment_provider.scale_deployment(deployment, replicas=deployment.replicas + 1)

    @AutoscalingTimer.wraps
    def scale_down(self, deployments):
        logging.info('Scaling deployment ↓...')
        for deployment in deployments:
            logging.info('Scaling deployment ↓ from %s to %s replicas...',
                         deployment.replicas, deployment.replicas - 1)
            self._deployment_provider.scale_deployment(deployment, replicas=deployment.replicas - 1)


class NodeScaler:

    def __init__(self, configuration, timer, node_provider, instance_provider, pod_provider):
        self._configuration: AutoscalingConfiguration = configuration
        self._timer: AutoscalingTimer = timer
        self._node_provider: NodeProvider = node_provider
        self._instance_provider: InstanceProvider = instance_provider
        self._pod_provider: PodProvider = pod_provider

    @AutoscalingTimer.wraps
    def scale_up(self):
        logging.info('Scaling nodes ↑...')
        instance_id, _ = self._instance_provider.launch_instance()
        node = self._register_node(instance_id)
        self._initialize_node(node)
        logging.info('Node %s has been scaled up.', instance_id)

    def _register_node(self, node_name):
        try:
            logging.info('Registering node %s...', node_name)
            timeout = self._configuration.timeout.scale_up_node_timeout
            node = None
            while timeout > 0:
                time.sleep(self._configuration.timeout.scale_up_node_delay)
                timeout -= self._configuration.timeout.scale_up_node_delay
                node = self._node_provider.get_node(name=node_name)
                if node:
                    logging.info('Node %s has been registered.', node_name)
                    break
            if not node:
                logging.warning('Node %s has not been registered after %s seconds.',
                                node_name, self._configuration.timeout.scale_up_node_timeout)
                raise NodeScaleUpTimeoutError(node_name)
            return node
        except Exception:
            logging.warning('Node %s registration has failed. It will be scaled down.', node_name)
            self._instance_provider.terminate_instance(Instance(name=node_name, persistence=Persistence.TRANSIENT))
            raise

    def _initialize_node(self, node):
        try:
            logging.info('Initializing node %s...', node.name)
            pods = list(self._pod_provider.get_pods_by_nodes([node]))
            timeout = self._configuration.timeout.scale_up_node_timeout
            while timeout > 0:
                time.sleep(self._configuration.timeout.scale_up_node_delay)
                timeout -= self._configuration.timeout.scale_up_node_delay
                for pod in list(pods):
                    pod_conditions = self._pod_provider.get_pod_conditions(pod)
                    if Condition.READY in pod_conditions:
                        logging.info('System pod %s is ready on node %s.', pod.name, node.name)
                        pods.remove(pod)
                if not pods:
                    break
            if timeout <= 0:
                logging.warning('Node %s has not been initialized after %s seconds.',
                                node.name, self._configuration.timeout.scale_up_node_timeout)
                raise NodeScaleUpTimeoutError(node.name)
            logging.info('Node %s has been initialized.', node.name)
        except Exception:
            logging.warning('Node %s initialization has failed. It will be scaled down.', node.name)
            self.scale_down_node(node)
            raise

    @AutoscalingTimer.wraps
    def scale_down(self, nodes):
        logging.info('Scaling nodes ↓...')
        node = self._select_node_for_scale_down(nodes)
        if not node:
            logging.info('Skipping scaling down because there are no suitable nodes...')
            return
        self.scale_down_node(node)

    def _select_node_for_scale_down(self, nodes):
        logging.info('Selecting node to scale down from %s...', nodes)
        if not nodes:
            logging.info('There are no nodes to scale down.')
            return None
        scaling_down_node = nodes[0]
        if scaling_down_node.name in self._configuration.target.forbidden_nodes:
            logging.warning('Node %s is forbidden to be scaled down.')
            raise ForbiddenNodeScaleDownError(scaling_down_node.name)
        logging.info('Node %s has been selected for scaling down.', scaling_down_node.name)
        return scaling_down_node

    @AutoscalingTimer.wraps
    def scale_down_node(self, node):
        try:
            self._node_provider.drain_node(node)
            self._node_provider.delete_node(node)
        finally:
            self._instance_provider.terminate_instance(Instance(name=node.name, persistence=node.persistence))
