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

import moment from 'moment-timezone';
import PipelineRunFilter from '../../../../../models/pipelines/PipelineRunSingleFilter';
import {parseRunServiceUrlConfiguration} from '../../../../../utils/multizone';
import PipelineRunInfo from '../../../../../models/pipelines/PipelineRunInfo';
import preferences from '../../../../../models/preferences/PreferencesLoad';
import LoadTool from '../../../../../models/tools/LoadTool';
import whoAmI from '../../../../../models/user/WhoAmI';
import {createObjectStorageWrapper} from '../../../../../utils/object-storage';
import storages from '../../../../../models/dataStorage/DataStorageAvailable';
import LoadToolVersionSettings from '../../../../../models/tools/LoadToolVersionSettings';

const DEFAULT_DOCKER_IMAGE = 'library/cellprofiler-web-api';

export async function getAnalysisSettings (refresh = false) {
  if (refresh) {
    await preferences.fetch();
  } else {
    await preferences.fetchIfNeededOrWait();
  }
  return preferences.hcsAnalysisConfiguration || {};
}

export async function getAnalysisEndpointSetting () {
  const configuration = await getAnalysisSettings();
  const {
    endpoint,
    api = endpoint
  } = configuration;
  return api;
}

const containsPlaceholder = (placeholder) =>
  (string) =>
    (new RegExp(`{${placeholder}}`, 'i')).test(string);
const hasUserPlaceholder = containsPlaceholder('user');
const hasUserStoragePlaceholder = containsPlaceholder('user-storage');
const fetchUserRequired = (specsFolder, resultsFolder) =>
  hasUserPlaceholder(specsFolder) ||
  hasUserStoragePlaceholder(specsFolder) ||
  hasUserPlaceholder(resultsFolder) ||
  hasUserStoragePlaceholder(resultsFolder);

export async function getDockerImageInfo (dockerImage, dockerImageVersion = 'latest') {
  if (!dockerImage) {
    throw new Error('Batch analysis: analysis tool not specified');
  }
  let toolRequestPayload = dockerImage;
  let registry;
  if (Number.isNaN(Number(dockerImage))) {
    const [
      toolAndVersion,
      group,
      parsedRegistry
    ] = dockerImage.split('/').reverse();
    registry = parsedRegistry;
    if (!group) {
      throw new Error('Batch analysis: tool image format is incorrect (group is missing)');
    }
    const [
      tool,
      version = 'latest'
    ] = toolAndVersion.split(':');
    dockerImageVersion = version;
    toolRequestPayload = [group, tool].filter(Boolean).join('/');
  }
  const toolRequest = new LoadTool(toolRequestPayload, registry);
  await toolRequest.fetch();
  if (toolRequest.error) {
    throw new Error(toolRequest.error);
  }
  if (!toolRequest.value) {
    throw new Error(`tool ${dockerImage} not found`);
  }
  const toolInfo = toolRequest.value;
  const toolSettings = new LoadToolVersionSettings(toolInfo.id, dockerImageVersion);
  await toolSettings.fetch();
  if (toolSettings.error) {
    throw new Error(toolSettings.error);
  }
  const info = (toolSettings.value || []).find(o => o.version === dockerImageVersion);
  let versionSettings = {};
  if (info && info.settings && info.settings.length > 0) {
    versionSettings = info.settings[0].configuration;
  }
  return {
    dockerImage: `${toolInfo.registry}/${toolInfo.image}:${dockerImageVersion}`,
    dockerImageWithoutVersion: `${toolInfo.registry}/${toolInfo.image}`,
    tool: toolInfo,
    settings: versionSettings
  };
}

export async function getBatchAnalysisDockerImage (refresh = false) {
  const settings = await getAnalysisSettings(refresh);
  const {
    dockerImage: analysisDockerImage,
    batch = {}
  } = settings || {};
  let {
    dockerImage = analysisDockerImage,
    dockerImageVersion = 'latest'
  } = batch;
  try {
    return getDockerImageInfo(dockerImage, dockerImageVersion);
  } catch (e) {
    throw new Error(`Batch analysis: ${e.message}`);
  }
}

