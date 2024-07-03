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

import collections
import json
import logging
from abc import abstractmethod, ABC


class MissingConfigurationParameterError(RuntimeError):
    pass


class UnsupportedCloudProviderConfigurationError(RuntimeError):
    pass


TargetConfiguration = collections.namedtuple('TargetConfiguration',
                                             'deployments, labels, transient_labels, tags, transient_tags, '
                                             'reserved_labels, forbidden_instances, forbidden_nodes')
UtilizationTriggerConfiguration = collections.namedtuple('UtilizationTriggerConfiguration',
                                                         'max, monitoring_period')
TriggerConfiguration = collections.namedtuple('TriggerConfiguration',
                                              'cluster_nodes_per_target_replicas, target_replicas_per_target_nodes, '
                                              'memory_pressured_nodes, disk_pressured_nodes, pid_pressured_nodes, '
                                              'cpu_utilization, memory_utilization')
OnLostInstancesStrategy = Enum('OnLostInstancesStrategy', 'SKIP, STOP')
OnLostNodesStrategy = Enum('OnLostNodesStrategy', 'SKIP, STOP')
ThresholdTriggerRuleConfiguration = NamedTuple('ThresholdTrigger', [('extra_replicas', int), ('extra_nodes', int)])
RulesConfiguration = NamedTuple('RulesConfiguration',
                                [('on_lost_instances', OnLostInstancesStrategy),
                                 ('on_lost_nodes', OnLostNodesStrategy),
                                 ('on_threshold_trigger', ThresholdTriggerRuleConfiguration)])
LimitConfiguration = collections.namedtuple('LimitConfiguration',
                                            'min_nodes_number, max_nodes_number, '
                                            'min_replicas_number, max_replicas_number, '
                                            'min_scale_interval, min_triggers_duration')
AwsInstanceConfiguration = collections.namedtuple('AwsInstanceConfiguration',
                                                  'cloud, region, image, type, disk, sshkey, subnet, name, '
                                                  'security_groups, role, init_script')
KubeNodeConfiguration = collections.namedtuple('KubeNodeConfiguration',
                                               'kube_token, kube_ip, kube_port, kube_dns_ip, aws_fs_url, '
                                               'http_proxy, https_proxy, no_proxy')
TimeoutConfiguration = collections.namedtuple('TimeoutConfiguration',
                                              'scale_up_node_timeout, scale_up_node_delay, '
                                              'scale_up_instance_timeout, scale_up_instance_delay, '
                                              'scale_down_node_timeout, scale_down_node_delay')
MiscConfiguration = collections.namedtuple('MiscConfiguration',
                                           'boto3_retry_count')


class RefreshableConfiguration(ABC):

    @abstractmethod
    def refresh(self):
        pass


class AutoscalingConfiguration(ABC):

    @property
    @abstractmethod
    def target(self) -> TargetConfiguration:
        pass

    @property
    @abstractmethod
    def trigger(self) -> TriggerConfiguration:
        pass

    @property
    @abstractmethod
    def rules(self) -> RulesConfiguration:
        pass

    @property
    @abstractmethod
    def limit(self) -> LimitConfiguration:
        pass

    @property
    @abstractmethod
    def instance(self) -> AwsInstanceConfiguration:
        pass

    @property
    @abstractmethod
    def node(self) -> KubeNodeConfiguration:
        pass

    @property
    @abstractmethod
    def timeout(self) -> TimeoutConfiguration:
        pass

    @property
    @abstractmethod
    def misc(self) -> MiscConfiguration:
        pass


