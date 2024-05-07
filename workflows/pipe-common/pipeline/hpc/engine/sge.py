import functools
from xml.etree import ElementTree

import math
import operator
from datetime import datetime

from pipeline.hpc.cmd import ExecutionError
from pipeline.hpc.engine.gridengine import GridEngine, GridEngineJobState, GridEngineJob, AllocationRule, \
    GridEngineType, _perform_command, GridEngineDemandSelector, GridEngineJobValidator, GridEngineLaunchAdapter, \
    GridEngineJobProcessor
from pipeline.hpc.logger import Logger
from pipeline.hpc.resource import IntegralDemand, ResourceSupply, FractionalDemand, CustomResourceSupply, \
    CustomResourceDemand
from pipeline.hpc.valid import WorkerValidatorHandler


class SunGridEngine(GridEngine):
    _DELETE_HOST = 'qconf -de %s'
    _SHOW_PE_ALLOCATION_RULE = 'qconf -sp %s | grep "^allocation_rule" | awk \'{print $2}\''
    _REMOVE_HOST_FROM_HOST_GROUP = 'qconf -dattr hostgroup hostlist %s %s'
    _REMOVE_HOST_FROM_QUEUE_SETTINGS = 'qconf -purge queue slots %s@%s'
    _SHUTDOWN_HOST_EXECUTION_DAEMON = 'qconf -ke %s'
    _REMOVE_HOST_FROM_ADMINISTRATIVE_HOSTS = 'qconf -dh %s'
    _QSTAT = 'qstat -u "*" -r -f -xml'
    _QHOST_RESOURCES = 'qhost -q -F -xml'
    _QHOST_GLOBAL_RESOURCES = 'qhost -h "*" -F -xml'
    _QSTAT_DATETIME_FORMAT = '%Y-%m-%dT%H:%M:%S'
    _QMOD_DISABLE = 'qmod -d %s@%s'
    _QMOD_ENABLE = 'qmod -e %s@%s'
    _SHOW_EXECUTION_HOST = 'qconf -se %s'
    _KILL_JOBS = 'qdel %s'
    _FORCE_KILL_JOBS = 'qdel -f %s'

    def __init__(self, cmd_executor, queue, hostlist, queue_default,
                 gpu_resource_name, mem_resource_name, exc_resource_name):
        self.cmd_executor = cmd_executor
        self.queue = queue
        self.hostlist = hostlist
        self.queue_default = queue_default
        self.tmp_queue_name_attribute = 'tmp_queue_name'
        self.gpu_resource_name = gpu_resource_name
        self.mem_resource_name = mem_resource_name
        self.exc_resource_name = exc_resource_name
        self.job_state_to_codes = {
            GridEngineJobState.RUNNING: ['r', 't', 'Rr', 'Rt'],
            GridEngineJobState.PENDING: ['qw', 'qw', 'hqw', 'hqw', 'hRwq', 'hRwq', 'hRwq', 'qw', 'qw'],
            GridEngineJobState.SUSPENDED: ['s', 'ts', 'S', 'tS', 'T', 'tT', 'Rs', 'Rts', 'RS', 'RtS', 'RT', 'RtT'],
            GridEngineJobState.ERROR: ['Eqw', 'Ehqw', 'EhRqw'],
            GridEngineJobState.DELETED: ['dr', 'dt', 'dRr', 'dRt', 'ds', 'dS', 'dT', 'dRs', 'dRS', 'dRT'],
            GridEngineJobState.COMPLETED: [],
            GridEngineJobState.UNKNOWN: []
        }

    def get_engine_type(self):
        return GridEngineType.SGE

    def get_jobs(self):
        try:
            output = self.cmd_executor.execute(SunGridEngine._QSTAT)
        except ExecutionError:
            Logger.warn('Grid engine jobs listing has failed.')
            return []
        jobs = {}
        root = ElementTree.fromstring(output)
        running_jobs = []
        queue_info = root.find('queue_info')
        for queue_list in queue_info.findall('Queue-List'):
            queue_name = queue_list.findtext('name')
            queue_running_jobs = queue_list.findall('job_list')
            for job_list in queue_running_jobs:
                job_queue_name = ElementTree.SubElement(job_list, self.tmp_queue_name_attribute)
                job_queue_name.text = queue_name
            running_jobs.extend(queue_running_jobs)
        job_info = root.find('job_info')
        pending_jobs = job_info.findall('job_list')
        for job_list in running_jobs + pending_jobs:
            job_requested_queue = job_list.findtext('hard_req_queue')
            job_actual_queue, job_host = self._parse_queue_and_host(job_list.findtext(self.tmp_queue_name_attribute))
            if job_requested_queue and job_requested_queue != self.queue \
                    or job_actual_queue and job_actual_queue != self.queue:
                # filter out a job with actual/requested queue specified
                # if a configured queue is different from the job's one
                continue
            if not job_requested_queue and not job_actual_queue and not self.queue_default:
                # filter out a job without actual/requested queue specified
                # if a configured queue is not a default queue
                continue
            root_job_id = job_list.findtext('JB_job_number')
            job_tasks = self._parse_array(job_list.findtext('tasks'))
            job_ids = ['{}.{}'.format(root_job_id, job_task) for job_task in job_tasks] or [root_job_id]
            job_name = job_list.findtext('JB_name')
            job_user = job_list.findtext('JB_owner')
            job_state = GridEngineJobState.from_letter_code(job_list.findtext('state'), self.job_state_to_codes)
            job_datetime = self._parse_date(
                job_list.findtext('JAT_start_time') or job_list.findtext('JB_submission_time'))
            job_hosts = [job_host] if job_host else []
            requested_pe = job_list.find('requested_pe')
            job_pe = requested_pe.get('name') if requested_pe is not None else 'local'
            job_cpu = int(requested_pe.text if requested_pe is not None else '1')
            job_gpu = 0
            job_mem = 0
            job_exc = 0
            job_requests = {}
            hard_requests = job_list.findall('hard_request')
            for request in hard_requests:
                request_name = request.get('name', '').strip()
                request_value = request.text or ''
                if not request_name or not request_value:
                    Logger.warn('Job #{job_id} by {job_user} has partial requirement: {name}={value}'
                                .format(job_id=root_job_id, job_user=job_user,
                                        name=request_name or '?', value=request_value or '?'))
                    continue
                if request_name == self.gpu_resource_name:
                    try:
                        job_gpu = self._parse_int(request_value)
                    except ValueError:
                        Logger.warn('Job #{job_id} by {job_user} has invalid requirement: {name}={value}'
                                    .format(job_id=root_job_id, job_user=job_user,
                                            name='gpu', value=request_value),
                                    trace=True)
                elif request_name == self.mem_resource_name:
                    try:
                        job_mem = self._parse_mem(request_value)
                    except Exception:
                        Logger.warn('Job #{job_id} by {job_user} has invalid requirement: {name}={value}'
                                    .format(job_id=root_job_id, job_user=job_user,
                                            name='mem', value=request_value),
                                    trace=True)
                elif request_name == self.exc_resource_name:
                    try:
                        job_exc = int(self._parse_bool(request_value))
                    except Exception:
                        Logger.warn('Job #{job_id} by {job_user} has invalid requirement: {name}={value}'
                                    .format(job_id=root_job_id, job_user=job_user,
                                            name='exc', value=request_value),
                                    trace=True)
                else:
                    job_requests[request_name] = request_value
            for job_id in job_ids:
                if job_id in jobs:
                    job = jobs[job_id]
                    if job_host:
                        job.hosts.append(job_host)
                else:
                    jobs[job_id] = GridEngineJob(
                        id=job_id,
                        root_id=root_job_id,
                        name=job_name,
                        user=job_user,
                        state=job_state,
                        datetime=job_datetime,
                        hosts=job_hosts,
                        cpu=job_cpu,
                        gpu=job_gpu,
                        mem=job_mem,
                        exc=job_exc,
                        requests=job_requests,
                        pe=job_pe
                    )
        return jobs.values()

    def _parse_int(self, value):
        return int(float(value))

    def _parse_bool(self, bool_request):
        if not bool_request:
            return False
        if bool_request.strip().lower() in ['true', 'yes', 'on']:
            return True
        if bool_request.strip().lower() in ['false', 'no', 'off']:
            return False
        raise ValueError()

    def _parse_date(self, date):
        return datetime.strptime(date, SunGridEngine._QSTAT_DATETIME_FORMAT)

    def _parse_queue_and_host(self, queue_and_host):
        return queue_and_host.split('@')[:2] if queue_and_host else (None, None)

    def _parse_array(self, array_jobs):
        result = []
        if not array_jobs:
            return result
        for interval in array_jobs.split(","):
            if ':' in interval:
                array_borders, _ = interval.split(':')
                start, stop = array_borders.split('-')
                result += list(range(int(start), int(stop) + 1))
            else:
                result += [int(interval)]
        return result

    def _parse_mem(self, mem_request):
        if not mem_request:
            return 0
        modifiers = {
            'k': 1000, 'm': 1000 ** 2, 'g': 1000 ** 3,
            'K': 1024, 'M': 1024 ** 2, 'G': 1024 ** 3
        }
        if mem_request[-1] in modifiers:
            number = self._parse_int(mem_request[:-1])
            modifier = modifiers[mem_request[-1]]
        else:
            number = self._parse_int(mem_request)
            modifier = 1
        size_in_bytes = number * modifier
        size_in_gibibytes = int(math.ceil(size_in_bytes / modifiers['G']))
        return size_in_gibibytes

    def disable_host(self, host):
        self.cmd_executor.execute(SunGridEngine._QMOD_DISABLE % (self.queue, host))

    def enable_host(self, host):
        self.cmd_executor.execute(SunGridEngine._QMOD_ENABLE % (self.queue, host))

    def get_pe_allocation_rule(self, pe):
        exec_result = self.cmd_executor.execute(SunGridEngine._SHOW_PE_ALLOCATION_RULE % pe)
        return AllocationRule(exec_result.strip()) if exec_result else AllocationRule.pe_slots()

    def delete_host(self, host, skip_on_failure=False):
        self._shutdown_execution_host(host, skip_on_failure=skip_on_failure)
        self._remove_host_from_queue_settings(host, self.queue, skip_on_failure=skip_on_failure)
        self._remove_host_from_host_group(host, self.hostlist, skip_on_failure=skip_on_failure)
        self._remove_host_from_administrative_hosts(host, skip_on_failure=skip_on_failure)
        self._remove_host_from_grid_engine(host, skip_on_failure=skip_on_failure)

    def get_global_supplies(self):
        yield CustomResourceSupply(values=dict(self._get_global_resources()))

    def _get_global_resources(self):
        output = self.cmd_executor.execute(SunGridEngine._QHOST_GLOBAL_RESOURCES)
        root = ElementTree.fromstring(output)
        for host in root.findall('host'):
            for resource in host.findall('resourcevalue'):
                resource_name = resource.get('name', '').strip()
                resource_value = resource.text or ''
                if not resource_name or not resource_value:
                    Logger.warn('Global has partial resource: {name}={value}'
                                .format(name=resource_name or '?', value=resource_value or '?'))
                    continue
                yield resource_name, resource_value

    def get_host_supplies(self):
        output = self.cmd_executor.execute(SunGridEngine._QHOST_RESOURCES)
        root = ElementTree.fromstring(output)
        for host in root.findall('host'):
            host_name = host.get('name', '').strip()
            host_gpu = 0
            host_mem = 0
            host_exc = 0
            for resource in host.findall('resourcevalue'):
                resource_name = resource.get('name', '').strip()
                resource_value = resource.text or ''
                if not resource_name or not resource_value:
                    Logger.warn('Host {host_name} has partial resource: {name}={value}'
                                .format(host_name=host_name, name=resource_name or '?', value=resource_value or '?'))
                    continue
                if resource_name == self.gpu_resource_name:
                    try:
                        host_gpu = self._parse_int(resource_value)
                    except ValueError:
                        Logger.warn('Host {host_name} has invalid resource: {name}={value}'
                                    .format(host_name=host_name, name='gpu', value=resource_value),
                                    trace=True)
                elif resource_name == self.mem_resource_name:
                    try:
                        host_mem = self._parse_mem(resource_value)
                    except Exception:
                        Logger.warn('Host {host_name} has invalid resource: {name}={value}'
                                    .format(host_name=host_name, name='mem', value=resource_value),
                                    trace=True)
                elif resource_name == self.exc_resource_name:
                    try:
                        host_exc = self._parse_int(resource_value)
                    except Exception:
                        Logger.warn('Host {host_name} has invalid resource: {name}={value}'
                                    .format(host_name=host_name, name='exc', value=resource_value),
                                    trace=True)
            for queue in host.findall('queue[@name=\'%s\']' % self.queue):
                host_slots = int(queue.find('queuevalue[@name=\'slots\']').text or '0')
                host_used = int(queue.find('queuevalue[@name=\'slots_used\']').text or '0')
                host_resv = int(queue.find('queuevalue[@name=\'slots_resv\']').text or '0')
                yield (ResourceSupply(cpu=host_slots, gpu=host_gpu, mem=host_mem, exc=host_exc)
                       - ResourceSupply(cpu=host_used + host_resv))

    def get_host_supply(self, host):
        for line in self.cmd_executor.execute_to_lines(SunGridEngine._SHOW_EXECUTION_HOST % host):
            if "processors" in line:
                return ResourceSupply(cpu=int(line.strip().split()[1]))
        return ResourceSupply()

    def _shutdown_execution_host(self, host, skip_on_failure):
        _perform_command(
            action=lambda: self.cmd_executor.execute(SunGridEngine._SHUTDOWN_HOST_EXECUTION_DAEMON % host),
            msg='Shutdown GE host execution daemon.',
            error_msg='Shutdown GE host execution daemon has failed.',
            skip_on_failure=skip_on_failure
        )

    def _remove_host_from_queue_settings(self, host, queue, skip_on_failure):
        _perform_command(
            action=lambda: self.cmd_executor.execute(SunGridEngine._REMOVE_HOST_FROM_QUEUE_SETTINGS % (queue, host)),
            msg='Remove host from queue settings.',
            error_msg='Removing host from queue settings has failed.',
            skip_on_failure=skip_on_failure
        )

    def _remove_host_from_host_group(self, host, hostgroup, skip_on_failure):
        _perform_command(
            action=lambda: self.cmd_executor.execute(SunGridEngine._REMOVE_HOST_FROM_HOST_GROUP % (host, hostgroup)),
            msg='Remove host from host group.',
            error_msg='Removing host from host group has failed.',
            skip_on_failure=skip_on_failure
        )

    def _remove_host_from_grid_engine(self, host, skip_on_failure):
        _perform_command(
            action=lambda: self.cmd_executor.execute(SunGridEngine._DELETE_HOST % host),
            msg='Remove host from GE.',
            error_msg='Removing host from GE has failed.',
            skip_on_failure=skip_on_failure
        )

    def _remove_host_from_administrative_hosts(self, host, skip_on_failure):
        _perform_command(
            action=lambda: self.cmd_executor.execute(SunGridEngine._REMOVE_HOST_FROM_ADMINISTRATIVE_HOSTS % host),
            msg='Remove host from list of administrative hosts.',
            error_msg='Removing host from list of administrative hosts has failed.',
            skip_on_failure=skip_on_failure
        )

    def kill_jobs(self, jobs, force=False):
        job_ids = [str(job.id) for job in jobs]
        self.cmd_executor.execute((SunGridEngine._FORCE_KILL_JOBS if force else SunGridEngine._KILL_JOBS) % ' '.join(job_ids))


