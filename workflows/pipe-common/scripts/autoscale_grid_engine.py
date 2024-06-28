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

import copy
import json
import logging
import multiprocessing
import os
import traceback

import sys
from datetime import timedelta

from pipeline.hpc.autoscaler import \
    GridEngineAutoscalingDaemon, GridEngineAutoscaler, \
    GridEngineScaleUpOrchestrator, GridEngineScaleUpHandler, \
    GridEngineScaleDownOrchestrator, GridEngineScaleDownHandler, \
    DoNothingAutoscalingDaemon, DoNothingScaleUpHandler, DoNothingScaleDownHandler
from pipeline.hpc.cloud import CloudProvider
from pipeline.hpc.cmd import CmdExecutor
from pipeline.hpc.engine.gridengine import GridEngineType, DoNothingGridEngineJobProcessor
from pipeline.hpc.engine.kube import KubeGridEngine, KubeJobValidator, KubeDefaultDemandSelector, KubeLaunchAdapter, \
    KubeResourceParser, get_kube_client
from pipeline.hpc.engine.sge import SunGridEngine, SunGridEngineDefaultDemandSelector, SunGridEngineJobValidator, \
    SunGridEngineHostWorkerValidatorHandler, SunGridEngineStateWorkerValidatorHandler, \
    SunGridEngineGlobalDemandSelector, SunGridEngineLaunchAdapter, SunGridEngineCustomRequestsPurgeJobProcessor
from pipeline.hpc.engine.slurm import SlurmGridEngine, SlurmDemandSelector, SlurmJobValidator, \
    SlurmLaunchAdapter
from pipeline.hpc.event import GridEngineEventManager
from pipeline.hpc.host import FileSystemHostStorage, ThreadSafeHostStorage
from pipeline.hpc.instance.avail import InstanceAvailabilityManager
from pipeline.hpc.instance.provider import DefaultInstanceProvider, \
    FamilyInstanceProvider, DescendingInstanceProvider, \
    SizeLimitingInstanceProvider, AvailableInstanceProvider
from pipeline.hpc.instance.select import CpuCapacityInstanceSelector, NaiveCpuCapacityInstanceSelector, \
    BackwardCompatibleInstanceSelector
from pipeline.hpc.logger import Logger
from pipeline.hpc.param import GridEngineParameters, ValidationError
from pipeline.hpc.pipe import CloudPipelineWorkerRecorder, CloudPipelineInstanceProvider, \
    CloudPipelineWorkerValidator, CloudPipelineWorkerValidatorHandler, \
    CloudPipelineWorkerTagsHandler
from pipeline.hpc.resource import ResourceSupply
from pipeline.hpc.utils import Clock, ScaleCommonUtils
from pipeline.hpc.valid import GracePeriodWorkerValidatorHandler
from pipeline.api.api import PipelineAPI
from pipeline.api.token import RefreshingToken
from pipeline.log.logger import RunLogger, TaskLogger, LevelLogger, LocalLogger, ResilientLogger
from pipeline.utils.path import mkdir


def fetch_instance_launch_params(api, launch_adapter, run_id, inheritable_explicit_param_names,
                                 instance_inheritable_param_prefixes):
    launch_params = {}
    launch_params.update(get_inherited_worker_launch_params(api, run_id, inheritable_explicit_param_names,
                                                            instance_inheritable_param_prefixes))
    launch_params.update(launch_adapter.get_worker_launch_params())
    launch_params.update({
        'CP_CAP_AUTOSCALE': 'false',
        'CP_CAP_AUTOSCALE_WORKERS': '0',
        'CP_DISABLE_RUN_ENDPOINTS': 'true',
        'cluster_role': 'worker',
        'cluster_role_type': 'additional'
    })
    return launch_params


def get_inherited_worker_launch_params(api, run_id, inheritable_explicit_param_names,
                                       instance_inheritable_param_prefixes):
    inheritable_system_params, inheritable_system_prefixes = get_inheritable_system_param_names(api)
    return dict(extract_params(inheritable_system_params + inheritable_explicit_param_names,
                               inheritable_system_prefixes + instance_inheritable_param_prefixes,
                               get_run_params(api, run_id)))


