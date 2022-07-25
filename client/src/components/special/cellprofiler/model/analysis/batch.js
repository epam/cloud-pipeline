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

/**
 * @typedef {Object}BatchAnalysisInput
 * @property {number} x
 * @property {number} y
 * @property {number} z
 * @property {number} timepoint
 * @property {number} channel
 * @property {string} channelName
 * @property {string} fieldId
 */

/**
 * @typedef {Object} BatchAnalysisModule
 * @property {string} moduleName
 * @property {number} moduleId
 * @property {*} parameters
 */

/**
 * @typedef {Object} BatchAnalysisSpecification
 * @property {string} [measurementUUID]
 * @property {BatchAnalysisInput[]} [inputs=[]]
 * @property {BatchAnalysisModule[]} [modules=[]]
 * @property {number} [storage]
 * @property {string} [path]
 * @property {AnalysisPipeline} [pipeline]
 * @property {string} [alias]
 */

import {getBatchAnalysisSettings} from './job-utilities';
import {PipelineRunner} from '../../../../../models/pipelines/PipelineRunner';
import {CP_CAP_LIMIT_MOUNTS} from '../../../../pipelines/launch/form/utilities/parameters';
import PipelineRunSingleFilter from '../../../../../models/pipelines/PipelineRunSingleFilter';
import generateUUID from '../common/generate-uuid';
import PipelineRunInfo from '../../../../../models/pipelines/PipelineRunInfo';
import {generateResourceUrl} from './output-utilities';

const CELLPROFILER_API_BATCH = 'CELLPROFILER_API_BATCH';
const CELLPROFILER_API_RAW_DATA_ROOT_DIR = 'CELLPROFILER_API_RAW_DATA_ROOT_DIR';
const CELLPROFILER_API_COMMON_RESULTS_DIR = 'CELLPROFILER_API_COMMON_RESULTS_DIR';
const CELLPROFILER_API_BATCH_RESULTS_DIR = 'CELLPROFILER_API_BATCH_RESULTS_DIR';
const CELLPROFILER_API_BATCH_RESULTS_STORAGE = 'CELLPROFILER_API_BATCH_RESULTS_STORAGE';
const CELLPROFILER_API_BATCH_RESULTS_STORAGE_PATH = 'CELLPROFILER_API_BATCH_RESULTS_STORAGE_PATH';
const CELLPROFILER_API_BATCH_SPEC_FILE = 'CELLPROFILER_API_BATCH_SPEC_FILE';
const CELLPROFILER_API_BATCH_SPEC_STORAGE = 'CELLPROFILER_API_BATCH_SPEC_STORAGE';
const CELLPROFILER_API_BATCH_SPEC_STORAGE_PATH = 'CELLPROFILER_API_BATCH_SPEC_STORAGE_PATH';
const CELLPROFILER_API_BATCH_UUID = 'CELLPROFILER_API_BATCH_UUID';
const CELLPROFILER_API_BATCH_PIPELINE = 'CELLPROFILER_API_BATCH_PIPELINE';
const CELLPROFILER_API_BATCH_FILE_STORAGE = 'CELLPROFILER_API_BATCH_FILE_STORAGE';
const CELLPROFILER_API_BATCH_FILE_PATH = 'CELLPROFILER_API_BATCH_FILE_PATH';
const CELLPROFILER_API_BATCH_FILE_NAME = 'CELLPROFILER_API_BATCH_FILE_NAME';

/**
 * @param {BatchAnalysisSpecification} specification
 * @returns {Promise<void>}
 */
