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
import {getExternalEvaluationsSettings} from './job-utilities';
import {createObjectStorageWrapper} from '../../../../../utils/object-storage';
import storages from '../../../../../models/dataStorage/DataStorageAvailable';
import parseHCSFileParts from '../../../hcs-image/utilities/parse-hcs-file-parts';
import escapeRegExp, {ESCAPE_CHARACTERS} from '../../../../../utils/escape-reg-exp';

const EVALUATION_PLACEHOLDER = 'EVALUATION_ID';
const OWNER_PLACEHOLDER = 'OWNER';

const EVALUATION_REG_EXP = new RegExp(`\\{${EVALUATION_PLACEHOLDER}\\}`, 'ig');
const OWNER_REG_EXP = new RegExp(`\\{${OWNER_PLACEHOLDER}\\}`, 'ig');

/**
 * @typedef {Object} HCSExternalEvaluationsReplacer
 * @property {string} [fileName]
 * @property {string} [fileNameWithExtension]
 * @property {string} [fullPath]
 * @property {string} [parentFolder]
 * @property {function:Promise<*>} [getHcsInfo]
 */

/**
 * @typedef {Object} HCSExternalEvaluationPathComponent
 * @property {string} path
 * @property {boolean} containsPlaceholders
 * @property {RegExp} [mask]
 */

/**
 * @param {string} part
 * @param {HCSExternalEvaluationsReplacer} replacer
 * @returns {Promise<HCSExternalEvaluationPathComponent>}
 */
async function parsePathPart (part, replacer) {
  if (!part) {
    return {
      path: '',
      containsPlaceholders: false
    };
  }
  const {
    getHcsInfo,
    fileName,
    fileNameWithExtension,
    parentFolder
  } = replacer || {};
  let result = part;
  if (/\{HCS_FILE_INFO\.[^}]+\}/i.test(part) && typeof getHcsInfo === 'function') {
    const hcsInfo = await getHcsInfo();
    const hcsInfoPlaceholders = [...new Set(part.match(/\{HCS_FILE_INFO\.[^}]+\}/ig))];
    const keys = Object.keys(hcsInfo || {});
    hcsInfoPlaceholders.forEach(placeholder => {
      const [, property] = placeholder.match(/^\{HCS_FILE_INFO\.(.+)\}$/i) || [];
      if (property) {
        const key = keys.find(aKey => aKey.toLowerCase() === property.toLowerCase());
        if (
          key &&
          (
            typeof hcsInfo[key] === 'string' ||
            typeof hcsInfo[key] === 'number' ||
            typeof hcsInfo[key] === 'boolean'
          )
        ) {
          result = result
            .replace(new RegExp(`\\{HCS_FILE_INFO\\.${property}\\}`, 'ig'), hcsInfo[key]);
        }
      }
    });
  }
  if (parentFolder) {
    result = result.replace(/\{(FILE_PARENT_FOLDER|HCS_FILE_PARENT_FOLDER)\}/ig, parentFolder);
  }
  if (fileName) {
    result = result.replace(/\{(FILE_NAME|HCS_FILE_NAME|FILE)\}/ig, fileName);
  }
  if (fileNameWithExtension) {
    result = result.replace(
      /\{(FILE_NAME_WITH_EXT|HCS_FILE_NAME_WITH_EXT|FILE_WITH_EXT)\}/ig,
      fileNameWithExtension
    );
  }
  const placeholdersRegExp = new RegExp(
    `\\{(${EVALUATION_PLACEHOLDER}|${OWNER_PLACEHOLDER})\\}`,
    'ig'
  );
  if (placeholdersRegExp.test(result)) {
    let escaped = escapeRegExp(result, ESCAPE_CHARACTERS.filter(o => !['{', '}'].includes(o)));
    escaped = escaped.replace(EVALUATION_REG_EXP, '(.+)');
    escaped = escaped.replace(OWNER_REG_EXP, '(.+)');
    const mask = new RegExp(`^${escaped}$`, 'i');
    return {
      path: result,
      containsPlaceholders: true,
      mask
    };
  }
  return {
    path: result,
    containsPlaceholders: false
  };
}

