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

import FolderLoad from './FolderLoadWithMetadata';

class Folders {
  /* eslint-disable */
  static getCache (cache, id, model) {
    if (!cache.has(id)) {
      cache.set(id, new model(id));
    }

    return cache.get(id);
  }

  /* eslint-enable */
  static invalidateCache (cache, id) {
    if (cache.has(id)) {
      if (cache.get(id).invalidateCache) {
        cache.get(id).invalidateCache();
      } else {
        cache.delete(id);
      }
    }
  }

  _foldersCache = new Map();

  load (id) {
    return this.constructor.getCache(this._foldersCache, `${id}`, FolderLoad);
  }

  invalidateFolder (id) {
    this.constructor.invalidateCache(this._foldersCache, `${id}`);
    FolderLoad.metadataCache.invalidateMetadata(id, 'FOLDER');
  }
}

export default new Folders();
