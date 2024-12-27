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

from datetime import datetime

from pipeline.hpc.engine.kube import KubeResourceParser

parser = KubeResourceParser()


def test_parse_cpu():
    assert parser.parse_cpu('') == 0
    assert parser.parse_cpu('0') == 0
    assert parser.parse_cpu('1') == 1
    assert parser.parse_cpu('2') == 2
    assert parser.parse_cpu('16') == 16
    assert parser.parse_cpu('100m') == 1
    assert parser.parse_cpu('1000m') == 1
    assert parser.parse_cpu('1100m') == 2
    assert parser.parse_cpu('4000m') == 4
    assert parser.parse_cpu('4100m') == 5


def test_parse_mem():
    assert parser.parse_mem('') == 0
    assert parser.parse_mem('0') == 0
    assert parser.parse_mem('1G') == 1
    assert parser.parse_mem('2Gi') == 2
    assert parser.parse_mem('2G') == 2
    assert parser.parse_mem('1000Mi') == 1
    assert parser.parse_mem('1000M') == 1
    assert parser.parse_mem('1024Mi') == 1
    assert parser.parse_mem('1024M') == 1
    assert parser.parse_mem('4000Mi') == 4
    assert parser.parse_mem('4000M') == 4
    assert parser.parse_mem('4096Mi') == 4
    assert parser.parse_mem('4096M') == 4


def test_parse_date():
    assert parser.parse_date('2023-12-27T09:52:47Z') \
           == datetime(2023, 12, 27,
                       9, 52, 47)
