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

from typing import Iterator, Optional

from abc import ABC, abstractmethod

from autoscaler.model import Node, Condition, Pod, Deployment


class NodeProvider(ABC):

    @abstractmethod
    def get_node(self, name: str) -> Optional[Node]:
        pass

    @abstractmethod
    def get_nodes_by_pods(self, pods: [Pod]) -> Iterator[Node]:
        pass

    @abstractmethod
    def get_node_conditions(self, node: Node) -> Iterator[Condition]:
        pass

    @abstractmethod
    def get_all_nodes_count(self) -> int:
        pass

    @abstractmethod
    def drain_node(self, node: Node):
        pass

    @abstractmethod
    def delete_node(self, node: Node):
        pass


class PodProvider(ABC):

    @abstractmethod
    def get_pods_by_nodes(self, nodes: [Node]) -> Iterator[Pod]:
        pass

    @abstractmethod
    def get_pods_by_deployments(self, deployments: [Deployment]) -> Iterator[Pod]:
        pass

    @abstractmethod
    def get_pod_conditions(self, pod: Pod) -> Iterator[Condition]:
        pass


class DeploymentProvider(ABC):

    @abstractmethod
    def get_deployments(self, names: [str]) -> Iterator[Deployment]:
        pass

    @abstractmethod
    def scale_deployment(self, deployment: Deployment, replicas: int):
        pass
