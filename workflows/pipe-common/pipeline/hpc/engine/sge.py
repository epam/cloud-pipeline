import functools
import math
import operator
import os
import traceback
from datetime import datetime
from xml.etree import ElementTree

from pipeline.hpc.cmd import ExecutionError
from pipeline.hpc.logger import Logger
from pipeline.hpc.resource import IntegralDemand, ResourceSupply, FractionalDemand
from pipeline.hpc.engine.gridengine import GridEngine, GridEngineJobState, GridEngineJob, AllocationRule, \
    GridEngineType, _perform_command, GridEngineDemandSelector, GridEngineJobValidator


class SunGridEngine(GridEngine):
    _DELETE_HOST = 'qconf -de %s'
    _SHOW_PE_ALLOCATION_RULE = 'qconf -sp %s | grep "^allocation_rule" | awk \'{print $2}\''
    _REMOVE_HOST_FROM_HOST_GROUP = 'qconf -dattr hostgroup hostlist %s %s'
    _REMOVE_HOST_FROM_QUEUE_SETTINGS = 'qconf -purge queue slots %s@%s'
    _SHUTDOWN_HOST_EXECUTION_DAEMON = 'qconf -ke %s'
    _REMOVE_HOST_FROM_ADMINISTRATIVE_HOSTS = 'qconf -dh %s'
    _QSTAT = 'qstat -u "*" -r -f -xml'
    _QHOST = 'qhost -q -xml'
    _QSTAT_DATETIME_FORMAT = '%Y-%m-%dT%H:%M:%S'
    _QMOD_DISABLE = 'qmod -d %s@%s'
    _QMOD_ENABLE = 'qmod -e %s@%s'
    _SHOW_EXECUTION_HOST = 'qconf -se %s'
    _KILL_JOBS = 'qdel %s'
    _FORCE_KILL_JOBS = 'qdel -f %s'
    _BAD_HOST_STATES = ['u', 'E', 'd']

    def __init__(self, cmd_executor, queue, hostlist, queue_default):
        self.cmd_executor = cmd_executor
        self.queue = queue
        self.hostlist = hostlist
        self.queue_default = queue_default
        self.tmp_queue_name_attribute = 'tmp_queue_name'
        # todo: Move to script init function
        self.gpu_resource_name = os.getenv('CP_CAP_GE_CONSUMABLE_RESOURCE_NAME_GPU', 'gpus')
        self.mem_resource_name = os.getenv('CP_CAP_GE_CONSUMABLE_RESOURCE_NAME_RAM', 'ram')

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
            job_state = GridEngineJobState.from_letter_code(job_list.findtext('state'))
            job_datetime = self._parse_date(
                job_list.findtext('JAT_start_time') or job_list.findtext('JB_submission_time'))
            job_hosts = [job_host] if job_host else []
            requested_pe = job_list.find('requested_pe')
            job_pe = requested_pe.get('name') if requested_pe is not None else 'local'
            job_cpu = int(requested_pe.text if requested_pe is not None else '1')
            job_gpu = 0
            job_mem = 0
            hard_requests = job_list.findall('hard_request')
            for hard_request in hard_requests:
                hard_request_name = hard_request.get('name')
                if hard_request_name == self.gpu_resource_name:
                    job_gpu_request = hard_request.text or '0'
                    try:
                        job_gpu = int(job_gpu_request)
                    except ValueError:
                        Logger.warn('Job #{job_id} by {job_user} has invalid gpu requirement '
                                    'which cannot be parsed: {request}'
                                    .format(job_id=root_job_id, job_user=job_user, request=job_gpu_request))
                        Logger.warn(traceback.format_exc())
                if hard_request_name == self.mem_resource_name:
                    job_mem_request = hard_request.text or '0G'
                    try:
                        job_mem = self._parse_mem(job_mem_request)
                    except Exception:
                        Logger.warn('Job #{job_id} by {job_user} has invalid mem requirement '
                                    'which cannot be parsed: {request}'
                                    .format(job_id=root_job_id, job_user=job_user, request=job_mem_request))
                        Logger.warn(traceback.format_exc())
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
                        pe=job_pe
                    )
        return jobs.values()

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
        """
        See https://linux.die.net/man/1/sge_types
        """
        if not mem_request:
            return 0
        modifiers = {
            'k': 1000, 'm': 1000 ** 2, 'g': 1000 ** 3,
            'K': 1024, 'M': 1024 ** 2, 'G': 1024 ** 3
        }
        if mem_request[-1] in modifiers:
            number = int(mem_request[:-1])
            modifier = modifiers[mem_request[-1]]
        else:
            number = int(mem_request)
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

    def get_host_supplies(self):
        output = self.cmd_executor.execute(SunGridEngine._QHOST)
        root = ElementTree.fromstring(output)
        for host in root.findall('host'):
            for queue in host.findall('queue[@name=\'%s\']' % self.queue):
                host_states = queue.find('queuevalue[@name=\'state_string\']').text or ''
                if any(host_state in self._BAD_HOST_STATES for host_state in host_states):
                    continue
                host_slots = int(queue.find('queuevalue[@name=\'slots\']').text or '0')
                host_used = int(queue.find('queuevalue[@name=\'slots_used\']').text or '0')
                host_resv = int(queue.find('queuevalue[@name=\'slots_resv\']').text or '0')
                yield ResourceSupply(cpu=host_slots) - ResourceSupply(cpu=host_used + host_resv)

    def get_host_supply(self, host):
        for line in self.cmd_executor.execute_to_lines(SunGridEngine._SHOW_EXECUTION_HOST % host):
            if "processors" in line:
                return ResourceSupply(cpu=int(line.strip().split()[1]))
        return ResourceSupply()

    def get_engine_type(self):
        return GridEngineType.SGE

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

    def is_valid(self, host):
        try:
            self.cmd_executor.execute_to_lines(SunGridEngine._SHOW_EXECUTION_HOST % host)
            output = self.cmd_executor.execute(SunGridEngine._QHOST)
            root = ElementTree.fromstring(output)
            for host_object in root.findall('host[@name=\'%s\']' % host):
                for queue in host_object.findall('queue[@name=\'%s\']' % self.queue):
                    host_states = queue.find('queuevalue[@name=\'state_string\']').text or ''
                    for host_state in host_states:
                        if host_state in self._BAD_HOST_STATES:
                            Logger.warn('Execution host %s GE state is %s which makes host invalid.'
                                        % (host, host_state))
                            return False
                    if host_states:
                        Logger.warn('Execution host %s GE state is not empty but is considered valid: %s.'
                                    % (host, host_states))
            return True
        except RuntimeError as e:
            Logger.warn('Execution host %s validation has failed in GE: %s' % (host, e))
            return False

    def kill_jobs(self, jobs, force=False):
        job_ids = [str(job.id) for job in jobs]
        self.cmd_executor.execute((SunGridEngine._FORCE_KILL_JOBS if force else SunGridEngine._KILL_JOBS) % ' '.join(job_ids))


