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
import {getAnalysisMethodURL} from './analysis-endpoint-utilities';

class AnalysisApi {
  @observable endpoint;
  constructor (endpoint, token) {
    this.endpoint = endpoint;
    this.token = token;
  }

  getMethodURL = (uri, query) => {
    const url = getAnalysisMethodURL(this.endpoint, uri);
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
    const {
      uri,
      url: rawURL,
      body,
      httpMethod = body ? 'POST' : 'GET',
      query,
      isJSON = true
    } = options || {};
    const url = rawURL || this.getMethodURL(uri, query);
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
        headers: {
          bearer: this.token
        }
      }
    );
    if (!response.ok) {
      throw new Error(`${uri || url}: ${response.statusText} ${response.status}`);
    }
    if (isJSON) {
      const json = await response.json();
      if (!/^ok$/i.test(json.status)) {
        throw new Error(`${uri || url}: ${json.status} ${json.message || json.error}`);
      }
      return json.payload;
    }
    return response.text();
  };

  buildPipeline = (measurementUUID) => this.apiCall({
    uri: 'hcs/pipelines',
    query: {measurementUUID},
    httpMethod: 'POST'
  });

  getPipeline = async (pipelineId) => {
    const payload = await this.apiCall({
      uri: 'hcs/pipelines',
      query: {pipelineId}
    });
    if (!payload || !payload.pipelineId) {
      throw new Error(`#${pipelineId} pipeline not found`);
    }
    return payload.pipelineId;
  };

  getPipelineModuleAtIndex = async (pipelineId, index) => {
    const pipeline = await this.getPipeline(pipelineId);
    if (!pipeline) {
      throw new Error(`#${pipelineId} pipeline not found`);
    }
    const {modules = []} = pipeline;
    return modules[index];
  };

  runPipeline = (pipelineId) => this.apiCall({
    uri: 'hcs/run/pipelines',
    query: {pipelineId},
    httpMethod: 'POST'
  });

  runPipelineModule = (pipelineId, moduleId) => this.apiCall({
    uri: 'hcs/run/pipelines',
    query: {pipelineId, moduleId},
    httpMethod: 'POST'
  });

  attachFiles = (pipelineId, ...files) => this.apiCall({
    uri: 'hcs/pipelines/files',
    query: {pipelineId},
    httpMethod: 'POST',
    body: files
  });

  createModule = (pipelineId, cpModule) => this.apiCall({
    uri: 'hcs/modules',
    query: {pipelineId},
    httpMethod: 'POST',
    body: cpModule
  });

  removeModule = (pipelineId, moduleId) => this.apiCall({
    uri: 'hcs/modules',
    query: {pipelineId, moduleId},
    httpMethod: 'DELETE'
  });

  getModuleStatus = (pipelineId, moduleId) => this.apiCall({
    uri: 'hcs/run/statuses',
    query: {pipelineId, moduleId}
  });

  getPipelineStatus = (pipelineId) => this.apiCall({
    uri: 'hcs/run/statuses',
    query: {pipelineId}
  });

  getPipelineModulesStatuses = async (pipelineId) => {
    const pipeline = await this.getPipeline(pipelineId);
    const {modules = []} = pipeline;
    const modulesStatuses = await Promise.all(
      modules.map((cpModule, idx) => this.getModuleStatus(pipelineId, idx + 1))
    );
    return {
      pipeline,
      statuses: modulesStatuses.map((status, idx) => ({cpModule: modules[idx], status}))
    };
  }
}

export default AnalysisApi;
