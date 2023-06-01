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

from datetime import datetime
from mock import MagicMock, Mock

from pipeline.hpc.autoscaler import Instance, AvailableInstanceProvider

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

unavailability_delay = 3600
started = datetime(2018, 12, 21, 11, 00, 00)
stopped = datetime(2018, 12, 21, 11, 5, 00)
run_id = 12345
worker_name = 'pipeline-12345'
price_type = 'spot'

inner_instance_provider = Mock()
availability_manager = Mock()
instance_provider = AvailableInstanceProvider(inner=inner_instance_provider, availability_manager=availability_manager)

instance_2cpu = Instance(name='m5.large', price_type=price_type, cpu=2, mem=8, gpu=0)
instance_4cpu = Instance(name='m5.xlarge', price_type=price_type, cpu=4, mem=16, gpu=0)
instance_8cpu = Instance(name='m5.2xlarge', price_type=price_type, cpu=8, mem=32, gpu=0)


def setup_function():
    inner_instance_provider.provide = MagicMock(return_value=[
        instance_2cpu,
        instance_4cpu,
        instance_8cpu])


def test_provide_if_all_available():
    availability_manager.get_unavailable = MagicMock(return_value=[])

    instances = instance_provider.provide()

    assert len(instances) == 3
    assert instance_2cpu in instances
    assert instance_4cpu in instances
    assert instance_8cpu in instances


def test_provide_if_one_unavailable():
    availability_manager.get_unavailable = MagicMock(return_value=[
        instance_8cpu.name])

    instances = instance_provider.provide()

    assert len(instances) == 2
    assert instance_2cpu in instances
    assert instance_4cpu in instances
    assert instance_8cpu not in instances


def test_provide_if_two_unavailable():
    availability_manager.get_unavailable = MagicMock(return_value=[
        instance_8cpu.name,
        instance_2cpu.name])

    instances = instance_provider.provide()

    assert len(instances) == 1
    assert instance_2cpu not in instances
    assert instance_4cpu in instances
    assert instance_8cpu not in instances


def test_provide_if_all_unavailable():
    availability_manager.get_unavailable = MagicMock(return_value=[
        instance_8cpu.name,
        instance_4cpu.name,
        instance_2cpu.name])

    instances = instance_provider.provide()

    assert not instances
