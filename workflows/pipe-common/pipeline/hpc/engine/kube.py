#  Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import functools
import os

import operator
import pykube

from pipeline.hpc.engine.gridengine import GridEngine, GridEngineDemandSelector, GridEngineJobValidator, \
    AllocationRuleParsingError, GridEngineType, GridEngineJob, GridEngineJobState, _perform_command, \
    GridEngineLaunchAdapter, GridEngineResourceParser
from pipeline.hpc.logger import Logger
from pipeline.hpc.resource import IntegralDemand, ResourceSupply


def get_kube_client():
    try:
        return pykube.HTTPClient(pykube.KubeConfig.from_service_account())
    except Exception:
        kube_config_path = os.path.join(os.path.expanduser('~'), '.kube', 'config')
        return pykube.HTTPClient(pykube.KubeConfig.from_file(kube_config_path))


class KubeGridEngine(GridEngine):

    def __init__(self, kube, resource_parser, owner):
        self._kube = kube
        self._resource_parser = resource_parser
        self._owner = owner
        self._job_state_to_codes = {
            GridEngineJobState.RUNNING: ['Running'],
            GridEngineJobState.PENDING: ['Pending'],
            GridEngineJobState.SUSPENDED: [],
            GridEngineJobState.ERROR: ['Failed'],
            GridEngineJobState.DELETED: [],
            GridEngineJobState.COMPLETED: ['Succeeded'],
            GridEngineJobState.UNKNOWN: ['Unknown']
        }

    def get_engine_type(self):
        return GridEngineType.KUBE

    def get_jobs(self):
        try:
            return list(self._get_jobs())
        except Exception:
            Logger.warn('Grid engine jobs listing has failed.')
            return []

    def _get_jobs(self):
        for pod in pykube.Pod.objects(self._kube):
            try:
                yield self._get_job(pod)
            except Exception:
                Logger.warn('Ignoring job {job_name} because its processing has failed...'
                            .format(job_name=pod.name),
                            trace=True)

    def _get_job(self, pod):
        job_root_id = pod.obj.get('metadata', {}).get('uid', '-')
        job_id = job_root_id
        job_name = pod.name
        job_user = self._owner
        job_state = GridEngineJobState.from_letter_code(pod.obj.get('status', {}).get('phase'),
                                                        self._job_state_to_codes)
        job_datetime = self._resource_parser.parse_date(pod.obj.get('metadata', {}).get('creationTimestamp')
                                                        or pod.obj.get('status', {}).get('startTime'))
        job_host = pod.obj.get('spec', {}).get('nodeName')
        job_hosts = [job_host] if job_host else []
        pod_demand = self._get_pod_demand(pod)
        return GridEngineJob(
            id=job_id,
            root_id=job_root_id,
            name=job_name,
            user=job_user,
            state=job_state,
            datetime=job_datetime,
            hosts=job_hosts,
            cpu=pod_demand.cpu,
            gpu=pod_demand.gpu,
            mem=pod_demand.mem
        )

    def disable_host(self, host):
        pykube.Node.objects(self._kube).get_by_name(host).cordon()

    def enable_host(self, host):
        pykube.Node.objects(self._kube).get_by_name(host).uncordon()

    def get_pe_allocation_rule(self, pe):
        raise AllocationRuleParsingError('Kube grid engine does not support parallel environments.')

    def delete_host(self, host, skip_on_failure=False):
        _perform_command(
            action=lambda: pykube.Node.objects(self._kube).get_by_name(host).delete(),
            msg='Remove host from GE.',
            error_msg='Removing host from GE has failed.',
            skip_on_failure=skip_on_failure
        )

    def get_host_supplies(self):
        for node in pykube.Node.objects(self._kube):
            node_taints = node.obj.get('spec', {}).get('taints', [])
            if any(node_taint.get('effect') == 'NoSchedule' for node_taint in node_taints):
                continue
            yield self._get_node_supply(node)

    def get_host_supply(self, host):
        node = pykube.Node.objects(self._kube).get_by_name(host)
        return self._get_node_supply(node)

    def _get_node_supply(self, node):
        return self._get_node_supply_total(node) - self._get_node_supply_used(node)

    def _get_node_supply_total(self, node):
        node_allocatable = node.obj.get('status', {}).get('allocatable', {})
        return ResourceSupply(cpu=self._resource_parser.parse_cpu(node_allocatable.get('cpu', '0')))

    def _get_node_supply_used(self, node):
        pod_demands = list(self._get_pod_demand(pod) for pod in self._get_pods(node))
        return functools.reduce(operator.add, pod_demands, IntegralDemand())

    def _get_pod_demand(self, pod):
        pod_demand = functools.reduce(operator.add, self._get_pod_demands(pod), IntegralDemand())
        if pod_demand.cpu < 1:
            return pod_demand + IntegralDemand(cpu=1)
        return pod_demand

    def _get_pod_demands(self, pod):
        containers = pod.obj.get('spec', {}).get('containers', [])
        for container in containers:
            container_requests = container.get('resources', {}).get('requests', {})
            container_cpu = 0
            container_cpu_raw = container_requests.get('cpu') or '0'
            try:
                container_cpu = self._resource_parser.parse_cpu(container_cpu_raw)
            except Exception:
                Logger.warn('Job {job_name} has invalid requirement: {name}={value}'
                            .format(job_name=pod.name, name='cpu', value=container_cpu_raw),
                            trace=True)
            container_mem = 0
            container_mem_raw = container_requests.get('memory') or '0'
            try:
                container_mem = self._resource_parser.parse_mem(container_mem_raw)
            except Exception:
                Logger.warn('Job {job_name} has invalid requirement: {name}={value}'
                            .format(job_name=pod.name, name='mem', value=container_mem_raw),
                            trace=True)
            yield IntegralDemand(cpu=container_cpu,
                                 mem=container_mem)

    def _get_pods(self, node):
        return pykube.Pod.objects(self._kube).filter(field_selector={'spec.nodeName': node.name})

    def is_valid(self, host):
        try:
            node = pykube.Node.objects(self._kube).get_by_name(host)
            node_conditions = node.obj.get('status', {}).get('conditions', [])
            for node_condition in node_conditions:
                node_condition_type = node_condition.get('type')
                node_condition_status = node_condition.get('status')
                if node_condition_type != 'Ready':
                    continue
                if node_condition_status == 'True':
                    return True
                Logger.warn('Execution host {host} is not ready which makes host invalid'
                            .format(host=host),
                            crucial=True)
                return False
        except Exception:
            Logger.warn('Execution host {host} validation has failed'.format(host=host), crucial=True, trace=True)
            return False

    def kill_jobs(self, jobs, force=False):
        for job in jobs:
            self._kill_job(job)

    def _kill_job(self, job):
        pykube.Pod.objects(self._kube).get_by_name(job.id).delete()


