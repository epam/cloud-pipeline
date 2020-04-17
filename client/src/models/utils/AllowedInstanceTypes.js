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
import {action, computed, observable} from 'mobx';
import defer from '../../utils/defer';

export default class AllowedInstanceTypes extends Remote {
  @observable _isSpot;
  @observable _toolId;
  @observable _regionId;
  @observable _changed = false;

  constructor (toolId, regionId, isSpot) {
    super();
    this._toolId = toolId;
    this._regionId = regionId;
    this._isSpot = isSpot;
    this.fetchOnChange();
  }

  @computed
  get isSpot () {
    return this._isSpot;
  }

  @computed
  get regionId () {
    return this._regionId;
  }

  @computed
  get toolId () {
    return this._toolId;
  }

  setParameters ({isSpot, regionId, toolId}) {
    let spotChanged, regionChanged, toolChanged;
    spotChanged = isSpot !== undefined && this._isSpot !== isSpot;
    regionChanged = regionId && regionId !== this._regionId;
    toolChanged = toolId && toolId !== this._toolId;
    this._isSpot = isSpot;
    this._regionId = regionId;
    this._toolId = toolId;
    if (spotChanged || regionChanged || toolChanged) {
      this.fetchOnChange();
    }
    return spotChanged || regionChanged || toolChanged;
  }

  setIsSpot (value) {
    if (value !== undefined && value !== null && this._isSpot !== value) {
      this._isSpot = value;
      this.fetchOnChange();
    }
  }

  setRegionId (value, force = false) {
    if ((value || force) && this._regionId !== value) {
      this._regionId = value;
      this.fetchOnChange();
    }
  }

  setToolId (value) {
    if (value && this._toolId !== value) {
      this._toolId = value;
      this.fetchOnChange();
    }
  }

  @computed
  get changed () {
    return this._changed;
  }

  @action
  handleChanged () {
    this._changed = false;
  }

  @action
  fetchOnChange () {
    this.initialize();
    this.invalidateCache();
    this._loadRequired = true;
    this.fetch(true).then(() => { this._changed = true; });
  }

  initialize () {
    const params = [];
    if (this._toolId) {
      params.push(`toolId=${this._toolId}`);
    }
    if (this._regionId) {
      params.push(`regionId=${this._regionId}`);
    }
    if (this._isSpot !== undefined && this._isSpot !== null) {
      params.push(`spot=${this._isSpot ? 'true' : 'false'}`);
    }
    if (params.length) {
      this.url = `/cluster/instance/allowed?${params.join('&')}`;
    } else {
      this.url = '/cluster/instance/allowed';
    }
  }

  /* eslint-disable */
  static getCache (cache, toolId, regionId, model) {
    if (!cache.has(`${+toolId}-${+regionId}`)) {
      cache.set(`${+toolId}-${+regionId}`, new model(toolId, regionId));
    }
    return cache.get(`${+toolId}-${+regionId}`);
  }

  /* eslint-enable */
  static invalidateCache (cache, toolId, regionId) {
    if (cache.has(`${+toolId}-${+regionId}`)) {
      if (cache.get(`${+toolId}-${+regionId}`).invalidateCache) {
        cache.get(`${+toolId}-${+regionId}`).invalidateCache();
      } else {
        cache.delete(`${+toolId}-${+regionId}`);
      }
    }
  }

  _allowedTypesCache = new Map();
  getAllowedTypes (toolId, regionId) {
    return this.constructor.getCache(
      this._allowedTypesCache,
      toolId,
      regionId,
      AllowedInstanceTypes
    );
  }

  invalidateAllowedTypes (toolId, regionId) {
    this.constructor.invalidateCache(this._allowedTypesCache, toolId, regionId);
  }

  async fetch (force = false) {
    this._loadRequired = false;
    if (!this._fetchPromise || force) {
      this._fetchPromise = new Promise(async (resolve) => {
        const isSpot = this._isSpot;
        const regionId = this._regionId;
        const toolId = this._toolId;
        this._pending = true;
        const {prefix, fetchOptions} = this.constructor;
        try {
          await defer();
          let headers = fetchOptions.headers;
          if (!headers) {
            headers = {};
          }
          fetchOptions.headers = headers;
          const response = await fetch(`${prefix}${this.url}`, fetchOptions);
          const data = this.constructor.isJson ? (await response.json()) : (await response.blob());
          if (isSpot !== this._isSpot || regionId !== this._regionId || toolId !== this._toolId) {
            resolve();
            return;
          } else {
            this.update(data);
          }
        } catch (e) {
          this.failed = true;
          this.error = e.toString();
        }

        this._pending = false;
        this._fetchPromise = null;
        resolve();
      });
    }
    return this._fetchPromise;
  }
}
