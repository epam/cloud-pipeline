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

"""Writes the documents to Elasticsearch 5 as heapster's sink did (common/elasticsearch/elasticsearch.go).

The index is `<index>-YYYY.MM.dd` of the batch time in UTC, created with heapster's mapping when it
does not exist, and carries the aliases `<index>-<family>`. The family is the document type.
"""

import json
import logging
from datetime import datetime, timezone

from metrics_collector.mapping import FAMILIES, index_mapping

logger = logging.getLogger(__name__)

_ALREADY_EXISTS = ('index_already_exists_exception', 'resource_already_exists_exception')


class ElasticError(Exception):
    pass


def index_name(index, epoch_seconds):
    return '%s-%s' % (index, datetime.fromtimestamp(epoch_seconds, tz=timezone.utc).strftime('%Y.%m.%d'))


class ElasticWriter:

    def __init__(self, url, index, session, timeout, bulk_size=2000):
        self._url = url.rstrip('/')
        self._index = index
        self._session = session
        self._timeout = timeout
        self._bulk_size = bulk_size
        self._ready = set()

    def write(self, epoch_seconds, docs):
        """Writes [(family, document)]; returns how many Elasticsearch rejected."""
        if not docs:
            return 0
        name = index_name(self._index, epoch_seconds)
        self._ensure_index(name)
        rejected = 0
        for start in range(0, len(docs), self._bulk_size):
            rejected += self._bulk(name, docs[start:start + self._bulk_size])
        return rejected

    def _ensure_index(self, name):
        if name in self._ready:
            return
        response = self._request('HEAD', '/' + name)
        if response.status_code == 404:
            response = self._request('PUT', '/' + name, data=json.dumps(index_mapping()))
            if response.status_code >= 300 and not any(text in response.text for text in _ALREADY_EXISTS):
                raise ElasticError('Cannot create the index %s: %s %s' % (name, response.status_code,
                                                                          response.text[:500]))
            logger.info('Created the index %s', name)
        elif response.status_code >= 300:
            raise ElasticError('Cannot check the index %s: %s' % (name, response.status_code))
        self._ensure_aliases(name)
        # Forget the earlier days' indices: the names differ only in the date, so they sort by it.
        self._ready = {ready for ready in self._ready if ready >= name}
        self._ready.add(name)

    def _ensure_aliases(self, name):
        response = self._request('GET', '/%s/_alias' % name)
        existing = set()
        if response.status_code == 200:
            existing = set(((response.json().get(name) or {}).get('aliases') or {}).keys())
        actions = [{'add': {'index': name, 'alias': '%s-%s' % (self._index, family)}}
                   for family in FAMILIES if '%s-%s' % (self._index, family) not in existing]
        if not actions:
            return
        response = self._request('POST', '/_aliases', data=json.dumps({'actions': actions}))
        if response.status_code >= 300:
            raise ElasticError('Cannot add the aliases of %s: %s %s' % (name, response.status_code,
                                                                        response.text[:500]))

    def _bulk(self, name, docs):
        lines = []
        for family, doc in docs:
            lines.append(json.dumps({'index': {'_index': name, '_type': family}}))
            lines.append(json.dumps(doc, separators=(',', ':')))
        response = self._request('POST', '/_bulk', data='\n'.join(lines) + '\n',
                                 content_type='application/x-ndjson')
        if response.status_code >= 300:
            raise ElasticError('Bulk request to %s failed: %s %s' % (name, response.status_code,
                                                                     response.text[:500]))
        body = response.json()
        if not body.get('errors'):
            return 0
        failed = [item.get('index') or {} for item in body.get('items') or []
                  if (item.get('index') or {}).get('status', 200) >= 300]
        if failed:
            logger.warning('Elasticsearch rejected %d of %d documents in %s, the first: %s',
                           len(failed), len(docs), name, json.dumps(failed[0].get('error'))[:500])
        return len(failed)

    def _request(self, method, path, data=None, content_type='application/json'):
        headers = {'Content-Type': content_type} if data is not None else {}
        return self._session.request(method, self._url + path, data=data, headers=headers,
                                     timeout=self._timeout)
