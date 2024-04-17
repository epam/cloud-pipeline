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

from pipeline.hpc.valid import GracePeriodWorkerValidatorHandler

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

HOST = 'HOST'

now = datetime.datetime(2018, 12, 21, 11, 10, 00)

invalid_timeout = 30

clock = Mock()
inner = Mock()
handler = GracePeriodWorkerValidatorHandler(inner=inner, grace_period=invalid_timeout, clock=clock)


def setup_function():
    clock.now = MagicMock(return_value=now)


def test_valid_job():
    inner.is_valid = MagicMock(return_value=True)

    assert handler.is_valid(HOST)


def test_invalid_job():
    inner.is_valid = MagicMock(return_value=False)

    assert handler.is_valid(HOST)


def test_invalid_job_inside_grace_period():
    inner.is_valid = MagicMock(return_value=False)

    assert handler.is_valid(HOST)

    clock.now = MagicMock(return_value=now + datetime.timedelta(seconds=invalid_timeout - 1))

    assert handler.is_valid(HOST)


def test_invalid_job_outside_grace_period():
    inner.is_valid = MagicMock(return_value=False)

    assert handler.is_valid(HOST)

    clock.now = MagicMock(return_value=now + datetime.timedelta(seconds=invalid_timeout + 1))

    assert not handler.is_valid(HOST)
