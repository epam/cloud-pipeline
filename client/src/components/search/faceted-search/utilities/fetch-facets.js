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

const PAGE_SIZE = 20;

function fetchFacetsGroup (facetNames, filters, query) {
  return new Promise((resolve) => {
    const payload = {
      query,
      facets: facetNames,
      filters: {...filters},
      offset: 0,
      pageSize: PAGE_SIZE,
      highlight: false
    };
    const request = new FacetedSearch();
    request.send(payload)
      .then(() => {
        if (request.loaded) {
          const {facets = {}} = request.value || {};
          resolve(facets);
        } else {
          resolve({});
        }
      })
      .catch(() => {
        resolve({});
      });
  });
}

function removeSingleSelection (selection, name) {
  const result = {...(selection || {})};
  if (result.hasOwnProperty(name)) {
    delete result[name];
  }
  return result;
}

function fetchFacets (facets, selection, query = '*') {
  const selectedFacets = Object.keys(selection);
  const notSelectedFacets = facets.filter(f => selectedFacets.indexOf(f) === -1);
  return new Promise((resolve) => {
    Promise.all([
      fetchFacetsGroup(notSelectedFacets, selection, query),
      ...selectedFacets
        .map(f => fetchFacetsGroup([f], removeSingleSelection(selection, f), query))
    ])
      .then((payloads) => {
        let result = {};
        for (let p = 0; p < payloads.length; p++) {
          result = {...result, ...(payloads[p])};
        }
        resolve(result);
      });
  });
}

export default fetchFacets;
