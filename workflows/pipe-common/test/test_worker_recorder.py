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

from scripts.autoscale_sge import CloudPipelineWorkerRecorder

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

run_id = 12345
run_name = 'pipeline-12345'
started = datetime(2018, 12, 21, 11, 00, 00)
stopped = datetime(2018, 12, 21, 11, 5, 00)
started_str = '2018-12-21 11:00:00.000'
stopped_str = '2018-12-21 11:05:00.000'
instance_type = 'm5.xlarge'
capacity = 5

api = Mock()
worker_recorder = CloudPipelineWorkerRecorder(api=api, capacity=capacity)


def setup_function():
    worker_recorder.clear()


def test_record():
    api.load_run = MagicMock(return_value=_sufficient_capacity_run())

    worker_recorder.record(run_id)

    records = worker_recorder.get()
    assert len(records) == 1
    record = records[0]
    assert record.id == run_id
    assert record.name == run_name
    assert record.instance_type == instance_type
    assert record.started == started
    assert record.stopped == stopped


def test_record_sufficient_instance_type():
    api.load_run = MagicMock(return_value=_sufficient_capacity_run())

    worker_recorder.record(run_id)

    records = worker_recorder.get()
    assert len(records) == 1
    record = records[0]
    assert not record.has_insufficient_instance_capacity


def test_record_insufficient_instance_type():
    api.load_run = MagicMock(return_value=_insufficient_capacity_run())

    worker_recorder.record(run_id)

    records = worker_recorder.get()
    assert len(records) == 1
    record = records[0]
    assert record.has_insufficient_instance_capacity


def test_record_multiple_workers():
    api.load_run = MagicMock(return_value=_insufficient_capacity_run())

    for _ in range(0, capacity * 2):
        worker_recorder.record(run_id)

    records = worker_recorder.get()
    assert len(records) == capacity


def _insufficient_capacity_run():
    run = _sufficient_capacity_run()
    run['status'] = 'FAILURE'
    run['stateReasonMessage'] = 'Insufficient instance capacity.'
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
