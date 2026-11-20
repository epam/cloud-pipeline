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

"""heapster v1.5.4's metric sets, built from the kubelet summary and the root cgroup's filesystems.

heapster read the legacy cAdvisor endpoint, then ran its processors in this order: the rate
calculator, the pod and namespace enrichers, the pod and node aggregators and the node autoscaling
enricher (metrics/heapster.go). `build`, `RateCalculator.process`, `aggregate` and `enrich_nodes` do
the same, in the same order, for the three set types the platform reads: `node`, `pod` and
`pod_container`. The `sys_container`, `ns` and `cluster` sets are not built: nothing reads them.
"""

from dataclasses import dataclass, field
from datetime import datetime, timezone, timedelta

from metrics_collector import quantity
from metrics_collector.mapping import FAMILIES, timestamp_field

TYPE_NODE = 'node'
TYPE_POD = 'pod'
TYPE_POD_CONTAINER = 'pod_container'

# heapster's RateMetricsMapping, without the disk I/O ones, which nothing reads.
CUMULATIVE_RATES = {
    'cpu/usage': 'cpu/usage_rate',
    'memory/page_faults': 'memory/page_faults_rate',
    'memory/major_page_faults': 'memory/major_page_faults_rate',
    'network/rx': 'network/rx_rate',
    'network/rx_errors': 'network/rx_errors_rate',
    'network/tx': 'network/tx_rate',
    'network/tx_errors': 'network/tx_errors_rate',
}
SPEC_METRICS = ('cpu/request', 'cpu/limit', 'memory/request', 'memory/limit')

_MEMORY_GAUGES = (('usageBytes', 'memory/usage'), ('workingSetBytes', 'memory/working_set'),
                  ('rssBytes', 'memory/rss'))
_MEMORY_CUMULATIVES = (('pageFaults', 'memory/page_faults'), ('majorPageFaults', 'memory/major_page_faults'))
_NETWORK_CUMULATIVES = (('rxBytes', 'network/rx'), ('rxErrors', 'network/rx_errors'),
                        ('txBytes', 'network/tx'), ('txErrors', 'network/tx_errors'))
_FS_FIELDS = (('usedBytes', 'filesystem/usage'), ('capacityBytes', 'filesystem/limit'),
              ('availableBytes', 'filesystem/available'), ('inodes', 'filesystem/inodes'),
              ('inodesFree', 'filesystem/inodes_free'))
_EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)


@dataclass
class LabeledMetric:
    name: str
    labels: dict
    value: object


@dataclass
class MetricSet:
    labels: dict
    # A rate is computed only between two scrapes of the same start time, as heapster's
    # CollectionStartTime check: a restarted container starts its counters again.
    start_time: object = None
    values: dict = field(default_factory=dict)
    # The time of the stats each cumulative value was read from, in nanoseconds.
    stat_times: dict = field(default_factory=dict)
    labeled: list = field(default_factory=list)

    @property
    def type(self):
        return self.labels['type']


@dataclass
class NodeScrape:
    target: object
    node: dict
    summary: dict
    # {device: {metric: value}} of the root cgroup, from /metrics/cadvisor; None where the node has
    # no such series (a Windows node), and then the summary's filesystems stand in for them.
    root_filesystems: dict = None
    # /metrics/cadvisor failed this time: no filesystem documents for the node rather than the
    # summary's, which would add series of other names to its charts.
    filesystems_failed: bool = False


def node_key(name):
    return 'node:' + name


def pod_key(namespace, name):
    return 'namespace:%s/pod:%s' % (namespace, name)


def container_key(namespace, name, container):
    return 'namespace:%s/pod:%s/container:%s' % (namespace, name, container)


def join_labels(labels):
    """heapster's LabelCopier: `key:value` pairs, sorted, joined by `,`."""
    return ','.join(sorted('%s:%s' % (key, value) for key, value in (labels or {}).items()))


def stat_time_nanos(text):
    if not text:
        return None
    moment = datetime.fromisoformat(text.replace('Z', '+00:00'))
    if moment.tzinfo is None:
        moment = moment.replace(tzinfo=timezone.utc)
    return (moment - _EPOCH) // timedelta(microseconds=1) * 1000


def format_timestamp(epoch_seconds):
    """The batch time as Go's time.Time JSON writes a UTC time: RFC 3339, no zero fraction."""
    moment = datetime.fromtimestamp(epoch_seconds, tz=timezone.utc)
    if moment.microsecond:
        return moment.strftime('%Y-%m-%dT%H:%M:%S.%f').rstrip('0') + 'Z'
    return moment.strftime('%Y-%m-%dT%H:%M:%SZ')


def _put_cumulative(metric_set, name, value, time):
    if value is None:
        return
    metric_set.values[name] = int(value)
    nanos = stat_time_nanos(time)
    if nanos is not None:
        metric_set.stat_times[name] = nanos


