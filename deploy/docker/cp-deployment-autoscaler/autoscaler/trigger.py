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

from enum import Enum
from typing import Tuple

import datetime
import logging
import sys
from abc import ABC, abstractmethod

from autoscaler.cluster.provider import NodeProvider
from autoscaler.config import AutoscalingConfiguration
from autoscaler.model import Condition


def _at_least(size, required_size, extra_size):
    return max(size, required_size + extra_size) if size >= required_size else required_size


def _scaling_msg(entities_type, entities_number, required_entities_number):
    if required_entities_number > entities_number:
        return f'Scaling {entities_type} ↑ from {entities_number} to {required_entities_number} is required. '
    elif required_entities_number < entities_number:
        return f'Scaling {entities_type} ↓ from {entities_number} to {required_entities_number} is required. '
    else:
        return f'Scaling {entities_type} is not required. '


class AutoscalingTrigger(ABC):

    @abstractmethod
    def apply(self, deployments_container, nodes_container, instances_container,
              nodes_number, required_nodes_number,
              replicas_number, required_replicas_number) -> Tuple[int, int]:
        pass


class CoefficientTrigger(AutoscalingTrigger):

    class EntitiesType(Enum):
        NODES = 1
        REPLICAS = 2

    def __init__(self, coefficient_name, coefficient_entities_type, coefficient_target, coefficient_func):
        self._coefficient_name = coefficient_name
        self._coefficient_entities_type = coefficient_entities_type
        self._coefficient_target = coefficient_target
        self._coefficient_func = coefficient_func

    def apply(self, deployments_container, nodes_container, instances_container,
              nodes_number, required_nodes_number,
              replicas_number, required_replicas_number) -> Tuple[int, int]:
        def _simplified_coefficient_func(entities_number):
            return self._coefficient_func(deployments_container, nodes_container, instances_container,
                                          entities_number if self._is_nodes_coefficient() else nodes_number,
                                          required_nodes_number,
                                          entities_number if not self._is_nodes_coefficient() else replicas_number,
                                          required_replicas_number)

        entities_number = nodes_number if self._is_nodes_coefficient() else replicas_number
        actual_coef = _simplified_coefficient_func(entities_number) if entities_number > 0 else sys.maxsize
        required_entities_number = 1
        required_coef = _simplified_coefficient_func(required_entities_number)
        for potential_entities_number in range(max(1, entities_number - 10), entities_number + 10):
            potential_coef = _simplified_coefficient_func(potential_entities_number)
            if abs(potential_coef - self._coefficient_target) < abs(required_coef - self._coefficient_target):
                required_entities_number = potential_entities_number
                required_coef = potential_coef
        if required_entities_number > entities_number:
            logging.info('[TRIGGER] %s (%.1f) -> (%.1f) ~ target (%.1f). '
                         'Scaling %s ↑ from %s to %s is required.',
                         self._coefficient_name, actual_coef, required_coef, self._coefficient_target,
                         self._coefficient_entities_type.name.lower(),
                         entities_number, required_entities_number)
        elif required_entities_number < entities_number:
            logging.info('[TRIGGER] %s (%.1f) -> (%.1f) ~ target (%.1f). '
                         'Scaling %s ↓ from %s to %s is required.',
                         self._coefficient_name, actual_coef, required_coef, self._coefficient_target,
                         self._coefficient_entities_type.name.lower(),
                         entities_number, required_entities_number)
        else:
            logging.info('Checking trigger %s (%.1f) ~ target (%.1f). '
                         'Scaling %s is not required.',
                         self._coefficient_name, actual_coef, self._coefficient_target,
                         self._coefficient_entities_type.name.lower())
        if self._is_nodes_coefficient():
            return required_entities_number, required_replicas_number
        else:
            return required_nodes_number, required_entities_number

    def _is_nodes_coefficient(self):
        return self._coefficient_entities_type == CoefficientTrigger.EntitiesType.NODES

    def _process_target_coef_trigger(self, trigger_name, entities_name, entities_number, target_coef, get_coef):
        actual_coef = get_coef(entities_number) if entities_number > 0 else sys.maxsize

        required_entities_number = 1
        required_coef = get_coef(required_entities_number)
        for potential_entities_number in range(max(1, entities_number - 10), entities_number + 10):
            potential_coef = get_coef(potential_entities_number)
            if abs(potential_coef - target_coef) < abs(required_coef - target_coef):
                required_entities_number = potential_entities_number
                required_coef = potential_coef

        if required_entities_number > entities_number:
            logging.info('[TRIGGER] %s (%.1f) -> (%.1f) ~ target (%.1f). '
                         'Scaling %s ↑ from %s to %s is required.',
                         trigger_name, actual_coef, required_coef, target_coef, entities_name,
                         entities_number, required_entities_number)
        elif required_entities_number < entities_number:
            logging.info('[TRIGGER] %s (%.1f) -> (%.1f) ~ target (%.1f). '
                         'Scaling %s ↓ from %s to %s is required.',
                         trigger_name, actual_coef, required_coef, target_coef, entities_name,
                         entities_number, required_entities_number)
        else:
            logging.info('Checking trigger %s (%.1f) ~ target (%.1f). '
                         'Scaling %s is not required.',
                         trigger_name, actual_coef, target_coef, entities_name)

        return required_entities_number


