/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import {FacetedSearch} from '../../../../models/search';

function doSearch (query, filters, offset, pageSize, facets) {
  const payload = {
    query: query || '*',
    filters: {...filters},
    facets,
    offset,
    pageSize,
    highlight: false
  };
  return new Promise((resolve) => {
    const request = new FacetedSearch();
    request.send(payload)
      .then(() => {
        if (request.error || !request.loaded) {
          resolve({error: request.error || 'Error performing faceted search'});
        } else {
          const {
            documents = [],
            facets = {},
            totalHits = 0
          } = request.value || {};
          resolve({
            documents,
            documentsOffset: offset,
            facets,
            totalHits
          });
        }
      })
      .catch(e => {
        resolve({error: e.message});
      });
  });
}

export default doSearch;
