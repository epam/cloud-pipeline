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

"""Compares two sets of heapster-shaped indices over a time window, as the platform reads them.

For each node, pod and node filesystem, the averages of the fields the API's monitoring requesters
and the deployment autoscaler average, from `<left>-YYYY.MM.dd` (heapster's, by default) and from
`<right>-YYYY.MM.dd` (the collector's, run beside heapster under another index name):

    python -m metrics_collector.compare --right cp-metrics-collector --minutes 30
"""

import argparse
import json
import os
import sys
from datetime import datetime, timedelta, timezone

from metrics_collector import config as config_module
from metrics_collector.kube import new_session
from metrics_collector.mapping import timestamp_field

# (name, family, MetricsTags.type, the tags that identify an entity, the averaged metrics)
VIEWS = [
    ('node cpu', 'cpu', 'node', ['nodename'], ['cpu/usage_rate', 'cpu/node_utilization', 'cpu/node_capacity']),
    ('node memory', 'memory', 'node', ['nodename'],
     ['memory/working_set', 'memory/usage', 'memory/node_utilization', 'memory/node_capacity']),
    ('node network', 'network', 'node', ['nodename'], ['network/rx_rate', 'network/tx_rate']),
    ('node disk', 'filesystem', 'node', ['nodename', 'resource_id'], ['filesystem/usage', 'filesystem/limit']),
    ('pod cpu', 'cpu', 'pod', ['nodename', 'pod_name'], ['cpu/usage_rate', 'cpu/limit']),
    ('pod memory', 'memory', 'pod', ['nodename', 'pod_name'], ['memory/working_set', 'memory/limit']),
    ('pod network', 'network', 'pod', ['nodename', 'pod_name'], ['network/rx_rate', 'network/tx_rate']),
]
_RAW = {'nodename', 'pod_name'}
_MAX_BUCKETS = 10000


def index_names(index, start, end):
    names = []
    day = start.date()
    while day <= end.date():
        names.append('%s-%s' % (index, day.strftime('%Y.%m.%d')))
        day += timedelta(days=1)
    return names


def _field(tag):
    return 'MetricsTags.%s%s' % (tag, '.raw' if tag in _RAW else '')


def _query(family, set_type, tags, metrics, start, end):
    leaf = {'avg_' + metric: {'avg': {'field': 'Metrics.%s.value' % metric}} for metric in metrics}
    leaf['samples'] = {'value_count': {'field': timestamp_field(family)}}
    aggregations = leaf
    for tag in reversed(tags):
        aggregations = {tag: {'terms': {'field': _field(tag), 'size': _MAX_BUCKETS}, 'aggs': aggregations}}
    return {
        'size': 0,
        'query': {'bool': {'filter': [
            {'term': {'MetricsTags.type': set_type}},
            {'range': {timestamp_field(family): {'gte': int(start.timestamp() * 1000),
                                                 'lte': int(end.timestamp() * 1000)}}},
        ]}},
        'aggs': aggregations,
    }


def _flatten(aggregations, tags, metrics, key=()):
    if not tags:
        values = {metric: (aggregations.get('avg_' + metric) or {}).get('value') for metric in metrics}
        values['samples'] = (aggregations.get('samples') or {}).get('value')
        return {key: values}
    result = {}
    for bucket in (aggregations.get(tags[0]) or {}).get('buckets') or []:
        result.update(_flatten(bucket, tags[1:], metrics, key + (bucket['key'],)))
    return result


def averages(session, url, index, view, start, end, timeout=30):
    """{(tag values): {metric: average, 'samples': documents}} of one view in one set of indices."""
    _, family, set_type, tags, metrics = view
    path = '%s/%s/%s/_search' % (url.rstrip('/'), ','.join(index_names(index, start, end)), family)
    response = session.request('POST', path,
                               params={'ignore_unavailable': 'true', 'allow_no_indices': 'true'},
                               data=json.dumps(_query(family, set_type, tags, metrics, start, end)),
                               headers={'Content-Type': 'application/json'}, timeout=timeout)
    response.raise_for_status()
    return _flatten(response.json().get('aggregations') or {}, tags, metrics)


def difference(left, right):
    """The relative difference of right to left, in percent; None where it cannot be told."""
    if left is None or right is None:
        return None
    if left == 0:
        return 0.0 if right == 0 else None
    return 100.0 * (right - left) / abs(left)


def compare(session, url, left, right, start, end, views=VIEWS):
    rows = []
    for view in views:
        name, _, _, _, metrics = view
        left_values = averages(session, url, left, view, start, end)
        right_values = averages(session, url, right, view, start, end)
        for key in sorted(set(left_values) | set(right_values)):
            for metric in metrics + ['samples']:
                left_value = (left_values.get(key) or {}).get(metric)
                right_value = (right_values.get(key) or {}).get(metric)
                rows.append((name, '/'.join(str(part) for part in key), metric, left_value, right_value,
                             difference(left_value, right_value)))
    return rows


def _number(value):
    if value is None:
        return '-'
    if abs(value) >= 1000:
        return '%.0f' % value
    return '%.4g' % value


def render(rows, left, right, threshold):
    """A table: every row, or with `threshold`, only those that differ by more percent, or are missing."""
    lines = ['%-13s %-60s %-26s %16s %16s %8s' % ('view', 'entity', 'metric', left[:16], right[:16], 'diff %')]
    for name, entity, metric, left_value, right_value, diff in rows:
        if threshold is not None and diff is not None and abs(diff) <= threshold:
            continue
        lines.append('%-13s %-60s %-26s %16s %16s %8s' % (name, entity[:60], metric, _number(left_value),
                                                          _number(right_value),
                                                          '-' if diff is None else '%+.1f' % diff))
    return '\n'.join(lines)


def main(argv=None, env=None, session=None, now=None):
    env = os.environ if env is None else env
    parser = argparse.ArgumentParser(prog='python -m metrics_collector.compare',
                                     description=__doc__.split('\n\n')[0])
    parser.add_argument('--elastic', help='Elasticsearch URL; by default the collector\'s own setting')
    parser.add_argument('--left', default='heapster', help='the reference indices (default: heapster)')
    parser.add_argument('--right', required=True, help='the indices to check')
    parser.add_argument('--minutes', type=int, default=30, help='the window, ending now (default: 30)')
    parser.add_argument('--skip-minutes', type=int, default=1,
                        help='leave out the last minutes, which may still be written (default: 1)')
    parser.add_argument('--threshold', type=float,
                        help='show only the rows that differ by more percent, or that one side lacks')
    args = parser.parse_args(argv)
    url = args.elastic or config_module.elastic_url_from_env(env)
    end = (now or datetime.now(timezone.utc)) - timedelta(minutes=args.skip_minutes)
    start = end - timedelta(minutes=args.minutes)
    session = session or new_session(2)
    rows = compare(session, url, args.left, args.right, start, end)
    print('%s .. %s, %s against %s, at %s' % (start.isoformat(), end.isoformat(), args.right, args.left, url))
    print(render(rows, args.left, args.right, args.threshold))
    return 0


if __name__ == '__main__':
    sys.exit(main())
