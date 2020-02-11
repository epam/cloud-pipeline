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


class ExecutionError(RuntimeError):
    pass


class ParsingError(RuntimeError):
    pass


class LoggingError(RuntimeError):
    pass


class ScalingError(RuntimeError):
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
            Logger.warn('Command \'%s\' execution has failed due to %s.' % (command, err.rstrip()))
            raise ExecutionError('Command \'%s\' execution has failed due to %s.' % (command, err.rstrip()))
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

    def __init__(self, id, name, user, state, datetime, host=None, array=None):
        self.id = id
        self.name = name
        self.user = user
        self.state = state
        self.datetime = datetime
        self.host = host
        self.array = array


class GridEngine:
    _MAIN_Q = os.getenv('CP_CAP_SGE_QUEUE_NAME', 'main.q')
    _PARALLEL_ENVIRONMENT = os.getenv('CP_CAP_SGE_PE_NAME', 'local')
    _ALL_HOSTS = '@allhosts'
    _DELETE_HOST = 'qconf -de %s'
    _SHOW_PARALLEL_ENVIRONMENT_SLOTS = 'qconf -sp %s | grep "^slots" | awk \'{print $2}\''
    _REPLACE_PARALLEL_ENVIRONMENT_SLOTS = 'qconf -rattr pe slots %s %s'
    _REMOVE_HOST_FROM_HOST_GROUP = 'qconf -dattr hostgroup hostlist %s %s'
    _REMOVE_HOST_FROM_QUEUE_SETTINGS = 'qconf -purge queue slots %s@%s'
    _SHUTDOWN_HOST_EXECUTION_DAEMON = 'qconf -ke %s'
    _QSTAT = 'qstat -u "*"'
    _QSTAT_DATETIME_FORMAT = '%m/%d/%Y %H:%M:%S'
    _QSTAT_COLUMNS = ['job-ID', 'prior', 'name', 'user', 'state', 'submit/start at', 'queue', 'slots', 'ja-task-ID']
    _QMOD_DISABLE = 'qmod -d %s@%s'
    _QMOD_ENABLE = 'qmod -e %s@%s'
    _SHOW_EXECUTION_HOST = 'qconf -se %s'
    _KILL_JOBS = 'qdel %s'
    _FORCE_KILL_JOBS = 'qdel -f %s'

    def __init__(self, cmd_executor):
        self.cmd_executor = cmd_executor

    def get_jobs(self):
        """
        Executes command and parse its output. The expected output is something like the following:

            job-ID  prior   name       user         state submit/start at     queue                          slots ja-task-ID
            -----------------------------------------------------------------------------------------------------------------
                  2 0.75000 sleep      root         r     12/21/2018 11:48:00 main.q@pipeline-38415              1
                  9 0.25000 sleep      root         qw    12/21/2018 12:39:38                                    1
                 11 0.25000 sleep      root         qw    12/21/2018 14:34:43                                    1 1-10:1

        :return: Grid engine jobs list.
        """
        lines = self.cmd_executor.execute_to_lines(GridEngine._QSTAT)
        if len(lines) == 0:
            return []
        jobs = []
        indentations = [lines[0].index(column) for column in GridEngine._QSTAT_COLUMNS]
        for line in lines[2:]:
            jobs.append(GridEngineJob(
                id=self._by_indent(line, indentations, 0),
                name=self._by_indent(line, indentations, 2),
                user=self._by_indent(line, indentations, 3),
                state=GridEngineJobState.from_letter_code(self._by_indent(line, indentations, 4)),
                datetime=self._parse_date(line, indentations),
                host=self._parse_host(line, indentations),
                array=self._parse_array(line, indentations)
            ))
        return jobs

    def _parse_date(self, line, indentations):
        return datetime.strptime(self._by_indent(line, indentations, 5), GridEngine._QSTAT_DATETIME_FORMAT)

    def _parse_host(self, line, indentations):
        queue_and_host = self._by_indent(line, indentations, 6)
        return queue_and_host.split('@')[1] if queue_and_host else None

    def _parse_array(self, line, indentations):
        array_jobs = self._by_indent(line, indentations, 8)
        if not array_jobs:
            return None
        if ':' in array_jobs:
            array_borders, _ = array_jobs.split(':')
            start, stop = array_borders.split('-')
            return list(range(int(start), int(stop) + 1))
        elif ',' in array_jobs:
            return list(map(int, array_jobs.split(',')))
        else:
            return [int(array_jobs)]

    def _by_indent(self, line, indentations, index):
        if index >= len(indentations) - 1:
            return line[indentations[index]:].strip()
        else:
            return line[indentations[index]:indentations[min(len(indentations) - 1, index + 1)]].strip()

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

    def increase_parallel_environment_slots(self, slots, pe=_PARALLEL_ENVIRONMENT):
        """
        Increases the number of parallel environment slots.

        :param slots: Number of slots to append.
        :param pe: Parallel environment to update number of slots for.
        """
        pe_slots = self.get_parallel_environment_slots(pe)
        self.cmd_executor.execute(GridEngine._REPLACE_PARALLEL_ENVIRONMENT_SLOTS % (pe_slots + slots, pe))

    def decrease_parallel_environment_slots(self, slots, pe=_PARALLEL_ENVIRONMENT):
        """
        Decreases the number of parallel environment slots.

        :param slots: Number of slots to subtract.
        :param pe: Parallel environment to update number of slots for.
        """
        pe_slots = self.get_parallel_environment_slots(pe)
        self.cmd_executor.execute(GridEngine._REPLACE_PARALLEL_ENVIRONMENT_SLOTS % (pe_slots - slots, pe))

    def get_parallel_environment_slots(self, pe=_PARALLEL_ENVIRONMENT):
        """
        Returns number of the parallel environment slots.

        :param pe: Parallel environment to return number of slots for.
        """
        return int(self.cmd_executor.execute(GridEngine._SHOW_PARALLEL_ENVIRONMENT_SLOTS % pe).strip())

    def delete_host(self, host, queue=_MAIN_Q, hostgroup=_ALL_HOSTS, skip_on_failure=False):
        """
        Completely deletes host from GE:
        1. Shutdown host execution daemon.
        2. Removes host from queue settings.
        3. Removes host from host group.
        4. Removes host from GE.

        :param host: Host to be removed.
        :param queue: Queue host is a part of.
        :param hostgroup: Host group queue uses.
        :param skip_on_failure: Specifies if the host killing should be continued even if some of
        the commands has failed.
        """
        self._shutdown_execution_host(host, skip_on_failure=skip_on_failure)
        self._remove_host_from_queue_settings(host, queue, skip_on_failure=skip_on_failure)
        self._remove_host_from_host_group(host, hostgroup, skip_on_failure=skip_on_failure)
        self._remove_host_from_grid_engine(host, skip_on_failure=skip_on_failure)

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
        Validates host in GE checking corresponding execution host availability.

        :param host: Host to be checked.
        :return: True if execution host exists.
        """
        try:
            self.cmd_executor.execute_to_lines(GridEngine._SHOW_EXECUTION_HOST % host)
            return True
        except RuntimeError:
            Logger.warn('Execution host %s in GE wasn\'t found.' % host)
            return False

    def kill_jobs(self, jobs, force=False):
        """
        Kills jobs in GE.

        :param jobs: Grid engine jobs.
        :param force: Specifies if this command should be performed with -f flag.
        """
        job_ids = []
        for job in jobs:
            job_id = str(job.id)
            job_array_index = '' if not job.array or len(job.array) > 1 else ('.%s' % job.array[0])
            job_ids.append(job_id + job_array_index)

        self.cmd_executor.execute((GridEngine._FORCE_KILL_JOBS if force else GridEngine._KILL_JOBS) % ' '.join(job_ids))


class Clock:

    def __init__(self):
        pass

    def now(self):
        return datetime.now()


class GridEngineScaleUpHandler:
    _POLL_ATTEMPTS = 60
    _POLL_DELAY = 10

    def __init__(self, cmd_executor, pipe, grid_engine, host_storage, parent_run_id, default_hostfile, instance_disk,
                 instance_type, instance_image, price_type, region_id, instance_cores, polling_timeout,
                 polling_delay=_POLL_DELAY):
        """
        Grid engine scale up implementation. It handles additional nodes launching and hosts configuration (/etc/hosts
        and self.default_hostfile).

        :param cmd_executor: Cmd executor.
        :param pipe: Cloud pipeline client.
        :param grid_engine: Grid engine client.
        :param host_storage: Additional hosts storage.
        :param parent_run_id: Additional nodes parent run id.
        :param default_hostfile: Default host file location.
        :param instance_disk: Additional nodes disk size.
        :param instance_type: Additional nodes instance type.
        :param instance_image: Additional nodes docker image.
        :param price_type: Additional nodes price type.
        :param region_id: Additional nodes Cloud Region id.
        :param instance_cores:  Additional nodes cores number.
        :param polling_timeout: Kubernetes and Pipeline APIs polling timeout - in seconds.
        :param polling_delay: Polling delay - in seconds.
        """
        self.executor = cmd_executor
        self.pipe = pipe
        self.grid_engine = grid_engine
        self.host_storage = host_storage
        self.parent_run_id = parent_run_id
        self.default_hostfile = default_hostfile
        self.instance_disk = instance_disk
        self.instance_type = instance_type
        self.instance_image = instance_image
        self.price_type = price_type
        self.region_id = region_id
        self.instance_cores = instance_cores
        self.polling_timeout = polling_timeout
        self.polling_delay = polling_delay

    def scale_up(self):
        """
        Launches new pipeline and configures local hosts files.

        Notice that master hosts file is altered before an additional working node starts to add itself to GE
        configuration. Otherwise GE configuration will end up with 'can't resolve hostname'-like errors.

        Also notice that an additional worker host is manually enabled in GE only after its pipeline is initialized.
        This happens because additional workers is disabled in GE by default to prevent job submissions to not
        initialized pipeline.

        :return: Host name of the launched pipeline.
        """
        run_id = self._launch_additional_worker()
        host = self._retrieve_pod_name(run_id)
        self.host_storage.add_host(host)
        pod = self._await_pod_initialization(run_id)
        self._add_worker_to_master_hosts(pod)
        self._await_worker_initialization(run_id)
        self.grid_engine.enable_host(pod.name)
        self._increase_parallel_environment_slots(self.instance_cores)
        Logger.info('Additional worker with host=%s has been created.' % pod.name, crucial=True)

        # todo: Some delay is needed for GE to submit task to a new host.
        #  Probably, we should check if some jobs is scheduled on the host and release the block only after that.
        #  On the other hand, some jobs may finish between our checks so the program may stuck until host is filled
        #  with some task.

        return pod.name

    def _launch_additional_worker(self):
        Logger.info('Launch additional worker.')
        pipe_run_command = 'pipe run --yes --quiet ' \
                           '--instance-disk %s ' \
                           '--instance-type %s ' \
                           '--docker-image %s ' \
                           '--cmd-template "sleep infinity" ' \
                           '--parent-id %s ' \
                           '--price-type %s ' \
                           '--region-id %s ' \
                           'cluster_role worker ' \
                           'cluster_role_type additional ' \
                           'CP_CAP_SGE false ' \
                           'CP_CAP_AUTOSCALE false ' \
                           'CP_CAP_AUTOSCALE_WORKERS 0' \
                           % (self.instance_disk, self.instance_type, self.instance_image, self.parent_run_id,
                              self._pipe_cli_price_type(self.price_type), self.region_id)
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
        run = self.pipe.load_run(run_id)
        if 'podId' in run:
            name = run['podId']
            Logger.info('Additional worker with run_id=%s and pod_name=%s has been retrieved.' % (run_id, name))
            return name
        else:
            error_msg = 'Worker with run_id=%s has no pod name specified.'
            Logger.fail(error_msg)
            raise ScalingError(error_msg)

    def _await_pod_initialization(self, run_id):
        Logger.info('Waiting for additional worker with run_id=%s pod to initialize.' % run_id)
        attempts = self.polling_timeout / self.polling_delay if self.polling_delay \
            else GridEngineScaleUpHandler._POLL_ATTEMPTS
        while attempts != 0:
            run = self.pipe.load_run(run_id)
            if 'podIP' in run:
                pod = KubernetesPod(ip=run['podIP'], name=run['podId'])
                Logger.info('Additional worker pod has started with ip=%s and name=%s.' % (pod.ip, pod.name))
                return pod
            Logger.info('Additional worker pod initialization hasn\'t finished yet. Only %s attempts remain left.'
                        % attempts)
            attempts -= 1
            time.sleep(self.polling_delay)
        error_msg = 'Pod with run_id=%s hasn\'t started after %s seconds.' % (run_id, self.polling_timeout)
        Logger.fail(error_msg)
        raise ScalingError(error_msg)

    def _add_worker_to_master_hosts(self, pod):
        self.executor.execute('echo "%s\t%s" >> /etc/hosts' % (pod.ip, pod.name))
        self.executor.execute('echo %s >> %s' % (pod.name, self.default_hostfile))

    def _await_worker_initialization(self, run_id):
        Logger.info('Waiting for additional worker with run_id=%s to initialize.' % run_id)
        attempts = self.polling_timeout / self.polling_delay if self.polling_delay \
            else GridEngineScaleUpHandler._POLL_ATTEMPTS
        while attempts != 0:
            run = self.pipe.load_run(run_id)
            if run['initialized']:
                Logger.info('Additional worker with run_id=%s has initialized.' % run_id)
                return
            Logger.info('Additional worker with run_id=%s hasn\'t been initialized yet. Only %s attempts remain left.'
                        % (run_id, attempts))
            attempts -= 1
            time.sleep(self.polling_delay)
        error_msg = 'Additional worker hasn\'t been initialized after %s seconds.' % self.polling_timeout
        Logger.fail(error_msg)
        raise ScalingError(error_msg)

    def _increase_parallel_environment_slots(self, slots_to_append):
        Logger.info('Increase number of parallel environment slots by %s.' % slots_to_append)
        self.grid_engine.increase_parallel_environment_slots(slots_to_append)
        Logger.info('Number of parallel environment slots was increased.')


class GridEngineScaleDownHandler:
    # todo: This approach is not interrupt-safe because cp command can end up in a damaged destination file.
    #  Another approach was to use mv command because it most likely has atomicity on the OS level
    #  but it ends up with a 'device or resource busy' error every time on /etc/hosts file at least.
    _REMOVE_LINE_COMMAND = 'cat %(file)s | grep -v "%(line)s" > %(file)s_MODIFIED; ' \
                           'cp %(file)s_MODIFIED %(file)s; ' \
                           'rm %(file)s_MODIFIED'

    def __init__(self, cmd_executor, grid_engine, default_hostfile, instance_cores):
        """
        Grid engine scale down implementation. It handles grid engine host removal, hosts configuration (/etc/hosts
        and self.default_hostfile) and additional nodes stopping.

        :param cmd_executor: Cmd executor.
        :param grid_engine: Grid engine client.
        :param default_hostfile: Default host file location.
        :param instance_cores:  Additional nodes cores number.
        """
        self.executor = cmd_executor
        self.grid_engine = grid_engine
        self.default_hostfile = default_hostfile
        self.instance_cores = instance_cores

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
        disabled_host_jobs = [job for job in jobs if job.host == child_host]
        if disabled_host_jobs:
            Logger.warn('Disabled additional worker with host=%s has %s associated jobs. Scaling down is interrupted.'
                        % (child_host, len(disabled_host_jobs)))
            Logger.info('Enable additional worker with host=%s again.' % child_host)
            self.grid_engine.enable_host(child_host)
            return False
        self._decrease_parallel_environment_slots(self.instance_cores)
        self._remove_host_from_grid_engine_configuration(child_host)
        self._stop_pipeline(child_host)
        self._remove_host_from_hosts(child_host)
        self._remove_host_from_default_hostfile(child_host)
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

    def _remove_host_from_default_hostfile(self, host):
        Logger.info('Remove host %s from default hostfile.' % host)
        self._remove_line_from_file(file=self.default_hostfile, line=host)

    def _remove_host_from_hosts(self, host):
        Logger.info('Remove host %s from /etc/hosts.' % host)
        self._remove_line_from_file(file='/etc/hosts', line=host)

    def _remove_line_from_file(self, file, line):
        self.executor.execute(GridEngineScaleDownHandler._REMOVE_LINE_COMMAND % {'file': file, 'line': line})


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
        pending_jobs = [job for job in updated_jobs if job.state == GridEngineJobState.PENDING]
        running_jobs = [job for job in updated_jobs if job.state == GridEngineJobState.RUNNING]
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
                    self.scale_up()
                else:
                    Logger.info('There are %s/%s additional child pipelines. Scaling up is aborted.' %
                                (len(additional_hosts), self.max_additional_hosts))
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
                Logger.info('There are no previously running jobs. Scaling is skipped.')
        Logger.info('Finish scaling step at %s.' % self.clock.now())
        post_scale_additional_hosts = self.host_storage.load_hosts()
        Logger.info('There are %s additional pipelines.' % len(post_scale_additional_hosts))

    def scale_up(self):
        """
        Launches new child pipeline.

        :return: Launched pipeline host.
        """
        Logger.info('Start grid engine SCALING UP.')
        return self.scale_up_handler.scale_up()

    def _scale_down(self, running_jobs, additional_hosts):
        active_hosts = set([job.host for job in running_jobs])
        inactive_additional_hosts = [host for host in additional_hosts if host not in active_hosts]
        if inactive_additional_hosts:
            Logger.info('There are %s inactive additional child pipelines. '
                        'Scaling down will be performed.' % len(inactive_additional_hosts))
            inactive_additional_host = inactive_additional_hosts[0]
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

    def __init__(self, cmd_executor, host_storage, grid_engine, scale_down_handler):
        """
        Grid engine worker validator.

        The reason why validator exists is that some additional hosts may be broken due to different circumstances.
        F.e. autoscaler has failed while configuring additional host so it is partly configured and has to be stopped.

        :param grid_engine: Grid engine.
        :param cmd_executor: Cmd executor.
        :param host_storage: Additional hosts storage.
        :param scale_down_handler: Scale down handler.
        """
        self.grid_engine = grid_engine
        self.executor = cmd_executor
        self.host_storage = host_storage
        self.scale_down_handler = scale_down_handler

    def validate_hosts(self):
        """
        Checks additional hosts if they are valid execution hosts in GE and kills invalid ones.
        """
        hosts = self.host_storage.load_hosts()
        Logger.info('Validate %s additional workers.' % len(hosts))
        invalid_hosts = [host for host in hosts if not self.grid_engine.is_valid(host)]
        for host in invalid_hosts:
            Logger.warn('Invalid additional host %s was found. It will be downscaled.' % host)
            run_id = self.scale_down_handler._get_run_id_from_host(host)
            self._try_stop_worker(run_id)
            self._try_disable_worker(host, run_id)
            self._try_kill_invalid_host_jobs(host)
            self.grid_engine.delete_host(host, skip_on_failure=True)
            self._remove_worker_from_hosts(host)
            self.host_storage.remove_host(host)
        Logger.info('Additional hosts validation has finished.')

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
        invalid_host_jobs = [job for job in self.grid_engine.get_jobs() if job.host == host]
        if invalid_host_jobs:
            self.grid_engine.kill_jobs(invalid_host_jobs, force=True)

    def _remove_worker_from_hosts(self, host):
        Logger.info('Disable worker with host=%s from GE.' % host)
        self.scale_down_handler._remove_host_from_hosts(host)
        self.scale_down_handler._remove_host_from_default_hostfile(host)


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
                Logger.warn('Manual stop the autoscaler daemon.')
                break
            except Exception as e:
                Logger.fail('Scaling step has failed due to %s.' % e)


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


def _retrieve_preference(pipe, preference, default_value):
    try:
        return pipe.get_preference(preference)['value']
    except:
        Logger.warn('Pipeline preference %s retrieving failed. Using default value: %s.' % (preference, default_value))
        return default_value


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

    pipeline_api = os.environ['API']
    master_run_id = os.environ['RUN_ID']
    default_hostfile = os.environ['DEFAULT_HOSTFILE']
    instance_disk = os.environ['instance_disk']
    instance_type = os.environ['instance_size']
    instance_image = os.environ['docker_image']
    price_type = os.environ['price_type']
    region_id = os.environ['CLOUD_REGION_ID']
    instance_cores = int(os.environ['CLOUD_PIPELINE_NODE_CORES']) \
        if 'CLOUD_PIPELINE_NODE_CORES' in os.environ else multiprocessing.cpu_count()
    max_additional_hosts = int(os.environ['CP_CAP_AUTOSCALE_WORKERS']) \
        if 'CP_CAP_AUTOSCALE_WORKERS' in os.environ else 3
    log_verbose = os.environ['CP_CAP_AUTOSCALE_VERBOSE'].strip().lower() == "true" \
        if 'CP_CAP_AUTOSCALE_VERBOSE' in os.environ else False

    Logger.init(cmd=args.debug, log_file='/common/workdir/.autoscaler.log', task='GridEngineAutoscaling',
                verbose=log_verbose)

    cmd_executor = CmdExecutor()
    grid_engine = GridEngine(cmd_executor=cmd_executor)
    host_storage = FileSystemHostStorage(cmd_executor=cmd_executor, storage_file='/common/workdir/.autoscaler.storage')
    pipe = PipelineAPI(api_url=pipeline_api, log_dir='/common/workdir/.pipe.log')
    scale_up_timeout = int(_retrieve_preference(pipe, 'ge.autoscaling.scale.up.timeout', default_value=30))
    scale_down_timeout = int(_retrieve_preference(pipe, 'ge.autoscaling.scale.down.timeout', default_value=30))
    scale_up_polling_timeout = int(_retrieve_preference(pipe, 'ge.autoscaling.scale.up.polling.timeout',
                                                        default_value=600))
    scale_up_handler = GridEngineScaleUpHandler(cmd_executor=cmd_executor, pipe=pipe, grid_engine=grid_engine,
                                                host_storage=host_storage, parent_run_id=master_run_id,
                                                default_hostfile=default_hostfile, instance_disk=instance_disk,
                                                instance_type=instance_type, instance_image=instance_image,
                                                price_type=price_type, region_id=region_id,
                                                instance_cores=instance_cores, polling_timeout=scale_up_polling_timeout)
    scale_down_handler = GridEngineScaleDownHandler(cmd_executor=cmd_executor, grid_engine=grid_engine,
                                                    default_hostfile=default_hostfile, instance_cores=instance_cores)
    worker_validator = GridEngineWorkerValidator(cmd_executor=cmd_executor, host_storage=host_storage,
                                                 grid_engine=grid_engine, scale_down_handler=scale_down_handler)
    autoscaler = GridEngineAutoscaler(grid_engine=grid_engine, cmd_executor=cmd_executor,
                                      scale_up_handler=scale_up_handler, scale_down_handler=scale_down_handler,
                                      host_storage=host_storage, scale_up_timeout=scale_up_timeout,
                                      scale_down_timeout=scale_down_timeout, max_additional_hosts=max_additional_hosts)
    daemon = GridEngineAutoscalingDaemon(autoscaler=autoscaler, worker_validator=worker_validator,
                                         polling_timeout=args.polling_interval)
    daemon.start()