class SunGridEngineDefaultDemandSelector(GridEngineDemandSelector):

    def __init__(self, grid_engine):
        self.grid_engine = grid_engine

    def select(self, jobs):
        initial_supply = functools.reduce(operator.add, self.grid_engine.get_host_supplies(), ResourceSupply())
        allocation_rules = {}
        for job in sorted(jobs, key=lambda job: job.root_id):
            allocation_rule = allocation_rules[job.pe] = allocation_rules.get(job.pe) \
                                                         or self.grid_engine.get_pe_allocation_rule(job.pe)
            if allocation_rule in AllocationRule.fractional_rules():
                initial_demand = FractionalDemand(cpu=job.cpu, gpu=job.gpu, mem=job.mem, exc=job.exc, owner=job.user)
                remaining_demand, remaining_supply = initial_demand.subtract(initial_supply)
            else:
                initial_demand = IntegralDemand(cpu=job.cpu, gpu=job.gpu, mem=job.mem, exc=job.exc, owner=job.user)
                remaining_demand, remaining_supply = initial_demand, initial_supply
            if not remaining_demand:
                Logger.warn('Ignoring job #{job_id} {job_name} by {job_user} because '
                            'it is pending even though '
                            'it requires resources which are available at the moment: '
                            '{job_resources}...'
                            .format(job_id=job.id, job_name=job.name, job_user=job.user,
                                    job_resources=self._as_resources_str(initial_demand, initial_supply)))
                continue
            initial_supply = remaining_supply
            yield remaining_demand

    def _as_resources_str(self, demand, supply):
        return ', '.join('{demand}/{supply} {name}'
                         .format(name=key,
                                 demand=getattr(demand, key),
                                 supply=getattr(supply, key))
                         for key in ['cpu', 'gpu', 'mem', 'exc'])