/**
 * @param {string} path
 * @param {HCSExternalEvaluationsReplacer} replacer
 * @returns {Promise<[HCSExternalEvaluationPathComponent]>}
 */
async function parse (path, replacer) {
  if (!path) {
    return [];
  }
  const asyncParsePathParts = async (parts = []) => {
    if (!parts || !parts.length) {
      return [];
    }
    const [current, ...rest] = parts;
    const currentPart = await parsePathPart(current, replacer);
    const otherParts = await asyncParsePathParts(rest);
    return [
      currentPart,
      ...otherParts
    ];
  };
  const parseResult = await asyncParsePathParts(path.split('/'));
  return parseResult.reduce((result, current) => {
    const last = (result || []).slice().pop();
    if (last && !last.containsPlaceholders && !current.containsPlaceholders) {
      return [
        ...result.slice(0, -1),
        {
          path: `${last.path}/${current.path}`,
          containsPlaceholders: false
        }
      ];
    }
    return [
      ...result,
      current
    ];
  }, []);
}

async function fetchHCSFileJson (storage, path) {
  const objectStorage = await createObjectStorageWrapper(
    storages,
    storage,
    {write: false, read: true},
    {isURL: true, generateCredentials: true}
  );
  const hcsPathInfo = parseHCSFileParts(
    path,
    objectStorage.delimiter
  );
  if (!hcsPathInfo) {
    throw new Error('Not a .hcs file');
  }
  return objectStorage.getFileContent(path, {json: true});
}

function parseFullPath (path) {
  const [,, storageName, relativePath] = path
    .match(/^(s3:|nfs:|http:|https:)\/\/([^/]+)\/(.+)$/i) || [];
  return {
    storageName,
    path: relativePath
  };
}

/**
 * @param {string} hcsFilePath
 * @returns {Promise<HCSExternalEvaluationsReplacer>}
 */
async function buildReplacer (hcsFilePath) {
  if (!hcsFilePath) {
    return undefined;
  }
  const fileNameWithExtension = hcsFilePath.split('/').pop();
  const fileName = fileNameWithExtension.split('.').slice(0, -1).join('.');
  let parentFolder;
  const settings = await getExternalEvaluationsSettings();
  if (/^(s3:|nfs:|http:|https:)\//i.test(hcsFilePath)) {
    // full path
    parentFolder = hcsFilePath.split('/').slice(0, -1).join('/');
  } else if (settings.hcsFilesFolder) {
    let path = settings.hcsFilesFolder;
    if (path.endsWith('/')) {
      path = path.slice(0, -1);
    }
    parentFolder = path;
  }
  let hcsInfoPromise;
  const getHcsInfo = () => {
    if (!hcsInfoPromise) {
      hcsInfoPromise = new Promise((resolve) => {
        const {
          storageName,
          path
        } = parseFullPath(parentFolder);
        fetchHCSFileJson(storageName, `${path}/${fileNameWithExtension}`)
          .catch((error) => {
            console.warn(`Error fetching hcs file info: ${error.message}`);
            return Promise.resolve(undefined);
          })
          .then(resolve);
      });
    }
    return hcsInfoPromise;
  };
  return {
    fileName,
    fileNameWithExtension,
    parentFolder,
    fullPath: `${parentFolder}/${fileNameWithExtension}`,
    getHcsInfo
  };
}

/**
 * @param {string} path
 * @param {HCSExternalEvaluationPathComponent} pathComponent
 * @returns {*}
 */
