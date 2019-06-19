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

from mock import MagicMock, Mock
from utils import assert_first_argument_contained

from scripts.autoscale_sge import GridEngineScaleUpHandler, MemoryHostStorage

HOSTNAME = 'hostname'
POD_IP = '127.0.0.1'
RUN_ID = '12345'

cmd_executor = Mock()
grid_engine = Mock()
pipe = Mock()
host_storage = MemoryHostStorage()
parent_run_id = 'parent_run_id'
default_hostfile = 'default_hostfile'
instance_disk = 'instance_disk'
instance_type = 'instance_type'
instance_image = 'instance_image'
price_type = 'price_type'
instance_cores = 4
polling_timeout = 600
scale_up_handler = GridEngineScaleUpHandler(cmd_executor=cmd_executor, pipe=pipe, grid_engine=grid_engine,
                                            host_storage=host_storage, parent_run_id=parent_run_id,
                                            default_hostfile=default_hostfile, instance_disk=instance_disk,
                                            instance_type=instance_type, instance_image=instance_image,
                                            price_type=price_type, instance_cores=instance_cores,
                                            polling_timeout=polling_timeout, polling_delay=0)


def setup_function():
    not_initialized_run = {'initialized': False, 'podId': HOSTNAME}
    initialized_pod_run = {'initialized': False, 'podId': HOSTNAME, 'podIP': POD_IP}
    initialized_run = {'initialized': True, 'podId': HOSTNAME, 'podIP': POD_IP}
    pipe.load_run = MagicMock(side_effect=[not_initialized_run] * 4 + [initialized_pod_run] * 4 + [initialized_run])
    cmd_executor.execute_to_lines = MagicMock(return_value=[RUN_ID])
    cmd_executor.execute = MagicMock()
    grid_engine.enable_host = MagicMock()
    host_storage.clear()


def test_waiting_for_run_to_initialize():
    scale_up_handler.scale_up()

    pipe.load_run.assert_called()
    assert pipe.load_run.call_count == 9


def test_enabling_worker_in_grid_engine():
    scale_up_handler.scale_up()

    grid_engine.enable_host.assert_called_with(HOSTNAME)


def test_updating_hosts():
    scale_up_handler.scale_up()

    assert_first_argument_contained(cmd_executor.execute, '%s\t%s' % (POD_IP, HOSTNAME))
    assert_first_argument_contained(cmd_executor.execute, '/etc/hosts')


def test_updating_default_hostfile():
    scale_up_handler.scale_up()

    assert_first_argument_contained(cmd_executor.execute, HOSTNAME)
    assert_first_argument_contained(cmd_executor.execute, default_hostfile)


def test_scale_up_add_host_to_storage():
    scale_up_handler.scale_up()

    assert [HOSTNAME] == host_storage.load_hosts()


def test_scale_up_increase_parallel_environment_slots():
    scale_up_handler.scale_up()

    grid_engine.increase_parallel_environment_slots.assert_called_with(instance_cores)