class SunGridEngineGlobalDemandSelector(GridEngineDemandSelector):

    def __init__(self, inner, grid_engine):
        self._inner = inner
        self._grid_engine = grid_engine

    def select(self, jobs):
        return self._inner.select(list(self.filter(jobs)))

    def filter(self, jobs):
        initial_supplies = map(self._get_int_supply, self._grid_engine.get_global_supplies())
        initial_supply = functools.reduce(operator.add, initial_supplies, CustomResourceSupply())
        for job in sorted(jobs, key=lambda job: job.root_id):
            initial_demand = self._get_job_int_demand(job, keys=initial_supply.values.keys())
            remaining_demand, remaining_supply = initial_demand.subtract(initial_supply)
            if remaining_demand:
                Logger.warn('Ignoring job #{job_id} {job_name} by {job_user} because '
                            'it requires global resources which are not available at the moment: '
                            '{job_resources}...'
                            .format(job_id=job.id, job_name=job.name, job_user=job.user,
                                    job_resources=self._as_resources_str(initial_demand, initial_supply)))
                continue
            initial_supply = remaining_supply
            yield job

    def _get_job_int_demand(self, job, keys):
        return CustomResourceDemand(values=dict(self._get_job_int_requests(job, keys)))

    def _get_job_int_requests(self, job, keys):
        for request_name, request_value in job.requests.items():
            if request_name not in keys:
                continue
            try:
                yield request_name, self._parse_int(request_value)
            except ValueError:
                Logger.warn('Job #{job_id} by {job_user} has unsupported requirement: {name}={value}'
                            .format(job_id=job.root_id, job_user=job.user,
                                    name=request_name, value=request_value),
                            trace=True)

    def _get_int_supply(self, supply):
        return CustomResourceSupply(values=dict(self._get_int_resources(supply)))

    def _get_int_resources(self, supply):
        for resource_name, resource_value in supply.values.items():
            try:
                yield resource_name, self._parse_int(resource_value)
            except ValueError:
                Logger.warn('Global has unsupported resource: {name}={value}'
                            .format(name=resource_name, value=resource_value),
                            trace=True)

    def _parse_int(self, value):
        return int(float(value))

    def _as_resources_str(self, custom_demand, custom_supply):
        return ', '.join('{demand}/{supply} {name}'
                         .format(name=key,
                                 demand=custom_demand.values.get(key, 0),
                                 supply=custom_supply.values.get(key, 0))
                         for key in custom_demand.values.keys())