class LocalFileAutoscalingConfiguration(AutoscalingConfiguration, RefreshableConfiguration):

    def __init__(self, configuration_path):
        super(LocalFileAutoscalingConfiguration).__init__()
        self._configuration_path = configuration_path
        self._target = None
        self._trigger = None
        self._rules = None
        self._limit = None
        self._instance = None
        self._node = None
        self._timeout = None
        self._misc = None

    @property
    def target(self):
        return self._target

    @property
    def trigger(self):
        return self._trigger

    @property
    def rules(self):
        return self._rules

    @property
    def limit(self):
        return self._limit

    @property
    def instance(self):
        return self._instance

    @property
    def node(self):
        return self._node

    @property
    def timeout(self):
        return self._timeout

    @property
    def misc(self):
        return self._misc

    def refresh(self):
        with open(self._configuration_path, 'r') as configuration_file:
            configuration = json.load(configuration_file)
        self._target = TargetConfiguration(
            deployments=self._get_list(configuration, 'target.deployments'),
            labels=self._get_dictionary(configuration, 'target.labels'),
            transient_labels=self._get_dictionary(configuration, 'target.transient_labels'),
            tags=self._get_dictionary(configuration, 'target.tags'),
            transient_tags=self._get_dictionary(configuration, 'target.transient_tags'),
            reserved_labels=self._get_list(configuration, 'target.reserved_labels'),
            forbidden_instances=self._get_list(configuration, 'target.forbidden_instances'),
            forbidden_nodes=self._get_list(configuration, 'target.forbidden_nodes'))
        self._trigger = TriggerConfiguration(
            cluster_nodes_per_target_replicas=self._get_number(configuration, 'trigger.cluster_nodes_per_target_replicas', 100),
            target_replicas_per_target_nodes=self._get_number(configuration, 'trigger.target_replicas_per_target_nodes', 1),
            memory_pressured_nodes=self._get_number(configuration, 'trigger.memory_pressured_nodes', 0),
            disk_pressured_nodes=self._get_number(configuration, 'trigger.disk_pressured_nodes', 0),
            pid_pressured_nodes=self._get_number(configuration, 'trigger.pid_pressured_nodes', 0),
            cpu_utilization=UtilizationTriggerConfiguration(
                max=self._get_number(configuration, 'trigger.cpu_utilization.max', 90),
                monitoring_period=self._get_number(configuration, 'trigger.cpu_utilization.monitoring_period', 600)),
            memory_utilization=UtilizationTriggerConfiguration(
                max=self._get_number(configuration, 'trigger.memory_utilization.max', 90),
                monitoring_period=self._get_number(configuration, 'trigger.memory_utilization.monitoring_period', 600)))
        self._rules = RulesConfiguration(
            on_lost_instances=OnLostInstancesStrategy[self._get_string(configuration, 'rules.on_lost_instances',
                                                                       OnLostInstancesStrategy.SKIP.name)],
            on_lost_nodes=OnLostNodesStrategy[self._get_string(configuration, 'rules.on_lost_nodes',
                                                               OnLostInstancesStrategy.SKIP.name)],
            on_threshold_trigger=ThresholdTriggerRuleConfiguration(
                extra_replicas=self._get_number(configuration, 'rules.on_threshold_trigger.extra_replicas', 1),
                extra_nodes=self._get_number(configuration, 'rules.on_threshold_trigger.extra_nodes', 1)))
        self._limit = LimitConfiguration(
            min_nodes_number=self._get_number(configuration, 'limit.min_nodes_number', 1),
            max_nodes_number=self._get_number(configuration, 'limit.max_nodes_number', 10),
            min_replicas_number=self._get_number(configuration, 'limit.min_replicas_number', 1),
            max_replicas_number=self._get_number(configuration, 'limit.max_replicas_number', 10),
            min_scale_interval=self._get_number(configuration, 'limit.min_scale_interval', 5 * 60),
            min_triggers_duration=self._get_number(configuration, 'limit.min_triggers_duration', 1 * 60))
        cloud = self._get_string(configuration, 'instance.instance_cloud')
        if cloud == 'aws':
            self._instance = AwsInstanceConfiguration(
                cloud=cloud,
                region=self._get_string(configuration, 'instance.instance_region'),
                image=self._get_string(configuration, 'instance.instance_image'),
                type=self._get_string(configuration, 'instance.instance_type'),
                disk=self._get_number(configuration, 'instance.instance_disk'),
                sshkey=self._get_string(configuration, 'instance.instance_sshkey'),
                subnet=self._get_string(configuration, 'instance.instance_subnet'),
                name=self._get_string(configuration, 'instance.instance_name'),
                security_groups=self._get_list(configuration, 'instance.instance_security_groups'),
                role=self._get_string(configuration, 'instance.instance_role'),
                init_script=self._get_string(configuration, 'instance.instance_init_script'))
        else:
            logging.warning('Instance cloud provider %s configuration is not supported yet.', cloud)
            raise UnsupportedCloudProviderConfigurationError(cloud)
        self._node = KubeNodeConfiguration(
            kube_token=self._get_string(configuration, 'node.kube_token'),
            kube_ip=self._get_string(configuration, 'node.kube_ip'),
            kube_port=self._get_string(configuration, 'node.kube_port'),
            kube_dns_ip=self._get_string(configuration, 'node.kube_dns_ip'),
            aws_fs_url=self._get_string(configuration, 'node.aws_fs_url'),
            http_proxy=self._get_string(configuration, 'node.http_proxy', ''),
            https_proxy=self._get_string(configuration, 'node.https_proxy', ''),
            no_proxy=self._get_string(configuration, 'node.no_proxy', ''))
        self._timeout = TimeoutConfiguration(
            scale_up_node_timeout=self._get_number(configuration, 'timeout.scale_up_node_timeout', 15 * 60),
            scale_up_node_delay=self._get_number(configuration, 'timeout.scale_up_node_delay', 10),
            scale_up_instance_timeout=self._get_number(configuration, 'timeout.scale_up_instance_timeout', 60),
            scale_up_instance_delay=self._get_number(configuration, 'timeout.scale_up_instance_delay', 10),
            scale_down_node_timeout=self._get_number(configuration, 'timeout.scale_down_node_timeout', 2 * 60),
            scale_down_node_delay=self._get_number(configuration, 'timeout.scale_down_node_delay', 10))
        self._misc = MiscConfiguration(
            boto3_retry_count=self._get_number(configuration, 'misc.boto3_retry_count', 10))

    def _get_list(self, configuration, parameter_path) -> list:
        parameter = self._get_parameter(configuration, parameter_path)
        if not parameter \
                or not isinstance(parameter, list) \
                or any(not key for key in parameter):
            raise MissingConfigurationParameterError(parameter_path)
        return parameter

    def _get_dictionary(self, configuration, parameter_path) -> dict:
        parameter = self._get_parameter(configuration, parameter_path)
        if not parameter \
                or not isinstance(parameter, dict) \
                or any(not key or not value for key, value in parameter.items()):
            raise MissingConfigurationParameterError(parameter_path)
        return parameter

    def _get_string(self, configuration, parameter_path, default=None) -> str:
        parameter = self._get_parameter(configuration, parameter_path)
        if parameter is None and default is None:
            raise MissingConfigurationParameterError(parameter_path)
        return parameter or default

    def _get_number(self, configuration, parameter_path, default=None) -> int:
        parameter = self._get_parameter(configuration, parameter_path)
        if parameter is None and default is None:
            raise MissingConfigurationParameterError(parameter_path)
        return parameter or default

    def _get_parameter(self, configuration, parameter_path) -> object:
        parameter = configuration
        for path_part in parameter_path.split('.'):
            parameter = parameter.get(path_part, {})
        return parameter
