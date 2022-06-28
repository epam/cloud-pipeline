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
import os
import pykube
import sys
from logging.handlers import TimedRotatingFileHandler

from autoscaler.cluster.kube import KubeProvider
from autoscaler.cluster.provider import NodeProvider, PodProvider, DeploymentProvider
from autoscaler.config import LocalFileAutoscalingConfiguration, AutoscalingConfiguration, \
    UnsupportedCloudProviderConfigurationError
from autoscaler.elastic import ElasticSearchClient
from autoscaler.exception import AbortScalingError
from autoscaler.instance.aws import AwsProvider
from autoscaler.instance.provider import InstanceProvider
from autoscaler.limit import ScaleIntervalLimit, AutoscalingLimit, TriggerDurationLimit, ScaleNodesLimit, \
    ScaleDeploymentsLimit
from autoscaler.model import NodesContainer, InstancesContainer, DeploymentsContainer
from autoscaler.rule import AutoscalingRule, LostTransientInstancesRule, LostTransientNodesRule
from autoscaler.scaler import NodeScaler, DeploymentScaler
from autoscaler.timer import AutoscalingTimer
from autoscaler.trigger import AutoscalingTrigger, ClusterNodesPerTargetReplicasCoefficientTrigger, \
    TargetReplicasPerTargetNodesCoefficientTrigger, \
    NodeDiskPressureTrigger, NodeMemoryPressureTrigger, NodePidPressureTrigger, NodeHeapsterElasticMetricTrigger


class AutoscalingDaemon:

    def __init__(self, autoscaler_supplier, configuration, timer, polling_timeout):
        """
        Autoscaling daemon.

        :param autoscaler_supplier: Autoscaler supplier.
        :param configuration: Autoscaling configuration.
        :param timer: Autoscaling timer.
        :param polling_timeout: Autoscaling polling timeout in seconds.
        """
        self._autoscaler_supplier = autoscaler_supplier
        self._configuration = configuration
        self._timer = timer
        self._polling_timeout = polling_timeout

    def start(self):
        while True:
            try:
                try:
                    logging.info('Scaling step has started.')
                    self._configuration.refresh()
                    autoscaler = self._autoscaler_supplier(configuration=self._configuration, timer=self._timer)
                    autoscaler.scale()
                    logging.info('Scaling step has finished.')
                    time.sleep(self._polling_timeout)
                except AbortScalingError:
                    logging.info('Scaling step has been aborted.')
                    time.sleep(self._polling_timeout)
                except Exception:
                    logging.exception('Scaling step has failed.')
                    time.sleep(self._polling_timeout)
            except KeyboardInterrupt:
                logging.warning('Interrupted.')
                break
            except BaseException:
                logging.exception('Scaling step has failed dramatically.')
                raise


