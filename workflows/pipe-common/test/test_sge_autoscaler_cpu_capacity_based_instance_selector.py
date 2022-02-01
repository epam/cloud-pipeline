# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

from scripts.autoscale_sge import CpuCapacityInstanceSelector, SolidDemand, InstanceDemand, Instance, \
    FluidDemand

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

instance_provider = Mock()
free_cores = 0
price_type = 'price_type'
owner = 'owner'
another_owner = 'another_owner'

instance_2cpu = Instance(name='m5.large', price_type=price_type, cpu=2, memory=8, gpu=0)
instance_4cpu = Instance(name='m5.xlarge', price_type=price_type, cpu=4, memory=16, gpu=0)
instance_8cpu = Instance(name='m5.2xlarge', price_type=price_type, cpu=8, memory=32, gpu=0)
instance_16cpu = Instance(name='m5.4xlarge', price_type=price_type, cpu=16, memory=64, gpu=0)
instance_32cpu = Instance(name='m5.8xlarge', price_type=price_type, cpu=32, memory=128, gpu=0)
instance_48cpu = Instance(name='m5.12xlarge', price_type=price_type, cpu=48, memory=192, gpu=0)
instance_64cpu = Instance(name='m5.16xlarge', price_type=price_type, cpu=64, memory=256, gpu=0)
instance_96cpu = Instance(name='m5.24xlarge', price_type=price_type, cpu=96, memory=384, gpu=0)
all_instances = [instance_2cpu, instance_4cpu,
                 instance_8cpu, instance_16cpu,
                 instance_32cpu, instance_48cpu,
                 instance_64cpu, instance_96cpu]

test_cases = [
    ['2cpu job using 2cpu instances',
     [instance_2cpu],
     [SolidDemand(cpu=2, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['2x2cpu jobs using 2cpu instances',
     [instance_2cpu],
     2 * [SolidDemand(cpu=2, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner),
      InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['3x1cpu and 2cpu jobs using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     3 * [SolidDemand(cpu=1, owner=owner)]
     + [SolidDemand(cpu=3, owner=owner)],
     [InstanceDemand(instance=instance_4cpu, owner=owner),
      InstanceDemand(instance=instance_4cpu, owner=owner)]],

    ['3x3cpu jobs using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     3 * [SolidDemand(cpu=3, owner=owner)],
     3 * [InstanceDemand(instance=instance_4cpu, owner=owner)]],

    ['3x3cpu jobs using all instances',
     all_instances,
     3 * [SolidDemand(cpu=3, owner=owner)],
     [InstanceDemand(instance=instance_16cpu, owner=owner)]],

    ['2cpu and 6cpu jobs using 2cpu and 4cpu and 8cpu instances',
     [instance_2cpu,
      instance_4cpu,
      instance_8cpu],
     [SolidDemand(cpu=2, owner=owner),
      SolidDemand(cpu=6, owner=owner)],
     [InstanceDemand(instance=instance_8cpu, owner=owner)]],

    ['10x16cpu jobs using all instances',
     all_instances,
     10 * [SolidDemand(cpu=16, owner=owner)],
     [InstanceDemand(instance=instance_96cpu, owner=owner),
      InstanceDemand(instance=instance_64cpu, owner=owner)]],

    ['2cpu fluid job using 2cpu instances',
     [instance_2cpu],
     [FluidDemand(cpu=2, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['2x2cpu fluid jobs using 2cpu instances',
     [instance_2cpu],
     2 * [FluidDemand(cpu=2, owner=owner)],
     2 * [InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['3x1cpu and 2cpu fluid jobs using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     3 * [FluidDemand(cpu=1, owner=owner)]
     + [FluidDemand(cpu=3, owner=owner)],
     [InstanceDemand(instance=instance_4cpu, owner=owner),
      InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['3x3cpu fluid jobs using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     3 * [FluidDemand(cpu=3, owner=owner)],
     2 * [InstanceDemand(instance=instance_4cpu, owner=owner)]
     + [InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['3x3cpu fluid jobs using all instances',
     all_instances,
     3 * [FluidDemand(cpu=3, owner=owner)],
     [InstanceDemand(instance=instance_16cpu, owner=owner)]],

    ['2cpu and 6cpu fluid jobs using 2cpu and 4cpu and 8cpu instances',
     [instance_2cpu,
      instance_4cpu,
      instance_8cpu],
     [FluidDemand(cpu=2, owner=owner),
      FluidDemand(cpu=6, owner=owner)],
     [InstanceDemand(instance=instance_8cpu, owner=owner)]],

    ['10x16cpu fluid jobs using all instances',
     all_instances,
     10 * [FluidDemand(cpu=16, owner=owner)],
     [InstanceDemand(instance=instance_96cpu, owner=owner),
      InstanceDemand(instance=instance_64cpu, owner=owner)]],

    ['16cpu owner jobs and 48cpu another owner jobs using all instances',
     all_instances,
     [SolidDemand(cpu=16, owner=owner),
      SolidDemand(cpu=48, owner=another_owner)],
     [InstanceDemand(instance=instance_64cpu, owner=another_owner)]],

    ['16cpu owner jobs and 48x1cpu another owner jobs using all instances',
     all_instances,
     [SolidDemand(cpu=16, owner=owner)]
     + 48 * [SolidDemand(cpu=1, owner=another_owner)],
     [InstanceDemand(instance=instance_64cpu, owner=another_owner)]],

    ['4x16cpu another owner jobs and 4x16cpu owner jobs using all instances',
     all_instances,
     4 * [SolidDemand(cpu=16, owner=owner)] + 4 * [SolidDemand(cpu=16, owner=another_owner)],
     [InstanceDemand(instance=instance_96cpu, owner=owner),
      InstanceDemand(instance=instance_32cpu, owner=another_owner)]],

    ['4x16cpu another owner jobs and 4x16cpu owner jobs using 64cpu instances',
     [instance_64cpu],
     4 * [SolidDemand(cpu=16, owner=owner)] + 4 * [SolidDemand(cpu=16, owner=another_owner)],
     [InstanceDemand(instance=instance_64cpu, owner=owner),
      InstanceDemand(instance=instance_64cpu, owner=another_owner)]]
]


@pytest.mark.parametrize('instances,input,output', [test_case[1:] for test_case in test_cases],
                         ids=[test_case[0] for test_case in test_cases])
def test_select(instances, input, output):
    instance_provider.get_allowed_instances = MagicMock(return_value=instances)
    selector = CpuCapacityInstanceSelector(instance_provider, free_cores)
    actual_demands = list(selector.select(input, price_type))
    assert output == actual_demands
