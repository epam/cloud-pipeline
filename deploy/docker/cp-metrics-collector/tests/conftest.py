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

"""Fixtures recorded from a kubeadm 1.15.4 cluster on Docker (one node) and a kind 1.37.0 cluster on
containerd (two nodes), each with a pod of two containers with and without requests and limits."""

import json
import os
import sys

import pytest
import requests

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

from metrics_collector import builder, kube, prometheus  # noqa: E402

FIXTURES = os.path.join(HERE, 'fixtures')
NODE_1_15 = 'ip-10-0-0-10.ec2.internal'
WORKER_1_37 = 'cp-mc-137-worker'
CONTROL_PLANE_1_37 = 'cp-mc-137-control-plane'


def fixture_path(version, name):
    return os.path.join(FIXTURES, version, name)


def load_json(version, name):
    with open(fixture_path(version, name)) as f:
        return json.load(f)


def summary_name(version, node, scrape):
    return 'summary-%d.json' % scrape if version == '1.15' else 'summary-%s-%d.json' % (node, scrape)


def cadvisor_name(version, node):
    return 'cadvisor.txt' if version == '1.15' else 'cadvisor-%s.txt' % node


def root_filesystems(version, node):
    """What KubeletClient.root_filesystems returns for the recorded /metrics/cadvisor."""
    devices = {}
    with open(fixture_path(version, cadvisor_name(version, node))) as f:
        for line in f:
            parsed = prometheus.parse_line(line)
            if parsed is None or parsed[0] not in kube.ROOT_FS_SERIES or parsed[1].get('id') != '/':
                continue
            devices.setdefault(parsed[1]['device'], {})[kube.ROOT_FS_SERIES[parsed[0]]] = int(round(parsed[2]))
    return devices


class Cluster:
    """A recorded cluster: its API lists, and its nodes' scrapes."""

    def __init__(self, version):
        self.version = version
        self.nodes = load_json(version, 'nodes.json')['items']
        self.pod_list = load_json(version, 'pods.json')['items']
        self.namespace_list = load_json(version, 'namespaces.json')['items']
        self.pods = {(p['metadata']['namespace'], p['metadata']['name']): p for p in self.pod_list}
        self.namespaces = {n['metadata']['name']: n['metadata']['uid'] for n in self.namespace_list}

    def node(self, name):
        return next(n for n in self.nodes if n['metadata']['name'] == name)

    def scrapes(self, scrape, names=None):
        result = []
        for node in self.nodes:
            name = node['metadata']['name']
            if names is not None and name not in names:
                continue
            result.append(builder.NodeScrape(target=kube.node_target(node, 10250), node=node,
                                             summary=load_json(self.version, summary_name(self.version, name, scrape)),
                                             root_filesystems=root_filesystems(self.version, name)))
        return result

    def batch(self, scrapes_count=2, epoch=1795177260):
        """The metric sets and documents of the last of `scrapes_count` consecutive batches."""
        rates = builder.RateCalculator()
        sets, scrapes = None, None
        for scrape in range(1, scrapes_count + 1):
            scrapes = self.scrapes(scrape)
            sets = builder.build(scrapes, self.pods, self.namespaces)
            rates.process(sets)
            builder.aggregate(sets)
            builder.enrich_nodes(sets, scrapes)
        return sets, builder.documents(sets, epoch, 'default')


@pytest.fixture(params=['1.15', '1.37'])
def cluster(request):
    return Cluster(request.param)


@pytest.fixture
def cluster_1_15():
    return Cluster('1.15')


@pytest.fixture
def cluster_1_37():
    return Cluster('1.37')


def find_docs(docs, family=None, **tags):
    return [doc for doc_family, doc in docs
            if (family is None or doc_family == family)
            and all(doc['MetricsTags'].get(key) == value for key, value in tags.items())]


class FakeResponse:

    def __init__(self, status_code=200, body=None, text=None, lines=None):
        self.status_code = status_code
        self._body = body
        self.text = text if text is not None else (json.dumps(body) if body is not None else '')
        self._lines = lines
        self.encoding = 'utf-8'
        self.closed = False

    def json(self):
        if self._body is None:
            raise ValueError('No JSON')
        return self._body

    def iter_lines(self, chunk_size=None, decode_unicode=False):
        return iter(self._lines if self._lines is not None else self.text.splitlines())

    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.HTTPError('%d error' % self.status_code, response=self)

    def close(self):
        self.closed = True


class FakeSession:
    """Answers each (method, url) with a queued response, and records the requests."""

    def __init__(self, routes=None):
        self.routes = routes or {}
        self.calls = []

    def request(self, method, url, **kwargs):
        self.calls.append((method, url, kwargs))
        answer = self.routes.get((method, url))
        if answer is None:
            raise AssertionError('Unexpected request %s %s' % (method, url))
        if isinstance(answer, list):
            answer = answer.pop(0) if len(answer) > 1 else answer[0]
        if isinstance(answer, Exception):
            raise answer
        return answer(kwargs) if callable(answer) else answer

    def get(self, url, **kwargs):
        return self.request('GET', url, **kwargs)
