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

import PipelineRunFilter from '../../../../../models/pipelines/PipelineRunSingleFilter';
import {parseRunServiceUrlConfiguration} from '../../../../../utils/multizone';
import PipelineRunInfo from '../../../../../models/pipelines/PipelineRunInfo';
import preferences from '../../../../../models/preferences/PreferencesLoad';
import LoadTool from '../../../../../models/tools/LoadTool';

const DEFAULT_DOCKER_IMAGE = 'library/cellprofiler-web-api';

export async function findJobWithDockerImage (options = {}) {
  await preferences.fetchIfNeededOrWait();
  const configuration = preferences.hcsAnalysisConfiguration || {};
  const {
    dockerImage = DEFAULT_DOCKER_IMAGE,
    jobId
  } = configuration;
  if (jobId) {
    return {
      id: jobId,
      status: 'RUNNING'
    };
  }
  if (!dockerImage) {
    throw new Error('CellProfiler docker image is not specified');
  }
  const {
    userInfo
  } = options;
  let owners;
  let dockerImageRegExp;
  if (typeof dockerImage === 'string') {
    const withVersion = dockerImage.split('/').pop().split(':').length === 2;
    dockerImageRegExp = new RegExp(`${dockerImage}${withVersion ? '$' : ''}`, 'i');
  } else if (typeof dockerImage.test === 'function') {
    dockerImageRegExp = dockerImage;
  } else {
    throw new Error(`Unknown docker image format: ${dockerImage}`);
  }
  if (userInfo) {
    await userInfo.fetchIfNeededOrWait();
    if (userInfo.loaded) {}
    owners = [userInfo.value.userName];
  }
  const request = new PipelineRunFilter({
    page: 1,
    pageSize: 1000,
    userModified: false,
    statuses: ['RUNNING', 'PAUSING', 'PAUSED', 'RESUMING'],
    owners
  }, true, false);
  await request.filter();
  if (request.error) {
    throw new Error(request.error);
  }
  const jobs = request.value || [];
  return jobs.find(aJob => aJob.id === jobId || dockerImageRegExp.test(aJob.dockerImage));
}

export async function launchJobWithDockerImage () {
  await preferences.fetchIfNeededOrWait();
  const configuration = preferences.hcsAnalysisConfiguration || {};
  const {
    dockerImage = DEFAULT_DOCKER_IMAGE
  } = configuration;
  if (!dockerImage) {
    throw new Error('CellProfiler docker image is not specified');
  }
  const [image, group, registry] = dockerImage.split('/').reverse();
  const toolRequest = new LoadTool([group, image].filter(Boolean).join('/'), registry);
  await toolRequest.fetch();
  if (toolRequest.error) {
    throw new Error(toolRequest.error);
  }
  if (!toolRequest.value) {
    throw new Error(`Tool ${dockerImage} not found`);
  }
  // todo: launch a tool
  return Promise.reject(new Error('CellProfiler job not found'));
}

export async function fetchJobInfo (jobId) {
  const request = new PipelineRunInfo(jobId);
  await request.fetch();
  if (request.error) {
    throw new Error(request.error);
  }
  if (request.loaded) {
    return request.value;
  }
  return undefined;
}

async function jobIsInitialized (job) {
  if (job) {
    const {initialized, serviceUrl} = job;
    const parsed = parseRunServiceUrlConfiguration(serviceUrl);
    if (initialized && parsed.length) {
      const defaultUrl = parsed.find(o => o.isDefault) || parsed[0];
      if (defaultUrl && defaultUrl.url) {
        const {url} = defaultUrl;
        await preferences.fetchIfNeededOrWait();
        const configuration = preferences.hcsAnalysisConfiguration || {};
        const {
          endpointRegion
        } = configuration;
        if (url && url[endpointRegion]) {
          return url[endpointRegion];
        }
        return Object.values(url).pop();
      }
    }
  }
  return undefined;
}

const REFETCH_INTERVAL_MS = 5000;
const TIMEOUT_MS = 1000 * 60 * 5;
const MAX_ATTEMPTS = TIMEOUT_MS / REFETCH_INTERVAL_MS;

/**
 * Returns job endpoint
 * @param job
 * @param attempt
 * @returns {Promise<never>|Promise<string>}
 */
export async function waitForJobToBeInitialized (job, attempt = 0) {
  if (!job) {
    throw new Error('Job not specified');
  }
  if (!/^running$/i.test(job.status)) {
    throw new Error('Job stopped');
  }
  if (attempt > MAX_ATTEMPTS) {
    throw new Error('Job is not initialized (max attempts exceeded)');
  }
  const endpoint = await jobIsInitialized(job);
  if (endpoint) {
    return endpoint;
  }
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      fetchJobInfo(job.id)
        .then(info => waitForJobToBeInitialized(info, attempt + 1))
        .then(resolve)
        .catch(reject);
    }, REFETCH_INTERVAL_MS);
  });
}
