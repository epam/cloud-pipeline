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

from pipeline.hpc.instance.provider import Instance
from pipeline.hpc.instance.select import InstanceDemand, CpuCapacityInstanceSelector
from pipeline.hpc.resource import IntegralDemand, FractionalDemand, ResourceSupply

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

instance_provider = Mock()
reserved_supply = ResourceSupply()
price_type = 'price_type'
owner = 'owner'
another_owner = 'another_owner'

instance_2cpu = Instance(name='m5.large', price_type=price_type, cpu=2, mem=8, gpu=0)
instance_4cpu = Instance(name='m5.xlarge', price_type=price_type, cpu=4, mem=16, gpu=0)
instance_8cpu = Instance(name='m5.2xlarge', price_type=price_type, cpu=8, mem=32, gpu=0)
instance_16cpu = Instance(name='m5.4xlarge', price_type=price_type, cpu=16, mem=64, gpu=0)
instance_32cpu = Instance(name='m5.8xlarge', price_type=price_type, cpu=32, mem=128, gpu=0)
instance_48cpu = Instance(name='m5.12xlarge', price_type=price_type, cpu=48, mem=192, gpu=0)
instance_64cpu = Instance(name='m5.16xlarge', price_type=price_type, cpu=64, mem=256, gpu=0)
instance_96cpu = Instance(name='m5.24xlarge', price_type=price_type, cpu=96, mem=384, gpu=0)
instance_8cpu1gpu = Instance(name='p3.2xlarge', price_type=price_type, cpu=8, mem=61, gpu=1)
instance_32cpu4gpu = Instance(name='p3.8xlarge', price_type=price_type, cpu=32, mem=244, gpu=4)
instance_64cpu8gpu = Instance(name='p3.16xlarge', price_type=price_type, cpu=64, mem=488, gpu=8)
all_instances = [instance_2cpu, instance_4cpu,
                 instance_8cpu, instance_16cpu,
                 instance_32cpu, instance_48cpu,
                 instance_64cpu, instance_96cpu]

