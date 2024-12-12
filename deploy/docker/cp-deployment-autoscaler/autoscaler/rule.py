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

import logging
from abc import ABC, abstractmethod
import time

from autoscaler.cluster.provider import NodeProvider
from autoscaler.config import OnLostInstancesStrategy, AutoscalingConfiguration, OnLostNodesStrategy, \
    OnNotRunningTargetPodsStrategy
from autoscaler.exception import AbortScalingError
from autoscaler.instance.provider import InstanceProvider
from autoscaler.model import DeploymentsContainer, NodesContainer, InstancesContainer, Node
from autoscaler.scaler import NodeScaler
from autoscaler.timer import AutoscalingTimer


class AutoscalingRule(ABC):

    @abstractmethod
    def apply(self, deployments_container, nodes_container, instances_container):
        pass


class LostTransientInstancesRule(AutoscalingRule):

    def __init__(self, configuration: AutoscalingConfiguration, instance_provider: InstanceProvider):
        self._configuration = configuration
        self._instance_provider = instance_provider

    def apply(self, deployments_container, nodes_container, instances_container):
        logging.info('Searching for lost transient instances...')
        transient_lost_instances = []
        for instance in instances_container.transient_instances:
            if instance.name not in nodes_container.target_node_names:
                transient_lost_instances.append(instance)
        if transient_lost_instances:
            logging.warning('Found %s lost transient instances: %s.',
                            len(transient_lost_instances), transient_lost_instances)
            if self._configuration.rules.on_lost_instances == OnLostInstancesStrategy.STOP:
                logging.warning('Terminating lost transient instances...')
                for instance in transient_lost_instances:
                    self._instance_provider.terminate_instance(instance)
                raise AbortScalingError()


class LostTransientNodesRule(AutoscalingRule):

    def __init__(self, configuration: AutoscalingConfiguration, node_provider: NodeProvider):
        self._configuration = configuration
        self._node_provider = node_provider

    def apply(self, deployments_container, nodes_container, instances_container):
        logging.info('Searching for lost transient nodes...')
        transient_lost_nodes = []
        for node in nodes_container.transient_nodes:
            if node.name not in instances_container.transient_instance_names:
                transient_lost_nodes.append(node)
        if transient_lost_nodes:
            logging.warning('Found %s lost transient nodes: %s.',
                            len(transient_lost_nodes), transient_lost_nodes)
            if self._configuration.rules.on_lost_nodes == OnLostNodesStrategy.STOP:
                logging.warning('Deleting lost transient nodes...')
                for node in transient_lost_nodes:
                    self._node_provider.drain_node(node)
                    self._node_provider.delete_node(node)
                raise AbortScalingError()


class NoRunningTargetPodsOnNodeRule(AutoscalingRule):
    """
    The current rule implements scaling down nodes with no not-terminated target pods.
    Forbidden nodes (configuration.target.forbidden_nodes) shall not be scaled down.
    The minimum cluster size shall be kept up.
    Scaling down shall not be applied if interval between last scaling process is nor greater than minimum acceptable
    (config.limit.min_scale_interval)
    """
    def __init__(self, configuration: AutoscalingConfiguration, node_provider: NodeProvider,
                 node_scaler: NodeScaler, timer: AutoscalingTimer):
        self._configuration = configuration
        self._node_provider = node_provider
        self._node_scaler = node_scaler
        self._timer = timer

    def apply(self, deployments_container: DeploymentsContainer, nodes_container: NodesContainer,
              instances_container: InstancesContainer):
        logging.info('Searching for nodes without running target pods...')
        empty_nodes = []
        for node in nodes_container.transient_nodes:
            if self._node_provider.has_running_target_pods(node.name, deployments_container.pods):
                continue
            if node.name in self._configuration.target.forbidden_nodes:
                logging.warning(f"Node '{node.name}' hasn't running target pods but forbidden to scale down. "
                                f"Skipping node.")
                continue
            empty_nodes.append(node)
        if empty_nodes:
            logging.info(f"Found '{len(empty_nodes)}'/'{nodes_container.nodes_number}' nodes without running "
                         f"target pods.")
            if self._timer.last_scale_operation_time:
                current_scale_operation = time.time()
                scale_interval = current_scale_operation - self._timer.last_scale_operation_time
                if scale_interval <= self._configuration.limit.min_scale_interval:
                    logging.info('Scale interval (%.1f) < minimum scale interval (%.1f). '
                                 'Scaling nodes ↓ is aborted.',
                                 scale_interval, self._configuration.limit.min_scale_interval)
                    return
            available_to_scale_down_count = nodes_container.nodes_number - self._configuration.limit.min_nodes_number
            if available_to_scale_down_count <= 0:
                logging.info("Scaling nodes ↓ forbidden: minimum nodes number is "
                             f"'{self._configuration.limit.min_nodes_number}'.")
                return
            available_to_scale_down_count = min(available_to_scale_down_count, len(empty_nodes))
            empty_nodes = empty_nodes[:available_to_scale_down_count]
            if self._configuration.rules.on_not_running_target_pods == OnNotRunningTargetPodsStrategy.STOP:
                self._node_scaler.scale_down(empty_nodes)
                raise AbortScalingError()
            else:
                logging.info("Skipping scaling nodes ↓")
        else:
            logging.debug('No nodes without running target pods found.')
