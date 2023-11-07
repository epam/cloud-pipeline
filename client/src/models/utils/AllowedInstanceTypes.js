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
import cloudRegions from '../cloudRegions/CloudRegions';

function providersAreEqual (setA, setB) {
  const a = [...new Set(setA || [])].sort();
  const b = [...new Set(setB || [])].sort();
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i += 1) {
    if (a[i] !== b[i]) {
      return false;
    }
  }
  return true;
}

function mergeResults (results = [], regions = [], mainRegion = undefined) {
  const combined = results.map((r, idx) => ({
    ...r,
    region: regions[idx]
  }));
  const unauthorized = combined.find((o) => o.status === 401);
  if (unauthorized) {
    return unauthorized;
  }
  if (combined.length > 0 && !combined.some((o) => o.status === 'OK')) {
    return combined[0];
  }
  const filtered = combined.filter((o) => o.status === 'OK');
  if (filtered.length === 1) {
    return filtered[0];
  }
  const instanceKeys = [
    'cluster.allowed.instance.types',
    'cluster.allowed.instance.types.docker'
  ];
  let initial = filtered
    .find((o) => o.region === mainRegion) || filtered[0];
  if (initial.status !== 'OK') {
    initial = filtered[0];
  }
  return filtered
    .reduce((r, current) => {
      const {
        payload: resultedPayload = {}
      } = r;
      const {
        payload = {}
      } = current;
      instanceKeys.forEach((key) => {
        if (!resultedPayload[key]) {
          resultedPayload[key] = [];
        }
        (payload[key] || []).forEach((instance) => {
          const e = resultedPayload[key].find((o) => o.name === instance.name);
          if (e) {
            const {
              regionId,
              regionIds = []
            } = e;
            e.regionIds = [...new Set([...regionIds, regionId, instance.regionId])];
          } else {
            resultedPayload[key].push(instance);
          }
        });
      });
      return {
        ...r,
        payload: {
          ...resultedPayload,
          mergedRegions: filtered.map((r) => r.region)
        }
      };
    }, initial);
}

/**
 * @param {AllowedInstanceTypesOptions} options
 * @returns {string}
 */
function getCacheKey (options = {}) {
  const {
    toolId = '',
    regionId = '',
    spot
  } = options;
  return [
    toolId,
    regionId,
    spot === undefined ? '' : `${!!spot}`
  ].join('|');
}

export default class AllowedInstanceTypes extends Remote {
  @observable _isSpot;
  @observable _toolId;
  @observable _regionId;
  @observable _changed = false;

  _requestAllRegionsForProviders = [];

  /**
   * @typedef {Object} AllowedInstanceTypesOptions
   * @property {string|number} [toolId]
   * @property {string|number} [regionId]
   * @property {string|boolean} [spot]
   * @property {string[]} [requestAllRegionsForProviders]
   */

