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

import doSearch from './do-search';
import fetchFacets from './fetch-facets';
import getItemUrl from './get-item-url';
import getFacetFilterToken from './facet-filter-token';

export default function facetsSearch (fetchFacetsOptions) {
  const {
    query,
    filters,
    sortingOrder,
    pageSize,
    options,
    scrollingParameters,
    abortSignal,
    skipFacets = false
  } = fetchFacetsOptions;
  const {
    facets = [],
    facetsToken: currentFacetsToken,
    facetsCount,
    stores,
    metadataFields = []
  } = options;
  const facetsToken = getFacetFilterToken({
    query,
    filters,
    sortingOrder,
    pageSize: 1
  });
  const doFetchFacets = currentFacetsToken !== facetsToken && !skipFacets;
  return new Promise((resolve) => {
    const promises = [
      doSearch({
        query,
        filters,
        sortingOrder,
        metadataFields,
        pageSize,
        facets,
        scrollingParameters,
        abortSignal
      })
    ];
    if (doFetchFacets && Object.keys(filters || {}).length > 0) {
      // if {filters} is empty (Object.keys.length === 0)
      // we can get results (facets count) from doSearch() call
      promises.push(
        fetchFacets(facets, filters, query, sortingOrder, abortSignal)
      );
    }
    Promise
      .all(promises)
      .then(result => {
        const [
          searchResult,
          facetsCountResult
        ] = result;
        const {
          error,
          documents = [],
          facets: facetsCountFromSearch
        } = searchResult;
        if (result.some(r => r.aborted)) {
          resolve({aborted: true});
        }
        let facetsCountUpdated, facetsTokenUpdated;
        if (facetsCountResult) {
          facetsCountUpdated = facetsCountResult.facetsCount || facetsCount;
          facetsTokenUpdated = facetsCountResult.facetsToken || facetsToken;
        } else if (doFetchFacets) {
          facetsCountUpdated = facetsCountFromSearch || facetsCount;
          facetsTokenUpdated = facetsToken;
        } else {
          facetsCountUpdated = facetsCount;
          facetsTokenUpdated = facetsToken;
        }
        Promise.all(
          documents.map(doc => getItemUrl(doc, stores))
        )
          .then(urls => {
            urls.forEach((url, index) => {
              documents[index].url = url;
            });
            resolve({
              error,
              facetsCount: facetsCountUpdated,
              facetsToken: facetsTokenUpdated,
              documents: documents.slice()
            });
          });
      });
  });
}
