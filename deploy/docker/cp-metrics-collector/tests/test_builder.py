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

import copy

import pytest

from metrics_collector import builder, kube, quantity

from conftest import (CONTROL_PLANE_1_37, NODE_1_15, WORKER_1_37, find_docs, load_json, summary_name)


def _metrics(docs, family, **tags):
    found = find_docs(docs, family, **tags)
    assert len(found) == 1, 'Expected one %s document for %s, got %d' % (family, tags, len(found))
    return {name: value['value'] for name, value in found[0]['Metrics'].items()}


def _summary(cluster, node, scrape):
    return load_json(cluster.version, summary_name(cluster.version, node, scrape))


def _pod_stats(summary, name):
    return next(p for p in summary['pods'] if p['podRef']['name'] == name)


def _nanos(text):
    return builder.stat_time_nanos(text)


# --- the node -----------------------------------------------------------------------------------------

@pytest.mark.parametrize('version, node', [('1.15', NODE_1_15), ('1.37', WORKER_1_37)])
def test_node_cpu_and_memory(version, node, request):
    cluster = request.getfixturevalue('cluster_1_15' if version == '1.15' else 'cluster_1_37')
    _, docs = cluster.batch()
    first, second = _summary(cluster, node, 1)['node'], _summary(cluster, node, 2)['node']
    allocatable = quantity.milli_value(cluster.node(node)['status']['allocatable']['cpu'])

    cpu = _metrics(docs, 'cpu', type='node', nodename=node)
    rate = 1000 * (second['cpu']['usageCoreNanoSeconds'] - first['cpu']['usageCoreNanoSeconds']) \
        // (_nanos(second['cpu']['time']) - _nanos(first['cpu']['time']))
    assert cpu['cpu/usage'] == second['cpu']['usageCoreNanoSeconds']
    assert cpu['cpu/usage_rate'] == rate
    assert isinstance(cpu['cpu/usage_rate'], int)
    assert cpu['cpu/node_capacity'] == float(quantity.milli_value(cluster.node(node)['status']['capacity']['cpu']))
    assert cpu['cpu/node_allocatable'] == float(allocatable)
    assert cpu['cpu/node_utilization'] == pytest.approx(rate / allocatable)

    memory = _metrics(docs, 'memory', type='node', nodename=node)
    allocatable_memory = quantity.value(cluster.node(node)['status']['allocatable']['memory'])
    assert memory['memory/working_set'] == second['memory']['workingSetBytes']
    assert memory['memory/usage'] == second['memory']['usageBytes']
    assert memory['memory/node_capacity'] == float(quantity.value(cluster.node(node)['status']['capacity']['memory']))
    # heapster divided memory/usage, not the working set, by the allocatable memory.
    assert memory['memory/node_utilization'] == pytest.approx(second['memory']['usageBytes'] / allocatable_memory)


def test_node_network_sums_every_interface(cluster_1_15):
    _, docs = cluster_1_15.batch()
    first, second = (_summary(cluster_1_15, NODE_1_15, scrape)['node']['network'] for scrape in (1, 2))
    # The default interface the summary's inline fields describe (eth0) is not on this node.
    assert second['name'] == '' and len(second['interfaces']) > 1
    network = _metrics(docs, 'network', type='node', nodename=NODE_1_15)
    rx = [sum(i['rxBytes'] for i in stats['interfaces']) for stats in (first, second)]
    assert network['network/rx'] == rx[1]
    elapsed = _nanos(second['time']) - _nanos(first['time'])
    assert network['network/rx_rate'] == pytest.approx(1e9 * (rx[1] - rx[0]) / elapsed)
    assert 'network/tx_rate' in network


def test_node_requests_and_limits_are_the_sum_of_its_pods(cluster):
    sets, docs = cluster.batch()
    for node in cluster.nodes:
        name = node['metadata']['name']
        pods = [s for s in sets.values() if s.type == builder.TYPE_POD and s.labels['nodename'] == name]
        cpu = _metrics(docs, 'cpu', type='node', nodename=name)
        assert cpu['cpu/request'] == sum(p.values.get('cpu/request', 0) for p in pods)
        assert cpu['cpu/limit'] == sum(p.values.get('cpu/limit', 0) for p in pods)
        allocatable = quantity.milli_value(node['status']['allocatable']['cpu'])
        assert cpu['cpu/node_reservation'] == pytest.approx(cpu['cpu/request'] / allocatable)


