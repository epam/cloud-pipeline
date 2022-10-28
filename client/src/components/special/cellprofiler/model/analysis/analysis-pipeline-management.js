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
import {AnalysisPipeline} from './pipeline';
import generateUUID from '../common/generate-uuid';
import displayDate from '../../../../../utils/displayDate';

const PIPELINE_META_FILE_NAME = 'meta.pipeline';

function generatePipelinePathComponent (pipelineName) {
  return (pipelineName || generateUUID())
    .replace(/[^a-zA-Z0-9]/g, '_')
    .toLowerCase();
}

export async function loadAvailablePipelineGroups () {
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
  const folders = contents.filter(o => /^folder$/i.test(o.type));
  const pipelines = await Promise.all(
    folders
      .map(o => loadPipelineGroup(objectStorage, o.path))
  );
  return pipelines
    .filter(Boolean)
    .sort((a, b) => (a.name || '').toLowerCase() < (b.name || '').toLowerCase() ? -1 : 1);
}

async function loadPipelineGroup (objectStorage, pipelineFolderPath) {
  let pipelineMetaPath = pipelineFolderPath;
  if (pipelineMetaPath.endsWith('/')) {
    pipelineMetaPath = pipelineMetaPath.slice(0, -1);
  }
  pipelineMetaPath = pipelineMetaPath.concat('/', PIPELINE_META_FILE_NAME);
  try {
    const metaContents = await objectStorage.getFileContent(pipelineMetaPath);
    const {
      name,
      latest
    } = JSON.parse(metaContents);
    if (!latest) {
      throw new Error('No latest version is specified');
    }
    const loadVersion = async (versionFile) => {
      const {
        key,
        version,
        name: versionName
      } = versionFile || {};
      const versionContents = await objectStorage
        .getFileContent(`${pipelineFolderPath}/${version}.version`);
      const pipeline = AnalysisPipeline.importPipeline(versionContents);
      if (pipeline) {
        pipeline.name = name;
        pipeline.path = pipelineFolderPath;
        pipeline.version = version;
        return {
          pipeline,
          key,
          version,
          name: versionName,
          load () {
            return Promise.resolve(pipeline);
          }
        };
      }
      return undefined;
    };
    return {
      name,
      path: pipelineFolderPath,
      key: `${pipelineFolderPath}|latest`,
      version: latest.version,
      pipeline: {
        ...latest,
        path: pipelineFolderPath
      },
      async load () {
        const loaded = await loadVersion(latest);
        if (loaded) {
          return loaded.load();
        }
        return undefined;
      },
      children: [{
        loading: true,
        key: `${pipelineFolderPath}|loading`
      }],
      async loadVersions () {
        const directoryContents = await objectStorage.getFolderContents(pipelineFolderPath || '');
        const getVersionName = (aFile) => {
          const {
            name,
            changed
          } = aFile;
          const version = name.split('.')[0];
          return [
            displayDate(changed, 'DD.MM.YYYY HH:mm'),
            version === latest.version ? '(latest)' : undefined
          ].filter(Boolean).join(' ');
        };
        const versions = directoryContents
          .filter(o => /^file$/i.test(o.type) && /\.version$/i.test(o.path))
          .map(o => ({
            ...o,
            key: `${pipelineFolderPath}|${o.name.split('.')[0]}`,
            version: o.name.split('.')[0],
            name: getVersionName(o),
            changeDate: moment.utc(o.changed)
          }))
          .sort((a, b) => b.changeDate - a.changeDate);
        const loaded = await Promise.all(versions.map(loadVersion));
        this.children = loaded.filter(Boolean);
      }
    };
  } catch (error) {
    // eslint-disable-next-line max-len
    console.log(`Error reading pipeline info. Folder "${pipelineFolderPath}", error: ${error.message}`);
    return undefined;
  }
}

/**
 * @param {AnalysisPipeline} pipeline
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
  if (!objectStorage) {
    throw new Error('CellProfiler analysis pipelines storage not found');
  }
  pipeline.path = pipeline.path || `${path}/${generatePipelinePathComponent(pipeline.name)}`;
  pipeline.version = generateUUID();
  const pipelineVersionPath = `${pipeline.path}/${pipeline.version}.version`;
  const pipelineMetaPath = `${pipeline.path}/${PIPELINE_META_FILE_NAME}`;
  const request = new DataStorageItemUpdateContent(objectStorage.id, pipelineVersionPath);
  pipeline.modifiedDate = moment.utc();
  const content = pipeline.exportPipeline();
  await request.send(content);
  if (request.error) {
    throw new Error(request.error);
  }
  const metaUpdateRequest = new DataStorageItemUpdateContent(objectStorage.id, pipelineMetaPath);
  await metaUpdateRequest.send(JSON.stringify({
    name: pipeline.name,
    latest: {
      version: pipeline.version,
      author: pipeline.author,
      description: pipeline.description
    }
  }));
}
