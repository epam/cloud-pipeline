# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import argparse
import errno
import logging
import os
from datetime import datetime, timedelta
from pipeline import PipelineAPI, Logger as CloudPipelineLogger
import subprocess
import time
import multiprocessing
import requests
import re
import sys
import json


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
    def init(cmd=True, task=None, log_file=None, verbose=False):
        if not cmd and (not task or not log_file):
            raise LoggingError('Arguments \'task\' and \'log_file\' should be specified if \'cmd\' is False.')
        Logger.task = task
        Logger.cmd = cmd
        Logger.verbose = verbose
        if cmd:
            logging.basicConfig(level=logging.DEBUG,
                                format='%(asctime)s %(message)s')
        else:
            make_dirs(os.path.dirname(log_file))
            logging.basicConfig(filename=log_file,
                                level=logging.INFO,
                                format='%(asctime)s %(message)s')

    @staticmethod
    def info(message, crucial=False, *args, **kwargs):
        logging.info(message, *args, **kwargs)
        if not Logger.cmd and (crucial or Logger.verbose):
            CloudPipelineLogger.info(message, task_name=Logger.task)

    @staticmethod
    def warn(message, crucial=False, *args, **kwargs):
        logging.warn(message, *args, **kwargs)
        if not Logger.cmd and (crucial or Logger.verbose):
            CloudPipelineLogger.warn(message, task_name=Logger.task)

    @staticmethod
    def success(message, crucial=True, *args, **kwargs):
        logging.info(message, *args, **kwargs)
        if not Logger.cmd and (crucial or Logger.verbose):
            CloudPipelineLogger.success(message, task_name=Logger.task)

    @staticmethod
    def fail(message, crucial=True, *args, **kwargs):
        logging.error(message, *args, **kwargs)
        if not Logger.cmd and (crucial or Logger.verbose):
            CloudPipelineLogger.fail(message, task_name=Logger.task)


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


class KubernetesPod:

    def __init__(self, ip, name):
        self.ip = ip
        self.name = name


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

    def __init__(self, id, name, user, state, datetime, hosts=None, slots=0, pe='local'):
        self.id = id
        self.name = name
        self.user = user
        self.state = state
        self.datetime = datetime
        self.hosts = hosts if hosts else []
        self.slots = slots
        self.pe = pe