function extractInfo (info, path, pathComponent) {
  if (!path || !pathComponent) {
    return undefined;
  }
  const {
    path: rawMask,
    containsPlaceholders,
    mask
  } = pathComponent;
  if (!containsPlaceholders || !mask) {
    return {};
  }
  if (mask.test(path)) {
    const [, ...substitutions] = mask.exec(path);
    const infoKeys = rawMask.match(/\{[^}]+\}/ig).map(o => o.slice(1, -1));
    const mergedInfo = {...(info || {})};
    for (let k = 0; k < infoKeys.length; k++) {
      const substitution = substitutions[k];
      const key = infoKeys[k];
      if (!mergedInfo[key] || mergedInfo[key] === substitution) {
        mergedInfo[key] = substitution;
      } else {
        return undefined;
      }
    }
    return mergedInfo;
  }
  return undefined;
}

/**
 * @typedef {Object} IterationSession
 * @property {*} objectStorages
 * @property {*} contents
 */

/**
 * @param {string|ExternalJobFilePath} path
 * @param {IterationSession} session
 * @returns {Promise<ObjectStorage>}
 */
async function getObjectStorageFromSession (path, session) {
  if (!path) {
    return undefined;
  }
  let storage, isURL;
  if (typeof path === 'string') {
    const {
      storageName
    } = parseFullPath(path);
    storage = storageName;
    isURL = true;
  }
  if (typeof path === 'object' && path.storageId) {
    storage = path.storageId;
    isURL = false;
  }
  if (session.objectStorages && session.objectStorages[storage]) {
    return Promise.resolve(session.objectStorages[storage]);
  }
  const objectStorage = await createObjectStorageWrapper(
    storages,
    storage,
    {write: false, read: true},
    {isURL, generateCredentials: false}
  );
  if (!session.objectStorages) {
    session.objectStorages = {};
  }
  session.objectStorages[storage] = objectStorage;
  return objectStorage;
}

/**
 * @param {{path: string, info: *}} [root]
 * @param {HCSExternalEvaluationPathComponent} pathComponent
 * @param {IterationSession} session
 */
async function iterateContents (root, pathComponent, session = {}) {
  const {
    path: rootPath,
    info = {}
  } = root || {};
  const oStorage = await getObjectStorageFromSession(rootPath, session);
  if (oStorage) {
    if (!pathComponent.containsPlaceholders) {
      return [{
        path: `${rootPath || ''}/${pathComponent.path}`,
        info
      }];
    }
    const rootCorrected = oStorage.getRelativePath(rootPath);
    let contents = [];
    if (!session.contents) {
      session.contents = {};
    }
    if (!session.contents[oStorage.id]) {
      session.contents[oStorage.id] = {};
    }
    if (session.contents[oStorage.id][rootCorrected]) {
      contents = session.contents[oStorage.id][rootCorrected];
    } else {
      contents = await oStorage.getFolderContents(rootCorrected);
      session.contents[oStorage.id][rootCorrected] = contents.map((o) => ({
        name: o.name,
        path: o.path,
        type: o.type
      }));
    }
    return contents
      .map((item) => ({
        item,
        info: extractInfo(info, item.name, pathComponent),
        path: `${rootPath}/${item.name}`
      }))
      .filter(o => !!o.info);
  }
  return [];
}

/**
 * @param {HCSExternalEvaluationPathComponent[]} components
 * @param {IterationSession} session
 * @returns {Promise<{evaluation: string?, owner: string?, path: ExternalJobFilePath}[]>}
 */
async function getEvaluationsByComponents (components, session = {}) {
  const [root, ...rest] = components;
  if (root.containsPlaceholders) {
    return [];
  }
  const iterate = async (root, array) => {
    if (!array || !array.length) {
      return [root];
    }
    const [current, ...otherComponents] = array;
    const items = await iterateContents(root, current, session);
    const processed = await Promise.all(items.map(item => iterate(item, otherComponents)));
    return processed.reduce((r, c) => ([...r, ...c]), []);
  };
  const evaluations = await iterate({path: root.path, info: {}}, rest);
  const raw = evaluations.map(({path, info}) => {
    const {
      [EVALUATION_PLACEHOLDER]: evaluation,
      [OWNER_PLACEHOLDER]: owner
    } = info || {};
    return {
      evaluation,
      owner,
      path
    };
  });
  return Promise.all(raw.map(item => new Promise((resolve) =>
    getObjectStorageFromSession(item.path, session)
      .then((aStorage) => resolve({
        evaluation: item.evaluation,
        owner: item.owner,
        path: aStorage
          ? {storageId: aStorage.id, path: aStorage.getRelativePath(item.path)}
          : undefined
      }))
      .catch((error) => {
        console.warn(`Error processing external evaluation: ${error.message}`);
        resolve();
      }))));
}

