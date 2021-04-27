/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import RemotePost from '../basic/RemotePost';
import SearchItemTypes from './search-item-types';
import mapElasticDocument from './map-elastic-document';
export {default as FacetedSearch} from './facet';
export {mapElasticDocument};
export {SearchItemTypes};

export class Search extends RemotePost {
  query;
  scrollingParameters;
  pageSize;

  constructor () {
    super();
    this.url = '/search';
  }

  async send (query, scrollingParameters, pageSize, types = []) {
    if (query) {
      this.query = query;
      this.scrollingParameters = scrollingParameters;
      this.pageSize = pageSize;
      return super.send({
        query,
        offset: scrollingParameters ? undefined : 0,
        scrollingParameters,
        pageSize,
        highlight: true,
        aggregate: true,
        filterTypes: types.length === 0 ? undefined : types
      });
    }
  }

  postprocess (value) {
    value.payload.documents = (value.payload.documents || []).map(mapElasticDocument);
    return value.payload;
  }
}