class GridEngine:
    _MAIN_Q = os.getenv('CP_CAP_SGE_QUEUE_NAME', 'main.q')
    _ALL_HOSTS = '@allhosts'
    _DELETE_HOST = 'qconf -de %s'
    _SHOW_PARALLEL_ENVIRONMENTS = 'qconf -spl'
    _SHOW_PARALLEL_ENVIRONMENT_SLOTS = 'qconf -sp %s | grep "^slots" | awk \'{print $2}\''
    _REPLACE_PARALLEL_ENVIRONMENT_SLOTS = 'qconf -rattr pe slots %s %s'
    _SHOW_JOB_PARALLEL_ENVIRONMENT = 'qstat -j %s | grep "^parallel environment" | awk \'{print $3}\''
    _SHOW_JOB_PARALLEL_ENVIRONMENT_SLOTS = 'qstat -j %s | grep "^parallel environment" | awk \'{print $5}\''
    _SHOW_PE_ALLOCATION_RULE = 'qconf -sp %s | grep "^allocation_rule" | awk \'{print $2}\''
    _REMOVE_HOST_FROM_HOST_GROUP = 'qconf -dattr hostgroup hostlist %s %s'
    _REMOVE_HOST_FROM_QUEUE_SETTINGS = 'qconf -purge queue slots %s@%s'
    _SHUTDOWN_HOST_EXECUTION_DAEMON = 'qconf -ke %s'
    _REMOVE_HOST_FROM_ADMINISTRATIVE_HOSTS = 'qconf -dh %s'
    _QSTAT = 'qstat -f -u "*"'
    _SHOW_EXECUTION_HOSTS_SLOTS = 'qstat -f -u "*" | grep %s' % _MAIN_Q
    _QSTAT_DATETIME_FORMAT = '%m/%d/%Y %H:%M:%S'
    _QMOD_DISABLE = 'qmod -d %s@%s'
    _QMOD_ENABLE = 'qmod -e %s@%s'
    _SHOW_EXECUTION_HOST = 'qconf -se %s'
    _KILL_JOBS = 'qdel %s'
    _FORCE_KILL_JOBS = 'qdel -f %s'
    _SHOW_HOST_STATES = 'qstat -f | grep \'%s@%s\' | awk \'{print $6}\''
    _BAD_HOST_STATES = ['u', 'E', 'd']

    def __init__(self, cmd_executor, max_instance_cores, max_cluster_cores):
        self.cmd_executor = cmd_executor
        self.max_instance_cores = max_instance_cores
        self.max_cluster_cores = max_cluster_cores

    def get_jobs(self):
        """
        Executes command and parse its output. The expected output is something like the following:

            queuename                      qtype resv/used/tot. load_avg arch          states
            ---------------------------------------------------------------------------------
            main.q@pipeline-18033          BIP   0/2/2          0.06     lx-amd64
                 20 0.50000 sleep.sh   root         r     11/27/2019 14:48:59     2
            ---------------------------------------------------------------------------------
            main.q@pipeline-18031          BIP   0/2/2          0.46     lx-amd64
                 15 0.50000 sleep.sh   root         r     11/27/2019 11:47:40     2
            ############################################################################
             - PENDING JOBS - PENDING JOBS - PENDING JOBS - PENDING JOBS - PENDING JOBS
            ############################################################################
                 21 0.50000 sleep.sh   root         qw    11/27/2019 14:48:58     2

        :return: Grid engine jobs list.
        """
        lines = self.cmd_executor.execute_to_lines(GridEngine._QSTAT)
        if len(lines) == 0:
            return []
        jobs = {}
        current_host = None
        for line in lines:
            tokens = line.strip().split()
            # host line like: main.q@pipeline-18033          BIP   0/0/2          0.50     lx-amd64
            if tokens[0].startswith(self._MAIN_Q):
                current_host = self._parse_host(tokens[0])
            # job line: 15 0.50000 sleep.sh   root         r     11/27/2019 11:47:40     2
            elif tokens[0].isdigit():
                job_array = self._parse_array(tokens[8] if len(tokens) >= 9 else None)
                job_ids = [tokens[0] + "." + str(sub_id) for sub_id in job_array] if job_array else [tokens[0]]
                for job_id in job_ids:
                    if job_id in jobs:
                        job = jobs[job_id]
                        job.hosts.append(current_host)
                    else:
                        pe = self.get_job_parallel_environment(job_id)
                        job_slots = self.get_job_slots(job_id)
                        jobs[job_id] = GridEngineJob(
                            id=job_id,
                            name=tokens[2],
                            user=tokens[3],
                            state=GridEngineJobState.from_letter_code(tokens[4]),
                            datetime=self._parse_date("%s %s" % (tokens[5], tokens[6])),
                            hosts=[current_host] if current_host else [],
                            slots=job_slots,
                            pe=pe
                        )
            else:
                current_host = None
        return jobs.values()

    def _parse_date(self, date):
        return datetime.strptime(date, GridEngine._QSTAT_DATETIME_FORMAT)

    def _parse_host(self, queue_and_host):
        return queue_and_host.split('@')[1] if queue_and_host else None

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
        Logger.info('Validation of job #{job_id} allocation rule: {alloc_rule} job slots: {slots}'.format(
            job_id=job.id, alloc_rule=allocation_rule.value, slots=job.slots))
        if job.slots:
            if allocation_rule == AllocationRule.pe_slots():
                result = job.slots <= self.max_instance_cores
            elif allocation_rule in [AllocationRule.fill_up(), AllocationRule.round_robin()]:
                result = job.slots <= self.max_cluster_cores
        Logger.info('Validation of job #{job_id}: {res}'.format(job_id=job.id, res=result))
        return result

    def disable_host(self, host, queue=_MAIN_Q):
        """
        Disables host to prevent receiving new jobs from the given queue.
        This command does not abort currently running jobs.

        :param host: Host to be enabled.
        :param queue: Queue that host is a part of.
        """
        self.cmd_executor.execute(GridEngine._QMOD_DISABLE % (queue, host))

    def enable_host(self, host, queue=_MAIN_Q):
        """
        Enables host to make it available to receive new jobs from the given queue.

        :param host: Host to be enabled.
        :param queue: Queue that host is a part of.
        """
        self.cmd_executor.execute(GridEngine._QMOD_ENABLE % (queue, host))

    def increase_parallel_environment_slots(self, slots):
        """
        Increases the number of parallel environment slots.

        :param slots: Number of slots to append.
        """
        for pe in self.get_parallel_environments():
            pe_slots = self.get_parallel_environment_slots(pe)
            self.cmd_executor.execute(GridEngine._REPLACE_PARALLEL_ENVIRONMENT_SLOTS % (pe_slots + slots, pe))

    def decrease_parallel_environment_slots(self, slots):
        """
        Decreases the number of parallel environment slots.

        :param slots: Number of slots to subtract.
        """
        for pe in self.get_parallel_environments():
            pe_slots = self.get_parallel_environment_slots(pe)
            self.cmd_executor.execute(GridEngine._REPLACE_PARALLEL_ENVIRONMENT_SLOTS % (pe_slots - slots, pe))

    def get_parallel_environments(self):
        """
        Returns number of the parallel environment slots.

        """
        return self.cmd_executor.execute_to_lines(GridEngine._SHOW_PARALLEL_ENVIRONMENTS)

    def get_parallel_environment_slots(self, pe):
        """
        Returns number of the parallel environment slots.

        :param pe: Parallel environment to return number of slots for.
        """
        return int(self.cmd_executor.execute(GridEngine._SHOW_PARALLEL_ENVIRONMENT_SLOTS % pe).strip())

    def get_pe_allocation_rule(self, pe):
        """
        Returns allocation rule of the pe

        :param pe: Parallel environment to return allocation rule.
        """
        exec_result = self.cmd_executor.execute(GridEngine._SHOW_PE_ALLOCATION_RULE % pe)
        return AllocationRule(exec_result.strip()) if exec_result else AllocationRule.pe_slots()

    def get_job_parallel_environment(self, job_id):
        """
        Returns PE of the specific job.

        :param job_id: id of a SGE job
        """
        return str(self.cmd_executor.execute(GridEngine._SHOW_JOB_PARALLEL_ENVIRONMENT % job_id) or 'local').strip()

    def get_job_slots(self, job_id):
        """
        Returns number of slots of the specific job.

        :param job_id: id of a SGE job
        """
        return int(self.cmd_executor.execute(GridEngine._SHOW_JOB_PARALLEL_ENVIRONMENT_SLOTS % job_id) or 1)

    def delete_host(self, host, queue=_MAIN_Q, hostgroup=_ALL_HOSTS, skip_on_failure=False):
        """
        Completely deletes host from GE:
        1. Shutdown host execution daemon.
        2. Removes host from queue settings.
        3. Removes host from host group.
        4. Removes host from administrative hosts.
        5. Removes host from GE.

        :param host: Host to be removed.
        :param queue: Queue host is a part of.
        :param hostgroup: Host group queue uses.
        :param skip_on_failure: Specifies if the host killing should be continued even if some of
        the commands has failed.
        """
        self._shutdown_execution_host(host, skip_on_failure=skip_on_failure)
        self._remove_host_from_queue_settings(host, queue, skip_on_failure=skip_on_failure)
        self._remove_host_from_host_group(host, hostgroup, skip_on_failure=skip_on_failure)
        self._remove_host_from_administrative_hosts(host, skip_on_failure=skip_on_failure)
        self._remove_host_from_grid_engine(host, skip_on_failure=skip_on_failure)

    def get_resource_demand(self, expired_jobs):
        demand_slots = 0
        available_slots = self._get_available_slots()
        for job in expired_jobs:
            if self.get_pe_allocation_rule(job.pe) in [AllocationRule.round_robin(), AllocationRule.fill_up()]:
                if available_slots >= job.slots:
                    available_slots -= job.slots
                else:
                    demand_slots += job.slots - available_slots
                    available_slots = 0
            else:
                demand_slots += job.slots
        return ComputeResource(demand_slots)

    def get_host_to_scale_down(self, hosts):
        # choose the weakest one (map to number of CPU, sort in reverse order and get from top)
        # TODO: in the future other resources like RAM and GPU can be count here
        return sorted([(host, self.get_host_resource(host)) for host in hosts],
                      cmp=lambda h1, h2: h1[1].cpu - h2[1].cpu, reverse=True).pop()[0]

    def _get_available_slots(self):
        available_slots = 0
        # there should be lines like:  main.q@pipeline-18033          BIP   0/2/2          0.06     lx-amd64
        # and we are interested in 0/2/2 - slots status
        for line in self.cmd_executor.execute_to_lines(GridEngine._SHOW_EXECUTION_HOSTS_SLOTS):
            rsrv_used_total = line.strip().split()[2].split("/")
            available_slots += int(rsrv_used_total[2]) - int(rsrv_used_total[1]) - int(rsrv_used_total[0])
        return available_slots

    def get_host_resource(self, host):
        for line in self.cmd_executor.execute_to_lines(GridEngine._SHOW_EXECUTION_HOST % host):
            if "processors" in line:
                return ComputeResource(int(line.strip().split()[1]))

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

    def is_valid(self, host, queue=_MAIN_Q):
        """
        Validates host in GE checking corresponding execution host availability and its states.

        :param host: Host to be checked.
        :return: True if execution host is valid.
        """
        try:
            self.cmd_executor.execute_to_lines(GridEngine._SHOW_EXECUTION_HOST % host)
            host_states = self.cmd_executor.execute(GridEngine._SHOW_HOST_STATES % (queue, host)).strip()
            for host_state in host_states:
                if host_state in self._BAD_HOST_STATES:
                    Logger.warn('Execution host %s GE state is %s which makes host invalid.' % (host, host_states))
                    return False
            if host_states:
                Logger.warn('Execution host %s GE state is not empty: %s.' % (host, host_states))
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

    def __init__(self, cpu, gpu=0, memory=0, disk=0):
        self.cpu = cpu
        self.gpu = gpu
        self.memory = memory
        self.disk = disk


