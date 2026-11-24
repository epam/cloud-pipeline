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

"""One batch: list the cluster, scrape every Ready node's kubelet, build the sets, write them."""

import logging
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass

import requests

from metrics_collector import builder
from metrics_collector.kube import node_target

logger = logging.getLogger(__name__)

# How long the last node and pod lists stand in for the API server's, when it does not answer.
LISTS_MAX_AGE = 600
_LIST_ERRORS = (requests.RequestException, ValueError, OSError)


@dataclass
class BatchResult:
    nodes: int
    scraped: int
    documents: int
    rejected: int


class Collector:

    def __init__(self, kube, kubelets, writer, kubelet_port, cluster_name, threads, clock=time.monotonic):
        self._kube = kube
        self._kubelets = kubelets
        self._writer = writer
        self._kubelet_port = kubelet_port
        self._cluster_name = cluster_name
        self._executor = ThreadPoolExecutor(max_workers=threads, thread_name_prefix='scrape')
        self._rates = builder.RateCalculator()
        self._clock = clock
        self._last_lists = {}
        self._api_failed = None

    def collect(self, epoch_seconds):
        """Collects and writes the batch of `epoch_seconds`, the end of its window."""
        self._api_failed = None
        nodes = self._list('nodes', self._kube.nodes)
        pods = {(pod['metadata'].get('namespace'), pod['metadata'].get('name')): pod
                for pod in self._list('pods', self._kube.pods)}
        namespaces = {namespace['metadata'].get('name'): namespace['metadata'].get('uid')
                      for namespace in self._list('namespaces', self._kube.namespaces, required=False)}
        targets = [(node, target) for node in nodes
                   for target in [node_target(node, self._kubelet_port)] if target is not None]
        scrapes = [scrape for scrape in self._executor.map(lambda pair: self._scrape(*pair), targets)
                   if scrape is not None]
        sets = builder.build(scrapes, pods, namespaces)
        self._rates.process(sets)
        builder.aggregate(sets)
        builder.enrich_nodes(sets, scrapes)
        docs = builder.documents(sets, epoch_seconds, self._cluster_name)
        rejected = self._writer.write(epoch_seconds, docs)
        return BatchResult(nodes=len(targets), scraped=len(scrapes), documents=len(docs), rejected=rejected)

    def _list(self, name, fetch, required=True):
        """The API server's list, or the last one for a while when it does not answer.

        heapster read the API server through its informers' caches and wrote on through a short
        outage, such as an API server restart. A list older than LISTS_MAX_AGE fails the batch,
        but the namespaces only lose the `namespace_id` tag.
        """
        try:
            if self._api_failed is not None:
                # One list failed in this batch already: waiting for another timeout would only
                # make the batch run past its window.
                raise self._api_failed
            items = fetch()
        except _LIST_ERRORS as e:
            self._api_failed = e
            last = self._last_lists.get(name)
            if last is not None and (not required or self._clock() - last[0] <= LISTS_MAX_AGE):
                logger.warning('Cannot list the %s (%s): using the list of %d seconds ago',
                               name, e, self._clock() - last[0])
                return last[1]
            if not required:
                logger.warning('Cannot list the %s (%s): going on without them', name, e)
                return []
            raise
        self._last_lists[name] = (self._clock(), items)
        return items

    def _scrape(self, node, target):
        try:
            summary = self._kubelets.summary(target)
        except (requests.RequestException, ValueError) as e:
            logger.warning('Cannot read the stats summary of %s (%s): %s', target.name, target.url, e)
            return None
        scrape = builder.NodeScrape(target=target, node=node, summary=summary)
        try:
            scrape.root_filesystems = self._kubelets.root_filesystems(target)
        except requests.HTTPError as e:
            if e.response is not None and e.response.status_code == 404:
                scrape.root_filesystems = None
            else:
                logger.warning('Cannot read the cAdvisor metrics of %s: %s', target.name, e)
                scrape.filesystems_failed = True
        except requests.RequestException as e:
            logger.warning('Cannot read the cAdvisor metrics of %s: %s', target.name, e)
            scrape.filesystems_failed = True
        return scrape

    def close(self):
        self._executor.shutdown(wait=False, cancel_futures=True)