def _put_stats(metric_set, cpu, memory, network):
    if cpu:
        _put_cumulative(metric_set, 'cpu/usage', cpu.get('usageCoreNanoSeconds'), cpu.get('time'))
    if memory:
        for source, name in _MEMORY_GAUGES:
            if memory.get(source) is not None:
                metric_set.values[name] = int(memory[source])
        for source, name in _MEMORY_CUMULATIVES:
            _put_cumulative(metric_set, name, memory.get(source), memory.get('time'))
    if network:
        # heapster summed every interface cAdvisor reported. The summary lists them all in
        # `interfaces`; its inline fields are only the default interface, `eth0`, which is often
        # absent on the node (`ens5`, for one), and then empty.
        interfaces = network.get('interfaces') or ([network] if network.get('name') else [])
        if interfaces:
            for source, name in _NETWORK_CUMULATIVES:
                total = sum(int(interface.get(source) or 0) for interface in interfaces)
                _put_cumulative(metric_set, name, total, network.get('time'))


def heapster_device(device):
    """The name heapster's cAdvisor gave a root cgroup filesystem, None for one it did not report.

    The cAdvisor of Kubernetes 1.15 (v0.33) reported ext*, xfs, btrfs and zfs filesystems by their
    device, and the tmpfs ones all as one `tmpfs` (the first mount, normally /dev/shm), which the API
    leaves out of a node's disk space. The cAdvisor of current kubelets also reports every tmpfs by
    its mount point (`/dev/shm`, `/run`, `/tmp`), overlay mounts (`overlay_<major>-<minor>`) and NFS
    mounts (`<server>:<path>`), which would add memory and remote storage to the node's disks.
    """
    if device == 'tmpfs' or device == '/dev/shm':
        return 'tmpfs'
    if device.startswith('overlay') or ':' in device:
        return None
    if device.startswith('/') and (device == '/dev' or not device.startswith('/dev/')):
        # A tmpfs, named by its mount point: a device is /dev/<name>.
        return None
    return device


def _fs_values(stats):
    return {name: int(stats[source]) for source, name in _FS_FIELDS if (stats or {}).get(source) is not None}


def _image_fs_device(root_filesystems, image_fs):
    """The device of the runtime's image filesystem, the one holding the containers' root filesystems.

    The summary does not name it, so it is the root cgroup's device of the same capacity. heapster
    took the device cAdvisor named for each container, which is that one.
    """
    capacity = (image_fs or {}).get('capacityBytes')
    if capacity is not None:
        for source_device in sorted(root_filesystems or {}):
            device = heapster_device(source_device)
            if device not in (None, 'tmpfs') \
                    and root_filesystems[source_device].get('filesystem/limit') == int(capacity):
                return device
    return 'imagefs'


def _node_filesystems(summary_node, root_filesystems):
    node_fs = summary_node.get('fs') or {}
    image_fs = (summary_node.get('runtime') or {}).get('imageFs') or {}
    if root_filesystems is None:
        # No root cgroup series (a Windows node): the summary's filesystems, named by their role.
        filesystems = {'rootfs': _fs_values(node_fs)} if node_fs else {}
        if image_fs and image_fs.get('capacityBytes') != node_fs.get('capacityBytes'):
            filesystems['imagefs'] = _fs_values(image_fs)
        return filesystems
    filesystems = {}
    for source_device, values in sorted(root_filesystems.items()):
        device = heapster_device(source_device)
        if device is None or device in filesystems:
            continue
        values = dict(values)
        # cAdvisor's Prometheus series have no available bytes; the summary has them for the
        # devices it knows, matched by capacity.
        for stats in (node_fs, image_fs):
            if stats.get('availableBytes') is not None and stats.get('capacityBytes') is not None \
                    and values.get('filesystem/limit') == int(stats['capacityBytes']) and device != 'tmpfs':
                values['filesystem/available'] = int(stats['availableBytes'])
                break
        filesystems[device] = values
    return filesystems


def _spec_values(container):
    resources = container.get('resources') or {}
    requests = resources.get('requests') or {}
    limits = resources.get('limits') or {}
    return {
        'cpu/request': quantity.milli_value(requests['cpu']) if 'cpu' in requests else 0,
        'memory/request': quantity.value(requests['memory']) if 'memory' in requests else 0,
        'cpu/limit': quantity.milli_value(limits['cpu']) if 'cpu' in limits else 0,
        'memory/limit': quantity.value(limits['memory']) if 'memory' in limits else 0,
    }