class CPInstance:

    def __init__(self, name, price_type, memory, gpu, cpu):
        self.name = name
        self.price_type = price_type
        self.memory = memory
        self.gpu = gpu
        self.cpu = cpu

    @staticmethod
    def from_cp_response(instance):
        return CPInstance(name=instance['name'],
                          price_type=instance['termType'],
                          cpu=int(instance['vcpu']),
                          gpu=int(instance['gpu']),
                          memory=int(instance['memory']))

class Clock:

    def __init__(self):
        pass

    def now(self):
        return datetime.now()


class GridEngineScaleUpHandler:
    _POLL_TIMEOUT = 900
    _POLL_ATTEMPTS = 60
    _POLL_DELAY = 10
    _GE_POLL_TIMEOUT = 60
    _GE_POLL_ATTEMPTS = 6

    def __init__(self, cmd_executor, api, grid_engine, host_storage, instance_helper, parent_run_id, default_hostfile, instance_disk,
                 instance_image, cmd_template, price_type, region_id, polling_timeout=_POLL_TIMEOUT, polling_delay=_POLL_DELAY,
                 ge_polling_timeout=_GE_POLL_TIMEOUT, instance_family=None, worker_launch_system_params=''):
        """
        Grid engine scale up implementation. It handles additional nodes launching and hosts configuration (/etc/hosts
        and self.default_hostfile).

        :param cmd_executor: Cmd executor.
        :param api: Cloud pipeline client.
        :param grid_engine: Grid engine client.
        :param host_storage: Additional hosts storage.
        :param instance_helper: Object to get information from CloupPipeline about instance types.
        :param parent_run_id: Additional nodes parent run id.
        :param default_hostfile: Default host file location.
        :param instance_disk: Additional nodes disk size.
        :param instance_image: Additional nodes docker image.
        :param cmd_template: Additional nodes cmd template.
        :param price_type: Additional nodes price type.
        :param region_id: Additional nodes Cloud Region id.
        :param polling_timeout: Kubernetes and Pipeline APIs polling timeout - in seconds.
        :param polling_delay: Polling delay - in seconds.
        :param ge_polling_timeout: Grid Engine polling timeout - in seconds.
        :param instance_family: Instance family for launching additional instance,
                e.g. c5 means that you can run instances like c5.large, c5.xlarge etc.
        """
        self.executor = cmd_executor
        self.api = api
        self.grid_engine = grid_engine
        self.host_storage = host_storage
        self.instance_helper = instance_helper
        self.parent_run_id = parent_run_id
        self.default_hostfile = default_hostfile
        self.instance_disk = instance_disk
        self.instance_image = instance_image
        self.cmd_template = cmd_template
        self.price_type = price_type
        self.region_id = region_id
        self.polling_timeout = polling_timeout
        self.polling_delay = polling_delay
        self.ge_polling_timeout = ge_polling_timeout
        self.instance_family = instance_family
        self.worker_launch_system_params = worker_launch_system_params

    def scale_up(self, resource):
        """
        Launches new pipeline and configures local hosts files.

        Notice that master hosts file is altered before an additional working node starts to add itself to GE
        configuration. Otherwise GE configuration will end up with 'can't resolve hostname'-like errors.

        Also notice that an additional worker host is manually enabled in GE only after its pipeline is initialized.
        This happens because additional workers is disabled in GE by default to prevent job submissions to not
        initialized pipeline.

        :return: Host name of the launched pipeline.
        """
        instance_to_run = self.instance_helper.select_instance(resource, self.price_type)
        Logger.info('The next instance is matched according to allowed: %s' % instance_to_run.name)
        run_id = self._launch_additional_worker(instance_to_run.name)
        host = self._retrieve_pod_name(run_id)
        self.host_storage.add_host(host)
        pod = self._await_pod_initialization(run_id)
        self._add_worker_to_master_hosts(pod)
        self._await_worker_initialization(run_id)
        self._enable_worker_in_grid_engine(pod)
        self._increase_parallel_environment_slots(instance_to_run.cpu)
        Logger.info('Additional worker with host=%s and instance type=%s has been created.' % (pod.name, instance_to_run.name), crucial=True)

        # todo: Some delay is needed for GE to submit task to a new host.
        #  Probably, we should check if some jobs is scheduled on the host and release the block only after that.
        #  On the other hand, some jobs may finish between our checks so the program may stuck until host is filled
        #  with some task.

        return pod.name

    def _launch_additional_worker(self, instance):
        Logger.info('Launch additional worker.')
        Logger.info('Pass to worker the next parameters: {}'.format(self.worker_launch_system_params))
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
                           '%s' \
                           % (self.instance_disk, instance, self.instance_image, self.cmd_template, self.parent_run_id,
                              self._pipe_cli_price_type(self.price_type), self.region_id, self.worker_launch_system_params)
        run_id = int(self.executor.execute_to_lines(pipe_run_command)[0])
        Logger.info('Additional worker run id is %s.' % run_id)
        return run_id


    def _pipe_cli_price_type(self, price_type):
        """
        Pipe-cli has a bit different price types notation. F.i. server-side "on_demand" price type becomes "on-demand"
        pipe-cli price type.
        """
        return price_type.replace('_', '-')

    def _retrieve_pod_name(self, run_id):
        Logger.info('Retrieve pod name of additional worker with run_id=%s.' % run_id)
        run = self.api.load_run(run_id)
        if 'podId' in run:
            name = run['podId']
            Logger.info('Additional worker with run_id=%s and pod_name=%s has been retrieved.' % (run_id, name))
            return name
        else:
            error_msg = 'Worker with run_id=%s has no pod name specified.'
            Logger.warn(error_msg, crucial=True)
            raise ScalingError(error_msg)

    def _await_pod_initialization(self, run_id):
        Logger.info('Waiting for additional worker with run_id=%s pod to initialize.' % run_id)
        attempts = self.polling_timeout / self.polling_delay if self.polling_delay \
            else GridEngineScaleUpHandler._POLL_ATTEMPTS
        while attempts != 0:
            run = self.api.load_run(run_id)
            if run.get('status', 'RUNNING') != 'RUNNING':
                error_msg = 'Additional worker is not running. Probably it has failed.'
                Logger.warn(error_msg, crucial=True)
                raise ScalingError(error_msg)
            if 'podIP' in run:
                pod = KubernetesPod(ip=run['podIP'], name=run['podId'])
                Logger.info('Additional worker pod has started with ip=%s and name=%s.' % (pod.ip, pod.name))
                return pod
            Logger.info('Additional worker pod initialization hasn\'t finished yet. Only %s attempts remain left.'
                        % attempts)
            attempts -= 1
            time.sleep(self.polling_delay)
        error_msg = 'Pod with run_id=%s hasn\'t started after %s seconds.' % (run_id, self.polling_timeout)
        Logger.warn(error_msg, crucial=True)
        raise ScalingError(error_msg)

    def _add_worker_to_master_hosts(self, pod):
        self.executor.execute('add_to_hosts "%s" "%s"' % (pod.name, pod.ip))

    def _await_worker_initialization(self, run_id):
        Logger.info('Waiting for additional worker with run_id=%s to initialize.' % run_id)
        attempts = self.polling_timeout / self.polling_delay if self.polling_delay \
            else GridEngineScaleUpHandler._POLL_ATTEMPTS
        while attempts > 0:
            run = self.api.load_run(run_id)
            if run.get('status', 'RUNNING') != 'RUNNING':
                error_msg = 'Additional worker is not running. Probably it has failed.'
                Logger.warn(error_msg, crucial=True)
                raise ScalingError(error_msg)
            if run['initialized']:
                Logger.info('Additional worker with run_id=%s has been marked as initialized.' % run_id)
                Logger.info('Checking additional worker with run_id=%s grid engine initialization status.' % run_id)
                run_sge_tasks = self.api.load_task(run_id, 'SGEWorkerSetup')
                if any(run_sge_task.get('status') == 'SUCCESS' for run_sge_task in run_sge_tasks):
                    Logger.info('Additional worker with run_id=%s has been initialized.' % run_id)
                    return
            Logger.info('Additional worker with run_id=%s hasn\'t been initialized yet. Only %s attempts remain left.'
                        % (run_id, attempts))
            attempts -= 1
            time.sleep(self.polling_delay)
        error_msg = 'Additional worker hasn\'t been initialized after %s seconds.' % self.polling_timeout
        Logger.warn(error_msg, crucial=True)
        raise ScalingError(error_msg)

    def _enable_worker_in_grid_engine(self, pod):
        Logger.info('Enabling additional worker with host=%s in grid engine.' % pod.name)
        attempts = self.ge_polling_timeout / self.polling_delay if self.polling_delay \
            else GridEngineScaleUpHandler._GE_POLL_ATTEMPTS
        while attempts > 0:
            try:
                self.grid_engine.enable_host(pod.name)
                Logger.info('Additional worker with host=%s has been enabled in grid engine.' % pod.name)
                return
            except Exception as e:
                Logger.warn('Additional worker with host=%s enabling in grid engine has failed '
                            'with only %s attempts remain left: %s.'
                            % (pod.name, attempts, str(e)))
                attempts -= 1
                time.sleep(self.polling_delay)
        error_msg = 'Additional worker hasn\'t been enabled in grid engine after %s seconds.' % self.ge_polling_timeout
        Logger.warn(error_msg, crucial=True)
        raise ScalingError(error_msg)

    def _increase_parallel_environment_slots(self, slots_to_append):
        Logger.info('Increase number of parallel environment slots by %s.' % slots_to_append)
        self.grid_engine.increase_parallel_environment_slots(slots_to_append)
        Logger.info('Number of parallel environment slots was increased.')