class Autoscaler:

    def __init__(self, configuration, timer, node_scaler, deployment_scaler, node_provider, pod_provider,
                 deployment_provider, instance_provider, rules, triggers, limits):
        self._configuration: AutoscalingConfiguration = configuration
        self._timer: AutoscalingTimer = timer
        self._node_scaler: NodeScaler = node_scaler
        self._deployment_scaler: DeploymentScaler = deployment_scaler
        self._node_provider: NodeProvider = node_provider
        self._pod_provider: PodProvider = pod_provider
        self._deployment_provider: DeploymentProvider = deployment_provider
        self._instance_provider: InstanceProvider = instance_provider
        self._rules: [AutoscalingRule] = rules
        self._triggers: [AutoscalingTrigger] = triggers
        self._limits: [AutoscalingLimit] = limits

    def scale(self):
        logging.info('Checking prerequisites...')
        deployments_container = self._collect_deployments()
        nodes_container = self._collect_nodes(deployments_container)
        instances_container = self._collect_instances()
        deployments_container.log()
        nodes_container.log()
        instances_container.log()

        logging.info('Checking rules...')
        for rule in self._rules:
            rule.apply(deployments_container, nodes_container, instances_container)

        logging.info('Checking triggers...')
        # todo: Check target pod failures per hour coefficient (ex. target coefficient = 3 pod failures per hour)

        nodes_number = nodes_container.nodes_number
        replicas_number = deployments_container.replicas_number
        required_nodes_number = nodes_number
        required_replicas_number = replicas_number

        for trigger in self._triggers:
            required_nodes_number, required_replicas_number = trigger.apply(
                deployments_container, nodes_container, instances_container,
                nodes_number, required_nodes_number,
                replicas_number, required_replicas_number)

        logging.info('Checking limits...')
        for limit in self._limits:
            limit.apply(
                deployments_container, nodes_container, instances_container,
                nodes_number, required_nodes_number,
                replicas_number, required_replicas_number)

        logging.info('Checking requirements...')
        self._scale_nodes(nodes_container, nodes_number, required_nodes_number)
        self._scale_deployments(deployments_container, replicas_number, required_replicas_number)

    def _collect_deployments(self):
        logging.info('Collecting deployments...')
        deployments = list(self._deployment_provider.get_deployments(self._configuration.target.deployments))
        logging.info('Collecting deployment pods...')
        pods = list(self._pod_provider.get_pods_by_deployments(deployments))
        return DeploymentsContainer(deployments=deployments, pods=pods)

    def _collect_nodes(self, deployments_container):
        logging.info('Collecting nodes...')
        cluster_nodes_number = self._node_provider.get_all_nodes_count()
        nodes = list(self._node_provider.get_nodes_by_pods(deployments_container.pods))
        return NodesContainer(cluster_nodes_number=cluster_nodes_number, nodes=nodes)

    def _collect_instances(self):
        logging.info('Collecting instances...')
        instances = list(self._instance_provider.get_instances())
        return InstancesContainer(instances=instances)

    def _scale_nodes(self, nodes_container, nodes_number, required_nodes_number):
        if required_nodes_number > nodes_number:
            if nodes_number < self._configuration.limit.max_nodes_number:
                self._node_scaler.scale_up()
            else:
                logging.info('[LIMIT] current nodes count (%s) >= maximum nodes count (%s). '
                             'Scaling nodes ↑ is aborted.',
                             nodes_number, self._configuration.limit.max_nodes_number)
        elif required_nodes_number < nodes_number:
            if nodes_number > self._configuration.limit.min_nodes_number:
                self._node_scaler.scale_down(nodes_container.manageable_nodes)
            else:
                logging.info('[LIMIT] current nodes count (%s) <= minimum nodes count (%s). '
                             'Scaling nodes ↓ is aborted.',
                             nodes_number, self._configuration.limit.min_nodes_number)
        else:
            logging.info('Skipping nodes scaling...')

    def _scale_deployments(self, deployments_container, replicas_number, required_replicas_number):
        if required_replicas_number > replicas_number:
            if replicas_number < self._configuration.limit.max_replicas_number:
                self._deployment_scaler.scale_up(deployments_container.deployments)
            else:
                logging.info('[LIMIT] current replicas count (%s) >= maximum replicas count (%s). '
                             'Scaling replicas ↑ is aborted.',
                             replicas_number, self._configuration.limit.max_replicas_number)
        elif required_replicas_number < replicas_number:
            if replicas_number > self._configuration.limit.min_replicas_number:
                self._deployment_scaler.scale_down(deployments_container.deployments)
            else:
                logging.info('[LIMIT] current replicas count (%s) <= minimum replicas count (%s). '
                             'Scaling replicas ↓ is aborted.',
                             replicas_number, self._configuration.limit.min_replicas_number)
        else:
            logging.info('Skipping replicas scaling...')


