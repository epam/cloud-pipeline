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
import preferences from '../../../../../models/preferences/PreferencesLoad';
import {getStorageFileAccessInfo} from '../../../../../utils/object-storage';
// eslint-disable-next-line max-len
import DataStorageItemUpdateContent from '../../../../../models/dataStorage/DataStorageItemUpdateContent';
import DataStorageTagsUpdate from '../../../../../models/dataStorage/tags/DataStorageTagsUpdate';
import {AnalysisPipeline} from './pipeline';
import {observable} from 'mobx';

export const CP_CELLPROFILER_PIPELINE_NAME = 'CP_CELLPROFILER_PIPELINE_NAME';
export const CP_CELLPROFILER_PIPELINE_DESCRIPTION = 'CP_CELLPROFILER_PIPELINE_DESCRIPTION';

/**
 * @typedef {Object} AnalysisPipelineFile
 * @property {string} name
 * @property {string} path
 * @property {ObjectStorage} objectStorage
 * @property {AnalysisPipeline} [pipeline]
 * @property {Promise<void>} [pipelinePromise]
 * @property {function} load
 * @property {Object} info
 */

/**
 * @returns {Promise<AnalysisPipelineFile[]>}
 */
export async function loadAvailablePipelines () {
  await preferences.fetchIfNeededOrWait();
  const configuration = preferences.hcsAnalysisConfiguration || {};
  const {
    pipelinesPath
  } = configuration;
  if (!pipelinesPath) {
    throw new Error('CellProfiler analysis pipelines path is not specified');
  }
  const info = await getStorageFileAccessInfo(pipelinesPath);
  if (!info) {
    return [];
  }
  const {
    objectStorage,
    path
  } = info;
  if (!objectStorage) {
    return [];
  }
  const contents = await objectStorage.getFolderContents(path || '');
  return contents
    .filter(o => /^file$/i.test(o.type) && /\.pipeline$/i.test(o.name))
    .map(o => ({objectStorage, path: o.path, name: o.name, info: o.labels}))
    .map(pipelineFile => {
      function load () {
        if (this.pipelinePromise) {
          return this.pipelinePromise;
        }
        this.pipelinePromise = new Promise((resolve) => {
          loadPipeline(this)
            .then(pipeline => {
              this.pipeline = observable(pipeline);
              return Promise.resolve(this);
            })
            .catch(() => {})
            .then(resolve);
        });
        return this.pipelinePromise;
      }
      pipelineFile.load = load.bind(pipelineFile);
      return pipelineFile;
    });
}

/**
 * @param {AnalysisPipelineFile} pipelineFile
 * @returns {Promise<AnalysisPipeline>}
 */
export async function loadPipeline (pipelineFile) {
  if (!pipelineFile || !pipelineFile.objectStorage || !pipelineFile.path) {
    return undefined;
  }
  const contents = await pipelineFile.objectStorage.getFileContent(pipelineFile.path);
  const pipeline = AnalysisPipeline.importPipeline(contents);
  if (pipeline) {
    pipeline.path = pipelineFile.path;
  }
  return pipeline;
}

/**
 * @param {AnalysisPipeline} pipeline
 * @returns {Promise<string>}
 */
export async function savePipeline (pipeline) {
  if (!pipeline) {
    return undefined;
  }
  await preferences.fetchIfNeededOrWait();
  const configuration = preferences.hcsAnalysisConfiguration || {};
  const {
    pipelinesPath
  } = configuration;
  if (!pipelinesPath) {
    throw new Error('CellProfiler analysis pipelines path is not specified');
  }
  const info = await getStorageFileAccessInfo(pipelinesPath);
  if (!info) {
    throw new Error('CellProfiler analysis pipelines storage not found');
  }
  const {
    objectStorage,
    path
  } = info;
  const pipelinePath = !pipeline.path
    ? `${path}/${pipeline.author || ''}-${moment.utc().format('YYYY-MM-DD-HH-mm-ss')}.pipeline`
    : pipeline.path;
  if (!objectStorage) {
    throw new Error('CellProfiler analysis pipelines storage not found');
  }
  const request = new DataStorageItemUpdateContent(objectStorage.id, pipelinePath);
  const content = pipeline.exportPipeline();
  await request.send(content);
  if (request.error) {
    throw new Error(request.error);
  }
  const tagsUpdate = new DataStorageTagsUpdate(objectStorage.id, pipelinePath);
  await tagsUpdate.send({
    CP_OWNER: pipeline.author,
    [CP_CELLPROFILER_PIPELINE_NAME]: pipeline.name,
    [CP_CELLPROFILER_PIPELINE_DESCRIPTION]: pipeline.description
  });
  return pipelinePath;
}