class GridEngineScaleDownHandler:

    def __init__(self, cmd_executor, grid_engine, default_hostfile):
        """
        Grid engine scale down implementation. It handles grid engine host removal, hosts configuration (/etc/hosts
        and self.default_hostfile) and additional nodes stopping.

        :param cmd_executor: Cmd executor.
        :param grid_engine: Grid engine client.
        :param default_hostfile: Default host file location.
        """
        self.executor = cmd_executor
        self.grid_engine = grid_engine
        self.default_hostfile = default_hostfile

    def scale_down(self, child_host):
        """
        Kills pipeline, removes it from the GE cluster configuration.
        Also removes host from master /etc/hosts and self.default_hostfile.

        :param child_host: Host name of the pipeline to be killed.
        :return: True if the pipeline killing went well, False otherwise.
        """
        Logger.info('Disable additional worker with host=%s.' % child_host)
        self.grid_engine.disable_host(child_host)
        jobs = self.grid_engine.get_jobs()
        disabled_host_jobs = [job for job in jobs if child_host in job.hosts]
        if disabled_host_jobs:
            Logger.warn('Disabled additional worker with host=%s has %s associated jobs. Scaling down is interrupted.'
                        % (child_host, len(disabled_host_jobs)))
            Logger.info('Enable additional worker with host=%s again.' % child_host)
            self.grid_engine.enable_host(child_host)
            return False
        child_host_slots = self.grid_engine.get_host_resource(child_host).cpu
        self._remove_host_from_grid_engine_configuration(child_host)
        self._decrease_parallel_environment_slots(child_host_slots)
        self._stop_pipeline(child_host)
        self._remove_host_from_hosts(child_host)
        Logger.info('Additional worker with host=%s has been stopped.' % child_host, crucial=True)
        return True

    def _decrease_parallel_environment_slots(self, slots_to_remove):
        Logger.info('Decrease number of parallel environment slots by %s.' % slots_to_remove)
        self.grid_engine.decrease_parallel_environment_slots(slots_to_remove)
        Logger.info('Number of parallel environment slots was decreased.')

    def _remove_host_from_grid_engine_configuration(self, host):
        Logger.info('Remove additional worker with host=%s from GE cluster configuration.' % host)
        self.grid_engine.delete_host(host)
        Logger.info('Additional worker with host=%s was removed from GE cluster configuration.' % host)

    def _stop_pipeline(self, host):
        run_id = self._get_run_id_from_host(host)
        Logger.info('Stop pipeline with run_id=%s.' % run_id)
        self.executor.execute('pipe stop --yes %s' % run_id)
        Logger.info('Pipeline with run_id=%s was stopped.' % run_id)

    def _get_run_id_from_host(self, host):
        host_elements = host.split('-')
        return host_elements[len(host_elements) - 1]

    def _remove_host_from_hosts(self, host):
        Logger.info('Remove host %s from /etc/hosts and default hostfile.' % host)
        self.executor.execute('remove_from_hosts "%s"' % host)


