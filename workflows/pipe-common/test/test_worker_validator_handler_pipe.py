#  Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import datetime

from mock import Mock, MagicMock

from pipeline.hpc.pipe import CloudPipelineWorkerValidatorHandler

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

HOST = 'HOST'

now = datetime.datetime(2018, 12, 21, 11, 10, 00)


api = Mock()
common_utils = Mock()
handler = CloudPipelineWorkerValidatorHandler(api=api, common_utils=common_utils)


def test_run_status_running():
    api.load_run_efficiently = MagicMock(return_value={'status': 'RUNNING'})

    assert handler.is_valid(HOST)


def test_run_status_failure():
    api.load_run_efficiently = MagicMock(return_value={'status': 'FAILURE'})

    assert not handler.is_valid(HOST)


def test_run_status_stopped():
    api.load_run_efficiently = MagicMock(return_value={'status': 'STOPPED'})

    assert not handler.is_valid(HOST)