export async function getBatchAnalysisSettings (refresh = false) {
  const settings = await getAnalysisSettings(refresh);
  const {
    batch = {}
  } = settings || {};
  let {
    specsFolder,
    resultsFolder,
    defaultStorage,
    rawImageDataRoot: predefinedRawImageDataRoot,
    mounts = [],
    tempFilesPath,
    ...rest
  } = batch;
  if (!specsFolder) {
    throw new Error('Batch analysis: specs folder not specified');
  }
  if (!resultsFolder) {
    throw new Error('Batch analysis: results folder not specified');
  }
  let userName;
  let userStorage = defaultStorage;
  const date = moment.utc().format('YYYY-MM-DD');
  if (fetchUserRequired(specsFolder, resultsFolder)) {
    await whoAmI.fetchIfNeededOrWait();
    if (whoAmI.error || !whoAmI.loaded) {
      throw new Error('Batch analysis: not authenticated');
    }
    userName = whoAmI.value.userName;
    await storages.fetchIfNeededOrWait();
    if (whoAmI.value.defaultStorageId) {
      const aStorage = (storages.value || [])
        .find(s => Number(s.id) === Number(whoAmI.value.defaultStorageId));
      if (aStorage) {
        userStorage = aStorage.pathMask;
      }
    }
  }
  const replacePlaceholders = (string) => {
    let result = string;
    if (/{user}/i.test(result)) {
      result = result.replace(/{user}/ig, userName);
    }
    if (/{user-storage}/i.test(result)) {
      if (!userStorage) {
        throw new Error('Batch analysis: default user storage not specified');
      }
      result = result.replace(/{user-storage}/ig, userStorage);
    }
    return result.replace(/{date}/ig, date);
  };
  specsFolder = replacePlaceholders(specsFolder);
  resultsFolder = replacePlaceholders(resultsFolder);
  const specsStorage = await createObjectStorageWrapper(
    storages,
    specsFolder,
    {write: true, read: true},
    {generateCredentials: false, isURL: true}
  );
  if (!specsStorage) {
    throw new Error(`Batch analysis: storage for path "${specsFolder}" not found`);
  }
  const resultsStorage = await createObjectStorageWrapper(
    storages,
    resultsFolder,
    {write: true, read: true},
    {generateCredentials: false, isURL: true}
  );
  if (!resultsStorage) {
    throw new Error(`Batch analysis: storage for path "${resultsFolder}" not found`);
  }
  const dockerImageInfo = await getBatchAnalysisDockerImage(false);
  const additionalMounts = new Set(mounts.map(o => Number(o)));
  let rawImagesPath;
  if (predefinedRawImageDataRoot) {
    const rawImagesStorage = await createObjectStorageWrapper(
      storages,
      predefinedRawImageDataRoot,
      {read: true, write: false},
      {generateCredentials: true, isURL: true}
    );
    if (rawImagesStorage) {
      additionalMounts.add(rawImagesStorage.id);
      rawImagesPath = rawImagesStorage.getRelativePath(predefinedRawImageDataRoot);
    }
  }
  return {
    ...dockerImageInfo,
    ...rest,
    specs: {
      folder: specsStorage.getRelativePath(specsFolder),
      storage: specsStorage
    },
    results: {
      folder: resultsStorage.getRelativePath(resultsFolder),
      storage: resultsStorage
    },
    mounts: [...additionalMounts],
    rawImagesPath,
    tempFilesPath
  };
}

export async function getExternalEvaluationsSettings (refresh = false) {
  const settings = await getAnalysisSettings(refresh);
  const {
    hcsFilesFolder,
    batch = {}
  } = settings || {};
  const {
    otherEvaluations = {}
  } = batch;
  const {
    specPath = '{HCS_FILE_INFO.previewDir}/eval/{EVALUATION_ID}/spec.json',
    resultsPath = '{HCS_FILE_INFO.previewDir}/eval/{EVALUATION_ID}/Results.csv',
    analysisPath = '{HCS_FILE_INFO.previewDir}/eval/{EVALUATION_ID}/AnalysisFile.aas'
  } = otherEvaluations;
  return {
    hcsFilesFolder,
    specPath,
    resultsPath,
    analysisPath
  };
}

export async function getVideoSettings (refresh = false) {
  const settings = await getAnalysisSettings(refresh);
  const {
    api: mainAPI,
    video
  } = settings || {};
  const {
    api = mainAPI,
    ...rest
  } = video || {};
  return {
    ...rest,
    api
  };
}

export async function getBatchAnalysisSimilarCheckSettings (refresh = false) {
  const settings = await getAnalysisSettings(refresh);
  const {
    batch = {}
  } = settings || {};
  const {
    similar = {}
  } = batch;
  const {
    'max-different-parameters': maxDifferentParameters,
    mode = 'total'
  } = similar;
  return {
    maxDifferentParameters,
    mode
  };
}

export async function findJobWithDockerImage (options = {}) {
  const configuration = await getAnalysisSettings();
  const {
    dockerImage = DEFAULT_DOCKER_IMAGE,
    jobId
  } = configuration;
  if (jobId) {
    return {
      id: jobId,
      status: 'RUNNING',
      __predefined__: true
    };
  }
  if (!dockerImage) {
    throw new Error('CellProfiler docker image is not specified');
  }
  const {
    dockerImage: fullDockerImage
  } = await getDockerImageInfo(dockerImage);
  const {
    userInfo
  } = options;
  let owners;
  const dockerImageRegExp = new RegExp(`^${fullDockerImage}$`, 'i');
  if (userInfo) {
    await userInfo.fetchIfNeededOrWait();
    if (userInfo.loaded) {}
    owners = [userInfo.value.userName];
  }
  const request = new PipelineRunFilter({
    page: 1,
    pageSize: 1000,
    userModified: false,
    statuses: ['RUNNING'],
    owners
  }, false);
  await request.filter();
  if (request.error) {
    throw new Error(request.error);
  }
  const jobs = request.value || [];
  return jobs.find(aJob => aJob.id === jobId || dockerImageRegExp.test(aJob.dockerImage));
}

export async function launchJobWithDockerImage () {
  const configuration = await getAnalysisSettings();
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
        const configuration = await getAnalysisSettings();
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
  const interval = job.__predefined__ ? 0 : REFETCH_INTERVAL_MS;
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      fetchJobInfo(job.id)
        .then(info => waitForJobToBeInitialized(info, attempt + 1))
        .then(resolve)
        .catch(reject);
    }, interval);
  });
}
