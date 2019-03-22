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
import Pipeline from './Pipeline';
import Version from './Version';
import Source from './Source';
import PipelineLanguage from './PipelineLanguage';
import PipelineConfigurations from './PipelineConfigurations';
import Docs from './Docs';

class Pipelines extends Remote {
  static defaultValue = [];
  url = '/pipeline/loadAll';

  /* eslint-disable */
  static getCache (cache, id, model) {
    if (!cache.has(+id)) {
      cache.set(+id, new model(id));
    }

    return cache.get(+id);
  }

  /* eslint-enable */
  static invalidateCache (cache, id) {
    if (cache.has(+id)) {
      if (cache.get(+id).invalidateCache) {
        cache.get(+id).invalidateCache();
      } else {
        cache.delete(+id);
      }
    }
  }

  /* eslint-disable */
  static getIdVersionCache (cache, id, version, model) {
    const key = `${id}/${version}`;
    if (!cache.has(key)) {
      cache.set(key, new model(id, version));
    }
    return cache.get(key);
  }

  /* eslint-enable */
  static invalidateIdVersionCache (cache, id, version) {
    const key = `${id}/${version}`;
    if (cache.has(key)) {
      if (cache.get(key).invalidateCache) {
        cache.get(key).invalidateCache();
      } else {
        cache.delete(key);
      }
    }
  }

  _pipelinesCache = new Map();
  getPipeline (id) {
    return this.constructor.getCache(this._pipelinesCache, id, Pipeline);
  }

  invalidatePipeline (id) {
    this.constructor.invalidateCache(this._pipelinesCache, id);
    this.invalidateVersionsForPipeline(id);
  }

  _versionsCache = new Map();
  versionsForPipeline (id) {
    return this.constructor.getCache(this._versionsCache, id, Version);
  }

  invalidateVersionsForPipeline (id) {
    this.constructor.invalidateCache(this._versionsCache, id);
  }

  _sourceCache;

  getSource (id, version, path) {
    if (!this._sourceCache ||
      this._sourceCache.id !== id ||
      this._sourceCache.version !== version ||
      this._sourceCache.path !== path) {
      this._sourceCache = new Source(id, version, path);
    }
    return this._sourceCache;
  }

  _languagesCache = new Map();
  getLanguage (id, version) {
    return this.constructor.getIdVersionCache(this._languagesCache, id, version, PipelineLanguage);
  }

  invalidateLanguage (id, version) {
    this.constructor.invalidateIdVersionCache(this._languagesCache, id, version);
  }

  _configurationsCache = new Map();
  getConfiguration (id, version) {
    return this.constructor.getIdVersionCache(this._configurationsCache, id, version, PipelineConfigurations);
  }

  invalidateConfiguration (id, version) {
    this.constructor.invalidateIdVersionCache(this._configurationsCache, id, version);
  }

  _docsCache = new Map();
  getDocuments (id, version) {
    return this.constructor.getIdVersionCache(this._docsCache, id, version, Docs);
  }

  invalidateDocuments (id, version) {
    this.constructor.invalidateIdVersionCache(this._docsCache, id, version);
  }
}

export default new Pipelines();
