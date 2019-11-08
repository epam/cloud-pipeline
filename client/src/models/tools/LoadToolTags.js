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

export default class LoadToolTags extends Remote {
  constructor (id) {
    super();
    this.url = `/tool/${id}/tags`;
  }
}

class ToolTagsCache {
  /* eslint-disable */
  static getCache (cache, id) {
    if (!cache.has(`${id}`)) {
      cache.set(`${id}`, new LoadToolTags(id));
    }

    return cache.get(`${id}`);
  }

  /* eslint-enable */
  static invalidateCache (cache, id) {
    if (cache.has(`${id}`)) {
      if (cache.get(`${id}`).invalidateCache) {
        cache.get(`${id}`).invalidateCache();
      } else {
        cache.delete(`${id}`);
      }
    }
  }

  cache = new Map();

  getToolTags (id) {
    return ToolTagsCache.getCache(this.cache, id);
  }

  invalidateToolTags (id) {
    ToolTagsCache.invalidateCache(this.cache, id);
  }

  invalidateAllToolsTags () {
    this.cache.clear();
  }
}

export {ToolTagsCache};
