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

import json
from datetime import datetime, timezone

import pytest

from metrics_collector import compare

from conftest import FakeResponse, FakeSession

START = datetime(2026, 11, 19, 23, 50, tzinfo=timezone.utc)
END = datetime(2026, 11, 20, 0, 20, tzinfo=timezone.utc)
NODE_CPU = compare.VIEWS[0]
NODE_DISK = compare.VIEWS[3]


def _node_buckets(values):
    buckets = []
    for node, metrics in values.items():
        bucket = {'key': node, 'samples': {'value': 60}}
        bucket.update({'avg_' + metric: {'value': value} for metric, value in metrics.items()})
        buckets.append(bucket)
    return {'nodename': {'buckets': buckets}}


def test_index_names_span_the_days():
    assert compare.index_names('heapster', START, END) == ['heapster-2026.11.19', 'heapster-2026.11.20']


def test_query_filters_the_type_and_window_and_groups_by_raw_tags():
    session = FakeSession({('POST', 'http://es:9200/heapster-2026.11.19,heapster-2026.11.20/filesystem/_search'):
                           FakeResponse(body={'aggregations': {}})})
    assert compare.averages(session, 'http://es:9200/', 'heapster', NODE_DISK, START, END) == {}
    _, _, kwargs = session.calls[0]
    assert kwargs['params'] == {'ignore_unavailable': 'true', 'allow_no_indices': 'true'}
    body = json.loads(kwargs['data'])
    assert body['size'] == 0
    assert body['query']['bool']['filter'][0] == {'term': {'MetricsTags.type': 'node'}}
    assert body['query']['bool']['filter'][1]['range']['FilesystemMetricsTimestamp'] == {
        'gte': int(START.timestamp() * 1000), 'lte': int(END.timestamp() * 1000)}
    nodes = body['aggs']['nodename']
    assert nodes['terms']['field'] == 'MetricsTags.nodename.raw'
    devices = nodes['aggs']['resource_id']
    assert devices['terms']['field'] == 'MetricsTags.resource_id'
    assert devices['aggs']['avg_filesystem/usage'] == {'avg': {'field': 'Metrics.filesystem/usage.value'}}


def test_nested_buckets_are_flattened():
    body = {'aggregations': {'nodename': {'buckets': [{'key': 'n1', 'resource_id': {'buckets': [
        {'key': '/dev/sda1', 'avg_filesystem/usage': {'value': 5.0}, 'avg_filesystem/limit': {'value': 10.0},
         'samples': {'value': 2}}]}}]}}}
    session = FakeSession({('POST', 'http://es/c-2026.11.19,c-2026.11.20/filesystem/_search'): FakeResponse(body=body)})
    assert compare.averages(session, 'http://es', 'c', NODE_DISK, START, END) == {
        ('n1', '/dev/sda1'): {'filesystem/usage': 5.0, 'filesystem/limit': 10.0, 'samples': 2}}


@pytest.mark.parametrize('left, right, diff', [(100, 110, 10.0), (100, 90, -10.0), (0, 0, 0.0), (0, 1, None),
                                               (None, 1, None), (1, None, None), (-10, -5, 50.0)])
def test_difference(left, right, diff):
    assert compare.difference(left, right) == diff


def test_compare_and_render():
    path = 'http://es/%s-2026.11.19,%s-2026.11.20/cpu/_search'
    session = FakeSession({
        ('POST', path % ('heapster', 'heapster')): FakeResponse(body={'aggregations': _node_buckets({
            'n1': {'cpu/usage_rate': 100, 'cpu/node_utilization': 0.025, 'cpu/node_capacity': 4000},
            'gone': {'cpu/usage_rate': 1, 'cpu/node_utilization': 0.1, 'cpu/node_capacity': 1000}})}),
        ('POST', path % ('cmp', 'cmp')): FakeResponse(body={'aggregations': _node_buckets({
            'n1': {'cpu/usage_rate': 103, 'cpu/node_utilization': 0.02575, 'cpu/node_capacity': 4000}})}),
    })
    rows = compare.compare(session, 'http://es', 'heapster', 'cmp', START, END, views=[NODE_CPU])
    assert ('node cpu', 'n1', 'cpu/usage_rate', 100, 103, pytest.approx(3.0)) in rows
    assert ('node cpu', 'gone', 'cpu/usage_rate', 1, None, None) in rows
    table = compare.render(rows, 'heapster', 'cmp', threshold=5)
    lines = table.splitlines()
    assert lines[0].split()[:3] == ['view', 'entity', 'metric']
    assert not any(' n1 ' in line for line in lines[1:])
    assert any('gone' in line and 'cpu/usage_rate' in line for line in lines)
    assert 'cpu/usage_rate' in compare.render(rows, 'heapster', 'cmp', threshold=None)


def test_main_uses_the_collector_settings(capsys):
    calls = []

    class Session:
        def request(self, method, url, **kwargs):
            calls.append(url)
            return FakeResponse(body={'aggregations': {}})

    now = datetime(2026, 11, 20, 12, 0, tzinfo=timezone.utc)
    assert compare.main(['--right', 'cmp', '--minutes', '10'], env={'CP_HEAPSTER_ELK_INTERNAL_HOST': 'elk'},
                        session=Session(), now=now) == 0
    assert calls[0] == 'http://elk:30094/heapster-2026.11.20/cpu/_search'
    assert len(calls) == 2 * len(compare.VIEWS)
    out = capsys.readouterr().out
    assert out.startswith('2026-11-20T11:49:00+00:00 .. 2026-11-20T11:59:00+00:00, cmp against heapster')