class SunGridEngineDemandSelector(GridEngineDemandSelector):

    def __init__(self, grid_engine):
        self.grid_engine = grid_engine

    def select(self, jobs):
        remaining_supply = functools.reduce(operator.add, self.grid_engine.get_host_supplies(), ResourceSupply())
        allocation_rules = {}
        for job in sorted(jobs, key=lambda job: job.root_id):
            allocation_rule = allocation_rules[job.pe] = allocation_rules.get(job.pe) \
                                                         or self.grid_engine.get_pe_allocation_rule(job.pe)
            if allocation_rule in AllocationRule.fractional_rules():
                remaining_demand = FractionalDemand(cpu=job.cpu, gpu=job.gpu, mem=job.mem, owner=job.user)
                remaining_demand, remaining_supply = remaining_demand.subtract(remaining_supply)
            else:
                remaining_demand = IntegralDemand(cpu=job.cpu, gpu=job.gpu, mem=job.mem, owner=job.user)
            if not remaining_demand:
                Logger.warn('Problematic job #{job_id} {job_name} by {job_user} is pending for an unknown reason. '
                            'The job requires resources which are already satisfied by the cluster: '
                            '{job_cpu} cpu, {job_gpu} gpu, {job_mem} mem.'
                            .format(job_id=job.id, job_name=job.name, job_user=job.user,
                                    job_cpu=job.cpu, job_gpu=job.gpu, job_mem=job.mem),
                            crucial=True)
                continue
            yield remaining_demand


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
            job_demand = IntegralDemand(cpu=job.cpu, gpu=job.gpu, mem=job.mem)
            if allocation_rule in AllocationRule.fractional_rules():
                if job_demand > self.cluster_max_supply:
                    Logger.warn('Invalid job #{job_id} {job_name} by {job_user} requires resources '
                                'which cannot be satisfied by the cluster: '
                                '{job_cpu}/{available_cpu} cpu, '
                                '{job_gpu}/{available_gpu} gpu, '
                                '{job_mem}/{available_mem} mem.'
                                .format(job_id=job.id, job_name=job.name, job_user=job.user,
                                        job_cpu=job.cpu, available_cpu=self.cluster_max_supply.cpu,
                                        job_gpu=job.gpu, available_gpu=self.cluster_max_supply.gpu,
                                        job_mem=job.mem, available_mem=self.cluster_max_supply.mem),
                                crucial=True)
                    invalid_jobs.append(job)
                    continue
            else:
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

