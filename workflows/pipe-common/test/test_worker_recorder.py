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

from scripts.autoscale_sge import CloudPipelineWorkerRecorder, AvailableInstanceEvent, \
    InsufficientInstanceEvent, FailingInstanceEvent

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

run_id = 12345
run_name = 'pipeline-12345'
started = datetime(2018, 12, 21, 11, 00, 00)
stopped = datetime(2018, 12, 21, 11, 5, 00)
now = datetime(2018, 12, 21, 11, 10, 00)
started_str = '2018-12-21 11:00:00.000'
stopped_str = '2018-12-21 11:05:00.000'
instance_type = 'm5.xlarge'
capacity = 5

api = Mock()
event_manager = Mock()
clock = Mock()
worker_recorder = CloudPipelineWorkerRecorder(api=api, event_manager=event_manager, clock=clock)


def setup_function():
    clock.now = MagicMock(return_value=now)


def test_record_sufficient_instance_type():
    api.load_run = MagicMock(return_value=_sufficient_capacity_run())

    worker_recorder.record(run_id)

    event_manager.register.assert_called_with(
        AvailableInstanceEvent(instance_type=instance_type, date=now))


def test_record_insufficient_instance_type():
    api.load_run = MagicMock(return_value=_insufficient_capacity_run())

    worker_recorder.record(run_id)

    event_manager.register.assert_called_with(
        InsufficientInstanceEvent(instance_type=instance_type, date=stopped))


def test_record_failing_instance_type():
    api.load_run = MagicMock(return_value=_failing_run())

    worker_recorder.record(run_id)

    event_manager.register.assert_called_with(
        FailingInstanceEvent(instance_type=instance_type, date=stopped))


def _insufficient_capacity_run():
    run = _failing_run()
    run['stateReasonMessage'] = 'Insufficient instance capacity.'
    return run


def _failing_run():
    run = _sufficient_capacity_run()
    run['status'] = 'FAILURE'
    return run


def _sufficient_capacity_run():
    return {
        'podId': run_name,
        'startDate': started_str,
        'endDate': stopped_str,
        'instance': {
            'nodeType': instance_type
        }
    }
