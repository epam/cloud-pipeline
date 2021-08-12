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
import FolderProject from './FolderProject';

class FolderProjects extends Remote {
  /* eslint-disable */
  static getCache (cache, id, type) {
    const key = `${id}-${type}`;
    if (!cache.has(key)) {
      cache.set(key, new FolderProject(id, type));
    }
    return cache.get(key);
  }

  /* eslint-enable */
  static invalidateCache (cache, id, type) {
    const key = `${id}-${type}`;
    if (cache.has(key)) {
      if (cache.get(key).invalidateCache) {
        cache.get(key).invalidateCache();
      } else {
        cache.delete(key);
      }
    }
  }
  constructor () {
    super();
    this.url = '/folder/projects';
    this.projects = new Map();
  }

  getProjectFor (id, type) {
    return this.constructor.getCache(this.projects, id, type);
  }

  invalidateProjectFor (id, type) {
    this.constructor.invalidateCache(this.projects, id, type);
  }
}

export default new FolderProjects();
