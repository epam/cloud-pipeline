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

import logging
from datetime import datetime, timedelta

from mock import MagicMock, Mock

from pipeline.hpc.event import AvailableInstanceEvent, InsufficientInstanceEvent, FailingInstanceEvent
from pipeline.hpc.instance.avail import InstanceAvailabilityManager
from pipeline.hpc.instance.provider import Instance

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

unavailability_delay = 3600
unavailability_count_insufficient = 1
unavailability_count_failure = 10
started = datetime(2018, 12, 21, 11, 00, 00)
stopped = datetime(2018, 12, 21, 11, 5, 00)
run_id = 12345
worker_name = 'pipeline-12345'
price_type = 'spot'

inner_instance_provider = Mock()
clock = Mock()
event_manager = Mock()
availability_manager = InstanceAvailabilityManager(event_manager=event_manager, clock=clock,
                                                   unavail_delay=unavailability_delay,
                                                   unavail_count_insufficient=unavailability_count_insufficient,
                                                   unavail_count_failure=unavailability_count_failure)

instance_2cpu = Instance(name='m5.large', price_type=price_type, cpu=2, mem=8, gpu=0)
instance_4cpu = Instance(name='m5.xlarge', price_type=price_type, cpu=4, mem=16, gpu=0)
instance_8cpu = Instance(name='m5.2xlarge', price_type=price_type, cpu=8, mem=32, gpu=0)


def setup_function():
    inner_instance_provider.provide = MagicMock(return_value=[
        instance_2cpu,
        instance_4cpu,
        instance_8cpu])
    clock.now = MagicMock(return_value=stopped + timedelta(seconds=unavailability_delay - 1))


def test_get_unavailable_if_no_events():
    event_manager.get = MagicMock(return_value=[])

    unavailable_instances = list(availability_manager.get_unavailable())

    assert not unavailable_instances


def test_get_unavailable_if_available_event():
    event_manager.get = MagicMock(return_value=[
        AvailableInstanceEvent(instance_type=instance_8cpu.name, date=stopped)])

    unavailable_instances = list(availability_manager.get_unavailable())

    assert not unavailable_instances


def test_get_unavailable_if_insufficient_event():
    event_manager.get = MagicMock(return_value=[
        InsufficientInstanceEvent(instance_type=instance_8cpu.name, date=stopped)])

    unavailable_instances = list(availability_manager.get_unavailable())

    assert len(unavailable_instances) == 1
    assert instance_8cpu.name in unavailable_instances


def test_get_unavailable_if_insufficient_outdated_event():
    clock.now = MagicMock(return_value=stopped + timedelta(seconds=unavailability_delay + 1))
    event_manager.get = MagicMock(return_value=[
        InsufficientInstanceEvent(instance_type=instance_8cpu.name, date=stopped)])

    unavailable_instances = list(availability_manager.get_unavailable())

    assert not unavailable_instances


def test_get_unavailable_if_available_and_insufficient_events():
    event_manager.get = MagicMock(return_value=[
        AvailableInstanceEvent(instance_type=instance_8cpu.name, date=stopped),
        InsufficientInstanceEvent(instance_type=instance_8cpu.name, date=stopped)])

    unavailable_instances = list(availability_manager.get_unavailable())

    assert len(unavailable_instances) == 1
    assert instance_8cpu.name in unavailable_instances


def test_get_unavailable_if_insufficient_and_available_events():
    event_manager.get = MagicMock(return_value=[
        InsufficientInstanceEvent(instance_type=instance_8cpu.name, date=stopped),
        AvailableInstanceEvent(instance_type=instance_8cpu.name, date=stopped)])

    unavailable_instances = list(availability_manager.get_unavailable())

    assert not unavailable_instances


def test_get_unavailable_if_failing_event():
    event_manager.get = MagicMock(return_value=[
        FailingInstanceEvent(instance_type=instance_8cpu.name, date=stopped)])

    unavailable_instances = list(availability_manager.get_unavailable())

    assert not unavailable_instances


def test_get_unavailable_if_multiple_failing_events():
    event_manager.get = MagicMock(
        return_value=unavailability_count_failure *
                     [FailingInstanceEvent(instance_type=instance_8cpu.name, date=stopped)])

    unavailable_instances = list(availability_manager.get_unavailable())

    assert len(unavailable_instances) == 1
    assert instance_8cpu.name in unavailable_instances


def test_get_unavailable_if_multiple_failing_outdated_events():
    clock.now = MagicMock(return_value=stopped + timedelta(seconds=unavailability_delay + 1))
    event_manager.get = MagicMock(
        return_value=unavailability_count_failure *
                     [FailingInstanceEvent(instance_type=instance_8cpu.name, date=stopped)])

    unavailable_instances = list(availability_manager.get_unavailable())

    assert not unavailable_instances


def test_get_unavailable_if_available_and_multiple_failing_events():
    event_manager.get = MagicMock(
        return_value=[AvailableInstanceEvent(instance_type=instance_8cpu.name, date=stopped)]
                     + unavailability_count_failure *
                     [FailingInstanceEvent(instance_type=instance_8cpu.name, date=stopped)])

    unavailable_instances = list(availability_manager.get_unavailable())

    assert len(unavailable_instances) == 1
    assert instance_8cpu.name in unavailable_instances


def test_get_unavailable_if_multiple_failing_and_available_events():
    event_manager.get = MagicMock(
        return_value=unavailability_count_failure *
                     [FailingInstanceEvent(instance_type=instance_8cpu.name, date=stopped)]
                     + [AvailableInstanceEvent(instance_type=instance_8cpu.name, date=stopped)])

    unavailable_instances = list(availability_manager.get_unavailable())

    assert not unavailable_instances


def test_get_unavailable_if_couple_failing_and_available_and_couple_failing_events():
    event_manager.get = MagicMock(
        return_value=(unavailability_count_failure - 1) *
                     [FailingInstanceEvent(instance_type=instance_8cpu.name, date=stopped)]
                     + [AvailableInstanceEvent(instance_type=instance_8cpu.name, date=stopped)]
                     + (unavailability_count_failure - 1) *
                     [FailingInstanceEvent(instance_type=instance_8cpu.name, date=stopped)])

    unavailable_instances = list(availability_manager.get_unavailable())

    assert not unavailable_instances


def test_get_unavailable_if_first_instance_insufficient_and_second_instance_multiple_failing_events():
    event_manager.get = MagicMock(
        return_value=([InsufficientInstanceEvent(instance_8cpu.name, date=stopped)]
                      + unavailability_count_failure *
                      [FailingInstanceEvent(instance_type=instance_4cpu.name, date=stopped)]))

    unavailable_instances = list(availability_manager.get_unavailable())

    assert len(unavailable_instances) == 2
    assert instance_8cpu.name in unavailable_instances
    assert instance_4cpu.name in unavailable_instances