def test_node_tags(cluster):
    _, docs = cluster.batch()
    for node in cluster.nodes:
        name = node['metadata']['name']
        tags = find_docs(docs, 'cpu', type='node', nodename=name)[0]['MetricsTags']
        assert tags['hostname'] == name
        assert tags['host_id'] == ''
        assert tags['schedulable'] == 'true'
        assert tags['cluster_name'] == 'default'
        assert tags['labels'] == ','.join(sorted('%s:%s' % kv for kv in node['metadata']['labels'].items()))


def test_unreadable_or_missing_capacity_counts_as_zero(cluster_1_15):
    scrapes = cluster_1_15.scrapes(1)
    scrapes[0].node['status']['capacity']['cpu'] = 'four'
    del scrapes[0].node['status']['allocatable']['memory']
    sets = builder.build(scrapes, cluster_1_15.pods, cluster_1_15.namespaces)
    builder.enrich_nodes(sets, scrapes)
    values = sets[builder.node_key(NODE_1_15)].values
    assert values['cpu/node_capacity'] == 0.0
    assert values['memory/node_allocatable'] == 0.0
    assert 'memory/node_utilization' not in values


def test_first_scrape_has_no_rates_and_no_cpu_utilization(cluster):
    _, docs = cluster.batch(scrapes_count=1)
    for node in cluster.nodes:
        cpu = _metrics(docs, 'cpu', type='node', nodename=node['metadata']['name'])
        assert 'cpu/usage' in cpu
        assert 'cpu/usage_rate' not in cpu
        assert 'cpu/node_utilization' not in cpu
        assert 'cpu/node_capacity' in cpu
        assert 'memory/node_utilization' in _metrics(docs, 'memory', type='node', nodename=node['metadata']['name'])


# --- the node's filesystems ---------------------------------------------------------------------------

def test_node_filesystems_1_15_are_heapsters_devices(cluster_1_15):
    _, docs = cluster_1_15.batch()
    devices = {d['MetricsTags']['resource_id']: d for d in find_docs(docs, 'filesystem', type='node')}
    assert set(devices) == {'/dev/nvme0n1p1', 'tmpfs'}
    root = {k: v['value'] for k, v in devices['/dev/nvme0n1p1']['Metrics'].items()}
    summary_fs = _summary(cluster_1_15, NODE_1_15, 2)['node']['fs']
    assert root['filesystem/limit'] == summary_fs['capacityBytes']
    assert root['filesystem/available'] == summary_fs['availableBytes']
    assert root['filesystem/usage'] > 0
    assert {'filesystem/inodes', 'filesystem/inodes_free'} <= set(root)
    assert 'filesystem/available' not in devices['tmpfs']['Metrics']


def test_node_filesystems_1_37_drop_mount_named_tmpfs_and_overlay(cluster_1_37):
    _, docs = cluster_1_37.batch()
    devices = {d['MetricsTags']['resource_id']
               for d in find_docs(docs, 'filesystem', type='node', nodename=WORKER_1_37)}
    # Recorded: /dev, /dev/root, /dev/shm, /dev/vdb1, /run, /run/credentials/..., /tmp, overlay, overlay_0-51.
    assert devices == {'/dev/root', '/dev/vdb1', 'tmpfs'}


@pytest.mark.parametrize('device, expected', [
    ('/dev/nvme0n1p1', '/dev/nvme0n1p1'), ('/dev/mapper/vg-lv', '/dev/mapper/vg-lv'), ('/dev/root', '/dev/root'),
    ('rpool/ROOT', 'rpool/ROOT'), ('tmpfs', 'tmpfs'), ('/dev/shm', 'tmpfs'), ('/dev', None), ('/run', None),
    ('/run/user/1000', None), ('/tmp', None), ('overlay', None), ('overlay_0-51', None),
    ('10.0.0.5:/export', None), ('fs-1.efs.us-east-1.amazonaws.com:/', None),
])
def test_heapster_device(device, expected):
    assert builder.heapster_device(device) == expected


