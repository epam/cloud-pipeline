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
from pipeline.hpc.engine.kube import KubeDefaultDemandSelector
from pipeline.hpc.resource import IntegralDemand, ResourceSupply

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

grid_engine = Mock()
owner = 'owner'

job_args = {'id': '1', 'root_id': 1, 'name': '', 'user': owner, 'state': '', 'datetime': ''}

job_2cpu = GridEngineJob(cpu=2, mem=0, **job_args)
job_4cpu = GridEngineJob(cpu=4, mem=0, **job_args)
job_8cpu = GridEngineJob(cpu=8, mem=0, **job_args)

job_2cpu_host = GridEngineJob(cpu=2, mem=0, hosts=['host'], **job_args)
job_4cpu_host = GridEngineJob(cpu=4, mem=0, hosts=['host'], **job_args)
job_8cpu_host = GridEngineJob(cpu=8, mem=0, hosts=['host'], **job_args)

job_8cpu32mem = GridEngineJob(cpu=8, mem=32, **job_args)
job_32cpu128mem = GridEngineJob(cpu=32, mem=128, **job_args)

test_cases = [
    ['2cpu and 4cpu and 8cpu jobs using 0cpu supply',
     [job_2cpu,
      job_4cpu,
      job_8cpu],
     [ResourceSupply(cpu=0)],
     [IntegralDemand(cpu=2, owner=owner),
      IntegralDemand(cpu=4, owner=owner),
      IntegralDemand(cpu=8, owner=owner)]],

    ['2cpu host and 4cpu and 8cpu jobs using 0cpu supply',
     [job_2cpu_host,
      job_4cpu,
      job_8cpu],
     [ResourceSupply(cpu=0)],
     [IntegralDemand(cpu=4, owner=owner),
      IntegralDemand(cpu=8, owner=owner)]],

    ['2cpu host and 4cpu host and 8cpu host jobs using 0cpu supply',
     [job_2cpu_host,
      job_4cpu_host,
      job_8cpu_host],
     [ResourceSupply(cpu=0)],
     []],

    ['2cpu and 4cpu and 8cpu jobs using 2cpu supply',
     [job_2cpu,
      job_4cpu,
      job_8cpu],
     [ResourceSupply(cpu=2)],
     [IntegralDemand(cpu=4, owner=owner),
      IntegralDemand(cpu=8, owner=owner)]],

    ['2cpu and 4cpu and 8cpu jobs using 16cpu supply',
     [job_2cpu,
      job_4cpu,
      job_8cpu],
     [ResourceSupply(cpu=16)],
     []],

    ['8cpu/32mem job using 0cpu supply',
     [job_8cpu32mem],
     [ResourceSupply(cpu=0)],
     [IntegralDemand(cpu=8, mem=32, owner=owner)]],

    ['8cpu/32mem and 32cpu/128mem job using 0cpu supply',
     [job_8cpu32mem,
      job_32cpu128mem],
     [ResourceSupply(cpu=0)],
     [IntegralDemand(cpu=8, mem=32, owner=owner),
      IntegralDemand(cpu=32, mem=128, owner=owner)]],

    ['8cpu/32mem job using 32cpu/128mem supply',
     [job_8cpu32mem],
     [ResourceSupply(cpu=32, mem=128)],
     []],
]


@pytest.mark.parametrize('jobs,resource_supplies,required_resource_demands',
                         [test_case[1:] for test_case in test_cases],
                         ids=[test_case[0] for test_case in test_cases])
def test_select(jobs, resource_supplies, required_resource_demands):
    grid_engine.get_host_supplies = MagicMock(return_value=iter(resource_supplies))
    demand_selector = KubeDefaultDemandSelector(grid_engine=grid_engine)
    actual_resource_demands = list(demand_selector.select(jobs))
    assert required_resource_demands == actual_resource_demands
