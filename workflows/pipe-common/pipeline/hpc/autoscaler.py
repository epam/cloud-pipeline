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
import operator
import threading
import traceback
from collections import namedtuple
from datetime import timedelta

import itertools
import time

from pipeline.hpc.engine.gridengine import GridEngineJobState
from pipeline.hpc.logger import Logger
from pipeline.hpc.resource import IntegralDemand
from pipeline.hpc.utils import Clock

try:
    from queue import Queue, Empty as QueueEmptyError
except ImportError:
    from Queue import Queue, Empty as QueueEmptyError


KubernetesPod = namedtuple('KubernetesPod', 'ip,name')


class ScalingError(RuntimeError):
    pass


class GridEngineScaleUpOrchestrator:
    _POLL_DELAY = 10

    def __init__(self, scale_up_handler, grid_engine, host_storage, static_host_storage, worker_tags_handler,
                 instance_selector, worker_recorder, batch_size, polling_delay=_POLL_DELAY, clock=Clock()):
        """
        Grid engine scale up orchestrator.

        Handles additional workers batch scaling up.
        Scales up no more than a configured number of additional workers at once.
        Waits for all batch additional workers to scale up before continuing.

        :param scale_up_handler: Scaling up handler.
        :param grid_engine: Grid engine client.
        :param host_storage: Additional hosts storage.
        :param static_host_storage: Static workers host storage
        :param worker_tags_handler: Additional workers tags handler.
        :param instance_selector: Additional instances selector.
        :param worker_recorder: Additional hosts recorder.
        :param batch_size: Scaling up batch size.
        :param polling_delay: Polling delay - in seconds.
        """
        self.scale_up_handler = scale_up_handler
        self.grid_engine = grid_engine
        self.host_storage = host_storage
        self.static_host_storage = static_host_storage
        self.worker_tags_handler = worker_tags_handler
        self.instance_selector = instance_selector
        self.worker_recorder = worker_recorder
        self.batch_size = batch_size
        self.polling_delay = polling_delay
        self.clock = clock

    def scale_up(self, resource_demands, max_batch_size):
        instance_demands = list(itertools.islice(self.instance_selector.select(resource_demands),
                                                 min(self.batch_size, max_batch_size)))
        if not instance_demands:
            Logger.info('There are no instance demands. Scaling up is aborted.')
            return
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
            self.worker_tags_handler.process_tags()
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
            self.static_host_storage.update_running_jobs_host_activity(running_jobs, self.clock.now())


class DoNothingScaleUpHandler:

    def scale_up(self, instance, owner, run_id_queue):
        pass