  /**
   * @param {AllowedInstanceTypesOptions} [options]
   */
  constructor (options = {}) {
    super();
    const {
      toolId,
      regionId,
      spot: isSpot,
      requestAllRegionsForProviders = []
    } = options || {};
    this._toolId = toolId;
    this._regionId = regionId;
    this._isSpot = isSpot;
    this._requestAllRegionsForProviders = requestAllRegionsForProviders;
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

  @computed
  get regionsMerged () {
    return !!this.loaded &&
      !!this.value &&
      !!this.value.mergedRegions &&
      this.value.mergedRegions.length > 1;
  }

  /**
   * @param {AllowedInstanceTypesOptions} options
   * @returns {boolean|*}
   */
  setParameters (options = {}) {
    const {
      toolId,
      regionId,
      spot: isSpot,
      requestAllRegionsForProviders = this._requestAllRegionsForProviders.slice()
    } = options || {};
    const spotChanged = isSpot !== undefined && this._isSpot !== isSpot;
    const regionChanged = regionId && regionId !== this._regionId;
    const toolChanged = toolId && toolId !== this._toolId;
    const providersChanged = !providersAreEqual(
      requestAllRegionsForProviders,
      this._requestAllRegionsForProviders
    );
    this._isSpot = isSpot;
    this._regionId = regionId;
    this._toolId = toolId;
    this._requestAllRegionsForProviders = requestAllRegionsForProviders;
    if (spotChanged || regionChanged || toolChanged || providersChanged) {
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
    this.url = this.initializeForRegion(this._regionId);
  }

  initializeForRegion (region = undefined) {
    const params = [];
    if (this._toolId) {
      params.push(`toolId=${this._toolId}`);
    }
    if (region) {
      params.push(`regionId=${region}`);
    }
    if (this._isSpot !== undefined && this._isSpot !== null) {
      params.push(`spot=${this._isSpot ? 'true' : 'false'}`);
    }
    if (params.length) {
      return `/cluster/instance/allowed?${params.join('&')}`;
    }
    return '/cluster/instance/allowed';
  }

  /* eslint-disable */
  static getCache (cache, toolId, regionId, model) {
    if (!cache.has(`${+toolId}-${+regionId}`)) {
      cache.set(`${+toolId}-${+regionId}`, new model({
        toolId,
        regionId
      }));
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
  _allRequestsCache = new Map();
  getAllowedTypes (toolId, regionId) {
    return this.constructor.getCache(
      this._allowedTypesCache,
      toolId,
      regionId,
      AllowedInstanceTypes
    );
  }

  invalidateAllowedTypes (toolId, regionId) {
    this._allRequestsCache.delete(getCacheKey({toolId, regionId}));
    this._allRequestsCache.delete(getCacheKey({toolId, regionId, spot: true}));
    this._allRequestsCache.delete(getCacheKey({toolId, regionId, spot: false}));
    this.constructor.invalidateCache(this._allowedTypesCache, toolId, regionId);
  }

  async doFetchForRegion (region = undefined) {
    const url = this.initializeForRegion(region);
    const key = getCacheKey({
      toolId: this._toolId,
      regionId: region,
      spot: this._isSpot
    });
    if (this._allRequestsCache.has(key)) {
      return this._allRequestsCache.get(key);
    }
    try {
      const {prefix, fetchOptions} = this.constructor;
      let headers = fetchOptions.headers;
      if (!headers) {
        headers = {};
      }
      fetchOptions.headers = headers;
      const response = await fetch(`${prefix}${url}`, fetchOptions);
      const result = await response.json();
      if (result && result.status === 'OK') {
        this._allRequestsCache.set(key, result);
      }
      return result;
    } catch (error) {
      return {
        status: 'ERROR',
        message: error.message
      };
    }
  }

  async fetch (force = false) {
    this._loadRequired = false;
    if (!this._fetchPromise || force) {
      this._fetchPromise = new Promise(async (resolve) => {
        const isSpot = this._isSpot;
        const regionId = this._regionId;
        const toolId = this._toolId;
        this._pending = true;
        try {
          let regions = [this._regionId];
          if (
            regionId &&
            this._requestAllRegionsForProviders &&
            this._requestAllRegionsForProviders.length > 0
          ) {
            await cloudRegions.fetchIfNeededOrWait();
            if (cloudRegions.loaded) {
              const regionInfo = (cloudRegions.value || [])
                .find((o) => Number(o.id) === Number(regionId));
              if (regionInfo && this._requestAllRegionsForProviders.includes(regionInfo.provider)) {
                regions = (cloudRegions.value || [])
                  .filter((r) => this._requestAllRegionsForProviders.includes(r.provider))
                  .map((r) => r.id);
              }
            }
          }
          const results = await Promise.all(regions.map((r) => this.doFetchForRegion(r)));
          const merged = mergeResults(results, regions, regionId);
          if (isSpot !== this._isSpot || regionId !== this._regionId || toolId !== this._toolId) {
            resolve();
            return;
          } else {
            this.update(merged);
          }
        } catch (e) {
          console.warn(e);
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