/**
 * @returns {IterationSession}
 */
function createIterationSession () {
  return {
    objectStorages: {},
    contents: {}
  };
}

/**
 * @param {IterationSession} session
 */
function clearIterationSession (session) {
  delete session.contents;
  delete session.objectStorages;
}

/**
 * @typedef {Object} ExternalJobFilePath
 * @property {string} storageId
 * @property {string} path
 */

/**
 * @typedef {Object} CellProfilerExternalJob
 * @property {string} id
 * @property {string} name
 * @property {string} alias
 * @property {ExternalJobFilePath} input
 * @property {ExternalJobFilePath} spec
 * @property {ExternalJobFilePath} result
 * @property {ExternalJobFilePath} [analysisFile]
 * @property {string} evaluation
 * @property {string} owner
 * @property {string} startDate
 * @property {{inputs: []}} specification
 */

export function isExternalJobIdentifier (jobId) {
  if (!jobId || typeof jobId !== 'string') {
    return false;
  }
  try {
    const [,, spec, result] = JSON.parse(jobId) || [];
    return spec && result;
  } catch (_) {
    return false;
  }
}

/**
 * @param {ExternalJobFilePath} input
 * @param {ExternalJobFilePath} spec
 * @param {ExternalJobFilePath} result
 * @param {ExternalJobFilePath} [analysisFile]
 * @returns {string}
 */
function buildExternalJobIdentifier (
  input,
  spec,
  result,
  analysisFile
) {
  if (!spec || !result || !input) {
    return undefined;
  }
  return JSON.stringify([
    input.storageId,
    input.path,
    spec.storageId,
    spec.path,
    result.storageId,
    result.path,
    analysisFile ? analysisFile.storageId : undefined,
    analysisFile ? analysisFile.path : undefined
  ].filter(Boolean));
}

/**
 * @typedef {Object} ParseExternalJobIdentifierResult
 * @property {ExternalJobFilePath} input
 * @property {ExternalJobFilePath} spec
 * @property {ExternalJobFilePath} result
 * @property {ExternalJobFilePath} [analysisFile]
 */

/**
 * @param {string} identifier
 * @returns {ParseExternalJobIdentifierResult}
 */
function parseExternalJobIdentifier (identifier) {
  if (!identifier) {
    return undefined;
  }
  try {
    const [
      inputStorageId,
      inputPath,
      specStorageId,
      specPath,
      resultsStorageId,
      resultsPath,
      analysisStorageId,
      analysisPath
    ] = JSON.parse(identifier);
    return {
      input: {
        storageId: inputStorageId,
        path: inputPath
      },
      spec: {
        storageId: specStorageId,
        path: specPath
      },
      result: {
        storageId: resultsStorageId,
        path: resultsPath
      },
      analysisFile: analysisStorageId && analysisPath
        ? {storageId: analysisStorageId, path: analysisPath}
        : undefined
    };
  } catch (_) {
    return undefined;
  }
}

function evaluationSorter (a, b) {
  if (!a && !b) {
    return 0;
  }
  const {
    alias: aAlias,
    startDate: aStartDate
  } = a;
  const {
    alias: bAlias,
    startDate: bStartDate
  } = b;
  if (!aStartDate && !bStartDate) {
    const aName = (aAlias || '').toLowerCase();
    const bName = (bAlias || '').toLowerCase();
    return aName.localeCompare(bName);
  }
  if (!aStartDate) {
    return 1;
  }
  if (!bStartDate) {
    return -1;
  }
  const aDate = moment.utc(aStartDate);
  const bDate = moment.utc(bStartDate);
  return bDate.diff(aDate);
}