def get_inheritable_system_param_names(api):
    system_launch_params_string = resolve_preference(api, 'launch.system.parameters', default='[]')
    system_launch_params = json.loads(system_launch_params_string)
    params = []
    prefixes = []
    for system_launch_param in system_launch_params:
        param_name = system_launch_param.get('name')
        if not param_name:
            continue
        if not system_launch_param.get('passToWorkers', False):
            continue
        if system_launch_param.get('prefix', False):
            prefixes.append(param_name)
        else:
            params.append(param_name)
    return params, prefixes


def get_run_params(api, run_id):
    run = api.load_run_efficiently(run_id)
    return {param.get('name'): param.get('resolvedValue') for param in run.get('pipelineRunParameters', [])}


def extract_params(keys, prefixes, params):
    for key in keys:
        if not key:
            continue
        value = str(os.getenv(key, params.get(key, '')))
        if not value:
            continue
        yield key, value

    for prefix in prefixes:
        if not prefix:
            continue
        for param in os.environ:
            if param.startswith(prefix):
                value = str(os.getenv(param, params.get(param, '')))
                if value:
                    yield param, value


def load_default_hosts(default_hostsfile):
    if os.path.exists(default_hostsfile):
        with open(default_hostsfile) as hosts_file:
            hosts = []
            for line in hosts_file.readlines():
                hosts.append(line.strip().strip(FileSystemHostStorage._LINE_BREAKER))
            return hosts
    else:
        return []


def init_static_hosts(default_hostfile, static_host_storage, clock, active_timeout, static_hosts_enabled):
    try:
        if static_host_storage.load_hosts():
            Logger.info('Static hosts already initialized.')
            return
        Logger.info('Starting static hosts initialization.')
        if static_hosts_enabled:
            hosts = load_default_hosts(default_hostfile)
            for host in hosts:
                static_host_storage.add_host(host)
        else:
            master_host = os.getenv('HOSTNAME')
            static_host_storage.add_host(master_host)
            hosts = [master_host]
        # to prevent false positive run active status let's add outdated date to hosts:
        timeout = active_timeout * 2
        timestamp = clock.now() - timedelta(seconds=timeout)
        static_host_storage.update_hosts_activity(hosts, timestamp)
        Logger.info('Static hosts have been initialized.')
    except Exception:
        Logger.warn('Static hosts initialization has failed.')
        Logger.warn(traceback.format_exc())


def resolve_preference(api, name, default):
    try:
        return api.get_preference_value(name) or default
    except Exception:
        Logger.warn('Pipeline preference %s retrieving has failed. Using default value: %s.'
                    % (name, default))
        Logger.warn(traceback.format_exc())
        return default