def build(scrapes, pods, namespaces):
    """The metric sets of one batch, keyed as heapster keyed them.

    `pods` maps (namespace, name) to the API's Pod, `namespaces` maps a name to its uid: the pod
    and namespace enrichers' data. A pod the API no longer knows still gets its sets, without its
    labels, its spec's requests and limits and its container images, as heapster left it.
    """
    sets = {}
    for scrape in scrapes:
        summary_node = scrape.summary.get('node') or {}
        node = scrape.node
        name = scrape.target.name
        common = {
            'nodename': name,
            'hostname': scrape.target.hostname,
            'host_id': (node.get('spec') or {}).get('externalID') or '',
        }
        schedulable = 'false' if (node.get('spec') or {}).get('unschedulable') else 'true'
        node_set = MetricSet(labels=dict(common, type=TYPE_NODE, schedulable=schedulable,
                                         labels=join_labels((node.get('metadata') or {}).get('labels'))),
                             start_time=summary_node.get('startTime'))
        _put_stats(node_set, summary_node.get('cpu'), summary_node.get('memory'), summary_node.get('network'))
        image_device = None
        if not scrape.filesystems_failed:
            for device, values in sorted(_node_filesystems(summary_node, scrape.root_filesystems).items()):
                for metric, value in values.items():
                    node_set.labeled.append(LabeledMetric(metric, {'resource_id': device}, value))
            image_device = _image_fs_device(scrape.root_filesystems,
                                            (summary_node.get('runtime') or {}).get('imageFs'))
        sets[node_key(name)] = node_set

        for pod_stats in scrape.summary.get('pods') or []:
            _build_pod(sets, pod_stats, common, pods, namespaces, image_device)
    return sets


def _build_pod(sets, pod_stats, common, pods, namespaces, image_device):
    ref = pod_stats.get('podRef') or {}
    namespace, name = ref.get('namespace'), ref.get('name')
    if not namespace or not name:
        return
    pod = pods.get((namespace, name))
    labels = dict(common, type=TYPE_POD, pod_name=name, namespace_name=namespace)
    # A static pod's uid in the summary is not its mirror pod's; heapster took the API's.
    labels['pod_id'] = ((pod or {}).get('metadata') or {}).get('uid') or ref.get('uid') or ''
    if pod is not None:
        labels['labels'] = join_labels(pod['metadata'].get('labels'))
    if namespace in namespaces:
        labels['namespace_id'] = namespaces[namespace]
    pod_set = MetricSet(labels=labels, start_time=pod_stats.get('startTime'))
    _put_stats(pod_set, pod_stats.get('cpu'), pod_stats.get('memory'), pod_stats.get('network'))
    sets[pod_key(namespace, name)] = pod_set

    spec_containers = {container.get('name'): container
                       for container in ((pod or {}).get('spec') or {}).get('containers') or []}
    for container_stats in pod_stats.get('containers') or []:
        container_name = container_stats.get('name')
        if not container_name:
            continue
        container_labels = dict(labels, type=TYPE_POD_CONTAINER, container_name=container_name)
        container_set = MetricSet(labels=container_labels, start_time=container_stats.get('startTime'))
        _put_stats(container_set, container_stats.get('cpu'), container_stats.get('memory'), None)
        spec = spec_containers.get(container_name)
        if spec is not None:
            container_labels['container_base_image'] = spec.get('image') or ''
            container_set.values.update(_spec_values(spec))
        if image_device is not None:
            for metric, value in _fs_values(container_stats.get('rootfs')).items():
                container_set.labeled.append(LabeledMetric(metric, {'resource_id': image_device}, value))
        sets[container_key(namespace, name, container_name)] = container_set


class RateCalculator:
    """heapster's rate calculator: rates of the cumulative metrics between two consecutive batches.

    `cpu/usage_rate` is in whole millicores (`1000 * nanoseconds / nanoseconds`, an integer
    division), the others in units per second. No rate on a set's first scrape, nor when its start
    time changed, nor when a counter went down.
    """

    def __init__(self):
        self._previous = {}

    def process(self, sets):
        for key, current in sets.items():
            previous = self._previous.get(key)
            if previous is None or previous.start_time != current.start_time:
                continue
            for name, rate_name in CUMULATIVE_RATES.items():
                if name not in current.values or name not in previous.values:
                    continue
                current_time = current.stat_times.get(name)
                previous_time = previous.stat_times.get(name)
                if current_time is None or previous_time is None or current_time <= previous_time:
                    continue
                delta = current.values[name] - previous.values[name]
                if delta < 0:
                    continue
                elapsed = current_time - previous_time
                if name == 'cpu/usage':
                    current.values[rate_name] = 1000 * delta // elapsed
                else:
                    current.values[rate_name] = 1e9 * delta / elapsed
        self._previous = {
            key: MetricSet(labels={}, start_time=metric_set.start_time,
                           values={name: metric_set.values[name]
                                   for name in CUMULATIVE_RATES if name in metric_set.values},
                           stat_times=dict(metric_set.stat_times))
            for key, metric_set in sets.items()
        }


