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

import functools
import json
import logging
import multiprocessing
import os
import re
import subprocess
import threading
import traceback
from collections import Counter, namedtuple
from xml.etree import ElementTree

import errno
import itertools
import requests
import sys
import time
from datetime import datetime, timedelta

from pipeline import PipelineAPI, Logger as CloudPipelineLogger

try:
    from queue import Queue, Empty as QueueEmptyError
except ImportError:
    from Queue import Queue, Empty as QueueEmptyError

GridEngineParameter = namedtuple('GridEngineParameter', 'name,help')
KubernetesPod = namedtuple('KubernetesPod', 'ip,name')


class GridEngineParametersGroup:

    def as_list(self):
        return list(self.as_gen())

    def as_dict(self):
        return {attr.name: attr for attr in self.as_gen()}

    def as_gen(self):
        for attr_name in sorted(dir(self)):
            if attr_name.startswith('__'):
                continue
            attr = getattr(self, attr_name)
            if not isinstance(attr, GridEngineParameter):
                continue
            yield attr


class GridEngineAutoscalingParametersGroup(GridEngineParametersGroup):

    def __init__(self):
        self.autoscale = GridEngineParameter(
            name='CP_CAP_AUTOSCALE',
            help='Enables autoscaling.')
        self.autoscaling_hosts_number = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_WORKERS',
            help='Specifies a maximum number of autoscaling workers.')
        self.instance_type = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_INSTANCE_TYPE',
            help='Specifies worker instance type.')
        self.instance_disk = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_INSTANCE_DISK',
            help='Specifies worker disk size.')
        self.instance_image = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_INSTANCE_IMAGE',
            help='Specifies worker docker image.')
        self.price_type = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_PRICE_TYPE',
            help='Specifies worker price type.')
        self.cmd_template = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_CMD_TEMPLATE',
            help='Specifies worker cmd template.')
        self.hybrid_autoscale = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_HYBRID',
            help='Enables hybrid autoscaling.')
        self.hybrid_instance_family = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_HYBRID_FAMILY',
            help='Specifies hybrid worker instance type family.')
        self.hybrid_instance_cores = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_HYBRID_MAX_CORE_PER_NODE',
            help='Specifies a maximum number of CPUs available on hybrid autoscaling workers.\n'
                 'If specified only instance types which have less or equal number of CPUs will be used.')
        self.descending_autoscale = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_DESCENDING',
            help='Enables descending autoscaling.\n'
                 'As long as default instance type is available then autoscaling works as non hybrid.\n'
                 'If target instance type is temporary unavailable then autoscaling works as hybrid\n'
                 'using only smaller instance types from the same instance family.')
        self.scale_up_strategy = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_SCALE_UP_STRATEGY',
            help='Specifies autoscaling strategy.\n'
                 'Allowed values:\n'
                 '    cpu-capacity (default):\n'
                 '        Scales up instance types which capacities allow to execute\n'
                 '        the waiting jobs in the fastest possible manner.\n'
                 '    naive-cpu-capacity (deprecated):\n'
                 '        Scales up instance types based on naive sum of all job CPU requirements.\n'
                 '    default (deprecated):\n'
                 '        If batch autoscaling is used\n'
                 '        then has the same effect as cpu-capacity strategy\n'
                 '        else has the same effect as naive-cpu-capacity.')
        self.scale_up_batch_size = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_SCALE_UP_BATCH_SIZE',
            help='Specifies a maximum number of simultaneously scaling up workers.')
        self.scale_up_polling_delay = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_SCALE_UP_POLLING_DELAY',
            help='Specifies a status polling delay in seconds for workers scaling up.')
        self.scale_up_unavailability_delay = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_INSTANCE_UNAVAILABILITY_DELAY',
            help='Specifies a delay in seconds to temporary avoid unavailable instance types usage.\n'
                 'An instance type is considered unavailable if cloud region lacks such instances at the moment.')
        self.scale_down_idle_timeout = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_IDLE_TIMEOUT',
            help='Specifies a timeout in seconds after which an inactive worker is considered idled.\n'
                 'If an autoscaling worker is idle then it is scaled down.')
        self.log_dir = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_LOGDIR',
            help='Specifies logging directory.')
        self.log_verbose = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_VERBOSE',
            help='Enables verbose logging.')


class GridEngineAdvancedAutoscalingParametersGroup(GridEngineParametersGroup):

    def __init__(self):
        self.instance_cloud_provider = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_CLOUD_PROVIDER',
            help='Specifies worker cloud provider.\n'
                 'Allowed values: AWS, GCP and AZURE.')
        self.instance_region_id = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_CLOUD_REGION_ID',
            help='Specifies cloud region id.')
        self.instance_owner_param = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_OWNER_PARAMETER_NAME',
            help='Specifies worker parameter name which is used to specify an owner of a worker.\n'
                 'The parameter is used to bill specific users rather than a cluster owner.')
        self.work_dir = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_WORKDIR',
            help='Specifies autoscaler working directory.')
        self.log_task = GridEngineParameter(
            name='CP_CAP_AUTOSCALE_TASK',
            help='Specifies logging task.')


class GridEngineQueueParameters(GridEngineParametersGroup):

    def __init__(self):
        self.queue_name = GridEngineParameter(
            name='CP_CAP_SGE_QUEUE_NAME',
            help='Specifies a name of a queue which is going to be autoscaled.')
        self.queue_static = GridEngineParameter(
            name='CP_CAP_SGE_QUEUE_STATIC',
            help='Enables static queue processing.\n'
                 'If enabled then all static workers are considered to be part of this queue.')
        self.queue_default = GridEngineParameter(
            name='CP_CAP_SGE_QUEUE_DEFAULT',
            help='Enables default queue processing.\n'
                 'If enabled then all jobs without hard queue requirement are considered to be part of this queue.')
        self.hostlist_name = GridEngineParameter(
            name='CP_CAP_SGE_HOSTLIST_NAME',
            help='Specifies a name of a hostlist which is associated with the autoscaling queue.')
        self.hosts_free_cores = GridEngineParameter(
            name='CP_CAP_SGE_WORKER_FREE_CORES',
            help='Specifies a number of free cores on workers.')
        self.master_cores = GridEngineParameter(
            name='CP_CAP_SGE_MASTER_CORES',
            help='Specifies a number of available cores on a cluster manager.')


class GridEngineParameters(GridEngineParametersGroup):

    def __init__(self):
        self.autoscaling = GridEngineAutoscalingParametersGroup()
        self.autoscaling_advanced = GridEngineAdvancedAutoscalingParametersGroup()
        self.queue = GridEngineQueueParameters()

    def as_gen(self):
        attrs = itertools.chain(self.autoscaling.as_gen(),
                                self.autoscaling_advanced.as_gen(),
                                self.queue.as_gen())
        return sorted(attrs, key=lambda attr: attr.name)


