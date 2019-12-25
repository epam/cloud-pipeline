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

import Remote from '../basic/Remote';

class PipelineRunFSBrowser extends Remote {
  constructor (id) {
    super();
    this.url = `/run/${id}/fsbrowser`;
  }
}

class PipelineRunFSBrowserCache {
  /* eslint-disable */
  static getCache (cache, id) {
    const key = `${id}`;
    if (!cache.has(key)) {
      cache.set(key, new PipelineRunFSBrowser(id));
    }
    return cache.get(key);
  }

  /* eslint-enable */
  static invalidateCache (cache, id) {
    const key = `${id}`;
    if (cache.has(key)) {
      if (cache.get(key).invalidateCache) {
        cache.get(key).invalidateCache();
      } else {
        cache.delete(key);
      }
    }
  }

  _cache = new Map();
  getRunFSBrowserLink (id) {
    return this.constructor.getCache(this._cache, id);
  }
}

export default new PipelineRunFSBrowserCache();