class MemoryHostStorage:

    def __init__(self):
        """
        Additional hosts storage.
        It stores all hosts in memory unlike FileSystemHostStorage implementation.
        """
        self._storage = list()

    def add_host(self, host):
        if host in self._storage:
            raise ScalingError('Host with name \'%s\' is already in the host storage' % host)
        self._storage.append(host)

    def remove_host(self, host):
        if host not in self._storage:
            raise ScalingError('Host with name \'%s\' doesn\'t exist in the host storage' % host)
        self._storage.remove(host)

    def load_hosts(self):
        return list(self._storage)

    def clear(self):
        self._storage = list()


class FileSystemHostStorage:
    _REPLACE_FILE = 'echo "%(content)s" > %(file)s_MODIFIED; ' \
                    'mv %(file)s_MODIFIED %(file)s'
    _LINE_BREAKER = '\n'

    def __init__(self, cmd_executor, storage_file):
        """
        Additional hosts storage.

        It uses file system to persist all hosts. Therefore it can be used through relaunches of the autoscaler script.

        :param cmd_executor: Cmd executor.
        :param storage_file: File to store hosts into.
        """
        self.executor = cmd_executor
        self.storage_file = storage_file

    def add_host(self, host):
        """
        Persist host to storage.

        :param host: Additional host name.
        """
        hosts = self.load_hosts()
        if host in hosts:
            raise ScalingError('Host with name \'%s\' is already in the host storage' % host)
        hosts.append(host)
        self._update_storage_file(hosts)

    def remove_host(self, host):
        """
        Remove host from storage.

        :param host: Additional host name.
        """
        hosts = self.load_hosts()
        if host not in hosts:
            raise ScalingError('Host with name \'%s\' doesn\'t exist in the host storage' % host)
        hosts.remove(host)
        self._update_storage_file(hosts)

    def _update_storage_file(self, hosts):
        self.executor.execute(FileSystemHostStorage._REPLACE_FILE % {'content': '\n'.join(hosts),
                                                                     'file': self.storage_file})

    def load_hosts(self):
        """
        Load all additional hosts from storage.

        :return: A set of all additional hosts.
        """
        if os.path.exists(self.storage_file):
            with open(self.storage_file) as file:
                hosts = []
                for line in file.readlines():
                    stripped_line = line.strip().strip(FileSystemHostStorage._LINE_BREAKER)
                    if stripped_line:
                        hosts.append(stripped_line)
                return hosts
        else:
            return []

    def clear(self):
        self._update_storage_file([])


