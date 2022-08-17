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

class APICallError extends Error {
  constructor (options = {}) {
    const {
      title,
      error
    } = options;
    super([title, error].filter(Boolean).join(': '));
  }
}

/**
 * @typedef {Object} EndpointAPIOptions
 * @property {string} [name]
 * @property {boolean} [credentials=false] Include credentials (cookies) to the request
 * @property {boolean} [fetchToken=true] Fetch user token if not provided
 * @property {string} [token] User token (will be included as `bearer` header)
 * @property {string} [testConnectionURI] Test endpoint availability
 */

class EndpointAPI {
  /**
   * @param {string} endpoint
   * @param {EndpointAPIOptions} options
   * @returns {Promise<boolean>}
   */
  static check (endpoint, options = {}) {
    try {
      const api = new this(endpoint, options);
      return api.testConnection();
    } catch (_) {
      return Promise.resolve(false);
    }
  }
  @observable endpoint;

  /**
   * @param {string} endpoint
   * @param {EndpointAPIOptions} options
   */
  constructor (endpoint, options = {}) {
    const {
      token,
      fetchToken = true,
      credentials = false,
      name,
      testConnectionURI = ''
    } = options;
    this.endpoint = endpoint;
    this.fetchToken = fetchToken;
    this.token = token ? Promise.resolve(token) : undefined;
    this.credentials = credentials ? 'include' : 'omit';
    this.name = name;
    this.testConnectionURI = testConnectionURI;
  }

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
   * @returns {Promise<boolean>}
   */
  testConnection = async () => {
    try {
      await this.apiCall({
        uri: this.testConnectionURI
      });
      console.info(`Endpoint "${this.endpoint}" -> available`);
      return true;
    } catch (exception) {
      const available = exception instanceof APICallError;
      console.info(`Endpoint "${this.endpoint}" -> ${available ? 'available' : 'not available'}`);
      return available;
    }
  };

  /**
   * @typedef {Object} APICallOptions
   * @property {string} [uri]
   * @property {string} [url]
   * @property {string} [httpMethod=GET]
   * @property {string|object} [body]
   * @property {string|object} [query]
   * @property {boolean} [isJSON=true]
   * @property {boolean} [ignoreResponse]
   */

  /**
   * @param {APICallOptions} options
   */
  apiCall = async (options = {}) => {
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
      isJSON = true,
      ignoreResponse = false
    } = options || {};
    const url = rawURL || this.getMethodURL(uri, query);
    const errorName = this.name || uri || url;
    let bodyFormatted;
    if (typeof body === 'object') {
      bodyFormatted = JSON.stringify(body);
    } else if (typeof body !== 'undefined') {
      bodyFormatted = body;
    }
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
    if (ignoreResponse) {
      return undefined;
    }
    if (!response.ok) {
      const infos = [
        response.statusText,
        response.status ? `(${response.status})` : false
      ].filter(Boolean);
      if (!infos.length) {
        infos.push('error fetching data');
      }
      throw new Error(`${errorName} ${infos.join(' ')}`);
    }
    if (isJSON) {
      try {
        const json = await response.json();
        if (!/^ok$/i.test(json.status)) {
          throw new APICallError({
            error: `${json.status} ${json.message || json.error}`
          });
        }
        return json.payload;
      } catch (e) {
        throw new APICallError({title: errorName, error: e.message});
      }
    }
    return response.text();
  };
}

export default EndpointAPI;
