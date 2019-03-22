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

from datetime import datetime, timedelta

from mock import MagicMock, Mock

from scripts.autoscale_sge import GridEngineAutoscaler, GridEngineJob, GridEngineJobState, Clock, MemoryHostStorage

MASTER_HOST = 'pipeline-1000'
ADDITIONAL_HOST = 'pipeline-1001'

cmd_executor = Mock()
grid_engine = Mock()
host_storage = MemoryHostStorage()
scale_up_timeout = 30
scale_down_timeout = 30
max_additional_hosts = 2
clock = Clock()
autoscaler = GridEngineAutoscaler(grid_engine=grid_engine,
                                  cmd_executor=cmd_executor,
                                  scale_up_handler=None,
                                  scale_down_handler=None,
                                  host_storage=host_storage,
                                  scale_up_timeout=scale_up_timeout,
                                  scale_down_timeout=scale_down_timeout,
                                  max_additional_hosts=max_additional_hosts,
                                  clock=clock)


def setup_function():
    autoscaler.scale_down = MagicMock()
    autoscaler.host_storage.clear()
    autoscaler.host_storage.add_host(ADDITIONAL_HOST)


def test_scale_down_if_all_jobs_are_running_for_more_than_scale_down_timeout():
    submit_datetime = datetime(2018, 12, 21, 11, 00, 00)
    jobs = [
        GridEngineJob(
            id=1,
            name='name1',
            user='user',
            state=GridEngineJobState.RUNNING,
            datetime=submit_datetime,
            host=MASTER_HOST
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_down_timeout))

    autoscaler.scale()

    autoscaler.scale_down.assert_called_with(ADDITIONAL_HOST)


def test_not_scale_down_if_all_jobs_are_running_for_less_than_scale_down_timeout():
    submit_datetime = datetime(2018, 12, 21, 11, 00, 00)
    jobs = [
        GridEngineJob(
            id=1,
            name='name1',
            user='user',
            state=GridEngineJobState.RUNNING,
            datetime=submit_datetime,
            host=MASTER_HOST
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_down_timeout - 1))

    autoscaler.scale()

    autoscaler.scale_down.assert_not_called()


def test_not_scale_down_if_all_jobs_are_running_for_more_than_scale_down_timeout_and_additional_host_is_active():
    submit_datetime = datetime(2018, 12, 21, 11, 00, 00)
    jobs = [
        GridEngineJob(
            id=1,
            name='name1',
            user='user',
            state=GridEngineJobState.RUNNING,
            datetime=submit_datetime,
            host=ADDITIONAL_HOST
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_down_timeout))

    autoscaler.scale()

    autoscaler.scale_down.assert_not_called()


def test_that_scale_down_only_stops_inactive_additional_hosts():
    submit_datetime = datetime(2018, 12, 21, 11, 00, 00)
    jobs = [
        GridEngineJob(
            id=1,
            name='name1',
            user='user',
            state=GridEngineJobState.RUNNING,
            datetime=submit_datetime,
            host=ADDITIONAL_HOST
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_down_timeout))
    inactive_hosts = ['inactive-host-1', 'inactive-host-2']
    for inactive_host in inactive_hosts:
        autoscaler.host_storage.add_host(inactive_host)

    for _ in range(0, len(inactive_hosts) * 2):
        autoscaler.scale()

    for inactive_host in inactive_hosts:
        autoscaler.scale_down.assert_any_call(inactive_host)

    hosts = autoscaler.host_storage.load_hosts()
    assert len(hosts) == 1
    assert ADDITIONAL_HOST in hosts


def test_scale_down_if_there_are_no_pending_and_running_jobs_for_more_than_scale_down_timeout():
    submit_datetime = datetime(2018, 12, 21, 11, 00, 00)
    jobs = [
        GridEngineJob(
            id=1,
            name='name1',
            user='user',
            state=GridEngineJobState.RUNNING,
            datetime=submit_datetime,
            host=MASTER_HOST
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime)

    autoscaler.scale()

    autoscaler.scale_down.assert_not_called()
    hosts = autoscaler.host_storage.load_hosts()
    assert len(hosts) == 1
    assert ADDITIONAL_HOST in hosts

    grid_engine.get_jobs = MagicMock(return_value=[])
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_down_timeout))

    autoscaler.scale()

    autoscaler.scale_down.assert_called_with(ADDITIONAL_HOST)
    assert len(autoscaler.host_storage.load_hosts()) == 0


def test_not_scale_down_if_there_are_pending_jobs_and_running_jobs():
    submit_datetime = datetime(2018, 12, 21, 11, 00, 00)
    jobs = [
        GridEngineJob(
            id=1,
            name='name1',
            user='user',
            state=GridEngineJobState.RUNNING,
            datetime=submit_datetime,
            host=MASTER_HOST
        ),
        GridEngineJob(
            id=2,
            name='name2',
            user='user',
            state=GridEngineJobState.PENDING,
            datetime=submit_datetime + timedelta(seconds=scale_down_timeout),
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_down_timeout))

    autoscaler.scale()

    autoscaler.scale_down.assert_not_called()


def test_not_scale_down_if_there_are_pending_jobs_and_no_running_jobs_yet():
    submit_datetime = datetime(2018, 12, 21, 11, 00, 00)
    jobs = [
        GridEngineJob(
            id=1,
            name='name1',
            user='user',
            state=GridEngineJobState.PENDING,
            datetime=submit_datetime
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime)

    autoscaler.scale()

    autoscaler.scale_down.assert_not_called()


def test_host_is_not_removed_from_storage_if_scaling_down_is_aborted():
    submit_datetime = datetime(2018, 12, 21, 11, 00, 00)
    jobs = [
        GridEngineJob(
            id=1,
            name='name1',
            user='user',
            state=GridEngineJobState.RUNNING,
            datetime=submit_datetime,
            host=MASTER_HOST
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_down_timeout))
    autoscaler.scale_down = MagicMock(return_value=False)

    autoscaler.scale()

    autoscaler.scale_down.assert_called()
    assert ADDITIONAL_HOST in autoscaler.host_storage.load_hosts()