class GridEngineAutoscaler:

    def __init__(self, grid_engine, cmd_executor, scale_up_handler, scale_down_handler, host_storage, scale_up_timeout,
                 scale_down_timeout, max_additional_hosts, clock=Clock()):
        """
        Grid engine autoscaler.

        It can launch additional pipelines - grid engine hosts - if some jobs are waiting in grid engine queue
        more than the given time internal. Also it scales the cluster down if there are no running jobs or if where
        wasn't new jobs for the given time interval.

        :param grid_engine: Grid engine.
        :param cmd_executor: Cmd executor.
        :param scale_up_handler: Scaling up handler.
        :param scale_down_handler: Scaling down handler.
        :param host_storage: Additional hosts storage.
        :param scale_up_timeout: Maximum number of seconds job could wait in queue
        before autoscaler will scale up the cluster.
        :param scale_down_timeout: Maximum number of seconds the waiting queue could be empty
        before autoscaler will scale down the cluster.
        :param max_additional_hosts: Maximum number of additional hosts that autoscaler can launch.
        :param clock: Clock.
        """
        self.grid_engine = grid_engine
        self.executor = cmd_executor
        self.scale_up_handler = scale_up_handler
        self.scale_down_handler = scale_down_handler
        self.host_storage = host_storage
        self.scale_up_timeout = timedelta(seconds=scale_up_timeout)
        self.scale_down_timeout = timedelta(seconds=scale_down_timeout)
        self.max_additional_hosts = max_additional_hosts
        self.clock = clock

        self.latest_running_job = None

    def scale(self):
        now = self.clock.now()
        Logger.info('Start scaling step at %s.' % now)
        additional_hosts = self.host_storage.load_hosts()
        Logger.info('There are %s additional pipelines.' % len(additional_hosts))
        updated_jobs = self.grid_engine.get_jobs()
        running_jobs = [job for job in updated_jobs if job.state == GridEngineJobState.RUNNING]
        pending_jobs = self._filter_pending_job(updated_jobs)

        if running_jobs:
            self.latest_running_job = sorted(running_jobs, key=lambda job: job.datetime, reverse=True)[0]
        if pending_jobs:
            Logger.info('There are %s waiting jobs.' % len(pending_jobs))
            expiration_datetimes = [job.datetime + self.scale_up_timeout for job in pending_jobs]
            expired_jobs = [job for index, job in enumerate(pending_jobs) if now >= expiration_datetimes[index]]
            if expired_jobs:
                Logger.info('There are %s waiting jobs that are in queue for more than %s seconds. '
                            'Scaling up is required.' % (len(expired_jobs), self.scale_up_timeout.seconds))
                if len(additional_hosts) < self.max_additional_hosts:
                    Logger.info('There are %s/%s additional child pipelines. Scaling up will be performed.' %
                                (len(additional_hosts), self.max_additional_hosts))
                    resource = self.grid_engine.get_resource_demand(pending_jobs)
                    Logger.info('The next resource is requested by pending jobs to be run: '
                                'cpu - {cpu}, gpu - {gpu}, memory - {mem}, disk - {disk}'.
                                format(cpu=resource.cpu, gpu=resource.gpu, mem=resource.memory, disk=resource.disk))
                    self.scale_up(resource)
                else:
                    Logger.info('There are %s/%s additional child pipelines. Scaling up is aborted.' %
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
                    self._scale_down(running_jobs, additional_hosts)
                else:
                    Logger.info('Latest job started less than %s seconds. '
                                'Scaling down is not required.' % self.scale_down_timeout.seconds)
            else:
                Logger.info('There are no previously running jobs. Scaling down is required.')
                self._scale_down(running_jobs, additional_hosts)
        Logger.info('Finish scaling step at %s.' % self.clock.now())
        post_scale_additional_hosts = self.host_storage.load_hosts()
        Logger.info('There are %s additional pipelines.' % len(post_scale_additional_hosts))

    def _filter_pending_job(self, updated_jobs):
        # kill jobs that are pending and can't be satisfied with requested resource
        # f.i. we have only 3 instance max and the biggest possible type has 10 cores but job requests 40 coresf
        pending_jobs = [job for job in updated_jobs if job.state == GridEngineJobState.PENDING]
        valid_pending_jobs = []
        invalid_pending_jobs = []
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
        return valid_pending_jobs

    def scale_up(self, resource):
        """
        Launches new child pipeline.

        :return: Launched pipeline host.
        """
        Logger.info('Start grid engine SCALING UP.')
        return self.scale_up_handler.scale_up(resource)

    def _scale_down(self, running_jobs, additional_hosts):
        active_hosts = set([host for job in running_jobs for host in job.hosts])
        inactive_additional_hosts = [host for host in additional_hosts if host not in active_hosts]
        if inactive_additional_hosts:
            Logger.info('There are %s inactive additional child pipelines. '
                        'Scaling down will be performed.' % len(inactive_additional_hosts))
            # TODO: here we always choose weakest host, even if all hosts are inactive and we can drop strongest one firstly
            # TODO in order to safe some money
            inactive_additional_host = self.grid_engine.get_host_to_scale_down(inactive_additional_hosts)
            succeed = self.scale_down(inactive_additional_host)
            if succeed:
                self.host_storage.remove_host(inactive_additional_host)
        else:
            Logger.info('There are no inactive additional child pipelines. Scaling down will not be performed.')

    def scale_down(self, child_host):
        """
        Stops required child pipeline.

        :param child_host: Child pipeline host that supposed to be stopped.
        :return: True if the worker was scaled down, False otherwise.
        """
        Logger.info('Start grid engine SCALING DOWN for %s host.' % child_host)
        return self.scale_down_handler.scale_down(child_host)


class GridEngineWorkerValidator:
    _STOP_PIPELINE = 'pipe stop --yes %s'
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
        Finds and removes any additional hosts which aren't valid execution hosts in GE or not running pipelines.
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
            Logger.warn('Additional host with run_id=%s status is not %s but %s.'
                        % (run_id, self._RUNNING_STATUS, status))
            return False
        except APIError as e:
            Logger.warn('Additional host with run_id=%s status retrieving has failed '
                        'and it is considered not running: %s' % (run_id, str(e)))
            return False
        except Exception as e:
            Logger.warn('Additional host with run_id=%s status retrieving has failed '
                        'but it is temporary considered running: %s' % (run_id, str(e)))
            return True

    def _try_stop_worker(self, run_id):
        try:
            Logger.info('Stop pipeline with run_id=%s' % run_id)
            self.executor.execute(GridEngineWorkerValidator._STOP_PIPELINE % run_id)
        except:
            Logger.warn('Invalid additional worker disabling has failed.')

    def _try_disable_worker(self, host, run_id):
        try:
            Logger.info('Disable worker in GE with run_id=%s' % run_id)
            self.grid_engine.disable_host(host)
        except:
            Logger.warn('Invalid additional worker disabling has failed.')

    def _try_kill_invalid_host_jobs(self, host):
        invalid_host_jobs = [job for job in self.grid_engine.get_jobs() if host in job.hosts]
        if invalid_host_jobs:
            self.grid_engine.kill_jobs(invalid_host_jobs, force=True)

    def _remove_worker_from_hosts(self, host):
        Logger.info('Remove worker (host=%s) from the well-known hosts.' % host)
        self.scale_down_handler._remove_host_from_hosts(host)


class CloudPipelineInstanceHelper:

    def __init__(self, pipe, cloud_provider, region_id, master_instance_type, instance_family,
                 hybrid_autoscale, hybrid_instance_cores, free_cores):
        self.pipe = pipe
        self.cloud_provider = cloud_provider
        self.region_id = region_id
        self.master_instance_type = master_instance_type
        self.instance_family = instance_family
        self.hybrid_autoscale = hybrid_autoscale
        self.hybrid_instance_cores = hybrid_instance_cores
        self.free_cores = free_cores

    def select_instance(self, resource, price_type):
        allowed = self._get_allowed_instances(price_type)
        for instance in allowed:
            if instance.cpu - self.free_cores >= resource.cpu:
                return instance
        return allowed.pop()

    def get_max_allowed(self, price_type):
        return self._get_allowed_instances(price_type).pop()

    def _get_allowed_instances(self, price_type):
        if self.hybrid_autoscale and self.instance_family:
            return self._get_hybrid_instances(price_type)
        else:
            Logger.info('Hybrid autoscaling is disabled, allowed list of instances will be trimmed '
                        'to master instance type: {type}'.format(type=self.master_instance_type))
            return self._get_master_instance(price_type)

    def _get_master_instance(self, price_type):
        return [instance for instance in self._get_existing_instances(price_type)
                if instance.name == self.master_instance_type]

    def _get_hybrid_instances(self, price_type):
        return sorted([instance for instance in self._get_existing_instances(price_type)
                       if self._is_instance_from_family(instance.name) and instance.cpu <= self.hybrid_instance_cores],
                      key=lambda instance: instance.cpu)

    def _get_existing_instances(self, price_type):
        allowed_instance_types = pipe.get_allowed_instance_types(self.region_id, price_type == 'spot')
        docker_instance_types = allowed_instance_types['cluster.allowed.instance.types.docker']
        return [CPInstance.from_cp_response(instance) for instance in docker_instance_types]

    @staticmethod
    def get_family_from_type(cloud_provider, instance_type):
        if cloud_provider == CloudProvider.aws():
            search = re.search('^([a-z]\d+)\..*', instance_type)
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

    def _is_instance_from_family(self, instance_type):
        return CloudPipelineInstanceHelper.get_family_from_type(self.cloud_provider, instance_type) == self.instance_family


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


def fetch_worker_launch_system_params(api, master_run_id):
    parent_run = api.load_run(master_run_id)
    master_system_params = {param.get('name'): param.get('resolvedValue') for param in parent_run.get('pipelineRunParameters', [])}
    system_launch_params_string = api.retrieve_preference('launch.system.parameters', default_value='[]')
    system_launch_params = json.loads(system_launch_params_string)
    worker_launch_system_params = 'CP_CAP_SGE false ' \
                                  'CP_CAP_AUTOSCALE false ' \
                                  'CP_CAP_AUTOSCALE_WORKERS 0 ' \
                                  'CP_DISABLE_RUN_ENDPOINTS true '
    for launch_param in system_launch_params:
        param_name = launch_param.get('name')
        if launch_param.get('passToWorkers', False) and param_name in master_system_params:
            worker_launch_system_params += ' {} {}'.format(param_name, master_system_params.get(param_name))
    return worker_launch_system_params


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Launches grid engine autoscaler long running process.',
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--debug',
                        action='store_true',
                        help='If specified print all logs to command line.')
    parser.add_argument('--polling-interval',
                        default=10,
                        help='Autoscaling polling interval in seconds.')
    args = parser.parse_args()

    cloud_provider = CloudProvider(os.environ['CLOUD_PROVIDER'])
    pipeline_api = os.environ['API']
    master_run_id = os.environ['RUN_ID']
    default_hostfile = os.environ['DEFAULT_HOSTFILE']
    instance_disk = os.getenv('CP_CAP_AUTOSCALE_INSTANCE_DISK', os.environ['instance_disk'])
    instance_type = os.getenv('CP_CAP_AUTOSCALE_INSTANCE_TYPE', os.environ['instance_size'])
    instance_image = os.getenv('CP_CAP_AUTOSCALE_INSTANCE_IMAGE', os.environ['docker_image'])
    cmd_template = os.getenv('CP_CAP_AUTOSCALE_CMD_TEMPLATE', 'sleep infinity')
    price_type = os.getenv('CP_CAP_AUTOSCALE_PRICE_TYPE', os.environ['price_type'])
    region_id = os.getenv('CP_CAP_AUTOSCALE_CLOUD_REGION_ID', os.environ['CLOUD_REGION_ID'])
    instance_cores = int(os.getenv('CLOUD_PIPELINE_NODE_CORES', multiprocessing.cpu_count()))
    additional_hosts = int(os.getenv('CP_CAP_AUTOSCALE_WORKERS', 3))
    log_verbose = os.getenv('CP_CAP_AUTOSCALE_VERBOSE', 'false').strip().lower() == 'true'
    free_cores = int(os.getenv('CP_CAP_SGE_WORKER_FREE_CORES', 0))
    master_cores = int(os.getenv('CP_CAP_SGE_MASTER_CORES', instance_cores))
    master_cores = master_cores - free_cores if master_cores - free_cores > 0 else master_cores
    shared_work_dir = os.getenv('SHARED_WORK_FOLDER', '/common/workdir')
    hybrid_autoscale = os.getenv('CP_CAP_AUTOSCALE_HYBRID', 'false').strip().lower() == 'true'
    hybrid_instance_cores = int(os.getenv('CP_CAP_AUTOSCALE_HYBRID_MAX_CORE_PER_NODE', sys.maxint))
    instance_family = os.getenv('CP_CAP_AUTOSCALE_HYBRID_FAMILY',
                                CloudPipelineInstanceHelper.get_family_from_type(cloud_provider, instance_type))

    # TODO: Replace all the usages of PipelineAPI raw client with an actual CloudPipelineAPI client
    pipe = PipelineAPI(api_url=pipeline_api, log_dir=os.path.join(shared_work_dir, '.pipe.log'))
    api = CloudPipelineAPI(pipe=pipe)

    worker_launch_system_params = fetch_worker_launch_system_params(api, master_run_id)

    instance_helper = CloudPipelineInstanceHelper(cloud_provider=cloud_provider, region_id=region_id,
                                                  instance_family=instance_family, master_instance_type=instance_type,
                                                  pipe=pipe, hybrid_autoscale=hybrid_autoscale,
                                                  hybrid_instance_cores=hybrid_instance_cores,
                                                  free_cores=free_cores)

    default_hosts = int(os.getenv('node_count', 0))

    max_instance_cores = instance_helper.get_max_allowed(price_type).cpu - free_cores
    max_cluster_cores = max_instance_cores * additional_hosts \
                        + (instance_cores - free_cores) * default_hosts \
                        + master_cores

    Logger.init(cmd=args.debug, log_file=os.path.join(shared_work_dir, '.autoscaler.log'),
                task='GridEngineAutoscaling', verbose=log_verbose)

    cmd_executor = CmdExecutor()

    grid_engine = GridEngine(cmd_executor=cmd_executor, max_instance_cores=max_instance_cores,
                             max_cluster_cores=max_cluster_cores)
    host_storage = FileSystemHostStorage(cmd_executor=cmd_executor,
                                         storage_file=os.path.join(shared_work_dir, '.autoscaler.storage'))
    scale_up_timeout = int(api.retrieve_preference('ge.autoscaling.scale.up.timeout', default_value=30))
    scale_down_timeout = int(api.retrieve_preference('ge.autoscaling.scale.down.timeout', default_value=30))
    scale_up_polling_timeout = int(api.retrieve_preference('ge.autoscaling.scale.up.polling.timeout',
                                                           default_value=900))
    scale_up_polling_delay = int(os.getenv('CP_CAP_AUTOSCALE_SCALE_UP_POLLING_DELAY', 10))
    scale_up_handler = GridEngineScaleUpHandler(cmd_executor=cmd_executor, api=api, grid_engine=grid_engine,
                                                host_storage=host_storage, instance_helper=instance_helper,
                                                parent_run_id=master_run_id, default_hostfile=default_hostfile,
                                                instance_disk=instance_disk, instance_image=instance_image,
                                                cmd_template=cmd_template,
                                                price_type=price_type, region_id=region_id,
                                                polling_delay=scale_up_polling_delay,
                                                polling_timeout=scale_up_polling_timeout,
                                                instance_family=instance_family,
                                                worker_launch_system_params=worker_launch_system_params)
    scale_down_handler = GridEngineScaleDownHandler(cmd_executor=cmd_executor, grid_engine=grid_engine,
                                                    default_hostfile=default_hostfile)
    worker_validator = GridEngineWorkerValidator(cmd_executor=cmd_executor, api=api, host_storage=host_storage,
                                                 grid_engine=grid_engine, scale_down_handler=scale_down_handler)
    autoscaler = GridEngineAutoscaler(grid_engine=grid_engine, cmd_executor=cmd_executor,
                                      scale_up_handler=scale_up_handler, scale_down_handler=scale_down_handler,
                                      host_storage=host_storage, scale_up_timeout=scale_up_timeout,
                                      scale_down_timeout=scale_down_timeout, max_additional_hosts=additional_hosts)
    daemon = GridEngineAutoscalingDaemon(autoscaler=autoscaler, worker_validator=worker_validator,
                                         polling_timeout=args.polling_interval)
    daemon.start()
