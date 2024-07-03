# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import datetime
import json
import logging
import requests

from autoscaler.exception import HeapsterElasticHTTPError
from autoscaler.model import Node


class ElasticSearchClient:

    def __init__(self, protocol, host, port):
        self._elastic_protocol = protocol
        self._elastic_host = host
        self._elastic_port = port
        self._headers = {
            'Content-Type': 'application/json'
        }

    def get_utilization(self, node: Node, from_timestamp: float, to_timestamp: float):
        return self._get_cpu_utilization(node, from_timestamp, to_timestamp), \
               self._get_memory_utilization(node, from_timestamp, to_timestamp)

    def _get_cpu_utilization(self, node, from_timestamp, to_timestamp):
        return self._extract_cpu_utilization(self._request(self._request_cpu_utilization(
            node_name=node.name, from_timestamp=from_timestamp, to_timestamp=to_timestamp)))

    def _request_cpu_utilization(self, node_name: str, from_timestamp: float, to_timestamp: float):
        return self._request_utilization(node_name, 'CpuMetricsTimestamp', from_timestamp, to_timestamp, {
            "avg_cpu_utilization": {
                "avg": {
                    "field": "Metrics.cpu/node_utilization.value"
                }
            },
            "avg_cpu_capacity": {
                "avg": {
                    "field": "Metrics.cpu/node_capacity.value"
                }
            }
        })

    def _extract_cpu_utilization(self, response):
        capacity = response.get('aggregations', {}).get('avg_cpu_capacity', {}).get('value') or 0
        capacity = capacity / 1000
        utilization = response.get('aggregations', {}).get('avg_cpu_utilization', {}).get('value') or 0
        utilization = utilization * capacity
        return int(float(utilization) / float(capacity) * 100) if capacity else None

    def _get_memory_utilization(self, node, from_timestamp, to_timestamp):
        return self._extract_memory_utilization(self._request(self._request_memory_utilization(
            node_name=node.name, from_timestamp=from_timestamp, to_timestamp=to_timestamp)))

    def _request_memory_utilization(self, node_name, from_timestamp, to_timestamp):
        return self._request_utilization(node_name, 'MemoryMetricsTimestamp', from_timestamp, to_timestamp, {
            "avg_memory_utilization": {
                "avg": {
                    "field": "Metrics.memory/working_set.value"
                }
            },
            "avg_memory_capacity": {
                "avg": {
                    "field": "Metrics.memory/node_capacity.value"
                }
            }
        })

    def _extract_memory_utilization(self, response):
        utilization = response.get('aggregations', {}).get('avg_memory_utilization', {}).get('value') or 0
        capacity = response.get('aggregations', {}).get('avg_memory_capacity', {}).get('value') or 0
        return int(float(utilization) / float(capacity) * 100) if capacity else None

    def _request_utilization(self, node_name, metric_timestamp_name, from_timestamp, to_timestamp, aggregations):
        from_timestamp = int(from_timestamp) * 1000
        to_timestamp = int(to_timestamp) * 1000
        return json.dumps({
            "size": 0,
            "query": {
                "bool": {
                    "filter": [
                        {
                            "terms": {
                                "MetricsTags.nodename.raw": [
                                    node_name
                                ],
                                "boost": 1
                            }
                        },
                        {
                            "term": {
                                "MetricsTags.type": {
                                    "value": "node",
                                    "boost": 1
                                }
                            }
                        },
                        {
                            "range": {
                                metric_timestamp_name: {
                                    "from": from_timestamp,
                                    "to": to_timestamp,
                                    "include_lower": True,
                                    "include_upper": True,
                                    "boost": 1
                                }
                            }
                        }
                    ],
                    "disable_coord": False,
                    "adjust_pure_negative": True,
                    "boost": 1
                }
            },
            "aggregations": aggregations
        })

    def _request(self, data):
        indices = 'heapster-' + datetime.datetime.now().strftime('%Y.%m.%d')
        url = f'{self._elastic_protocol}://{self._elastic_host}:{self._elastic_port}/{indices}/_search'
        response = requests.request('GET', url, headers=self._headers, data=data)
        if response.status_code != 200:
            logging.warning('Unexpected response HTTP status code %s.', response.status_code)
            raise HeapsterElasticHTTPError(response.status_code)
        return response.json() or {}
