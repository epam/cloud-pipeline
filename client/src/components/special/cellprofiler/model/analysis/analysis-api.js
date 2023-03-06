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

import EndpointAPI from '../../../../../models/basic/endpoint-api';

class AnalysisApi extends EndpointAPI {
  constructor (endpoint) {
    super(
      endpoint,
      {
        fetchToken: true,
        credentials: true,
        name: 'Analysis endpoint',
        testConnectionURI: 'hcs/pipelines'
      }
    );
  }

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
    httpMethod: 'POST',
    ignoreResponse: true
  });

  runPipelineModule = (pipelineId, moduleId) => this.apiCall({
    uri: 'hcs/run/pipelines',
    query: {pipelineId, moduleId},
    httpMethod: 'POST'
  });

  attachFiles = (pipelineId, filesPayload) => this.apiCall({
    uri: 'hcs/pipelines/files',
    query: {pipelineId},
    httpMethod: 'POST',
    body: filesPayload
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

  generateVideo = (options) => this.apiCall({
    uri: 'hcs/clip',
    query: options
  })
}

export default AnalysisApi;
