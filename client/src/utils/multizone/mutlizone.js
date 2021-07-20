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
  @observable _latencies = {};

  constructor (defaultRegion) {
    this._defaultRegion = defaultRegion;
  }

  @computed
  get defaultRegion () {
    return this._defaultRegion;
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
      .filter(({region}) => recalculate || !result.hasOwnProperty(region));
    const regionsToCheck = [...new Set(configurations.map(configuration => configuration.region))];
    if (regionsToCheck.length === 0) {
      return Promise.resolve(this.defaultRegion);
    }
    if (!recalculate) {
      regionsToCheck.forEach(region => {
        this._latencies[region] = Infinity;
      });
    }
    const check = regionsToCheck
      .map(region => configurations.find(configuration => configuration.region === region))
      .filter(Boolean);
    return new Promise((resolve) => {
      Promise.all(
        check.map(({region, url}) => getRegionLatency(region, url))
      )
        .then(latencies => {
          latencies.forEach(({region, latency}) => {
            result[region] = latency;
          });
          this._latencies = result;
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
        });
    });
  }

  @action
  updateDefaultRegion () {
    const latencies = Object
      .entries(this._latencies)
      .map(([region, latency]) => ({region, latency: Number(latency)}))
      .sort((latencyA, latencyB) => latencyA.latency - latencyB.latency);
    this._defaultRegion = latencies.length > 0 ? latencies[0].region : undefined;
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
}

export default Mutlizone;
