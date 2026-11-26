/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import Remote from '../basic/Remote';

// One instance per region, held for the page session: the capacity-block catalogue a
// region sells changes on AWS's own timescale, not a user's, so refetching it on every
// dialog reopen or region revisit buys nothing. Keyed by `regionId`, not exported - a
// failed fetch is evicted in `forRegion` below so the next call retries it instead of
// replaying the same error for the rest of the session.
const cache = new Map();

class CapacityBlockInstanceTypes extends Remote {
  // The endpoint returns a list; `Remote`'s own default is `{}`, which a consumer
  // reading the value before the first fetch resolves would have to special-case.
  static defaultValue = [];

  constructor (regionId) {
    super();
    this.url = `/cluster/capacity-reservation/instance-types?regionId=${regionId}`;
  }

  /**
   * The cached instance for `regionId`, fetching it the first time this page session
   * asks and reusing the resolved one after. A request already in flight is shared
   * the same way `fetchIfNeededOrWait` shares one on a single instance; a failed one
   * is dropped from the cache so the very next call starts a fresh fetch rather than
   * being stuck with the error for the rest of the session.
   * @param {number|string} regionId
   * @returns {Promise<CapacityBlockInstanceTypes>}
   */
  static forRegion (regionId) {
    if (!cache.has(regionId)) {
      cache.set(regionId, new CapacityBlockInstanceTypes(regionId));
    }
    const request = cache.get(regionId);
    return request.fetchIfNeededOrWait()
      .then(() => {
        if (!request.loaded) {
          cache.delete(regionId);
        }
        return request;
      });
  }

  // For a unit test's own `beforeEach`, where the module-level cache would otherwise
  // outlive any one test and make `forRegion`'s call count depend on run order - the
  // real page session this class is cached for has no equivalent need to clear it.
  static clearCache () {
    cache.clear();
  }
}

export default CapacityBlockInstanceTypes;
