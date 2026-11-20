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

import pytest
import requests

from metrics_collector import kube

from conftest import FakeResponse, FakeSession, NODE_1_15, WORKER_1_37, fixture_path, root_filesystems


class Tokens:
    def headers(self):
        return {'Authorization': 'Bearer t'}


def _node(conditions, addresses, port=None):
    status = {'conditions': conditions, 'addresses': addresses}
    if port is not None:
        status['daemonEndpoints'] = {'kubeletEndpoint': {'Port': port}}
    return {'metadata': {'name': 'n1'}, 'status': status}


READY = [{'type': 'MemoryPressure', 'status': 'False'}, {'type': 'Ready', 'status': 'True'},
         {'type': 'NetworkUnavailable', 'status': 'False'}]


def test_token_is_read_again_after_max_age(tmp_path):
    token_file = tmp_path / 'token'
    token_file.write_text('one\n')
    now = [0]
    tokens = kube.TokenSource(str(token_file), max_age=60, clock=lambda: now[0])
    assert tokens.headers() == {'Authorization': 'Bearer one'}
    token_file.write_text('two')
    now[0] = 59
    assert tokens.token() == 'one'
    now[0] = 60
    assert tokens.token() == 'two'


def test_ready_is_read_by_type_not_by_position():
    assert kube.is_ready(_node(READY, []))
    assert not kube.is_ready(_node([{'type': 'Ready', 'status': 'Unknown'}], []))
    assert not kube.is_ready(_node([], []))


@pytest.mark.parametrize('addresses, expected', [
    ([{'type': 'Hostname', 'address': 'h'}, {'type': 'InternalIP', 'address': '10.0.0.1'},
      {'type': 'ExternalIP', 'address': '1.2.3.4'}], '10.0.0.1'),
    ([{'type': 'ExternalIP', 'address': '1.2.3.4'}, {'type': 'LegacyHostIP', 'address': '10.0.0.2'}], '10.0.0.2'),
    ([{'type': 'ExternalIP', 'address': '1.2.3.4'}], '1.2.3.4'),
])
def test_address_preference(addresses, expected):
    assert kube.node_target(_node(READY, addresses), 10250).address == expected


def test_target_port_hostname_and_url():
    target = kube.node_target(_node(READY, [{'type': 'InternalIP', 'address': '10.0.0.1'},
                                            {'type': 'Hostname', 'address': 'host-1'}], port=10255), 10250)
    assert (target.name, target.hostname, target.port) == ('n1', 'host-1', 10255)
    assert target.url == 'https://10.0.0.1:10255'
    target = kube.node_target(_node(READY, [{'type': 'InternalIP', 'address': 'fd00::1'}]), 10250)
    assert (target.hostname, target.port, target.url) == ('n1', 10250, 'https://[fd00::1]:10250')


def test_no_target_without_an_address():
    assert kube.node_target(_node(READY, [{'type': 'Hostname', 'address': 'h'}]), 10250) is None


def test_recorded_nodes_are_targets(cluster):
    for node in cluster.nodes:
        target = kube.node_target(node, 1)
        assert target is not None and target.port == 10250


def test_list_asks_the_cache_and_returns_items():
    session = FakeSession({('GET', 'https://api:6443/api/v1/pods'): lambda kw: FakeResponse(
        body={'items': [{'metadata': {'name': 'p'}}]} if kw['params'] == {'resourceVersion': '0'} else None)})
    client = kube.KubeClient('https://api:6443/', session, Tokens(), '/ca.crt', 5)
    assert client.pods() == [{'metadata': {'name': 'p'}}]
    _, _, kwargs = session.calls[0]
    assert kwargs['headers'] == {'Authorization': 'Bearer t'}
    assert kwargs['verify'] == '/ca.crt' and kwargs['timeout'] == 5


def test_list_raises_on_errors():
    session = FakeSession({('GET', 'https://api/api/v1/nodes'): FakeResponse(403, text='forbidden')})
    with pytest.raises(requests.HTTPError):
        kube.KubeClient('https://api', session, Tokens(), True, 5).nodes()


TARGET = kube.NodeTarget(name='n', hostname='n', address='10.0.0.1', port=10250)


@pytest.mark.parametrize('version, node, name', [
    ('1.15', NODE_1_15, 'cadvisor.txt'), ('1.37', WORKER_1_37, 'cadvisor-cp-mc-137-worker.txt')])
def test_root_filesystems_from_recorded_cadvisor(version, node, name):
    with open(fixture_path(version, name)) as f:
        lines = f.read().splitlines()
    response = FakeResponse(lines=lines)
    session = FakeSession({('GET', 'https://10.0.0.1:10250/metrics/cadvisor'): response})
    devices = kube.KubeletClient(session, Tokens(), False, 5).root_filesystems(TARGET)
    assert devices == root_filesystems(version, node)
    assert all(set(values) == {'filesystem/usage', 'filesystem/limit', 'filesystem/inodes', 'filesystem/inodes_free'}
               for values in devices.values())
    assert response.closed
    assert session.calls[0][2]['stream'] is True and session.calls[0][2]['verify'] is False


def test_root_filesystems_skip_other_cgroups_bad_lines_and_nan():
    lines = [
        '# TYPE container_fs_usage_bytes gauge',
        'container_fs_usage_bytes{device="/dev/sda1",id="/kubepods/pod1"} 5',
        'container_fs_usage_bytes{device="/dev/sda1",id="/"} 7',
        'container_fs_limit_bytes{device="/dev/sda1",id="/"} NaN',
        'container_fs_usage_bytes{device="/dev/sdb",id="/" 3',
        'container_fs_io_current{device="/dev/sda1",id="/"} 1',
        'container_cpu_usage_seconds_total{id="/"} 9',
    ]
    session = FakeSession({('GET', 'https://10.0.0.1:10250/metrics/cadvisor'): FakeResponse(lines=lines)})
    assert kube.KubeletClient(session, Tokens(), False, 5).root_filesystems(TARGET) == \
        {'/dev/sda1': {'filesystem/usage': 7}}


def test_a_failed_streamed_response_is_closed():
    response = FakeResponse(500, text='error')
    session = FakeSession({('GET', 'https://10.0.0.1:10250/metrics/cadvisor'): response})
    with pytest.raises(requests.HTTPError):
        kube.KubeletClient(session, Tokens(), False, 5).root_filesystems(TARGET)
    assert response.closed


def test_root_filesystems_none_without_root_series():
    session = FakeSession({('GET', 'https://10.0.0.1:10250/metrics/cadvisor'): FakeResponse(lines=['# nothing'])})
    assert kube.KubeletClient(session, Tokens(), False, 5).root_filesystems(TARGET) is None


def test_summary():
    session = FakeSession({('GET', 'https://10.0.0.1:10250/stats/summary'): FakeResponse(body={'node': {}})})
    assert kube.KubeletClient(session, Tokens(), '/kubelet-ca', 5).summary(TARGET) == {'node': {}}
    assert session.calls[0][2]['verify'] == '/kubelet-ca'


def test_new_session_ignores_proxies():
    assert kube.new_session(4).trust_env is False
