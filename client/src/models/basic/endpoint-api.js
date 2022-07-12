/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import {observable} from 'mobx';
import {fetchToken} from '../user/UserToken';

const removeSlashes = (str) => {
  if (!str) {
    return '';
  }
  let result = str.slice();
  if (result.startsWith('/')) {
    result = result.slice(1);
  }
  if (result.endsWith('/')) {
    result = result.slice(0, -1);
  }
  return result;
};

function getMethodURL (endpoint, method) {
  return [endpoint, method]
    .map(removeSlashes)
    .filter(o => o.length)
    .join('/');
}

class AuthenticationRequiredError extends Error {
  constructor (title) {
    super(title ? `${title}: authentication required` : 'Authentication required');
  }
}

function waitForAuthentication (
  endpoint,
  healthEndpoint,
  fetchOptions = {mode: 'cors', credentials: 'include'},
  options = {}
) {
  const {
    authenticationTimeoutMs = 1000 * 60,
    healthCheckTimeoutMs = 1000,
    initialHealthCheckTimeoutMs = 500,
    popupConfiguration
  } = options;
  const check = async () => {
    try {
      const response = await fetch(healthEndpoint || endpoint, fetchOptions);
      if (response.redirected) {
        throw new Error('redirected');
      }
      return true;
    } catch (e) {
      // Network-related error
      return false;
    }
  };
  return new Promise((resolve, reject) => {
    let handled = false;
    let healthCheckTimeOut;
    let timeoutHandler;
    const popup = popupConfiguration
      ? window.open(endpoint, '_blank', popupConfiguration)
      : window.open(endpoint, '_blank');
    const healthCheck = async () => {
      clearTimeout(healthCheckTimeOut);
      const healthy = await check();
      if (handled) {
        return;
      }
      if (healthy) {
        handled = true;
        if (popup) {
          popup.close();
        }
        clearTimeout(timeoutHandler);
        resolve();
      } else if (popup && popup.closed) {
        clearTimeout(timeoutHandler);
        reject(new Error('Authentication failed'));
      } else {
        healthCheckTimeOut = setTimeout(healthCheck, healthCheckTimeoutMs);
      }
    };
    timeoutHandler = setTimeout(() => {
      if (handled) {
        return;
      }
      clearTimeout(healthCheckTimeOut);
      handled = true;
      if (popup) {
        popup.close();
      }
      reject(new Error('Authentication timeout'));
    }, authenticationTimeoutMs);
    setTimeout(healthCheck, initialHealthCheckTimeoutMs);
  });
}

class APICallError extends Error {
  constructor (options = {}) {
    const {
      title,
      error
    } = options;
    super([title, error].filter(Boolean).join(': '));
  }
}

class EndpointAPI {
  @observable endpoint;
  @observable requiresUserAuthentication = false;
  authenticationPromise;
  constructor (endpoint, options = {}) {
    const {
      token,
      fetchToken = true,
      credentials = false,
      name,
      healthCheckURI,
      authenticationTimeoutMs,
      healthCheckTimeoutMs,
      initialHealthCheckTimeoutMs,
      popupConfiguration
    } = options;
    this.endpoint = endpoint;
    this.fetchToken = fetchToken;
    this.token = token ? Promise.resolve(token) : undefined;
    this.credentials = credentials ? 'include' : 'omit';
    this.name = name;
    this._healthCheckURI = healthCheckURI || '';
    this.authenticationTimeOutOptions = {
      authenticationTimeoutMs,
      healthCheckTimeoutMs,
      initialHealthCheckTimeoutMs,
      popupConfiguration
    };
  }

  authenticate = () => {
    if (!this.authenticationPromise) {
      this.authenticationPromise = waitForAuthentication(
        this.endpoint,
        this.getMethodURL(this._healthCheckURI),
        {
          mode: 'cors',
          credentials: this.credentials
        },
        this.authenticationTimeOutOptions
      );
      this.authenticationPromise
        .then(() => {
          this.requiresUserAuthentication = false;
        })
        .catch(() => {})
        .then(() => {
          this.authenticationPromise = undefined;
        });
    }
    return this.authenticationPromise;
  };

  getMethodURL = (uri, query) => {
    const url = getMethodURL(this.endpoint, uri);
    if (!query) {
      return url;
    }
    if (typeof query === 'string') {
      return `${url}?${query.startsWith('?') ? query.slice(1) : query}`;
    }
    if (typeof query === 'object') {
      const parameters = Object.entries(query)
        .filter(([, value]) => value !== undefined && value !== null);
      if (parameters.length) {
        const queryString = parameters
          .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
          .join('&');
        return `${url}?${queryString}`;
      }
    }
    return url;
  }

  /**
   * @typedef {Object} APICallOptions
   * @property {string} [uri]
   * @property {string} [url]
   * @property {string} [httpMethod=GET]
   * @property {string|object} [body]
   * @property {string|object} [query]
   * @property {boolean} [isJSON=true]
   */

  /**
   * @param {APICallOptions} options
   */
  apiCall = async (options = {}) => {
    const wrap = async () => {
      try {
        const result = await this._apiCall(options);
        return {result};
      } catch (error) {
        return {error};
      }
    };
    const {result, error} = await wrap();
    if (!error) {
      return result;
    }
    if (this.requiresUserAuthentication) {
      console.log(options.uri, 'requires authentication');
      await this.authenticate();
      return this._apiCall(options);
    } else if (error) {
      throw error;
    }
  };

  /**
   * @param {APICallOptions} options
   */
  _apiCall = async (options = {}) => {
    if (!this.token && this.fetchToken) {
      this.token = fetchToken();
    }
    let token;
    if (this.token) {
      token = await this.token;
    }
    const {
      uri,
      url: rawURL,
      body,
      httpMethod = body ? 'POST' : 'GET',
      query,
      isJSON = true
    } = options || {};
    const url = rawURL || this.getMethodURL(uri, query);
    const errorName = this.name || uri || url;
    let bodyFormatted;
    if (typeof body === 'object') {
      bodyFormatted = JSON.stringify(body);
    } else if (typeof body !== 'undefined') {
      bodyFormatted = body;
    }
    try {
      const response = await fetch(
        url,
        {
          method: httpMethod,
          body: bodyFormatted,
          mode: 'cors',
          credentials: this.credentials,
          headers: {
            ...(token ? {bearer: token} : {})
          }
        }
      );
      if (response.redirected) {
        this.requiresUserAuthentication = true;
        throw new AuthenticationRequiredError(errorName);
      }
      if (!response.ok) {
        const infos = [
          response.statusText,
          response.status ? `(${response.status})` : false
        ].filter(Boolean);
        if (!infos.length) {
          infos.push('error fetching data');
        }
        throw new APICallError(errorName, infos.join(' '));
      }
      if (isJSON) {
        try {
          const json = await response.json();
          if (!/^ok$/i.test(json.status)) {
            throw new APICallError(errorName, `${json.status} ${json.message || json.error}`);
          }
          return json.payload;
        } catch (e) {
          throw new APICallError(errorName, e.message);
        }
      }
      return response.text();
    } catch (e) {
      // Network error
      this.requiresUserAuthentication = !(e instanceof APICallError);
      if (this.requiresUserAuthentication) {
        throw new AuthenticationRequiredError(errorName);
      } else {
        throw e;
      }
    }
  };
}

export default EndpointAPI;
