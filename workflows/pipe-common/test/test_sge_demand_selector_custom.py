# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import pytest
from mock import MagicMock, Mock

from pipeline.hpc.engine.gridengine import GridEngineJob
from pipeline.hpc.engine.sge import SunGridEngineCustomDemandSelector
from pipeline.hpc.resource import CustomResourceSupply

PE_LOCAL = 'local'
PE_MPI = 'mpi'

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

grid_engine = Mock()
inner_demand_selector = Mock()
owner = 'owner'

job_args = {'id': '1', 'root_id': 1, 'name': '', 'user': owner, 'state': '', 'datetime': ''}

job_0a = GridEngineJob(requests={'a': 0}, **job_args)
job_2a = GridEngineJob(requests={'a': 2}, **job_args)
job_4a = GridEngineJob(requests={'a': 4}, **job_args)
job_8a = GridEngineJob(requests={'a': 8}, **job_args)

job_0b = GridEngineJob(requests={'b': 0}, **job_args)
job_2b = GridEngineJob(requests={'b': 2}, **job_args)
job_4b = GridEngineJob(requests={'b': 4}, **job_args)
job_8b = GridEngineJob(requests={'b': 8}, **job_args)

job_0a0b = GridEngineJob(requests={'a': 0, 'b': 0}, **job_args)
job_2a2b = GridEngineJob(requests={'a': 2, 'b': 2}, **job_args)
job_4a4b = GridEngineJob(requests={'a': 4, 'b': 4}, **job_args)
job_8a8b = GridEngineJob(requests={'a': 8, 'b': 8}, **job_args)

test_cases = [

    ['2a using 0a supply',
     [job_2a],
     [CustomResourceSupply(values={'a': 0})],
     []],

    ['2a using 2a supply',
     [job_2a],
     [CustomResourceSupply(values={'a': 2})],
     [job_2a]],

    ['2a and 4a and 8a jobs using 0a supply',
     [job_2a,
      job_4a,
      job_8a],
     [CustomResourceSupply(values={'a': 0})],
     []],

    ['2a and 4a and 8a jobs using 2a supply',
     [job_2a,
      job_4a,
      job_8a],
     [CustomResourceSupply(values={'a': 2})],
     [job_2a]],

    ['2a and 4a and 8a jobs using 6a supply',
     [job_2a,
      job_4a,
      job_8a],
     [CustomResourceSupply(values={'a': 6})],
     [job_2a,
      job_4a]],

    ['2a and 4a and 8a jobs using 14a supply',
     [job_2a,
      job_4a,
      job_8a],
     [CustomResourceSupply(values={'a': 14})],
     [job_2a,
      job_4a,
      job_8a]],

    ['2a and 2b jobs using 2a supply',
     [job_2a,
      job_2b],
     [CustomResourceSupply(values={'a': 2})],
     [job_2a,
      job_2b]],

    ['2a and 2b jobs using 2a/0b supply',
     [job_2a,
      job_2b],
     [CustomResourceSupply(values={'a': 2, 'b': 0})],
     [job_2a]],

    ['2a and 2b jobs using 2b supply',
     [job_2a,
      job_2b],
     [CustomResourceSupply(values={'b': 2})],
     [job_2a,
      job_2b]],

    ['2a and 2b jobs using 0b/2b supply',
     [job_2a,
      job_2b],
     [CustomResourceSupply(values={'a': 0, 'b': 2})],
     [job_2b]],

    ['2a and 2b jobs using 2a/2b supply',
     [job_2a,
      job_2b],
     [CustomResourceSupply(values={'a': 2, 'b': 2})],
     [job_2a,
      job_2b]],

    ['2a/2b job using 2a supply',
     [job_2a2b],
     [CustomResourceSupply(values={'a': 2})],
     [job_2a2b]],

    ['2a/2b job using 2a/0b supply',
     [job_2a2b],
     [CustomResourceSupply(values={'a': 2, 'b': 0})],
     []],

    ['2a/2b job using 2b supply',
     [job_2a2b],
     [CustomResourceSupply(values={'b': 2})],
     [job_2a2b]],

    ['2a/2b job using 0b/2b supply',
     [job_2a2b],
     [CustomResourceSupply(values={'a': 0, 'b': 2})],
     []],

    ['2a/2b job using 2a/2b supply',
     [job_2a2b],
     [CustomResourceSupply(values={'a': 2, 'b': 2})],
     [job_2a2b]],

    ['0a using no supply',
     [job_0a],
     [],
     [job_0a]],

    ['0a using 0a supply',
     [job_0a],
     [CustomResourceSupply(values={'a': 0})],
     [job_0a]],

    ['0a/0b job using no supply',
     [job_0a0b],
     [],
     [job_0a0b]],

    ['0a/0b job using 0a supply',
     [job_0a0b],
     [CustomResourceSupply(values={'a': 0})],
     [job_0a0b]],

    ['0a/0b job using 0b supply',
     [job_0a0b],
     [CustomResourceSupply(values={'b': 0})],
     [job_0a0b]],

    ['0a/0b job using 0a/0b supply',
     [job_0a0b],
     [CustomResourceSupply(values={'a': 0, 'b': 0})],
     [job_0a0b]],
]


@pytest.mark.parametrize('jobs,resource_supplies,required_filtered_jobs',
                         [test_case[1:] for test_case in test_cases],
                         ids=[test_case[0] for test_case in test_cases])
def test_filter(jobs, resource_supplies, required_filtered_jobs):
    grid_engine.get_global_supplies = MagicMock(return_value=iter(resource_supplies))
    demand_selector = SunGridEngineCustomDemandSelector(inner=inner_demand_selector, grid_engine=grid_engine)
    actual_filtered_jobs = list(demand_selector.filter(jobs))
    assert required_filtered_jobs == actual_filtered_jobs
