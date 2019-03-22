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
    hosts = [str(run_id) for run_id in range(0, 2 * max_additional_hosts)]

    def add_host():
        host = hosts.pop()
        host_storage.add_host(host)
        return host

    autoscaler.scale_up = MagicMock(side_effect=add_host)
    autoscaler.host_storage.clear()


def test_scale_up_if_some_of_the_jobs_are_in_queue_for_more_than_scale_up_timeout():
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
            datetime=submit_datetime
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_up_timeout))

    autoscaler.scale()

    autoscaler.scale_up.assert_called()


def test_not_scale_up_if_none_of_the_jobs_are_in_queue_for_more_than_scale_up_timeout():
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
            datetime=submit_datetime
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_up_timeout - 1))

    autoscaler.scale()

    autoscaler.scale_up.assert_not_called()


def test_that_scale_up_will_not_launch_more_additional_workers_than_limit():
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
            datetime=submit_datetime
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_up_timeout))

    for _ in range(0, max_additional_hosts * 2):
        autoscaler.scale()

    assert autoscaler.scale_up.call_count == 2
    assert len(autoscaler.host_storage.load_hosts()) == max_additional_hosts


def test_scale_up_if_some_of_the_array_jobs_are_in_queue_for_more_than_scale_up_timeout():
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
            datetime=submit_datetime,
            array=range(5, 10)
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_up_timeout))

    autoscaler.scale()

    autoscaler.scale_up.assert_called()


def test_not_scale_up_if_none_of_the_array_jobs_are_in_queue_for_more_than_scale_up_timeout():
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
            datetime=submit_datetime,
            array=range(5, 10)
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_up_timeout - 1))

    autoscaler.scale()

    autoscaler.scale_up.assert_not_called()


def test_not_scale_up_if_number_of_additional_workers_is_already_equals_to_the_limit_but_there_pending_jobs():
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
            datetime=submit_datetime
        )
    ]
    for run_id in range(0, autoscaler.max_additional_hosts):
        autoscaler.host_storage.add_host(str(run_id))
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_up_timeout))

    autoscaler.scale()

    autoscaler.scale_up.assert_not_called()


def test_not_scale_up_if_number_of_additional_workers_is_already_equals_to_the_limit_but_there_pending_array_jobs():
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
            datetime=submit_datetime,
            array=range(5, 10)
        )
    ]
    for run_id in range(0, autoscaler.max_additional_hosts):
        autoscaler.host_storage.add_host(str(run_id))
    grid_engine.get_jobs = MagicMock(return_value=jobs)
    clock.now = MagicMock(return_value=submit_datetime + timedelta(seconds=scale_up_timeout))

    autoscaler.scale()

    autoscaler.scale_up.assert_not_called()