def aggregate(sets):
    """heapster's pod and node aggregators.

    A pod takes the sum of its containers' gauges and rates for every metric it has no value of its
    own for: the requests and limits always, and its usage only where the summary has no pod-level
    stats. heapster summed the containers onto the pause container's values; the pod-level stats
    are that sum already, from the pod's cgroup. A node takes the sum of its pods' requests and
    limits.
    """
    sums = {}
    for key, metric_set in sets.items():
        if metric_set.type != TYPE_POD_CONTAINER:
            continue
        labels = metric_set.labels
        target = sums.setdefault(pod_key(labels['namespace_name'], labels['pod_name']), {})
        for name, value in metric_set.values.items():
            if name in CUMULATIVE_RATES:
                continue
            target[name] = target.get(name, 0) + value
    for key, totals in sums.items():
        pod_set = sets.get(key)
        if pod_set is None:
            continue
        for name, total in totals.items():
            pod_set.values.setdefault(name, total)

    for metric_set in list(sets.values()):
        if metric_set.type != TYPE_POD:
            continue
        node_set = sets.get(node_key(metric_set.labels['nodename']))
        if node_set is None:
            continue
        for name in SPEC_METRICS:
            if name in metric_set.values:
                node_set.values[name] = node_set.values.get(name, 0) + metric_set.values[name]


def _resource(resources, name, convert):
    """A capacity or allocatable value, 0 where it is missing or unreadable, as heapster's zero Quantity."""
    value = (resources or {}).get(name)
    if value is None:
        return 0
    try:
        return convert(value)
    except ValueError:
        return 0


def enrich_nodes(sets, scrapes):
    """heapster's node autoscaling enricher: capacity, allocatable, utilization and reservation.

    `cpu/node_utilization` is `cpu/usage_rate` over the allocatable millicores and
    `memory/node_utilization` is `memory/usage` (not the working set) over the allocatable bytes.
    Unlike heapster, a node with no usage value yet (its first scrape has no rate) gets no
    utilization rather than 0, which would pull the averages down.
    """
    for scrape in scrapes:
        node_set = sets.get(node_key(scrape.target.name))
        if node_set is None:
            continue
        status = scrape.node.get('status') or {}
        capacity_cpu = _resource(status.get('capacity'), 'cpu', quantity.milli_value)
        capacity_memory = _resource(status.get('capacity'), 'memory', quantity.value)
        allocatable_cpu = _resource(status.get('allocatable'), 'cpu', quantity.milli_value)
        allocatable_memory = _resource(status.get('allocatable'), 'memory', quantity.value)
        values = node_set.values
        if allocatable_cpu:
            if 'cpu/usage_rate' in values:
                values['cpu/node_utilization'] = values['cpu/usage_rate'] / allocatable_cpu
            values['cpu/node_reservation'] = values.get('cpu/request', 0) / allocatable_cpu
        values['cpu/node_capacity'] = float(capacity_cpu)
        values['cpu/node_allocatable'] = float(allocatable_cpu)
        if allocatable_memory:
            if 'memory/usage' in values:
                values['memory/node_utilization'] = values['memory/usage'] / allocatable_memory
            values['memory/node_reservation'] = values.get('memory/request', 0) / allocatable_memory
        values['memory/node_capacity'] = float(capacity_memory)
        values['memory/node_allocatable'] = float(allocatable_memory)


def documents(sets, epoch_seconds, cluster_name):
    """[(family, document)] as heapster's Elasticsearch sink wrote them.

    One document per family per set, and one per family per labeled group (a filesystem's
    `resource_id`), each with the batch time, the set's tags plus `cluster_name`, and
    `Metrics.<name>.value`.
    """
    timestamp = format_timestamp(epoch_seconds)
    result = []
    for metric_set in sets.values():
        tags = dict(metric_set.labels, cluster_name=cluster_name)
        families = {}
        for name, value in metric_set.values.items():
            family = name.split('/', 1)[0]
            if family in FAMILIES:
                families.setdefault(family, {})[name] = {'value': value}
        for family, metrics in families.items():
            result.append((family, {timestamp_field(family): timestamp, 'MetricsTags': tags, 'Metrics': metrics}))
        groups = {}
        for metric in metric_set.labeled:
            family = metric.name.split('/', 1)[0]
            if family not in FAMILIES:
                continue
            group_key = (family, tuple(sorted(metric.labels.items())))
            group = groups.setdefault(group_key, (dict(tags, **metric.labels), {}))
            group[1][metric.name] = {'value': metric.value}
        for (family, _), (group_tags, metrics) in groups.items():
            result.append((family, {timestamp_field(family): timestamp, 'MetricsTags': group_tags,
                                    'Metrics': metrics}))
    return result
