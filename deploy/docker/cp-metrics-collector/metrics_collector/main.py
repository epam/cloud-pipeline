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

"""The collector's loop and its health endpoint.

As heapster's manager: every `resolution` seconds, at the end of the window plus `scrape_offset`,
one batch stamped with the window's end. A batch that runs past the next window's start makes the
loop skip to the window after, rather than queue batches up.
"""

import logging
import math
import os
import signal
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import requests
import urllib3

from metrics_collector import config as config_module
from metrics_collector.collector import Collector
from metrics_collector.elastic import ElasticError, ElasticWriter
from metrics_collector.kube import KubeClient, KubeletClient, TokenSource, new_session

logger = logging.getLogger('metrics_collector')

# heapster's healthz reported unhealthy once its last batch was older than 3 minutes.
_MAX_DELAY = 180


class Health:
    """Whether the loop still runs: a batch finished, written or not, within the last few minutes.

    A batch that Elasticsearch or the API server failed does not count against it: restarting the
    collector does not bring them back, and it would lose the counters the next rates need.
    """

    def __init__(self, resolution, clock=time.time):
        self._clock = clock
        self._max_delay = max(_MAX_DELAY, 3 * resolution)
        self._started = clock()
        self._last_batch = None
        self._last_written = None
        self._lock = threading.Lock()

    def batch_done(self, written):
        with self._lock:
            self._last_batch = self._clock()
            if written:
                self._last_written = self._last_batch

    def status(self):
        """(healthy, message): healthy while the last batch is recent, or since the start."""
        with self._lock:
            last = self._last_batch if self._last_batch is not None else self._started
            written = self._last_written
        now = self._clock()
        detail = 'last written %d seconds ago' % (now - written) if written is not None else 'nothing written yet'
        if now - last <= self._max_delay:
            return True, 'ok, ' + detail
        return False, 'no batch for %d seconds, %s' % (now - last, detail)


def _handler(health):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path.split('?', 1)[0] != '/healthz':
                self.send_error(404)
                return
            healthy, message = health.status()
            body = (message + '\n').encode()
            self.send_response(200 if healthy else 503)
            self.send_header('Content-Type', 'text/plain')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, format, *args):
            logger.debug('healthz: ' + format, *args)

    return Handler


def next_batch(now, resolution, offset):
    """(the batch's time, when to collect it): the current window's end, and that plus the offset."""
    end = (math.floor(now / resolution) + 1) * resolution
    return end, end + offset


def run(config, collector, health, stop, clock=time.time):
    while not stop.is_set():
        batch_time, collect_at = next_batch(clock(), config.resolution, config.scrape_offset)
        if stop.wait(max(0.0, collect_at - clock())):
            break
        started = clock()
        try:
            result = collector.collect(batch_time)
        except (ElasticError, requests.RequestException, OSError, ValueError) as e:
            # Elasticsearch or the API server is down, or the token is missing: one line, not a
            # stack trace every batch.
            logger.error('The batch of %d failed: %s', batch_time, e)
            health.batch_done(written=False)
            continue
        except Exception:
            logger.exception('The batch of %d failed', batch_time)
            health.batch_done(written=False)
            continue
        health.batch_done(written=True)
        logger.info('Batch %d: %d of %d nodes scraped, %d documents written, %d rejected, in %.1f s',
                    batch_time, result.scraped, result.nodes, result.documents - result.rejected,
                    result.rejected, clock() - started)


def main(env=None):
    env = os.environ if env is None else env
    try:
        config = config_module.from_env(env)
    except config_module.ConfigError as e:
        print('Configuration error: %s' % e, file=sys.stderr)
        return 2
    logging.basicConfig(level=config.log_level, stream=sys.stdout,
                        format='%(asctime)s %(levelname)s %(name)s: %(message)s')

    tokens = TokenSource(config.token_file)
    kube_session = new_session(4)
    kubelet_session = new_session(config.threads)
    kubelet_verify = config.kubelet_ca_file or False
    if not kubelet_verify:
        # The kubelets of a kubeadm cluster serve self-signed certificates, as heapster's
        # `insecure=true` assumed. The service account token is still checked by each kubelet.
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        logger.info('Kubelet certificates are not verified: set CP_METRICS_COLLECTOR_KUBELET_CA_FILE to verify them')
    collector = Collector(
        kube=KubeClient(config.kube_url, kube_session, tokens, config.kube_ca_file, config.timeout),
        kubelets=KubeletClient(kubelet_session, tokens, kubelet_verify, config.timeout),
        writer=ElasticWriter(config.elastic_url, config.index, new_session(2), config.timeout),
        kubelet_port=config.kubelet_port,
        cluster_name=config.cluster_name,
        threads=config.threads)
    health = Health(config.resolution)
    server = ThreadingHTTPServer(('', config.port), _handler(health))
    threading.Thread(target=server.serve_forever, name='healthz', daemon=True).start()

    stop = threading.Event()
    for signum in (signal.SIGTERM, signal.SIGINT):
        signal.signal(signum, lambda *_: stop.set())
    logger.info('Collecting every %s s from %s into %s, index %s-YYYY.MM.dd',
                config.resolution, config.kube_url, config.elastic_url, config.index)
    try:
        run(config, collector, health, stop)
    finally:
        server.shutdown()
        collector.close()
    logger.info('Stopped')
    return 0