export async function submitBatchAnalysis (specification) {
  const {
    dockerImage,
    settings,
    specs,
    results,
    mounts,
    rawImagesPath,
    tempFilesPath
  } = await getBatchAnalysisSettings(true);
  const uuid = generateUUID();
  const specFullPath = specs.storage.joinPaths(specs.folder, uuid, 'spec.json');
  const {
    measurementUUID,
    inputs,
    modules,
    pipeline
  } = specification;
  const spec = {
    measurementUUID,
    inputs,
    modules,
    pipeline
  };
  await specs.storage.writeFile(specFullPath, JSON.stringify(spec, undefined, ' '));
  const resultsDirFullPath = results.storage.joinPaths(results.folder, uuid);
  const localSpecPath = specs.storage.getLocalPath(specFullPath);
  const localResultsPath = results.storage.getLocalPath(resultsDirFullPath);
  const storagesToMount = [
    ...new Set([...mounts, specs.storage, results.storage].map(o => o.id))
  ];
  const {
    parameters: toolParameters = {},
    cmd_template: cmdTemplate,
    instance_disk: hddSize,
    instance_size: instanceType,
    is_spot: isSpot,
    nonPause
  } = settings || {};
  const parameters = {
    ...toolParameters
  };
  const getParameterValue = (parameter) => {
    if (parameters[parameter]) {
      return parameters[parameter].value;
    }
    return undefined;
  };
  const setParameterValue = (parameter, value, skipIfExists = false) => {
    if (value === undefined) {
      return;
    }
    if (
      !skipIfExists ||
      !parameters[parameter] ||
      parameters[parameter].value === undefined
    ) {
      parameters[parameter] = {value, type: 'string'};
    }
  };
  const limitMountsParameter = [...new Set(
    (getParameterValue(CP_CAP_LIMIT_MOUNTS) || '')
      .split(',')
      .map(o => Number(o))
      .concat(storagesToMount)
  )].join(',');
  setParameterValue(CP_CAP_LIMIT_MOUNTS, limitMountsParameter);
  if (rawImagesPath) {
    setParameterValue(CELLPROFILER_API_RAW_DATA_ROOT_DIR, rawImagesPath, true);
  }
  if (tempFilesPath) {
    setParameterValue(CELLPROFILER_API_COMMON_RESULTS_DIR, tempFilesPath);
  }
  setParameterValue(CELLPROFILER_API_BATCH_RESULTS_DIR, localResultsPath);
  setParameterValue(CELLPROFILER_API_BATCH_RESULTS_STORAGE, results.storage.id);
  setParameterValue(CELLPROFILER_API_BATCH_RESULTS_STORAGE_PATH, resultsDirFullPath);
  setParameterValue(CELLPROFILER_API_BATCH_SPEC_FILE, localSpecPath);
  setParameterValue(CELLPROFILER_API_BATCH_SPEC_STORAGE, specs.storage.id);
  setParameterValue(CELLPROFILER_API_BATCH_SPEC_STORAGE_PATH, specFullPath);
  setParameterValue(CELLPROFILER_API_BATCH_UUID, specification.measurementUUID);
  setParameterValue(CELLPROFILER_API_BATCH_FILE_STORAGE, specification.storage);
  setParameterValue(CELLPROFILER_API_BATCH_FILE_PATH, specification.path);
  setParameterValue(
    CELLPROFILER_API_BATCH_FILE_NAME,
    (specification.path || '').split(/[\\/]/).pop()
  );
  setParameterValue(
    CELLPROFILER_API_BATCH_PIPELINE,
    pipeline ? (pipeline.uuid || pipeline.path) : undefined
  );
  setParameterValue(CELLPROFILER_API_BATCH, true);
  const tags = {};
  if (specification.alias) {
    tags.analysisAlias = specification.alias;
  }
  const pipelineRunnerRequest = new PipelineRunner();
  await pipelineRunnerRequest.send({
    dockerImage,
    cmdTemplate,
    instanceType,
    hddSize,
    isSpot,
    nonPause,
    params: parameters,
    tags
  });
  if (pipelineRunnerRequest.error) {
    throw new Error(`Batch analysis: ${pipelineRunnerRequest.error}`);
  }
  return pipelineRunnerRequest.value;
}

const getJobParameter = (job, parameterName) => {
  if (!job || !parameterName) {
    return undefined;
  }
  const {
    pipelineRunParameters = []
  } = job;
  const parameter = pipelineRunParameters.find(o => o.name === parameterName);
  return parameter ? parameter.value : undefined;
};

const getJobStorageLocation = (job, storageParameter, pathParameter) => {
  const storage = getJobParameter(job, storageParameter);
  const file = getJobParameter(job, pathParameter);
  if (file && storage) {
    return {
      storageId: Number(storage),
      path: file
    };
  }
  return undefined;
};
const getJobInput = (job) => {
  return getJobStorageLocation(
    job,
    CELLPROFILER_API_BATCH_FILE_STORAGE,
    CELLPROFILER_API_BATCH_FILE_PATH
  );
};
const getJobOutputFolder = (job) => {
  return getJobStorageLocation(
    job,
    CELLPROFILER_API_BATCH_RESULTS_STORAGE,
    CELLPROFILER_API_BATCH_RESULTS_STORAGE_PATH
  );
};
const getJobSpec = (job) => {
  return getJobStorageLocation(
    job,
    CELLPROFILER_API_BATCH_SPEC_STORAGE,
    CELLPROFILER_API_BATCH_SPEC_STORAGE_PATH
  );
};