class SunGridEngineHostWorkerValidatorHandler(WorkerValidatorHandler):

    def __init__(self, cmd_executor):
        self._cmd_executor = cmd_executor
        self._cmd = 'qconf -se %s'

    def is_valid(self, host):
        try:
            self._cmd_executor.execute(self._cmd % host)
            return True
        except RuntimeError as e:
            if 'not an execution host' in str(e):
                Logger.warn('Execution host {host} not found in GE which makes host unavailable'
                            .format(host=host),
                            crucial=True, trace=True)
                return False
            if 'can\'t resolve hostname' in str(e):
                Logger.warn('Execution host {host} not found in GE (DNS) which makes host unavailable'
                            .format(host=host),
                            crucial=True, trace=True)
                return False
            Logger.warn('Execution host {host} not found in GE but it is considered available'
                        .format(host=host),
                        crucial=True, trace=True)
            return True


class SunGridEngineStateWorkerValidatorHandler(WorkerValidatorHandler):

    def __init__(self, cmd_executor, queue):
        self._cmd_executor = cmd_executor
        self._queue = queue
        self._cmd = 'qhost -q -xml'
        self._host_bad_states = ['u', 'E', 'd']

    def is_valid(self, host):
        try:
            output = self._cmd_executor.execute(self._cmd)
            root = ElementTree.fromstring(output)
            for host_object in root.findall('host[@name=\'%s\']' % host):
                for queue in host_object.findall('queue[@name=\'%s\']' % self._queue):
                    host_states = queue.find('queuevalue[@name=\'state_string\']').text or ''
                    for host_state in host_states:
                        if host_state in self._host_bad_states:
                            Logger.warn('Execution host {host} GE state is {host_state} which makes host unavailable'
                                        .format(host=host, host_state=host_state),
                                        crucial=True)
                            return False
                    if host_states:
                        Logger.warn('Execution host {host} GE state is {host_state} but it is considered available'
                                    .format(host=host, host_state=', '.join(host_states)),
                                    crucial=True)
            return True
        except RuntimeError:
            Logger.warn('Execution host {host} GE state not found which makes host unavailable'
                        .format(host=host),
                        crucial=True, trace=True)
            return False


