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

"""Settings, from the environment. The CP_HEAPSTER_* and CP_KUBE_* ones come from cp-config-global."""

import re
from dataclasses import dataclass

SERVICE_ACCOUNT_DIR = '/var/run/secrets/kubernetes.io/serviceaccount'
# The indices the other writers keep in cp-heapster-elk, which the API reads and deletes by prefix:
# heapster's, the monitoring service's GPU stats and the tinyproxy network events.
_OTHER_INDEX_PREFIXES = ('heapster-', 'cp-gpu-monitor-', 'cp-network-events-')
_DURATION = re.compile(r'^(\d+)(ms|s|m|h)?$')
_DURATION_SECONDS = {'ms': 0.001, 's': 1, 'm': 60, 'h': 3600, None: 1}


class ConfigError(ValueError):
    pass


def parse_duration(text):
    """Seconds of `30`, `30s`, `1m` or `1h`, as heapster's --metric_resolution took them."""
    match = _DURATION.match(str(text).strip())
    if not match:
        raise ConfigError('Not a duration: %r' % text)
    return int(match.group(1)) * _DURATION_SECONDS[match.group(2)]


@dataclass(frozen=True)
class Config:
    resolution: float
    scrape_offset: float
    kube_url: str
    kube_ca_file: str
    token_file: str
    kubelet_port: int
    kubelet_ca_file: str
    elastic_url: str
    index: str
    cluster_name: str
    threads: int
    timeout: float
    port: int
    log_level: str


def _first(env, *names, default=None):
    for name in names:
        value = env.get(name)
        if value not in (None, ''):
            return value
    return default


def _positive_int(env, name, default):
    text = _first(env, name, default=str(default))
    try:
        value = int(text)
    except ValueError as e:
        raise ConfigError('%s is not a number: %r' % (name, text)) from e
    if value <= 0:
        raise ConfigError('%s must be positive: %r' % (name, text))
    return value


def elastic_url_from_env(env):
    """cp-heapster-elk's address, from cp-config-global, unless CP_METRICS_COLLECTOR_ELASTIC_URL is set."""
    return _first(env, 'CP_METRICS_COLLECTOR_ELASTIC_URL') or '%s://%s:%s' % (
        _first(env, 'CP_HEAPSTER_ELK_INTERNAL_SCHEME', default='http'),
        _first(env, 'CP_HEAPSTER_ELK_INTERNAL_HOST', default='cp-heapster-elk.default.svc.cluster.local'),
        _first(env, 'CP_HEAPSTER_ELK_INTERNAL_PORT', default='30094'))


def from_env(env):
    resolution = parse_duration(_first(env, 'CP_METRICS_COLLECTOR_RESOLUTION', 'CP_HEAPSTER_METRIC_RESOLUTION',
                                       default='30s'))
    if resolution < 5:
        raise ConfigError('The resolution must be 5 seconds or more: %s' % resolution)
    kube_url = _first(env, 'CP_METRICS_COLLECTOR_KUBE_URL')
    if not kube_url:
        host = _first(env, 'KUBERNETES_SERVICE_HOST')
        port = _first(env, 'KUBERNETES_SERVICE_PORT', default='443')
        if not host:
            raise ConfigError('Set CP_METRICS_COLLECTOR_KUBE_URL, or run in a pod (KUBERNETES_SERVICE_HOST)')
        kube_url = 'https://%s:%s' % ('[%s]' % host if ':' in host else host, port)
    elastic_url = elastic_url_from_env(env)
    index = _first(env, 'CP_METRICS_COLLECTOR_INDEX', default='heapster')
    if not re.match(r'^[a-z0-9][a-z0-9_.-]*$', index):
        raise ConfigError('Not a valid index name: %r' % index)
    if index != 'heapster' and any(index.startswith(prefix) or prefix.startswith(index + '-')
                                   for prefix in _OTHER_INDEX_PREFIXES):
        # The API takes every index starting with `heapster-` for a day of heapster's and parses the
        # rest as the date: `heapster-cmp-YYYY.MM.dd` would stop its retention and fail its charts.
        # A name like `cp` would put this collector's template (`cp-*`) on the GPU stats indices.
        raise ConfigError('The index name %r reaches the indices of another writer (%s): choose another'
                          % (index, ', '.join(p + '*' for p in _OTHER_INDEX_PREFIXES)))
    timeout = float(_first(env, 'CP_METRICS_COLLECTOR_TIMEOUT', default=str(min(10, resolution / 2))))
    if timeout <= 0 or timeout >= resolution:
        raise ConfigError('The timeout must be positive and shorter than the resolution: %s' % timeout)
    return Config(
        resolution=resolution,
        scrape_offset=parse_duration(_first(env, 'CP_METRICS_COLLECTOR_SCRAPE_OFFSET', default='5s')),
        kube_url=kube_url,
        kube_ca_file=_first(env, 'CP_METRICS_COLLECTOR_KUBE_CA_FILE', default=SERVICE_ACCOUNT_DIR + '/ca.crt'),
        token_file=_first(env, 'CP_METRICS_COLLECTOR_TOKEN_FILE', default=SERVICE_ACCOUNT_DIR + '/token'),
        kubelet_port=_positive_int(env, 'CP_METRICS_COLLECTOR_KUBELET_PORT',
                                   _first(env, 'CP_KUBE_KUBELET_PORT', default='10250')),
        kubelet_ca_file=_first(env, 'CP_METRICS_COLLECTOR_KUBELET_CA_FILE', default=''),
        elastic_url=elastic_url,
        index=index,
        cluster_name=_first(env, 'CP_METRICS_COLLECTOR_CLUSTER_NAME', default='default'),
        threads=_positive_int(env, 'CP_METRICS_COLLECTOR_THREADS', 10),
        timeout=timeout,
        port=_positive_int(env, 'CP_METRICS_COLLECTOR_PORT', 8082),
        log_level=_first(env, 'CP_METRICS_COLLECTOR_LOG_LEVEL', default='INFO').upper(),
    )