class ClusterNodesPerTargetReplicasCoefficientTrigger(CoefficientTrigger):

    def __init__(self, configuration):
        super().__init__('cluster nodes per target replicas coefficient', CoefficientTrigger.EntitiesType.REPLICAS,
                         configuration.trigger.cluster_nodes_per_target_replicas,
                         ClusterNodesPerTargetReplicasCoefficientTrigger._coefficient_func)

    @staticmethod
    def _coefficient_func(deployments_container, nodes_container, instances_container,
                          nodes_number, required_nodes_number,
                          replicas_number, required_replicas_number):
        return nodes_container.non_target_nodes_number / replicas_number


class TargetReplicasPerTargetNodesCoefficientTrigger(CoefficientTrigger):

    def __init__(self, configuration):
        super().__init__('target replicas per target nodes coefficient', CoefficientTrigger.EntitiesType.NODES,
                         configuration.trigger.target_replicas_per_target_nodes,
                         TargetReplicasPerTargetNodesCoefficientTrigger._coefficient_func)

    @staticmethod
    def _coefficient_func(deployments_container, nodes_container, instances_container,
                          nodes_number, required_nodes_number,
                          replicas_number, required_replicas_number):
        return required_replicas_number / nodes_number


class NodeConditionTrigger(AutoscalingTrigger):

    def __init__(self, configuration, node_provider, condition, condition_name, condition_target):
        self._configuration: AutoscalingConfiguration = configuration
        self._node_provider: NodeProvider = node_provider
        self._condition: Condition = condition
        self._condition_name: str = condition_name
        self._condition_target: int = condition_target

    def apply(self, deployments_container, nodes_container, instances_container,
              nodes_number, required_nodes_number,
              replicas_number, required_replicas_number) -> Tuple[int, int]:
        pressured_nodes_number = self._count_conditions(nodes_container.target_nodes,
                                                        self._condition)
        if pressured_nodes_number >= self._condition_target:
            required_nodes_number = _at_least(nodes_number, required_nodes_number,
                                              self._configuration.rules.on_threshold_trigger.extra_nodes)
            required_replicas_number = _at_least(replicas_number, required_replicas_number,
                                                 self._configuration.rules.on_threshold_trigger.extra_replicas)
            logging.info('[TRIGGER] %s (%s) >= target (%s). '
                         + _scaling_msg('nodes', nodes_number, required_nodes_number)
                         + _scaling_msg('replicas', replicas_number, required_replicas_number),
                         self._condition_name, pressured_nodes_number, self._condition_target)
        return required_nodes_number, required_replicas_number

    def _count_conditions(self, nodes, condition):
        conditions_number = 0
        for node in nodes:
            node_conditions = self._node_provider.get_node_conditions(node)
            if condition in node_conditions:
                logging.info('Detected %s on node %s.', condition, node.name)
                conditions_number += 1
        return conditions_number