/**
 * @typedef {Object} StorageItemLocation
 * @property {number} storageId
 * @property {string} path
 */

/**
 * @typedef {Object} BatchAnalysisJob
 * @property {number} id
 * @property {string} status
 * @property {string} startDate
 * @property {string} endDate
 * @property {string} owner
 * @property {string} measurementUUID
 * @property {StorageItemLocation} input
 * @property {StorageItemLocation} outputFolder
 * @property {StorageItemLocation} spec
 * @property {object} job
 * @property {string} [alias]
 */

/**
 * @typedef {Object} BatchJobsFilters
 * @property {string[]} [userNames]
 * @property {string} [source]
 * @property {string} [pipeline]
 * @property {string} [measurementUUID]
 * @property {number} [page=0] Zero-based page number
 * @property {number} [pageSize=30]
 */

/**
 * @param {Object} run
 * @returns {BatchAnalysisJob}
 */
function parseJob (run) {
  if (!run) {
    return undefined;
  }
  return {
    id: run.id,
    status: run.status,
    startDate: run.startDate,
    endDate: run.endDate,
    measurementUUID: getJobParameter(run, CELLPROFILER_API_BATCH_UUID),
    input: getJobInput(run),
    outputFolder: getJobOutputFolder(run),
    spec: getJobSpec(run),
    owner: run.owner,
    alias: (run.tags || {}).analysisAlias,
    job: run
  };
}

/**
 * @param {BatchJobsFilters} a
 * @param {BatchJobsFilters} b
 * @param {boolean} [ignorePagination=false]
 * @returns {boolean}
 */
export function filtersAreEqual (a, b, ignorePagination = false) {
  const {
    userNames: aUserNames = [],
    measurementUUID: aImage,
    pipeline: aPipeline,
    page: aPage,
    pageSize: aPageSize,
    source: aSource
  } = a || {};
  const {
    userNames: bUserNames = [],
    measurementUUID: bImage,
    pipeline: bPipeline,
    page: bPage,
    pageSize: bPageSize,
    source: bSource
  } = b || {};
  const aNames = [...new Set(aUserNames)].sort();
  const bNames = [...new Set(bUserNames)].sort();
  if (aNames.length !== bNames.length) {
    return false;
  }
  for (let i = 0; i < aNames.length; i++) {
    if (aNames[i] !== bNames[i]) {
      return false;
    }
  }
  return aImage === bImage &&
    aPipeline === bPipeline &&
    (
      ignorePagination ||
      (aPage === bPage && aPageSize === bPageSize)
    ) &&
    aSource === bSource;
}

/**
 * @param {BatchJobsFilters} filters
 * @returns {Promise<{total: number, jobs: BatchAnalysisJob[], page: number, pageSize: number}>}
 */
export async function getBatchJobs (filters = {}) {
  const {
    page = 0,
    pageSize = 30,
    userNames = [],
    measurementUUID
  } = filters;
  const payload = {
    page: page + 1,
    pageSize,
    userModified: false
  };
  if (userNames.length) {
    payload.owners = userNames.slice();
  }
  if (measurementUUID) {
    payload.partialParameters = `${CELLPROFILER_API_BATCH_UUID}=${measurementUUID}`;
  } else {
    payload.partialParameters = `${CELLPROFILER_API_BATCH}=true`;
  }
  const request = new PipelineRunSingleFilter(payload, false, false);
  await request.filter();
  if (request.error) {
    throw new Error(request.error);
  }
  const total = request.total;
  const jobs = (request.value || []).map(parseJob);
  return {
    total,
    jobs,
    page,
    pageSize
  };
}

export async function getBatchJobInfo (jobId) {
  const request = new PipelineRunInfo(jobId);
  await request.fetch();
  if (request.error) {
    throw new Error(`Error fetching batch analysis results: ${request.error}`);
  }
  return parseJob(request.value);
}

/**
 * @param {BatchAnalysisJob|string|{storageId: number, path: string}} job
 * @returns {Promise<BatchAnalysisSpecification>}
 */
export async function getSpecification (job) {
  if (!job) {
    return undefined;
  }
  const options = {};
  if (typeof job === 'string') {
    options.url = job;
  } else if (typeof job === 'object') {
    if (typeof job.spec === 'object') {
      options.storageId = job.spec.storageId;
      options.path = job.spec.path;
    } else if (job.storageId && job.path) {
      options.storageId = job.storageId;
      options.path = job.path;
    }
  }
  const specsUrl = await generateResourceUrl(options);
  if (!specsUrl) {
    return undefined;
  }
  const response = await fetch(specsUrl);
  return response.json();
}
