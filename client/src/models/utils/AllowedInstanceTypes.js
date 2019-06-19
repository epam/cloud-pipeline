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

export default class AllowedInstanceTypes extends Remote {

  _isSpot;
  _toolId;
  _regionId;
  _initialRegionId;
  _changed = false;

  constructor (toolId, regionId, isSpot) {
    super();
    this._toolId = toolId;
    this._regionId = regionId;
    this._isSpot = isSpot;
    this.initialize();
    this.fetchIfNeededOrWait();
  }

  get isSpot () {
    return this._isSpot;
  }

  set isSpot (value) {
    if (value !== undefined && value !== null && this._isSpot !== value) {
      this._isSpot = value;
      this.initialize();
      this.invalidateCache();
      this._loaded = false;
      this.fetchIfNeededOrWait();
      this._changed = true;
    }
  }

  set toolId (value) {
    if (value && this._toolId !== value) {
      this._toolId = value;
      this.initialize();
      this.invalidateCache();
      this._loaded = false;
      this.fetchIfNeededOrWait();
      this._changed = true;
    }
  }

  get toolId () {
    return this._toolId;
  }

  set regionId (value) {
    if (value && this._regionId !== value) {
      this._regionId = value;
      this.initialize();
      this.invalidateCache();
      this._loaded = false;
      this.fetchIfNeededOrWait();
      this._changed = true;
    }
  }

  set initialRegionId (value) {
    if (this._initialRegionId !== value) {
      this._initialRegionId = value;
      this._regionId = value;
      this.initialize();
      this.invalidateCache();
      this.fetchIfNeededOrWait();
      this._changed = true;
    }
  }

  get regionId () {
    return this._regionId;
  }

  get changed () {
    return this._changed;
  }

  handleChanged () {
    this._changed = false;
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
    return this.constructor.getCache(this._allowedTypesCache, toolId, regionId, AllowedInstanceTypes);
  }

  invalidateAllowedTypes (toolId, regionId) {
    this.constructor.invalidateCache(this._allowedTypesCache, toolId, regionId);
  }

}
