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
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass

import requests

from metrics_collector import builder
from metrics_collector.kube import node_target

logger = logging.getLogger(__name__)


@dataclass
class BatchResult:
    nodes: int
    scraped: int
    documents: int
    rejected: int


class Collector:

    def __init__(self, kube, kubelets, writer, kubelet_port, cluster_name, threads):
        self._kube = kube
        self._kubelets = kubelets
        self._writer = writer
        self._kubelet_port = kubelet_port
        self._cluster_name = cluster_name
        self._executor = ThreadPoolExecutor(max_workers=threads, thread_name_prefix='scrape')
        self._rates = builder.RateCalculator()

    def collect(self, epoch_seconds):
        """Collects and writes the batch of `epoch_seconds`, the end of its window."""
        nodes = self._kube.nodes()
        pods = {(pod['metadata'].get('namespace'), pod['metadata'].get('name')): pod for pod in self._kube.pods()}
        namespaces = {namespace['metadata'].get('name'): namespace['metadata'].get('uid')
                      for namespace in self._kube.namespaces()}
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