class NodeDiskPressureTrigger(NodeConditionTrigger):

    def __init__(self, configuration, node_provider):
        super().__init__(configuration, node_provider, Condition.DISK_PRESSURE, 'disk pressured nodes',
                         configuration.trigger.disk_pressured_nodes)


class NodeMemoryPressureTrigger(NodeConditionTrigger):

    def __init__(self, configuration, node_provider):
        super().__init__(configuration, node_provider, Condition.MEMORY_PRESSURE, 'memory pressured nodes',
                         configuration.trigger.memory_pressured_nodes)


class NodePidPressureTrigger(NodeConditionTrigger):

    def __init__(self, configuration, node_provider):
        super().__init__(configuration, node_provider, Condition.PID_PRESSURE, 'pid pressured nodes',
                         configuration.trigger.pid_pressured_nodes)


class NodeHeapsterElasticMetricTrigger(AutoscalingTrigger):

    def __init__(self, configuration, elastic):
        self._configuration = configuration
        self._elastic = elastic

    def apply(self, deployments_container, nodes_container, instances_container,
              nodes_number, required_nodes_number,
              replicas_number, required_replicas_number) -> Tuple[int, int]:
        now = datetime.datetime.now()
        monitoring_timedelta = datetime.timedelta(seconds=self._configuration.trigger.cpu_utilization.monitoring_period)
        from_timestamp = (now - monitoring_timedelta).timestamp()
        to_timestamp = now.timestamp()
        logging.info('Resolving nodes utilization...')
        nodes_utilization = {}
        nodes_utilization_summary = 'Nodes utilization:'
        for node in nodes_container.target_nodes:
            cpu_utilization, memory_utilization = self._elastic.get_utilization(node, from_timestamp, to_timestamp)
            nodes_utilization[node] = (cpu_utilization, memory_utilization)
            nodes_utilization_summary += f'\n{node.name} ' \
                                         f'({cpu_utilization or "?"}% cpu, ' \
                                         f'{memory_utilization or "?"}% mem)'
        logging.info(nodes_utilization_summary)
        for node, (cpu_utilization, memory_utilization) in nodes_utilization.items():
            target_cpu_utilization = self._configuration.trigger.cpu_utilization.max
            if cpu_utilization is not None and cpu_utilization > target_cpu_utilization:
                return self._apply('nodes cpu utilization', cpu_utilization, target_cpu_utilization,
                                   nodes_number, required_nodes_number,
                                   replicas_number, required_replicas_number)
            target_memory_utilization = self._configuration.trigger.memory_utilization.max
            if memory_utilization is not None and memory_utilization > target_memory_utilization:
                return self._apply('nodes memory utilization', memory_utilization, target_memory_utilization,
                                   nodes_number, required_nodes_number,
                                   replicas_number, required_replicas_number)
        return required_nodes_number, required_replicas_number

    def _apply(self, trigger_name, current_utilization, target_utilization,
               nodes_number, required_nodes_number,
               replicas_number, required_replicas_number):
        required_nodes_number = _at_least(nodes_number, required_nodes_number,
                                          self._configuration.rules.on_threshold_trigger.extra_nodes)
        required_replicas_number = _at_least(replicas_number, required_replicas_number,
                                             self._configuration.rules.on_threshold_trigger.extra_replicas)
        logging.info('[TRIGGER] %s (%s%%) >= target (%s%%). '
                     + _scaling_msg('nodes', nodes_number, required_nodes_number)
                     + _scaling_msg('replicas', replicas_number, required_replicas_number),
                     trigger_name, current_utilization, target_utilization)
        return required_nodes_number, required_replicas_number
