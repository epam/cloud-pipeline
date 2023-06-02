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

from pipeline.hpc.gridengine import GridEngineDemandSelector, GridEngineJob, AllocationRule
from pipeline.hpc.resource import IntegralDemand, FractionalDemand, ResourceSupply

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

grid_engine = Mock()
owner = 'owner'

job_2cpu_local = GridEngineJob(id='1', root_id=1, name='', user=owner, state='', datetime='',
                               pe='local', cpu=2)
job_4cpu_local = GridEngineJob(id='1', root_id=1, name='', user=owner, state='', datetime='',
                               pe='local', cpu=4)
job_8cpu_local = GridEngineJob(id='1', root_id=1, name='', user=owner, state='', datetime='',
                               pe='local', cpu=8)
job_2cpu_mpi = GridEngineJob(id='1', root_id=1, name='', user=owner, state='', datetime='',
                             pe='mpi', cpu=2)
job_4cpu_mpi = GridEngineJob(id='1', root_id=1, name='', user=owner, state='', datetime='',
                             pe='mpi', cpu=4)
job_8cpu_mpi = GridEngineJob(id='1', root_id=1, name='', user=owner, state='', datetime='',
                             pe='mpi', cpu=8)

test_cases = [
    ['2cpu and 4cpu and 8cpu local jobs using 0cpu supply',
     [job_2cpu_local,
      job_4cpu_local,
      job_8cpu_local],
     [ResourceSupply(cpu=0)],
     [IntegralDemand(cpu=2, owner=owner),
      IntegralDemand(cpu=4, owner=owner),
      IntegralDemand(cpu=8, owner=owner)]],
    ['2cpu and 4cpu and 8cpu local jobs using 2cpu supply',
     [job_2cpu_local,
      job_4cpu_local,
      job_8cpu_local],
     [ResourceSupply(cpu=2)],
     [IntegralDemand(cpu=2, owner=owner),
      IntegralDemand(cpu=4, owner=owner),
      IntegralDemand(cpu=8, owner=owner)]],
    ['2cpu and 4cpu and 8cpu local jobs using 16cpu supply',
     [job_2cpu_local,
      job_4cpu_local,
      job_8cpu_local],
     [ResourceSupply(cpu=16)],
     [IntegralDemand(cpu=2, owner=owner),
      IntegralDemand(cpu=4, owner=owner),
      IntegralDemand(cpu=8, owner=owner)]],
    ['2cpu mpi job using 0cpu supply',
     [job_2cpu_mpi],
     [ResourceSupply(cpu=0)],
     [FractionalDemand(cpu=2, owner=owner)]],
    ['2cpu mpi job using 2cpu supply',
     [job_2cpu_mpi],
     [ResourceSupply(cpu=2)],
     [FractionalDemand(cpu=1, owner=owner)]],
    ['2cpu mpi job using 4cpu supply',
     [job_2cpu_mpi],
     [ResourceSupply(cpu=4)],
     [FractionalDemand(cpu=1, owner=owner)]],
    ['4cpu mpi job using 2cpu supply',
     [job_4cpu_mpi],
     [ResourceSupply(cpu=2)],
     [FractionalDemand(cpu=2, owner=owner)]],
    ['2cpu and 8cpu mpi jobs using 3cpu supply',
     [job_2cpu_mpi,
      job_8cpu_mpi],
     [ResourceSupply(cpu=3)],
     [FractionalDemand(cpu=1, owner=owner),
      FractionalDemand(cpu=7, owner=owner)]],
    ['2cpu and 8cpu mpi jobs using 2x3cpu supply',
     [job_2cpu_mpi,
      job_8cpu_mpi],
     [ResourceSupply(cpu=3),
      ResourceSupply(cpu=3)],
     [FractionalDemand(cpu=1, owner=owner),
      FractionalDemand(cpu=4, owner=owner)]],
    ['2x8cpu mpi jobs using 2x32cpu supply',
     2 * [job_8cpu_mpi],
     [ResourceSupply(cpu=32),
      ResourceSupply(cpu=32)],
     2 * [FractionalDemand(cpu=1, owner=owner)]],
]


@pytest.mark.parametrize('jobs,resource_supplies,required_resource_demands',
                         [test_case[1:] for test_case in test_cases],
                         ids=[test_case[0] for test_case in test_cases])
def test_select(jobs, resource_supplies, required_resource_demands):
    grid_engine.get_pe_allocation_rule = MagicMock(
        side_effect=lambda pe: AllocationRule.pe_slots() if pe == 'local' else AllocationRule.fill_up())
    grid_engine.get_host_supplies = MagicMock(return_value=iter(resource_supplies))
    demand_selector = GridEngineDemandSelector(grid_engine=grid_engine)
    actual_resource_demands = list(demand_selector.select(jobs))
    assert required_resource_demands == actual_resource_demands
