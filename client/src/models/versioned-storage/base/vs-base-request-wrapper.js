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

import {computed, observable} from 'mobx';
import {fetchToken} from '../../user/UserToken';
import pipelineRunFSBrowserCache from '../../pipelines/PipelineRunFSBrowserCache';
import multiZoneManager from '../../../utils/multizone';

function fetchEndpoint (runId, defaultRegion = undefined) {
  return new Promise((resolve, reject) => {
    const fsBrowserRequest = pipelineRunFSBrowserCache.getPipelineRunFSBrowser(runId);
    fsBrowserRequest
      .fetchIfNeededOrWait()
      .then(() => {
        if (fsBrowserRequest.error || !fsBrowserRequest.value) {
          // eslint-disable-next-line
          reject(new Error(`Error fetching FS Browser endpoint for #${runId} run: ${fsBrowserRequest.error}`));
        } else {
          const multiZoneEndpoints = fsBrowserRequest.value;
          multiZoneManager
            .checkRegions()
            .then(() => {
              let bestRegion = defaultRegion;
              if (!bestRegion || !multiZoneEndpoints.hasOwnProperty(bestRegion)) {
                bestRegion = multiZoneManager.getDefaultURLRegion(multiZoneEndpoints);
              }
              resolve({url: multiZoneEndpoints[bestRegion], configuration: multiZoneEndpoints});
            });
        }
      })
      .catch(e => {
        reject(new Error(`Error fetching FS Browser endpoint for #${runId} run: ${e.message}`));
      });
  });
}

function fetchConfigurations (runId, defaultRegion = undefined) {
  return new Promise((resolve, reject) => {
    Promise.all([
      fetchToken(),
      fetchEndpoint(runId, defaultRegion)
    ])
      .then(payloads => {
        const [token, endpoint] = payloads;
        const {url, configuration} = endpoint || {};
        resolve({token, endpoint: url, configuration});
      })
      .catch(reject);
  });
}

export default function wrapStandardRequest (RequestClass) {
  return class extends RequestClass {
    @observable _endpoint;
    @observable _token;
    @observable _endpointsConfiguration;
    fetchConfigurationPromise;
    runId;

    @computed
    get endpoint () {
      return this._endpoint;
    }

    set endpoint (value) {
      if (value) {
        this._endpoint = value.endsWith('/') ? value : value.concat('/');
        this.constructor.prefix = this._endpoint;
      } else {
        this._endpoint = undefined;
      }
    }

    @computed
    get endpointsConfiguration () {
      return this._endpointsConfiguration;
    }

    @computed
    get token () {
      return this._token;
    }

    set token (value) {
      if (value) {
        this.constructor.fetchOptions.headers = Object.assign(
          {},
          this.constructor.fetchOptions.headers || {},
          {
            bearer: value
          }
        );
        this._token = value;
      } else {
        this._endpoint = undefined;
      }
    }

    constructor (runId, defaultRegion = undefined) {
      super();
      this.runId = runId;
      this.defaultRegion = defaultRegion;
    }

    fetchRequestOptions () {
      if (this.endpoint && this.token) {
        return Promise.resolve({
          endpoint: this.endpoint,
          token: this.token
        });
      }
      if (this.fetchConfigurationPromise) {
        return this.fetchConfigurationPromise;
      }
      const runId = this.runId;
      this.fetchConfigurationPromise = new Promise((resolve) => {
        fetchConfigurations(runId, this.defaultRegion)
          .then((configuration) => {
            if (configuration && configuration.endpoint && configuration.token) {
              this.endpoint = configuration.endpoint;
              this.token = configuration.token;
              resolve({
                endpoint: this.endpoint,
                token: this.token
              });
            } else {
              console.warn('Error fetching FS Browser API endpoint and user token');
              this.fetchConfigurationPromise = undefined;
              resolve();
            }
          })
          .catch(e => {
            console.warn(e.message);
            this.fetchConfigurationPromise = undefined;
            resolve();
          });
      });
      return this.fetchConfigurationPromise;
    }
  };
}
