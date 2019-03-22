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

import DataStorageTags from './tags/DataStorageTags';
import DataStorageItemContent from './DataStorageItemContent';
import GenerateDownloadUrlRequest from './GenerateDownloadUrl';

class DataStorageCache {

  static getKey (id, path, version) {
    if (version) {
      return `${id}-${path}-${version}`;
    } else {
      return `${id}-${path}`;
    }
  }

  /* eslint-disable */
  static getCache (cache, id, path, version, model) {
    const key = DataStorageCache.getKey(id, path, version);
    if (!cache.has(key)) {
      cache.set(key, new model(id, path, version));
    }
    return cache.get(key);
  }

  /* eslint-enable */
  static invalidateCache (cache, id, path, version) {
    const key = DataStorageCache.getKey(id, path, version);
    if (cache.has(key)) {
      if (cache.get(key).invalidateCache) {
        cache.get(key).invalidateCache();
      } else {
        cache.delete(key);
      }
    }
  }

  _cache = new Map();
  getTags (id, path, version) {
    return DataStorageCache.getCache(this._cache, id, path, version, DataStorageTags);
  }

  invalidateTags (id, path, version) {
    DataStorageCache.invalidateCache(this._cache, id, path, version);
  }

  _contentCache = new Map();
  getContent (id, path, version) {
    return DataStorageCache.getCache(this._contentCache, id, path, version, DataStorageItemContent);
  }

  invalidateContent (id, path, version) {
    DataStorageCache.invalidateCache(this._contentCache, id, path, version);
  }

  _downloadUrlCache = new Map();
  getDownloadUrl (id, path, version) {
    return DataStorageCache.getCache(this._downloadUrlCache, id, path, version, GenerateDownloadUrlRequest);
  }

  invalidateDownloadUrl (id, path, version) {
    DataStorageCache.invalidateCache(this._downloadUrlCache, id, path, version);
  }
}

export default new DataStorageCache();