if __name__ == '__main__':
    logging_format = os.getenv('CP_LOGGING_FORMAT', default='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')
    logging_level = os.getenv('CP_LOGGING_LEVEL', default='DEBUG')
    logging_file = os.getenv('CP_LOGGING_FILE', default='cp-deployment-autoscaler.log')
    logging_history = int(os.getenv('CP_LOGGING_HISTORY', default='10'))
    configuration_file = os.getenv('CP_DEPLOYMENT_AUTOSCALE_CONFIGURATION_FILE', default='config.json')
    polling_timeout = int(os.getenv('CP_DEPLOYMENT_AUTOSCALE_POLLING_TIMEOUT', default='10'))
    elastic_scheme = os.getenv('CP_HEAPSTER_ELK_INTERNAL_SCHEME', default='http')
    elastic_host = os.getenv('CP_HEAPSTER_ELK_INTERNAL_HOST', default='cp-heapster-elk.default.svc.cluster.local')
    elastic_port = os.getenv('CP_HEAPSTER_ELK_INTERNAL_PORT', default='30094')

    logging_formatter = logging.Formatter(logging_format)

    logging.getLogger().setLevel(logging_level)

    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(logging.INFO)
    console_handler.setFormatter(logging_formatter)
    logging.getLogger().addHandler(console_handler)

    file_handler = logging.handlers.TimedRotatingFileHandler(logging_file, when='D', interval=1,
                                                             backupCount=logging_history)
    file_handler.setLevel(logging.DEBUG)
    file_handler.setFormatter(logging_formatter)
    logging.getLogger().addHandler(file_handler)

    logging.info('Initiating...')


    def autoscaler_supplier(configuration, timer):
        if configuration.instance.cloud == 'aws':
            instance_provider = AwsProvider(configuration)
        else:
            logging.warning('Instance cloud provider %s configuration is not supported yet.',
                            configuration.instance.cloud)
            raise UnsupportedCloudProviderConfigurationError(configuration.instance.cloud)

        try:
            kube_client = pykube.HTTPClient(pykube.KubeConfig.from_service_account())
        except Exception:
            kube_config_path = os.path.join(os.path.expanduser('~'), '.kube', 'config')
            kube_client = pykube.HTTPClient(pykube.KubeConfig.from_file(kube_config_path))
        kube_client.session.verify = False
        node_provider = KubeProvider(kube=kube_client, configuration=configuration)
        pod_provider = node_provider
        deployment_provider = node_provider

        node_scaler = NodeScaler(configuration=configuration, timer=timer,
                                 node_provider=node_provider, instance_provider=instance_provider,
                                 pod_provider=pod_provider)

        deployment_scaler = DeploymentScaler(configuration=configuration, timer=timer,
                                             deployment_provider=deployment_provider)

        elastic_client = ElasticSearchClient(protocol=elastic_scheme, host=elastic_host, port=elastic_port)

        rules = [
            LostTransientInstancesRule(configuration=configuration, instance_provider=instance_provider),
            LostTransientNodesRule(configuration=configuration, node_provider=node_provider)
        ]

        triggers = [
            ClusterNodesPerTargetReplicasCoefficientTrigger(configuration),
            TargetReplicasPerTargetNodesCoefficientTrigger(configuration),
            NodeDiskPressureTrigger(configuration, node_provider),
            NodeMemoryPressureTrigger(configuration, node_provider),
            NodePidPressureTrigger(configuration, node_provider),
            NodeHeapsterElasticMetricTrigger(configuration, elastic_client)
        ]

        limits = [
            ScaleIntervalLimit(configuration, timer),
            TriggerDurationLimit(configuration, timer),
            ScaleNodesLimit(configuration, node_scaler),
            ScaleDeploymentsLimit(configuration, deployment_scaler)
        ]

        return Autoscaler(configuration=configuration, timer=timer,
                          node_scaler=node_scaler, deployment_scaler=deployment_scaler,
                          node_provider=node_provider, pod_provider=pod_provider,
                          deployment_provider=deployment_provider, instance_provider=instance_provider,
                          rules=rules, triggers=triggers, limits=limits)


    configuration = LocalFileAutoscalingConfiguration(configuration_file)
    timer = AutoscalingTimer()
    daemon = AutoscalingDaemon(autoscaler_supplier=autoscaler_supplier, configuration=configuration, timer=timer,
                               polling_timeout=polling_timeout)
    daemon.start()
    logging.info('Exiting...')
