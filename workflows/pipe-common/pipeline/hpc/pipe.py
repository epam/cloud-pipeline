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

import traceback
from datetime import datetime, timedelta

from pipeline.api.api import APIError
from pipeline.hpc.event import InsufficientInstanceEvent, FailingInstanceEvent, AvailableInstanceEvent
from pipeline.hpc.instance.provider import GridEngineInstanceProvider, Instance
from pipeline.hpc.logger import Logger
from pipeline.hpc.valid import WorkerValidator, WorkerValidatorHandler


class CloudPipelineInstanceProvider(GridEngineInstanceProvider):

    def __init__(self, api, region_id, price_type):
        self.api = api
        self.region_id = region_id
        self.price_type = price_type

    def provide(self):
        allowed_instance_types = self.api.get_allowed_instance_types(self.region_id, self.price_type == 'spot')
        docker_instance_types = allowed_instance_types['cluster.allowed.instance.types.docker']
        return [Instance.from_cp_response(instance) for instance in docker_instance_types]


class GridEngineWorkerRecorder:

    def record(self, run_id):
        pass


class CloudPipelineWorkerRecorder(GridEngineWorkerRecorder):

    def __init__(self, api, event_manager, clock):
        self._api = api
        self._event_manager = event_manager
        self._clock = clock
        self._datetime_format = '%Y-%m-%d %H:%M:%S.%f'

    def record(self, run_id):
        try:
            Logger.info('Recording details of additional worker #%s...' % run_id)
            run = self._api.load_run_efficiently(run_id)
            run_stopped = self._to_datetime(run.get('endDate'))
            instance_type = run.get('instance', {}).get('nodeType')
            run_status = run.get('status')
            run_status_reason = run.get('stateReasonMessage')
            if run_status == 'FAILURE':
                if run_status_reason == 'Insufficient instance capacity.':
                    Logger.warn('Detected insufficient instance type %s.' % run.get('instance', {}).get('nodeType'))
                    self._event_manager.register(InsufficientInstanceEvent(instance_type=instance_type,
                                                                           date=run_stopped))
                else:
                    Logger.warn('Detected failing instance type %s.' % run.get('instance', {}).get('nodeType'))
                    self._event_manager.register(FailingInstanceEvent(instance_type=instance_type,
                                                                      date=run_stopped))
            else:
                self._event_manager.register(AvailableInstanceEvent(instance_type=instance_type,
                                                                    date=self._clock.now()))
        except Exception as e:
            Logger.warn('Recording details of additional worker #%s has failed due to %s.' % (run_id, str(e)),
                        crucial=True)
            Logger.warn(traceback.format_exc())

    def _to_datetime(self, run_started):
        if not run_started:
            return None
        return datetime.strptime(run_started, self._datetime_format)


class CloudPipelineWorkerValidator(WorkerValidator):

    def __init__(self, cmd_executor, api, host_storage, grid_engine, scale_down_handler, handlers,
                 common_utils, dry_run):
        """
        Grid engine worker validator.

        The reason why validator exists is that some additional hosts may be broken due to different circumstances.
        F.e. autoscaler has failed while configuring additional host so it is partly configured and has to be stopped.
        F.e. a spot worker instance was preempted and it has to be removed from its autoscaled cluster.

        :param cmd_executor: Cmd executor.
        :param api: Cloud pipeline client.
        :param host_storage: Additional hosts storage.
        :param grid_engine: Grid engine.
        :param scale_down_handler: Scale down handler.
        :param handlers: Validation handlers.
        :param common_utils: helpful stuff
        :param dry_run: Dry run flag.
        """
        self.executor = cmd_executor
        self.api = api
        self.host_storage = host_storage
        self.grid_engine = grid_engine
        self.scale_down_handler = scale_down_handler
        self.handlers = handlers
        self.common_utils = common_utils
        self.dry_run = dry_run

    def validate(self):
        """
        Finds and removes any additional hosts which aren't valid execution hosts in GE or active runs.
        """
        hosts = self.host_storage.load_hosts()
        if not hosts:
            Logger.info('Skip: Workers validation.')
            return
        Logger.info('Init: Workers validation.')
        valid_hosts, invalid_hosts = [], []
        for host in hosts:
            if any(not handler.is_valid(host) for handler in self.handlers):
                invalid_hosts.append(host)
                continue
        for host in invalid_hosts:
            run_id = self.common_utils.get_run_id_from_host(host)
            Logger.warn('Invalid additional host %s was found. '
                        'It will be downscaled.' % host,
                        crucial=True)
            if self.dry_run:
                continue
            self._try_stop_worker(run_id)
            self._try_disable_worker(host, run_id)
            self._try_kill_invalid_host_jobs(host)
            self.grid_engine.delete_host(host, skip_on_failure=True)
            self._remove_worker_from_hosts(host)
            self.host_storage.remove_host(host)
        Logger.info('Done: Workers validation.')

    def _try_stop_worker(self, run_id):
        try:
            Logger.info('Stopping run #%s...' % run_id)
            self.api.stop_run(run_id)
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


