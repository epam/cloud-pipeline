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
from abc import ABC, abstractmethod

from autoscaler.exception import AbortScalingError


class AutoscalingLimit(ABC):

    @abstractmethod
    def apply(self, deployments_container, nodes_container, instances_container,
              nodes_number, required_nodes_number,
              replicas_number, required_replicas_number):
        pass


class ScaleIntervalLimit(AutoscalingLimit):

    def __init__(self, configuration, timer):
        self._configuration = configuration
        self._timer = timer

    def apply(self, deployments_container, nodes_container, instances_container,
              nodes_number, required_nodes_number,
              replicas_number, required_replicas_number):
        if self._timer.last_scale_operation_time:
            current_scale_operation = time.time()
            scale_interval = current_scale_operation - self._timer.last_scale_operation_time
            if scale_interval > self._configuration.limit.min_scale_interval:
                logging.info('Checking limit scale interval (%.1f) > minimum scale interval (%.1f). '
                             'Scaling nodes/replicas is allowed.',
                             scale_interval, self._configuration.limit.min_scale_interval)
            else:
                logging.info('[LIMIT] scale interval (%.1f) < minimum scale interval (%.1f). '
                             'Scaling nodes/replicas ↑↓ is aborted.',
                             scale_interval, self._configuration.limit.min_scale_interval)
                raise AbortScalingError()


class TriggerDurationLimit(AutoscalingLimit):

    def __init__(self, configuration, timer):
        self._configuration = configuration
        self._timer = timer

    def apply(self, deployments_container, nodes_container, instances_container,
              nodes_number, required_nodes_number,
              replicas_number, required_replicas_number):
        current_trigger_time = time.time()
        try:
            if required_nodes_number > nodes_number or required_replicas_number > replicas_number:
                self._timer.scale_up_triggers_duration += current_trigger_time - self._timer.last_trigger_time
                self._timer.scale_down_triggers_duration = 0
                if self._timer.scale_up_triggers_duration > self._configuration.limit.min_triggers_duration:
                    logging.info('Checking limit scale ↑ triggers duration (%.1f) > minimum triggers duration (%.1f). '
                                 'Scaling nodes/replicas is allowed.',
                                 self._timer.scale_up_triggers_duration,
                                 self._configuration.limit.min_triggers_duration)
                    self._timer.scale_up_triggers_duration = 0
                else:
                    logging.info('[LIMIT] scale ↑ triggers duration (%.1f) < minimum triggers duration (%.1f). '
                                 'Scaling nodes/replicas ↑ is aborted.',
                                 self._timer.scale_up_triggers_duration,
                                 self._configuration.limit.min_triggers_duration)
                    raise AbortScalingError()
            elif required_nodes_number < nodes_number or required_replicas_number < replicas_number:
                self._timer.scale_up_triggers_duration = 0
                self._timer.scale_down_triggers_duration += current_trigger_time - self._timer.last_trigger_time
                if self._timer.scale_down_triggers_duration > self._configuration.limit.min_triggers_duration:
                    logging.info('Checking limit scale ↓ triggers duration (%.1f) > minimum triggers duration (%.1f). '
                                 'Scaling nodes/replicas is allowed.',
                                 self._timer.scale_down_triggers_duration,
                                 self._configuration.limit.min_triggers_duration)
                    self._timer.scale_down_triggers_duration = 0
                else:
                    logging.info('[LIMIT] scale ↓ triggers duration (%.1f) < minimum triggers duration (%.1f). '
                                 'Scaling nodes/replicas ↓ is aborted.',
                                 self._timer.scale_down_triggers_duration,
                                 self._configuration.limit.min_triggers_duration)
                    raise AbortScalingError()
            else:
                self._timer.scale_up_triggers_duration = 0
                self._timer.scale_down_triggers_duration = 0
        finally:
            self._timer.last_trigger_time = current_trigger_time


class ScaleNodesLimit(AutoscalingLimit):

    def __init__(self, configuration, node_scaler):
        self._configuration = configuration
        self._node_scaler = node_scaler

    def apply(self, deployments_container, nodes_container, instances_container,
              nodes_number, required_nodes_number,
              replicas_number, required_replicas_number):
        if nodes_number < self._configuration.limit.min_nodes_number:
            logging.info('[LIMIT] current nodes count (%s) < minimum nodes count (%s). '
                         'Scaling nodes ↑ from %s to %s...',
                         nodes_number, self._configuration.limit.min_nodes_number,
                         nodes_number, nodes_number + 1)
            self._node_scaler.scale_up()
            raise AbortScalingError()
        if nodes_number > self._configuration.limit.max_nodes_number:
            logging.info('[LIMIT] current nodes count (%s) > maximum nodes count (%s). '
                         'Scaling nodes ↓ from %s to %s...',
                         nodes_number, self._configuration.limit.max_nodes_number,
                         nodes_number, nodes_number - 1)
            self._node_scaler.scale_down(nodes_container.manageable_nodes)
            raise AbortScalingError()


class ScaleDeploymentsLimit(AutoscalingLimit):

    def __init__(self, configuration, deployment_scaler):
        self._configuration = configuration
        self._deployment_scaler = deployment_scaler

    def apply(self, deployments_container, nodes_container, instances_container,
              nodes_number, required_nodes_number,
              replicas_number, required_replicas_number):
        if replicas_number < self._configuration.limit.min_replicas_number:
            logging.info('[LIMIT] current replicas count (%s) < minimum replicas count (%s). '
                         'Scaling replicas ↑ from %s to %s...',
                         replicas_number, self._configuration.limit.min_replicas_number,
                         replicas_number, replicas_number + 1)
            self._deployment_scaler.scale_up(deployments_container.deployments)
            raise AbortScalingError()
        if replicas_number > self._configuration.limit.max_replicas_number:
            logging.info('[LIMIT] current replicas count (%s) > maximum replicas count (%s). '
                         'Scaling replicas ↓ from %s to %s...',
                         replicas_number, self._configuration.limit.max_replicas_number,
                         replicas_number, replicas_number - 1)
            self._deployment_scaler.scale_down(deployments_container.deployments)
            raise AbortScalingError()
