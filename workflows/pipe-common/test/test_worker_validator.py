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

from mock import Mock, MagicMock

from scripts.autoscale_sge import GridEngineWorkerValidator, MemoryHostStorage, GridEngineJob
from utils import assert_first_argument_contained, assert_first_argument_not_contained

HOST1 = 'HOST1'
HOST2 = 'HOST2'
HOST3 = 'HOST3'

executor = Mock()
host_storage = MemoryHostStorage()
grid_engine = Mock()
pipe = Mock()
scale_down_handler = Mock()
worker_validator = GridEngineWorkerValidator(cmd_executor=executor, host_storage=host_storage,
                                             grid_engine=grid_engine, scale_down_handler=scale_down_handler)


def setup_function():
    host_storage.clear()
    for host in [HOST1, HOST2, HOST3]:
        host_storage.add_host(host)
    executor.execute = MagicMock()
    grid_engine.is_valid = MagicMock(side_effect=[True, False, True])
    grid_engine.get_jobs = MagicMock(return_value=[])
    grid_engine.kill_jobs = MagicMock()
    scale_down_handler._get_run_id_from_host = MagicMock(side_effect=lambda x: x)


def test_stopping_hosts_that_are_invalid_in_grid_engine():
    worker_validator.validate_hosts()

    assert [HOST1, HOST3] == host_storage.load_hosts()


def test_stopping_invalid_worker_pipeline():
    worker_validator.validate_hosts()

    assert_first_argument_contained(executor.execute, 'pipe stop ')
    assert_first_argument_contained(executor.execute, HOST2)
    assert_first_argument_not_contained(executor.execute, HOST1)
    assert_first_argument_not_contained(executor.execute, HOST3)


def test_force_killing_invalid_host_jobs():
    jobs = [GridEngineJob(id=1, name='', user='', state='', datetime='', host=HOST2)]
    grid_engine.get_jobs = MagicMock(return_value=jobs)

    worker_validator.validate_hosts()

    grid_engine.kill_jobs.assert_called_with(jobs, force=True)
