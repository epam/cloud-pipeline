# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import sys
from datetime import datetime

import pytest
from mock import MagicMock, Mock

from pipeline.hpc.instance.provider import Instance
from pipeline.hpc.pipe import CloudPipelineReservationInstanceProvider
from pipeline.hpc.resource import ResourceSupply

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

unavailability_delay = 3600
started = datetime(2018, 12, 21, 11, 00, 00)
stopped = datetime(2018, 12, 21, 11, 5, 00)
run_id = 12345
worker_name = 'pipeline-12345'
price_type = 'spot'

inner_instance_provider = Mock()
availability_manager = Mock()
instance_provider = CloudPipelineReservationInstanceProvider(inner=inner_instance_provider,
                                                             kube_mem_ratio=0.025,
                                                             kube_mem_min_mib=256,
                                                             kube_mem_max_mib=1024,
                                                             system_mem_ratio=0.025,
                                                             system_mem_min_mib=256,
                                                             system_mem_max_mib=1024,
                                                             extra_mem_ratio=0.05,
                                                             extra_mem_min_mib=512,
                                                             extra_mem_max_mib=sys.maxsize)

instance_2cpu_0mem = Instance(name='t3.nano', price_type=price_type, cpu=2, mem=0, gpu=0)
instance_2cpu_1mem = Instance(name='t3.micro', price_type=price_type, cpu=2, mem=1, gpu=0)
instance_2cpu_2mem = Instance(name='t3.small', price_type=price_type, cpu=2, mem=2, gpu=0)
instance_2cpu_4mem = Instance(name='t3.medium', price_type=price_type, cpu=2, mem=4, gpu=0)
instance_2cpu_8mem = Instance(name='m5.large', price_type=price_type, cpu=2, mem=8, gpu=0)
instance_4cpu_16mem = Instance(name='m5.xlarge', price_type=price_type, cpu=4, mem=16, gpu=0)
instance_8cpu_32mem = Instance(name='m5.2xlarge', price_type=price_type, cpu=8, mem=32, gpu=0)
instance_16cpu_64mem = Instance(name='m5.4xlarge', price_type=price_type, cpu=16, mem=64, gpu=0)
instance_32cpu_128mem = Instance(name='m5.8xlarge', price_type=price_type, cpu=32, mem=128, gpu=0)
instance_32cpu_244mem = Instance(name='p3.8xlarge', price_type=price_type, cpu=32, mem=244, gpu=0)
instance_32cpu_256mem = Instance(name='r6i.8xlarge', price_type=price_type, cpu=32, mem=256, gpu=0)
instance_48cpu_384mem = Instance(name='r5.12xlarge', price_type=price_type, cpu=48, mem=384, gpu=0)
instance_96cpu_768mem = Instance(name='r5.24xlarge', price_type=price_type, cpu=96, mem=768, gpu=0)
instance_128cpu_1024mem = Instance(name='r6i.32xlarge', price_type=price_type, cpu=128, mem=1024, gpu=0)

test_cases = [
    ['2cpu,0mem instance',
     [instance_2cpu_0mem],
     [ResourceSupply(cpu=2, mem=0, exc=1)]],

    ['2cpu,1mem instance',
     [instance_2cpu_1mem],
     [ResourceSupply(cpu=2, mem=0, exc=1)]],

    ['2cpu,2mem instance',
     [instance_2cpu_2mem],
     [ResourceSupply(cpu=2, mem=1, exc=1)]],

    ['2cpu,4mem instance',
     [instance_2cpu_4mem],
     [ResourceSupply(cpu=2, mem=3, exc=1)]],

    ['2cpu,8mem instance',
     [instance_2cpu_8mem],
     [ResourceSupply(cpu=2, mem=7, exc=1)]],

    ['4cpu,16mem instance',
     [instance_4cpu_16mem],
     [ResourceSupply(cpu=4, mem=14, exc=1)]],

    ['8cpu,32mem instance',
     [instance_8cpu_32mem],
     [ResourceSupply(cpu=8, mem=28, exc=1)]],

    ['16cpu,64mem instance',
     [instance_16cpu_64mem],
     [ResourceSupply(cpu=16, mem=58, exc=1)]],

    ['32cpu,128mem instance',
     [instance_32cpu_128mem],
     [ResourceSupply(cpu=32, mem=119, exc=1)]],

    ['32cpu,244mem instance',
     [instance_32cpu_244mem],
     [ResourceSupply(cpu=32, mem=229, exc=1)]],

    ['32cpu, 256mem instance',
     [instance_32cpu_256mem],
     [ResourceSupply(cpu=32, mem=241, exc=1)]],

    ['48cpu,384mem instance',
     [instance_48cpu_384mem],
     [ResourceSupply(cpu=48, mem=362, exc=1)]],

    ['96cpu,768mem instance',
     [instance_96cpu_768mem],
     [ResourceSupply(cpu=96, mem=727, exc=1)]],

    ['128cpu,1024mem instance',
     [instance_128cpu_1024mem],
     [ResourceSupply(cpu=128, mem=970, exc=1)]],
]

@pytest.mark.parametrize('instances,required_resource_supplies',
                         [test_case[1:] for test_case in test_cases],
                         ids=[test_case[0] for test_case in test_cases])
def test_select(instances, required_resource_supplies):
    inner_instance_provider.provide = MagicMock(return_value=instances)
    actual_instances = list(instance_provider.provide())
    actual_resource_supplies = [ResourceSupply.of(instance) for instance in actual_instances]
    assert actual_resource_supplies == required_resource_supplies