class SunGridEngineJobValidator(GridEngineJobValidator):

    def __init__(self, grid_engine, instance_max_supply, cluster_max_supply):
        self.grid_engine = grid_engine
        self.instance_max_supply = instance_max_supply
        self.cluster_max_supply = cluster_max_supply

    def validate(self, jobs):
        valid_jobs, invalid_jobs = [], []
        allocation_rules = {}
        for job in jobs:
            allocation_rule = allocation_rules[job.pe] = allocation_rules.get(job.pe) \
                                                         or self.grid_engine.get_pe_allocation_rule(job.pe)
            job_demand = IntegralDemand(cpu=job.cpu, gpu=job.gpu, mem=job.mem, exc=job.exc)
            if allocation_rule in AllocationRule.fractional_rules():
                if job_demand > self.cluster_max_supply:
                    Logger.warn('Invalid job #{job_id} {job_name} by {job_user} requires resources '
                                'which cannot be satisfied by the cluster: '
                                '{job_cpu}/{available_cpu} cpu, '
                                '{job_gpu}/{available_gpu} gpu, '
                                '{job_mem}/{available_mem} mem, '
                                '{job_exc}/{available_exc} exc.'
                                .format(job_id=job.id, job_name=job.name, job_user=job.user,
                                        job_cpu=job.cpu, available_cpu=self.cluster_max_supply.cpu,
                                        job_gpu=job.gpu, available_gpu=self.cluster_max_supply.gpu,
                                        job_mem=job.mem, available_mem=self.cluster_max_supply.mem,
                                        job_exc=job.exc, available_exc=self.cluster_max_supply.exc),
                                crucial=True)
                    invalid_jobs.append(job)
                    continue
            else:
                if job_demand > self.instance_max_supply:
                    Logger.warn('Invalid job #{job_id} {job_name} by {job_user} requires resources '
                                'which cannot be satisfied by the biggest instance in cluster: '
                                '{job_cpu}/{available_cpu} cpu, '
                                '{job_gpu}/{available_gpu} gpu, '
                                '{job_mem}/{available_mem} mem, '
                                '{job_exc}/{available_exc} exc.'
                                .format(job_id=job.id, job_name=job.name, job_user=job.user,
                                        job_cpu=job.cpu, available_cpu=self.instance_max_supply.cpu,
                                        job_gpu=job.gpu, available_gpu=self.instance_max_supply.gpu,
                                        job_mem=job.mem, available_mem=self.instance_max_supply.mem,
                                        job_exc=job.exc, available_exc=self.instance_max_supply.exc),
                                crucial=True)
                    invalid_jobs.append(job)
                    continue
            valid_jobs.append(job)
        return valid_jobs, invalid_jobs


