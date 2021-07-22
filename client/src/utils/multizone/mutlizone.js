/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import {action, computed, observable} from 'mobx';
import measureUrlLatency from './measure-url-latency';
import parseRunServiceUrl from '../parseRunServiceUrl';
import PreferenceLoad from '../../models/preferences/PreferenceLoad';

function getRegionLatency (region, url) {
  return new Promise((resolve) => {
    measureUrlLatency(url)
      .then(latency => resolve({region, latency}));
  });
}

function getDefaultServiceUrl (regionServiceUrl) {
  try {
    const urls = parseRunServiceUrl(regionServiceUrl);
    const defaultUrl = urls.find(url => url.default) || urls[0];
    if (defaultUrl) {
      return defaultUrl.url;
    }
    return undefined;
  } catch (_) {
    return undefined;
  }
}

class Mutlizone {
  @observable _defaultRegion;
  @observable _defaultRegionPreference;
  _defaultRegionPreferenceFetched;
  @observable _latencies = {};
  _checkRegions = {};

  constructor (defaultRegion) {
    this._defaultRegion = defaultRegion;
    this.fetchDefaultRegionPreference();
  }

  @computed
  get defaultRegion () {
    return this._defaultRegion || this._defaultRegionPreference;
  }

  fetchDefaultRegionPreference () {
    if (this._defaultRegionPreferenceFetched) {
      return Promise.resolve(this._defaultRegionPreference);
    }
    return new Promise((resolve) => {
      const request = new PreferenceLoad('default.edge.region');
      request
        .fetch()
        .then(() => {
          if (request.loaded && request.value) {
            this._defaultRegionPreference = request.value.value;
          }
        })
        .catch(() => {})
        .then(() => {
          this._defaultRegionPreferenceFetched = true;
          console.info(
            'Default region (preference):',
            this._defaultRegionPreference || '<not set>'
          );
          console.info('Default region:', this.defaultRegion);
        });
    });
  }

  /**
   * Checks run ssh / fsbrowser response
   * @param request
   */
  checkRunUrlRequest (request) {
    return new Promise((resolve) => {
      request
        .fetchIfNeededOrWait()
        .then(() => {
          if (request.loaded) {
            return this.check(request.value);
          }
          return Promise.resolve();
        })
        .then(resolve)
        .catch(() => resolve());
    });
  }

  checkRun (runRequest) {
    return new Promise((resolve) => {
      runRequest
        .fetchIfNeededOrWait()
        .then(() => {
          if (runRequest.loaded && runRequest.value.serviceUrl) {
            const {serviceUrl = {}} = runRequest.value;
            return this.checkRunServiceUrl(serviceUrl);
          }
          return Promise.resolve();
        })
        .then(resolve)
        .catch(() => resolve());
    });
  }

  checkRunsServiceUrls (runs) {
    const serviceUrls = (runs || [])
      .map(run => run.serviceUrl)
      .filter(Boolean);
    const configuration = serviceUrls.reduce((r, c) => ({...r, ...c}), {});
    return this.checkRunServiceUrl(configuration);
  }

  checkRunServiceUrl (serviceUrl) {
    const configurations = Object
      .entries(serviceUrl || {})
      .map(([region, urlConfiguration]) => ({
        [region]: getDefaultServiceUrl(urlConfiguration)
      }))
      .reduce((r, c) => ({...r, ...c}), {});
    return this.check(configurations);
  }

  check (urlConfiguration, recalculate = false) {
    const result = {...this._latencies};
    const configurations = Object.entries(urlConfiguration)
      .map(([region, url]) => ({region, url}))
      .filter(({region}) => recalculate ||
        !result.hasOwnProperty(region) ||
        result[region] === Infinity
      );
    const regionsToCheck = [...new Set(configurations.map(configuration => configuration.region))]
      .filter(region => !this._checkRegions[region]);
    if (regionsToCheck.length === 0) {
      return Promise.resolve(this.defaultRegion);
    }
    regionsToCheck.forEach(region => {
      this._checkRegions[region] = true;
    });
    const check = regionsToCheck
      .map(region => configurations.find(configuration => configuration.region === region))
      .filter(Boolean);
    return new Promise((resolve) => {
      Promise.all(
        check.map(({region, url}) => getRegionLatency(region, url))
      )
        .then(latencies => {
          let changed = false;
          latencies.forEach(({region, latency}) => {
            if (result[region] !== latency) {
              result[region] = latency;
              changed = true;
            }
          });
          if (changed) {
            this._latencies = result;
          }
          const ms = value => value === Infinity ? '---' : (`${Math.round(value * 100) / 100.0}ms`);
          console.info(
            'Multi-zone latency check:',
            Object.entries(result)
              .map(([key, value]) => `${key}: ${ms(value)}`)
              .join(', ')
          );
          this.updateDefaultRegion();
        })
        .then(() => {
          resolve(this.defaultRegion);
        })
        .then(() => {
          regionsToCheck.forEach(region => {
            delete this._checkRegions[region];
          });
        });
    });
  }

  @action
  updateDefaultRegion () {
    const latencies = Object
      .entries(this._latencies)
      .map(([region, latency]) => ({region, latency: Number(latency)}))
      .filter(info => info.latency !== Infinity)
      .sort((latencyA, latencyB) => latencyA.latency - latencyB.latency);
    const defaultRegion = latencies.length > 0 ? latencies[0].region : undefined;
    if (defaultRegion !== this._defaultRegion) {
      this._defaultRegion = defaultRegion;
      console.info('Default region changed:', this.defaultRegion);
    }
  }

  getDefaultRegion (...regions) {
    if (regions.length === 0) {
      return this.defaultRegion;
    }
    const latencies = Object
      .entries(this._latencies || {})
      .map(([region, latency]) => ({region, latency: Number(latency)}))
      .sort((latencyA, latencyB) => latencyA.latency - latencyB.latency);
    const defaultRegion = latencies.find(latencyInfo => regions.indexOf(latencyInfo.region) >= 0);
    if (defaultRegion) {
      return defaultRegion.region;
    }
    return this.defaultRegion;
  }

  getDefaultURLRegion (configuration) {
    const regions = Object.keys(configuration || {});
    const defaultRegion = this.getDefaultRegion(...regions) || regions[0];
    if (configuration.hasOwnProperty(defaultRegion)) {
      return defaultRegion;
    }
    return undefined;
  }

  getSortedRegions (regions) {
    const sorted = [...regions];
    const getRegionLatency = region => this._latencies.hasOwnProperty(region)
      ? this._latencies[region]
      : Infinity;
    sorted
      .sort((a, b) => {
        if (a === this._defaultRegionPreference) {
          return -1;
        }
        if (b === this._defaultRegionPreference) {
          return 1;
        }
        return 0;
      })
      .sort((a, b) => getRegionLatency(a) - getRegionLatency(b));
    return sorted;
  }

  getSortedRegionsWithUrls (configuration) {
    return this
      .getSortedRegions(Object.keys(configuration || {}))
      .map(region => ({
        region,
        url: configuration[region]
      }));
  }
}

export default Mutlizone;