def synchronized(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        lock = args[0]._lock
        try:
            lock.acquire()
            return_value = func(*args, **kwargs)
            return return_value
        finally:
            lock.release()
    return wrapper


class ExecutionError(RuntimeError):
    pass


class ParsingError(RuntimeError):
    pass


class LoggingError(RuntimeError):
    pass


class ScalingError(RuntimeError):
    pass


class ServerError(RuntimeError):
    pass


class HTTPError(ServerError):
    pass


class APIError(ServerError):
    pass


class Logger:
    task = None
    cmd = None
    verbose = None

    @staticmethod
    def init(task=None, log_file=None, verbose=False):
        if not task or not log_file:
            raise LoggingError('Arguments \'task\' and \'log_file\' should be specified.')
        Logger.task = task
        Logger.verbose = verbose

        make_dirs(os.path.dirname(log_file))

        logging_level = logging.INFO
        logging_formatter = logging.Formatter('%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

        logging.getLogger().setLevel(logging_level)

        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setLevel(logging_level)
        console_handler.setFormatter(logging_formatter)
        logging.getLogger().addHandler(console_handler)

        file_handler = logging.FileHandler(log_file)
        file_handler.setLevel(logging_level)
        file_handler.setFormatter(logging_formatter)
        logging.getLogger().addHandler(file_handler)

    @staticmethod
    def info(message, crucial=False, *args, **kwargs):
        logging.info(message, *args, **kwargs)
        if not Logger.cmd and (crucial or Logger.verbose):
            CloudPipelineLogger.info(message, task_name=Logger.task, omit_console=True)

    @staticmethod
    def warn(message, crucial=False, *args, **kwargs):
        logging.warn(message, *args, **kwargs)
        if not Logger.cmd and (crucial or Logger.verbose):
            CloudPipelineLogger.warn(message, task_name=Logger.task, omit_console=True)

    @staticmethod
    def success(message, crucial=True, *args, **kwargs):
        logging.info(message, *args, **kwargs)
        if not Logger.cmd and (crucial or Logger.verbose):
            CloudPipelineLogger.success(message, task_name=Logger.task, omit_console=True)

    @staticmethod
    def fail(message, crucial=True, *args, **kwargs):
        logging.error(message, *args, **kwargs)
        if not Logger.cmd and (crucial or Logger.verbose):
            CloudPipelineLogger.fail(message, task_name=Logger.task, omit_console=True)


class CmdExecutor:

    def __init__(self):
        pass

    def execute(self, command):
        process = subprocess.Popen(command, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE)
        out, err = process.communicate()
        exit_code = process.wait()
        if exit_code != 0:
            exec_err_msg = 'Command \'%s\' execution has failed. Out: %s Err: %s.' % (command, out.rstrip(), err.rstrip())
            Logger.warn(exec_err_msg)
            raise ExecutionError(exec_err_msg)
        return out

    def execute_to_lines(self, command):
        return self._non_empty(self.execute(command).splitlines())

    def _non_empty(self, elements):
        return [element for element in elements if element.strip()]


class GridEngineJobState:
    RUNNING = 'running'
    PENDING = 'pending'
    SUSPENDED = 'suspended'
    ERROR = 'errored'
    DELETED = 'deleted'

    _letter_codes_to_states = {
        RUNNING: ['r', 't', 'Rr', 'Rt'],
        PENDING: ['qw', 'qw', 'hqw', 'hqw', 'hRwq', 'hRwq', 'hRwq', 'qw', 'qw'],
        SUSPENDED: ['s', 'ts', 'S', 'tS', 'T', 'tT', 'Rs', 'Rts', 'RS', 'RtS', 'RT', 'RtT'],
        ERROR: ['Eqw', 'Ehqw', 'EhRqw'],
        DELETED: ['dr', 'dt', 'dRr', 'dRt', 'ds', 'dS', 'dT', 'dRs', 'dRS', 'dRT']
    }

    @staticmethod
    def from_letter_code(code):
        for key in GridEngineJobState._letter_codes_to_states:
            if code in GridEngineJobState._letter_codes_to_states[key]:
                return key
        raise ParsingError('Unknown sge job state: %s.' % code)


class GridEngineJob:

    def __init__(self, id, root_id, name, user, state, datetime, hosts=None, slots=0, pe='local'):
        self.id = id
        self.root_id = root_id
        self.name = name
        self.user = user
        self.state = state
        self.datetime = datetime
        self.hosts = hosts if hosts else []
        self.slots = slots
        self.pe = pe


class GridEngine:
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

    def __init__(self, cmd_executor, max_instance_cores, max_cluster_cores, queue, hostlist, queue_default):
        self.cmd_executor = cmd_executor
        self.max_instance_cores = max_instance_cores
        self.max_cluster_cores = max_cluster_cores
        self.queue = queue
        self.hostlist = hostlist
        self.queue_default = queue_default
        self.tmp_queue_name_attribute = 'tmp_queue_name'

    def get_jobs(self):
        try:
            output = self.cmd_executor.execute(GridEngine._QSTAT)
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
            job_datetime = self._parse_date(job_list.findtext('JAT_start_time') or job_list.findtext('JB_submission_time'))
            job_hosts = [job_host] if job_host else []
            requested_pe = job_list.find('requested_pe')
            job_slots = int(requested_pe.text if requested_pe is not None else '1')
            job_pe = requested_pe.get('name') if requested_pe is not None else 'local'
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
                        slots=job_slots,
                        pe=job_pe
                    )
        return jobs.values()

    def _parse_date(self, date):
        return datetime.strptime(date, GridEngine._QSTAT_DATETIME_FORMAT)

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

    def is_job_valid(self, job):
        result = True
        allocation_rule = self.get_pe_allocation_rule(job.pe) if job.pe else AllocationRule.pe_slots()
        if job.slots:
            if allocation_rule == AllocationRule.pe_slots():
                result = job.slots <= self.max_instance_cores
                if not result:
                    Logger.warn('Invalid job {job_id} found with allocation_rule={alloc_rule} and slots={slots}. '
                                'Number of job slots should be less or equal '
                                'to the number of instance cores of the largest allowed instance. '
                                'It is {max_instance_cores} for the current launch.'
                                .format(job_id=job.id, alloc_rule=allocation_rule.value, slots=job.slots,
                                        max_instance_cores=self.max_instance_cores))
            elif allocation_rule in [AllocationRule.fill_up(), AllocationRule.round_robin()]:
                result = job.slots <= self.max_cluster_cores
                if not result:
                    Logger.warn('Invalid job {job_id} found with allocation_rule={alloc_rule} and slots={slots}. '
                                'Number of job slots should be less or equal '
                                'to the maximum possible number of cluster cores. '
                                'It is {max_cluster_cores} for the current launch.'
                                .format(job_id=job.id, alloc_rule=allocation_rule.value, slots=job.slots,
                                        max_cluster_cores=self.max_cluster_cores))
        return result

    def disable_host(self, host):
        """
        Disables host to prevent receiving new jobs from the queue.
        This command does not abort currently running jobs.

        :param host: Host to be enabled.
        """
        self.cmd_executor.execute(GridEngine._QMOD_DISABLE % (self.queue, host))

    def enable_host(self, host):
        """
        Enables host to make it available to receive new jobs from the queue.

        :param host: Host to be enabled.
        """
        self.cmd_executor.execute(GridEngine._QMOD_ENABLE % (self.queue, host))

    def get_pe_allocation_rule(self, pe):
        """
        Returns allocation rule of the pe

        :param pe: Parallel environment to return allocation rule.
        """
        exec_result = self.cmd_executor.execute(GridEngine._SHOW_PE_ALLOCATION_RULE % pe)
        return AllocationRule(exec_result.strip()) if exec_result else AllocationRule.pe_slots()

    def delete_host(self, host, skip_on_failure=False):
        """
        Completely deletes host from GE:
        1. Shutdown host execution daemon.
        2. Removes host from queue settings.
        3. Removes host from host group.
        4. Removes host from administrative hosts.
        5. Removes host from GE.

        :param host: Host to be removed.
        :param skip_on_failure: Specifies if the host killing should be continued even if some of
        the commands has failed.
        """
        self._shutdown_execution_host(host, skip_on_failure=skip_on_failure)
        self._remove_host_from_queue_settings(host, self.queue, skip_on_failure=skip_on_failure)
        self._remove_host_from_host_group(host, self.hostlist, skip_on_failure=skip_on_failure)
        self._remove_host_from_administrative_hosts(host, skip_on_failure=skip_on_failure)
        self._remove_host_from_grid_engine(host, skip_on_failure=skip_on_failure)

    def get_resource_demands(self, expired_jobs):
        demands = []
        available_slots = self._get_available_slots()
        for job in sorted(expired_jobs, key=lambda job: job.root_id):
            if self.get_pe_allocation_rule(job.pe) in [AllocationRule.round_robin(), AllocationRule.fill_up()]:
                if available_slots >= job.slots:
                    available_slots -= job.slots
                else:
                    demands.append(FractionalDemand(job.slots - available_slots, owner=job.user))
                    available_slots = 0
            else:
                demands.append(IntegralDemand(job.slots, owner=job.user))
        return demands

    def get_host_to_scale_down(self, hosts):
        # choose the weakest one (map to number of CPU, sort in reverse order and get from top)
        # TODO: in the future other resources like RAM and GPU can be count here
        return sorted([(host, self.get_host_resource(host)) for host in hosts],
                      cmp=lambda h1, h2: h1[1].cpu - h2[1].cpu, reverse=True).pop()[0]

    def _get_available_slots(self):
        available_slots = 0
        output = self.cmd_executor.execute(GridEngine._QHOST)
        root = ElementTree.fromstring(output)
        for host in root.findall('host'):
            for queue in host.findall('queue[@name=\'%s\']' % self.queue):
                host_used = int(queue.find('queuevalue[@name=\'slots_used\']').text or '0')
                host_resv = int(queue.find('queuevalue[@name=\'slots_resv\']').text or '0')
                host_slots = int(queue.find('queuevalue[@name=\'slots\']').text or '0')
                host_states = queue.find('queuevalue[@name=\'state_string\']').text or ''
                if all(host_state not in self._BAD_HOST_STATES for host_state in host_states):
                    available_slots += max(host_slots - host_used - host_resv, 0)
        return available_slots

    def get_host_resource(self, host):
        for line in self.cmd_executor.execute_to_lines(GridEngine._SHOW_EXECUTION_HOST % host):
            if "processors" in line:
                return ResourceSupply(int(line.strip().split()[1]))

    def _shutdown_execution_host(self, host, skip_on_failure):
        self._perform_command(
            action=lambda: self.cmd_executor.execute(GridEngine._SHUTDOWN_HOST_EXECUTION_DAEMON % host),
            msg='Shutdown GE host execution daemon.',
            error_msg='Shutdown GE host execution daemon has failed.',
            skip_on_failure=skip_on_failure
        )

    def _remove_host_from_queue_settings(self, host, queue, skip_on_failure):
        self._perform_command(
            action=lambda: self.cmd_executor.execute(GridEngine._REMOVE_HOST_FROM_QUEUE_SETTINGS % (queue, host)),
            msg='Remove host from queue settings.',
            error_msg='Removing host from queue settings has failed.',
            skip_on_failure=skip_on_failure
        )

    def _remove_host_from_host_group(self, host, hostgroup, skip_on_failure):
        self._perform_command(
            action=lambda: self.cmd_executor.execute(GridEngine._REMOVE_HOST_FROM_HOST_GROUP % (host, hostgroup)),
            msg='Remove host from host group.',
            error_msg='Removing host from host group has failed.',
            skip_on_failure=skip_on_failure
        )

    def _remove_host_from_grid_engine(self, host, skip_on_failure):
        self._perform_command(
            action=lambda: self.cmd_executor.execute(GridEngine._DELETE_HOST % host),
            msg='Remove host from GE.',
            error_msg='Removing host from GE has failed.',
            skip_on_failure=skip_on_failure
        )

    def _remove_host_from_administrative_hosts(self, host, skip_on_failure):
        self._perform_command(
            action=lambda: self.cmd_executor.execute(GridEngine._REMOVE_HOST_FROM_ADMINISTRATIVE_HOSTS % host),
            msg='Remove host from list of administrative hosts.',
            error_msg='Removing host from list of administrative hosts has failed.',
            skip_on_failure=skip_on_failure
        )

    def _perform_command(self, action, msg, error_msg, skip_on_failure):
        Logger.info(msg)
        try:
            action()
        except RuntimeError as e:
            Logger.warn(error_msg)
            if not skip_on_failure:
                raise RuntimeError(error_msg, e)

    def is_valid(self, host):
        """
        Validates host in GE checking corresponding execution host availability and its states.

        :param host: Host to be checked.
        :return: True if execution host is valid.
        """
        try:
            self.cmd_executor.execute_to_lines(GridEngine._SHOW_EXECUTION_HOST % host)
            output = self.cmd_executor.execute(GridEngine._QHOST)
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
        """
        Kills jobs in GE.

        :param jobs: Grid engine jobs.
        :param force: Specifies if this command should be performed with -f flag.
        """
        job_ids = [str(job.id) for job in jobs]
        self.cmd_executor.execute((GridEngine._FORCE_KILL_JOBS if force else GridEngine._KILL_JOBS) % ' '.join(job_ids))


class ComputeResource:

    def __init__(self, cpu=0, gpu=0, memory=0, disk=0, owner=None):
        """
        Common compute resource.
        """
        self.cpu = cpu
        self.gpu = gpu
        self.memory = memory
        self.disk = disk
        self.owner = owner

    def add(self, other):
        return self.__class__(cpu=self.cpu + other.cpu,
                              gpu=self.gpu + other.gpu,
                              memory=self.memory + other.memory,
                              disk=self.disk + other.disk,
                              owner=self.owner or other.owner)

    def subtract(self, other):
        return (self.__class__(cpu=max(0, self.cpu - other.cpu),
                               gpu=max(0, self.gpu - other.gpu),
                               memory=max(0, self.memory - other.memory),
                               disk=max(0, self.disk - other.disk),
                               owner=self.owner or other.owner),
                other.__class__(cpu=max(0, other.cpu - self.cpu),
                                gpu=max(0, other.gpu - self.gpu),
                                memory=max(0, other.memory - self.memory),
                                disk=max(0, other.disk - self.disk),
                                owner=self.owner or other.owner))

    def __bool__(self):
        return self.cpu + self.gpu + self.memory + self.disk > 0

    __nonzero__ = __bool__


class FractionalDemand(ComputeResource):
    """
    Fractional resource demand which can be fulfilled using multiple resource supplies.

    Example of a fractional demand is mpi grid engine job requirements.
    """
    pass


class IntegralDemand(ComputeResource):
    """
    Integral resource demand which can be fulfilled using only a single resource supply.

    Example of a integral demand is non mpi grid engine job requirements.
    """
    pass


class ResourceSupply(ComputeResource):
    """
    Resource supply which can be used to fulfill resource demands.
    """
    pass


class InstanceDemand:

    def __init__(self, instance, owner):
        """
        Execution instance demand.
        """
        self.instance = instance
        self.owner = owner

    def __eq__(self, other):
        return self.__dict__ == other.__dict__

    def __repr__(self):
        return str(self.__dict__)


class Instance:

    def __init__(self, name, price_type, memory, gpu, cpu):
        """
        Execution instance.
        """
        self.name = name
        self.price_type = price_type
        self.memory = memory
        self.gpu = gpu
        self.cpu = cpu

    @staticmethod
    def from_cp_response(instance):
        return Instance(name=instance['name'],
                        price_type=instance['termType'],
                        cpu=int(instance['vcpu']),
                        gpu=int(instance['gpu']),
                        memory=int(instance['memory']))

    def __eq__(self, other):
        return self.__dict__ == other.__dict__

    def __repr__(self):
        return str(self.__dict__)


class Clock:

    def __init__(self):
        pass

    def now(self):
        return datetime.now()


class GridEngineScaleUpOrchestrator:
    _POLL_DELAY = 10

    def __init__(self, scale_up_handler, grid_engine, host_storage, instance_selector, worker_recorder,
                 batch_size, polling_delay=_POLL_DELAY, clock=Clock()):
        """
        Grid engine scale up orchestrator.

        Handles additional workers batch scaling up.
        Scales up no more than a configured number of additional workers at once.
        Waits for all batch additional workers to scale up before continuing.

        :param scale_up_handler: Scaling up handler.
        :param grid_engine: Grid engine client.
        :param host_storage: Additional hosts storage.
        :param instance_selector: Additional instances selector.
        :param worker_recorder: Additional hosts recorder.
        :param batch_size: Scaling up batch size.
        :param polling_delay: Polling delay - in seconds.
        """
        self.scale_up_handler = scale_up_handler
        self.grid_engine = grid_engine
        self.host_storage = host_storage
        self.instance_selector = instance_selector
        self.worker_recorder = worker_recorder
        self.batch_size = batch_size
        self.polling_delay = polling_delay
        self.clock = clock

    def scale_up(self, resource_demands, max_batch_size):
        instance_demands = list(itertools.islice(self.instance_selector.select(resource_demands),
                                                 min(self.batch_size, max_batch_size)))
        number_of_threads = len(instance_demands)
        Logger.info('Scaling up %s additional workers...' % number_of_threads)
        threads = []
        run_id_queue = Queue()
        for instance_demand in instance_demands:
            thread = threading.Thread(target=self.scale_up_handler.scale_up,
                                      args=(instance_demand.instance, instance_demand.owner, run_id_queue))
            thread.setDaemon(True)
            thread.start()
            threads.append(thread)
        while True:
            time.sleep(self.polling_delay)
            number_of_finished_threads = len([thread for thread in threads if not thread.is_alive()])
            if number_of_finished_threads == number_of_threads:
                Logger.info('All %s/%s additional workers have been scaled up.'
                            % (number_of_threads, number_of_threads))
                break
            Logger.info('Only %s/%s additional workers have been scaled up.'
                        % (number_of_finished_threads, number_of_threads))
            self._update_last_activity_for_currently_running_jobs()
        Logger.info('Recording details of %s additional workers...' % number_of_threads)
        while True:
            try:
                self.worker_recorder.record(run_id_queue.get(timeout=0))
            except QueueEmptyError:
                break
        Logger.info('Additional workers details recording has finished.')

    def _update_last_activity_for_currently_running_jobs(self):
        jobs = self.grid_engine.get_jobs()
        running_jobs = [job for job in jobs if job.state == GridEngineJobState.RUNNING]
        if running_jobs:
            self.host_storage.update_running_jobs_host_activity(running_jobs, self.clock.now())


class GridEngineScaleUpHandler:
    _POLL_TIMEOUT = 900
    _POLL_ATTEMPTS = 60
    _POLL_DELAY = 10
    _GE_POLL_TIMEOUT = 60
    _GE_POLL_ATTEMPTS = 6

    def __init__(self, cmd_executor, api, grid_engine, host_storage, parent_run_id, default_hostfile, instance_disk,
                 instance_image, cmd_template, price_type, region_id, queue, hostlist, owner_param_name, polling_timeout=_POLL_TIMEOUT, polling_delay=_POLL_DELAY,
                 ge_polling_timeout=_GE_POLL_TIMEOUT, instance_launch_params=None, clock=Clock()):
        """
        Grid engine scale up handler.

        Manages additional workers scaling up.

        :param cmd_executor: Cmd executor.
        :param api: Cloud pipeline client.
        :param grid_engine: Grid engine client.
        :param host_storage: Additional hosts storage.
        :param parent_run_id: Additional nodes parent run id.
        :param default_hostfile: Default host file location.
        :param instance_disk: Additional nodes disk size.
        :param instance_image: Additional nodes docker image.
        :param cmd_template: Additional nodes cmd template.
        :param price_type: Additional nodes price type.
        :param region_id: Additional nodes Cloud Region id.
        :param queue: Additional nodes queue.
        :param hostlist: Additional nodes hostlist.
        :param owner_param_name: Instance owner param name.
        :param polling_timeout: Kubernetes and Pipeline APIs polling timeout - in seconds.
        :param polling_delay: Polling delay - in seconds.
        :param ge_polling_timeout: Grid Engine polling timeout - in seconds.
        :param instance_launch_params: Instance launch params dictionary.
        """
        self.executor = cmd_executor
        self.api = api
        self.grid_engine = grid_engine
        self.host_storage = host_storage
        self.parent_run_id = parent_run_id
        self.default_hostfile = default_hostfile
        self.instance_disk = instance_disk
        self.instance_image = instance_image
        self.cmd_template = cmd_template
        self.price_type = price_type
        self.region_id = region_id
        self.queue = queue
        self.hostlist = hostlist
        self.owner_param_name = owner_param_name
        self.polling_timeout = polling_timeout
        self.polling_delay = polling_delay
        self.ge_polling_timeout = ge_polling_timeout
        self.instance_launch_params = instance_launch_params or {}
        self.clock = clock

    def scale_up(self, instance, owner, run_id_queue):
        """
        Scales up an additional worker.

        Notice that master hosts file is altered before an additional working node starts to add itself to GE
        configuration. Otherwise GE configuration will end up with 'can't resolve hostname'-like errors.

        Also notice that an additional worker host is manually enabled in GE only after its run is initialized.
        This happens because additional workers are disabled in GE by default to prevent job submissions to not
        initialized runs.
        """
        try:
            Logger.info('Scaling up additional worker (%s)...' % instance.name)
            run_id = self._launch_additional_worker(instance.name, owner)
            run_id_queue.put(run_id)
            host = self._retrieve_pod_name(run_id)
            self.host_storage.add_host(host)
            pod = self._await_pod_initialization(run_id)
            self._add_worker_to_master_hosts(pod)
            self._await_worker_initialization(run_id)
            self._enable_worker_in_grid_engine(pod)
            Logger.info('Additional worker %s (%s) has been scaled up.' % (pod.name, instance.name), crucial=True)
        except KeyboardInterrupt:
            pass
        except Exception as e:
            Logger.warn('Scaling up additional worker (%s) has failed due to %s.' % (instance.name, str(e)),
                        crucial=True)
            Logger.warn(traceback.format_exc())

        # todo: Some delay is needed for GE to submit task to a new host.
        #  Probably, we should check if some jobs is scheduled on the host and release the block only after that.
        #  On the other hand, some jobs may finish between our checks so the program may stuck until host is filled
        #  with some task.

    def _launch_additional_worker(self, instance, owner):
        Logger.info('Launching additional worker (%s)...' % instance)
        instance_dynamic_launch_params = {
            self.owner_param_name: owner
        }
        pipe_run_command = 'pipe run --yes --quiet ' \
                           '--instance-disk %s ' \
                           '--instance-type %s ' \
                           '--docker-image %s ' \
                           '--cmd-template "%s" ' \
                           '--parent-id %s ' \
                           '--price-type %s ' \
                           '--region-id %s ' \
                           'cluster_role worker ' \
                           'cluster_role_type additional ' \
                           '%s ' \
                           '%s' \
                           % (self.instance_disk, instance, self.instance_image, self.cmd_template, self.parent_run_id,
                              self._pipe_cli_price_type(self.price_type), self.region_id,
                              self._parameters_str(self.instance_launch_params),
                              self._parameters_str(instance_dynamic_launch_params))
        run_id = int(self.executor.execute_to_lines(pipe_run_command)[0])
        Logger.info('Additional worker #%s (%s) has been launched.' % (run_id, instance))
        return run_id

    def _parameters_str(self, instance_launch_params):
         return ' '.join('{} {}'.format(key, value) for key, value in instance_launch_params.items())

    def _pipe_cli_price_type(self, price_type):
        """
        Pipe-cli has a bit different price types notation. F.i. server-side "on_demand" price type becomes "on-demand"
        pipe-cli price type.
        """
        return price_type.replace('_', '-')

    def _retrieve_pod_name(self, run_id):
        Logger.info('Retrieving pod name of additional worker #%s...' % run_id)
        run = self.api.load_run(run_id)
        if 'podId' in run:
            name = run['podId']
            Logger.info('Additional worker #%s pod name %s has been retrieved.' % (run_id, name))
            return name
        else:
            error_msg = 'Additional worker #%s has no pod name specified.'
            Logger.warn(error_msg, crucial=True)
            raise ScalingError(error_msg)

    def _await_pod_initialization(self, run_id):
        Logger.info('Waiting for additional worker #%s pod to initialize...' % run_id)
        attempts = self.polling_timeout / self.polling_delay if self.polling_delay \
            else GridEngineScaleUpHandler._POLL_ATTEMPTS
        while attempts != 0:
            run = self.api.load_run(run_id)
            if run.get('status', 'RUNNING') != 'RUNNING':
                error_msg = 'Additional worker #%s is not running. Probably it has failed.' % run_id
                Logger.warn(error_msg, crucial=True)
                raise ScalingError(error_msg)
            if 'podIP' in run:
                pod = KubernetesPod(ip=run['podIP'], name=run['podId'])
                Logger.info('Additional worker #%s pod has started: %s (%s).' % (run_id, pod.name, pod.ip))
                return pod
            Logger.info('Additional worker #%s pod initialization hasn\'t finished yet. Only %s attempts remain left.'
                        % (run_id, attempts))
            attempts -= 1
            time.sleep(self.polling_delay)
        error_msg = 'Additional worker #%s pod hasn\'t started after %s seconds.' % (run_id, self.polling_timeout)
        Logger.warn(error_msg, crucial=True)
        raise ScalingError(error_msg)

    def _add_worker_to_master_hosts(self, pod):
        Logger.info('Adding host %s (%s) to hosts...' % (pod.name, pod.ip))
        self.executor.execute('add_to_hosts "%s" "%s"' % (pod.name, pod.ip))

    def _await_worker_initialization(self, run_id):
        Logger.info('Waiting for additional worker #%s to initialize...' % run_id)
        attempts = self.polling_timeout / self.polling_delay if self.polling_delay \
            else GridEngineScaleUpHandler._POLL_ATTEMPTS
        while attempts > 0:
            run = self.api.load_run(run_id)
            if run.get('status', 'RUNNING') != 'RUNNING':
                error_msg = 'Additional worker #%s is not running. Probably it has failed.' % run_id
                Logger.warn(error_msg, crucial=True)
                raise ScalingError(error_msg)
            if run['initialized']:
                Logger.info('Additional worker #%s has been marked as initialized.' % run_id)
                Logger.info('Checking additional worker #%s grid engine initialization status...' % run_id)
                run_sge_tasks = self.api.load_task(run_id, 'SGEWorkerSetup')
                if any(run_sge_task.get('status') == 'SUCCESS' for run_sge_task in run_sge_tasks):
                    Logger.info('Additional worker #%s has been initialized.' % run_id)
                    return
            Logger.info('Additional worker #%s hasn\'t been initialized yet. Only %s attempts remain left.'
                        % (run_id, attempts))
            attempts -= 1
            time.sleep(self.polling_delay)
        error_msg = 'Additional worker #%s hasn\'t been initialized after %s seconds.' % (run_id, self.polling_timeout)
        Logger.warn(error_msg, crucial=True)
        raise ScalingError(error_msg)

    def _enable_worker_in_grid_engine(self, pod):
        Logger.info('Enabling additional worker %s in grid engine...' % pod.name)
        attempts = self.ge_polling_timeout / self.polling_delay if self.polling_delay \
            else GridEngineScaleUpHandler._GE_POLL_ATTEMPTS
        while attempts > 0:
            try:
                self.grid_engine.enable_host(pod.name)
                Logger.info('Additional worker %s has been enabled in grid engine.' % pod.name)
                self.host_storage.update_hosts_activity([pod.name], self.clock.now())
                return
            except Exception as e:
                Logger.warn('Additional worker %s enabling in grid engine has failed '
                            'with only %s attempts remain left: %s.'
                            % (pod.name, attempts, str(e)))
                attempts -= 1
                time.sleep(self.polling_delay)
        error_msg = 'Additional worker %s hasn\'t been enabled in grid engine after %s seconds.' \
                    % (pod.name, self.ge_polling_timeout)
        Logger.warn(error_msg, crucial=True)
        raise ScalingError(error_msg)


class GridEngineScaleDownHandler:

    def __init__(self, cmd_executor, grid_engine, default_hostfile):
        """
        Grid engine scale down handler.

        Manages additional workers scaling down.

        :param cmd_executor: Cmd executor.
        :param grid_engine: Grid engine client.
        :param default_hostfile: Default host file location.
        """
        self.executor = cmd_executor
        self.grid_engine = grid_engine
        self.default_hostfile = default_hostfile

    def scale_down(self, child_host):
        """
        Scales down an additional worker.

        It stops the corresponding run, removes it from the GE cluster configuration and
        removes host from master /etc/hosts and self.default_hostfile.

        :param child_host: Host name of an additional worker to be scaled down.
        :return: True if the run stopping was successful, False otherwise.
        """
        Logger.info('Disabling additional worker %s...' % child_host)
        self.grid_engine.disable_host(child_host)
        jobs = self.grid_engine.get_jobs()
        disabled_host_jobs = [job for job in jobs if child_host in job.hosts]
        if disabled_host_jobs:
            Logger.warn('Disabled additional worker %s has %s associated jobs. Scaling down is interrupted.'
                        % (child_host, len(disabled_host_jobs)))
            Logger.info('Enable additional worker %s again.' % child_host)
            self.grid_engine.enable_host(child_host)
            return False
        self._remove_host_from_grid_engine_configuration(child_host)
        self._stop_run(child_host)
        self._remove_host_from_hosts(child_host)
        Logger.info('Additional worker %s has been scaled down.' % child_host, crucial=True)
        return True

    def _remove_host_from_grid_engine_configuration(self, host):
        Logger.info('Removing additional worker %s from GE cluster configuration...' % host)
        self.grid_engine.delete_host(host)
        Logger.info('Additional worker %s was removed from GE cluster configuration.' % host)

    def _stop_run(self, host):
        run_id = self._get_run_id_from_host(host)
        Logger.info('Stopping run #%s...' % run_id)
        self.executor.execute('pipe stop --yes %s' % run_id)
        Logger.info('Run #%s was stopped.' % run_id)

    def _get_run_id_from_host(self, host):
        host_elements = host.split('-')
        return host_elements[len(host_elements) - 1]

    def _remove_host_from_hosts(self, host):
        Logger.info('Removing host %s from hosts...' % host)
        self.executor.execute('remove_from_hosts "%s"' % host)


class ThreadSafeHostStorage:

    def __init__(self, storage):
        """
        Thread safe host storage.

        Works as a thread safe decorator for an underlying storage.
        """
        self._storage = storage
        self._lock = threading.RLock()

    @synchronized
    def add_host(self, host):
        return self._storage.add_host(host)

    @synchronized
    def remove_host(self, host):
        return self._storage.remove_host(host)

    @synchronized
    def update_running_jobs_host_activity(self, running_jobs, activity_timestamp):
        return self._storage.update_running_jobs_host_activity(running_jobs, activity_timestamp)

    @synchronized
    def update_hosts_activity(self, hosts, timestamp):
        return self._storage.update_hosts_activity(hosts, timestamp)

    @synchronized
    def get_hosts_activity(self, hosts):
        return self._storage.get_hosts_activity(hosts)

    @synchronized
    def load_hosts(self):
        return self._storage.load_hosts()

    @synchronized
    def clear(self):
        return self._storage.clear()


class MemoryHostStorage:

    def __init__(self):
        """
        In memory additional hosts storage.

        Contains the hostname along with the time of the last activity on it.
        Additional hosts details are lost on grid engine autoscaler restart.
        """
        self._storage = dict()
        self.clock = Clock()

    def add_host(self, host):
        if host in self._storage:
            raise ScalingError('Host with name \'%s\' is already in the host storage' % host)
        self._storage[host] = self.clock.now()

    def remove_host(self, host):
        self._validate_existence(host)
        self._storage.pop(host)

    def update_running_jobs_host_activity(self, running_jobs, activity_timestamp):
        active_hosts = set()
        for job in running_jobs:
            active_hosts.update(job.hosts)
        if active_hosts:
            self.update_hosts_activity(active_hosts, activity_timestamp)

    def update_hosts_activity(self, hosts, timestamp):
        for host in hosts:
            if host in self._storage:
                self._storage[host] = timestamp

    def get_hosts_activity(self, hosts):
        hosts_activity = {}
        for host in hosts:
            self._validate_existence(host)
            hosts_activity[host] = self._storage[host]
        return hosts_activity

    def load_hosts(self):
        return list(self._storage.keys())

    def clear(self):
        self._storage = dict()

    def _validate_existence(self, host):
        if host not in self._storage:
            raise ScalingError('Host with name \'%s\' doesn\'t exist in the host storage' % host)


class FileSystemHostStorage:
    _REPLACE_FILE = 'echo "%(content)s" > %(file)s_MODIFIED; ' \
                    'mv %(file)s_MODIFIED %(file)s'
    _DATETIME_FORMAT = '%m/%d/%Y %H:%M:%S'
    _VALUE_BREAKER = '|'
    _LINE_BREAKER = '\n'

    def __init__(self, cmd_executor, storage_file, clock=Clock()):
        """
        File system additional hosts storage.

        Contains the hostname along with the time of the last activity on it.
        It uses file system to persist all hosts.
        Additional hosts details are persisted between grid engine autoscaler restarts.

        :param cmd_executor: Cmd executor.
        :param storage_file: File to store hosts into.
        """
        self.executor = cmd_executor
        self.storage_file = storage_file
        self.clock = clock

    def add_host(self, host):
        """
        Persist host to storage.

        :param host: Additional host name.
        """
        hosts = self._load_hosts_stats()
        if host in hosts:
            raise ScalingError('Host with name \'%s\' is already in the host storage' % host)
        hosts[host] = self.clock.now()
        self._update_storage_file(hosts)

    def update_running_jobs_host_activity(self, running_jobs, activity_timestamp):
        active_hosts = set()
        for job in running_jobs:
            active_hosts.update(job.hosts)
        if active_hosts:
            self.update_hosts_activity(active_hosts, activity_timestamp)

    def update_hosts_activity(self, hosts, timestamp):
        latest_hosts_stats = self._load_hosts_stats()
        for host in hosts:
            if host in latest_hosts_stats:
                latest_hosts_stats[host] = timestamp
        self._update_storage_file(latest_hosts_stats)

    def get_hosts_activity(self, hosts):
        hosts_activity = {}
        latest_hosts_activity = self._load_hosts_stats()
        for host in hosts:
            self._validate_existence(host, latest_hosts_activity)
            hosts_activity[host] = latest_hosts_activity[host]
        return hosts_activity

    def remove_host(self, host):
        """
        Remove host from storage.

        :param host: Additional host name.
        """
        hosts = self._load_hosts_stats()
        self._validate_existence(host, hosts)
        hosts.pop(host)
        self._update_storage_file(hosts)

    def _update_storage_file(self, hosts):
        hosts_summary_table = []
        for host, last_activity in hosts.items():
            formatted_activity = last_activity.strftime(FileSystemHostStorage._DATETIME_FORMAT)
            hosts_summary_table.append(FileSystemHostStorage._VALUE_BREAKER.join([host, formatted_activity]))
        self.executor.execute(FileSystemHostStorage._REPLACE_FILE % {'content': '\n'.join(hosts_summary_table),
                                                                     'file': self.storage_file})

    def load_hosts(self):
        return list(self._load_hosts_stats().keys())

    def _load_hosts_stats(self):
        """
        Load all additional hosts from storage.

        :return: A set of all additional hosts.
        """
        if os.path.exists(self.storage_file):
            with open(self.storage_file) as file:
                hosts = {}
                for line in file.readlines():
                    stripped_line = line.strip().strip(FileSystemHostStorage._LINE_BREAKER)
                    if stripped_line:
                        host_stats = stripped_line.strip().split(FileSystemHostStorage._VALUE_BREAKER)
                        if host_stats:
                            hostname = host_stats[0]
                            last_activity = datetime.strptime(host_stats[1], FileSystemHostStorage._DATETIME_FORMAT)
                            hosts[hostname] = last_activity
                return hosts
        else:
            return {}

    def clear(self):
        self._update_storage_file({})

    def _validate_existence(self, host, hosts_dict):
        if host not in hosts_dict:
            raise ScalingError('Host with name \'%s\' doesn\'t exist in the host storage' % host)


class GridEngineAutoscaler:

    def __init__(self, grid_engine, cmd_executor, scale_up_orchestrator, scale_down_handler, host_storage, scale_up_timeout,
                 scale_down_timeout, max_additional_hosts, idle_timeout=30, clock=Clock()):
        """
        Grid engine autoscaler.

        It scales up additional workers if some jobs are waiting in grid engine queue
        for more than the given time internal.

        It scales down existing additional workers if there are no waiting jobs in grid engine queue
        and there were no new jobs for the given time interval.

        :param grid_engine: Grid engine.
        :param cmd_executor: Cmd executor.
        :param scale_up_orchestrator: Scaling up orchestrator.
        :param scale_down_handler: Scaling down handler.
        :param host_storage: Additional hosts storage.
        :param scale_up_timeout: Maximum number of seconds job could wait in queue
        before autoscaler will scale up the cluster.
        :param scale_down_timeout: Maximum number of seconds the waiting queue could be empty
        before autoscaler will scale down the cluster.
        :param max_additional_hosts: Maximum number of additional hosts that autoscaler can launch.
        :param clock: Clock.
        :param idle_timeout: Maximum number of seconds a host could wait for a new job before getting scaled-down.
        """
        self.grid_engine = grid_engine
        self.executor = cmd_executor
        self.scale_up_orchestrator = scale_up_orchestrator
        self.scale_down_handler = scale_down_handler
        self.host_storage = host_storage
        self.scale_up_timeout = timedelta(seconds=scale_up_timeout)
        self.scale_down_timeout = timedelta(seconds=scale_down_timeout)
        self.max_additional_hosts = max_additional_hosts
        self.clock = clock

        self.latest_running_job = None
        self.idle_timeout = timedelta(seconds=idle_timeout)

    def scale(self):
        now = self.clock.now()
        Logger.info('Start scaling step at %s.' % now)
        additional_hosts = self.host_storage.load_hosts()
        Logger.info('There are %s/%s additional workers.' % (len(additional_hosts), self.max_additional_hosts))
        updated_jobs = self.grid_engine.get_jobs()
        running_jobs = [job for job in updated_jobs if job.state == GridEngineJobState.RUNNING]
        pending_jobs = self._filter_pending_job(updated_jobs)
        if running_jobs:
            self.host_storage.update_running_jobs_host_activity(running_jobs, now)
            self.latest_running_job = sorted(running_jobs, key=lambda job: job.datetime, reverse=True)[0]
        if pending_jobs:
            Logger.info('There are %s waiting jobs.' % len(pending_jobs))
            expiration_datetimes = [job.datetime + self.scale_up_timeout for job in pending_jobs]
            expired_jobs = [job for index, job in enumerate(pending_jobs) if now >= expiration_datetimes[index]]
            if expired_jobs:
                Logger.info('There are %s waiting jobs that are in queue for more than %s seconds. '
                            'Scaling up is required.' % (len(expired_jobs), self.scale_up_timeout.seconds))
                if len(additional_hosts) < self.max_additional_hosts:
                    Logger.info('There are %s/%s additional workers. Scaling up will be performed.' %
                                (len(additional_hosts), self.max_additional_hosts))
                    resource_demands = self.grid_engine.get_resource_demands(pending_jobs)
                    resource_demand = functools.reduce(ComputeResource.add, resource_demands)
                    Logger.info('Waiting jobs require: '
                                '{cpu} cpus, {gpu} gpus, {mem} mem, {disk} disk.'
                                .format(cpu=resource_demand.cpu, gpu=resource_demand.gpu,
                                        mem=resource_demand.memory, disk=resource_demand.disk))
                    remaining_additional_hosts = self.max_additional_hosts - len(additional_hosts)
                    self.scale_up(resource_demands, remaining_additional_hosts)
                else:
                    Logger.info('There are %s/%s additional workers. Scaling up is aborted.' %
                                (len(additional_hosts), self.max_additional_hosts))
                    Logger.info('Probable deadlock situation observed. Scaling down will be attempted.')
                    self._scale_down(running_jobs, additional_hosts)
            else:
                Logger.info('There are no waiting jobs that are in queue for more than %s seconds. '
                            'Scaling up is not required.' % self.scale_up_timeout.seconds)
        else:
            Logger.info('There are no waiting jobs.')
            if self.latest_running_job:
                Logger.info('Latest started job with id %s has started at %s.' %
                            (self.latest_running_job.id, self.latest_running_job.datetime))
                if now >= self.latest_running_job.datetime + self.scale_down_timeout:
                    Logger.info('Latest job started more than %s seconds ago. Scaling down is required.' %
                                self.scale_down_timeout.seconds)
                    self._scale_down(running_jobs, additional_hosts, now)
                else:
                    Logger.info('Latest job started less than %s seconds. '
                                'Scaling down is not required.' % self.scale_down_timeout.seconds)
            else:
                Logger.info('There are no previously running jobs. Scaling down is required.')
                self._scale_down(running_jobs, additional_hosts, now)
        Logger.info('Finish scaling step at %s.' % self.clock.now())
        post_scale_additional_hosts = self.host_storage.load_hosts()
        Logger.info('There are %s/%s additional workers.'
                    % (len(post_scale_additional_hosts), self.max_additional_hosts))

    def _filter_pending_job(self, updated_jobs):
        # kill jobs that are pending and can't be satisfied with requested resource
        # f.i. we have only 3 instance max and the biggest possible type has 10 cores but job requests 40 coresf
        pending_jobs = [job for job in updated_jobs if job.state == GridEngineJobState.PENDING]
        if not pending_jobs:
            return []
        valid_pending_jobs = []
        invalid_pending_jobs = []
        Logger.info('Validate %s pending jobs.' % len(pending_jobs))
        for pending_job in pending_jobs:
            if not self.grid_engine.is_job_valid(pending_job):
                invalid_pending_jobs.append(pending_job)
            else:
                valid_pending_jobs.append(pending_job)
        if invalid_pending_jobs:
            Logger.warn('The following jobs cannot be satisfied with the requested resources '
                        'and therefore they will be rejected: %s'
                        % ', '.join('%s (%s cpu)' % (job.id, job.slots) for job in invalid_pending_jobs),
                        crucial=True)
            self.grid_engine.kill_jobs(invalid_pending_jobs)
        Logger.info('Pending jobs validation has finished.')
        return valid_pending_jobs

    def scale_up(self, resource_demands, remaining_additional_hosts):
        """
        Scales up a new additional worker.
        """
        Logger.info('Start grid engine SCALING UP.')
        self.scale_up_orchestrator.scale_up(resource_demands, remaining_additional_hosts)

    def _scale_down(self, running_jobs, additional_hosts, scaling_period_start=None):
        active_hosts = set([host for job in running_jobs for host in job.hosts])
        inactive_additional_hosts = [host for host in additional_hosts if host not in active_hosts]
        if inactive_additional_hosts:
            Logger.info('There are %s inactive additional workers.' % len(inactive_additional_hosts))
            if scaling_period_start:
                idle_additional_hosts = self._filter_valid_idle_hosts(inactive_additional_hosts, scaling_period_start)
                Logger.info('There are %s idle additional workers.' % len(idle_additional_hosts))
                inactive_additional_hosts = idle_additional_hosts
        if inactive_additional_hosts:
            Logger.info('Scaling down will be performed.')
            # TODO: here we always choose weakest host, even if all hosts are inactive and we can drop strongest one firstly
            # TODO in order to safe some money
            inactive_additional_host = self.grid_engine.get_host_to_scale_down(inactive_additional_hosts)
            succeed = self.scale_down(inactive_additional_host)
            if succeed:
                self.host_storage.remove_host(inactive_additional_host)
        else:
            Logger.info('There are no additional workers to scale down.')

    def _filter_valid_idle_hosts(self, inactive_host_candidates, scaling_period_start):
        inactive_hosts = []
        hosts_activity = self.host_storage.get_hosts_activity(inactive_host_candidates)
        for host, last_activity in hosts_activity.items():
            if scaling_period_start >= last_activity + self.idle_timeout:
                inactive_hosts.append(host)
        return inactive_hosts

    def scale_down(self, child_host):
        """
        Scales down an additional worker.

        :param child_host: Host name of an additional worker to be scaled down.
        :return: True if the worker was scaled down, False otherwise.
        """
        Logger.info('Start grid engine SCALING DOWN for %s host.' % child_host)
        return self.scale_down_handler.scale_down(child_host)


class GridEngineWorkerValidator:
    _STOP_RUN = 'pipe stop --yes %s'
    _SHOW_RUN_STATUS = 'pipe view-runs %s | grep Status | awk \'{print $2}\''
    _RUNNING_STATUS = 'RUNNING'

    def __init__(self, cmd_executor, api, host_storage, grid_engine, scale_down_handler):
        """
        Grid engine worker validator.

        The reason why validator exists is that some additional hosts may be broken due to different circumstances.
        F.e. autoscaler has failed while configuring additional host so it is partly configured and has to be stopped.
        F.e. a spot worker instance was preempted and it has to be removed from its autoscaled cluster.

        :param grid_engine: Grid engine.
        :param api: Cloud pipeline client.
        :param cmd_executor: Cmd executor.
        :param host_storage: Additional hosts storage.
        :param scale_down_handler: Scale down handler.
        """
        self.grid_engine = grid_engine
        self.api = api
        self.executor = cmd_executor
        self.host_storage = host_storage
        self.scale_down_handler = scale_down_handler

    def validate_hosts(self):
        """
        Finds and removes any additional hosts which aren't valid execution hosts in GE or active runs.
        """
        hosts = self.host_storage.load_hosts()
        Logger.info('Validate %s additional workers.' % len(hosts))
        invalid_hosts = []
        for host in hosts:
            run_id = self.scale_down_handler._get_run_id_from_host(host)
            if (not self.grid_engine.is_valid(host)) or (not self._is_running(run_id)):
                invalid_hosts.append((host, run_id))
        for host, run_id in invalid_hosts:
            Logger.warn('Invalid additional host %s was found. It will be downscaled.' % host, crucial=True)
            self._try_stop_worker(run_id)
            self._try_disable_worker(host, run_id)
            self._try_kill_invalid_host_jobs(host)
            self.grid_engine.delete_host(host, skip_on_failure=True)
            self._remove_worker_from_hosts(host)
            self.host_storage.remove_host(host)
        Logger.info('Additional hosts validation has finished.')

    def _is_running(self, run_id):
        try:
            run_info = self.api.load_run(run_id)
            status = run_info.get('status', 'not found').strip().upper()
            if status == self._RUNNING_STATUS:
                return True
            Logger.warn('Additional worker #%s status is not %s but %s.'
                        % (run_id, self._RUNNING_STATUS, status))
            return False
        except APIError as e:
            Logger.warn('Additional worker #%s status retrieving has failed '
                        'and it is considered not running: %s' % (run_id, str(e)))
            return False
        except Exception as e:
            Logger.warn('Additional worker #%s status retrieving has failed '
                        'but it is temporary considered running: %s' % (run_id, str(e)))
            return True

    def _try_stop_worker(self, run_id):
        try:
            Logger.info('Stopping run #%s...' % run_id)
            self.executor.execute(GridEngineWorkerValidator._STOP_RUN % run_id)
        except:
            Logger.warn('Invalid additional worker run stopping has failed.')

    def _try_disable_worker(self, host, run_id):
        try:
            Logger.info('Disabling additional worker #%s in GE...' % run_id)
            self.grid_engine.disable_host(host)
        except:
            Logger.warn('Invalid additional worker disabling has failed.')

    def _try_kill_invalid_host_jobs(self, host):
        invalid_host_jobs = [job for job in self.grid_engine.get_jobs() if host in job.hosts]
        if invalid_host_jobs:
            self.grid_engine.kill_jobs(invalid_host_jobs, force=True)

    def _remove_worker_from_hosts(self, host):
        Logger.info('Removing additional worker %s from the well-known hosts.' % host)
        self.scale_down_handler._remove_host_from_hosts(host)


class GridEngineInstanceSelector:

    def select(self, demands):
        pass


class BackwardCompatibleInstanceSelector(GridEngineInstanceSelector):

    def __init__(self, instance_provider, free_cores, batch_size):
        """
        Backward compatible CPU capacity instance selector.

        Non batch autoscaling works the same way as in the previous versions of grid engine autoscaler.
        Batch autoscaling uses cpu capacity strategy to select instances.

        :param instance_provider: Cloud Pipeline instance provider.
        :param free_cores: Number of system reserved cpus on each cluster instance.
        :param batch_size: Scaling up batch size.
        """
        if batch_size > 1:
            self.instance_selector = CpuCapacityInstanceSelector(instance_provider, free_cores)
        else:
            self.instance_selector = NaiveCpuCapacityInstanceSelector(instance_provider, free_cores)

    def select(self, demands):
        return self.instance_selector.select(demands)


class NaiveCpuCapacityInstanceSelector(GridEngineInstanceSelector):

    def __init__(self, instance_provider, free_cores):
        """
        Naive CPU capacity instance selector.

        CPU capacity is a number of job CPU requirements a single instance can process.
        If a bigger instance can process more job CPU requirements then it will be selected.
        Handles resource demands as if they were fractional.

        :param instance_provider: Cloud Pipeline instance provider.
        :param free_cores: Number of system reserved cpus on each cluster instance.
        """
        self.instance_provider = instance_provider
        self.free_cores = free_cores
        self.instance_selector = CpuCapacityInstanceSelector(instance_provider, free_cores)

    def select(self, demands):
        fractional_demands = [demand if isinstance(demand, FractionalDemand)
                              else FractionalDemand(cpu=demand.cpu,
                                                    gpu=demand.gpu,
                                                    memory=demand.memory,
                                                    disk=demand.disk,
                                                    owner=demand.owner)
                              for demand in demands]
        return self.instance_selector.select(fractional_demands)


class CpuCapacityInstanceSelector(GridEngineInstanceSelector):

    def __init__(self, instance_provider, free_cores):
        """
        CPU capacity instance selector.

        CPU capacity is a number of job CPU requirements a single instance can process.
        If a bigger instance can process more job CPU requirements then it will be selected.

        :param instance_provider: Cloud Pipeline instance provider.
        :param free_cores: Number of system reserved cpus on each cluster instance.
        """
        self.instance_provider = instance_provider
        self.free_cores = free_cores

    def select(self, demands):
        instances = self.instance_provider.provide()
        remaining_demands = demands
        while remaining_demands:
            best_capacity = 0
            best_instance = None
            best_remaining_demands = None
            best_fulfilled_demands = None
            for instance in instances:
                supply = ResourceSupply(cpu=instance.cpu - self.free_cores, gpu=instance.gpu, memory=instance.memory)
                current_remaining_demands, current_fulfilled_demands = self._apply(remaining_demands, supply)
                current_fulfilled_demand = functools.reduce(ComputeResource.add, current_fulfilled_demands,
                                                            IntegralDemand())
                current_capacity = current_fulfilled_demand.cpu
                if current_capacity > best_capacity:
                    best_capacity = current_capacity
                    best_instance = instance
                    best_remaining_demands = current_remaining_demands
                    best_fulfilled_demands = current_fulfilled_demands
            remaining_demands = best_remaining_demands
            if not best_instance:
                raise ScalingError('No instances were found which satisfy any of the resource demands.')
            best_instance_owner = self._resolve_owner(best_fulfilled_demands)
            logging.info('Selecting %s instance using %s/%s cpus for %s user...'
                         % (best_instance.name, best_capacity, best_instance.cpu, best_instance_owner))
            yield InstanceDemand(best_instance, best_instance_owner)

    def _apply(self, demands, supply):
        remaining_supply = supply
        remaining_demands = []
        fulfilled_demands = []
        for i, demand in enumerate(demands):
            if not remaining_supply:
                remaining_demands.extend(demands[i:])
                break
            if isinstance(demand, IntegralDemand):
                current_remaining_supply, remaining_demand = remaining_supply.subtract(demand)
                if remaining_demand:
                    remaining_demands.append(demand)
                else:
                    fulfilled_demands.append(demand)
                    remaining_supply = current_remaining_supply
            elif isinstance(demand, FractionalDemand):
                current_remaining_supply, remaining_demand = remaining_supply.subtract(demand)
                if remaining_demand:
                    remaining_demands.append(remaining_demand)
                fulfilled_demand, _ = demand.subtract(remaining_demand)
                fulfilled_demands.append(fulfilled_demand)
                remaining_supply = current_remaining_supply
            else:
                raise ScalingError('Unsupported demand type %s.', type(demand))
        return remaining_demands, fulfilled_demands

    def _resolve_owner(self, demands):
        owner_cpus_counter = sum([Counter({demand.owner: demand.cpu}) for demand in demands], Counter())
        return owner_cpus_counter.most_common()[0][0]


class GridEngineWorkerRecord:

    def __init__(self, id, name, instance_type, started, stopped, has_insufficient_instance_capacity):
        self.id = id
        self.name = name
        self.instance_type = instance_type
        self.started = started
        self.stopped = stopped
        self.has_insufficient_instance_capacity = has_insufficient_instance_capacity


class GridEngineWorkerRecorder:

    def record(self, run_id):
        pass

    def get(self):
        pass

    def clear(self):
        pass


class CloudPipelineWorkerRecorder(GridEngineWorkerRecorder):

    def __init__(self, api, capacity=100):
        self._api = api
        self._records = []
        self._capacity = capacity
        self._datetime_format = '%Y-%m-%d %H:%M:%S.%f'

    def record(self, run_id):
        try:
            Logger.info('Recording details of additional worker #%s...' % run_id)
            run = self._api.load_run(run_id)
            initialize_node_tasks = self._api.load_task(run_id, 'InitializeNode')
            run_name = run.get('podId')
            run_started = self._to_datetime(run.get('startDate'))
            run_stopped = self._to_datetime(run.get('endDate'))
            instance_type = run.get('instance', {}).get('nodeType')
            has_insufficient_instance_capacity = self._has_insufficient_instance_capacity(run, initialize_node_tasks)
            record = GridEngineWorkerRecord(id=run_id, name=run_name, instance_type=instance_type,
                                            started=run_started, stopped=run_stopped,
                                            has_insufficient_instance_capacity=has_insufficient_instance_capacity)
            self._records.append(record)
            self._records = self._records[-self._capacity:]
        except KeyboardInterrupt:
            pass
        except Exception as e:
            Logger.warn('Recording details of additional worker #%s has failed due to %s.' % (run_id, str(e)),
                        crucial=True)
            Logger.warn(traceback.format_exc())

    def _to_datetime(self, run_started):
        if not run_started:
            return None
        return datetime.strptime(run_started, self._datetime_format)

    def _has_insufficient_instance_capacity(self, run, initialize_node_tasks):
        if initialize_node_tasks:
            initialize_node_task = initialize_node_tasks[-1]
            if initialize_node_task.get('status') == 'FAILURE' \
                    and 'InsufficientInstanceCapacity' in initialize_node_task.get('logText', ''):
                Logger.warn('Insufficient instance capacity detected for %s instance type'
                            % run.get('instance', {}).get('nodeType'))
                return True
        return False

    def get(self):
        return list(self._records)

    def clear(self):
        self._records = []


class GridEngineInstanceProvider:

    def provide(self):
        pass


class DescendingInstanceProvider(GridEngineInstanceProvider):

    def __init__(self, inner):
        self._inner = inner

    def provide(self):
        return sorted(self._inner.provide(),
                      key=lambda instance: instance.cpu,
                      reverse=True)


class AvailableInstanceProvider(GridEngineInstanceProvider):

    def __init__(self, inner, worker_recorder, unavailability_delay, clock):
        self._inner = inner
        self._worker_recorder = worker_recorder
        self._unavailability_delay = timedelta(seconds=unavailability_delay)
        self._clock = clock

    def provide(self):
        allowed_instances = self._inner.provide()
        available_instances = list(self._get_available(allowed_instances))
        if available_instances:
            return available_instances
        Logger.warn('There are no available instance types. Trying to use all allowed instance types...')
        return allowed_instances

    def _get_available(self, instances):
        for instance in instances:
            if self._is_available(instance.name):
                yield instance
            else:
                Logger.warn('Circuit breaking %s instance type because it is unavailable...' % instance.name)

    def _is_available(self, instance_type):
        now = self._clock.now()
        unavailability_expiration = now - self._unavailability_delay
        unavailability = None
        for record in self._worker_recorder.get():
            if record.instance_type != instance_type:
                continue
            if record.has_insufficient_instance_capacity:
                unavailability = record.stopped
        return not unavailability or unavailability < unavailability_expiration


class SizeLimitingInstanceProvider(GridEngineInstanceProvider):

    def __init__(self, inner, max_instance_cores):
        self.inner = inner
        self.instance_cores = max_instance_cores

    def provide(self):
        return [instance for instance in self.inner.provide()
                if instance.cpu <= self.instance_cores]


class FamilyInstanceProvider(GridEngineInstanceProvider):

    def __init__(self, inner, instance_cloud_provider, instance_family):
        self.inner = inner
        self.instance_cloud_provider = instance_cloud_provider
        self.instance_family = instance_family

    def provide(self):
        return sorted([instance for instance in self.inner.provide()
                       if self._is_part_of_family(instance.name)],
                      key=lambda instance: instance.cpu)

    def _is_part_of_family(self, instance_type):
        return extract_family_from_instance_type(self.instance_cloud_provider, instance_type) == self.instance_family


class DefaultInstanceProvider(GridEngineInstanceProvider):

    def __init__(self, inner, instance_type):
        self.inner = inner
        self.instance_type = instance_type

    def provide(self):
        return [instance for instance in self.inner.provide()
                if instance.name == self.instance_type]


class CloudPipelineInstanceProvider(GridEngineInstanceProvider):

    def __init__(self, pipe, region_id, price_type):
        self.pipe = pipe
        self.region_id = region_id
        self.price_type = price_type

    def provide(self):
        allowed_instance_types = self.pipe.get_allowed_instance_types(self.region_id, self.price_type == 'spot')
        docker_instance_types = allowed_instance_types['cluster.allowed.instance.types.docker']
        return [Instance.from_cp_response(instance) for instance in docker_instance_types]


def extract_family_from_instance_type(cloud_provider, instance_type):
    if cloud_provider == CloudProvider.aws():
        search = re.search('^(\w+)\..*', instance_type)
        return search.group(1) if search else None
    elif cloud_provider == CloudProvider.gcp():
        search = re.search('^\w\d\-(\w+)-.*', instance_type)
        return search.group(1) if search else None
    elif cloud_provider == CloudProvider.azure():
        # will return Bms for Standard_B1ms or Dsv3 for Standard_D2s_v3 instance types
        search = re.search('^([a-zA-Z]+)\d+(.*)', instance_type.split('_', 1)[1].replace('_', ''))
        return search.group(1) + search.group(2) if search else None
    else:
        return None


class CloudProvider:

    ALLOWED_VALUES = ['AWS', 'GCP', 'AZURE']

    def __init__(self, value):
        if value in CloudProvider.ALLOWED_VALUES:
            self.value = value
        else:
            raise ParsingError('Wrong CloudProvider value, only %s is available!' % CloudProvider.ALLOWED_VALUES)

    @staticmethod
    def aws():
        return CloudProvider('AWS')

    @staticmethod
    def gcp():
        return CloudProvider('GCP')

    @staticmethod
    def azure():
        return CloudProvider('AZURE')

    def __eq__(self, other):
        if not isinstance(other, CloudProvider):
            # don't attempt to compare against unrelated types
            return False
        return other.value == self.value

    def __repr__(self):
        return self.value


class AllocationRule:

    ALLOWED_VALUES = ['$pe_slots', '$fill_up', '$round_robin']

    def __init__(self, value):
        if value in AllocationRule.ALLOWED_VALUES:
            self.value = value
        else:
            raise ParsingError('Wrong AllocationRule value, only %s is available!' % AllocationRule.ALLOWED_VALUES)

    @staticmethod
    def pe_slots():
        return AllocationRule('$pe_slots')

    @staticmethod
    def fill_up():
        return AllocationRule('$fill_up')

    @staticmethod
    def round_robin():
        return AllocationRule('$round_robin')

    def __eq__(self, other):
        if not isinstance(other, AllocationRule):
            # don't attempt to compare against unrelated types
            return False
        return other.value == self.value


class GridEngineAutoscalingDaemon:

    def __init__(self, autoscaler, worker_validator, polling_timeout=5):
        """
        Grid engine autoscaling daemon.

        :param autoscaler: Autoscaler.
        :param worker_validator: Additional workers validator.
        :param polling_timeout: Autoscaler polling timeout - in seconds.
        """
        self.autoscaler = autoscaler
        self.worker_validator = worker_validator
        self.timeout = polling_timeout

    def start(self):
        Logger.info('Launching grid engine autoscaling daemon...')
        while True:
            try:
                time.sleep(self.timeout)
                self.worker_validator.validate_hosts()
                self.autoscaler.scale()
            except KeyboardInterrupt:
                Logger.warn('Manual stop of the autoscaler daemon.', crucial=True)
                break
            except Exception as e:
                Logger.warn('Scaling step has failed due to %s.' % str(e), crucial=True)
                Logger.warn(traceback.format_exc())


def make_dirs(path):
    """
    Creates directory and all intermediate parent directories. Does not fail if some of the directories already exist.
    Basically, it is python version of sh command "mkdir -p path".
    """
    try:
        os.makedirs(path)
    except OSError as exc:
        if exc.errno == errno.EEXIST and os.path.isdir(path):
            # Tail folder already exists
            pass
        else:
            raise


class CloudPipelineAPI:

    def __init__(self, pipe):
        """
        Cloud pipeline client.

        :param pipe: Cloud pipeline raw client.
        """
        self.pipe = pipe

    def retrieve_preference(self, preference, default_value):
        try:
            return self.pipe.get_preference(preference)['value']
        except:
            Logger.warn('Pipeline preference %s retrieving has failed. Using default value: %s.'
                        % (preference, default_value))
            return default_value

    def load_run(self, run_id):
        result = self._execute_request(str(self.pipe.api_url) + self.pipe.GET_RUN_URL.format(run_id))
        return result or {}

    def load_task(self, run_id, task):
        result = self._execute_request(str(self.pipe.api_url) + self.pipe.GET_TASK_URL.format(run_id, task))
        return result or []

    def _execute_request(self, url):
        count = 0
        exceptions = []
        while count < self.pipe.attempts:
            count += 1
            try:
                response = requests.get(url, headers=self.pipe.header, verify=False, timeout=self.pipe.connection_timeout)
                if response.status_code != 200:
                    raise HTTPError('API responded with http status %s.' % str(response.status_code))
                data = response.json()
                status = data.get('status')
                message = data.get('message')
                if not status:
                    raise APIError('API responded without any status.')
                if status != self.pipe.RESPONSE_STATUS_OK:
                    if message:
                        raise APIError('API responded with status %s and error message: %s.' % (status, message))
                    else:
                        raise APIError('API responded with status %s.' % status)
                return data.get('payload')
            except Exception as e:
                exceptions.append(e)
                Logger.warn('An error has occurred during request %s/%s to API: %s'
                            % (count, self.pipe.attempts, str(e)))
            time.sleep(self.pipe.timeout)
        err_msg = 'Exceeded maximum retry count %s for API request.' % self.pipe.attempts
        Logger.warn(err_msg)
        raise exceptions[-1]


def fetch_instance_launch_params(api, master_run_id, queue, hostlist):
    parent_run = api.load_run(master_run_id)
    master_system_params = {param.get('name'): param.get('resolvedValue') for param in parent_run.get('pipelineRunParameters', [])}
    system_launch_params_string = api.retrieve_preference('launch.system.parameters', default_value='[]')
    system_launch_params = json.loads(system_launch_params_string)
    launch_params = {}
    for launch_param in system_launch_params:
        param_name = launch_param.get('name')
        if launch_param.get('passToWorkers', False) and param_name in master_system_params:
            launch_params[param_name] = str(os.getenv(param_name, master_system_params.get(param_name)))
    launch_params.update({
        'CP_CAP_SGE': 'false',
        'CP_CAP_AUTOSCALE': 'false',
        'CP_CAP_AUTOSCALE_WORKERS': '0',
        'CP_DISABLE_RUN_ENDPOINTS': 'true',
        'CP_CAP_SGE_QUEUE_NAME': queue,
        'CP_CAP_SGE_HOSTLIST_NAME': hostlist,
        'cluster_role': 'worker',
        'cluster_role_type': 'additional'
    })
    return launch_params


def main():
    params = GridEngineParameters()
    pipeline_api = os.environ['API']
    master_run_id = os.environ['RUN_ID']
    default_hostfile = os.environ['DEFAULT_HOSTFILE']

    static_hosts_cores = int(os.getenv('CLOUD_PIPELINE_NODE_CORES', multiprocessing.cpu_count()))
    static_hosts_number = int(os.getenv('node_count', 0))
    autoscaling_hosts_number = int(os.getenv(params.autoscaling.autoscaling_hosts_number.name, 3))

    instance_cloud_provider = CloudProvider(os.getenv(params.autoscaling_advanced.instance_cloud_provider.name, os.environ['CLOUD_PROVIDER']))
    instance_region_id = os.getenv(params.autoscaling_advanced.instance_region_id.name, os.environ['CLOUD_REGION_ID'])
    instance_type = os.getenv(params.autoscaling.instance_type.name, os.environ['instance_size'])
    instance_disk = os.getenv(params.autoscaling.instance_disk.name, os.environ['instance_disk'])
    instance_image = os.getenv(params.autoscaling.instance_image.name, os.environ['docker_image'])
    instance_price_type = os.getenv(params.autoscaling.price_type.name, os.environ['price_type'])
    instance_cmd_template = os.getenv(params.autoscaling.cmd_template.name, 'sleep infinity')
    instance_owner_param = os.getenv(params.autoscaling_advanced.instance_owner_param.name, 'CP_CAP_AUTOSCALE_OWNER')

    hybrid_autoscale = os.getenv(params.autoscaling.hybrid_autoscale.name, 'false').strip().lower() == 'true'
    hybrid_instance_cores = int(os.getenv(params.autoscaling.hybrid_instance_cores.name, 0))
    hybrid_instance_family = os.getenv(params.autoscaling.hybrid_instance_family.name,
                                       extract_family_from_instance_type(instance_cloud_provider, instance_type))
    descending_autoscale = os.getenv(params.autoscaling.descending_autoscale.name, 'true').strip().lower() == 'true'

    scale_up_strategy = os.getenv(params.autoscaling.scale_up_strategy.name, 'cpu-capacity')
    scale_up_batch_size = int(os.getenv(params.autoscaling.scale_up_batch_size.name, 1))
    scale_up_polling_delay = int(os.getenv(params.autoscaling.scale_up_polling_delay.name, 10))
    scale_up_unavailability_delay = int(os.getenv(params.autoscaling.scale_up_unavailability_delay.name, 1800))

    scale_down_idle_timeout = int(os.getenv(params.autoscaling.scale_down_idle_timeout.name, 30))

    queue_name = os.getenv(params.queue.queue_name.name, 'main.q')
    queue_name_short = (queue_name if not queue_name.endswith('.q') else queue_name[:-2])
    queue_static = os.getenv(params.queue.queue_static.name, 'false').strip().lower() == 'true'
    queue_default = os.getenv(params.queue.queue_default.name, 'false').strip().lower() == 'true'
    hostlist_name = os.getenv(params.queue.hostlist_name.name, '@allhosts')
    hosts_free_cores = int(os.getenv(params.queue.hosts_free_cores.name, 0))
    master_cores = int(os.getenv(params.queue.master_cores.name, static_hosts_cores))
    master_cores = master_cores - hosts_free_cores if master_cores - hosts_free_cores > 0 else master_cores

    work_dir = os.getenv(params.autoscaling_advanced.work_dir.name, os.getenv('TMP_DIR', '/tmp'))
    log_dir = os.getenv(params.autoscaling.log_dir.name, os.getenv('LOG_DIR', '/var/log'))
    log_task = os.getenv(params.autoscaling_advanced.log_task.name, 'GridEngineAutoscaling-%s' % queue_name_short)
    log_verbose = os.getenv(params.autoscaling.log_verbose.name, 'false').strip().lower() == 'true'

    Logger.init(log_file=os.path.join(log_dir, '.autoscaler.%s.log' % queue_name),
                task=log_task, verbose=log_verbose)

    Logger.info('Initiating grid engine autoscaling...')

    # TODO: Replace all the usages of PipelineAPI raw client with an actual CloudPipelineAPI client
    pipe = PipelineAPI(api_url=pipeline_api, log_dir=os.path.join(log_dir, '.autoscaler.%s.pipe.log' % queue_name))
    api = CloudPipelineAPI(pipe=pipe)

    instance_launch_params = fetch_instance_launch_params(api, master_run_id, queue_name, hostlist_name)

    clock = Clock()
    cmd_executor = CmdExecutor()

    worker_recorder = CloudPipelineWorkerRecorder(api=api)
    cloud_instance_provider = CloudPipelineInstanceProvider(pipe=pipe, region_id=instance_region_id,
                                                            price_type=instance_price_type)
    default_instance_provider = DefaultInstanceProvider(inner=cloud_instance_provider, instance_type=instance_type)

    descending_instance = default_instance_provider.provide().pop()
    descending_instance_cores = descending_instance.cpu if descending_instance else 0
    descending_instance_type = descending_instance.name if descending_instance else ''
    descending_instance_family = extract_family_from_instance_type(instance_cloud_provider, descending_instance_type)

    if hybrid_autoscale and hybrid_instance_family:
        Logger.info('Using hybrid autoscaling of {} instances...'.format(hybrid_instance_family))
        instance_provider = FamilyInstanceProvider(inner=cloud_instance_provider,
                                                   instance_cloud_provider=instance_cloud_provider,
                                                   instance_family=hybrid_instance_family)
        if hybrid_instance_cores:
            Logger.info('Using instances with no more than {} cpus...'.format(hybrid_instance_cores))
            instance_provider = SizeLimitingInstanceProvider(inner=instance_provider,
                                                             max_instance_cores=hybrid_instance_cores)
        if scale_up_unavailability_delay:
            Logger.info('Using only available instances...')
            instance_provider = AvailableInstanceProvider(inner=instance_provider,
                                                          worker_recorder=worker_recorder,
                                                          unavailability_delay=scale_up_unavailability_delay,
                                                          clock=clock)
    elif descending_autoscale and descending_instance_family and descending_instance_cores:
        Logger.info('Using descending autoscaling of {} instances...'.format(descending_instance_type))
        instance_provider = FamilyInstanceProvider(inner=cloud_instance_provider,
                                                   instance_cloud_provider=instance_cloud_provider,
                                                   instance_family=descending_instance_family)
        if descending_instance_cores:
            Logger.info('Using instances with no more than {} cpus...'.format(descending_instance_cores))
            instance_provider = SizeLimitingInstanceProvider(inner=instance_provider,
                                                             max_instance_cores=descending_instance_cores)
        if scale_up_unavailability_delay:
            Logger.info('Using only available instances...')
            instance_provider = AvailableInstanceProvider(inner=instance_provider,
                                                          worker_recorder=worker_recorder,
                                                          unavailability_delay=scale_up_unavailability_delay,
                                                          clock=clock)
        instance_provider = DescendingInstanceProvider(inner=instance_provider)
    else:
        Logger.info('Using default autoscaling of {} instances...'.format(instance_type))
        instance_provider = default_instance_provider

    Logger.info('Using the following instance types:\n{}'
                .format('\n'.join('- {} ({} cpus, {} gpus)'.format(instance.name, instance.cpu, instance.gpu)
                                  for instance in instance_provider.provide())))

    if scale_up_strategy == 'cpu-capacity':
        Logger.info('Selecting instances using cpu capacity strategy...')
        instance_selector = CpuCapacityInstanceSelector(instance_provider=instance_provider,
                                                        free_cores=hosts_free_cores)
    elif scale_up_strategy == 'naive-cpu-capacity':
        Logger.info('Selecting instances using fractional cpu capacity strategy...')
        instance_selector = NaiveCpuCapacityInstanceSelector(instance_provider=instance_provider,
                                                             free_cores=hosts_free_cores)
    else:
        Logger.info('Selecting instances using default strategy...')
        instance_selector = BackwardCompatibleInstanceSelector(instance_provider=instance_provider,
                                                               free_cores=hosts_free_cores,
                                                               batch_size=scale_up_batch_size)

    max_instance = sorted(instance_provider.provide(), key=lambda instance: instance.cpu).pop()
    max_instance_cores = max_instance.cpu - hosts_free_cores
    max_cluster_cores = max_instance_cores * autoscaling_hosts_number
    if queue_static:
        max_cluster_cores += master_cores + (static_hosts_cores - hosts_free_cores) * static_hosts_number

    grid_engine = GridEngine(cmd_executor=cmd_executor, max_instance_cores=max_instance_cores,
                             max_cluster_cores=max_cluster_cores, queue=queue_name, hostlist=hostlist_name,
                             queue_default=queue_default)
    host_storage = FileSystemHostStorage(cmd_executor=cmd_executor,
                                         storage_file=os.path.join(work_dir, '.autoscaler.%s.storage' % queue_name),
                                         clock=clock)
    host_storage = ThreadSafeHostStorage(host_storage)
    scale_up_timeout = int(api.retrieve_preference('ge.autoscaling.scale.up.timeout', default_value=30))
    scale_down_timeout = int(api.retrieve_preference('ge.autoscaling.scale.down.timeout', default_value=30))
    scale_up_polling_timeout = int(api.retrieve_preference('ge.autoscaling.scale.up.polling.timeout',
                                                           default_value=900))
    scale_up_handler = GridEngineScaleUpHandler(cmd_executor=cmd_executor, api=api, grid_engine=grid_engine,
                                                host_storage=host_storage,
                                                parent_run_id=master_run_id, default_hostfile=default_hostfile,
                                                instance_disk=instance_disk, instance_image=instance_image,
                                                cmd_template=instance_cmd_template,
                                                price_type=instance_price_type, region_id=instance_region_id,
                                                queue=queue_name, hostlist=hostlist_name,
                                                owner_param_name=instance_owner_param,
                                                polling_delay=scale_up_polling_delay,
                                                polling_timeout=scale_up_polling_timeout,
                                                instance_launch_params=instance_launch_params,
                                                clock=clock)
    scale_up_orchestrator = GridEngineScaleUpOrchestrator(scale_up_handler=scale_up_handler, grid_engine=grid_engine,
                                                          host_storage=host_storage,
                                                          instance_selector=instance_selector,
                                                          worker_recorder=worker_recorder,
                                                          batch_size=scale_up_batch_size,
                                                          polling_delay=scale_up_polling_delay,
                                                          clock=clock)
    scale_down_handler = GridEngineScaleDownHandler(cmd_executor=cmd_executor, grid_engine=grid_engine,
                                                    default_hostfile=default_hostfile)
    worker_validator = GridEngineWorkerValidator(cmd_executor=cmd_executor, api=api, host_storage=host_storage,
                                                 grid_engine=grid_engine, scale_down_handler=scale_down_handler)
    autoscaler = GridEngineAutoscaler(grid_engine=grid_engine, cmd_executor=cmd_executor,
                                      scale_up_orchestrator=scale_up_orchestrator, scale_down_handler=scale_down_handler,
                                      host_storage=host_storage, scale_up_timeout=scale_up_timeout,
                                      scale_down_timeout=scale_down_timeout, max_additional_hosts=autoscaling_hosts_number,
                                      idle_timeout=scale_down_idle_timeout, clock=clock)
    daemon = GridEngineAutoscalingDaemon(autoscaler=autoscaler, worker_validator=worker_validator,
                                         polling_timeout=10)
    daemon.start()


if __name__ == '__main__':
    main()