def test_without_cadvisor_series_the_summary_filesystems_stand_in(cluster_1_15):
    scrape = cluster_1_15.scrapes(1)[0]
    scrape.root_filesystems = None
    sets = builder.build([scrape], cluster_1_15.pods, cluster_1_15.namespaces)
    labeled = sets[builder.node_key(NODE_1_15)].labeled
    # rootfs and imagefs are one device here, so imagefs is not written twice.
    assert {m.labels['resource_id'] for m in labeled} == {'rootfs'}
    containers = [s for s in sets.values() if s.type == builder.TYPE_POD_CONTAINER]
    assert {m.labels['resource_id'] for s in containers for m in s.labeled} == {'imagefs'}


def test_a_failed_cadvisor_read_writes_no_filesystems(cluster_1_15):
    scrape = cluster_1_15.scrapes(1)[0]
    scrape.root_filesystems = None
    scrape.filesystems_failed = True
    sets = builder.build([scrape], cluster_1_15.pods, cluster_1_15.namespaces)
    assert all(not s.labeled for s in sets.values())
    assert 'cpu/usage' in sets[builder.node_key(NODE_1_15)].values


# --- pods and containers ------------------------------------------------------------------------------

def test_load_pod_and_its_containers(cluster):
    _, docs = cluster.batch()
    node = NODE_1_15 if cluster.version == '1.15' else WORKER_1_37
    second = _pod_stats(_summary(cluster, node, 2), 'mc-load')
    pod = cluster.pods[('default', 'mc-load')]

    cpu = _metrics(docs, 'cpu', type='pod', pod_name='mc-load')
    assert cpu['cpu/limit'] == 500  # burn: 500m, chatty: none (0)
    assert cpu['cpu/request'] == 250
    assert cpu['cpu/usage'] == second['cpu']['usageCoreNanoSeconds']
    assert cpu['cpu/usage_rate'] > 0
    memory = _metrics(docs, 'memory', type='pod', pod_name='mc-load')
    assert memory['memory/limit'] == 128 * 2 ** 20
    assert memory['memory/working_set'] == second['memory']['workingSetBytes']
    assert 'network/rx_rate' in _metrics(docs, 'network', type='pod', pod_name='mc-load')

    tags = find_docs(docs, 'cpu', type='pod', pod_name='mc-load')[0]['MetricsTags']
    assert tags['namespace_name'] == 'default'
    assert tags['pod_id'] == pod['metadata']['uid']
    assert tags['namespace_id'] == cluster.namespaces['default']
    assert tags['labels'] == 'app:mc-load,runid:12345'
    assert tags['nodename'] == node

    burn = _metrics(docs, 'cpu', type='pod_container', pod_name='mc-load', container_name='burn')
    assert burn['cpu/limit'] == 500 and burn['cpu/request'] == 250
    chatty = _metrics(docs, 'memory', type='pod_container', pod_name='mc-load', container_name='chatty')
    assert chatty['memory/limit'] == 0 and chatty['memory/request'] == 0
    burn_tags = find_docs(docs, 'cpu', type='pod_container', container_name='burn')[0]['MetricsTags']
    assert burn_tags['container_base_image'] == 'busybox:1.36'
    assert burn_tags['labels'] == 'app:mc-load,runid:12345'

    rootfs = find_docs(docs, 'filesystem', type='pod_container', container_name='burn')
    assert len(rootfs) == 1
    expected_device = '/dev/nvme0n1p1' if cluster.version == '1.15' else '/dev/vdb1'
    assert rootfs[0]['MetricsTags']['resource_id'] == expected_device
    burn_stats = next(c for c in second['containers'] if c['name'] == 'burn')
    # Not the filesystem's inodes and free space, which the rootfs stats carry too.
    assert set(rootfs[0]['Metrics']) == {'filesystem/usage', 'filesystem/limit'}
    assert rootfs[0]['Metrics']['filesystem/usage']['value'] == burn_stats['rootfs']['usedBytes']
    assert rootfs[0]['Metrics']['filesystem/limit']['value'] == burn_stats['rootfs']['capacityBytes']
    # The container wrote 64 MiB into its root filesystem. Docker's usage comes from cAdvisor's
    # periodic `du`, which had counted part of it when this was recorded.
    assert rootfs[0]['Metrics']['filesystem/usage']['value'] > 2 ** 20


