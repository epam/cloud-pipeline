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

import math

import pytest

from metrics_collector import prometheus

from conftest import fixture_path


def test_sample_with_labels_and_timestamp():
    line = ('container_fs_limit_bytes{container="",device="/dev/nvme0n1p1",id="/",image="",name="",'
            'namespace="",pod=""} 3.2132542464e+10 1795177231464')
    name, labels, value = prometheus.parse_line(line)
    assert name == 'container_fs_limit_bytes'
    assert labels['device'] == '/dev/nvme0n1p1'
    assert labels['id'] == '/'
    assert labels['pod'] == ''
    assert value == 32132542464


def test_sample_without_labels():
    assert prometheus.parse_line('machine_cpu_cores 4') == ('machine_cpu_cores', {}, 4.0)


def test_escapes_and_trailing_comma():
    _, labels, _ = prometheus.parse_line(r'm{a="x\"y",b="c\\d",n="1\n2",} 1')
    assert labels == {'a': 'x"y', 'b': 'c\\d', 'n': '1\n2'}


def test_special_values():
    assert math.isnan(prometheus.parse_line('m{a="1"} NaN')[2])
    assert prometheus.parse_line('m +Inf')[2] == math.inf


@pytest.mark.parametrize('line', ['', '   ', '# HELP m help', '# TYPE m gauge'])
def test_comments_and_blanks(line):
    assert prometheus.parse_line(line) is None


@pytest.mark.parametrize('line', ['m', 'm{a="1"', 'm{a=1} 2', 'm{a="1"} x', 'm{a="1}'])
def test_malformed(line):
    with pytest.raises(prometheus.ParseError):
        prometheus.parse_line(line)


@pytest.mark.parametrize('version, name', [('1.15', 'cadvisor.txt'), ('1.37', 'cadvisor-cp-mc-137-worker.txt')])
def test_every_recorded_line_parses(version, name):
    count = 0
    with open(fixture_path(version, name)) as f:
        for line in f:
            if prometheus.parse_line(line) is not None:
                count += 1
    assert count > 100
