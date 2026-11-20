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

"""The index mapping heapster v1.5.4 created (common/elasticsearch/mapping.go), in Elasticsearch 5 terms.

heapster wrote `"type": "string"` with `"index": "analyzed"` or `"not_analyzed"`; Elasticsearch 5 turns
those into `text` and `keyword`, which is what this writes. The readers depend on the `.raw` keyword
sub-fields (`MetricsTags.nodename.raw`, `MetricsTags.pod_name.raw`) and on the keyword `type` and
`resource_id`.
"""

FAMILY_METRICS = {
    'cpu': [
        'cpu/limit', 'cpu/request', 'cpu/usage', 'cpu/usage_rate',
        'cpu/node_allocatable', 'cpu/node_capacity', 'cpu/node_reservation', 'cpu/node_utilization',
    ],
    'filesystem': [
        'filesystem/available', 'filesystem/limit', 'filesystem/usage',
        'filesystem/inodes', 'filesystem/inodes_free',
    ],
    'memory': [
        'memory/limit', 'memory/major_page_faults', 'memory/major_page_faults_rate',
        'memory/page_faults', 'memory/page_faults_rate', 'memory/request', 'memory/usage',
        'memory/rss', 'memory/cache', 'memory/working_set',
        'memory/node_allocatable', 'memory/node_capacity', 'memory/node_utilization',
        'memory/node_reservation',
    ],
    'network': [
        'network/rx', 'network/rx_errors', 'network/rx_errors_rate', 'network/rx_rate',
        'network/tx', 'network/tx_errors', 'network/tx_errors_rate', 'network/tx_rate',
    ],
}
FAMILIES = tuple(FAMILY_METRICS)

_TEXT_WITH_RAW = {'type': 'text', 'fields': {'raw': {'type': 'keyword'}}}
_KEYWORD = {'type': 'keyword'}
_DATE = {'type': 'date', 'format': 'strict_date_optional_time||epoch_millis'}
_DOUBLE_VALUE = {'properties': {'value': {'type': 'double'}}}

_TAGS = {
    'container_base_image': _TEXT_WITH_RAW,
    'container_name': _TEXT_WITH_RAW,
    'host_id': _KEYWORD,
    'hostname': _TEXT_WITH_RAW,
    'labels': _TEXT_WITH_RAW,
    'namespace_id': _KEYWORD,
    'namespace_name': _TEXT_WITH_RAW,
    'nodename': _TEXT_WITH_RAW,
    'cluster_name': _KEYWORD,
    'pod_id': _KEYWORD,
    'pod_name': _TEXT_WITH_RAW,
    'resource_id': _KEYWORD,
    'type': _KEYWORD,
}


def timestamp_field(family):
    """`cpu` -> `CpuMetricsTimestamp`, as heapster's MetricFamilyTimestamp."""
    return family.title() + 'MetricsTimestamp'


def _type_mapping(properties):
    return {
        '_all': {'enabled': False},
        'properties': dict({'MetricsTags': {'properties': dict(_TAGS)}}, **properties),
    }


def index_mapping():
    """The body of the index creation request."""
    mappings = {'_default_': {'_all': {'enabled': False}}}
    for family, metrics in FAMILY_METRICS.items():
        mappings[family] = _type_mapping({
            timestamp_field(family): _DATE,
            'Metrics': {'properties': {name: _DOUBLE_VALUE for name in metrics}},
        })
    # heapster mapped its `general` family too (uptime, restart_count). This collector writes none,
    # but heapster may still write to an index this collector created, while the two are switched.
    mappings['general'] = _type_mapping({
        'MetricsName': _TEXT_WITH_RAW,
        timestamp_field('general'): _DATE,
        'MetricsValue': _DOUBLE_VALUE,
    })
    return {'mappings': mappings}