class SunGridEngineCustomRequestsPurgeJobProcessor(GridEngineJobProcessor):

    def __init__(self, cmd_executor, gpu_resource_name, mem_resource_name, exc_resource_name, dry_run):
        self._cmd_executor = cmd_executor
        self._gpu_resource_name = gpu_resource_name
        self._mem_resource_name = mem_resource_name
        self._exc_resource_name = exc_resource_name
        self._default_resource_names = [self._gpu_resource_name,
                                        self._mem_resource_name,
                                        self._exc_resource_name]
        self._dry_run = dry_run
        self._cmd = 'qalter {job_id} -l {job_requests}'

    def process(self, jobs):
        relevant_jobs, irrelevant_jobs = [], []
        for job in jobs:
            if job.root_id != job.id:
                relevant_jobs.append(job)
                continue
            if all(request_name in self._default_resource_names for request_name in job.requests):
                relevant_jobs.append(job)
                continue
            try:
                Logger.info('Purging job #{} custom requirements...'.format(job.id))
                self._purge_custom_requests(job)
                irrelevant_jobs.append(job)
            except Exception:
                Logger.warn('Job #{} custom requirements purge has failed'.format(job.id), crucial=True, trace=True)
                relevant_jobs.append(job)
        return relevant_jobs, irrelevant_jobs

    def _purge_custom_requests(self, job):
        if self._dry_run:
            return
        job_default_requests = {}
        if job.gpu:
            job_default_requests[self._gpu_resource_name] = str(job.gpu)
        if job.mem:
            job_default_requests[self._mem_resource_name] = str(job.mem) + 'G'
        if job.exc:
            job_default_requests[self._exc_resource_name] = str(bool(job.exc)).lower()
        self._cmd_executor.execute(self._cmd.format(
            job_id=job.root_id,
            job_requests=','.join('{}={}'.format(k, v) for k, v in job_default_requests.items())))


class SunGridEngineLaunchAdapter(GridEngineLaunchAdapter):

    def __init__(self, queue, hostlist):
        self._queue = queue
        self._hostlist = hostlist

    def get_worker_init_task_name(self):
        return 'SGEWorkerSetup'

    def get_worker_launch_params(self):
        return {
            'CP_CAP_SGE': 'false',
            'CP_CAP_SGE_QUEUE_NAME': self._queue,
            'CP_CAP_SGE_HOSTLIST_NAME': self._hostlist
        }
