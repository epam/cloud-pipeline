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

// Held for the page session: the catalogue a region sells changes on AWS's timescale,
// not a user's.
const cache = new Map();

class CapacityBlockInstanceTypes extends Remote {
  // `Remote` defaults to `{}`; consumers read this value before the first fetch resolves.
  static defaultValue = [];

  constructor (regionId) {
    super();
    this.url = `/cluster/capacity-reservation/instance-types?regionId=${regionId}`;
  }

  /**
   * The cached, page-session instance for `regionId`.
   * @param {number|string} regionId
   * @returns {Promise<CapacityBlockInstanceTypes>}
   */
  static forRegion (regionId) {
    // A loaded pool supplies a numeric regionId, while RegionSelect's option value is a string.
    const key = String(regionId);
    if (!cache.has(key)) {
      cache.set(key, new CapacityBlockInstanceTypes(regionId));
    }
    const request = cache.get(key);
    return request.fetchIfNeededOrWait()
      .then(() => {
        // Evict a failed fetch so the next call retries it.
        if (!request.loaded) {
          cache.delete(key);
        }
        return request;
      });
  }

  // Test-only: the cache otherwise outlives a test and makes call counts order-dependent.
  static clearCache () {
    cache.clear();
  }
}

export default CapacityBlockInstanceTypes;
