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

from datetime import datetime

from mock import MagicMock, Mock

from scripts.autoscale_sge import GridEngineScaleDownHandler, GridEngineJob, GridEngineJobState
from utils import assert_first_argument_contained, assert_first_argument_not_contained

HOSTNAME = 'hostname'
ANOTHER_HOSTNAME = 'another-hostname'
RUN_ID = '12345'

cmd_executor = Mock()
grid_engine = Mock()
default_hostfile = 'default_hostfile'
instance_cores = 4
scale_down_handler = GridEngineScaleDownHandler(cmd_executor=cmd_executor, grid_engine=grid_engine,
                                                default_hostfile=default_hostfile, instance_cores=instance_cores)


def setup_function():
    grid_engine.get_jobs = MagicMock(return_value=[])
    grid_engine.enable_host = MagicMock()
    grid_engine.disable_host = MagicMock()
    grid_engine.delete_host = MagicMock()
    cmd_executor.execute = MagicMock()


def test_not_scaling_down_if_host_has_running_jobs():
    submit_datetime = datetime(2018, 12, 29, 11, 00, 00)
    jobs = [
        GridEngineJob(
            id=1,
            name='name1',
            user='user',
            state=GridEngineJobState.RUNNING,
            datetime=submit_datetime,
            host=HOSTNAME
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)

    assert not scale_down_handler.scale_down(HOSTNAME)

    grid_engine.disable_host.assert_called()
    grid_engine.enable_host.assert_called()
    grid_engine.delete_host.assert_not_called()
    assert_first_argument_not_contained(cmd_executor.execute, HOSTNAME)


def test_scaling_down_if_host_has_no_running_jobs():
    submit_datetime = datetime(2018, 12, 29, 11, 00, 00)
    jobs = [
        GridEngineJob(
            id=1,
            name='name1',
            user='user',
            state=GridEngineJobState.RUNNING,
            datetime=submit_datetime,
            host=ANOTHER_HOSTNAME
        )
    ]
    grid_engine.get_jobs = MagicMock(return_value=jobs)

    assert scale_down_handler.scale_down(HOSTNAME)

    grid_engine.disable_host.assert_called()
    grid_engine.enable_host.assert_not_called()
    grid_engine.delete_host.assert_called()
    assert_first_argument_contained(cmd_executor.execute, HOSTNAME)


def test_scaling_down_stops_pipeline():
    scale_down_handler.scale_down(HOSTNAME)

    assert_first_argument_contained(cmd_executor.execute, 'pipe stop')


def test_scaling_down_updates_hosts():
    scale_down_handler.scale_down(HOSTNAME)

    assert_first_argument_contained(cmd_executor.execute, HOSTNAME)
    assert_first_argument_contained(cmd_executor.execute, '/etc/hosts')


def test_scaling_down_updates_default_hostfile():
    scale_down_handler.scale_down(HOSTNAME)

    assert_first_argument_contained(cmd_executor.execute, HOSTNAME)
    assert_first_argument_contained(cmd_executor.execute, default_hostfile)


def test_scaling_down_decreases_parallel_environment_slots():
    scale_down_handler.scale_down(HOSTNAME)

    grid_engine.decrease_parallel_environment_slots.assert_called_with(instance_cores)