class GridEngineScaleUpHandler:
    _POLL_TIMEOUT = 900
    _POLL_ATTEMPTS = 60
    _POLL_DELAY = 10
    _GE_POLL_TIMEOUT = 60
    _GE_POLL_ATTEMPTS = 6

    def __init__(self, cmd_executor, api, grid_engine, launch_adapter, host_storage, parent_run_id, instance_disk,
                 instance_image, cmd_template, price_type, region_id, queue, hostlist, owner_param_name,
                 polling_timeout=_POLL_TIMEOUT, polling_delay=_POLL_DELAY,
                 ge_polling_timeout=_GE_POLL_TIMEOUT, instance_launch_params=None, clock=Clock()):
        """
        Grid engine scale up handler.

        Manages additional workers scaling up.

        :param cmd_executor: Cmd executor.
        :param api: Cloud pipeline client.
        :param grid_engine: Grid engine client.
        :param launch_adapter: Grid engine launch adapter.
        :param host_storage: Additional hosts storage.
        :param parent_run_id: Additional nodes parent run id.
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
        self.launch_adapter = launch_adapter
        self.host_storage = host_storage
        self.parent_run_id = parent_run_id
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
                run_grid_engine_tasks = self.api.load_task(run_id, self.launch_adapter.get_worker_init_task_name())
                if any(run_grid_engine_task.get('status') == 'SUCCESS'
                       for run_grid_engine_task in run_grid_engine_tasks):
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


class DoNothingScaleDownHandler:

    def scale_down(self, child_host):
        pass


class GridEngineScaleDownHandler:

    def __init__(self, cmd_executor, grid_engine, common_utils):
        """
        Grid engine scale down handler.

        Manages additional workers scaling down.

        :param cmd_executor: Cmd executor.
        :param grid_engine: Grid engine client.
        :param common_utils: helpful stuff
        """
        self.executor = cmd_executor
        self.grid_engine = grid_engine
        self.common_utils = common_utils

    def scale_down(self, child_host):
        """
        Scales down an additional worker.

        It stops the corresponding run, removes it from the GE cluster configuration and
        removes host from master hosts.

        :param child_host: Host name of an additional worker to be scaled down.
        :return: True if the run stopping was successful, False otherwise.
        """
        Logger.info('Disabling additional worker %s...' % child_host)
        self.grid_engine.disable_host(child_host)
        jobs = self.grid_engine.get_jobs()
        running_jobs = [job for job in jobs if job.state == GridEngineJobState.RUNNING]
        disabled_host_jobs = [job for job in running_jobs if child_host in job.hosts]
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
        run_id = self.common_utils.get_run_id_from_host(host)
        Logger.info('Stopping run #%s...' % run_id)
        self.executor.execute('pipe stop --yes %s' % run_id)
        Logger.info('Run #%s was stopped.' % run_id)

    def _remove_host_from_hosts(self, host):
        Logger.info('Removing host %s from hosts...' % host)
        self.executor.execute('remove_from_hosts "%s"' % host)


class GridEngineScaleDownOrchestrator:

    def __init__(self, scale_down_handler, grid_engine, host_storage, batch_size):
        """
        Grid engine scale down orchestrator.

        Handles additional workers batch scaling down.
        Scales down no more than a configured number of additional workers at once.

        :param scale_down_handler: Scaling down handler.
        :param grid_engine: Grid engine client.
        :param host_storage: Additional hosts storage.
        :param batch_size: Scaling up batch size.
        """
        self.scale_down_handler = scale_down_handler
        self.grid_engine = grid_engine
        self.host_storage = host_storage
        self.batch_size = batch_size

    def scale_down(self, inactive_additional_hosts):
        hosts_to_scale_down = list(itertools.islice(self.select_hosts_to_scale_down(inactive_additional_hosts),
                                                    self.batch_size))
        number_of_threads = len(hosts_to_scale_down)
        number_of_finished_threads = 0
        Logger.info('Scaling down %s additional workers...' % number_of_threads)
        for host in hosts_to_scale_down:
            succeed = self.scale_down_handler.scale_down(host)
            if succeed:
                self.host_storage.remove_host(host)
            number_of_finished_threads += 1
            if number_of_finished_threads < number_of_threads:
                Logger.info('Only %s/%s additional workers have been scaled down.'
                            % (number_of_finished_threads, number_of_threads))
        Logger.info('All %s/%s additional workers have been scaled down.'
                    % (number_of_threads, number_of_threads))

    def select_hosts_to_scale_down(self, hosts):
        for host in sorted(hosts, key=self.grid_engine.get_host_supply, reverse=True):
            yield host


class GridEngineAutoscaler:

    def __init__(self, grid_engine, job_validator, demand_selector,
                 cmd_executor, scale_up_orchestrator, scale_down_orchestrator, host_storage,
                 static_host_storage, scale_up_timeout, scale_down_timeout, max_additional_hosts, idle_timeout=30,
                 clock=Clock()):
        """
        Grid engine autoscaler.

        It scales up additional workers if some jobs are waiting in grid engine queue
        for more than the given time internal.

        It scales down existing additional workers if there are no waiting jobs in grid engine queue
        and there were no new jobs for the given time interval.

        :param grid_engine: Grid engine.
        :param job_validator: Job validator.
        :param demand_selector: Demand selector.
        :param cmd_executor: Cmd executor.
        :param scale_up_orchestrator: Scaling up orchestrator.
        :param scale_down_orchestrator: Scaling down orchestrator.
        :param host_storage: Additional hosts storage.
        :param static_host_storage: Static workers host storage
        :param scale_up_timeout: Maximum number of seconds job could wait in queue
        before autoscaler will scale up the cluster.
        :param scale_down_timeout: Maximum number of seconds the waiting queue could be empty
        before autoscaler will scale down the cluster.
        :param max_additional_hosts: Maximum number of additional hosts that autoscaler can launch.
        :param clock: Clock.
        :param idle_timeout: Maximum number of seconds a host could wait for a new job before getting scaled-down.
        """
        self.grid_engine = grid_engine
        self.demand_selector = demand_selector
        self.job_validator = job_validator
        self.executor = cmd_executor
        self.scale_up_orchestrator = scale_up_orchestrator
        self.scale_down_orchestrator = scale_down_orchestrator
        self.host_storage = host_storage
        self.static_host_storage = static_host_storage
        self.scale_up_timeout = timedelta(seconds=scale_up_timeout)
        self.scale_down_timeout = timedelta(seconds=scale_down_timeout)
        self.max_additional_hosts = max_additional_hosts
        self.clock = clock

        self.latest_running_job = None
        self.idle_timeout = timedelta(seconds=idle_timeout)

    def scale(self):
        now = self.clock.now()
        Logger.info('Init: Scaling.')
        additional_hosts = self.host_storage.load_hosts()
        Logger.info('There are %s/%s additional workers.' % (len(additional_hosts), self.max_additional_hosts))
        updated_jobs = self.grid_engine.get_jobs()
        running_jobs = [job for job in updated_jobs if job.state == GridEngineJobState.RUNNING]
        if running_jobs:
            self.host_storage.update_running_jobs_host_activity(running_jobs, now)
            self.static_host_storage.update_running_jobs_host_activity(running_jobs, now)
            self.latest_running_job = sorted(running_jobs, key=lambda job: job.datetime, reverse=True)[0]
        if not self.max_additional_hosts:
            Logger.info('Done: Scaling.')
            return
        pending_jobs = [job for job in updated_jobs if job.state == GridEngineJobState.PENDING]
        waiting_jobs = self._get_valid_jobs(pending_jobs)
        Logger.info('There are %s waiting jobs.' % len(waiting_jobs))
        if waiting_jobs:
            expiration_datetimes = [job.datetime + self.scale_up_timeout for job in waiting_jobs]
            expired_jobs = [job for index, job in enumerate(waiting_jobs) if now >= expiration_datetimes[index]]
            if expired_jobs:
                Logger.info('There are %s waiting jobs that are in queue for more than %s seconds. '
                            'Scaling up is required.' % (len(expired_jobs), self.scale_up_timeout.seconds))
                if len(additional_hosts) < self.max_additional_hosts:
                    Logger.info('There are %s/%s additional workers. Scaling up will be performed.' %
                                (len(additional_hosts), self.max_additional_hosts))
                    resource_demands = list(self.demand_selector.select(waiting_jobs))
                    resource_demand = functools.reduce(operator.add, resource_demands, IntegralDemand())
                    Logger.info('Waiting jobs require: '
                                '{cpu} cpu, {gpu} gpu, {mem} mem.'
                                .format(cpu=resource_demand.cpu, gpu=resource_demand.gpu, mem=resource_demand.mem))
                    remaining_additional_hosts = self.max_additional_hosts - len(additional_hosts)
                    self.scale_up(resource_demands, remaining_additional_hosts)
                else:
                    Logger.info('There are %s/%s additional workers. Scaling up is aborted.' %
                                (len(additional_hosts), self.max_additional_hosts))
                    Logger.info('Probable deadlock situation observed. Scaling down will be attempted.')
                    self._scale_down(running_jobs, additional_hosts)
            else:
                Logger.info('There are 0 waiting jobs that are in queue for more than %s seconds. '
                            'Scaling up is not required.' % self.scale_up_timeout.seconds)
        else:
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
                Logger.info('There are 0 previously running jobs. Scaling down is required.')
                self._scale_down(running_jobs, additional_hosts, now)
        post_scale_additional_hosts = self.host_storage.load_hosts()
        Logger.info('There are %s/%s additional workers.'
                    % (len(post_scale_additional_hosts), self.max_additional_hosts))
        Logger.info('Done: Scaling.')

    def _get_valid_jobs(self, jobs):
        Logger.info('Validating %s jobs...' % len(jobs))
        valid_jobs, invalid_jobs = self.job_validator.validate(jobs)
        if invalid_jobs:
            Logger.warn('The following jobs cannot be satisfied with the requested resources '
                        'and therefore will be killed: #{}'
                        .format(', #'.join(job.id for job in invalid_jobs)),
                        crucial=True)
            self.grid_engine.kill_jobs(invalid_jobs)
        return valid_jobs

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
            self.scale_down(inactive_additional_hosts)
        else:
            Logger.info('There are 0 additional workers to scale down. Scaling down is aborted.')

    def _filter_valid_idle_hosts(self, inactive_host_candidates, scaling_period_start):
        inactive_hosts = []
        hosts_activity = self.host_storage.get_hosts_activity(inactive_host_candidates)
        for host, last_activity in hosts_activity.items():
            if scaling_period_start >= last_activity + self.idle_timeout:
                inactive_hosts.append(host)
        return inactive_hosts

    def scale_down(self, inactive_additional_hosts):
        """
        Scales down an additional worker.

        :param inactive_additional_hosts: Host names to be scaled down.
        """
        Logger.info('Start grid engine SCALING DOWN for %s hosts.' % len(inactive_additional_hosts))
        self.scale_down_orchestrator.scale_down(inactive_additional_hosts)


class GridEngineAutoscalingDaemon:

    def __init__(self, autoscaler, worker_validator, worker_tags_handler, polling_timeout=5):
        """
        Grid engine autoscaling daemon.

        :param autoscaler: Autoscaler.
        :param worker_validator: Additional workers validator.
        :param worker_tags_handler: Additional workers tags handler.
        :param polling_timeout: Autoscaler polling timeout - in seconds.
        """
        self.autoscaler = autoscaler
        self.worker_validator = worker_validator
        self.worker_tags_handler = worker_tags_handler
        self.timeout = polling_timeout

    def start(self):
        Logger.info('Launching grid engine autoscaling daemon...')
        while True:
            try:
                time.sleep(self.timeout)
                self.worker_validator.validate()
                self.autoscaler.scale()
                self.worker_tags_handler.process_tags()
            except KeyboardInterrupt:
                Logger.warn('Manual stop of the autoscaler daemon.', crucial=True)
                break
            except Exception as e:
                Logger.warn('Scaling has failed due to %s' % str(e), crucial=True)
                Logger.warn(traceback.format_exc())


class DoNothingAutoscalingDaemon:

    def start(self):
        pass