def test_every_summary_pod_and_container_gets_documents(cluster):
    _, docs = cluster.batch()
    for node in cluster.nodes:
        name = node['metadata']['name']
        summary = _summary(cluster, name, 2)
        for pod in summary['pods']:
            ref = pod['podRef']
            assert find_docs(docs, 'memory', type='pod', pod_name=ref['name'], namespace_name=ref['namespace'])
            for container in pod['containers']:
                assert find_docs(docs, 'memory', type='pod_container', pod_name=ref['name'],
                                 container_name=container['name'])


def test_static_pod_takes_the_mirror_pods_uid(cluster_1_15):
    _, docs = cluster_1_15.batch()
    name = 'kube-apiserver-' + NODE_1_15
    summary_uid = _pod_stats(_summary(cluster_1_15, NODE_1_15, 2), name)['podRef']['uid']
    api_uid = cluster_1_15.pods[('kube-system', name)]['metadata']['uid']
    assert summary_uid != api_uid
    assert find_docs(docs, 'memory', type='pod', pod_name=name)[0]['MetricsTags']['pod_id'] == api_uid


def test_pod_unknown_to_the_api_keeps_its_stats(cluster_1_15):
    pods = {key: pod for key, pod in cluster_1_15.pods.items() if key[1] != 'mc-load' and 'kube-dns' not in key[1]}
    sets = builder.build(cluster_1_15.scrapes(1), pods, cluster_1_15.namespaces)
    dns = next(s for s in sets.values() if s.type == builder.TYPE_POD and s.labels['pod_name'].startswith('kube-dns'))
    assert 'labels' not in dns.labels
    assert dns.labels['pod_id']  # from the summary
    containers = [s for s in sets.values() if s.type == builder.TYPE_POD_CONTAINER
                  and s.labels['pod_name'] == dns.labels['pod_name']]
    assert containers and all('cpu/limit' not in c.values and 'container_base_image' not in c.labels
                              for c in containers)


def test_pod_without_pod_level_stats_sums_its_containers(cluster_1_15):
    scrapes = cluster_1_15.scrapes(2)
    for pod in scrapes[0].summary['pods']:
        pod.pop('cpu', None)
        pod.pop('memory', None)
    sets = builder.build(scrapes, cluster_1_15.pods, cluster_1_15.namespaces)
    builder.aggregate(sets)
    for metric_set in sets.values():
        if metric_set.type != builder.TYPE_POD:
            continue
        containers = [s for s in sets.values() if s.type == builder.TYPE_POD_CONTAINER
                      and s.labels['pod_name'] == metric_set.labels['pod_name']]
        assert metric_set.values['memory/working_set'] == sum(c.values['memory/working_set'] for c in containers)
        # A cumulative counter is never summed: each container's starts at its own start time.
        assert 'cpu/usage' not in metric_set.values


# --- rates --------------------------------------------------------------------------------------------

def _single_set(cpu_usage, time, start='2026-11-20T12:00:00Z'):
    metric_set = builder.MetricSet(labels={'type': builder.TYPE_NODE, 'nodename': 'n'}, start_time=start)
    metric_set.values['cpu/usage'] = cpu_usage
    metric_set.stat_times['cpu/usage'] = builder.stat_time_nanos(time)
    metric_set.values['network/rx'] = cpu_usage
    metric_set.stat_times['network/rx'] = builder.stat_time_nanos(time)
    return {'node:n': metric_set}


