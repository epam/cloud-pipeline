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

"""The Kubernetes API server and the kubelets, over plain HTTPS.

Only core v1 lists and the kubelet's own endpoints, which Kubernetes 1.15 and 1.37 serve alike, so
no client library can tie the collector to one Kubernetes version.
"""

import logging
import math
import time
from dataclasses import dataclass

import requests
from requests.adapters import HTTPAdapter

from metrics_collector import prometheus

logger = logging.getLogger(__name__)

# The series of the root cgroup (`id="/"`) of /metrics/cadvisor that heapster read from the legacy
# cAdvisor endpoint as the node's filesystems, one per device.
ROOT_FS_SERIES = {
    'container_fs_usage_bytes': 'filesystem/usage',
    'container_fs_limit_bytes': 'filesystem/limit',
    'container_fs_inodes_total': 'filesystem/inodes',
    'container_fs_inodes_free': 'filesystem/inodes_free',
}
_ROOT_FS_PREFIX = 'container_fs_'


def new_session(pool_size):
    """A session that ignores the proxy settings of the environment: all its traffic is in-cluster."""
    session = requests.Session()
    session.trust_env = False
    adapter = HTTPAdapter(pool_connections=max(pool_size, 10), pool_maxsize=max(pool_size, 10))
    session.mount('https://', adapter)
    session.mount('http://', adapter)
    return session


class TokenSource:
    """The service account token, read again at most every `max_age` seconds.

    A bound service account token (Kubernetes 1.21 and later) is replaced in the file before it
    expires, so it is never read only once.
    """

    def __init__(self, path, max_age=60, clock=time.monotonic):
        self._path = path
        self._max_age = max_age
        self._clock = clock
        self._token = None
        self._read_at = None

    def token(self):
        now = self._clock()
        if self._token is None or now - self._read_at >= self._max_age:
            with open(self._path) as token_file:
                self._token = token_file.read().strip()
            self._read_at = now
        return self._token

    def headers(self):
        return {'Authorization': 'Bearer ' + self.token()}


class KubeClient:

    def __init__(self, url, session, tokens, verify, timeout):
        self._url = url.rstrip('/')
        self._session = session
        self._tokens = tokens
        self._verify = verify
        self._timeout = timeout

    def list(self, path):
        """The items of a list; `resourceVersion=0` lets the API server answer from its cache."""
        response = self._session.get(self._url + path, params={'resourceVersion': '0'},
                                     headers=self._tokens.headers(), verify=self._verify,
                                     timeout=self._timeout)
        response.raise_for_status()
        return response.json().get('items') or []

    def nodes(self):
        return self.list('/api/v1/nodes')

    def pods(self):
        return self.list('/api/v1/pods')

    def namespaces(self):
        return self.list('/api/v1/namespaces')


@dataclass(frozen=True)
class NodeTarget:
    name: str
    hostname: str
    address: str
    port: int

    @property
    def url(self):
        host = '[%s]' % self.address if ':' in self.address else self.address
        return 'https://%s:%d' % (host, self.port)


def is_ready(node):
    """By condition type, never by position: the order of the conditions differs between versions."""
    for condition in (node.get('status') or {}).get('conditions') or []:
        if condition.get('type') == 'Ready':
            return condition.get('status') == 'True'
    return False


def node_target(node, default_port):
    """Where to reach a node's kubelet; None for a node that is not Ready, which heapster skipped too.

    The address is the first InternalIP, else the LegacyHostIP, else the ExternalIP, in heapster's
    order of preference (heapster kept the last InternalIP; the first is the node's primary one).
    The port is the one the kubelet reports in `status.daemonEndpoints`, else `default_port`;
    heapster always took its configured port.
    """
    if not is_ready(node):
        return None
    name = node['metadata']['name']
    status = node.get('status') or {}
    addresses = {}
    for address in status.get('addresses') or []:
        if address.get('address'):
            addresses.setdefault(address.get('type'), address['address'])
    ip = addresses.get('InternalIP') or addresses.get('LegacyHostIP') or addresses.get('ExternalIP')
    if not ip:
        return None
    port = (((status.get('daemonEndpoints') or {}).get('kubeletEndpoint') or {}).get('Port')) or default_port
    return NodeTarget(name=name, hostname=addresses.get('Hostname') or name, address=ip, port=int(port))


class KubeletClient:

    def __init__(self, session, tokens, verify, timeout):
        self._session = session
        self._tokens = tokens
        self._verify = verify
        self._timeout = timeout

    def summary(self, target):
        response = self._get(target, '/stats/summary')
        return response.json()

    def root_filesystems(self, target):
        """{device: {metric name: value}} of the root cgroup's filesystems, from /metrics/cadvisor.

        None where the endpoint has no such series. The response is streamed and only the
        `container_fs_` lines are parsed: on a busy node it runs to megabytes, almost all of it
        per-container series.
        """
        response = self._get(target, '/metrics/cadvisor', stream=True)
        devices = {}
        try:
            for line in response.iter_lines(chunk_size=65536, decode_unicode=True):
                if not line or not line.startswith(_ROOT_FS_PREFIX):
                    continue
                name = line.split('{', 1)[0].split(' ', 1)[0]
                if name not in ROOT_FS_SERIES:
                    continue
                try:
                    _, labels, value = prometheus.parse_line(line)
                except prometheus.ParseError as e:
                    logger.debug('Skipping a line from %s: %s', target.name, e)
                    continue
                device = labels.get('device')
                if labels.get('id') != '/' or not device or not math.isfinite(value):
                    continue
                devices.setdefault(device, {})[ROOT_FS_SERIES[name]] = int(round(value))
        finally:
            response.close()
        return devices or None

    def _get(self, target, path, stream=False):
        response = self._session.get(target.url + path, headers=self._tokens.headers(),
                                     verify=self._verify, timeout=self._timeout, stream=stream)
        if response.status_code >= 400:
            # A streamed response holds its connection until it is read or closed.
            response.close()
        response.raise_for_status()
        if stream and response.encoding is None:
            response.encoding = 'utf-8'
        return response
