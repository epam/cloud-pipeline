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

import requests

from metrics_collector.collector import Collector

from conftest import (CONTROL_PLANE_1_37, FakeResponse, WORKER_1_37, find_docs, load_json, root_filesystems,
                      summary_name)


class FakeKube:
    def __init__(self, cluster):
        self.cluster = cluster

    def nodes(self):
        return self.cluster.nodes

    def pods(self):
        return self.cluster.pod_list

    def namespaces(self):
        return self.cluster.namespace_list


class FakeKubelets:
    """Answers with a cluster's recorded scrape `scrape`, or with the errors set per node."""

    def __init__(self, cluster):
        self.cluster = cluster
        self.scrape = 1
        self.summary_errors = {}
        self.cadvisor_errors = {}

    def summary(self, target):
        if target.name in self.summary_errors:
            raise self.summary_errors[target.name]
        return load_json(self.cluster.version, summary_name(self.cluster.version, target.name, self.scrape))

    def root_filesystems(self, target):
        if target.name in self.cadvisor_errors:
            raise self.cadvisor_errors[target.name]
        return root_filesystems(self.cluster.version, target.name)


class FakeWriter:
    def __init__(self):
        self.batches = []

    def write(self, epoch, docs):
        self.batches.append((epoch, docs))
        return 0


def _collector(cluster):
    kubelets, writer = FakeKubelets(cluster), FakeWriter()
    return Collector(FakeKube(cluster), kubelets, writer, 10250, 'default', 4), kubelets, writer


def test_two_batches(cluster):
    collector, kubelets, writer = _collector(cluster)
    first = collector.collect(1795177230)
    kubelets.scrape = 2
    second = collector.collect(1795177260)
    collector.close()
    assert first.nodes == second.nodes == len(cluster.nodes)
    assert second.scraped == len(cluster.nodes) and second.rejected == 0
    assert [epoch for epoch, _ in writer.batches] == [1795177230, 1795177260]
    _, docs = writer.batches[1]
    assert second.documents == len(docs)
    for node in cluster.nodes:
        cpu = find_docs(docs, 'cpu', type='node', nodename=node['metadata']['name'])[0]['Metrics']
        assert 'cpu/usage_rate' in cpu and 'cpu/node_utilization' in cpu


def test_a_node_whose_summary_fails_is_left_out(cluster_1_37):
    collector, kubelets, writer = _collector(cluster_1_37)
    kubelets.summary_errors[CONTROL_PLANE_1_37] = requests.ConnectionError('refused')
    result = collector.collect(1795177230)
    assert (result.nodes, result.scraped) == (2, 1)
    _, docs = writer.batches[0]
    assert not find_docs(docs, nodename=CONTROL_PLANE_1_37)
    assert find_docs(docs, 'cpu', type='node', nodename=WORKER_1_37)


def _node_devices(docs, node):
    return {d['MetricsTags']['resource_id'] for d in find_docs(docs, 'filesystem', type='node', nodename=node)}


def test_cadvisor_not_found_falls_back_to_the_summary(cluster_1_37):
    collector, kubelets, writer = _collector(cluster_1_37)
    kubelets.cadvisor_errors[WORKER_1_37] = requests.HTTPError('404', response=FakeResponse(404))
    collector.collect(1795177230)
    _, docs = writer.batches[0]
    assert _node_devices(docs, WORKER_1_37) == {'rootfs'}
    assert _node_devices(docs, CONTROL_PLANE_1_37) == {'/dev/root', '/dev/vdb1', 'tmpfs'}


def test_cadvisor_failure_writes_no_filesystems_for_the_node(cluster_1_37):
    collector, kubelets, writer = _collector(cluster_1_37)
    kubelets.cadvisor_errors[WORKER_1_37] = requests.Timeout('slow')
    collector.collect(1795177230)
    _, docs = writer.batches[0]
    assert not find_docs(docs, 'filesystem', nodename=WORKER_1_37)
    assert find_docs(docs, 'memory', type='node', nodename=WORKER_1_37)
    kubelets.cadvisor_errors[WORKER_1_37] = requests.HTTPError('500', response=FakeResponse(500))
    collector.collect(1795177260)
    _, docs = writer.batches[1]
    assert not find_docs(docs, 'filesystem', nodename=WORKER_1_37)


def test_nodes_that_are_not_ready_are_not_scraped(cluster_1_37):
    for condition in cluster_1_37.node(WORKER_1_37)['status']['conditions']:
        if condition['type'] == 'Ready':
            condition['status'] = 'False'
    collector, _, writer = _collector(cluster_1_37)
    result = collector.collect(1795177230)
    assert (result.nodes, result.scraped) == (1, 1)
    assert not find_docs(writer.batches[0][1], nodename=WORKER_1_37)