def get_daemon():
    params = GridEngineParameters()

    grid_engine_type = params.autoscaling_advanced.grid_engine.get() \
        or (GridEngineType.KUBE if params.queue.kube_grid_engine.get()
            else GridEngineType.SLURM if params.queue.slurm_grid_engine.get()
            else GridEngineType.SGE)

    api_url = os.environ['API']

    cluster_owner = os.getenv('OWNER', 'root')
    cluster_hostfile = os.environ['DEFAULT_HOSTFILE']
    cluster_master_run_id = os.environ['RUN_ID']
    cluster_master_name = os.getenv('HOSTNAME', 'pipeline-' + str(cluster_master_run_id))
    cluster_work_dir = params.autoscaling_advanced.work_dir.get()

    queue_name = params.queue.queue_name.get()
    queue_name_short = (queue_name if not queue_name.endswith('.q') else queue_name[:-2])

    logging_dir = params.autoscaling.log_dir.get()
    logging_verbose = params.autoscaling.log_verbose.get()
    logging_level_run = params.autoscaling_advanced.logging_level_run.get()
    logging_level_file = params.autoscaling_advanced.logging_level_file.get()
    logging_level_console = params.autoscaling_advanced.logging_level_console.get()
    logging_format = params.autoscaling_advanced.logging_format.get()
    logging_task = (params.autoscaling_advanced.log_task.get()
                    or '{type}Autoscaling-{queue}'
                       .format(type=grid_engine_type, queue=queue_name_short))
    logging_file = os.path.join(logging_dir,
                                'autoscaler.{type}.{queue}.log'
                                .format(type=grid_engine_type.lower(), queue=queue_name))

    if logging_verbose:
        logging_level_run = 'DEBUG'

    # TODO: Git rid of CloudPipelineAPI usage in favor of PipelineAPI
    api = PipelineAPI(api_url=api_url, log_dir=logging_dir, token=RefreshingToken())

    mkdir(os.path.dirname(logging_file))

    logging_formatter = logging.Formatter(logging_format)

    logging_logger_root = logging.getLogger()
    logging_logger_root.setLevel(logging.WARNING)

    logging_logger = logging.getLogger(name=logging_task)
    logging_logger.setLevel(logging.DEBUG)

    if not logging_logger.handlers:
        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setLevel(logging_level_console)
        console_handler.setFormatter(logging_formatter)
        logging_logger.addHandler(console_handler)

        file_handler = logging.FileHandler(logging_file)
        file_handler.setLevel(logging_level_file)
        file_handler.setFormatter(logging_formatter)
        logging_logger.addHandler(file_handler)

    logger = RunLogger(api=api, run_id=cluster_master_run_id)
    logger = TaskLogger(task=logging_task, inner=logger)
    logger = LevelLogger(level=logging_level_run, inner=logger)
    logger = LocalLogger(logger=logging_logger, inner=logger)
    logger = ResilientLogger(inner=logger, fallback=LocalLogger(logger=logging_logger))

    # todo: Get rid of Logger usage in favor of logger
    Logger.inner = logger

    common_utils = ScaleCommonUtils()

    static_instance_cpus = int(os.getenv('CLOUD_PIPELINE_NODE_CORES', multiprocessing.cpu_count()))
    static_instance_number = int(os.getenv('node_count', 0))
    static_instance_type = params.autoscaling_advanced.static_instance_type.get()

    instance_cloud_provider = CloudProvider(params.autoscaling_advanced.instance_cloud_provider.get())
    instance_region_id = params.autoscaling_advanced.instance_region_id.get()
    instance_type = params.autoscaling.instance_type.get()
    instance_disk = params.autoscaling.instance_disk.get()
    instance_image = params.autoscaling.instance_image.get()
    instance_price_type = params.autoscaling.price_type.get()
    instance_cmd_template = params.autoscaling.cmd_template.get()
    instance_owner_param = params.autoscaling_advanced.instance_owner_param.get()
    instance_inheritable_params = (params.autoscaling_advanced.instance_inheritable_params.get() or '').split(',')
    instance_inheritable_param_prefixes = \
        (params.autoscaling_advanced.instance_inheritable_param_prefixes.get() or '').split(',')

    autoscale = params.autoscaling.autoscale.get()
    autoscale_instance_number = params.autoscaling.autoscaling_hosts_number.get()

    descending_autoscale = params.autoscaling.descending_autoscale.get()

    hybrid_autoscale = params.autoscaling.hybrid_autoscale.get()
    hybrid_instance_cores = params.autoscaling.hybrid_instance_cores.get()
    hybrid_instance_family = params.autoscaling.hybrid_instance_family.get() \
                             or common_utils.extract_family_from_instance_type(instance_cloud_provider, instance_type)

    polling_delay = params.autoscaling_advanced.polling_delay.get()

    scale_up_strategy = params.autoscaling.scale_up_strategy.get()
    scale_up_batch_size = params.autoscaling.scale_up_batch_size.get()
    scale_up_polling_delay = params.autoscaling.scale_up_polling_delay.get()
    scale_up_unavail_delay = params.autoscaling.scale_up_unavail_delay.get()
    scale_up_unavail_count_insufficient = params.autoscaling.scale_up_unavail_count_insufficient.get()
    scale_up_unavail_count_failure = params.autoscaling.scale_up_unavail_count_failure.get()

    scale_up_timeout = int(resolve_preference(api, 'ge.autoscaling.scale.up.timeout', default=30))
    scale_up_polling_timeout = int(resolve_preference(api, 'ge.autoscaling.scale.up.polling.timeout', default=900))

    scale_down_batch_size = params.autoscaling.scale_down_batch_size.get()
    scale_down_timeout = int(resolve_preference(api, 'ge.autoscaling.scale.down.timeout', default=30))
    scale_down_idle_timeout = params.autoscaling.scale_down_idle_timeout.get()
    scale_down_invalid_timeout = params.autoscaling.scale_down_invalid_timeout.get()

    active_timeout = params.autoscaling_advanced.active_timeout.get()

    dry_init = params.autoscaling_advanced.dry_init.get()
    dry_run = params.autoscaling_advanced.dry_run.get()

    event_ttl = params.autoscaling_advanced.event_ttl.get()

    custom_requirements = params.autoscaling_advanced.custom_requirements.get()
    custom_requirements_purge = params.autoscaling_advanced.custom_requirements_purge.get()

    queue_static = params.queue.queue_static.get()
    queue_default = params.queue.queue_default.get()
    queue_hostlist_name = params.queue.hostlist_name.get()
    queue_reserved_cpu = params.queue.hosts_free_cores.get()
    queue_master_cpu = params.queue.master_cores.get() or static_instance_cpus
    queue_gpu_resource_name = params.queue.gpu_resource_name.get()
    queue_mem_resource_name = params.queue.mem_resource_name.get()
    queue_exc_resource_name = params.queue.exc_resource_name.get()

    host_storage_file = os.path.join(cluster_work_dir, '.autoscaler.%s.storage' % queue_name)
    host_storage_static_file = os.path.join(cluster_work_dir, '.autoscaler.%s.static.storage' % queue_name)

    Logger.info('Initiating grid engine autoscaling...')

    if not autoscale:
        Logger.info('Using non autoscaling mode...')
        autoscale_instance_number = 0

    if dry_init:
        Logger.info('Using dry init mode...')

    if dry_run:
        Logger.info('Using dry run mode...')

    clock = Clock()
    # TODO: Git rid of CmdExecutor usage in favor of CloudPipelineExecutor implementation
    cmd_executor = CmdExecutor()

    reserved_supply = ResourceSupply(cpu=queue_reserved_cpu)

    event_manager = GridEngineEventManager(ttl=event_ttl, clock=clock)
    worker_recorder = CloudPipelineWorkerRecorder(api=api, event_manager=event_manager, clock=clock)
    availability_manager = InstanceAvailabilityManager(event_manager=event_manager, clock=clock,
                                                       unavail_delay=scale_up_unavail_delay,
                                                       unavail_count_insufficient=scale_up_unavail_count_insufficient,
                                                       unavail_count_failure=scale_up_unavail_count_failure)
    cloud_instance_provider = CloudPipelineInstanceProvider(api=api, region_id=instance_region_id,
                                                            price_type=instance_price_type)
    default_instance_provider = DefaultInstanceProvider(inner=cloud_instance_provider,
                                                        instance_type=instance_type)
    static_instance_provider = DefaultInstanceProvider(inner=cloud_instance_provider,
                                                       instance_type=static_instance_type)

    descending_instances = default_instance_provider.provide()
    if not descending_instances:
        raise ValidationError('Parameter {name} has invalid value {value}. '
                              'Such instance type is not available. '
                              'Please specify available instance type. \n\n'
                              '{name}\n{help}'
                              .format(name=params.autoscaling.instance_type.name, value=instance_type,
                                      help=params.autoscaling.instance_type.help))
    descending_instance = descending_instances.pop()
    descending_instance_cores = descending_instance.cpu if descending_instance else 0
    descending_instance_type = descending_instance.name if descending_instance else ''
    descending_instance_family = common_utils.extract_family_from_instance_type(instance_cloud_provider,
                                                                                descending_instance_type)

    if hybrid_autoscale and hybrid_instance_family:
        Logger.info('Using hybrid autoscaling of {} instances...'.format(hybrid_instance_family))
        instance_provider = FamilyInstanceProvider(inner=cloud_instance_provider,
                                                   instance_cloud_provider=instance_cloud_provider,
                                                   instance_family=hybrid_instance_family,
                                                   common_utils=common_utils)
        if not instance_provider.provide():
            raise ValidationError('Parameter {name} has invalid value {value}. '
                                  'Such instance type family is not available. '
                                  'Please specify available instance type family. \n\n'
                                  '{name}\n{help}'
                                  .format(name=params.autoscaling.hybrid_instance_family.name,
                                          value=hybrid_instance_family,
                                          help=params.autoscaling.hybrid_instance_family.help))
        if hybrid_instance_cores:
            Logger.info('Using instances with no more than {} cpus...'.format(hybrid_instance_cores))
            instance_provider = SizeLimitingInstanceProvider(inner=instance_provider,
                                                             max_instance_cores=hybrid_instance_cores)
        if not instance_provider.provide():
            raise ValidationError('Parameter {name} has invalid value {value}. '
                                  'There are no such instance types available. '
                                  'Please specify a different value. \n\n'
                                  '{name}\n'
                                  '{help}'
                                  .format(name=params.autoscaling.hybrid_instance_cores.name,
                                          value=hybrid_instance_cores,
                                          help=params.autoscaling.hybrid_instance_cores.help))
    elif descending_autoscale and descending_instance_family and descending_instance_cores:
        Logger.info('Using descending autoscaling of {} instances...'.format(descending_instance_type))
        instance_provider = FamilyInstanceProvider(inner=cloud_instance_provider,
                                                   instance_cloud_provider=instance_cloud_provider,
                                                   instance_family=descending_instance_family,
                                                   common_utils=common_utils)
        if not instance_provider.provide():
            raise ValidationError('Parameter {name} has invalid value {value}. '
                                  'Such instance type\'s family is not available. '
                                  'Please specify different instance type. \n\n'
                                  '{name}\n{help}'
                                  .format(name=params.autoscaling.instance_type.name,
                                          value=instance_type,
                                          help=params.autoscaling.instance_type.help))
        if descending_instance_cores:
            Logger.info('Using instances with no more than {} cpus...'.format(descending_instance_cores))
            instance_provider = SizeLimitingInstanceProvider(inner=instance_provider,
                                                             max_instance_cores=descending_instance_cores)
        if not instance_provider.provide():
            raise ValidationError('Parameter {name} has invalid value {value}. '
                                  'There are no such instance types available. '
                                  'Please specify different instance type. \n\n'
                                  '{name}\n{help}'
                                  .format(name=params.autoscaling.instance_type.name,
                                          value=instance_type,
                                          help=params.autoscaling.instance_type.help))
        instance_provider = DescendingInstanceProvider(inner=instance_provider)
    else:
        Logger.info('Using default autoscaling of {} instances...'.format(instance_type))
        instance_provider = default_instance_provider

    if scale_up_unavail_delay:
        Logger.info('Using only available instances...')
        instance_provider = AvailableInstanceProvider(inner=instance_provider,
                                                      availability_manager=availability_manager)

    if scale_up_strategy == 'cpu-capacity':
        Logger.info('Selecting instances using cpu capacity strategy...')
        instance_selector = CpuCapacityInstanceSelector(instance_provider=instance_provider,
                                                        reserved_supply=reserved_supply)
    elif scale_up_strategy == 'naive-cpu-capacity':
        Logger.info('Selecting instances using fractional cpu capacity strategy...')
        instance_selector = NaiveCpuCapacityInstanceSelector(instance_provider=instance_provider,
                                                             reserved_supply=reserved_supply)
    else:
        Logger.info('Selecting instances using default strategy...')
        instance_selector = BackwardCompatibleInstanceSelector(instance_provider=instance_provider,
                                                               reserved_supply=reserved_supply,
                                                               batch_size=scale_up_batch_size)

    instances = instance_provider.provide()
    if not instances:
        raise ValidationError('Grid engine autoscaler configuration is invalid. '
                              'There are no required instance types available. '
                              'Please use different configuration parameters.')

    static_instances = static_instance_provider.provide()
    if not static_instances:
        raise ValidationError('Parameter {name} has invalid value {value}. '
                              'Such instance type is not available. '
                              'Please specify available instance type. \n\n'
                              '{name}\n{help}'
                              .format(name=params.autoscaling_advanced.static_instance_type.name,
                                      value=static_instance_type,
                                      help=params.autoscaling_advanced.static_instance_type.help))
    static_instance = static_instances.pop()

    instance_supplies = [ResourceSupply.of(available_instance) - reserved_supply for available_instance in instances]
    biggest_instance_supply = sorted(instance_supplies, key=lambda supply: supply.cpu).pop()
    static_instance_supply = ResourceSupply.of(static_instance) - reserved_supply
    master_instance_supply = copy.deepcopy(static_instance_supply)
    master_instance_supply.cpu = queue_master_cpu - queue_reserved_cpu \
        if queue_master_cpu - queue_reserved_cpu > 0 \
        else queue_master_cpu
    cluster_supply = biggest_instance_supply * autoscale_instance_number
    if queue_static:
        cluster_supply += master_instance_supply + static_instance_supply * static_instance_number

    if grid_engine_type == GridEngineType.SLURM:
        grid_engine = SlurmGridEngine(cmd_executor=cmd_executor)
        job_preprocessor = DoNothingGridEngineJobProcessor()
        job_validator = SlurmJobValidator(grid_engine=grid_engine, instance_max_supply=biggest_instance_supply,
                                          cluster_max_supply=cluster_supply)
        demand_selector = SlurmDemandSelector(grid_engine=grid_engine)
        launch_adapter = SlurmLaunchAdapter()
    elif grid_engine_type == GridEngineType.KUBE:
        kube_client = get_kube_client()
        resource_parser = KubeResourceParser()
        grid_engine = KubeGridEngine(kube=kube_client, resource_parser=resource_parser, owner=cluster_owner)
        job_preprocessor = DoNothingGridEngineJobProcessor()
        job_validator = KubeJobValidator(grid_engine=grid_engine, instance_max_supply=biggest_instance_supply,
                                         cluster_max_supply=cluster_supply)
        demand_selector = KubeDefaultDemandSelector(grid_engine=grid_engine)
        launch_adapter = KubeLaunchAdapter()
    else:
        grid_engine = SunGridEngine(cmd_executor=cmd_executor, queue=queue_name, hostlist=queue_hostlist_name,
                                    queue_default=queue_default,
                                    gpu_resource_name=queue_gpu_resource_name,
                                    mem_resource_name=queue_mem_resource_name,
                                    exc_resource_name=queue_exc_resource_name)
        if custom_requirements_purge:
            job_preprocessor = SunGridEngineCustomRequestsPurgeJobProcessor(
                cmd_executor=cmd_executor,
                gpu_resource_name=queue_gpu_resource_name,
                mem_resource_name=queue_mem_resource_name,
                exc_resource_name=queue_exc_resource_name,
                dry_run=dry_run)
        else:
            job_preprocessor = DoNothingGridEngineJobProcessor()
        job_validator = SunGridEngineJobValidator(grid_engine=grid_engine,
                                                  instance_max_supply=biggest_instance_supply,
                                                  cluster_max_supply=cluster_supply)
        demand_selector = SunGridEngineDefaultDemandSelector(grid_engine=grid_engine)
        if custom_requirements:
            demand_selector = SunGridEngineGlobalDemandSelector(inner=demand_selector, grid_engine=grid_engine)
        launch_adapter = SunGridEngineLaunchAdapter(queue=queue_name, hostlist=queue_hostlist_name)

    host_storage = FileSystemHostStorage(cmd_executor=cmd_executor, storage_file=host_storage_file, clock=clock)
    host_storage = ThreadSafeHostStorage(host_storage)
    static_host_storage = FileSystemHostStorage(cmd_executor=cmd_executor, storage_file=host_storage_static_file,
                                                clock=clock)
    init_static_hosts(default_hostfile=cluster_hostfile, static_host_storage=static_host_storage, clock=clock,
                      active_timeout=active_timeout, static_hosts_enabled=queue_static and static_instance_number)

    if queue_static:
        Logger.info('Using static workers:\n{}\n{}'
                    .format('- {} {} ({} cpu, {} gpu, {} mem, {} exc)'
                            .format(cluster_master_name, static_instance.name,
                                    master_instance_supply.cpu,
                                    master_instance_supply.gpu,
                                    master_instance_supply.mem,
                                    master_instance_supply.exc),
                            '\n'.join('- {} {} ({} cpu, {} gpu, {} mem, {} exc)'
                                      .format(host, static_instance.name,
                                              static_instance_supply.cpu,
                                              static_instance_supply.gpu,
                                              static_instance_supply.mem,
                                              static_instance_supply.exc)
                                      for host in static_host_storage.load_hosts()
                                      if host != cluster_master_name))
                    .strip())
    Logger.info('Using autoscaling instance types:\n{}'
                .format('\n'.join('- {} ({} cpu, {} gpu, {} mem, {} exc)'
                                  .format(instance.name,
                                          instance_supply.cpu,
                                          instance_supply.gpu,
                                          instance_supply.mem,
                                          instance_supply.exc)
                                  for instance, instance_supply
                                  in zip(instances, instance_supplies))))

    instance_launch_params = fetch_instance_launch_params(api, launch_adapter, cluster_master_run_id,
                                                          instance_inheritable_params,
                                                          instance_inheritable_param_prefixes)

    worker_tags_handler = CloudPipelineWorkerTagsHandler(api=api, active_timeout=active_timeout,
                                                         active_tag=grid_engine_type + '_IN_USE',
                                                         host_storage=host_storage,
                                                         static_host_storage=static_host_storage, clock=clock,
                                                         common_utils=common_utils, dry_run=dry_run)
    scale_up_handler = GridEngineScaleUpHandler(cmd_executor=cmd_executor, api=api, grid_engine=grid_engine,
                                                launch_adapter=launch_adapter,
                                                host_storage=host_storage,
                                                parent_run_id=cluster_master_run_id,
                                                instance_disk=instance_disk, instance_image=instance_image,
                                                cmd_template=instance_cmd_template,
                                                price_type=instance_price_type, region_id=instance_region_id,
                                                queue=queue_name, hostlist=queue_hostlist_name,
                                                owner_param_name=instance_owner_param,
                                                polling_delay=scale_up_polling_delay,
                                                polling_timeout=scale_up_polling_timeout,
                                                instance_launch_params=instance_launch_params,
                                                clock=clock)
    if dry_run:
        scale_up_handler = DoNothingScaleUpHandler()
    scale_up_orchestrator = GridEngineScaleUpOrchestrator(scale_up_handler=scale_up_handler,
                                                          grid_engine=grid_engine,
                                                          host_storage=host_storage,
                                                          static_host_storage=static_host_storage,
                                                          worker_tags_handler=worker_tags_handler,
                                                          instance_selector=instance_selector,
                                                          worker_recorder=worker_recorder,
                                                          batch_size=scale_up_batch_size,
                                                          polling_delay=scale_up_polling_delay,
                                                          clock=clock)
    scale_down_handler = GridEngineScaleDownHandler(cmd_executor=cmd_executor, api=api, grid_engine=grid_engine,
                                                    common_utils=common_utils)
    if dry_run:
        scale_down_handler = DoNothingScaleDownHandler()
    scale_down_orchestrator = GridEngineScaleDownOrchestrator(scale_down_handler=scale_down_handler,
                                                              grid_engine=grid_engine,
                                                              host_storage=host_storage,
                                                              batch_size=scale_down_batch_size)
    worker_validator_handlers = [
        CloudPipelineWorkerValidatorHandler(api=api, common_utils=common_utils),
    ]

    if grid_engine_type == GridEngineType.SLURM:
        worker_validator_handlers.extend([
            GracePeriodWorkerValidatorHandler(inner=grid_engine, grace_period=scale_down_invalid_timeout, clock=clock)
        ])
    elif grid_engine_type == GridEngineType.KUBE:
        worker_validator_handlers.extend([
            GracePeriodWorkerValidatorHandler(inner=grid_engine, grace_period=scale_down_invalid_timeout, clock=clock)
        ])
    else:
        worker_validator_handlers.extend([
            SunGridEngineHostWorkerValidatorHandler(cmd_executor=cmd_executor),
            GracePeriodWorkerValidatorHandler(
                inner=SunGridEngineStateWorkerValidatorHandler(cmd_executor=cmd_executor, queue=queue_name),
                grace_period=scale_down_invalid_timeout,
                clock=clock)
        ])

    worker_validator = CloudPipelineWorkerValidator(cmd_executor=cmd_executor, api=api, host_storage=host_storage,
                                                    grid_engine=grid_engine, scale_down_handler=scale_down_handler,
                                                    handlers=worker_validator_handlers,
                                                    common_utils=common_utils, dry_run=dry_run)
    autoscaler = GridEngineAutoscaler(grid_engine=grid_engine,
                                      job_preprocessor=job_preprocessor,
                                      job_validator=job_validator,
                                      demand_selector=demand_selector,
                                      cmd_executor=cmd_executor,
                                      scale_up_orchestrator=scale_up_orchestrator,
                                      scale_down_orchestrator=scale_down_orchestrator,
                                      host_storage=host_storage, static_host_storage=static_host_storage,
                                      scale_up_timeout=scale_up_timeout,
                                      scale_down_timeout=scale_down_timeout,
                                      max_additional_hosts=autoscale_instance_number,
                                      idle_timeout=scale_down_idle_timeout, clock=clock)
    daemon = GridEngineAutoscalingDaemon(autoscaler=autoscaler, worker_validator=worker_validator,
                                         worker_tags_handler=worker_tags_handler, polling_timeout=polling_delay)
    if dry_init:
        daemon = DoNothingAutoscalingDaemon()
    return daemon


def autoscale_grid_engine_queue():
    try:
        daemon = get_daemon()
    except ValidationError as e:
        Logger.warn(str(e), crucial=True)
        exit(1)
    except Exception:
        Logger.warn('Grid engine autoscaling initialization has failed.', trace=True, crucial=True)
        exit(1)

    daemon.start()


if __name__ == '__main__':
    autoscale_grid_engine_queue()
