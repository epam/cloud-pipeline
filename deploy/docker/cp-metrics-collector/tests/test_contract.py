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

"""What the platform's readers of the heapster indices query, checked on both recorded clusters.

The readers: the API's dao/monitoring/metricrequester (CPURequester, MemoryRequester, FSRequester,
NetworkRequester and their Pod* variants) and cp-deployment-autoscaler's autoscaler/elastic.py.
"""

import pytest

from metrics_collector.mapping import FAMILY_METRICS, index_mapping

from conftest import find_docs

# (document type, MetricsTags.type, the metrics a reader averages)
READ = [
    ('cpu', 'node', ['cpu/usage_rate', 'cpu/node_utilization', 'cpu/node_capacity']),
    ('memory', 'node', ['memory/working_set', 'memory/node_capacity']),
    ('network', 'node', ['network/rx_rate', 'network/tx_rate']),
    ('filesystem', 'node', ['filesystem/usage', 'filesystem/limit']),
    ('cpu', 'pod', ['cpu/usage_rate', 'cpu/limit']),
    ('memory', 'pod', ['memory/working_set', 'memory/limit']),
    ('network', 'pod', ['network/rx_rate', 'network/tx_rate']),
    ('filesystem', 'pod_container', ['filesystem/usage', 'filesystem/limit']),
]
# The tags the readers filter and group on; `.raw` is the keyword sub-field of the first two.
TAGS = {'nodename', 'type'}
POD_TAGS = {'pod_name'}


@pytest.mark.parametrize('family, set_type, metrics', READ)
def test_every_read_metric_is_written(cluster, family, set_type, metrics):
    _, docs = cluster.batch()
    found = find_docs(docs, family, type=set_type)
    assert found, 'No %s documents of type %s' % (family, set_type)
    for metric in metrics:
        assert any(metric in doc['Metrics'] for doc in found), '%s is never written for %s' % (metric, set_type)
    for doc in found:
        assert TAGS <= set(doc['MetricsTags'])
        if set_type != 'node':
            assert POD_TAGS <= set(doc['MetricsTags'])
        if family == 'filesystem':
            assert doc['MetricsTags']['resource_id']


def test_cpu_capacity_is_cores_times_1000(cluster):
    # CPURequester drops the last three digits of the averaged capacity to get the cores.
    _, docs = cluster.batch()
    for doc in find_docs(docs, 'cpu', type='node'):
        capacity = doc['Metrics']['cpu/node_capacity']['value']
        assert capacity % 1000 == 0 and capacity >= 1000


def test_the_mapping_maps_every_written_metric_and_tag(cluster):
    _, docs = cluster.batch()
    mapping = index_mapping()['mappings']
    for family, doc in docs:
        properties = mapping[family]['properties']
        assert set(doc['Metrics']) <= set(properties['Metrics']['properties'])
        assert set(doc['Metrics']) <= set(FAMILY_METRICS[family])
        unmapped = set(doc['MetricsTags']) - set(properties['MetricsTags']['properties'])
        # heapster did not map `schedulable` either: Elasticsearch maps it on first use.
        assert unmapped <= {'schedulable'}


def test_the_mapping_has_the_keyword_fields_the_readers_query():
    mapping = index_mapping()['mappings']
    for family in FAMILY_METRICS:
        tags = mapping[family]['properties']['MetricsTags']['properties']
        assert tags['nodename']['fields']['raw']['type'] == 'keyword'
        assert tags['pod_name']['fields']['raw']['type'] == 'keyword'
        assert tags['type']['type'] == 'keyword'
        assert tags['resource_id']['type'] == 'keyword'
        timestamp = mapping[family]['properties'][family.title() + 'MetricsTimestamp']
        assert timestamp['type'] == 'date'
        assert mapping[family]['_all'] == {'enabled': False}
