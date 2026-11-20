# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import pytest

from metrics_collector import quantity


@pytest.mark.parametrize('text, millis', [
    ('4', 4000), ('3920m', 3920), ('0.5', 500), ('250m', 250), ('1.0001', 1001), ('100u', 1), ('2k', 2000000),
])
def test_milli_value_rounds_up(text, millis):
    assert quantity.milli_value(text) == millis


@pytest.mark.parametrize('text, value', [
    ('16086188Ki', 16086188 * 1024), ('128Mi', 128 * 2 ** 20), ('1Gi', 2 ** 30), ('1e9', 10 ** 9),
    ('129M', 129 * 10 ** 6), ('128974848', 128974848), ('1.5Gi', 3 * 2 ** 29), ('1E', 10 ** 18),
    ('1E3', 1000), ('100m', 1), ('28919288170', 28919288170),
])
def test_value(text, value):
    assert quantity.value(text) == value


@pytest.mark.parametrize('text', ['', 'abc', '1Kb', '1 Gi', '--1', '1e'])
def test_not_a_quantity(text):
    with pytest.raises(ValueError):
        quantity.parse(text)