test_cases = [
    ['2cpu job using no instances',
     [],
     [IntegralDemand(cpu=2, owner=owner)],
     []],

    ['4cpu job using 2cpu instances',
     [instance_2cpu],
     [IntegralDemand(cpu=4, owner=owner)],
     []],

    ['2cpu job using 2cpu instances',
     [instance_2cpu],
     [IntegralDemand(cpu=2, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['2x2cpu jobs using 2cpu instances',
     [instance_2cpu],
     2 * [IntegralDemand(cpu=2, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner),
      InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['3x1cpu and 2cpu jobs using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     3 * [IntegralDemand(cpu=1, owner=owner)]
     + [IntegralDemand(cpu=3, owner=owner)],
     [InstanceDemand(instance=instance_4cpu, owner=owner),
      InstanceDemand(instance=instance_4cpu, owner=owner)]],

    ['3x3cpu jobs using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     3 * [IntegralDemand(cpu=3, owner=owner)],
     3 * [InstanceDemand(instance=instance_4cpu, owner=owner)]],

    ['3x3cpu jobs using all instances',
     all_instances,
     3 * [IntegralDemand(cpu=3, owner=owner)],
     [InstanceDemand(instance=instance_16cpu, owner=owner)]],

    ['2cpu and 6cpu jobs using 2cpu and 4cpu and 8cpu instances',
     [instance_2cpu,
      instance_4cpu,
      instance_8cpu],
     [IntegralDemand(cpu=2, owner=owner),
      IntegralDemand(cpu=6, owner=owner)],
     [InstanceDemand(instance=instance_8cpu, owner=owner)]],

    ['10x16cpu jobs using all instances',
     all_instances,
     10 * [IntegralDemand(cpu=16, owner=owner)],
     [InstanceDemand(instance=instance_96cpu, owner=owner),
      InstanceDemand(instance=instance_64cpu, owner=owner)]],

    ['2cpu fractional job using 2cpu instances',
     [instance_2cpu],
     [FractionalDemand(cpu=2, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['2x2cpu fractional jobs using 2cpu instances',
     [instance_2cpu],
     2 * [FractionalDemand(cpu=2, owner=owner)],
     2 * [InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['3x1cpu and 2cpu fractional jobs using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     3 * [FractionalDemand(cpu=1, owner=owner)]
     + [FractionalDemand(cpu=3, owner=owner)],
     [InstanceDemand(instance=instance_4cpu, owner=owner),
      InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['3x3cpu fractional jobs using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     3 * [FractionalDemand(cpu=3, owner=owner)],
     2 * [InstanceDemand(instance=instance_4cpu, owner=owner)]
     + [InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['3x3cpu fractional jobs using all instances',
     all_instances,
     3 * [FractionalDemand(cpu=3, owner=owner)],
     [InstanceDemand(instance=instance_16cpu, owner=owner)]],

    ['2cpu and 6cpu fractional jobs using 2cpu and 4cpu and 8cpu instances',
     [instance_2cpu,
      instance_4cpu,
      instance_8cpu],
     [FractionalDemand(cpu=2, owner=owner),
      FractionalDemand(cpu=6, owner=owner)],
     [InstanceDemand(instance=instance_8cpu, owner=owner)]],

    ['10x16cpu fractional jobs using all instances',
     all_instances,
     10 * [FractionalDemand(cpu=16, owner=owner)],
     [InstanceDemand(instance=instance_96cpu, owner=owner),
      InstanceDemand(instance=instance_64cpu, owner=owner)]],

    ['16cpu owner jobs and 48cpu another owner jobs using all instances',
     all_instances,
     [IntegralDemand(cpu=16, owner=owner),
      IntegralDemand(cpu=48, owner=another_owner)],
     [InstanceDemand(instance=instance_64cpu, owner=another_owner)]],

    ['16cpu owner jobs and 48x1cpu another owner jobs using all instances',
     all_instances,
     [IntegralDemand(cpu=16, owner=owner)]
     + 48 * [IntegralDemand(cpu=1, owner=another_owner)],
     [InstanceDemand(instance=instance_64cpu, owner=another_owner)]],

    ['4x16cpu another owner jobs and 4x16cpu owner jobs using all instances',
     all_instances,
     4 * [IntegralDemand(cpu=16, owner=owner)]
     + 4 * [IntegralDemand(cpu=16, owner=another_owner)],
     [InstanceDemand(instance=instance_96cpu, owner=owner),
      InstanceDemand(instance=instance_32cpu, owner=another_owner)]],

    ['4x16cpu another owner jobs and 4x16cpu owner jobs using 64cpu instances',
     [instance_64cpu],
     4 * [IntegralDemand(cpu=16, owner=owner)]
     + 4 * [IntegralDemand(cpu=16, owner=another_owner)],
     [InstanceDemand(instance=instance_64cpu, owner=owner),
      InstanceDemand(instance=instance_64cpu, owner=another_owner)]],

    ['2x3cpu integral jobs and 2x1cpu fractional jobs using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     2 * [IntegralDemand(cpu=3, owner=owner)]
     + 2 * [FractionalDemand(cpu=1, owner=owner)],
     [InstanceDemand(instance=instance_4cpu, owner=owner),
      InstanceDemand(instance=instance_4cpu, owner=owner)]],

    ['2x3cpu integral jobs and 3x1cpu fractional jobs using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     2 * [IntegralDemand(cpu=3, owner=owner)]
     + 3 * [FractionalDemand(cpu=1, owner=owner)],
     [InstanceDemand(instance=instance_4cpu, owner=owner),
      InstanceDemand(instance=instance_4cpu, owner=owner),
      InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['2cpu job using 4cpu and 2cpu instances (largest first)',
     [instance_4cpu,
      instance_2cpu],
     [IntegralDemand(cpu=2, owner=owner)],
     [InstanceDemand(instance=instance_4cpu, owner=owner)]],

    ['3x2cpu job using 4cpu and 2cpu instances (largest first)',
     [instance_4cpu,
      instance_2cpu],
     3 * [IntegralDemand(cpu=2, owner=owner)],
     [InstanceDemand(instance=instance_4cpu, owner=owner),
      InstanceDemand(instance=instance_4cpu, owner=owner)]],

    ['3x2cpu fractional job using 4cpu and 2cpu instances (largest first)',
     [instance_4cpu,
      instance_2cpu],
     3 * [IntegralDemand(cpu=2, owner=owner)],
     [InstanceDemand(instance=instance_4cpu, owner=owner),
      InstanceDemand(instance=instance_4cpu, owner=owner)]],

    ['2cpu,1gpu job using 2cpu instances',
     [instance_2cpu],
     [IntegralDemand(cpu=2, gpu=1, owner=owner)],
     []],

    ['2cpu,1gpu job using 8cpu,1gpu instances',
     [instance_8cpu1gpu],
     [IntegralDemand(cpu=2, gpu=1, owner=owner)],
     [InstanceDemand(instance=instance_8cpu1gpu, owner=owner)]],

    ['2x2cpu,1gpu job using 8cpu,1gpu instances',
     [instance_8cpu1gpu],
     2 * [IntegralDemand(cpu=2, gpu=1, owner=owner)],
     [InstanceDemand(instance=instance_8cpu1gpu, owner=owner),
      InstanceDemand(instance=instance_8cpu1gpu, owner=owner)]],

    ['2cpu,2gpu job using 8cpu,1gpu instances',
     [instance_8cpu1gpu],
     [IntegralDemand(cpu=2, gpu=2, owner=owner)],
     []],

    ['2cpu,2gpu job using 32cpu,4gpu instances',
     [instance_32cpu4gpu],
     [IntegralDemand(cpu=2, gpu=2, owner=owner)],
     [InstanceDemand(instance=instance_32cpu4gpu, owner=owner)]],

    ['2x2cpu,2gpu job using 32cpu,4gpu instances',
     [instance_32cpu4gpu],
     2 * [IntegralDemand(cpu=2, gpu=2, owner=owner)],
     [InstanceDemand(instance=instance_32cpu4gpu, owner=owner)]],

    ['3x2cpu,2gpu job using 32cpu,4gpu instances',
     [instance_32cpu4gpu],
     3 * [IntegralDemand(cpu=2, gpu=2, owner=owner)],
     [InstanceDemand(instance=instance_32cpu4gpu, owner=owner),
      InstanceDemand(instance=instance_32cpu4gpu, owner=owner)]],

    ['3x2cpu,2gpu job using 32cpu,4gpu instances',
     [instance_32cpu4gpu],
     4 * [IntegralDemand(cpu=2, gpu=2, owner=owner)],
     [InstanceDemand(instance=instance_32cpu4gpu, owner=owner),
      InstanceDemand(instance=instance_32cpu4gpu, owner=owner)]],

    ['4cpu,4gpu job using 32cpu,4gpu instances',
     [instance_32cpu4gpu],
     [IntegralDemand(cpu=4, gpu=4, owner=owner)],
     [InstanceDemand(instance=instance_32cpu4gpu, owner=owner)]],

    ['2x4cpu,4gpu job using 32cpu,4gpu instances',
     [instance_32cpu4gpu],
     2 * [IntegralDemand(cpu=4, gpu=4, owner=owner)],
     [InstanceDemand(instance=instance_32cpu4gpu, owner=owner),
      InstanceDemand(instance=instance_32cpu4gpu, owner=owner)]],

    ['4cpu,4gpu job using 8cpu,1gpu instances',
     [instance_8cpu1gpu],
     [IntegralDemand(cpu=4, gpu=4, owner=owner)],
     []],

    ['1cpu,1gpu job using 8cpu,1gpu and 32cpu,4gpu instances',
     [instance_8cpu1gpu,
      instance_32cpu4gpu],
     [IntegralDemand(cpu=1, gpu=1, owner=owner)],
     [InstanceDemand(instance=instance_8cpu1gpu, owner=owner)]],

    ['2x1cpu,1gpu job using 8cpu,1gpu and 32cpu,4gpu instances',
     [instance_8cpu1gpu,
      instance_32cpu4gpu],
     2 * [IntegralDemand(cpu=1, gpu=1, owner=owner)],
     [InstanceDemand(instance=instance_32cpu4gpu, owner=owner)]],

    ['4cpu,4gpu job using 8cpu,1gpu and 32cpu,4gpu instances',
     [instance_8cpu1gpu,
      instance_32cpu4gpu],
     [IntegralDemand(cpu=4, gpu=4, owner=owner)],
     [InstanceDemand(instance=instance_32cpu4gpu, owner=owner)]],

    ['4cpu,4gpu job using 8cpu,1gpu and 32cpu,4gpu and 64cpu,8gpu instances',
     [instance_8cpu1gpu,
      instance_32cpu4gpu,
      instance_64cpu8gpu],
     [IntegralDemand(cpu=4, gpu=4, owner=owner)],
     [InstanceDemand(instance=instance_32cpu4gpu, owner=owner)]],

    ['1cpu,1gpu job using 8cpu,1gpu and 32cpu,4gpu and 64cpu,8gpu instances',
     [instance_8cpu1gpu,
      instance_32cpu4gpu,
      instance_64cpu8gpu],
     [IntegralDemand(cpu=1, gpu=1, owner=owner)],
     [InstanceDemand(instance=instance_8cpu1gpu, owner=owner)]],

    ['9x1cpu,1gpu job using 8cpu,1gpu and 32cpu,4gpu and 64cpu,8gpu instances',
     [instance_8cpu1gpu,
      instance_32cpu4gpu,
      instance_64cpu8gpu],
     9 * [IntegralDemand(cpu=1, gpu=1, owner=owner)],
     [InstanceDemand(instance=instance_64cpu8gpu, owner=owner),
      InstanceDemand(instance=instance_8cpu1gpu, owner=owner)]],

    ['10x1cpu,1gpu job using 8cpu,1gpu and 32cpu,4gpu and 64cpu,8gpu instances',
     [instance_8cpu1gpu,
      instance_32cpu4gpu,
      instance_64cpu8gpu],
     10 * [IntegralDemand(cpu=1, gpu=1, owner=owner)],
     [InstanceDemand(instance=instance_64cpu8gpu, owner=owner),
      InstanceDemand(instance=instance_32cpu4gpu, owner=owner)]],

    ['1cpu,1exc job using 2cpu instances',
     [instance_2cpu],
     [IntegralDemand(cpu=1, exc=1, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['1cpu,1exc job using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     [IntegralDemand(cpu=1, exc=1, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['2x1cpu,1exc job using 2cpu instances',
     [instance_2cpu],
     2 * [IntegralDemand(cpu=1, exc=1, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner),
      InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['2x1cpu,1exc job using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     2 * [IntegralDemand(cpu=1, exc=1, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner),
      InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['4x1cpu,1exc job using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     4 * [IntegralDemand(cpu=1, exc=1, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner),
      InstanceDemand(instance=instance_2cpu, owner=owner),
      InstanceDemand(instance=instance_2cpu, owner=owner),
      InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['4cpu,1exc job using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     [IntegralDemand(cpu=4, exc=1, owner=owner)],
     [InstanceDemand(instance=instance_4cpu, owner=owner)]],

    ['1cpu,1exc and 3cpu,1exc job using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     [IntegralDemand(cpu=1, exc=1, owner=owner),
      IntegralDemand(cpu=3, exc=1, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner),
      InstanceDemand(instance=instance_4cpu, owner=owner)]],

    ['1cpu and 1cpu,1exc job using 2cpu instances',
     [instance_2cpu],
     [IntegralDemand(cpu=1, owner=owner),
      IntegralDemand(cpu=1, exc=1, owner=owner)],
     [InstanceDemand(instance=instance_2cpu, owner=owner)]],

    ['1cpu and 3cpu,1exc job using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     [IntegralDemand(cpu=1, owner=owner),
      IntegralDemand(cpu=3, exc=1, owner=owner)],
     [InstanceDemand(instance=instance_4cpu, owner=owner)]],

    ['1cpu,1exc and 3cpu job using 2cpu and 4cpu instances',
     [instance_2cpu,
      instance_4cpu],
     [IntegralDemand(cpu=1, exc=1, owner=owner),
      IntegralDemand(cpu=3, owner=owner)],
     [InstanceDemand(instance=instance_4cpu, owner=owner)]],
]


@pytest.mark.parametrize('instances,resource_demands,required_instance_demands',
                         [test_case[1:] for test_case in test_cases],
                         ids=[test_case[0] for test_case in test_cases])
def test_select(instances, resource_demands, required_instance_demands):
    instance_provider.provide = MagicMock(return_value=instances)
    instance_selector = CpuCapacityInstanceSelector(instance_provider, reserved_supply)
    actual_instance_demands = list(instance_selector.select(resource_demands))
    assert required_instance_demands == actual_instance_demands