/**
 * @param {string} hcsFileName
 * @returns {Promise<CellProfilerExternalJob[]>}
 */
export async function getExternalEvaluations (hcsFileName) {
  if (!hcsFileName) {
    return [];
  }
  const replacer = await buildReplacer(hcsFileName);
  const {
    fileName,
    fullPath
  } = replacer;
  const {
    specPath,
    resultsPath,
    analysisPath
  } = await getExternalEvaluationsSettings();
  const specsPathParsed = await parse(specPath, replacer);
  const resultsPathParsed = await parse(resultsPath, replacer);
  const analysisPathParsed = await parse(analysisPath, replacer);
  const session = createIterationSession();
  const specs = await getEvaluationsByComponents(specsPathParsed, session);
  const results = await getEvaluationsByComponents(resultsPathParsed, session);
  const analysisFiles = await getEvaluationsByComponents(analysisPathParsed, session);
  const inputStorage = await getObjectStorageFromSession(fullPath, session);
  const input = inputStorage
    ? {storageId: inputStorage.id, path: inputStorage.getRelativePath(fullPath)}
    : undefined;
  const evaluations = specs
    .map(aSpec => {
      const {
        evaluation,
        owner,
        path
      } = aSpec;
      const result = results.find(r => r.evaluation === evaluation);
      const analysisFile = analysisFiles.find(r => r.evaluation === evaluation);
      return {
        evaluation,
        owner,
        input,
        spec: path,
        result: result ? result.path : undefined,
        analysisFile: analysisFile ? analysisFile.path : undefined,
        name: fileName,
        id: buildExternalJobIdentifier(
          input,
          path,
          result ? result.path : undefined,
          analysisFile ? analysisFile.path : undefined
        )
      };
    })
    .filter(o => o.spec && o.result);
  const evaluationsInfos = await Promise.all(
    evaluations.map(evaluation => getExternalJobInfo(evaluation.id, session))
  );
  clearIterationSession(session);
  return evaluationsInfos
    .filter(Boolean)
    .sort(evaluationSorter);
}

/**
 * @param {string} jobId
 * @param {IterationSession} [session]
 * @returns {Promise<CellProfilerExternalJob>}
 */
export async function getExternalJobInfo (jobId, session) {
  try {
    const {
      input,
      spec,
      result,
      analysisFile
    } = parseExternalJobIdentifier(jobId);
    const iterationSession = session || createIterationSession();
    const output = result
      ? {...result, path: (result.path || '').split('/').slice(0, -1).join('/')}
      : undefined;
    if (!session) {
      clearIterationSession(iterationSession);
    }
    const specStorage = await getObjectStorageFromSession(spec, iterationSession);
    let specData = {};
    if (specStorage) {
      try {
        if (!specStorage.initialized) {
          await specStorage.initialize();
        }
        specData = await specStorage.getFileContent(spec.path, {json: true});
      } catch (error) {
        console.warn(`Error reading spec file: ${error.message}`);
      }
    }
    const {
      date,
      owner,
      name,
      timepoints = [],
      fields = [],
      wells = []
    } = specData || {};
    const inputs = [];
    for (let t of timepoints) {
      for (let f of fields) {
        for (let w of wells) {
          inputs.push({
            ...w,
            fieldId: f,
            timepoint: t,
            z: 0
          });
        }
      }
    }
    return {
      id: jobId,
      status: 'SUCCESS',
      job: {
        status: 'SUCCESS'
      },
      alias: name,
      spec,
      other: true,
      input,
      result,
      analysisFile,
      outputFolder: output,
      startDate: date,
      owner,
      specification: {
        inputs
      }
    };
  } catch (_) {
    return undefined;
  }
}