class KubeDefaultDemandSelector(GridEngineDemandSelector):

    def __init__(self, grid_engine):
        self.grid_engine = grid_engine

    def select(self, jobs):
        initial_supplies = list(self.grid_engine.get_host_supplies())
        for job in sorted(jobs, key=lambda job: job.root_id):
            if job.hosts:
                Logger.warn('Ignoring job #{job_id} {job_name} by {job_user} because '
                            'it is pending even though '
                            'it is associated with specific hosts: '
                            '{job_hosts}...'
                            .format(job_id=job.id, job_name=job.name, job_user=job.user,
                                    job_hosts=', '.join(job.hosts)))
                continue
            initial_demand = IntegralDemand(cpu=job.cpu, gpu=job.gpu, mem=job.mem, owner=job.user)
            if any(not (initial_demand - initial_supply) for initial_supply in initial_supplies):
                initial_supply = functools.reduce(operator.add, initial_supplies, ResourceSupply())
                Logger.warn('Ignoring job #{job_id} {job_name} by {job_user} because '
                            'it is pending even though '
                            'it requires resources which are available at the moment: '
                            '{job_resources}...'
                            .format(job_id=job.id, job_name=job.name, job_user=job.user,
                                    job_resources=self._as_resources_str(initial_demand,
                                                                         initial_supply)))
                continue

            yield initial_demand

    def _as_resources_str(self, demand, supply):
        return ', '.join('{demand}/{supply} {name}'
                         .format(name=key,
                                 demand=getattr(demand, key),
                                 supply=getattr(supply, key))
                         for key in ['cpu', 'gpu', 'mem'])


class KubeJobValidator(GridEngineJobValidator):

    def __init__(self, grid_engine, instance_max_supply, cluster_max_supply):
        self.grid_engine = grid_engine
        self.instance_max_supply = instance_max_supply
        self.cluster_max_supply = cluster_max_supply

    def validate(self, jobs):
        valid_jobs, invalid_jobs = [], []
        for job in jobs:
            job_demand = IntegralDemand(cpu=job.cpu, gpu=job.gpu, mem=job.mem)
            if job_demand > self.instance_max_supply:
                Logger.warn('Invalid job #{job_id} {job_name} by {job_user} requires resources '
                            'which cannot be satisfied by the biggest instance in cluster: '
                            '{job_cpu}/{available_cpu} cpu, '
                            '{job_gpu}/{available_gpu} gpu, '
                            '{job_mem}/{available_mem} mem.'
                            .format(job_id=job.id, job_name=job.name, job_user=job.user,
                                    job_cpu=job.cpu, available_cpu=self.instance_max_supply.cpu,
                                    job_gpu=job.gpu, available_gpu=self.instance_max_supply.gpu,
                                    job_mem=job.mem, available_mem=self.instance_max_supply.mem),
                            crucial=True)
                invalid_jobs.append(job)
                continue
            valid_jobs.append(job)
        return valid_jobs, invalid_jobs


class KubeLaunchAdapter(GridEngineLaunchAdapter):

    def __init__(self):
        pass

    def get_worker_init_task_name(self):
        return 'KubeWorkerSetup'

    def get_worker_launch_params(self):
        return {
            'CP_CAP_KUBE': 'false'
        }


class KubeResourceParser:

    def __init__(self):
        self._inner = GridEngineResourceParser(
            datatime_format='%Y-%m-%dT%H:%M:%SZ',
            cpu_unit='',
            cpu_modifiers={
                'm': 0.001,
                '': 1
            },
            mem_unit='Gi',
            mem_modifiers={
                'm': 0.001,
                '': 1,
                'k': 1000, 'M': 1000 ** 2, 'G': 1000 ** 3, 'T': 1000 ** 4, 'P': 1000 ** 5, 'E': 1000 ** 6,
                'Ki': 1024, 'Mi': 1024 ** 2, 'Gi': 1024 ** 3, 'Ti': 1024 ** 4, 'Pi': 1024 ** 5, 'Ei': 1024 ** 6,
            })

    def parse_date(self, timestamp):
        return self._inner.parse_date(timestamp)

    def parse_cpu(self, quantity):
        return self._inner.parse_cpu(quantity)

    def parse_mem(self, quantity):
        return self._inner.parse_mem(quantity)
