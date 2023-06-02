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

import logging

from mock import MagicMock, Mock

from pipeline.hpc.autoscaler import GridEngineScaleUpHandler
from pipeline.hpc.host import MemoryHostStorage
from pipeline.hpc.instance import Instance
from utils import assert_first_argument_contained

try:
    from queue import Queue
except ImportError:
    from Queue import Queue

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

HOSTNAME = 'hostname'
POD_IP = '127.0.0.1'
RUN_ID = '12345'

cmd_executor = Mock()
grid_engine = Mock()
api = Mock()
host_storage = MemoryHostStorage()
instance_helper = Mock()
parent_run_id = 'parent_run_id'
instance_disk = 'instance_disk'
instance_type = 'instance_type'
instance_image = 'instance_image'
cmd_template = 'cmd_template'
price_type = 'price_type'
owner = 'owner'
owner_param_name = 'owner_param_name'
region_id = 1
instance_cores = 4
polling_timeout = 600
instance = Instance(name='instance', price_type=price_type, cpu=4, mem=16, gpu=0)
queue_name = 'main.q'
hostlist = '@allhosts'
run_id_queue = Queue()
scale_up_handler = GridEngineScaleUpHandler(cmd_executor=cmd_executor, api=api, grid_engine=grid_engine,
                                            host_storage=host_storage, parent_run_id=parent_run_id,
                                            instance_disk=instance_disk, instance_image=instance_image,
                                            cmd_template=cmd_template, price_type=price_type,
                                            region_id=region_id, queue=queue_name, hostlist=hostlist,
                                            owner_param_name=owner_param_name,
                                            polling_timeout=polling_timeout, polling_delay=0)


def setup_function():
    not_initialized_run = {'initialized': False, 'podId': HOSTNAME}
    initialized_pod_run = {'initialized': False, 'podId': HOSTNAME, 'podIP': POD_IP}
    initialized_run = {'initialized': True, 'podId': HOSTNAME, 'podIP': POD_IP}
    api.load_run = MagicMock(side_effect=[not_initialized_run] * 4 + [initialized_pod_run] * 4 + [initialized_run])
    api.load_task = MagicMock(return_value=[{'status': 'SUCCESS'}])
    cmd_executor.execute_to_lines = MagicMock(return_value=[RUN_ID])
    instance_helper.select_instance = MagicMock(return_value=
        Instance.from_cp_response({
            "sku": "78J32SRETMXEPY86",
            "name": "c5.xlarge",
            "termType": "OnDemand",
            "operatingSystem": "Linux",
            "memory": 96,
            "memoryUnit": "GiB",
            "instanceFamily": "Compute optimized",
            "gpu": 0,
            "regionId": 1,
            "vcpu": instance_cores
        }))
    cmd_executor.execute = MagicMock()
    grid_engine.enable_host = MagicMock()
    host_storage.clear()


def test_waiting_for_run_to_initialize():
    scale_up_handler.scale_up(instance, owner, run_id_queue)

    api.load_run.assert_called()
    assert api.load_run.call_count == 9


def test_enabling_worker_in_grid_engine():
    scale_up_handler.scale_up(instance, owner, run_id_queue)

    grid_engine.enable_host.assert_called_with(HOSTNAME)


def test_updating_hosts():
    scale_up_handler.scale_up(instance, owner, run_id_queue)

    assert_first_argument_contained(cmd_executor.execute, 'add_to_hosts')
    assert_first_argument_contained(cmd_executor.execute,  HOSTNAME)
    assert_first_argument_contained(cmd_executor.execute,  POD_IP)


def test_scale_up_add_host_to_storage():
    scale_up_handler.scale_up(instance, owner, run_id_queue)

    assert [HOSTNAME] == host_storage.load_hosts()