class CloudPipelineWorkerValidatorHandler(WorkerValidatorHandler):

    _RUNNING_STATUS = 'RUNNING'

    def __init__(self, api, common_utils):
        self._api = api
        self._common_utils = common_utils

    def is_valid(self, host):
        run_id = self._common_utils.get_run_id_from_host(host)
        if self._is_running(run_id):
            return True
        Logger.warn('Not running additional host %s was found.' % host, crucial=True)
        return False

    def _is_running(self, run_id):
        try:
            run_info = self._api.load_run_efficiently(run_id)
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


class LastActionMarker:

    def __init__(self):
        self.last_action_timestamp = None
        self.last_tag_timestamp = None


class GridEngineWorkerTagsHandler:

    def process_tags(self):
        pass


class CloudPipelineWorkerTagsHandler(GridEngineWorkerTagsHandler):

    def __init__(self, api, active_timeout, active_tag, host_storage, static_host_storage, clock, common_utils,
                 dry_run):
        """
        Processes active additional workers tags: if at least one job is running at the additional host
        the corresponding run shall be tagged.

        :param api: Cloud pipeline client.
        :param active_timeout: Indicates how many seconds must pass before the run is recognized as active.
        :param active_tag: Active worker tag.
        :param host_storage: Additional hosts storage.
        :param static_host_storage: Static workers host storage.
        :param clock: Clock.
        :param common_utils: helpful stuff.
        :param dry_run: Dry run flag.
        """
        self.api = api
        self.host_storage = host_storage
        self.static_host_storage = static_host_storage
        self.clock = clock
        self.last_monitored_hosts = {}
        self.active_timeout = timedelta(seconds=active_timeout)
        self.active_tag = active_tag
        self.common_utils = common_utils
        self.dry_run = dry_run
        self.static_hosts = self.static_host_storage.load_hosts()

    def process_tags(self):
        try:
            Logger.info('Init: Tags processing.')
            current_hosts = self.host_storage.load_hosts()
            hosts_activity = self.host_storage.get_hosts_activity(current_hosts)
            hosts_activity.update(self.static_host_storage.get_hosts_activity(self.static_hosts))
            monitored_hosts = list(self.last_monitored_hosts.keys())
            current_hosts += self.static_hosts
            self._process_current_hosts(current_hosts, hosts_activity)
            self._process_outdated_hosts(monitored_hosts, current_hosts)
            Logger.info('Done: Tags processing.')
        except Exception as e:
            Logger.warn('Fail: Tags processing due to %s' % str(e))
            Logger.warn(traceback.format_exc())

    def _run_is_active(self, timestamp):
        return timestamp > self.clock.now() - self.active_timeout

    def _tag_run(self, host, timestamp, last_monitored_timestamps):
        if self.dry_run:
            return
        run_id = self.common_utils.get_run_id_from_host(host)
        self._add_worker_tag(run_id)
        last_monitored_timestamps.last_action_timestamp = timestamp
        last_monitored_timestamps.last_tag_timestamp = self.clock.now()
        self.last_monitored_hosts.update({host: last_monitored_timestamps})

    def _untag_run(self, host, timestamp=None, last_monitored_timestamps=None):
        Logger.info("Removing tag from run for host '%s'." % host)
        if self.dry_run:
            return
        run_id = self.common_utils.get_run_id_from_host(host)
        self._remove_worker_tag(run_id)
        if not last_monitored_timestamps:
            del self.last_monitored_hosts[host]
            return
        last_monitored_timestamps.last_action_timestamp = timestamp
        last_monitored_timestamps.last_tag_timestamp = None
        self.last_monitored_hosts.update({host: last_monitored_timestamps})

    def _process_current_hosts(self, current_hosts, hosts_activity):
        for current_host in current_hosts:
            timestamp = hosts_activity[current_host]
            if not timestamp:
                continue
            last_monitored_timestamps = self.last_monitored_hosts.get(current_host, None)
            if not last_monitored_timestamps:
                last_action_marker = LastActionMarker()
                self.last_monitored_hosts.update({current_host: last_action_marker})
                continue
            if self._run_is_active(timestamp):
                if not last_monitored_timestamps.last_tag_timestamp:
                    Logger.info("Adding tag to run for host '%s'." % current_host)
                    self._tag_run(current_host, timestamp, last_monitored_timestamps)
                # do nothing if active run was already tagged
                continue
            if last_monitored_timestamps and last_monitored_timestamps.last_tag_timestamp:
                self._untag_run(current_host, timestamp, last_monitored_timestamps)

    def _process_outdated_hosts(self, monitored_hosts, current_hosts):
        for monitored_host in monitored_hosts:
            if monitored_host not in current_hosts:
                self._untag_run(monitored_host)

    def _add_worker_tag(self, run_id):
        run = self.api.load_run_efficiently(run_id)
        tags = run.get('tags') or {}
        tags.update({self.active_tag: 'true'})
        self.api.update_pipeline_run_tags(run_id, tags)

    def _remove_worker_tag(self, run_id):
        run = self.api.load_run_efficiently(run_id)
        tags = run.get('tags') or {}
        if self.active_tag in tags:
            del tags[self.active_tag]
            self.api.update_pipeline_run_tags(run_id, tags)