def test_rates_are_heapsters():
    rates = builder.RateCalculator()
    rates.process(_single_set(1000000000, '2026-11-20T12:00:00Z'))
    current = _single_set(1000000000 + 45 * 10 ** 9 // 2, '2026-11-20T12:00:30Z')
    rates.process(current)
    # 22.5 s of CPU over 30 s: 750 millicores, an integer.
    assert current['node:n'].values['cpu/usage_rate'] == 750
    assert current['node:n'].values['network/rx_rate'] == pytest.approx(22.5e9 / 30)


@pytest.mark.parametrize('second', [
    _single_set(2 * 10 ** 9, '2026-11-20T12:00:30Z', start='2026-11-20T12:00:10Z'),  # restarted
    _single_set(10 ** 8, '2026-11-20T12:00:30Z'),                                      # counter went down
    _single_set(2 * 10 ** 9, '2026-11-20T12:00:00Z'),                                  # stale stats
])
def test_no_rate(second):
    rates = builder.RateCalculator()
    rates.process(_single_set(10 ** 9, '2026-11-20T12:00:00Z'))
    rates.process(second)
    assert 'cpu/usage_rate' not in second['node:n'].values


def test_rates_use_the_previous_batch_only():
    rates = builder.RateCalculator()
    rates.process(_single_set(10 ** 9, '2026-11-20T12:00:00Z'))
    rates.process({})
    third = _single_set(2 * 10 ** 9, '2026-11-20T12:01:00Z')
    rates.process(third)
    assert 'cpu/usage_rate' not in third['node:n'].values


# --- documents ----------------------------------------------------------------------------------------

def test_documents_are_heapsters(cluster):
    _, docs = cluster.batch(epoch=1795177260)
    families = {family for family, _ in docs}
    assert families == {'cpu', 'memory', 'network', 'filesystem'}
    for family, doc in docs:
        assert set(doc) == {family.title() + 'MetricsTimestamp', 'MetricsTags', 'Metrics'}
        assert doc[family.title() + 'MetricsTimestamp'] == '2026-11-20T12:21:00Z'
        assert all(name.startswith(family + '/') and set(value) == {'value'} for name, value in doc['Metrics'].items())
        assert doc['MetricsTags']['type'] in ('node', 'pod', 'pod_container')
        assert (family == 'filesystem') == ('resource_id' in doc['MetricsTags'])


def test_one_document_per_family_and_device():
    metric_set = builder.MetricSet(labels={'type': 'node', 'nodename': 'n'})
    metric_set.values.update({'cpu/usage': 1, 'cpu/usage_rate': 2, 'memory/usage': 3, 'uptime': 4})
    metric_set.labeled += [builder.LabeledMetric('filesystem/usage', {'resource_id': 'a'}, 5),
                           builder.LabeledMetric('filesystem/limit', {'resource_id': 'a'}, 6),
                           builder.LabeledMetric('filesystem/usage', {'resource_id': 'b'}, 7)]
    docs = builder.documents({'node:n': metric_set}, 0, 'c')
    assert sorted((family, doc['MetricsTags'].get('resource_id'), sorted(doc['Metrics'])) for family, doc in docs) == [
        ('cpu', None, ['cpu/usage', 'cpu/usage_rate']),
        ('filesystem', 'a', ['filesystem/limit', 'filesystem/usage']),
        ('filesystem', 'b', ['filesystem/usage']),
        ('memory', None, ['memory/usage']),
    ]
    assert all(doc['MetricsTags']['cluster_name'] == 'c' for _, doc in docs)


@pytest.mark.parametrize('epoch, text', [(0, '1970-01-01T00:00:00Z'), (1795177230, '2026-11-20T12:20:30Z'),
                                         (1795177230.5, '2026-11-20T12:20:30.5Z')])
def test_timestamp_format(epoch, text):
    assert builder.format_timestamp(epoch) == text


def test_node_that_is_not_ready_is_not_scraped(cluster_1_37):
    node = copy.deepcopy(cluster_1_37.node(CONTROL_PLANE_1_37))
    for condition in node['status']['conditions']:
        if condition['type'] == 'Ready':
            condition['status'] = 'False'
    assert kube.node_target(node, 10250) is None
