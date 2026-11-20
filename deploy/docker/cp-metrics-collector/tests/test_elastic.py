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

import pytest

from metrics_collector import elastic
from metrics_collector.mapping import index_mapping

from conftest import FakeResponse, FakeSession

ES = 'http://es:9200'
EPOCH = 1795177230  # 2026-11-20T09:00:30Z
INDEX = 'heapster-2026.11.20'
DOCS = [('cpu', {'CpuMetricsTimestamp': '2026-11-20T09:00:30Z', 'MetricsTags': {'type': 'node'},
                 'Metrics': {'cpu/usage_rate': {'value': 5}}}),
        ('memory', {'MemoryMetricsTimestamp': '2026-11-20T09:00:30Z', 'MetricsTags': {'type': 'node'},
                    'Metrics': {'memory/usage': {'value': 6}}})]


def _bulk_ok(kwargs):
    return FakeResponse(body={'errors': False, 'items': []})


def _routes(**overrides):
    routes = {
        ('HEAD', ES + '/' + INDEX): FakeResponse(404),
        ('PUT', ES + '/' + INDEX): FakeResponse(200, body={'acknowledged': True}),
        ('GET', ES + '/%s/_alias' % INDEX): FakeResponse(200, body={INDEX: {'aliases': {}}}),
        ('POST', ES + '/_aliases'): FakeResponse(200, body={'acknowledged': True}),
        ('POST', ES + '/_bulk'): _bulk_ok,
    }
    routes.update(overrides)
    return routes


def test_index_name_is_the_utc_day():
    assert elastic.index_name('heapster', EPOCH) == INDEX
    assert elastic.index_name('cp-metrics', 1795132799) == 'cp-metrics-2026.11.19'


def test_creates_the_index_with_aliases_then_writes():
    session = FakeSession(_routes())
    writer = elastic.ElasticWriter(ES + '/', 'heapster', session, 5)
    assert writer.write(EPOCH, DOCS) == 0
    methods = [(method, url.replace(ES, '')) for method, url, _ in session.calls]
    assert methods == [('HEAD', '/' + INDEX), ('PUT', '/' + INDEX), ('GET', '/%s/_alias' % INDEX),
                       ('POST', '/_aliases'), ('POST', '/_bulk')]
    assert json.loads(session.calls[1][2]['data']) == index_mapping()
    aliases = json.loads(session.calls[3][2]['data'])['actions']
    assert [a['add']['alias'] for a in aliases] == ['heapster-cpu', 'heapster-filesystem', 'heapster-memory',
                                                    'heapster-network']
    bulk = session.calls[4][2]
    assert bulk['headers'] == {'Content-Type': 'application/x-ndjson'}
    lines = bulk['data'].split('\n')
    assert lines[-1] == '' and len(lines) == 5
    assert json.loads(lines[0]) == {'index': {'_index': INDEX, '_type': 'cpu'}}
    assert json.loads(lines[1]) == DOCS[0][1]
    assert json.loads(lines[2]) == {'index': {'_index': INDEX, '_type': 'memory'}}


def test_an_existing_index_is_checked_once_and_missing_aliases_added():
    session = FakeSession(_routes(**{
        ('HEAD', ES + '/' + INDEX): FakeResponse(200),
        ('GET', ES + '/%s/_alias' % INDEX): FakeResponse(200, body={INDEX: {'aliases': {
            'heapster-cpu': {}, 'heapster-memory': {}, 'heapster-network': {}}}}),
    }))
    writer = elastic.ElasticWriter(ES, 'heapster', session, 5)
    writer.write(EPOCH, DOCS)
    writer.write(EPOCH + 30, DOCS)
    methods = [(method, url.replace(ES, '')) for method, url, _ in session.calls]
    assert methods == [('HEAD', '/' + INDEX), ('GET', '/%s/_alias' % INDEX), ('POST', '/_aliases'),
                       ('POST', '/_bulk'), ('POST', '/_bulk')]
    assert json.loads(session.calls[2][2]['data']) == {
        'actions': [{'add': {'index': INDEX, 'alias': 'heapster-filesystem'}}]}


def test_an_index_another_writer_just_created_is_fine():
    session = FakeSession(_routes(**{('PUT', ES + '/' + INDEX): FakeResponse(
        400, text='{"error":{"type":"index_already_exists_exception"}}')}))
    assert elastic.ElasticWriter(ES, 'heapster', session, 5).write(EPOCH, DOCS) == 0


def test_index_creation_failure_raises():
    session = FakeSession(_routes(**{('PUT', ES + '/' + INDEX): FakeResponse(400, text='mapper_parsing_exception')}))
    with pytest.raises(elastic.ElasticError):
        elastic.ElasticWriter(ES, 'heapster', session, 5).write(EPOCH, DOCS)


def test_rejected_documents_are_counted():
    rejected = FakeResponse(body={'errors': True, 'items': [
        {'index': {'status': 201}},
        {'index': {'status': 400, 'error': {'type': 'mapper_parsing_exception'}}}]})
    session = FakeSession(_routes(**{('POST', ES + '/_bulk'): rejected}))
    assert elastic.ElasticWriter(ES, 'heapster', session, 5).write(EPOCH, DOCS) == 1


def test_failed_bulk_raises():
    session = FakeSession(_routes(**{('POST', ES + '/_bulk'): FakeResponse(503, text='unavailable')}))
    with pytest.raises(elastic.ElasticError):
        elastic.ElasticWriter(ES, 'heapster', session, 5).write(EPOCH, DOCS)


def test_bulk_is_split():
    session = FakeSession(_routes())
    docs = DOCS * 5
    elastic.ElasticWriter(ES, 'heapster', session, 5, bulk_size=4).write(EPOCH, docs)
    bulks = [kwargs['data'] for method, url, kwargs in session.calls if url.endswith('/_bulk')]
    assert [b.count('\n') // 2 for b in bulks] == [4, 4, 2]


def test_nothing_to_write():
    session = FakeSession({})
    assert elastic.ElasticWriter(ES, 'heapster', session, 5).write(EPOCH, []) == 0
    assert session.calls == []
