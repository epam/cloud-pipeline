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
from typing import NamedTuple

import logging

Persistence = Enum('Persistence', 'TRANSIENT, PERSISTENT')
Deployment = NamedTuple('Deployment', [('name', str),
                                       ('replicas', int),
                                       ('labels', dict)])
Pod = NamedTuple('Pod', [('name', str),
                         ('namespace', str),
                         ('node_name', str),
                         ('reserved', bool)])
Instance = NamedTuple('Instance', [('name', str),
                                   ('persistence', Persistence)])
Node = NamedTuple('Node',
                  [('name', str),
                   ('persistence', Persistence),
                   ('reserved', bool),
                   ('used', bool)])
Condition = Enum('Condition', 'MEMORY_PRESSURE, DISK_PRESSURE, PID_PRESSURE, READY')


class DeploymentsContainer:

    def __init__(self, deployments: [Deployment], pods: [Pod]):
        self._deployments: [Deployment] = deployments
        self._replicas_number: int = 0
        self._pods: [Pod] = pods
        self._pod_names: [str] = []
        self._reserved_pod_names: [str] = []
        self._unreserved_pod_names: [str] = []
        for deployment in self._deployments:
            self._replicas_number += deployment.replicas
        for pod in self._pods:
            self._pod_names.append(pod.name)
            if pod.reserved:
                self._reserved_pod_names.append(pod.name)
            else:
                self._unreserved_pod_names.append(pod.name)

    @property
    def deployments(self):
        return self._deployments

    @property
    def pods(self):
        return self._pods

    @property
    def pod_names(self):
        return self._pod_names

    @property
    def replicas_number(self):
        return self._replicas_number

    def log(self):
        logging.info("""Deployments summary:
%s target replicas
%s target pods
    %s reserved %s
    %s unreserved %s""",
                     self._replicas_number,
                     len(self._pods),
                     len(self._reserved_pod_names), self._reserved_pod_names,
                     len(self._unreserved_pod_names), self._unreserved_pod_names)


class NodesContainer:

    def __init__(self, cluster_nodes_number: int, nodes: [Node]):
        self._cluster_nodes_number: int = cluster_nodes_number

        self._target_nodes: [Node] = nodes

        self._non_target_nodes_number: int = 0

        self._transient_nodes: [Node] = []
        self._transient_used_node_names: [str] = []
        self._transient_unused_node_names: [str] = []
        self._transient_reserved_node_names: [str] = []

        self._static_nodes: [Node] = []
        self._static_used_node_names: [str] = []
        self._static_unused_node_names: [str] = []

        self._manageable_nodes: [Node] = []

        self._non_target_nodes_number = self._cluster_nodes_number - len(self._target_nodes)
        for node in self._target_nodes:
            if node.persistence == Persistence.TRANSIENT:
                self._transient_nodes.append(node)
                if node.used:
                    self._transient_used_node_names.append(node.name)
                else:
                    self._transient_unused_node_names.append(node.name)
                if node.reserved:
                    self._transient_reserved_node_names.append(node.name)
                else:
                    self._manageable_nodes.append(node)
            if node.persistence == Persistence.PERSISTENT:
                self._static_nodes.append(node)
                if node.used:
                    self._static_used_node_names.append(node.name)
                else:
                    self._static_unused_node_names.append(node.name)

    @property
    def target_nodes(self):
        return self._target_nodes

    @property
    def non_target_nodes_number(self):
        return self._non_target_nodes_number

    @property
    def nodes_number(self):
        return len(self._target_nodes)

    @property
    def target_node_names(self):
        return [node.name for node in self._target_nodes]

    @property
    def manageable_nodes(self):
        return self._manageable_nodes

    @property
    def transient_nodes(self):
        return self._transient_nodes

    def log(self):
        logging.info("""Nodes summary:
%s cluster nodes
    %s target
        %s transient
            %s used %s
            %s unused %s
            %s reserved %s
        %s static
            %s used %s
            %s unused %s
    %s non target""",
                     self._cluster_nodes_number,
                     len(self._target_nodes),
                     len(self._transient_nodes),
                     len(self._transient_used_node_names), self._transient_used_node_names,
                     len(self._transient_unused_node_names), self._transient_unused_node_names,
                     len(self._transient_reserved_node_names), self._transient_reserved_node_names,
                     len(self._static_nodes),
                     len(self._static_used_node_names), self._static_used_node_names,
                     len(self._static_unused_node_names), self._static_unused_node_names,
                     self._non_target_nodes_number)


class InstancesContainer:

    def __init__(self, instances: [Instance]):
        self._instances: [Instance] = instances

        self._transient_instances: [Instance] = []
        self._transient_instance_names: [str] = []

        self._persistent_instances: [Instance] = []
        self._persistent_instance_names: [str] = []

        for instance in self._instances:
            if instance.persistence == Persistence.TRANSIENT:
                self._transient_instances.append(instance)
                self._transient_instance_names.append(instance.name)
            if instance.persistence == Persistence.PERSISTENT:
                self._persistent_instances.append(instance)
                self._persistent_instance_names.append(instance.name)

    @property
    def transient_instances(self):
        return self._transient_instances

    @property
    def transient_instance_names(self):
        return [instance.name for instance in self._transient_instances]

    def log(self):
        logging.info("""Instances summary:
%s target instances
    %s transient %s
    %s static %s""",
                     len(self._instances),
                     len(self._transient_instance_names), self._transient_instance_names,
                     len(self._persistent_instance_names), self._persistent_instance_names)
