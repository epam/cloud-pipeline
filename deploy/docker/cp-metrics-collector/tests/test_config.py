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

from metrics_collector import config

POD = {'KUBERNETES_SERVICE_HOST': '10.96.0.1', 'KUBERNETES_SERVICE_PORT': '443'}


def test_defaults_in_a_pod():
    c = config.from_env(POD)
    assert c.resolution == 30 and c.scrape_offset == 5 and c.timeout == 10
    assert c.kube_url == 'https://10.96.0.1:443'
    assert c.token_file == '/var/run/secrets/kubernetes.io/serviceaccount/token'
    assert c.kube_ca_file == '/var/run/secrets/kubernetes.io/serviceaccount/ca.crt'
    assert c.elastic_url == 'http://cp-heapster-elk.default.svc.cluster.local:30094'
    assert (c.index, c.cluster_name, c.kubelet_port, c.kubelet_ca_file) == ('heapster', 'default', 10250, '')
    assert (c.threads, c.port, c.log_level) == (10, 8082, 'INFO')


def test_platform_settings():
    c = config.from_env(dict(POD, CP_HEAPSTER_METRIC_RESOLUTION='1m', CP_HEAPSTER_ELK_INTERNAL_HOST='elk',
                             CP_HEAPSTER_ELK_INTERNAL_PORT='9200', CP_HEAPSTER_ELK_INTERNAL_SCHEME='https',
                             CP_KUBE_KUBELET_PORT='10255'))
    assert c.resolution == 60
    assert c.elastic_url == 'https://elk:9200'
    assert c.kubelet_port == 10255


def test_own_settings_win():
    c = config.from_env(dict(POD, CP_HEAPSTER_METRIC_RESOLUTION='1m', CP_METRICS_COLLECTOR_RESOLUTION='15s',
                             CP_METRICS_COLLECTOR_ELASTIC_URL='http://other:9200',
                             CP_METRICS_COLLECTOR_INDEX='cp-metrics',
                             CP_METRICS_COLLECTOR_KUBE_URL='https://api:6443', CP_METRICS_COLLECTOR_KUBELET_PORT='1',
                             CP_KUBE_KUBELET_PORT='10255', CP_METRICS_COLLECTOR_TIMEOUT='3',
                             CP_METRICS_COLLECTOR_LOG_LEVEL='debug', CP_METRICS_COLLECTOR_THREADS='32'))
    assert (c.resolution, c.timeout, c.threads, c.log_level) == (15, 3, 32, 'DEBUG')
    assert (c.elastic_url, c.index, c.kube_url, c.kubelet_port) == \
        ('http://other:9200', 'cp-metrics', 'https://api:6443', 1)


@pytest.mark.parametrize('index', ['heapster', 'heapsterx', 'cp-metrics-collector', 'kubelet-heapster'])
def test_index_names_the_api_leaves_alone(index):
    assert config.from_env(dict(POD, CP_METRICS_COLLECTOR_INDEX=index)).index == index


def test_ipv6_service_host():
    assert config.from_env({'KUBERNETES_SERVICE_HOST': 'fd00::1'}).kube_url == 'https://[fd00::1]:443'


@pytest.mark.parametrize('env', [
    {},
    dict(POD, CP_METRICS_COLLECTOR_RESOLUTION='2s'),
    dict(POD, CP_METRICS_COLLECTOR_RESOLUTION='30 seconds'),
    dict(POD, CP_METRICS_COLLECTOR_INDEX='Heapster'),
    dict(POD, CP_METRICS_COLLECTOR_INDEX='heapster-cmp'),
    dict(POD, CP_METRICS_COLLECTOR_INDEX='heapster-'),
    dict(POD, CP_METRICS_COLLECTOR_TIMEOUT='30'),
    dict(POD, CP_METRICS_COLLECTOR_THREADS='0'),
    dict(POD, CP_METRICS_COLLECTOR_PORT='x'),
])
def test_bad_settings(env):
    with pytest.raises(config.ConfigError):
        config.from_env(env)


@pytest.mark.parametrize('text, seconds', [('30', 30), ('30s', 30), ('2m', 120), ('1h', 3600), ('500ms', 0.5)])
def test_durations(text, seconds):
    assert config.parse_duration(text) == seconds
